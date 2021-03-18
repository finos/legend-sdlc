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

import org.finos.legend.sdlc.server.gitlab.mode.GitLabMode;
import org.finos.legend.sdlc.server.gitlab.mode.GitLabModeInfo;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public class TestGitLabSession implements GitLabSession
{
    private final String userId;
    private String accessToken;
    private GitLabModeInfo gitLabModeInfo;

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
    public Set<GitLabMode> getValidModes()
    {
        return Collections.unmodifiableSet(EnumSet.of(GitLabMode.PROD));
    }

    @Override
    public boolean isValidMode(GitLabMode mode)
    {
        return GitLabMode.PROD.equals(mode);
    }

    @Override
    public boolean gitLabOAuthCallback(GitLabMode mode, String code)
    {
        return false;
    }

    public void setAccessToken(String token)
    {
        this.accessToken = token;
    }

    @Override
    public String getAccessToken(GitLabMode mode)
    {
        return this.accessToken;
    }

    @Override
    public void clearAccessTokens()
    {

    }

    @Override
    public void putAccessToken(GitLabMode mode, String token)
    {

    }

    public void setModeInfo(GitLabModeInfo gitLabModeInfo)
    {
        this.gitLabModeInfo = gitLabModeInfo;
    }

    @Override
    public GitLabModeInfo getModeInfo(GitLabMode mode)
    {
        return this.gitLabModeInfo;
    }
}
