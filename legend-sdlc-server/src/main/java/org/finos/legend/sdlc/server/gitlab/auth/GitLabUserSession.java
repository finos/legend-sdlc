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

import org.finos.legend.sdlc.server.auth.Token;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.mode.GitLabMode;
import org.finos.legend.sdlc.server.gitlab.mode.GitLabModeInfo;
import org.finos.legend.server.pac4j.gitlab.GitlabUserProfile;
import org.gitlab4j.api.Constants.TokenType;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.AbstractUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public class GitLabUserSession extends BaseCommonProfileSession<GitlabUserProfile> implements GitLabSession
{
    private static final Logger LOGGER = LoggerFactory.getLogger(GitLabOidcSession.class);

    private final GitLabTokenManager tokenManager;

    protected GitLabUserSession(GitlabUserProfile profile, String userId, Instant creationTime, GitLabTokenManager tokenManager)
    {
        super(profile, possiblyRetrieveUserId(tokenManager, profile, userId), creationTime);
        this.tokenManager = possiblyInitializeTokenManager(tokenManager, profile);
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }

        if (!(other instanceof GitLabUserSession))
        {
            return false;
        }

        GitLabUserSession that = (GitLabUserSession)other;
        return Objects.equals(this.getUserId(), that.getUserId()) &&
                Objects.equals(this.getProfile(), that.getProfile()) &&
                this.getCreationTime().equals(that.getCreationTime()) &&
                this.tokenManager.equals(that.tokenManager);
    }

    @Override
    public int hashCode()
    {
        return getCreationTime().hashCode() ^ getUserId().hashCode() ^ this.tokenManager.hashCode();
    }

    @Override
    public Set<GitLabMode> getValidModes()
    {
        return this.tokenManager.getValidModes();
    }

    @Override
    public boolean isValidMode(GitLabMode mode)
    {
        return this.tokenManager.isValidMode(mode);
    }

    @Override
    public boolean gitLabOAuthCallback(GitLabMode mode, String code)
    {
        return this.tokenManager.gitLabOAuthCallback(mode, code);
    }

    @Override
    public GitLabToken getGitLabToken(GitLabMode mode)
    {
        return this.tokenManager.getGitLabToken(mode);
    }

    @Override
    public void clearGitLabTokens()
    {
        this.tokenManager.clearGitLabTokens();
    }

    @Override
    public void putGitLabToken(GitLabMode mode, String token)
    {
        this.tokenManager.putPrivateAccessToken(mode, token);
    }

    @Override
    public void putGitLabToken(GitLabMode mode, GitLabToken token)
    {
        if (token.getTokenType().equals(Constants.TokenType.PRIVATE))
        {
            this.tokenManager.putGitLabToken(mode, token);
        }
    }

    @Override
    public GitLabModeInfo getModeInfo(GitLabMode mode)
    {
        return this.tokenManager.getModeInfo(mode);
    }

    @Override
    public Token.TokenBuilder encode(Token.TokenBuilder builder)
    {
        return this.tokenManager.encode(super.encode(builder));
    }

    @Override
    protected void writeToStringInfo(StringBuilder builder)
    {
        this.tokenManager.appendTokenInfo(builder.append(' '));
    }

    private static String possiblyRetrieveUserId(GitLabTokenManager tokenManager, GitlabUserProfile profile, String userId)
    {
        if (userId == null)
        {
            GitLabMode mode = tokenManager.getValidModes().stream().findFirst().orElse(null);
            String url = tokenManager.getModeInfo(mode).getServerInfo().getGitLabURLString();

            try
            {
                GitLabApi api = new GitLabApi(GitLabApi.ApiVersion.V4, url, TokenType.PRIVATE, profile.getToken());
                AbstractUser user = api.getUserApi().getCurrentUser();
                return user.getUsername();
            }
            catch (GitLabApiException ex)
            {
                throw new LegendSDLCServerException("Couldn't get userId for provided token", ex);
            }
        }
        else
        {
            return userId;
        }
    }

    private static GitLabTokenManager possiblyInitializeTokenManager(GitLabTokenManager tokenManager, GitlabUserProfile profile)
    {
        if ((tokenManager != null) && (profile != null))
        {
            LOGGER.debug("initializing with GitlabUserProfile: {}", profile);
            String token = profile.getToken();
            if (token != null)
            {
                tokenManager.getValidModes()
                        .stream()
                        .filter(mode -> tokenManager.getGitLabToken(mode) == null)
                        .map(tokenManager::getModeInfo)
                        .forEach(modeInfo ->
                        {
                            tokenManager.putOAuthToken(modeInfo.getMode(), token);
                            LOGGER.debug("Storing private access token from profile for mode {}", modeInfo.getMode());
                        });
            }
        }
        return tokenManager;
    }
}
