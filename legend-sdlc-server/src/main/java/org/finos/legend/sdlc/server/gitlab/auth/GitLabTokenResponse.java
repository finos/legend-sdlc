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

import org.gitlab4j.api.Constants.TokenType;

public class GitLabTokenResponse
{
    private final GitLabToken accessToken;
    private final GitLabToken refreshToken;
    private final Integer expiryIn;

    protected GitLabTokenResponse(String accessToken, String refreshToken, Integer expiryIn)
    {
        this.accessToken = GitLabToken.newGitLabToken(TokenType.OAUTH2_ACCESS, accessToken);
        this.refreshToken = GitLabToken.newGitLabToken(TokenType.OAUTH2_ACCESS, refreshToken);
        this.expiryIn = expiryIn != null ? expiryIn : 0;
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
                this.refreshToken.equals(that.refreshToken) &&
                this.expiryIn == that.expiryIn;
    }

    @Override
    public int hashCode()
    {
        return this.accessToken.hashCode() + 31 * (this.refreshToken.hashCode() + 31 * this.expiryIn.hashCode());
    }

    public GitLabToken getAccessToken()
    {
        return this.accessToken;
    }

    public GitLabToken getRefreshToken()
    {
        return this.refreshToken;
    }

    public long getExpiryIn()
    {
        return this.expiryIn.longValue();
    }
}
