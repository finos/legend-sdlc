// Copyright 2026 Goldman Sachs
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.finos.legend.sdlc.backend.api.spi.BackendConfiguration;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabAuthorizer;

import java.util.List;

/**
 * The {@code backend: {type: gitlab, ...}} configuration: the GitLab configuration's fields, inline. The legacy
 * {@code uat}/{@code prod} mode sections are not supported in this form — deployments migrating to the
 * {@code backend:} section use the flat server/app layout.
 */
public class GitLabBackendConfiguration extends BackendConfiguration
{
    private final GitLabConfiguration gitLabConfiguration;

    @JsonCreator
    public GitLabBackendConfiguration(
            @JsonProperty("projectTag") String projectTag,
            @JsonProperty("projectIdPrefix") String projectIdPrefix,
            @JsonProperty("auth") GitLabConfiguration.AuthConfiguration authConfig,
            @JsonProperty("server") GitLabConfiguration.ServerConfiguration serverConfig,
            @JsonProperty("app") GitLabConfiguration.AppConfiguration appConfig,
            @JsonProperty("newProjectVisibility") GitLabConfiguration.NewProjectVisibility newProjectVisibility,
            @JsonProperty("gitlabAuthorizers") List<GitLabAuthorizer> gitLabAuthorizers)
    {
        this.gitLabConfiguration = GitLabConfiguration.newGitLabConfiguration(projectTag, projectIdPrefix, authConfig, serverConfig, appConfig, newProjectVisibility, gitLabAuthorizers);
    }

    /**
     * Adapter constructor for the legacy top-level {@code gitLab:} configuration section.
     */
    public GitLabBackendConfiguration(GitLabConfiguration gitLabConfiguration)
    {
        this.gitLabConfiguration = gitLabConfiguration;
    }

    public GitLabConfiguration getGitLabConfiguration()
    {
        return this.gitLabConfiguration;
    }
}
