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

package org.finos.legend.sdlc.server.application.project;

import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactGeneration;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactType;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.version.VersionId;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class UpdateProjectConfigurationCommand
{
    private String message;
    private UpdateProjectConfigProjectStructureVersion projectStructureVersion = new UpdateProjectConfigProjectStructureVersion();
    private String groupId;
    private String artifactId;
    private List<UpdateProjectConfigProjectDependency> projectDependenciesToAdd;
    private List<UpdateProjectConfigProjectDependency> projectDependenciesToRemove;
    private List<UpdateArtifactGeneration> artifactGenerationsToAdd;
    private List<String> artifactGenerationsNamesToRemove;

    public String getMessage()
    {
        return this.message;
    }

    public void setMessage(String message)
    {
        this.message = message;
    }

    public UpdateProjectConfigProjectStructureVersion getProjectStructureVersion()
    {
        return this.projectStructureVersion;
    }

    public void setProjectStructureVersion(UpdateProjectConfigProjectStructureVersion projectStructureVersion)
    {
        this.projectStructureVersion = projectStructureVersion;
    }

    public String getGroupId()
    {
        return this.groupId;
    }

    public void setGroupId(String groupId)
    {
        this.groupId = groupId;
    }

    public String getArtifactId()
    {
        return this.artifactId;
    }

    public void setArtifactId(String artifactId)
    {
        this.artifactId = artifactId;
    }

    public List<UpdateProjectConfigProjectDependency> getProjectDependenciesToAdd()
    {
        return this.projectDependenciesToAdd;
    }

    public void setProjectDependenciesToAdd(List<UpdateProjectConfigProjectDependency> projectDependenciesToAdd)
    {
        this.projectDependenciesToAdd = projectDependenciesToAdd;
    }

    public List<UpdateProjectConfigProjectDependency> getProjectDependenciesToRemove()
    {
        return this.projectDependenciesToRemove;
    }

    public void setProjectDependenciesToRemove(List<UpdateProjectConfigProjectDependency> projectDependenciesToRemove)
    {
        this.projectDependenciesToRemove = projectDependenciesToRemove;
    }

    public List<UpdateArtifactGeneration> getArtifactGenerationsToAdd()
    {
        return artifactGenerationsToAdd;
    }

    public void setArtifactGenerationsToAdd(List<UpdateArtifactGeneration> artifactGenerationsToAdd)
    {
        this.artifactGenerationsToAdd = artifactGenerationsToAdd;
    }

    public List<String> getArtifactGenerationsNamesToRemove()
    {
        return artifactGenerationsNamesToRemove;
    }

    public void setArtifactGenerationsNamesToRemove(List<String> artifactGenerationsNamesToRemove)
    {
        this.artifactGenerationsNamesToRemove = artifactGenerationsNamesToRemove;
    }

    public static class UpdateProjectConfigProjectStructureVersion
    {
        Integer version;
        Integer extensionVersion;

        public Integer getVersion()
        {
            return this.version;
        }

        public Integer getExtensionVersion()
        {
            return this.extensionVersion;
        }
    }

    public static class UpdateArtifactGeneration implements ArtifactGeneration
    {

        String name;
        ArtifactType type;
        Map<String, Object> parameters = Collections.emptyMap();

        @Override
        public String getName()
        {
            return this.name;
        }

        @Override
        public ArtifactType getType()
        {
            return this.type;
        }

        @Override
        public Map<String, Object> getParameters()
        {
            return this.parameters;
        }
    }

    public static class UpdateProjectConfigProjectDependency extends ProjectDependency
    {
        String projectId;
        UpdateProjectConfigVersionId versionId;

        @Override
        public String getProjectId()
        {
            return this.projectId;
        }

        @Override
        public VersionId getVersionId()
        {
            return this.versionId;
        }
    }

    static class UpdateProjectConfigVersionId extends VersionId
    {
        int majorVersion;
        int minorVersion;
        int patchVersion;

        @Override
        public int getMajorVersion()
        {
            return this.majorVersion;
        }

        @Override
        public int getMinorVersion()
        {
            return this.minorVersion;
        }

        @Override
        public int getPatchVersion()
        {
            return this.patchVersion;
        }
    }
}
