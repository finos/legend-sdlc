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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactGeneration;
import org.finos.legend.sdlc.domain.model.project.configuration.PlatformConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.server.domain.api.project.ProjectConfigurationUpdater;
import org.finos.legend.sdlc.server.project.SimpleArtifactGeneration;
import org.finos.legend.sdlc.server.project.SimpleProjectConfiguration;
import org.finos.legend.sdlc.server.project.SimpleProjectDependency;

import java.util.List;

public class UpdateProjectConfigurationCommand
{
    private final String message;
    private final UpdateProjectConfigProjectStructureVersion projectStructureVersion;
    private final ProjectType projectType;
    private final String groupId;
    private final String artifactId;
    private final UpdatePlatformConfigurationsCommand platformConfigurations;
    private final List<ProjectDependency> projectDependenciesToAdd;
    private final List<ProjectDependency> projectDependenciesToRemove;
    private final List<ArtifactGeneration> artifactGenerationsToAdd;
    private final List<String> artifactGenerationsNamesToRemove;

    @JsonCreator
    public UpdateProjectConfigurationCommand(
            @JsonProperty("message") String message,
            @JsonProperty("projectStructureVersion") UpdateProjectConfigProjectStructureVersion projectStructureVersion,
            @JsonProperty("projectType") ProjectType projectType,
            @JsonProperty("groupId") String groupId,
            @JsonProperty("artifactId") String artifactId,
            @JsonProperty("platformConfigurations") UpdatePlatformConfigurationsCommand platformConfigurations,
            @JsonProperty("projectDependenciesToAdd") @JsonDeserialize(contentAs = SimpleProjectDependency.class) List<ProjectDependency> projectDependenciesToAdd,
            @JsonProperty("projectDependenciesToRemove") @JsonDeserialize(contentAs = SimpleProjectDependency.class) List<ProjectDependency> projectDependenciesToRemove,
            @JsonProperty("artifactGenerationsToAdd") @JsonDeserialize(contentAs = SimpleArtifactGeneration.class) List<ArtifactGeneration> artifactGenerationsToAdd,
            @JsonProperty("artifactGenerationsToRemove") List<String> artifactGenerationNamesToRemove)
    {
        this.message = message;
        this.projectStructureVersion = projectStructureVersion;
        this.projectType = projectType;
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.platformConfigurations = platformConfigurations;
        this.projectDependenciesToAdd = projectDependenciesToAdd;
        this.projectDependenciesToRemove = projectDependenciesToRemove;
        this.artifactGenerationsToAdd = artifactGenerationsToAdd;
        this.artifactGenerationsNamesToRemove = artifactGenerationNamesToRemove;
    }

    public String getMessage()
    {
        return this.message;
    }

    public ProjectConfigurationUpdater getProjectConfigurationUpdater()
    {
        ProjectConfigurationUpdater configUpdater = ProjectConfigurationUpdater.newUpdater()
                .withGroupId(this.groupId)
                .withArtifactId(this.artifactId)
                .withProjectDependenciesToAdd(this.projectDependenciesToAdd)
                .withProjectDependenciesToRemove(this.projectDependenciesToRemove)
                .withArtifactGenerationsToAdd(this.artifactGenerationsToAdd)
                .withArtifactGenerationsToRemove(this.artifactGenerationsNamesToRemove);
        if (this.projectType != null)
        {
            configUpdater.withProjectType(this.projectType);
        }
        if (this.projectStructureVersion != null)
        {
            configUpdater.withProjectStructureVersion(this.projectStructureVersion.getVersion())
                    .withProjectStructureExtensionVersion(this.projectStructureVersion.getExtensionVersion());
        }
        if (this.platformConfigurations != null)
        {
            configUpdater.setPlatformConfigurations(this.platformConfigurations.getPlatformConfigurations());
        }
        return configUpdater;
    }

    private static class UpdateProjectConfigProjectStructureVersion
    {
        private final Integer version;
        private final Integer extensionVersion;

        @JsonCreator
        private UpdateProjectConfigProjectStructureVersion(@JsonProperty("version") Integer version, @JsonProperty("extensionVersion") Integer extensionVersion)
        {
            this.version = version;
            this.extensionVersion = extensionVersion;
        }

        public Integer getVersion()
        {
            return this.version;
        }

        public Integer getExtensionVersion()
        {
            return this.extensionVersion;
        }
    }

    private static class UpdatePlatformConfigurationsCommand
    {
        private final List<PlatformConfiguration> platformConfigurations;

        @JsonCreator
        private UpdatePlatformConfigurationsCommand(@JsonProperty("platformConfigurations") @JsonDeserialize(contentAs = SimpleProjectConfiguration.SimplePlatformConfiguration.class) List<PlatformConfiguration> platformConfigurations)
        {
            this.platformConfigurations = platformConfigurations;
        }

        public List<PlatformConfiguration> getPlatformConfigurations()
        {
            return this.platformConfigurations;
        }
    }
}
