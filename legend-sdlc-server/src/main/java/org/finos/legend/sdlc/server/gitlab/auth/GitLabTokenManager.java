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

import org.finos.legend.sdlc.backend.api.spi.BackendSessionStateStore;
import org.finos.legend.sdlc.server.gitlab.GitLabAppInfo;
import org.gitlab4j.api.Constants;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * The GitLab backend's per-user token state, persisted through the host's
 * {@link BackendSessionStateStore session state store} (which the server backs with the session cookie): the
 * access token (type and value), the refresh token, and the token expiry. The stored state is keyed to the
 * GitLab application id — state written for one application is invisible to another, as with the former
 * cookie-embedded encoding. Mutations write through to the store immediately.
 */
class GitLabTokenManager
{
    private static final long DEFAULT_EXPIRY_SECS = 7200;

    private static final String APP_ID_KEY = "gitlab.appId";
    private static final String TOKEN_TYPE_KEY = "gitlab.token.type";
    private static final String TOKEN_KEY = "gitlab.token";
    private static final String REFRESH_TOKEN_KEY = "gitlab.refreshToken";
    private static final String TOKEN_EXPIRY_KEY = "gitlab.tokenExpiry";

    private final GitLabAppInfo appInfo;
    private final BackendSessionStateStore stateStore;
    private GitLabToken token;
    private String refreshToken;
    private LocalDateTime tokenExpiry;

    private GitLabTokenManager(GitLabAppInfo appInfo, BackendSessionStateStore stateStore)
    {
        this.appInfo = appInfo;
        this.stateStore = stateStore;
        readState();
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
        this.refreshToken = null;
        this.tokenExpiry = null;
        writeState();
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
        writeState();
    }

    void setRefreshToken(String refreshToken)
    {
        if (refreshToken == null)
        {
            throw new IllegalArgumentException("token may not be null");
        }
        this.refreshToken = refreshToken;
        writeState();
    }

    void setTokenExpiry(long expiresInSecs)
    {
        if (expiresInSecs <= 0L)
        {
            expiresInSecs = DEFAULT_EXPIRY_SECS;
        }
        setTokenExpiry(LocalDateTime.now().plusSeconds(expiresInSecs * 3 / 4));
    }

    void setTokenExpiry(LocalDateTime expiry)
    {
        this.tokenExpiry = expiry;
        writeState();
    }

    String getRefreshToken()
    {
        return this.refreshToken;
    }

    boolean shouldRefreshToken()
    {
        if ((this.token != null) && (this.token.getTokenType() == Constants.TokenType.PRIVATE))
        {
            // a personal access token is not refreshed through the OAuth flow
            return false;
        }
        return (this.tokenExpiry == null) || LocalDateTime.now().isAfter(this.tokenExpiry);
    }

    /**
     * Apply a token response: access token, refresh token, and expiry (the exact expiry when the response
     * carries one, otherwise derived from the expires-in duration).
     *
     * @param tokenResponse token response
     */
    void setTokenResponse(GitLabTokenResponse tokenResponse)
    {
        this.token = tokenResponse.getAccessToken();
        this.refreshToken = tokenResponse.getRefreshToken();
        this.tokenExpiry = (tokenResponse.getTokenExpiry() != null) ? tokenResponse.getTokenExpiry() : expiryFromNow(tokenResponse.getExpiresInSecs());
        writeState();
    }

    boolean gitLabOAuthCallback(String code)
    {
        GitLabTokenResponse tokenResponse = GitLabOAuthAuthenticator.newAuthenticator(this.appInfo).getOAuthTokenResponseFromAuthCode(code);
        GitLabToken oldToken = this.token;
        setTokenResponse(tokenResponse);
        return !this.token.equals(oldToken);
    }

    StringBuilder appendGitLabTokenInfo(StringBuilder builder)
    {
        return builder.append("token=").append(this.token != null ? ("'" + this.token + "'") : "null");
    }

    private static LocalDateTime expiryFromNow(long expiresInSecs)
    {
        if (expiresInSecs <= 0L)
        {
            expiresInSecs = DEFAULT_EXPIRY_SECS;
        }
        return LocalDateTime.now().plusSeconds(expiresInSecs * 3 / 4);
    }

    private void readState()
    {
        if (this.stateStore == null)
        {
            return;
        }
        if (!Objects.equals(this.stateStore.get(APP_ID_KEY), this.appInfo.getAppId()))
        {
            // state stored for a different GitLab application (or none): start clean
            return;
        }
        String typeName = this.stateStore.get(TOKEN_TYPE_KEY);
        String tokenValue = this.stateStore.get(TOKEN_KEY);
        if ((typeName != null) && (tokenValue != null))
        {
            Constants.TokenType type;
            try
            {
                type = Constants.TokenType.valueOf(typeName);
            }
            catch (IllegalArgumentException e)
            {
                // unknown token type - token will be ignored
                return;
            }
            this.token = GitLabToken.newGitLabToken(type, tokenValue);
            this.refreshToken = this.stateStore.get(REFRESH_TOKEN_KEY);
            String expiry = this.stateStore.get(TOKEN_EXPIRY_KEY);
            this.tokenExpiry = (expiry == null) ? null : LocalDateTime.parse(expiry);
        }
    }

    private void writeState()
    {
        if (this.stateStore == null)
        {
            return;
        }
        if (this.token == null)
        {
            this.stateStore.put(APP_ID_KEY, null);
            this.stateStore.put(TOKEN_TYPE_KEY, null);
            this.stateStore.put(TOKEN_KEY, null);
            this.stateStore.put(REFRESH_TOKEN_KEY, null);
            this.stateStore.put(TOKEN_EXPIRY_KEY, null);
            return;
        }
        this.stateStore.put(APP_ID_KEY, this.appInfo.getAppId());
        this.stateStore.put(TOKEN_TYPE_KEY, this.token.getTokenType().name());
        this.stateStore.put(TOKEN_KEY, this.token.getToken());
        this.stateStore.put(REFRESH_TOKEN_KEY, this.refreshToken);
        this.stateStore.put(TOKEN_EXPIRY_KEY, (this.tokenExpiry == null) ? null : this.tokenExpiry.toString());
    }

    static GitLabTokenManager newTokenManager(GitLabAppInfo appInfo, BackendSessionStateStore stateStore)
    {
        return new GitLabTokenManager(appInfo, stateStore);
    }
}
