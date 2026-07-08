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

package org.finos.legend.sdlc.project.structure;

import org.finos.legend.sdlc.domain.model.project.configuration.ConfigurationProperty;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectStructureVersion;

import java.util.Collections;
import java.util.List;

public abstract class ProjectStructureVersionFactory
{
    public abstract int getVersion();

    /**
     * Configuration options this structure version declares (seam S2 of the re-architecture; the schema and its use
     * are owned by the project-structure-configuration-options plan). Defaults to none, so existing versions are
     * unaffected until they opt in.
     *
     * @return declared configuration properties
     */
    public List<ConfigurationProperty> getConfigurationProperties()
    {
        return Collections.emptyList();
    }


    public final ProjectStructure newProjectStructure(ProjectConfiguration projectConfiguration, ProjectStructurePlatformExtensions projectStructurePlatformExtensions)
    {
        if (projectConfiguration != null)
        {
            ProjectStructureVersion projectStructureVersion = projectConfiguration.getProjectStructureVersion();
            if (projectStructureVersion == null)
            {
                throw new IllegalArgumentException("No project structure version: " + projectConfiguration);
            }
            if (getVersion() != projectStructureVersion.getVersion())
            {
                throw new IllegalArgumentException("Only project structures version " + getVersion() + " can be created by this factory; got version " + projectStructureVersion.getVersion());
            }
        }
        else if (!isNullProjectConfigurationAllowed())
        {
            throw new IllegalArgumentException("ProjectConfiguration is null");
        }
        ProjectStructure projectStructure = createProjectStructure(projectConfiguration, projectStructurePlatformExtensions);
        projectStructure.validate();
        return projectStructure;
    }

    protected abstract ProjectStructure createProjectStructure(ProjectConfiguration projectConfiguration, ProjectStructurePlatformExtensions projectStructurePlatformExtensions);

    protected boolean isNullProjectConfigurationAllowed()
    {
        return false;
    }
}
