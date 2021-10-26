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
import org.finos.legend.sdlc.server.gitlab.mode.GitLabModeInfo;
import org.finos.legend.sdlc.server.tools.StringTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.security.auth.Subject;
import javax.servlet.http.HttpServletRequest;

class GitLabOAuthAuthenticator
{
    private static final Logger LOGGER = LoggerFactory.getLogger(GitLabOAuthAuthenticator.class);

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String OAUTH_AUTH_PATH = "/oauth/authorize";
    private static final String OAUTH_TOKEN_PATH = "/oauth/token";

    private static final String ACCESS_TOKEN_PARAM = "access_token";
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
    private static final String TOKEN_RESPONSE_TYPE = "token";
    private static final String BEARER_TOKEN_TYPE = "bearer";

    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy()
    {
        @Override
        public boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException
        {
            return !locationStartsWithRedirectURI(getLocationHeaderValue(response)) && super.isRedirected(request, response, context);
        }
    };

    private final GitLabModeInfo modeInfo;

    private GitLabOAuthAuthenticator(GitLabModeInfo modeInfo)
    {
        this.modeInfo = modeInfo;
    }

    String getOAuthToken(Subject subject)
    {
        Cookie sessionCookie = GitLabSAMLAuthenticator.authenticateAndGetSessionCookie(this.modeInfo, subject);
        CookieStore cookieStore = new BasicCookieStore();
        cookieStore.addCookie(sessionCookie);

        try (CloseableHttpClient client = HttpClientBuilder.create().setDefaultCookieStore(cookieStore).setRedirectStrategy(this.redirectStrategy).build())
        {
            String state = getRandomState();
            URI authURI = buildAuthURI(state);
            try (CloseableHttpResponse response = client.execute(new HttpGet(authURI)))
            {
                // We expect a redirect to the redirect URI plus a fragment containing the access token
                int statusCode = response.getStatusLine().getStatusCode();

                switch (statusCode)
                {
                    case HttpStatus.SC_OK:
                    {
                        // If the response is OK (200), the user has probably not authorized the app
                        throw new UserInputRequiredException(this.modeInfo);
                    }
                    case HttpStatus.SC_UNAUTHORIZED:
                    case HttpStatus.SC_FORBIDDEN:
                    {
                        // This means that the user has successfully authenticated, but the request for an OAuth token
                        // has been denied (401 or 403). This could mean the user has enabled 2 factor authentication.
                        String message = "Failed to get OAuth token after successful authentication (perhaps 2 factor authentication is enabled); status: " + response.getStatusLine() + "; response: " + readResponseEntityForAuthError(response);
                        LOGGER.warn(message);
                        throw new GitLabAuthFailureException(this.modeInfo.getMode(), message);
                    }
                    case HttpStatus.SC_BAD_GATEWAY:
                    case HttpStatus.SC_SERVICE_UNAVAILABLE:
                    case HttpStatus.SC_GATEWAY_TIMEOUT:
                    {
                        // This means that there was some issue accessing the auth server.
                        String message = "Error accessing auth server; status: " + response.getStatusLine() + "; response: " + readResponseEntityForAuthError(response);
                        LOGGER.warn(message);
                        throw new GitLabAuthAccessException(this.modeInfo.getMode(), message);
                    }
                    default:
                    {
                        // Check that this is a redirect
                        if (!isRedirectStatus(statusCode))
                        {
                            String message = "Error getting OAuth token - expected redirect to " + getAppRedirectURI() + "; got status: " + response.getStatusLine() + "; response: " + readResponseEntityForAuthError(response);
                            LOGGER.warn(message);
                            throw new GitLabAuthOtherException(this.modeInfo.getMode(), message);
                        }
                    }
                }

                String location = getLocationHeaderValue(response);

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
                    throw new GitLabAuthOtherException(this.modeInfo.getMode(), message.toString());
                }

                // Get the access token from the location
                return getAccessTokenFromLocation(location, state);
            }
        }
        catch (UserInputRequiredException | GitLabAuthException e)
        {
            throw e;
        }
        catch (IOException e)
        {
            throw new GitLabAuthAccessException(this.modeInfo.getMode(), StringTools.appendThrowableMessageIfPresent("Error getting OAuth token", e), e);
        }
        catch (Exception e)
        {
            throw new GitLabAuthOtherException(this.modeInfo.getMode(), StringTools.appendThrowableMessageIfPresent("Error getting OAuth token", e), e);
        }
    }

    String getOAuthTokenFromAuthCode(String code)
    {
        try (CloseableHttpClient client = HttpClientBuilder.create().build())
        {
            URI uri = this.modeInfo.getServerInfo().newURIBuilder().setPath(OAUTH_TOKEN_PATH).build();
            HttpPost post = new HttpPost(uri);
            HttpEntity entity = EntityBuilder.create()
                    .setParameters(
                            new BasicNameValuePair(APP_ID_PARAM, this.modeInfo.getAppInfo().getAppId()),
                            new BasicNameValuePair(APP_SECRET_PARAM, this.modeInfo.getAppInfo().getAppSecret()),
                            new BasicNameValuePair(CODE_PARAM, code),
                            new BasicNameValuePair(GRANT_TYPE_PARAM, AUTHORIZATION_CODE_GRANT_TYPE),
                            new BasicNameValuePair(APP_REDIRECT_URI_PARAM, this.modeInfo.getAppInfo().getAppRedirectURI()))
                    .build();
            post.setEntity(entity);
            String responseString;
            try (CloseableHttpResponse response = client.execute(post))
            {
                StatusLine statusLine = response.getStatusLine();
                if (!isSuccessStatus(statusLine.getStatusCode()))
                {
                    throw new GitLabAuthOtherException(this.modeInfo.getMode(), "Got status: " + statusLine + "; response: " + readResponseEntityForAuthError(response));
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
                throw new GitLabAuthOtherException(this.modeInfo.getMode(), "Could not get access token from URI " + uri + ": error parsing response: " + responseString, e);
            }
            Object accessToken = responseObject.get(ACCESS_TOKEN_PARAM);
            if (!(accessToken instanceof String))
            {
                throw new GitLabAuthOtherException(this.modeInfo.getMode(), "Could not get access token from URI " + uri + ": no access token in response: " + responseString);
            }
            Object tokenType = responseObject.get(TOKEN_TYPE_PARAM);
            if (!(tokenType instanceof String) || !BEARER_TOKEN_TYPE.equalsIgnoreCase((String) tokenType))
            {
                throw new GitLabAuthOtherException(this.modeInfo.getMode(), "Could not get access token from URI " + uri + ": wrong token type in response (expected " + BEARER_TOKEN_TYPE + "): " + responseString);
            }
            return (String) accessToken;
        }
        catch (GitLabAuthException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new GitLabAuthOtherException(this.modeInfo.getMode(), StringTools.appendThrowableMessageIfPresent("Error getting OAuth token", e), e);
        }
    }

    private boolean isSuccessStatus(int statusCode)
    {
        return (200 <= statusCode) && (statusCode < 300);
    }

    private boolean isRedirectStatus(int statusCode)
    {
        return (301 <= statusCode) && (statusCode < 400);
    }

    private String readResponseEntityForAuthError(HttpResponse response)
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
        return this.modeInfo.getAppInfo().getAppRedirectURI();
    }

    private URI buildAuthURI(String state)
    {
        return buildAuthURI(this.modeInfo, state, TOKEN_RESPONSE_TYPE);
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

    private String getAccessTokenFromLocation(String location, String expectedState)
    {
        int redirectURILength = getAppRedirectURI().length();
        char nextChar = location.charAt(redirectURILength);
        if (nextChar != '#')
        {
            throw new GitLabAuthOtherException(this.modeInfo.getMode(), "Could not get access token from redirect URI " + location + ": expected URI of the form " + getAppRedirectURI() + "#...");
        }

        List<NameValuePair> parameters;
        try
        {
            parameters = URLEncodedUtils.parse(location.substring(redirectURILength + 1), Charset.defaultCharset());
        }
        catch (Exception e)
        {
            throw new GitLabAuthOtherException(this.modeInfo.getMode(), "Could not get access token from redirect URI " + location + ": could not parse parameters from fragment", e);
        }

        String accessToken = null;
        boolean foundState = false;
        for (NameValuePair nvp : parameters)
        {
            switch (nvp.getName())
            {
                case ACCESS_TOKEN_PARAM:
                {
                    accessToken = nvp.getValue();
                    break;
                }
                case STATE_PARAM:
                {
                    if (!Objects.equals(expectedState, nvp.getValue()))
                    {
                        throw new GitLabAuthOtherException(this.modeInfo.getMode(), "Could not get access token from redirect URI " + location + ": expected state " + expectedState + ", found " + nvp.getValue());
                    }
                    foundState = true;
                    break;
                }
                case TOKEN_TYPE_PARAM:
                {
                    if (!BEARER_TOKEN_TYPE.equalsIgnoreCase(nvp.getValue()))
                    {
                        throw new GitLabAuthOtherException(this.modeInfo.getMode(), "Could not get access token from redirect URI " + location + ": expected token type " + BEARER_TOKEN_TYPE + ", found " + nvp.getValue());
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
            throw new GitLabAuthOtherException(this.modeInfo.getMode(), "Could not get access token from redirect URI " + location + ": expected state " + expectedState + ", found no state");
        }
        if (accessToken == null)
        {
            throw new GitLabAuthOtherException(this.modeInfo.getMode(), "Could not get access token from redirect URI " + location + ": found no access token");
        }
        return accessToken;
    }

    private String getRandomState()
    {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        return Hex.encodeHexString(bytes);
    }

    static GitLabOAuthAuthenticator newAuthenticator(GitLabModeInfo modeInfo)
    {
        return new GitLabOAuthAuthenticator(modeInfo);
    }

    static URI buildAppAuthorizationURI(GitLabModeInfo modeInfo, HttpServletRequest httpRequest)
    {
        // Build state
        TokenBuilder stateBuilder = Token.newBuilder();

        // GitLab mode
        stateBuilder.putString(modeInfo.getMode().name());

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
        return buildAuthURI(modeInfo, stateBuilder.toTokenString(), CODE_RESPONSE_TYPE);
    }

    private static URI buildAuthURI(GitLabModeInfo modeInfo, String state, String responseType)
    {
        URIBuilder builder = modeInfo.getServerInfo().newURIBuilder()
                .setPath(OAUTH_AUTH_PATH)
                .addParameter(APP_ID_PARAM, modeInfo.getAppInfo().getAppId())
                .addParameter(APP_REDIRECT_URI_PARAM, modeInfo.getAppInfo().getAppRedirectURI())
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
        private final GitLabModeInfo modeInfo;

        private UserInputRequiredException(GitLabModeInfo modeInfo)
        {
            this.modeInfo = modeInfo;
        }

        GitLabModeInfo getModeInfo()
        {
            return this.modeInfo;
        }
    }
}
