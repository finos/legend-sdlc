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

import com.google.inject.servlet.RequestScoped;
import org.finos.legend.sdlc.server.auth.LegendSDLCWebFilter;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.GitLabAppInfo;
import org.finos.legend.sdlc.server.guice.UserContext;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApi.ApiVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response.Status;
import java.net.URI;
import java.util.Objects;

@RequestScoped
public class GitLabUserContext extends UserContext
{
    private static final Logger LOGGER = LoggerFactory.getLogger(GitLabUserContext.class);

    private final GitLabAuthorizerManager authorizerManager;
    private final GitLabAppInfo appInfo;

    private GitLabApi api;

    @Inject
    public GitLabUserContext(HttpServletRequest httpRequest, HttpServletResponse httpResponse, GitLabAuthorizerManager authorizerManager, GitLabAppInfo appInfo)
    {
        super(httpRequest, httpResponse);
        this.authorizerManager = Objects.requireNonNull(authorizerManager);
        this.appInfo = appInfo;
    }

    public void gitLabAuthCallback(String code)
    {
        GitLabSession gitLabSession = getGitLabSession();
        if (gitLabSession.gitLabOAuthCallback(code))
        {
            LegendSDLCWebFilter.setSessionCookie(this.httpResponse, gitLabSession);
        }
    }

    public GitLabApi getGitLabAPI()
    {
        return getGitLabAPI(false);
    }

    public GitLabApi getGitLabAPI(boolean redirectAllowed)
    {
        if (this.api == null)
        {
            GitLabSession gitLabSession = getGitLabSession();
            GitLabToken token = gitLabSession.getGitLabToken();
            if (token == null)
            {
                token = setGitlabTokenForSession(redirectAllowed, gitLabSession);
            }
            else if (gitLabSession.shouldRefreshToken())
            {
                if (gitLabSession.getRefreshToken() != null)
                {
                    try
                    {
                        GitLabTokenResponse tokenResponse = GitLabOAuthAuthenticator.getOAuthTokenFromRefreshToken(gitLabSession.getRefreshToken(), appInfo);
                        if (tokenResponse != null)
                        {
                            gitLabSession.setGitLabToken(tokenResponse.getAccessToken());
                            gitLabSession.setRefreshToken(tokenResponse.getRefreshToken());
                            gitLabSession.setTokenExpiry(tokenResponse.getExpiresInSecs());
                            token = gitLabSession.getGitLabToken();
                            LegendSDLCWebFilter.setSessionCookie(this.httpResponse, gitLabSession);
                        }
                    }
                    catch (Exception e)
                    {
                        LOGGER.warn("Error refreshing token", e);
                        token = setGitlabTokenForSession(redirectAllowed, gitLabSession);
                    }
                }
                else
                {
                    token = setGitlabTokenForSession(redirectAllowed, gitLabSession);
                }
            }
            this.api = new GitLabApi(ApiVersion.V4, this.appInfo.getServerInfo().getGitLabURLString(), token.getTokenType(), token.getToken());
        }
        return this.api;
    }

    private GitLabToken setGitlabTokenForSession(boolean redirectAllowed, GitLabSession gitLabSession)
    {
        GitLabToken token;
        GitLabTokenResponse tokenResponse;
        try
        {
            tokenResponse = authorizerManager.authorize(session, this.appInfo);
        }
        catch (GitLabOAuthAuthenticator.UserInputRequiredException e)
        {
            URI redirectURI = GitLabOAuthAuthenticator.buildAppAuthorizationURI(e.getAppInfo(), this.httpRequest);
            throw new LegendSDLCServerException(redirectURI.toString(), Status.FOUND);
        }
        catch (GitLabAuthFailureException e)
        {
            throw new LegendSDLCServerException(e.getMessage(), Status.FORBIDDEN, e);
        }
        catch (GitLabAuthException e)
        {
            throw new LegendSDLCServerException(e.getMessage(), Status.INTERNAL_SERVER_ERROR, e);
        }
        if (tokenResponse != null)
        {
            gitLabSession.setGitLabToken(tokenResponse.getAccessToken());
            gitLabSession.setRefreshToken(tokenResponse.getRefreshToken());
            gitLabSession.setTokenExpiry(tokenResponse.getExpiresInSecs());
            token = gitLabSession.getGitLabToken();
            LegendSDLCWebFilter.setSessionCookie(this.httpResponse, gitLabSession);
        }
        else if (redirectAllowed)
        {
            URI redirectURI = GitLabOAuthAuthenticator.buildAppAuthorizationURI(this.appInfo, this.httpRequest);
            throw new LegendSDLCServerException(redirectURI.toString(), Status.FOUND);
        }
        else
        {
            throw new LegendSDLCServerException("{\"message\":\"Authorization required\",\"auth_uri\":\"/auth/authorize\"}", Status.FORBIDDEN);
        }
        return token;
    }

    public boolean isUserAuthorized()
    {
        if (this.api == null)
        {
            GitLabSession gitLabSession = getGitLabSession();
            if (gitLabSession.getGitLabToken() == null)
            {
                try
                {
                    GitLabToken token = authorizerManager.authorize(session, this.appInfo).getAccessToken();
                    if (token == null)
                    {
                        return false;
                    }
                    // If we can get the token, then the mode is authorized. But since we have it, we might as well save it.
                    gitLabSession.setGitLabToken(token);
                    LegendSDLCWebFilter.setSessionCookie(this.httpResponse, gitLabSession);
                }
                catch (GitLabAuthFailureException | GitLabOAuthAuthenticator.UserInputRequiredException e)
                {
                    // These exceptions indicate the mode is not yet authorized or that authorization has failed.
                    return false;
                }
            }
        }
        return true;
    }

    public void clearAccessToken()
    {
        this.api = null;
        GitLabSession gitLabSession = getGitLabSession();
        gitLabSession.clearGitLabToken();
        LegendSDLCWebFilter.setSessionCookie(this.httpResponse, gitLabSession);
    }

    private GitLabSession getGitLabSession()
    {
        return (GitLabSession) this.session;
    }
}
