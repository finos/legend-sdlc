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

package org.finos.legend.sdlc.server.gitlab;

import java.io.Serializable;

public class GitLabAppInfo implements Serializable
{
    private static final long serialVersionUID = -1162395682634986743L;

    private final GitLabServerInfo serverInfo;
    private final String appId;
    private final String appSecret;
    private final String appRedirectURI;

    private GitLabAppInfo(GitLabServerInfo serverInfo, String appId, String appSecret, String appRedirectURI)
    {
        this.serverInfo = serverInfo;
        this.appId = appId;
        this.appSecret = appSecret;
        this.appRedirectURI = appRedirectURI;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }

        if (!(other instanceof GitLabAppInfo))
        {
            return false;
        }

        GitLabAppInfo that = (GitLabAppInfo)other;
        return this.serverInfo.equals(that.serverInfo) &&
                this.appId.equals(that.appId) &&
                this.appSecret.equals(that.appSecret) &&
                this.appRedirectURI.equals(that.appRedirectURI);
    }

    @Override
    public int hashCode()
    {
        return this.serverInfo.hashCode() + 97 * (this.appId.hashCode() + 97 * (this.appSecret.hashCode() + 97 * this.appRedirectURI.hashCode()));
    }

    public GitLabServerInfo getServerInfo()
    {
        return this.serverInfo;
    }

    public String getAppId()
    {
        return this.appId;
    }

    public String getAppSecret()
    {
        return this.appSecret;
    }

    public String getAppRedirectURI()
    {
        return this.appRedirectURI;
    }

    public static GitLabAppInfo newAppInfo(GitLabConfiguration.ServerConfiguration serverConfig, GitLabConfiguration.AppConfiguration appConfig)
    {
        return newAppInfo(GitLabServerInfo.newServerInfo(serverConfig), appConfig.getId(), appConfig.getSecret(), appConfig.getRedirectURI());
    }

    public static GitLabAppInfo newAppInfo(GitLabServerInfo serverInfo, String appId, String appSecret, String appRedirectURI)
    {
        return new GitLabAppInfo(serverInfo, appId, appSecret, appRedirectURI);
    }
}
