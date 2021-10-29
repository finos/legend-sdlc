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
import org.finos.legend.sdlc.server.depot.DepotConfiguration;
import org.finos.legend.sdlc.server.gitlab.GitLabConfiguration;
import org.finos.legend.sdlc.server.project.config.ProjectStructureConfiguration;

public class LegendSDLCServerConfiguration extends ServerConfiguration
{
    @JsonProperty("gitLab")
    private GitLabConfiguration gitLabConfig;

    @JsonProperty("projectStructure")
    private ProjectStructureConfiguration projectStructureConfiguration;

    @JsonProperty("depot")
    private DepotConfiguration depotConfiguration;

    public GitLabConfiguration getGitLabConfiguration()
    {
        return this.gitLabConfig;
    }

    public ProjectStructureConfiguration getProjectStructureConfiguration()
    {
        return this.projectStructureConfiguration;
    }

    public DepotConfiguration getDepotConfiguration()
    {
        return this.depotConfiguration;
    }
}
