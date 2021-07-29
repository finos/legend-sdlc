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
import org.finos.legend.sdlc.server.gitlab.mode.GitLabMode;
import org.finos.legend.sdlc.server.gitlab.mode.GitLabModeInfo;
import org.finos.legend.sdlc.server.gitlab.mode.GitLabModeInfos;
import org.gitlab4j.api.Constants.TokenType;

import java.io.Serializable;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

class GitLabTokenManager implements Serializable
{
    private static final long serialVersionUID = 4579663645788521787L;

    private final GitLabModeInfos modeInfos;
    private final Map<GitLabMode, GitLabToken> tokens = new EnumMap<>(GitLabMode.class);

    private GitLabTokenManager(GitLabModeInfos modeInfos)
    {
        this.modeInfos = modeInfos;
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

        GitLabTokenManager that = (GitLabTokenManager)other;
        return this.modeInfos.equals(that.modeInfos) && this.tokens.equals(that.tokens);
    }

    @Override
    public int hashCode()
    {
        return this.modeInfos.hashCode() ^ this.tokens.hashCode();
    }

    @Override
    public String toString()
    {
        return appendTokenInfo(new StringBuilder("<GitLabTokenManager ")).append('>').toString();
    }

    Set<GitLabMode> getValidModes()
    {
        return this.modeInfos.getModes();
    }

    boolean isValidMode(GitLabMode mode)
    {
        return this.modeInfos.hasModeInfo(mode);
    }

    GitLabModeInfo getModeInfo(GitLabMode mode)
    {
        return this.modeInfos.getModeInfo(mode);
    }

    GitLabToken getGitLabToken(GitLabMode mode)
    {
        return this.tokens.get(mode);
    }

    void clearGitLabTokens()
    {
        synchronized (this.tokens)
        {
            this.tokens.clear();
        }
    }

    void putGitLabToken(GitLabMode mode, GitLabToken token)
    {
        if (mode == null)
        {
            throw new IllegalArgumentException("mode may not be null");
        }
        if (!this.modeInfos.hasModeInfo(mode))
        {
            throw new IllegalArgumentException("mode has no info: " + mode);
        }
        if (token == null)
        {
            throw new IllegalArgumentException("token may not be null");
        }
        if (token.getTokenType() == null)
        {
            throw new IllegalArgumentException("token type may not be null");
        }
        synchronized (this.tokens)
        {
            this.tokens.put(mode, token);
        }
    }

    boolean gitLabOAuthCallback(GitLabMode mode, String code)
    {
        GitLabModeInfo modeInfo = getModeInfo(mode);
        if (modeInfo == null)
        {
            throw new IllegalStateException("Unsupported GitLab mode: " + mode);
        }
        synchronized (this.tokens)
        {
            GitLabToken token = GitLabToken.newGitLabToken(TokenType.OAUTH2_ACCESS, GitLabOAuthAuthenticator.newAuthenticator(modeInfo).getOAuthTokenFromAuthCode(code));
            GitLabToken oldToken = this.tokens.put(mode, token);
            return !token.equals(oldToken);
        }
    }

    StringBuilder appendTokenInfo(StringBuilder builder)
    {
        synchronized (this.tokens)
        {
            builder.append("tokens=[");
            int startLen = builder.length();
            for (GitLabMode mode : GitLabMode.values())
            {
                GitLabToken token = this.tokens.get(mode);
                if (token != null)
                {
                    ((builder.length() == startLen) ? builder : builder.append(", ")).append(mode.name()).append('=').append(token.toString());
                }
            }
            builder.append(']');
            return builder;
        }
    }

    Token.TokenBuilder encode(Token.TokenBuilder builder)
    {
        synchronized (this.tokens)
        {
            builder.putInt(this.tokens.size());
            for (GitLabMode mode : GitLabMode.values())
            {
                GitLabToken token = this.tokens.get(mode);
                if (token != null)
                {
                    builder.putString(mode.name());
                    builder.putString(this.modeInfos.getModeInfo(mode).getAppInfo().getAppId());
                    builder.putString(token.getTokenType().toString());
                    builder.putString(token.getToken());
                }
            }
            return builder;
        }
    }

    void putAllFromToken(Token.TokenReader reader)
    {
        synchronized (this.tokens)
        {
            for (int size = reader.getInt(); size > 0; size--)
            {
                // Read values
                String modeName = reader.getString();
                String appId = reader.getString();
                String typeName = reader.getString();
                String token = reader.getString();

                if ((modeName != null) && (appId != null) && (typeName != null) && (token != null))
                {
                    // Get mode and token type
                    GitLabMode mode;
                    TokenType type;
                    try
                    {
                        mode = GitLabMode.valueOf(modeName);
                        type = TokenType.valueOf(typeName);
                    }
                    catch (IllegalArgumentException e)
                    {
                        // unknown mode or token type - token will be ignored
                        continue;
                    }

                    // Check the mode info
                    GitLabModeInfo modeInfo = this.modeInfos.getModeInfo(mode);
                    if ((modeInfo != null) && appId.equals(modeInfo.getAppInfo().getAppId()))
                    {
                        this.tokens.put(mode, GitLabToken.newGitLabToken(type, token));
                    }
                }
            }
        }
    }

    static GitLabTokenManager newTokenManager(GitLabModeInfos modeInfos)
    {
        return new GitLabTokenManager(modeInfos);
    }
}
