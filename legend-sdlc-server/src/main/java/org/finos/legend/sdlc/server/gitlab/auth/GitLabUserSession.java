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
import org.finos.legend.sdlc.server.gitlab.mode.GitLabMode;
import org.finos.legend.sdlc.server.gitlab.mode.GitLabModeInfo;
import org.gitlab4j.api.Constants;
import org.pac4j.oidc.profile.OidcProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Set;

//public class GitLabUserSession extends BaseCommonProfileSession<GitLabUserProfile> implements GitLabSession
public class GitLabUserSession extends BaseCommonProfileSession<OidcProfile> implements GitLabSession
{
    private static final Logger LOGGER = LoggerFactory.getLogger(GitLabOidcSession.class);

    private final GitLabTokenManager tokenManager;

    protected GitLabUserSession(OidcProfile profile, String userId, Instant creationTime, GitLabTokenManager tokenManager)
    {
        super(profile, userId, creationTime);
        this.tokenManager = tokenManager;
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
}
