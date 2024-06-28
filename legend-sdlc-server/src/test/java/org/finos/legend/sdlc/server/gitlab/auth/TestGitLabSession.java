// Copyright 2021 Goldman Sachs
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

import java.time.Instant;
import java.time.LocalDateTime;

public class TestGitLabSession implements GitLabSession
{
    private final String userId;

    private GitLabToken token;

    private String refreshToken;

    private LocalDateTime tokenExpiry;

    private static final long DEFAULT_EXPIRY_SECS = 7200;

    public TestGitLabSession(String userId)
    {
        this.userId = userId;
    }

    @Override
    public String getUserId()
    {
        return this.userId;
    }

    @Override
    public Instant getCreationTime()
    {
        return null;
    }

    @Override
    public boolean isValid()
    {
        return false;
    }

    @Override
    public boolean gitLabOAuthCallback(String code)
    {
        return false;
    }

    @Override
    public GitLabToken getGitLabToken()
    {
        return this.token;
    }

    @Override
    public void clearGitLabToken()
    {
        this.token = null;
    }

    @Override
    public void setGitLabToken(GitLabToken token)
    {
        this.token = token;
    }

    @Override
    public void setRefreshToken(String refreshToken)
    {
        this.refreshToken = refreshToken;
    }

    @Override
    public String getRefreshToken()
    {
        return this.refreshToken;
    }

    @Override
    public void setTokenExpiry(long expiresInSecs)
    {
        if (expiresInSecs <= 0L)
        {
            expiresInSecs = DEFAULT_EXPIRY_SECS;
        }
        this.tokenExpiry = LocalDateTime.now().plusSeconds(expiresInSecs * 3 / 4);
    }

    @Override
    public boolean shouldRefreshToken()
    {
        return LocalDateTime.now().isAfter(this.tokenExpiry);
    }
}
