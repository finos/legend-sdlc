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
import com.fasterxml.jackson.databind.JsonNode;
import org.finos.legend.sdlc.backend.api.spi.BackendConfiguration;
import org.finos.legend.sdlc.server.BaseLegendSDLCServer;
import org.finos.legend.sdlc.server.depot.DepotConfiguration;
import org.finos.legend.sdlc.server.project.config.ProjectStructureConfiguration;

import java.util.HashMap;
import java.util.Map;

public class LegendSDLCServerConfiguration extends ServerConfiguration
{
    @JsonProperty("gitLab")
    private JsonNode legacyGitLabConfig;

    @JsonProperty("backend")
    private BackendConfiguration backendConfiguration;

    @JsonProperty("projectStructure")
    private ProjectStructureConfiguration projectStructureConfiguration;

    @JsonProperty("depot")
    private DepotConfiguration depotConfiguration;

    @JsonProperty("features")
    private LegendSDLCServerFeaturesConfiguration featuresConfiguration;

    /**
     * The backend configuration: the {@code backend:} section, or null if there is none (see
     * {@link #getLegacyGitLabConfig()} for the legacy adapter's raw section).
     *
     * @return backend configuration or null
     */
    public BackendConfiguration getBackendConfiguration()
    {
        return this.backendConfiguration;
    }

    /**
     * The legacy top-level {@code gitLab:} section, held as raw JSON: the server no longer compiles against the
     * GitLab backend's configuration class, so the transition adapter (a legacy GitLab deployment needs no
     * config change to select the GitLab backend) synthesizes {@code backend: {type: gitlab, ...}} from this
     * node through the configuration object mapper when no {@code backend:} section is present.
     *
     * @return legacy GitLab configuration section or null
     */
    public JsonNode getLegacyGitLabConfig()
    {
        return this.legacyGitLabConfig;
    }

    /**
     * Filter priorities, with an alias for the session filter's historical registered name: the filter that
     * creates the server session was named "GitLab" before the backend extraction, and deployments order it via
     * this map, so a "GitLab" entry applies to the session filter unless the new name is configured explicitly.
     *
     * @return filter priorities
     */
    @Override
    public Map<String, Integer> getFilterPriorities()
    {
        Map<String, Integer> priorities = super.getFilterPriorities();
        if ((priorities != null) && priorities.containsKey("GitLab") && !priorities.containsKey(BaseLegendSDLCServer.SESSION_FILTER_NAME))
        {
            Map<String, Integer> aliased = new HashMap<>(priorities);
            aliased.put(BaseLegendSDLCServer.SESSION_FILTER_NAME, priorities.get("GitLab"));
            return aliased;
        }
        return priorities;
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
