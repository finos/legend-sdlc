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

package org.finos.legend.sdlc.server.domain.model.project.configuration;

import org.finos.legend.sdlc.domain.model.project.configuration.MetamodelDependency;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectStructureVersion;

import java.util.Collections;
import java.util.List;

public class FileSystemProjectConfiguration implements ProjectConfiguration
{
    private final String projectId;
    private final ProjectStructureVersion projectStructureVersion;
    private final String groupId;
    private final String artifactId;

    public FileSystemProjectConfiguration(String projectId, ProjectStructureVersion projectStructureVersion, String groupId, String artifactId)
    {
        this.projectId = projectId;
        this.projectStructureVersion = projectStructureVersion;
        this.groupId = groupId;
        this.artifactId = artifactId;
    }

    @Override
    public String getProjectId()
    {
        return this.projectId;
    }

    @Override
    public ProjectStructureVersion getProjectStructureVersion()
    {
        return this.projectStructureVersion;
    }

    @Override
    public String getGroupId()
    {
        return this.groupId;
    }

    @Override
    public String getArtifactId()
    {
        return this.artifactId;
    }

    @Override
    public List<ProjectDependency> getProjectDependencies()
    {
        return Collections.emptyList();
    }

    @Override
    public List<MetamodelDependency> getMetamodelDependencies()
    {
        return Collections.emptyList();
    }
}
