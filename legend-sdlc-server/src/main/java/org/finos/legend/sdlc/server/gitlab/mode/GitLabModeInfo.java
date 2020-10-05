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

package org.finos.legend.sdlc.server.gitlab.mode;

import org.finos.legend.sdlc.server.gitlab.GitLabAppInfo;
import org.finos.legend.sdlc.server.gitlab.GitLabConfiguration;
import org.finos.legend.sdlc.server.gitlab.GitLabServerInfo;

import java.io.Serializable;

public class GitLabModeInfo implements Serializable
{
    private static final long serialVersionUID = -5848030620016152622L;

    private final GitLabMode mode;
    private final GitLabAppInfo appInfo;

    private GitLabModeInfo(GitLabMode mode, GitLabAppInfo appInfo)
    {
        this.mode = mode;
        this.appInfo = appInfo;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }

        if (!(other instanceof GitLabModeInfo))
        {
            return false;
        }

        GitLabModeInfo that = (GitLabModeInfo)other;
        return (this.mode == that.mode) && this.appInfo.equals(that.appInfo);
    }

    @Override
    public int hashCode()
    {
        return this.mode.hashCode();
    }

    public GitLabMode getMode()
    {
        return this.mode;
    }

    public GitLabServerInfo getServerInfo()
    {
        return this.appInfo.getServerInfo();
    }

    public GitLabAppInfo getAppInfo()
    {
        return this.appInfo;
    }

    public static GitLabModeInfo newModeInfo(GitLabMode mode, GitLabConfiguration.ModeConfiguration modeConfig)
    {
        return newModeInfo(mode, GitLabAppInfo.newAppInfo(modeConfig.getServerConfiguration(), modeConfig.getAppConfiguration()));
    }

    public static GitLabModeInfo newModeInfo(GitLabMode mode, String scheme, String hostName, Integer port, String appId, String appSecret, String appRedirectURI)
    {
        return newModeInfo(mode, GitLabAppInfo.newAppInfo(GitLabServerInfo.newServerInfo(scheme, hostName, port), appId, appSecret, appRedirectURI));
    }

    public static GitLabModeInfo newModeInfo(GitLabMode mode, GitLabAppInfo appInfo)
    {
        return new GitLabModeInfo(mode, appInfo);
    }
}
