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

package org.finos.legend.sdlc.backend.gitlab.auth;

import org.finos.legend.sdlc.backend.api.spi.AuthorizationRequiredException;
import org.finos.legend.sdlc.backend.api.spi.BackendSessionContext;
import org.finos.legend.sdlc.error.LegendSDLCException;
import org.finos.legend.sdlc.backend.gitlab.GitLabAppInfo;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApi.ApiVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * The GitLab backend's per-session view of a user: the lazily built {@link GitLabApi} and the token life cycle
 * (acquisition through the authorizer chain, refresh, persistence through the host's session state store). The
 * framework-free successor of the servlet-bound user context: identity and auth material come from the
 * {@link BackendSessionContext}, interactive authorization crosses back to the host as
 * {@link AuthorizationRequiredException}.
 */
public class GitLabUserContext
{
    private static final Logger LOGGER = LoggerFactory.getLogger(GitLabUserContext.class);

    private final BackendSessionContext sessionContext;
    private final GitLabAuthorizerManager authorizerManager;
    private final GitLabAppInfo appInfo;
    private final GitLabTokenManager tokenManager;

    private GitLabApi api;

    public GitLabUserContext(BackendSessionContext sessionContext, GitLabAuthorizerManager authorizerManager, GitLabAppInfo appInfo)
    {
        this.sessionContext = Objects.requireNonNull(sessionContext, "sessionContext may not be null");
        this.authorizerManager = Objects.requireNonNull(authorizerManager, "authorizerManager may not be null");
        this.appInfo = appInfo;
        this.tokenManager = GitLabTokenManager.newTokenManager(appInfo, sessionContext.getStateStore());
        harvestTokenIfAbsent();
    }

    public String getCurrentUser()
    {
        return this.sessionContext.getUserId();
    }

    public BackendSessionContext getSessionContext()
    {
        return this.sessionContext;
    }

    public void gitLabAuthCallback(String code)
    {
        this.tokenManager.gitLabOAuthCallback(code);
    }

    public GitLabApi getGitLabAPI()
    {
        if (this.api == null)
        {
            GitLabToken token = this.tokenManager.getGitLabToken();
            if (token == null)
            {
                token = authorizeSession();
            }
            else if (this.tokenManager.shouldRefreshToken())
            {
                if (this.tokenManager.getRefreshToken() != null)
                {
                    try
                    {
                        LOGGER.debug("Refreshing token for user: {}", getCurrentUser());
                        GitLabTokenResponse tokenResponse = GitLabOAuthAuthenticator.getOAuthTokenFromRefreshToken(this.tokenManager.getRefreshToken(), this.appInfo);
                        if (tokenResponse != null)
                        {
                            this.tokenManager.setTokenResponse(tokenResponse);
                            token = this.tokenManager.getGitLabToken();
                        }
                    }
                    catch (Exception e)
                    {
                        LOGGER.warn("Error refreshing token", e);
                        token = authorizeSession();
                    }
                }
                else
                {
                    token = authorizeSession();
                }
            }
            this.api = new GitLabApi(ApiVersion.V4, this.appInfo.getServerInfo().getGitLabURLString(), token.getTokenType(), token.getToken());
        }
        return this.api;
    }

    public boolean isUserAuthorized()
    {
        if (this.tokenManager.shouldRefreshToken())
        {
            return false;
        }
        if ((this.api == null) && (this.tokenManager.getGitLabToken() == null))
        {
            try
            {
                GitLabTokenResponse tokenResponse = this.authorizerManager.authorize(this.sessionContext, this.appInfo);
                if ((tokenResponse == null) || (tokenResponse.getAccessToken() == null))
                {
                    return false;
                }
                // If we can get the token, then the session is authorized. But since we have it, we might as well save it.
                this.tokenManager.setTokenResponse(tokenResponse);
            }
            catch (GitLabAuthFailureException | GitLabOAuthAuthenticator.UserInputRequiredException e)
            {
                // These exceptions indicate the session is not yet authorized or that authorization has failed.
                return false;
            }
        }
        return true;
    }

    public void clearAccessToken()
    {
        this.api = null;
        this.tokenManager.clearGitLabToken();
    }

    /**
     * For test setup: seed the session with a GitLab token acquired out of band.
     *
     * @param token GitLab token
     */
    public void setGitLabToken(GitLabToken token)
    {
        this.api = null;
        this.tokenManager.setGitLabToken(token);
        this.tokenManager.setTokenExpiry(0L);
    }

    private GitLabToken authorizeSession()
    {
        GitLabTokenResponse tokenResponse;
        try
        {
            tokenResponse = this.authorizerManager.authorize(this.sessionContext, this.appInfo);
        }
        catch (GitLabOAuthAuthenticator.UserInputRequiredException e)
        {
            throw new AuthorizationRequiredException(GitLabOAuthAuthenticator.buildAppAuthorizationURI(e.getAppInfo()));
        }
        catch (GitLabAuthFailureException e)
        {
            throw new LegendSDLCException(e.getMessage(), 403, e);
        }
        catch (GitLabAuthException e)
        {
            throw new LegendSDLCException(e.getMessage(), 500, e);
        }
        if (tokenResponse == null)
        {
            throw new AuthorizationRequiredException(GitLabOAuthAuthenticator.buildAppAuthorizationURI(this.appInfo));
        }
        this.tokenManager.setTokenResponse(tokenResponse);
        return this.tokenManager.getGitLabToken();
    }

    /**
     * The former session classes harvested OIDC/PAT auth material into the token state at session construction;
     * the harvest authorizers do it here, when the persisted state has no token yet. Interaction-free: only
     * material the host already holds is consulted.
     */
    private void harvestTokenIfAbsent()
    {
        if (this.tokenManager.getGitLabToken() == null)
        {
            GitLabTokenResponse tokenResponse = new OidcGitLabAuthorizer().authorize(this.sessionContext, this.appInfo);
            if (tokenResponse == null)
            {
                tokenResponse = new PersonalAccessTokenGitLabAuthorizer().authorize(this.sessionContext, this.appInfo);
            }
            if (tokenResponse != null)
            {
                this.tokenManager.setTokenResponse(tokenResponse);
            }
        }
    }
}
