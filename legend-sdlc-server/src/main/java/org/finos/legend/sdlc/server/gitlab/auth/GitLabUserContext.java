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
import org.finos.legend.sdlc.server.auth.KerberosSession;
import org.finos.legend.sdlc.server.auth.LegendSDLCWebFilter;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.mode.GitLabMode;
import org.finos.legend.sdlc.server.gitlab.mode.GitLabModeInfo;
import org.finos.legend.sdlc.server.guice.UserContext;
import org.gitlab4j.api.Constants.TokenType;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApi.ApiVersion;

import java.net.URI;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response.Status;

@RequestScoped
public class GitLabUserContext extends UserContext
{
    private final Map<GitLabMode, GitLabApi> apiCache = new EnumMap<>(GitLabMode.class);

    @Inject
    public GitLabUserContext(HttpServletRequest httpRequest, HttpServletResponse httpResponse)
    {
        super(httpRequest, httpResponse);
    }

    public void gitLabAuthCallback(GitLabMode mode, String code)
    {
        GitLabSession gitLabSession = getGitLabSession();
        if (gitLabSession.gitLabOAuthCallback(mode, code))
        {
            LegendSDLCWebFilter.setSessionCookie(this.httpResponse, gitLabSession);
        }
    }

    public GitLabApi getGitLabAPI(GitLabMode mode)
    {
        return getGitLabAPI(mode, false);
    }

    public GitLabApi getGitLabAPI(GitLabMode mode, boolean redirectAllowed)
    {
        GitLabApi api = this.apiCache.get(mode);
        if (api == null)
        {
            if (!isValidMode(mode))
            {
                throw new LegendSDLCServerException("GitLab mode " + mode + " is not supported", Status.BAD_REQUEST);
            }
            GitLabSession gitLabSession = getGitLabSession();
            synchronized (this.apiCache)
            {
                api = this.apiCache.get(mode);
                if (api == null)
                {
                    GitLabModeInfo modeInfo = gitLabSession.getModeInfo(mode);
                    String accessToken = gitLabSession.getAccessToken(mode);
                    if (accessToken == null)
                    {
                        if (gitLabSession instanceof KerberosSession)
                        {
                            try
                            {
                                accessToken = GitLabOAuthAuthenticator.newAuthenticator(modeInfo).getOAuthToken(((KerberosSession)gitLabSession).getSubject());
                                gitLabSession.putAccessToken(mode, accessToken);
                                LegendSDLCWebFilter.setSessionCookie(this.httpResponse, gitLabSession);
                            }
                            catch (GitLabOAuthAuthenticator.UserInputRequiredException e)
                            {
                                URI redirectURI = GitLabOAuthAuthenticator.buildAppAuthorizationURI(e.getModeInfo(), this.httpRequest);
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
                        }
                        else if (redirectAllowed)
                        {
                            URI redirectURI = GitLabOAuthAuthenticator.buildAppAuthorizationURI(modeInfo, this.httpRequest);
                            throw new LegendSDLCServerException(redirectURI.toString(), Status.FOUND);
                        }
                        else
                        {
                            throw new LegendSDLCServerException("{\"message\":\"Authorization required\",\"auth_uri\":\"/auth/authorize\"}", Status.FORBIDDEN);
                        }
                    }
                    api = new GitLabApi(ApiVersion.V4, modeInfo.getServerInfo().getGitLabURLString(), TokenType.OAUTH2_ACCESS, accessToken);
                    this.apiCache.put(mode, api);
                }
            }
        }
        return api;
    }

    public boolean isModeAuthorized(GitLabMode mode)
    {
        if (this.apiCache.get(mode) == null)
        {
            GitLabSession gitLabSession = getGitLabSession();
            if (gitLabSession.getAccessToken(mode) == null)
            {
                synchronized (this.apiCache)
                {
                    if (gitLabSession.getAccessToken(mode) == null)
                    {
                        if (gitLabSession instanceof KerberosSession)
                        {
                            try
                            {
                                String accessToken = GitLabOAuthAuthenticator.newAuthenticator(gitLabSession.getModeInfo(mode)).getOAuthToken(((KerberosSession) gitLabSession).getSubject());
                                // If we can get the token, then the mode is authorized. But since we have it, we might as well save it.
                                gitLabSession.putAccessToken(mode, accessToken);
                                LegendSDLCWebFilter.setSessionCookie(this.httpResponse, gitLabSession);
                            }
                            catch (GitLabAuthFailureException | GitLabOAuthAuthenticator.UserInputRequiredException e)
                            {
                                // These exceptions indicate the mode is not yet authorized or that authorization has failed.
                                return false;
                            }
                        }
                        else
                        {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    public void clearAccessTokens()
    {
        synchronized (this.apiCache)
        {
            this.apiCache.clear();
            GitLabSession gitLabSession = getGitLabSession();
            gitLabSession.clearAccessTokens();
            LegendSDLCWebFilter.setSessionCookie(this.httpResponse, gitLabSession);
        }
    }

    public Set<GitLabMode> getValidGitLabModes()
    {
        return getGitLabSession().getValidModes();
    }

    public boolean isValidMode(GitLabMode mode)
    {
        return getGitLabSession().isValidMode(mode);
    }

    private GitLabSession getGitLabSession()
    {
        return (GitLabSession)this.session;
    }
}
