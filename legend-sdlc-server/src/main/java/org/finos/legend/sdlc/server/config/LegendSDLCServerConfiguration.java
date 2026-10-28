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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class LegendSDLCServerConfiguration extends ServerConfiguration
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LegendSDLCServerConfiguration.class);

    // dropwizard-pac4j registers these by class name and unannotated, so each has priority 0 unless configured
    static final String PAC4J_SECURITY_FILTER_NAME = "org.pac4j.jee.filter.SecurityFilter";
    static final String PAC4J_CALLBACK_FILTER_NAME = "org.pac4j.jee.filter.CallbackFilter";
    static final String PAC4J_LOGOUT_FILTER_NAME = "org.pac4j.jee.filter.LogoutFilter";

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
     * The session filter is always given a priority below every pac4j filter, including when the map does not name
     * it or there is no map at all.
     *
     * @return filter priorities, never null
     */
    @Override
    public Map<String, Integer> getFilterPriorities()
    {
        Map<String, Integer> priorities = super.getFilterPriorities();
        Map<String, Integer> resolved = (priorities == null) ? new HashMap<>() : new HashMap<>(priorities);
        if (resolved.containsKey("GitLab") && !resolved.containsKey(BaseLegendSDLCServer.SESSION_FILTER_NAME))
        {
            resolved.put(BaseLegendSDLCServer.SESSION_FILTER_NAME, resolved.get("GitLab"));
        }

        // The session filter builds the server session from the request's pac4j profiles. A profile that is not
        // kept in the session store (a personal-access-token profile, any direct client's profile when the
        // clients are not all serializable) only exists once SecurityFilter has run, so the session filter must
        // run after it (ChainFixingFilterHandler runs higher priorities first). It also runs after the callback
        // and logout filters, which end the chain on their own paths: a session filter that rejects a stored
        // profile it cannot use would otherwise also reject the logout request that clears that profile.
        // An unnamed filter sorts at 0 in registration order, and pac4j's filters register after the session filter.
        Integer sessionPriority = resolved.get(BaseLegendSDLCServer.SESSION_FILTER_NAME);
        int pac4jPriority = Math.min(resolved.getOrDefault(PAC4J_SECURITY_FILTER_NAME, 0),
                Math.min(resolved.getOrDefault(PAC4J_CALLBACK_FILTER_NAME, 0), resolved.getOrDefault(PAC4J_LOGOUT_FILTER_NAME, 0)));
        if (sessionPriority == null)
        {
            resolved.put(BaseLegendSDLCServer.SESSION_FILTER_NAME, pac4jPriority - 1);
        }
        else if (sessionPriority >= pac4jPriority)
        {
            // info, not warn: the shipped filterPriorities (session filter at 1, pac4j's logout filter at 0) get here
            LOGGER.info("filterPriorities would run the {} filter ({}) before one of pac4j's filters (lowest priority {}); running it at {} instead",
                    BaseLegendSDLCServer.SESSION_FILTER_NAME, sessionPriority, pac4jPriority, pac4jPriority - 1);
            resolved.put(BaseLegendSDLCServer.SESSION_FILTER_NAME, pac4jPriority - 1);
        }
        return resolved;
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
