// Copyright 2024 Goldman Sachs
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

import org.gitlab4j.api.Constants.TokenType;

import java.time.LocalDateTime;
import java.util.Objects;

public class GitLabTokenResponse
{
    private final GitLabToken accessToken;
    private final String refreshToken;
    private final long expiresInSecs;
    private final LocalDateTime tokenExpiry;

    protected GitLabTokenResponse(String accessToken, String refreshToken, Integer expiresInSecs)
    {
        this(GitLabToken.newGitLabToken(TokenType.OAUTH2_ACCESS, accessToken), refreshToken, expiresInSecs, null);
    }

    protected GitLabTokenResponse(GitLabToken accessToken, String refreshToken, Integer expiresInSecs, LocalDateTime tokenExpiry)
    {
        this.accessToken = Objects.requireNonNull(accessToken, "accessToken may not be null");
        this.refreshToken = refreshToken;
        this.expiresInSecs = expiresInSecs != null ? expiresInSecs.longValue() : 0L;
        this.tokenExpiry = tokenExpiry;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }

        if (!(other instanceof GitLabTokenResponse))
        {
            return false;
        }

        GitLabTokenResponse that = (GitLabTokenResponse) other;
        return this.accessToken.equals(that.accessToken) &&
                Objects.equals(this.refreshToken, that.refreshToken) &&
                this.expiresInSecs == that.expiresInSecs;
    }

    @Override
    public int hashCode()
    {
        return this.accessToken.hashCode() + 31 * (Objects.hashCode(this.refreshToken) + 31 * Long.hashCode(this.expiresInSecs));
    }

    public GitLabToken getAccessToken()
    {
        return this.accessToken;
    }

    public String getRefreshToken()
    {
        return this.refreshToken;
    }

    public long getExpiresInSecs()
    {
        return this.expiresInSecs;
    }

    /**
     * The exact token expiry, when the token's source supplied one (e.g. an OIDC profile); null when only an
     * expires-in duration is known.
     *
     * @return token expiry or null
     */
    public LocalDateTime getTokenExpiry()
    {
        return this.tokenExpiry;
    }
}
