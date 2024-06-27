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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.binary.Hex;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.ProtocolException;
import org.apache.http.StatusLine;
import org.apache.http.client.CookieStore;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.eclipse.collections.impl.utility.StringIterate;
import org.finos.legend.sdlc.server.auth.Token;
import org.finos.legend.sdlc.server.auth.Token.TokenBuilder;
import org.finos.legend.sdlc.server.gitlab.GitLabAppInfo;
import org.finos.legend.sdlc.server.tools.StringTools;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class GitLabOAuthAuthenticator
{
    private static final Logger LOGGER = LoggerFactory.getLogger(GitLabOAuthAuthenticator.class);

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String OAUTH_AUTH_PATH = "/oauth/authorize";
    private static final String OAUTH_TOKEN_PATH = "/oauth/token";

    private static final String ACCESS_TOKEN_PARAM = "access_token";
    private static final String REFRESH_TOKEN_PARAM = "refresh_token";
    private static final String EXPIRES_IN_PARAM = "expires_in";
    private static final String APP_ID_PARAM = "client_id";
    private static final String APP_REDIRECT_URI_PARAM = "redirect_uri";
    private static final String APP_SECRET_PARAM = "client_secret";
    private static final String CODE_PARAM = "code";
    private static final String GRANT_TYPE_PARAM = "grant_type";
    private static final String RESPONSE_TYPE_PARAM = "response_type";
    private static final String STATE_PARAM = "state";
    private static final String TOKEN_TYPE_PARAM = "token_type";

    private static final String AUTHORIZATION_CODE_GRANT_TYPE = "authorization_code";
    private static final String CODE_RESPONSE_TYPE = "code";
    private static final String BEARER_TOKEN_TYPE = "bearer";

    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy()
    {
        @Override
        public boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException
        {
            return !locationStartsWithRedirectURI(getLocationHeaderValue(response)) && super.isRedirected(request, response, context);
        }
    };

    private final GitLabAppInfo appInfo;

    private GitLabOAuthAuthenticator(GitLabAppInfo appInfo)
    {
        this.appInfo = appInfo;
    }

    public GitLabTokenResponse getOAuthTokenResponseFromSessionCookie(Cookie sessionCookie)
    {
        CookieStore cookieStore = new BasicCookieStore();
        cookieStore.addCookie(sessionCookie);

        try (CloseableHttpClient client = HttpClientBuilder.create().setDefaultCookieStore(cookieStore).setRedirectStrategy(this.redirectStrategy).build())
        {
            String state = getRandomState();
            URI authURI = buildAuthURI(this.appInfo, state, CODE_RESPONSE_TYPE);
            try (CloseableHttpResponse response = client.execute(new HttpGet(authURI)))
            {
                // We expect a redirect to the redirect URI plus a fragment containing the access token
                int statusCode = response.getStatusLine().getStatusCode();
                String location;

                switch (statusCode)
                {
                    case HttpStatus.SC_OK:
                    {
                        Optional<String> redirectUrlFromResponse = getRedirectUrlFromResponse(response);
                        if (redirectUrlFromResponse.isPresent())
                        {
                            location = redirectUrlFromResponse.get();
                        }
                        else
                        {
                            // If the response is not a redirect page, the user has probably not authorized the app
                            throw new UserInputRequiredException(this.appInfo);
                        }
                        break;
                    }
                    case HttpStatus.SC_UNAUTHORIZED:
                    case HttpStatus.SC_FORBIDDEN:
                    {
                        // This means that the user has successfully authenticated, but the request for an OAuth token
                        // has been denied (401 or 403). This could mean the user has enabled 2-factor authentication.
                        String message = "Failed to get OAuth token after successful authentication (perhaps 2 factor authentication is enabled); status: " + response.getStatusLine() + "; response: " + readResponseEntityForAuthError(response);
                        LOGGER.warn(message);
                        throw new GitLabAuthFailureException(message);
                    }
                    case HttpStatus.SC_BAD_GATEWAY:
                    case HttpStatus.SC_SERVICE_UNAVAILABLE:
                    case HttpStatus.SC_GATEWAY_TIMEOUT:
                    {
                        // This means that there was some issue accessing the auth server.
                        String message = "Error accessing auth server; status: " + response.getStatusLine() + "; response: " + readResponseEntityForAuthError(response);
                        LOGGER.warn(message);
                        throw new GitLabAuthAccessException(message);
                    }
                    default:
                    {
                        // Check that this is a redirect
                        if (!isRedirectStatus(statusCode))
                        {
                            String message = "Error getting OAuth token - expected redirect to " + getAppRedirectURI() + "; got status: " + response.getStatusLine() + "; response: " + readResponseEntityForAuthError(response);
                            LOGGER.warn(message);
                            throw new GitLabAuthOtherException(message);
                        }
                        location = getLocationHeaderValue(response);
                    }
                }

                // Check that the location starts with the expected redirect URL
                if (!locationStartsWithRedirectURI(location))
                {
                    StringBuilder message = new StringBuilder("Error getting OAuth token - expected redirect to ");
                    message.append(getAppRedirectURI());
                    message.append(", got ");
                    if (location == null)
                    {
                        message.append("no redirect location");
                    }
                    else
                    {
                        message.append("redirected to ").append(location);
                    }
                    message.append(" (").append(response.getStatusLine()).append(')');
                    throw new GitLabAuthOtherException(message.toString());
                }

                // Get the code from the location
                String code = getCodeFromLocation(location, state);

                // get access token from code
                return getOAuthTokenResponseFromAuthCode(code);
            }
        }
        catch (UserInputRequiredException | GitLabAuthException e)
        {
            throw e;
        }
        catch (IOException e)
        {
            throw new GitLabAuthAccessException(StringTools.appendThrowableMessageIfPresent("Error getting OAuth token", e), e);
        }
        catch (Exception e)
        {
            throw new GitLabAuthOtherException(StringTools.appendThrowableMessageIfPresent("Error getting OAuth token", e), e);
        }
    }

    private Optional<String> getRedirectUrlFromResponse(CloseableHttpResponse response)
    {
        try
        {
            String responseString = EntityUtils.toString(response.getEntity());
            Document doc = Jsoup.parse(responseString);
            Element header = doc.getElementsByTag("h3").get(0);
            if ("Redirecting".equals(header.text()))
            {
                Element anchor = doc.getElementsByTag("a").get(0);
                String redirectUrl = anchor.attr("href");
                if (!StringIterate.isEmptyOrWhitespace(redirectUrl))
                {
                    return Optional.of(redirectUrl);
                }
            }
            return Optional.empty();
        }
        catch (Exception e)
        {
            return Optional.empty();
        }
    }

    GitLabTokenResponse getOAuthTokenResponseFromAuthCode(String code)
    {
        HttpEntity entity = EntityBuilder.create()
                .setParameters(
                        new BasicNameValuePair(APP_ID_PARAM, this.appInfo.getAppId()),
                        new BasicNameValuePair(APP_SECRET_PARAM, this.appInfo.getAppSecret()),
                        new BasicNameValuePair(CODE_PARAM, code),
                        new BasicNameValuePair(GRANT_TYPE_PARAM, AUTHORIZATION_CODE_GRANT_TYPE),
                        new BasicNameValuePair(APP_REDIRECT_URI_PARAM, this.appInfo.getAppRedirectURI()))
                .build();
        return executeAndRetrieveTokenResponse(entity, this.appInfo);
    }

    public static GitLabTokenResponse getOAuthTokenFromRefreshToken(String refreshToken, GitLabAppInfo appInfo)
    {
        HttpEntity entity = EntityBuilder.create()
                .setParameters(
                        new BasicNameValuePair(APP_ID_PARAM, appInfo.getAppId()),
                        new BasicNameValuePair(APP_SECRET_PARAM, appInfo.getAppSecret()),
                        new BasicNameValuePair(REFRESH_TOKEN_PARAM, refreshToken),
                        new BasicNameValuePair(GRANT_TYPE_PARAM, REFRESH_TOKEN_PARAM),
                        new BasicNameValuePair(APP_REDIRECT_URI_PARAM, appInfo.getAppRedirectURI()))
                .build();
        return executeAndRetrieveTokenResponse(entity, appInfo);
    }

    private static GitLabTokenResponse executeAndRetrieveTokenResponse(HttpEntity entity, GitLabAppInfo appInfo)
    {
        try (CloseableHttpClient client = HttpClientBuilder.create().build())
        {
            URI uri = appInfo.getServerInfo().newURIBuilder().setPath(OAUTH_TOKEN_PATH).build();
            HttpPost post = new HttpPost(uri);
            post.setEntity(entity);
            String responseString;
            try (CloseableHttpResponse response = client.execute(post))
            {
                StatusLine statusLine = response.getStatusLine();
                if (!isSuccessStatus(statusLine.getStatusCode()))
                {
                    throw new GitLabAuthOtherException("Got status: " + statusLine + "; response: " + readResponseEntityForAuthError(response));
                }
                responseString = EntityUtils.toString(response.getEntity());
            }

            Map<?, ?> responseObject;
            try
            {
                responseObject = JSON.readValue(responseString, Map.class);
            }
            catch (Exception e)
            {
                throw new GitLabAuthOtherException("Could not get access token from URI " + uri + ": error parsing response: " + responseString, e);
            }
            Object accessToken = responseObject.get(ACCESS_TOKEN_PARAM);
            if (!(accessToken instanceof String))
            {
                throw new GitLabAuthOtherException("Could not get access token from URI " + uri + ": no access token in response: " + responseString);
            }
            Object newRefreshToken = responseObject.get(REFRESH_TOKEN_PARAM);
            if (!(newRefreshToken instanceof String))
            {
                throw new GitLabAuthOtherException("Could not get refresh token from URI " + uri + ": no refresh token in response: " + responseString);
            }
            Object expiresIn = responseObject.get(EXPIRES_IN_PARAM);

            Object tokenType = responseObject.get(TOKEN_TYPE_PARAM);
            if (!(tokenType instanceof String) || !BEARER_TOKEN_TYPE.equalsIgnoreCase((String) tokenType))
            {
                throw new GitLabAuthOtherException("Could not get access token from URI " + uri + ": wrong token type in response (expected " + BEARER_TOKEN_TYPE + "): " + responseString);
            }
            return new GitLabTokenResponse((String) accessToken, (String) newRefreshToken, (Integer) expiresIn);
        }
        catch (GitLabAuthException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new GitLabAuthOtherException(StringTools.appendThrowableMessageIfPresent("Error getting OAuth token", e), e);
        }
    }

    private static boolean isSuccessStatus(int statusCode)
    {
        return (200 <= statusCode) && (statusCode < 300);
    }

    private boolean isRedirectStatus(int statusCode)
    {
        return (301 <= statusCode) && (statusCode < 400);
    }

    private static String readResponseEntityForAuthError(HttpResponse response)
    {
        try
        {
            String responseString = EntityUtils.toString(response.getEntity());
            return StringIterate.isEmptyOrWhitespace(responseString) ? "<empty response>" : responseString;
        }
        catch (Exception e)
        {
            LOGGER.warn("Error getting response during authorization (status {})", response.getStatusLine(), e);
            return "<error getting response>";
        }
    }

    private String getAppRedirectURI()
    {
        return this.appInfo.getAppRedirectURI();
    }

    private String getLocationHeaderValue(HttpResponse response)
    {
        Header locationHeader = response.getFirstHeader("location");
        return (locationHeader == null) ? null : locationHeader.getValue();
    }

    private boolean locationStartsWithRedirectURI(String location)
    {
        return (location != null) && location.startsWith(getAppRedirectURI());
    }

    private String getCodeFromLocation(String location, String expectedState)
    {
        int redirectURILength = getAppRedirectURI().length();
        char nextChar = location.charAt(redirectURILength);
        if (nextChar != '#')
        {
            throw new GitLabAuthOtherException("Could not get access token from redirect URI " + location + ": expected URI of the form " + getAppRedirectURI() + "#...");
        }

        List<NameValuePair> parameters;
        try
        {
            parameters = URLEncodedUtils.parse(location.substring(redirectURILength + 1), Charset.defaultCharset());
        }
        catch (Exception e)
        {
            throw new GitLabAuthOtherException("Could not get access token from redirect URI " + location + ": could not parse parameters from fragment", e);
        }

        String code = null;
        boolean foundState = false;
        for (NameValuePair nvp : parameters)
        {
            switch (nvp.getName())
            {
                case CODE_PARAM:
                {
                    code = nvp.getValue();
                    break;
                }
                case STATE_PARAM:
                {
                    if (!Objects.equals(expectedState, nvp.getValue()))
                    {
                        throw new GitLabAuthOtherException("Could not get access token from redirect URI " + location + ": expected state " + expectedState + ", found " + nvp.getValue());
                    }
                    foundState = true;
                    break;
                }
                case TOKEN_TYPE_PARAM:
                {
                    if (!BEARER_TOKEN_TYPE.equalsIgnoreCase(nvp.getValue()))
                    {
                        throw new GitLabAuthOtherException("Could not get access token from redirect URI " + location + ": expected token type " + BEARER_TOKEN_TYPE + ", found " + nvp.getValue());
                    }
                    break;
                }
                default:
                {
                    // nothing for other parameters
                }
            }
        }
        if (!foundState && (expectedState != null))
        {
            throw new GitLabAuthOtherException("Could not get access token from redirect URI " + location + ": expected state " + expectedState + ", found no state");
        }
        if (code == null)
        {
            throw new GitLabAuthOtherException("Could not get code from redirect URI " + location + ": found no code");
        }
        return code;
    }

    private String getRandomState()
    {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        return Hex.encodeHexString(bytes);
    }

    public static GitLabOAuthAuthenticator newAuthenticator(GitLabAppInfo appInfo)
    {
        return new GitLabOAuthAuthenticator(appInfo);
    }

    static URI buildAppAuthorizationURI(GitLabAppInfo appInfo, HttpServletRequest httpRequest)
    {
        // Build state
        TokenBuilder stateBuilder = Token.newBuilder();

        // Request Method
        stateBuilder.putString(httpRequest.getMethod());

        // Request URL
        StringBuffer urlBuilder = httpRequest.getRequestURL();
        String requestQueryString = httpRequest.getQueryString();
        if (requestQueryString != null)
        {
            urlBuilder.append('?').append(requestQueryString);
        }
        stateBuilder.putString(urlBuilder.toString());

        // Build URI
        return buildAuthURI(appInfo, stateBuilder.toTokenString(), CODE_RESPONSE_TYPE);
    }

    private static URI buildAuthURI(GitLabAppInfo appInfo, String state, String responseType)
    {
        URIBuilder builder = appInfo.getServerInfo().newURIBuilder()
            .setPath(OAUTH_AUTH_PATH)
            .addParameter(APP_ID_PARAM, appInfo.getAppId())
            .addParameter(APP_REDIRECT_URI_PARAM, appInfo.getAppRedirectURI())
            .addParameter(RESPONSE_TYPE_PARAM, responseType);
        if (state != null)
        {
            builder.addParameter(STATE_PARAM, state);
        }
        try
        {
            return builder.build();
        }
        catch (URISyntaxException e)
        {
            throw new RuntimeException("Error building OAuth URI: " + builder.toString(), e);
        }
    }

    static class UserInputRequiredException extends RuntimeException
    {
        private final GitLabAppInfo appInfo;

        private UserInputRequiredException(GitLabAppInfo appInfo)
        {
            this.appInfo = appInfo;
        }

        GitLabAppInfo getAppInfo()
        {
            return this.appInfo;
        }
    }
}
