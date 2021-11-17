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

package org.finos.legend.sdlc.server.inmemory.domain.api;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactGeneration;
import org.finos.legend.sdlc.domain.model.project.configuration.MetamodelDependency;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectStructureVersion;

import java.util.List;

public class InMemoryProjectConfiguration implements ProjectConfiguration
{
    private final MutableList<ProjectDependency> projectDependencies = Lists.mutable.empty();
    private String groupId;
    private String artifactId;

    public void setMavenCoordinates(String groupId, String artifactId)
    {
        this.groupId = groupId;
        this.artifactId = artifactId;
    }

    @Override
    public String getProjectId()
    {
        return null;
    }

    @Override
    public ProjectType getProjectType()
    {
        return null;
    }

    @Override
    public ProjectStructureVersion getProjectStructureVersion()
    {
        return ProjectStructureVersion.newProjectStructureVersion(6);
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
        return this.projectDependencies;
    }

    public void removeDependency(ProjectDependency projectDependency)
    {
        boolean deleted = this.projectDependencies.removeIf(projectDependency::equals);
        if (!deleted)
        {
            throw new IllegalStateException("Failed to delete dependency " + projectDependency);
        }
    }

    @Override
    public List<MetamodelDependency> getMetamodelDependencies()
    {
        return null;
    }

    @Override
    public List<ArtifactGeneration> getArtifactGenerations()
    {
        return null;
    }
}
