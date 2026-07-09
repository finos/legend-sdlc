// Copyright 2020 Goldman Sachs
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.finos.legend.sdlc.server.gitlab.auth;

import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.ClientCookie;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.finos.legend.sdlc.server.gitlab.GitLabAppInfo;
import org.finos.legend.sdlc.server.gitlab.GitLabServerInfo;
import org.finos.legend.sdlc.server.tools.AuthenticationTools;
import org.finos.legend.sdlc.tools.StringTools;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.FormElement;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Authenticates against GitLab via SAML using SPNego (Kerberos) and returns
 * a {@code _gitlab_session} cookie.
 *
 * <p>Key behaviours:
 * <ol>
 *   <li>The SAML request phase is initiated with {@code POST} instead of
 *       {@code GET}, because the GitLab SAML endpoint
 *       ({@code /users/auth/saml}) now rejects {@code GET} since the upgrade
 *       to OmniAuth 2.x.</li>
 *   <li>When the auth URI targets the GitLab server, the {@code POST} is
 *       preceded by a {@code GET} of {@code /users/sign_in} on the same
 *       cookie store. This primes a {@code _gitlab_session} cookie via
 *       SPNego and reads the Rails {@code authenticity_token} that
 *       {@code omniauth-rails_csrf_protection} requires on the body of the
 *       subsequent {@code POST}. Without this, GitLab silently 302s back to
 *       {@code /users/sign_in} and the auth flow fails with a 500.</li>
 *   <li>The HTTP client is configured with
 *       {@link org.apache.http.impl.client.LaxRedirectStrategy} so the
 *       {@code POST} can follow the {@code 302} that GitLab issues to the
 *       SAML IdP (the SAML HTTP-Redirect Binding response carrying the
 *       {@code SAMLRequest}). The default redirect strategy does not follow
 *       {@code 302} on {@code POST} per RFC 7231 &sect;6.4.3, which would
 *       cause the IdP redirect to look indistinguishable from the original
 *       {@code 302}-to-{@code /users/sign_in} failure.</li>
 *   <li>The HTTP client uses {@link CookieSpecs#STANDARD} (RFC 6265) for
 *       cookie handling. Apache HttpClient's legacy default cookie spec
 *       silently rejects GitLab's {@code _gitlab_session} {@code Set-Cookie}
 *       header because of how it parses the standard
 *       {@code expires=Mon, dd MMM yyyy HH:mm:ss GMT} attribute. Without
 *       this, the priming session cookie is dropped, the {@code POST} goes
 *       out unauthenticated, and GitLab 302s back to {@code /users/sign_in}
 *       (visually identical to the CSRF failure case).</li>
 * </ol>
 * Auth URIs that do not point at the GitLab server (e.g. a direct POST to
 * an external SAML IdP) skip the CSRF priming step.
 */
public abstract class GitLabSAMLAuthenticator
{
    private static final String SESSION_COOKIE_NAME = "_gitlab_session";
    private static final String SAML_FORM_TAG = "form";
    private static final String CSRF_SOURCE_PATH = "/users/sign_in";
    private static final String CSRF_PARAM_NAME = "authenticity_token";
    private static final String CSRF_META_NAME = "csrf-token";

    private final GitLabAppInfo appInfo;

    protected GitLabSAMLAuthenticator(GitLabAppInfo appInfo)
    {
        this.appInfo = appInfo;
    }

    public Cookie authenticateAndGetSessionCookie()
    {
        CookieStore cookieStore = new BasicCookieStore();
        try (CloseableHttpClient client = createSPNegoHttpClient(cookieStore))
        {
            String responseString;
            URI authURI = buildAuthURI();
            HttpPost authRequest = new HttpPost(authURI);

            // OmniAuth 2.x + omniauth-rails_csrf_protection requires a Rails authenticity_token
            // in the body of POST /users/auth/saml; without it GitLab 302s to /users/sign_in and
            // the auth flow fails. Prime a session + read the token here. Skip for auth URIs
            // that do not target the GitLab server (e.g. POSTing directly to an external IdP).
            if (isOnGitLabServer(authURI))
            {
                String csrfToken = obtainGitLabCsrfToken(client);
                if (csrfToken != null)
                {
                    List<NameValuePair> params = new ArrayList<>(1);
                    params.add(new BasicNameValuePair(CSRF_PARAM_NAME, csrfToken));
                    authRequest.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
                }
            }

            try (CloseableHttpResponse response = client.execute(authRequest))
            {
                int statusCode = response.getStatusLine().getStatusCode();
                switch (statusCode)
                {
                    case HttpStatus.SC_UNAUTHORIZED:
                    case HttpStatus.SC_FORBIDDEN:
                    {
                        // auth failed
                        try
                        {
                            responseString = EntityUtils.toString(response.getEntity());
                        }
                        catch (Exception ignore)
                        {
                            responseString = "(error getting response)";
                        }
                        throw new GitLabAuthFailureException(getUserId(), "Failed to get GitLab session token: " + response.getStatusLine() + "\nResponse:\n" + responseString);
                    }
                    case HttpStatus.SC_BAD_GATEWAY:
                    case HttpStatus.SC_SERVICE_UNAVAILABLE:
                    case HttpStatus.SC_GATEWAY_TIMEOUT:
                    {
                        // access error
                        try
                        {
                            responseString = EntityUtils.toString(response.getEntity());
                        }
                        catch (Exception ignore)
                        {
                            responseString = "(error getting response)";
                        }
                        throw new GitLabAuthAccessException(getUserId(), "Error accessing auth server: " + response.getStatusLine() + "\nResponse:\n" + responseString);
                    }
                    default:
                    {
                        if (isNonSuccessStatusCode(statusCode))
                        {
                            // other error
                            try
                            {
                                responseString = EntityUtils.toString(response.getEntity());
                            }
                            catch (Exception ignore)
                            {
                                responseString = "(error getting response)";
                            }
                            throw new GitLabAuthOtherException(getUserId(), "Error getting GitLab session token: " + response.getStatusLine() + "\nResponse:\n" + responseString);
                        }
                    }
                }
                responseString = EntityUtils.toString(response.getEntity());
            }
            // Parser based on spec found here: https://en.wikipedia.org/wiki/Security_Assertion_Markup_Language
            FormElement samlForm = getFormFromResponse(responseString);
            Connection.Response formResponse;
            try
            {
                formResponse = samlForm.submit().execute();
            }
            catch (Exception e)
            {
                StringBuilder builder = new StringBuilder("Error executing form:\n");
                String eMessage = e.getMessage();
                if (eMessage == null)
                {
                    builder.append(samlForm);
                }
                else
                {
                    builder.append("Form:\n").append(samlForm).append("\nMessage: ").append(eMessage);
                }
                throw new GitLabAuthOtherException(getUserId(), builder.toString(), e);
            }
            if (isNonSuccessStatusCode(formResponse.statusCode()))
            {
                throw new GitLabAuthOtherException(getUserId(), "Error getting token (status " + formResponse.statusCode() + "), response:\n" + formResponse.body());
            }
            String token = formResponse.cookie(SESSION_COOKIE_NAME);
            if (token == null)
            {
                // TODO Consider what to do in this case. good response but no gitlab session token. Might have another form to execute?
                throw new GitLabAuthOtherException(getUserId(), "Could not get token from form response:\n" + formResponse.body());
            }
            return makeSessionCookie(token, this.appInfo.getServerInfo());
        }
        catch (GitLabAuthException e)
        {
            throw e;
        }
        catch (IOException e)
        {
            throw new GitLabAuthAccessException(getUserId(), StringTools.appendThrowableMessageIfPresent("Error getting GitLab session token", e), e);
        }
        catch (Exception e)
        {
            throw new GitLabAuthOtherException(getUserId(), StringTools.appendThrowableMessageIfPresent("Error getting GitLab session token", e), e);
        }
    }

    private boolean isOnGitLabServer(URI authURI)
    {
        if ((authURI == null) || (authURI.getHost() == null))
        {
            return false;
        }
        String gitlabHost = this.appInfo.getServerInfo().getHost();
        return (gitlabHost != null) && gitlabHost.equalsIgnoreCase(authURI.getHost());
    }

    /**
     * Build an SPNego-authenticated {@link CloseableHttpClient} that:
     * <ul>
     *   <li>follows {@code POST} 302 redirects (via {@link LaxRedirectStrategy})
     *       so the SAML HTTP-Redirect Binding response from GitLab is chased
     *       onto the IdP the same way a browser would; and</li>
     *   <li>uses {@link CookieSpecs#STANDARD} (RFC 6265) so GitLab's
     *       {@code _gitlab_session} {@code Set-Cookie} header is not silently
     *       dropped by HttpClient's legacy default cookie spec.</li>
     * </ul>
     * Package-private so integration tests can build an equivalent client
     * without duplicating the configuration.
     */
    static CloseableHttpClient createSPNegoHttpClient(CookieStore cookieStore)
    {
        RequestConfig requestConfig = RequestConfig.custom()
                .setCookieSpec(CookieSpecs.STANDARD)
                .build();
        HttpClientBuilder builder = AuthenticationTools.createHttpClientBuilderForSPNego()
                .setRedirectStrategy(LaxRedirectStrategy.INSTANCE)
                .setDefaultRequestConfig(requestConfig);
        if (cookieStore != null)
        {
            builder.setDefaultCookieStore(cookieStore);
        }
        return builder.build();
    }

    /**
     * Fetch the GitLab sign-in page to (a) prime a {@code _gitlab_session} cookie via SPNego on
     * the shared cookie store and (b) read the Rails {@code authenticity_token} that
     * {@code omniauth-rails_csrf_protection} requires on the body of the subsequent
     * {@code POST /users/auth/saml}. Returns {@code null} if no token can be located in the
     * response, in which case the POST proceeds without one and any failure surfaces with
     * GitLab's own error message.
     *
     * <p>Package-private so integration tests in this package can exercise the GitLab CSRF
     * priming step in isolation from the rest of the SAML flow.</p>
     */
    String obtainGitLabCsrfToken(CloseableHttpClient client) throws IOException
    {
        URI csrfURI;
        try
        {
            csrfURI = this.appInfo.getServerInfo().newURIBuilder().setPath(CSRF_SOURCE_PATH).build();
        }
        catch (URISyntaxException e)
        {
            return null;
        }
        try (CloseableHttpResponse response = client.execute(new HttpGet(csrfURI)))
        {
            int statusCode = response.getStatusLine().getStatusCode();
            if (isNonSuccessStatusCode(statusCode))
            {
                return null;
            }
            String html = EntityUtils.toString(response.getEntity());
            return extractCsrfToken(html);
        }
    }

    // Package-private so unit tests in this package can exercise the parser without hitting the network.
    static String extractCsrfToken(String html)
    {
        if ((html == null) || html.isEmpty())
        {
            return null;
        }
        Document doc = Jsoup.parse(html);
        // Prefer <meta name="csrf-token" content="..."> (always rendered on modern GitLab pages).
        Element meta = doc.select("meta[name=" + CSRF_META_NAME + "]").first();
        if (meta != null)
        {
            String content = meta.attr("content");
            if (!content.isEmpty())
            {
                return content;
            }
        }
        // Fall back to a hidden form input.
        Element input = doc.select("input[name=" + CSRF_PARAM_NAME + "]").first();
        if (input != null)
        {
            String value = input.attr("value");
            if (!value.isEmpty())
            {
                return value;
            }
        }
        return null;
    }

    private boolean isNonSuccessStatusCode(int statusCode)
    {
        return (statusCode / 100) != 2;
    }

    protected abstract URI buildAuthURI();

    private FormElement getFormFromResponse(String responseString)
    {
        try
        {
            Document doc = Jsoup.parse(responseString);
            return (FormElement) doc.getElementsByTag(SAML_FORM_TAG).get(0);
        }
        catch (Exception e)
        {
            throw new GitLabAuthOtherException(getUserId(), "Invalid SAML form, got\n" + responseString, e);
        }
    }

    protected abstract String getUserId();

    protected GitLabAppInfo getAppInfo()
    {
        return this.appInfo;
    }

    static Cookie makeSessionCookie(String sessionToken, GitLabServerInfo serverInfo)
    {
        return new SessionCookie(serverInfo, sessionToken);
    }

    private static class SessionCookie implements ClientCookie
    {
        private final GitLabServerInfo serverInfo;
        private final String token;

        private SessionCookie(GitLabServerInfo serverInfo, String token)
        {
            this.serverInfo = serverInfo;
            this.token = token;
        }

        @Override
        public String getAttribute(String name)
        {
            return DOMAIN_ATTR.equalsIgnoreCase(name) ? getDomain() : null;
        }

        @Override
        public boolean containsAttribute(String name)
        {
            return DOMAIN_ATTR.equalsIgnoreCase(name);
        }

        @Override
        public String getName()
        {
            return SESSION_COOKIE_NAME;
        }

        @Override
        public String getValue()
        {
            return this.token;
        }

        @Override
        public String getComment()
        {
            return null;
        }

        @Override
        public String getCommentURL()
        {
            return null;
        }

        @Override
        public Date getExpiryDate()
        {
            return null;
        }

        @Override
        public boolean isPersistent()
        {
            return false;
        }

        @Override
        public String getDomain()
        {
            return this.serverInfo.getHost();
        }

        @Override
        public String getPath()
        {
            return "/";
        }

        @Override
        public int[] getPorts()
        {
            return null;
        }

        @Override
        public boolean isSecure()
        {
            return true;
        }

        @Override
        public int getVersion()
        {
            return 0;
        }

        @Override
        public boolean isExpired(Date date)
        {
            return false;
        }
    }
}
