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
import org.apache.http.client.CookieStore;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.cookie.ClientCookie;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.finos.legend.sdlc.server.gitlab.GitLabServerInfo;
import org.finos.legend.sdlc.server.gitlab.mode.GitLabMode;
import org.finos.legend.sdlc.server.gitlab.mode.GitLabModeInfo;
import org.finos.legend.sdlc.server.tools.AuthenticationTools;
import org.finos.legend.sdlc.server.tools.StringTools;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.FormElement;

import javax.security.auth.Subject;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.PrivilegedAction;
import java.util.Date;

class GitLabSAMLAuthenticator
{
    private static final String SAML_AUTH_PATH = "/users/auth/saml";
    private static final String SESSION_COOKIE_NAME = "_gitlab_session";
    private static final String SAML_FORM_TAG = "form";

    private final GitLabModeInfo modeInfo;
    private final Subject subject;

    private GitLabSAMLAuthenticator(GitLabModeInfo modeInfo, Subject subject)
    {
        this.modeInfo = modeInfo;
        this.subject = subject;
    }

    private Cookie authenticateAndGetSessionCookie()
    {
        try
        {
            return Subject.doAs(this.subject, (PrivilegedAction<Cookie>)this::doAuthenticateAndGetSessionCookie);
        }
        catch (GitLabAuthException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new GitLabAuthOtherException(getUserKerberosId(), getGitLabMode(), "Error getting GitLab session token", e);
        }
    }

    private Cookie doAuthenticateAndGetSessionCookie()
    {
        CookieStore cookieStore = new BasicCookieStore();
        try (CloseableHttpClient client = AuthenticationTools.createHttpClientForSPNego(cookieStore))
        {
            String responseString;
            URI authURI = buildAuthURI();
            try (CloseableHttpResponse response = client.execute(new HttpGet(authURI)))
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
                        throw new GitLabAuthFailureException(getUserKerberosId(), getGitLabMode(), "Failed to get GitLab session token: " + response.getStatusLine() + "\nResponse:\n" + responseString);
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
                        throw new GitLabAuthAccessException(getUserKerberosId(), getGitLabMode(), "Error accessing auth server: " + response.getStatusLine() + "\nResponse:\n" + responseString);
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
                            throw new GitLabAuthOtherException(getUserKerberosId(), getGitLabMode(), "Error getting GitLab session token: " + response.getStatusLine() + "\nResponse:\n" + responseString);
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
                throw new GitLabAuthOtherException(getUserKerberosId(), getGitLabMode(), builder.toString(), e);
            }
            if (isNonSuccessStatusCode(formResponse.statusCode()))
            {
                throw new GitLabAuthOtherException(getUserKerberosId(), getGitLabMode(), "Error getting token (status " + formResponse.statusCode() + "), response:\n" + formResponse.body());
            }
            String token = formResponse.cookie(SESSION_COOKIE_NAME);
            if (token == null)
            {
                // TODO Consider what to do in this case. good response but no gitlab session token. Might have another form to execute?
                throw new GitLabAuthOtherException(getUserKerberosId(), getGitLabMode(), "Could not get token from form response:\n" + formResponse.body());
            }
            return makeSessionCookie(token, this.modeInfo.getServerInfo());
        }
        catch (GitLabAuthException e)
        {
            throw e;
        }
        catch (IOException e)
        {
            throw new GitLabAuthAccessException(getUserKerberosId(), getGitLabMode(), StringTools.appendThrowableMessageIfPresent("Error getting GitLab session token", e), e);
        }
        catch (Exception e)
        {
            throw new GitLabAuthOtherException(getUserKerberosId(), getGitLabMode(), StringTools.appendThrowableMessageIfPresent("Error getting GitLab session token", e), e);
        }
    }

    private boolean isNonSuccessStatusCode(int statusCode)
    {
        return (statusCode / 100) != 2;
    }

    private URI buildAuthURI()
    {
        URIBuilder builder = this.modeInfo.getServerInfo().newURIBuilder().setPath(SAML_AUTH_PATH);
        try
        {
            return builder.build();
        }
        catch (URISyntaxException e)
        {
            throw new RuntimeException("Error building SAML authentication URI: " + builder.toString(), e);
        }
    }

    private FormElement getFormFromResponse(String responseString)
    {
        try
        {
            Document doc = Jsoup.parse(responseString);
            return (FormElement) doc.getElementsByTag(SAML_FORM_TAG).get(0);
        }
        catch (Exception e)
        {
            throw new GitLabAuthOtherException(getUserKerberosId(), getGitLabMode(), "Invalid SAML form, got\n" + responseString, e);
        }
    }

    private String getUserKerberosId()
    {
        return AuthenticationTools.getKerberosIdFromSubject(this.subject);
    }

    private GitLabMode getGitLabMode()
    {
        return this.modeInfo.getMode();
    }

    static Cookie makeSessionCookie(String sessionToken, GitLabServerInfo serverInfo)
    {
        return new SessionCookie(serverInfo, sessionToken);
    }

    static Cookie authenticateAndGetSessionCookie(GitLabModeInfo modeInfo, Subject subject)
    {
        return new GitLabSAMLAuthenticator(modeInfo, subject).authenticateAndGetSessionCookie();
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
