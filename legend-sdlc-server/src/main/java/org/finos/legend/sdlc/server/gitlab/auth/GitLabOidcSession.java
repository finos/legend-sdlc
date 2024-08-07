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

import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.token.AccessToken;
import org.finos.legend.sdlc.server.auth.BaseCommonProfileSession;
import org.finos.legend.sdlc.server.auth.Token;
import org.gitlab4j.api.Constants.TokenType;
import org.pac4j.oidc.profile.OidcProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;

public class GitLabOidcSession extends BaseCommonProfileSession<OidcProfile> implements GitLabSession
{
    private static final long serialVersionUID = -7376645595174820197L;
    private static final Logger LOGGER = LoggerFactory.getLogger(GitLabOidcSession.class);

    private final GitLabTokenManager tokenManager;

    GitLabOidcSession(OidcProfile profile, String userId, Instant creationTime, GitLabTokenManager tokenManager)
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

        if (!(other instanceof GitLabOidcSession))
        {
            return false;
        }

        GitLabOidcSession that = (GitLabOidcSession) other;
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
        this.tokenManager.setRefreshToken(refreshToken);
    }

    @Override
    public String getRefreshToken()
    {
        return this.tokenManager.getRefreshToken();
    }

    @Override
    public void setTokenExpiry(long expiresInSecs)
    {
        this.tokenManager.setTokenExpiry(expiresInSecs);
    }

    @Override
    public boolean shouldRefreshToken()
    {
        return this.tokenManager.shouldRefreshToken();
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

    private static GitLabTokenManager possiblyInitializeTokenManager(GitLabTokenManager tokenManager, OidcProfile profile)
    {
        if ((tokenManager != null) && (profile != null))
        {
            LOGGER.debug("initializing with profile from issuer {}: {}", profile.getIssuer(), profile);
            AccessToken accessToken = profile.getAccessToken();
            if (accessToken != null)
            {
                Scope scope = accessToken.getScope();
                if ((scope != null) && scope.contains("api"))
                {
                    String issuer = profile.getIssuer();
                    if (issuer != null)
                    {
                        LOGGER.debug("Found access token with appropriate scope for issuer: {}", issuer);
                        if (issuer.equals(tokenManager.getAppInfo().getServerInfo().getGitLabURLString()))
                        {
                            tokenManager.setGitLabToken(GitLabToken.newGitLabToken(TokenType.OAUTH2_ACCESS, accessToken.getValue()));
                            LOGGER.debug("Storing access token from OpenID Connect (OIDC) profile");
                        }
                    }
                }
            }
        }
        return tokenManager;
    }
}
