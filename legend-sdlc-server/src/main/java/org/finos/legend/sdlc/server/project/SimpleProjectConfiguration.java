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

package org.finos.legend.sdlc.server.project;

import com.fasterxml.jackson.annotation.JsonSetter;
import org.eclipse.collections.api.factory.Lists;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactGeneration;
import org.finos.legend.sdlc.domain.model.project.configuration.MetamodelDependency;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectStructureVersion;
import org.finos.legend.sdlc.domain.model.version.VersionId;

import java.util.Collections;
import java.util.List;

class SimpleProjectConfiguration implements ProjectConfiguration
{
    private ProjectStructureVersion projectStructureVersion;
    private String projectId;
    private ProjectType projectType;
    private String groupId;
    private String artifactId;
    private List<ProjectDependency> projectDependencies = Collections.emptyList();
    private List<MetamodelDependency> metamodelDependencies = Collections.emptyList();
    private List<ArtifactGeneration> artifactGeneration = Collections.emptyList();


    SimpleProjectConfiguration(String projectId, ProjectType projectType, ProjectStructureVersion projectStructureVersion,
                               String groupId, String artifactId, List<ProjectDependency> projectDependencies,
                               List<MetamodelDependency> metamodelDependencies, List<ArtifactGeneration> artifactGeneration)
    {
        this.projectId = projectId;
        this.projectType = projectType;
        this.projectStructureVersion = projectStructureVersion;
        this.groupId = groupId;
        this.artifactId = artifactId;
        if ((projectDependencies != null) && !projectDependencies.isEmpty())
        {
            this.projectDependencies = Lists.mutable.withAll(projectDependencies);
        }
        if ((metamodelDependencies != null) && !metamodelDependencies.isEmpty())
        {
            this.metamodelDependencies = Lists.mutable.withAll(metamodelDependencies);
        }
        if ((artifactGeneration != null) && !artifactGeneration.isEmpty())
        {
            this.artifactGeneration = Lists.mutable.withAll(artifactGeneration);
        }
    }

    SimpleProjectConfiguration(ProjectConfiguration projectConfiguration)
    {
        this(projectConfiguration.getProjectId(), projectConfiguration.getProjectType(),
                projectConfiguration.getProjectStructureVersion(), projectConfiguration.getGroupId(),
                projectConfiguration.getArtifactId(), projectConfiguration.getProjectDependencies(),
                projectConfiguration.getMetamodelDependencies(), projectConfiguration.getArtifactGenerations());
    }

    SimpleProjectConfiguration()
    {
    }

    @Override
    public String getProjectId()
    {
        return this.projectId;
    }

    public void setProjectId(String projectId)
    {
        this.projectId = projectId;
    }

    @Override
    public ProjectType getProjectType()
    {
        return this.projectType;
    }

    public void setProjectType(ProjectType projectType)
    {
        this.projectType = projectType;
    }

    @Override
    public ProjectStructureVersion getProjectStructureVersion()
    {
        return this.projectStructureVersion;
    }

    public void setProjectStructureVersion(ProjectStructureVersion projectStructureVersion)
    {
        this.projectStructureVersion = projectStructureVersion;
    }

    @JsonSetter("projectStructureVersion")
    public void setSimpleProjectStructureVersion(SimpleProjectStructureVersion projectStructureVersion)
    {
        setProjectStructureVersion(projectStructureVersion);
    }

    public void setProjectStructureVersion(int projectStructureVersion, Integer projectStructureExtensionVersion)
    {
        setProjectStructureVersion(new SimpleProjectStructureVersion(projectStructureVersion, projectStructureExtensionVersion));
    }

    @Override
    public String getGroupId()
    {
        return this.groupId;
    }

    public void setGroupId(String groupId)
    {
        this.groupId = groupId;
    }

    @Override
    public String getArtifactId()
    {
        return this.artifactId;
    }

    public void setArtifactId(String artifactId)
    {
        this.artifactId = artifactId;
    }

    @Override
    public List<ProjectDependency> getProjectDependencies()
    {
        return this.projectDependencies;
    }

    public void setProjectDependencies(List<ProjectDependency> projectDependencies)
    {
        this.projectDependencies = projectDependencies;
    }

    @JsonSetter("projectDependencies")
    public void setSimpleProjectDependencies(List<SimpleProjectDependency> projectDependencies)
    {
        this.projectDependencies = Lists.mutable.withAll(projectDependencies);
    }

    @Override
    public List<MetamodelDependency> getMetamodelDependencies()
    {
        return this.metamodelDependencies;
    }


    public void setMetamodelDependencies(List<MetamodelDependency> metamodelDependencies)
    {
        this.metamodelDependencies = metamodelDependencies;
    }

    @JsonSetter("metamodelDependencies")
    public void setSimpleMetamodelDependencies(List<SimpleMetamodelDependency> metamodelDependencies)
    {
        this.metamodelDependencies = Lists.mutable.withAll(metamodelDependencies);
    }

    @JsonSetter("artifactGenerations")
    public void setSimpleArtifactGenerations(List<SimpleArtifactGeneration> artifactGenerations)
    {
        this.artifactGeneration = (artifactGenerations == null) ? Collections.emptyList() : Lists.mutable.withAll(artifactGenerations);
    }

    public void setArtifactGeneration(List<ArtifactGeneration> artifactGeneration)
    {
        this.artifactGeneration = artifactGeneration;
    }

    public List<ArtifactGeneration> getArtifactGenerations()
    {
        return this.artifactGeneration;
    }

    static class SimpleProjectStructureVersion extends ProjectStructureVersion
    {
        private int version;
        private Integer extensionVersion;

        private SimpleProjectStructureVersion(int version, Integer extensionVersion)
        {
            this.version = version;
            this.extensionVersion = extensionVersion;
        }

        SimpleProjectStructureVersion()
        {
        }

        @Override
        public int getVersion()
        {
            return this.version;
        }

        public void setVersion(int version)
        {
            this.version = version;
        }

        @Override
        public Integer getExtensionVersion()
        {
            return this.extensionVersion;
        }

        public void setExtensionVersion(Integer version)
        {
            this.extensionVersion = version;
        }
    }

    static class SimpleProjectDependency extends ProjectDependency
    {
        String projectId;
        SimpleVersionId versionId;

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

    static class SimpleVersionId extends VersionId
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

    static class SimpleMetamodelDependency extends MetamodelDependency
    {
        String metamodel;
        int version;

        @Override
        public String getMetamodel()
        {
            return this.metamodel;
        }

        @Override
        public int getVersion()
        {
            return this.version;
        }
    }
}
