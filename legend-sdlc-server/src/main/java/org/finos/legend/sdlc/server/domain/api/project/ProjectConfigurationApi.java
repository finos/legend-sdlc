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

import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactTypeGenerationConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectStructureVersion;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.project.ProjectConfigurationStatusReport;

import java.util.Collections;
import java.util.List;
import javax.ws.rs.core.Response;

public interface ProjectConfigurationApi
{
    ProjectConfiguration getProjectProjectConfiguration(String projectId, String patchReleaseVersion);

    default ProjectConfiguration getProjectProjectConfiguration(String projectId)
    {
        return this.getProjectProjectConfiguration(projectId, null);
    }

    default ProjectConfiguration getProjectRevisionProjectConfiguration(String projectId, String revisionId)
    {
        return this.getProjectRevisionProjectConfiguration(projectId, null, revisionId);
    }

    ProjectConfiguration getProjectRevisionProjectConfiguration(String projectId, String patchReleaseVersion, String revisionId); // support revision ID alias

    default ProjectConfiguration getUserWorkspaceProjectConfiguration(String projectId, String workspaceId)
    {
        return this.getWorkspaceProjectConfiguration(projectId, null, workspaceId, WorkspaceType.USER);
    }

    default ProjectConfiguration getGroupWorkspaceProjectConfiguration(String projectId, String workspaceId)
    {
        return this.getWorkspaceProjectConfiguration(projectId, null, workspaceId, WorkspaceType.GROUP);
    }

    ProjectConfiguration getWorkspaceProjectConfiguration(String projectId, String patchReleaseVersion, String workspaceId, WorkspaceType workspaceType);

    default ProjectConfiguration getBackupUserWorkspaceProjectConfiguration(String projectId, String workspaceId)
    {
        return this.getWorkspaceProjectConfiguration(projectId, null, workspaceId, WorkspaceType.USER);
    }

    default ProjectConfiguration getBackupGroupWorkspaceProjectConfiguration(String projectId, String workspaceId)
    {
        return this.getWorkspaceProjectConfiguration(projectId, null, workspaceId, WorkspaceType.GROUP);
    }

    ProjectConfiguration getBackupWorkspaceProjectConfiguration(String projectId, String patchReleaseVersion, String workspaceId, WorkspaceType workspaceType);

    default ProjectConfiguration getUserWorkspaceWithConflictResolutionProjectConfiguration(String projectId, String workspaceId)
    {
        return this.getWorkspaceWithConflictResolutionProjectConfiguration(projectId, null, workspaceId, WorkspaceType.USER);
    }

    default ProjectConfiguration getGroupWorkspaceWithConflictResolutionProjectConfiguration(String projectId, String workspaceId)
    {
        return this.getWorkspaceWithConflictResolutionProjectConfiguration(projectId, null, workspaceId, WorkspaceType.GROUP);
    }

    ProjectConfiguration getWorkspaceWithConflictResolutionProjectConfiguration(String projectId, String patchReleaseVersion, String workspaceId, WorkspaceType workspaceType);

    default ProjectConfiguration getUserWorkspaceRevisionProjectConfiguration(String projectId, String workspaceId, String revisionId) // support revision ID alias
    {
        return this.getWorkspaceRevisionProjectConfiguration(projectId, workspaceId, WorkspaceType.USER, revisionId);
    }

    default ProjectConfiguration getGroupWorkspaceRevisionProjectConfiguration(String projectId, String workspaceId, String revisionId) // support revision ID alias
    {
        return this.getWorkspaceRevisionProjectConfiguration(projectId, workspaceId, WorkspaceType.GROUP, revisionId);
    }

    ProjectConfiguration getWorkspaceRevisionProjectConfiguration(String projectId, String patchReleaseVersion, String workspaceId, WorkspaceType workspaceType, String revisionId); // support revision ID alias

    default ProjectConfiguration getWorkspaceRevisionProjectConfiguration(String projectId, String workspaceId, WorkspaceType workspaceType, String revisionId)
    {
        return this.getWorkspaceRevisionProjectConfiguration(projectId, null, workspaceId, workspaceType, revisionId);
    }

    default ProjectConfiguration getBackupUserWorkspaceRevisionProjectConfiguration(String projectId, String workspaceId, String revisionId) // support revision ID alias
    {
        return this.getBackupWorkspaceRevisionProjectConfiguration(projectId, null, workspaceId, WorkspaceType.USER, revisionId);
    }

    default ProjectConfiguration getBackupGroupWorkspaceRevisionProjectConfiguration(String projectId, String workspaceId, String revisionId) // support revision ID alias
    {
        return this.getBackupWorkspaceRevisionProjectConfiguration(projectId, null, workspaceId, WorkspaceType.GROUP, revisionId);
    }

    ProjectConfiguration getBackupWorkspaceRevisionProjectConfiguration(String projectId, String patchReleaseVersion, String workspaceId, WorkspaceType workspaceType, String revisionId); // support revision ID alias

