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
import org.finos.legend.sdlc.server.gitlab.mode.GitLabMode;
import org.finos.legend.sdlc.server.gitlab.mode.GitLabModeInfo;
import org.pac4j.oidc.profile.OidcProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

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

        GitLabOidcSession that = (GitLabOidcSession)other;
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
    public String getAccessToken(GitLabMode mode)
    {
        return this.tokenManager.getAccessToken(mode);
    }

    @Override
    public void clearAccessTokens()
    {
        this.tokenManager.clearAccessTokens();
    }

    @Override
    public void putAccessToken(GitLabMode mode, String token)
    {
        this.tokenManager.putAccessToken(mode, token);
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
                        tokenManager.getValidModes()
                                .stream()
                                .filter(mode -> tokenManager.getAccessToken(mode) == null)
                                .map(tokenManager::getModeInfo)
                                .filter(modeInfo -> issuer.equals(modeInfo.getServerInfo().getGitLabURLString()))
                                .forEach(modeInfo ->
                                {
                                    tokenManager.putAccessToken(modeInfo.getMode(), accessToken.getValue());
                                    LOGGER.debug("Storing access token from profile for mode {}", modeInfo.getMode());
                                });
                    }
                }
            }
        }
        return tokenManager;
    }
}
