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
import org.finos.legend.sdlc.server.gitlab.GitLabAppInfo;
import org.gitlab4j.api.Constants;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

class GitLabTokenManager implements Serializable
{
    private static final long serialVersionUID = 4579663645788521787L;
    private static final long DEFAULT_EXPIRY_SECS = 7200;

    private final GitLabAppInfo appInfo;
    private GitLabToken token;
    private String refreshToken;
    private LocalDateTime tokenExpiry;

    private GitLabTokenManager(GitLabAppInfo appInfo)
    {
        this.appInfo = appInfo;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }

        if (!(other instanceof GitLabTokenManager))
        {
            return false;
        }

        GitLabTokenManager that = (GitLabTokenManager) other;
        return this.appInfo.equals(that.appInfo) && Objects.equals(this.token, that.token);
    }

    @Override
    public int hashCode()
    {
        return this.appInfo.hashCode() ^ Objects.hashCode(this.token);
    }

    @Override
    public String toString()
    {
        return this.appendGitLabTokenInfo(new StringBuilder("<GitLabTokenManager ")).append('>').toString();
    }

    public GitLabAppInfo getAppInfo()
    {
        return this.appInfo;
    }

    GitLabToken getGitLabToken()
    {
        return this.token;
    }

    void clearGitLabToken()
    {
        this.token = null;
    }

    void setGitLabToken(GitLabToken token)
    {
        if (token == null)
        {
            throw new IllegalArgumentException("token may not be null");
        }
        if (token.getTokenType() == null)
        {
            throw new IllegalArgumentException("token type may not be null");
        }
        this.token = token;
    }

    public void setRefreshToken(String refreshToken)
    {
        if (refreshToken == null)
        {
            throw new IllegalArgumentException("token may not be null");
        }
        this.refreshToken = refreshToken;
    }

    public void setTokenExpiry(long expiresInSecs)
    {
        if (expiresInSecs <= 0L)
        {
            expiresInSecs = DEFAULT_EXPIRY_SECS;
        }
        this.tokenExpiry = LocalDateTime.now().plusSeconds(expiresInSecs * 3 / 4);
    }

    public void setTokenExpiry(LocalDateTime expiry)
    {
        this.tokenExpiry = expiry;
    }

    public String getRefreshToken()
    {
        return this.refreshToken;
    }

    public boolean shouldRefreshToken()
    {
        return LocalDateTime.now().isAfter(this.tokenExpiry);
    }

    boolean gitLabOAuthCallback(String code)
    {
        GitLabTokenResponse tokenResponse = GitLabOAuthAuthenticator.newAuthenticator(this.appInfo).getOAuthTokenResponseFromAuthCode(code);
        GitLabToken oldToken = this.token;
        this.token = tokenResponse.getAccessToken();
        this.refreshToken = tokenResponse.getRefreshToken();
        this.setTokenExpiry(tokenResponse.getExpiryInSecs());
        return !token.equals(oldToken);
    }

    StringBuilder appendGitLabTokenInfo(StringBuilder builder)
    {
        return builder.append("token=").append(this.token != null ? ("'" + this.token.toString() + "'") : "null");
    }

    Token.TokenBuilder encode(Token.TokenBuilder builder)
    {
        GitLabToken token = this.token;
        builder.putInt(token != null ? 1 : 0);
        if (token != null)
        {
            builder.putString(this.appInfo.getAppId());
            builder.putString(token.getTokenType().toString());
            builder.putString(token.getToken());
        }
        return builder;
    }

    void decodeAndSetToken(Token.TokenReader reader)
    {
        // even if token size is just 0 or 1, it's important to read through all tokens from the reader,
        // so that it's in an appropriate state for whatever reads from it next.
        for (int size = reader.getInt(); size > 0; size--)
        {
            String appId = reader.getString();
            String typeName = reader.getString();
            String token = reader.getString();

            if ((appId != null) && (typeName != null) && (token != null) && appId.equals(this.appInfo.getAppId()))
            {
                Constants.TokenType type;
                try
                {
                    type = Constants.TokenType.valueOf(typeName);
                }
                catch (IllegalArgumentException e)
                {
                    // unknown token type - token will be ignored
                    continue;
                }
                this.token = GitLabToken.newGitLabToken(type, token);
            }
        }
    }

    static GitLabTokenManager newTokenManager(GitLabAppInfo appInfo)
    {
        return new GitLabTokenManager(appInfo);
    }
}
