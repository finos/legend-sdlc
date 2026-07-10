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

package org.finos.legend.sdlc.server.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.finos.legend.sdlc.backend.api.spi.BackendConfiguration;
import org.finos.legend.sdlc.server.depot.DepotConfiguration;
import org.finos.legend.sdlc.server.gitlab.GitLabBackendConfiguration;
import org.finos.legend.sdlc.server.gitlab.GitLabConfiguration;
import org.finos.legend.sdlc.server.project.config.ProjectStructureConfiguration;

public class LegendSDLCServerConfiguration extends ServerConfiguration
{
    @JsonProperty("gitLab")
    private GitLabConfiguration gitLabConfig;

    @JsonProperty("backend")
    private BackendConfiguration backendConfiguration;

    @JsonProperty("projectStructure")
    private ProjectStructureConfiguration projectStructureConfiguration;

    @JsonProperty("depot")
    private DepotConfiguration depotConfiguration;

    @JsonProperty("features")
    private LegendSDLCServerFeaturesConfiguration featuresConfiguration;

    /**
     * The GitLab configuration: the legacy top-level {@code gitLab:} section if present, otherwise the one
     * embedded in a {@code backend: {type: gitlab, ...}} section. The fallback keeps the GitLab bundle, app
     * info, and auth machinery working for deployments that have migrated to the {@code backend:} form.
     *
     * @return GitLab configuration or null
     */
    public GitLabConfiguration getGitLabConfiguration()
    {
        if (this.gitLabConfig != null)
        {
            return this.gitLabConfig;
        }
        return (this.backendConfiguration instanceof GitLabBackendConfiguration) ? ((GitLabBackendConfiguration) this.backendConfiguration).getGitLabConfiguration() : null;
    }

    /**
     * The backend configuration: the {@code backend:} section if present; otherwise, synthesized from a legacy
     * top-level {@code gitLab:} section (the transition adapter — a legacy GitLab deployment needs no config
     * change to select the GitLab backend).
     *
     * @return backend configuration or null
     */
    public BackendConfiguration getBackendConfiguration()
    {
        if (this.backendConfiguration != null)
        {
            return this.backendConfiguration;
        }
        return (this.gitLabConfig == null) ? null : new GitLabBackendConfiguration(this.gitLabConfig);
    }

    public ProjectStructureConfiguration getProjectStructureConfiguration()
    {
        return this.projectStructureConfiguration;
    }

    public DepotConfiguration getDepotConfiguration()
    {
        return this.depotConfiguration;
    }

    public LegendSDLCServerFeaturesConfiguration getFeaturesConfiguration()
    {
        return this.featuresConfiguration;
    }
}
