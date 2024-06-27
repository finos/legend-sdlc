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

import org.finos.legend.sdlc.server.auth.BaseCommonProfileSession;
import org.finos.legend.sdlc.server.auth.Token;
import org.finos.legend.server.pac4j.gitlab.GitlabPersonalAccessTokenProfile;
import org.gitlab4j.api.Constants.TokenType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;

public class GitLabPersonalAccessTokenSession extends BaseCommonProfileSession<GitlabPersonalAccessTokenProfile> implements GitLabSession
{
    private static final Logger LOGGER = LoggerFactory.getLogger(GitLabPersonalAccessTokenSession.class);

    private final GitLabTokenManager tokenManager;

    protected GitLabPersonalAccessTokenSession(GitlabPersonalAccessTokenProfile profile, String userId, Instant creationTime, GitLabTokenManager tokenManager)
    {
        super(profile, userId, creationTime);
        this.tokenManager = possiblyInitializeTokenManager(tokenManager, profile);
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }

        if (!(other instanceof GitLabPersonalAccessTokenSession))
        {
            return false;
        }

        GitLabPersonalAccessTokenSession that = (GitLabPersonalAccessTokenSession) other;
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
    public boolean gitLabOAuthCallback(String code)
    {
        return this.tokenManager.gitLabOAuthCallback(code);
    }

    @Override
    public GitLabToken getGitLabToken()
    {
        return this.tokenManager.getGitLabToken();
    }

    @Override
    public void clearGitLabToken()
    {
        this.tokenManager.clearGitLabToken();
    }

    @Override
    public void setGitLabToken(GitLabToken token)
    {
        this.tokenManager.setGitLabToken(token);
    }

    @Override
    public void setRefreshToken(String refreshToken)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public String getRefreshToken()
    {
        return null;
    }

    @Override
    public void setTokenExpiry(long expiryIn)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public boolean shouldRefreshToken()
    {
        return false;
    }

    @Override
    public Token.TokenBuilder encode(Token.TokenBuilder builder)
    {
        return this.tokenManager.encode(super.encode(builder));
    }

    @Override
    protected void writeToStringInfo(StringBuilder builder)
    {
        this.tokenManager.appendGitLabTokenInfo(builder.append(' '));
    }

    private static GitLabTokenManager possiblyInitializeTokenManager(GitLabTokenManager tokenManager, GitlabPersonalAccessTokenProfile profile)
    {
        if ((tokenManager != null) && (profile != null))
        {
            LOGGER.debug("initializing with GitLab Personal Access Token (PAT) profile: {}", profile);
            String token = profile.getPersonalAccessToken();

            if (token != null &&
                profile.getGitlabHost() != null &&
                tokenManager.getAppInfo().getServerInfo().getHost().equals(profile.getGitlabHost()))
            {
                tokenManager.setGitLabToken(GitLabToken.newGitLabToken(TokenType.PRIVATE, token));
                LOGGER.debug("Storing private access token from GitLab Personal Access Token (PAT) profile");
            }
        }
        return tokenManager;
    }
}