    default ProjectConfiguration getUserWorkspaceWithConflictResolutionRevisionProjectConfiguration(String projectId, String workspaceId, String revisionId) // support revision ID alias
    {
        return this.getWorkspaceWithConflictResolutionRevisionProjectConfiguration(projectId, null, workspaceId, WorkspaceType.USER, revisionId);
    }

    default ProjectConfiguration getGroupWorkspaceWithConflictResolutionRevisionProjectConfiguration(String projectId, String workspaceId, String revisionId) // support revision ID alias
    {
        return this.getWorkspaceWithConflictResolutionRevisionProjectConfiguration(projectId, null, workspaceId, WorkspaceType.GROUP, revisionId);
    }

    ProjectConfiguration getWorkspaceWithConflictResolutionRevisionProjectConfiguration(String projectId, String patchReleaseVersion, String workspaceId, WorkspaceType workspaceType, String revisionId); // support revision ID alias

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

    ProjectConfiguration getReviewFromProjectConfiguration(String projectId, String patchReleaseVersion, String reviewId);

    default ProjectConfiguration getReviewFromProjectConfiguration(String projectId, String reviewId)
    {
        return this.getReviewFromProjectConfiguration(projectId, null, reviewId);
    }

    ProjectConfiguration getReviewToProjectConfiguration(String projectId, String patchReleaseVersion, String reviewId);

    default ProjectConfiguration getReviewToProjectConfiguration(String projectId, String reviewId)
    {
        return this.getReviewToProjectConfiguration(projectId, null, reviewId);
    }

    Revision updateProjectConfiguration(String projectId, String patchReleaseVersion, String workspaceId, WorkspaceType workspaceType, String message, ProjectConfigurationUpdater updater);

    default Revision updateProjectConfiguration(String projectId, String workspaceId, WorkspaceType workspaceType, String message, ProjectConfigurationUpdater updater)
    {
        return this.updateProjectConfiguration(projectId, null, workspaceId, workspaceType, message, updater);
    }

    Revision updateProjectConfigurationForWorkspaceWithConflictResolution(String projectId, String workspaceId, String message, ProjectConfigurationUpdater updater);

    List<ArtifactTypeGenerationConfiguration> getProjectAvailableArtifactGenerations(String projectId, String patchReleaseVersion);

    default List<ArtifactTypeGenerationConfiguration> getProjectAvailableArtifactGenerations(String projectId)
    {
        return this.getProjectAvailableArtifactGenerations(projectId, null);
    }

    List<ArtifactTypeGenerationConfiguration> getRevisionAvailableArtifactGenerations(String projectId, String revisionId);

    default List<ArtifactTypeGenerationConfiguration> getUserWorkspaceRevisionAvailableArtifactGenerations(String projectId, String workspaceId, String revisionId)
    {
        return this.getWorkspaceRevisionAvailableArtifactGenerations(projectId, workspaceId, WorkspaceType.USER, revisionId);
    }

    default List<ArtifactTypeGenerationConfiguration> getGroupWorkspaceRevisionAvailableArtifactGenerations(String projectId, String workspaceId, String revisionId)
    {
        return this.getWorkspaceRevisionAvailableArtifactGenerations(projectId, workspaceId, WorkspaceType.GROUP, revisionId);
    }

    List<ArtifactTypeGenerationConfiguration> getWorkspaceRevisionAvailableArtifactGenerations(String projectId, String patchReleaseVersion, String workspaceId, WorkspaceType workspaceType, String revisionId);

    default List<ArtifactTypeGenerationConfiguration> getWorkspaceRevisionAvailableArtifactGenerations(String projectId, String workspaceId, WorkspaceType workspaceType, String revisionId)
    {
        return this.getWorkspaceRevisionAvailableArtifactGenerations(projectId, null, workspaceId, workspaceType, revisionId);
    }

    default List<ArtifactTypeGenerationConfiguration> getUserWorkspaceAvailableArtifactGenerations(String projectId, String workspaceId)
    {
        return this.getWorkspaceAvailableArtifactGenerations(projectId, null, workspaceId, WorkspaceType.USER);
    }

    default List<ArtifactTypeGenerationConfiguration> getGroupWorkspaceAvailableArtifactGenerations(String projectId, String workspaceId)
    {
        return this.getWorkspaceAvailableArtifactGenerations(projectId, null, workspaceId, WorkspaceType.GROUP);
    }

    List<ArtifactTypeGenerationConfiguration> getWorkspaceAvailableArtifactGenerations(String projectId, String patchReleaseVersion, String workspaceId, WorkspaceType workspaceType);

    List<ArtifactTypeGenerationConfiguration> getVersionAvailableArtifactGenerations(String projectId, String versionId);

    ProjectStructureVersion getLatestProjectStructureVersion();

    @Deprecated
    default List<ArtifactTypeGenerationConfiguration> getLatestAvailableArtifactGenerations()
    {
        return Collections.emptyList();
    }

    default ProjectConfigurationStatusReport getProjectConfigurationStatus(String projectId)
    {
        return  this.getProjectConfigurationStatus(projectId, null);
    }

    ProjectConfigurationStatusReport getProjectConfigurationStatus(String projectId, String patchReleaseversion);
}
