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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactGeneration;
import org.finos.legend.sdlc.domain.model.project.configuration.MetamodelDependency;
import org.finos.legend.sdlc.domain.model.project.configuration.PlatformConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectStructureVersion;

import java.util.Collections;
import java.util.List;

public class SimpleProjectConfiguration implements ProjectConfiguration
{
    private final String projectId;
    private ProjectType projectType;
    private ProjectStructureVersion projectStructureVersion;
    private List<PlatformConfiguration> platformConfigurations;
    private String groupId;
    private String artifactId;
    private List<ProjectDependency> projectDependencies;
    private List<MetamodelDependency> metamodelDependencies;
    private List<ArtifactGeneration> artifactGeneration;

    private SimpleProjectConfiguration(String projectId, ProjectType projectType, ProjectStructureVersion projectStructureVersion, List<PlatformConfiguration> platformConfigurations, String groupId, String artifactId, List<ProjectDependency> projectDependencies, List<MetamodelDependency> metamodelDependencies, List<ArtifactGeneration> artifactGeneration)
    {
        this.projectId = projectId;
        this.projectType = projectType;
        this.projectStructureVersion = projectStructureVersion;
        this.platformConfigurations = platformConfigurations;
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.projectDependencies = (projectDependencies == null) ? Collections.emptyList() : projectDependencies;
        this.metamodelDependencies = (metamodelDependencies == null) ? Collections.emptyList() : metamodelDependencies;
        this.artifactGeneration = (artifactGeneration == null) ? Collections.emptyList() : artifactGeneration;
    }

    SimpleProjectConfiguration(ProjectConfiguration config)
    {
        this(config.getProjectId(), config.getProjectType(), config.getProjectStructureVersion(), config.getPlatformConfigurations(), config.getGroupId(), config.getArtifactId(), config.getProjectDependencies(), config.getMetamodelDependencies(), config.getArtifactGenerations());
    }

    @Override
    public String getProjectId()
    {
        return this.projectId;
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

    public void setProjectStructureVersion(int projectStructureVersion, Integer projectStructureExtensionVersion)
    {
        setProjectStructureVersion(ProjectStructureVersion.newProjectStructureVersion(projectStructureVersion, projectStructureExtensionVersion));
    }

    @Override
    public List<PlatformConfiguration> getPlatformConfigurations()
    {
        return this.platformConfigurations;
    }

    public void setPlatformConfigurations(List<PlatformConfiguration> platformConfigurations)
    {
        this.platformConfigurations = platformConfigurations;
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

    @Override
    public List<MetamodelDependency> getMetamodelDependencies()
    {
        return this.metamodelDependencies;
    }

    public void setMetamodelDependencies(List<MetamodelDependency> metamodelDependencies)
    {
        this.metamodelDependencies = metamodelDependencies;
    }

    @Override
    public List<ArtifactGeneration> getArtifactGenerations()
    {
        return this.artifactGeneration;
    }

    @Deprecated
    public void setArtifactGeneration(List<ArtifactGeneration> artifactGeneration)
    {
        this.artifactGeneration = artifactGeneration;
    }

    @JsonCreator
    static SimpleProjectConfiguration newConfiguration(
            @JsonProperty("projectId") String projectId,
            @JsonProperty("projectType") ProjectType projectType,
            @JsonProperty("projectStructureVersion") @JsonDeserialize(as = SimpleProjectStructureVersion.class) ProjectStructureVersion projectStructureVersion,
            @JsonProperty("platformConfigurations") @JsonDeserialize(contentAs = SimplePlatformConfiguration.class) List<PlatformConfiguration> platforms,
            @JsonProperty("groupId") String groupId,
            @JsonProperty("artifactId") String artifactId,
            @JsonProperty("projectDependencies") @JsonDeserialize(contentAs = SimpleProjectDependency.class) List<ProjectDependency> projectDependencies,
            @JsonProperty("metamodelDependencies") @JsonDeserialize(contentAs = SimpleMetamodelDependency.class) List<MetamodelDependency> metamodelDependencies,
            @Deprecated @JsonProperty("artifactGenerations") @JsonDeserialize(contentAs = SimpleArtifactGeneration.class) List<ArtifactGeneration> artifactGenerations)
    {
        return new SimpleProjectConfiguration(projectId, projectType, projectStructureVersion, platforms, groupId, artifactId, projectDependencies, metamodelDependencies, artifactGenerations);
    }

    @Deprecated
    static SimpleProjectConfiguration newConfiguration(String projectId, ProjectStructureVersion projectStructureVersion, String groupId, String artifactId, List<ProjectDependency> projectDependencies, List<MetamodelDependency> metamodelDependencies, List<ArtifactGeneration> artifactGenerations)
    {
        return newConfiguration(projectId, ProjectType.MANAGED, projectStructureVersion, null, groupId, artifactId, projectDependencies, metamodelDependencies, artifactGenerations);
    }

    static SimpleProjectConfiguration newConfiguration(String projectId, ProjectStructureVersion projectStructureVersion, List<PlatformConfiguration> platforms, String groupId, String artifactId, List<ProjectDependency> projectDependencies, List<MetamodelDependency> metamodelDependencies, List<ArtifactGeneration> artifactGenerations)
    {
        return newConfiguration(projectId, ProjectType.MANAGED, projectStructureVersion, platforms, groupId, artifactId, projectDependencies, metamodelDependencies, artifactGenerations);
    }

    public static class SimpleProjectStructureVersion extends ProjectStructureVersion
    {
        private final int version;
        private final Integer extensionVersion;

        @JsonCreator
        private SimpleProjectStructureVersion(@JsonProperty("version") int version, @JsonProperty("extensionVersion") Integer extensionVersion)
        {
            this.version = version;
            this.extensionVersion = extensionVersion;
        }

        @Override
        public int getVersion()
        {
            return this.version;
        }

        @Override
        public Integer getExtensionVersion()
        {
            return this.extensionVersion;
        }
    }

    public static class SimpleMetamodelDependency extends MetamodelDependency
    {
        private final String metamodel;
        private final int version;

        @JsonCreator
        private SimpleMetamodelDependency(@JsonProperty("metamodel") String metamodel, @JsonProperty("version") int version)
        {
            this.metamodel = metamodel;
            this.version = version;
        }

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

    public static class SimplePlatformConfiguration implements PlatformConfiguration
    {
        private final String name;
        private final String version;

        @JsonCreator
        private SimplePlatformConfiguration(@JsonProperty("name") String name, @JsonProperty("version") String version)
        {
            this.name = name;
            this.version = version;
        }

        @Override
        public String getName()
        {
            return this.name;
        }

        @Override
        public String getVersion()
        {
            return this.version;
        }
    }
}
