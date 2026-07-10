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

import org.finos.legend.sdlc.backend.api.spi.Backend;
import org.finos.legend.sdlc.backend.api.spi.BackendConfiguration;
import org.finos.legend.sdlc.backend.api.spi.BackendEnvironment;
import org.finos.legend.sdlc.backend.api.spi.BackendFactory;
import org.finos.legend.sdlc.server.project.config.ProjectStructureConfiguration;

public class GitLabBackendFactory implements BackendFactory
{
    public static final String TYPE = "gitlab";

    @Override
    public String getType()
    {
        return TYPE;
    }

    @Override
    public Class<? extends BackendConfiguration> getConfigurationClass()
    {
        return GitLabBackendConfiguration.class;
    }

    @Override
    public Backend build(BackendConfiguration configuration, BackendEnvironment environment)
    {
        if (!(configuration instanceof GitLabBackendConfiguration))
        {
            throw new IllegalArgumentException("Expected GitLab backend configuration; got: " + ((configuration == null) ? null : configuration.getClass().getName()));
        }
        return new GitLabBackend(((GitLabBackendConfiguration) configuration).getGitLabConfiguration(), environment.getService(ProjectStructureConfiguration.class), environment);
    }
}
