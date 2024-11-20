// Copyright 2023 Goldman Sachs
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

package org.finos.legend.sdlc.server.backend;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.finos.legend.sdlc.server.gitlab.GitLabConfiguration;

public class BackendConfiguration
{
    private final GitLabConfiguration gitLabConfig;

    private BackendConfiguration(GitLabConfiguration gitLabConfig)
    {
        this.gitLabConfig = gitLabConfig;
    }

    public GitLabConfiguration getGitLabConfiguration()
    {
        return this.gitLabConfig;
    }

    @JsonCreator
    public static BackendConfiguration newBackendConfiguration(@JsonProperty("gitLab") GitLabConfiguration gitLabConfig)
    {
        return new BackendConfiguration(gitLabConfig);
    }
}
