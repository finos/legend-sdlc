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
    ProjectConfiguration getProjectProjectConfiguration(String projectId, VersionId patchReleaseVersionId);

    default ProjectConfiguration getProjectProjectConfiguration(String projectId)
    {
        return this.getProjectProjectConfiguration(projectId, null);
    }

    default ProjectConfiguration getProjectRevisionProjectConfiguration(String projectId, String revisionId)
    {
        return this.getProjectRevisionProjectConfiguration(projectId, null, revisionId);
    }

    ProjectConfiguration getProjectRevisionProjectConfiguration(String projectId, VersionId patchReleaseVersionId, String revisionId); // support revision ID alias

    default ProjectConfiguration getUserWorkspaceProjectConfiguration(String projectId, String workspaceId)
    {
        return this.getWorkspaceProjectConfiguration(projectId, SourceSpecification.newUserWorkspaceSourceSpecification(workspaceId));
    }

    default ProjectConfiguration getGroupWorkspaceProjectConfiguration(String projectId, String workspaceId)
    {
        return this.getWorkspaceProjectConfiguration(projectId, SourceSpecification.newGroupWorkspaceSourceSpecification(workspaceId));
    }

    ProjectConfiguration getWorkspaceProjectConfiguration(String projectId, SourceSpecification sourceSpecification);

    default ProjectConfiguration getBackupUserWorkspaceProjectConfiguration(String projectId, String workspaceId)
    {
        return this.getWorkspaceProjectConfiguration(projectId, SourceSpecification.newUserWorkspaceSourceSpecification(workspaceId));
    }

    default ProjectConfiguration getBackupGroupWorkspaceProjectConfiguration(String projectId, String workspaceId)
    {
        return this.getWorkspaceProjectConfiguration(projectId, SourceSpecification.newGroupWorkspaceSourceSpecification(workspaceId));
    }

    ProjectConfiguration getBackupWorkspaceProjectConfiguration(String projectId, SourceSpecification sourceSpecification);

    default ProjectConfiguration getUserWorkspaceWithConflictResolutionProjectConfiguration(String projectId, String workspaceId)
    {
        return this.getWorkspaceWithConflictResolutionProjectConfiguration(projectId, SourceSpecification.newUserWorkspaceSourceSpecification(workspaceId));
    }

    default ProjectConfiguration getGroupWorkspaceWithConflictResolutionProjectConfiguration(String projectId, String workspaceId)
    {
        return this.getWorkspaceWithConflictResolutionProjectConfiguration(projectId, SourceSpecification.newGroupWorkspaceSourceSpecification(workspaceId));
    }

    ProjectConfiguration getWorkspaceWithConflictResolutionProjectConfiguration(String projectId, SourceSpecification sourceSpecification);

    default ProjectConfiguration getUserWorkspaceRevisionProjectConfiguration(String projectId, String workspaceId, String revisionId) // support revision ID alias
    {
        return this.getWorkspaceRevisionProjectConfiguration(projectId, workspaceId, WorkspaceType.USER, revisionId);
    }

    default ProjectConfiguration getGroupWorkspaceRevisionProjectConfiguration(String projectId, String workspaceId, String revisionId) // support revision ID alias
    {
        return this.getWorkspaceRevisionProjectConfiguration(projectId, workspaceId, WorkspaceType.GROUP, revisionId);
    }

    ProjectConfiguration getWorkspaceRevisionProjectConfiguration(String projectId, SourceSpecification sourceSpecification, String revisionId); // support revision ID alias

    default ProjectConfiguration getWorkspaceRevisionProjectConfiguration(String projectId, String workspaceId, WorkspaceType workspaceType, String revisionId)
    {
        return this.getWorkspaceRevisionProjectConfiguration(projectId, SourceSpecification.newSourceSpecification(workspaceId, workspaceType), revisionId);
    }

    default ProjectConfiguration getBackupUserWorkspaceRevisionProjectConfiguration(String projectId, String workspaceId, String revisionId) // support revision ID alias
    {
        return this.getBackupWorkspaceRevisionProjectConfiguration(projectId, SourceSpecification.newUserWorkspaceSourceSpecification(workspaceId), revisionId);
    }

    default ProjectConfiguration getBackupGroupWorkspaceRevisionProjectConfiguration(String projectId, String workspaceId, String revisionId) // support revision ID alias
    {
        return this.getBackupWorkspaceRevisionProjectConfiguration(projectId, SourceSpecification.newGroupWorkspaceSourceSpecification(workspaceId), revisionId);
    }

    ProjectConfiguration getBackupWorkspaceRevisionProjectConfiguration(String projectId, SourceSpecification sourceSpecification, String revisionId); // support revision ID alias

    default ProjectConfiguration getUserWorkspaceWithConflictResolutionRevisionProjectConfiguration(String projectId, String workspaceId, String revisionId) // support revision ID alias
    {
        return this.getWorkspaceWithConflictResolutionRevisionProjectConfiguration(projectId, SourceSpecification.newUserWorkspaceSourceSpecification(workspaceId), revisionId);
    }

    default ProjectConfiguration getGroupWorkspaceWithConflictResolutionRevisionProjectConfiguration(String projectId, String workspaceId, String revisionId) // support revision ID alias
    {
        return this.getWorkspaceWithConflictResolutionRevisionProjectConfiguration(projectId, SourceSpecification.newGroupWorkspaceSourceSpecification(workspaceId), revisionId);
    }

    ProjectConfiguration getWorkspaceWithConflictResolutionRevisionProjectConfiguration(String projectId, SourceSpecification sourceSpecification, String revisionId); // support revision ID alias

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

    ProjectConfiguration getReviewFromProjectConfiguration(String projectId, VersionId patchReleaseVersionId, String reviewId);

    default ProjectConfiguration getReviewFromProjectConfiguration(String projectId, String reviewId)
    {
        return this.getReviewFromProjectConfiguration(projectId, null, reviewId);
    }

    ProjectConfiguration getReviewToProjectConfiguration(String projectId, VersionId patchReleaseVersionId, String reviewId);

    default ProjectConfiguration getReviewToProjectConfiguration(String projectId, String reviewId)
    {
        return this.getReviewToProjectConfiguration(projectId, null, reviewId);
    }

    Revision updateProjectConfiguration(String projectId, SourceSpecification sourceSpecification, String message, ProjectConfigurationUpdater updater);

    default Revision updateProjectConfiguration(String projectId, String workspaceId, WorkspaceType workspaceType, String message, ProjectConfigurationUpdater updater)
    {
        return this.updateProjectConfiguration(projectId, SourceSpecification.newSourceSpecification(workspaceId, workspaceType), message, updater);
    }

    Revision updateProjectConfigurationForWorkspaceWithConflictResolution(String projectId, String workspaceId, String message, ProjectConfigurationUpdater updater);

    List<ArtifactTypeGenerationConfiguration> getProjectAvailableArtifactGenerations(String projectId, VersionId patchReleaseVersionId);

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

    List<ArtifactTypeGenerationConfiguration> getWorkspaceRevisionAvailableArtifactGenerations(String projectId, SourceSpecification sourceSpecification, String revisionId);

    default List<ArtifactTypeGenerationConfiguration> getWorkspaceRevisionAvailableArtifactGenerations(String projectId, String workspaceId, WorkspaceType workspaceType, String revisionId)
    {
        return this.getWorkspaceRevisionAvailableArtifactGenerations(projectId, SourceSpecification.newSourceSpecification(workspaceId, workspaceType), revisionId);
    }

    default List<ArtifactTypeGenerationConfiguration> getUserWorkspaceAvailableArtifactGenerations(String projectId, String workspaceId)
    {
        return this.getWorkspaceAvailableArtifactGenerations(projectId, SourceSpecification.newUserWorkspaceSourceSpecification(workspaceId));
    }

    default List<ArtifactTypeGenerationConfiguration> getGroupWorkspaceAvailableArtifactGenerations(String projectId, String workspaceId)
    {
        return this.getWorkspaceAvailableArtifactGenerations(projectId, SourceSpecification.newGroupWorkspaceSourceSpecification(workspaceId));
    }

    List<ArtifactTypeGenerationConfiguration> getWorkspaceAvailableArtifactGenerations(String projectId, SourceSpecification sourceSpecification);

    List<ArtifactTypeGenerationConfiguration> getVersionAvailableArtifactGenerations(String projectId, String versionId);

    ProjectStructureVersion getLatestProjectStructureVersion();

    @Deprecated
    default List<ArtifactTypeGenerationConfiguration> getLatestAvailableArtifactGenerations()
    {
        return Collections.emptyList();
    }

    ProjectConfigurationStatusReport getProjectConfigurationStatus(String projectId);
}
