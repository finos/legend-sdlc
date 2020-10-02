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

import com.codahale.metrics.health.HealthCheck;
import org.finos.legend.sdlc.server.gitlab.mode.GitLabModeInfos;

public class GitLabServerHealthCheck extends HealthCheck
{
    private final GitLabModeInfos gitLabModeInfos;

    private GitLabServerHealthCheck(GitLabModeInfos gitLabModeInfos)
    {
        this.gitLabModeInfos = gitLabModeInfos;
    }

    @Override
    protected Result check()
    {
        // TODO do a more substantive health check
        return this.gitLabModeInfos.isEmpty() ? Result.unhealthy("No GitLab modes available") : Result.healthy();
    }

    public static GitLabServerHealthCheck fromConfig(GitLabConfiguration config)
    {
        return new GitLabServerHealthCheck(GitLabModeInfos.fromConfig(config));
    }
}
