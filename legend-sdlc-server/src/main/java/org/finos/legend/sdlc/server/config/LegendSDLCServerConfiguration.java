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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
     * The session filter always runs after every pac4j filter, including when the map does not name it or there is
     * no map at all. A configured priority is kept where possible (if it isn't below the default priority, an
     * unprioritised logout or callback filter is given a priority above it instead), so filters without a priority
     * still run after the session filter.
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
        if (sessionPriority == null)
        {
            int pac4jPriority = Math.min(resolved.getOrDefault(PAC4J_SECURITY_FILTER_NAME, 0),
                    Math.min(resolved.getOrDefault(PAC4J_CALLBACK_FILTER_NAME, 0), resolved.getOrDefault(PAC4J_LOGOUT_FILTER_NAME, 0)));
            resolved.put(BaseLegendSDLCServer.SESSION_FILTER_NAME, pac4jPriority - 1);
            return resolved;
        }

        // A configured priority is kept where possible, so the filters that ran after the session filter (and may
        // read the session) still do; with the shipped filterPriorities that is every filter without a priority. It
        // only moves below the pac4j filters that have a priority. An unprioritised logout or callback filter is
        // lifted above it instead, which changes nothing else because each ends the chain on its own path. An
        // unprioritised SecurityFilter can't be lifted without reordering it against every other filter, so then the
        // session filter moves below the default priority. Unprioritised pac4j filters sort at 0, so a session filter
        // below 0 already runs after them, and nothing is lifted.
        int target = sessionPriority;
        for (String pac4jFilter : Arrays.asList(PAC4J_SECURITY_FILTER_NAME, PAC4J_CALLBACK_FILTER_NAME, PAC4J_LOGOUT_FILTER_NAME))
        {
            Integer pac4jPriority = resolved.get(pac4jFilter);
            if (pac4jPriority != null)
            {
                target = Math.min(target, pac4jPriority - 1);
            }
        }
        if (target >= 0)
        {
            if (resolved.containsKey(PAC4J_SECURITY_FILTER_NAME))
            {
                resolved.putIfAbsent(PAC4J_CALLBACK_FILTER_NAME, target + 1);
                resolved.putIfAbsent(PAC4J_LOGOUT_FILTER_NAME, target + 1);
            }
            else
            {
                target = -1;
            }
        }
        if (target != sessionPriority)
        {
            int newPriority = target;
            List<String> passed = resolved.entrySet().stream()
                    .filter(e -> !e.getKey().startsWith("org.pac4j.") && !e.getKey().equals(BaseLegendSDLCServer.SESSION_FILTER_NAME) && !e.getKey().equals("GitLab"))
                    .filter(e -> (e.getValue() != null) && (e.getValue() >= newPriority) && (e.getValue() <= sessionPriority))
                    .map(Map.Entry::getKey)
                    .sorted()
                    .collect(Collectors.toList());
            if ((newPriority < 0) && (sessionPriority >= 0))
            {
                passed.add("every filter without a priority");
            }
            if (passed.isEmpty())
            {
                LOGGER.info("filterPriorities would run the {} filter ({}) before one of pac4j's filters; running it at {} instead",
                        BaseLegendSDLCServer.SESSION_FILTER_NAME, sessionPriority, newPriority);
            }
            else
            {
                LOGGER.warn("filterPriorities would run the {} filter ({}) before one of pac4j's filters; running it at {} instead, after {}: give a filter that reads the SDLC session a priority below {}",
                        BaseLegendSDLCServer.SESSION_FILTER_NAME, sessionPriority, newPriority, passed, newPriority);
            }
            resolved.put(BaseLegendSDLCServer.SESSION_FILTER_NAME, newPriority);
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
