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

package org.finos.legend.sdlc.server.domain.api.project;

import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactGeneration;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactTypeGenerationConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectStructureVersion;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;

import javax.ws.rs.core.Response;
import java.util.List;

public interface ProjectConfigurationApi
{
    ProjectConfiguration getProjectProjectConfiguration(String projectId);

    ProjectConfiguration getProjectRevisionProjectConfiguration(String projectId, String revisionId); // support revision ID alias

    ProjectConfiguration getUserWorkspaceProjectConfiguration(String projectId, String workspaceId);

    ProjectConfiguration getGroupWorkspaceProjectConfiguration(String projectId, String workspaceId);

    ProjectConfiguration getWorkspaceProjectConfiguration(String projectId, String workspaceId, boolean isGroupWorkspace);

    ProjectConfiguration getBackupWorkspaceProjectConfiguration(String projectId, String workspaceId);

    ProjectConfiguration getUserWorkspaceWithConflictResolutionProjectConfiguration(String projectId, String workspaceId);

    ProjectConfiguration getGroupWorkspaceWithConflictResolutionProjectConfiguration(String projectId, String workspaceId);

    ProjectConfiguration getWorkspaceWithConflictResolutionProjectConfiguration(String projectId, String workspaceId, boolean isGroupWorkspace);

    ProjectConfiguration getUserWorkspaceRevisionProjectConfiguration(String projectId, String workspaceId, String revisionId); // support revision ID alias

    ProjectConfiguration getGroupWorkspaceRevisionProjectConfiguration(String projectId, String workspaceId, String revisionId); // support revision ID alias

    ProjectConfiguration getWorkspaceRevisionProjectConfiguration(String projectId, String workspaceId, boolean isGroupWorkspace, String revisionId); // support revision ID alias

    ProjectConfiguration getBackupWorkspaceRevisionProjectConfiguration(String projectId, String workspaceId, String revisionId); // support revision ID alias

    ProjectConfiguration getWorkspaceWithConflictResolutionRevisionProjectConfiguration(String projectId, String workspaceId, String revisionId); // support revision ID alias

    default ProjectConfiguration getVersionProjectConfiguration(String projectId, String versionIdString)
    {
        VersionId versionId;
        try
        {
            versionId = VersionId.parseVersionId(versionIdString);
        }
        catch (IllegalArgumentException e)
        {
            throw new LegendSDLCServerException(e.getMessage(), Response.Status.BAD_REQUEST, e);
        }
        return getVersionProjectConfiguration(projectId, versionId);
    }

    ProjectConfiguration getVersionProjectConfiguration(String projectId, VersionId versionId);

    ProjectConfiguration getReviewFromProjectConfiguration(String projectId, String reviewId);

    ProjectConfiguration getReviewToProjectConfiguration(String projectId, String reviewId);

    Revision updateProjectConfigurationForUserWorkspace(String projectId, String workspaceId, String message, Integer projectStructureVersion, Integer projectStructureExtensionVersion, String groupId, String artifactId, Iterable<? extends ProjectDependency> projectDependenciesToAdd, Iterable<? extends ProjectDependency> projectDependenciesToRemove, List<? extends ArtifactGeneration> artifactGenerationsToAdd, List<String> artifactGenerationsToRemove);

    Revision updateProjectConfigurationForGroupWorkspace(String projectId, String workspaceId, String message, Integer projectStructureVersion, Integer projectStructureExtensionVersion, String groupId, String artifactId, Iterable<? extends ProjectDependency> projectDependenciesToAdd, Iterable<? extends ProjectDependency> projectDependenciesToRemove, List<? extends ArtifactGeneration> artifactGenerationsToAdd, List<String> artifactGenerationsToRemove);

    Revision updateProjectConfiguration(String projectId, String workspaceId, boolean isGroupWorkspace, String message, Integer projectStructureVersion, Integer projectStructureExtensionVersion, String groupId, String artifactId, Iterable<? extends ProjectDependency> projectDependenciesToAdd, Iterable<? extends ProjectDependency> projectDependenciesToRemove, List<? extends ArtifactGeneration> artifactGenerationsToAdd, List<String> artifactGenerationsToRemove);

    Revision updateProjectConfigurationForWorkspaceWithConflictResolution(String projectId, String workspaceId, String message, Integer projectStructureVersion, Integer projectStructureExtensionVersion, String groupId, String artifactId, Iterable<? extends ProjectDependency> projectDependenciesToAdd, Iterable<? extends ProjectDependency> projectDependenciesToRemove, List<? extends ArtifactGeneration> artifactGenerationsToAdd, List<String> artifactGenerationsToRemove);

    List<ArtifactTypeGenerationConfiguration> getProjectAvailableArtifactGenerations(String projectId);

    List<ArtifactTypeGenerationConfiguration> getRevisionAvailableArtifactGenerations(String projectId, String revisionId);

    List<ArtifactTypeGenerationConfiguration> getUserWorkspaceRevisionAvailableArtifactGenerations(String projectId, String workspaceId, String revisionId);

    List<ArtifactTypeGenerationConfiguration> getGroupWorkspaceRevisionAvailableArtifactGenerations(String projectId, String workspaceId, String revisionId);

    List<ArtifactTypeGenerationConfiguration> getWorkspaceRevisionAvailableArtifactGenerations(String projectId, String workspaceId, boolean isGroupWorkspace, String revisionId);

    List<ArtifactTypeGenerationConfiguration> getUserWorkspaceAvailableArtifactGenerations(String projectId, String workspaceId);

    List<ArtifactTypeGenerationConfiguration> getGroupWorkspaceAvailableArtifactGenerations(String projectId, String workspaceId);

    List<ArtifactTypeGenerationConfiguration> getWorkspaceAvailableArtifactGenerations(String projectId, String workspaceId, boolean isGroupWorkspace);

    List<ArtifactTypeGenerationConfiguration> getVersionAvailableArtifactGenerations(String projectId, String versionId);

    ProjectStructureVersion getLatestProjectStructureVersion();

    List<ArtifactTypeGenerationConfiguration> getLatestAvailableArtifactGenerations();

}
