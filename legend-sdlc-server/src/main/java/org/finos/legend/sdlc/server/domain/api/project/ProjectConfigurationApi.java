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
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification;
import org.finos.legend.sdlc.server.domain.api.project.source.WorkspaceSourceSpecification;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSpecification;
import org.finos.legend.sdlc.server.project.ProjectConfigurationStatusReport;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider.WorkspaceAccessType;

import java.util.Collections;
import java.util.List;

public interface ProjectConfigurationApi
{
    ProjectConfiguration getProjectConfiguration(String projectId, SourceSpecification sourceSpecification, String revisionId);

    default ProjectConfiguration getProjectConfiguration(String projectId, SourceSpecification sourceSpecification)
    {
        return getProjectConfiguration(projectId, sourceSpecification, null);
    }

    ProjectConfiguration getReviewFromProjectConfiguration(String projectId, String reviewId);

    ProjectConfiguration getReviewToProjectConfiguration(String projectId, String reviewId);

    Revision updateProjectConfiguration(String projectId, WorkspaceSourceSpecification sourceSpecification, String message, ProjectConfigurationUpdater updater);

    List<ArtifactTypeGenerationConfiguration> getAvailableArtifactGenerations(String projectId, SourceSpecification sourceSpecification, String revisionId);

    default List<ArtifactTypeGenerationConfiguration> getAvailableArtifactGenerations(String projectId, SourceSpecification sourceSpecification)
    {
        return getAvailableArtifactGenerations(projectId, sourceSpecification, null);
    }

    ProjectConfigurationStatusReport getProjectConfigurationStatus(String projectId);

    ProjectStructureVersion getLatestProjectStructureVersion();

    // Deprecated APIs

    @Deprecated
    default ProjectConfiguration getProjectProjectConfiguration(String projectId, VersionId patchReleaseVersionId)
    {
        return getProjectConfiguration(projectId, SourceSpecification.newSourceSpecification(patchReleaseVersionId));
    }

    @Deprecated
    default ProjectConfiguration getProjectProjectConfiguration(String projectId)
    {
        return getProjectConfiguration(projectId, SourceSpecification.projectSourceSpecification());
    }

    @Deprecated
    default ProjectConfiguration getProjectRevisionProjectConfiguration(String projectId, String revisionId)
    {
        return getProjectConfiguration(projectId, SourceSpecification.projectSourceSpecification(), revisionId);
    }

    @Deprecated
    default ProjectConfiguration getProjectRevisionProjectConfiguration(String projectId, VersionId patchReleaseVersionId, String revisionId)
    {
        return getProjectConfiguration(projectId, SourceSpecification.newSourceSpecification(patchReleaseVersionId), revisionId);
    }

    @Deprecated
    default ProjectConfiguration getUserWorkspaceProjectConfiguration(String projectId, String workspaceId)
    {
        return getUserWorkspaceRevisionProjectConfiguration(projectId, workspaceId, null);
    }

    @Deprecated
    default ProjectConfiguration getGroupWorkspaceProjectConfiguration(String projectId, String workspaceId)
    {
        return getGroupWorkspaceRevisionProjectConfiguration(projectId, workspaceId, null);
    }

    @Deprecated
    default ProjectConfiguration getWorkspaceProjectConfiguration(String projectId, SourceSpecification sourceSpecification)
    {
        return getWorkspaceRevisionProjectConfiguration(projectId, sourceSpecification, null);
    }

    @Deprecated
    default ProjectConfiguration getBackupUserWorkspaceProjectConfiguration(String projectId, String workspaceId)
    {
        return getBackupUserWorkspaceRevisionProjectConfiguration(projectId, workspaceId, null);
    }

    @Deprecated
    default ProjectConfiguration getBackupGroupWorkspaceProjectConfiguration(String projectId, String workspaceId)
    {
        return getBackupGroupWorkspaceRevisionProjectConfiguration(projectId, workspaceId, null);
    }

    @Deprecated
    default ProjectConfiguration getBackupWorkspaceProjectConfiguration(String projectId, SourceSpecification sourceSpecification)
    {
        return getBackupWorkspaceRevisionProjectConfiguration(projectId, sourceSpecification, null);
    }

    @Deprecated
    default ProjectConfiguration getUserWorkspaceWithConflictResolutionProjectConfiguration(String projectId, String workspaceId)
    {
        return getUserWorkspaceWithConflictResolutionRevisionProjectConfiguration(projectId, workspaceId, null);
    }

    @Deprecated
    default ProjectConfiguration getGroupWorkspaceWithConflictResolutionProjectConfiguration(String projectId, String workspaceId)
    {
        return getGroupWorkspaceWithConflictResolutionRevisionProjectConfiguration(projectId, workspaceId, null);
    }

    @Deprecated
    default ProjectConfiguration getWorkspaceWithConflictResolutionProjectConfiguration(String projectId, SourceSpecification sourceSpecification)
    {
        return getWorkspaceWithConflictResolutionRevisionProjectConfiguration(projectId, sourceSpecification, null);
    }

    @Deprecated
    default ProjectConfiguration getUserWorkspaceRevisionProjectConfiguration(String projectId, String workspaceId, String revisionId)
    {
        return getProjectConfiguration(projectId, SourceSpecification.workspaceSourceSpecification(WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.USER, WorkspaceAccessType.WORKSPACE)), revisionId);
    }

    @Deprecated
    default ProjectConfiguration getGroupWorkspaceRevisionProjectConfiguration(String projectId, String workspaceId, String revisionId)
    {
        return getProjectConfiguration(projectId, SourceSpecification.workspaceSourceSpecification(WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.GROUP, WorkspaceAccessType.WORKSPACE)), revisionId);
    }

    @Deprecated
    default ProjectConfiguration getWorkspaceRevisionProjectConfiguration(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
        if (!(sourceSpecification instanceof WorkspaceSourceSpecification))
        {
            throw new IllegalArgumentException("Not a workspace source specification: " + sourceSpecification);
        }
        return getProjectConfiguration(projectId, sourceSpecification, revisionId);
    }

    @Deprecated
    default ProjectConfiguration getWorkspaceRevisionProjectConfiguration(String projectId, String workspaceId, WorkspaceType workspaceType, String revisionId)
    {
        return getProjectConfiguration(projectId, SourceSpecification.workspaceSourceSpecification(WorkspaceSpecification.newWorkspaceSpecification(workspaceId, workspaceType, WorkspaceAccessType.WORKSPACE)), revisionId);
    }

    @Deprecated
    default ProjectConfiguration getBackupUserWorkspaceRevisionProjectConfiguration(String projectId, String workspaceId, String revisionId)
    {
        return getProjectConfiguration(projectId, SourceSpecification.workspaceSourceSpecification(WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.USER, WorkspaceAccessType.BACKUP)), revisionId);
    }

    @Deprecated
    default ProjectConfiguration getBackupGroupWorkspaceRevisionProjectConfiguration(String projectId, String workspaceId, String revisionId)
    {
        return getProjectConfiguration(projectId, SourceSpecification.workspaceSourceSpecification(WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.GROUP, WorkspaceAccessType.BACKUP)), revisionId);
    }

    @Deprecated
    default ProjectConfiguration getBackupWorkspaceRevisionProjectConfiguration(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
        if (!(sourceSpecification instanceof WorkspaceSourceSpecification) || (((WorkspaceSourceSpecification) sourceSpecification).getWorkspaceSpecification().getAccessType() != WorkspaceAccessType.BACKUP))
        {
            throw new IllegalArgumentException("Not a backup workspace source specification: " + sourceSpecification);
        }
        return getProjectConfiguration(projectId, sourceSpecification, revisionId);
    }

    @Deprecated
    default ProjectConfiguration getUserWorkspaceWithConflictResolutionRevisionProjectConfiguration(String projectId, String workspaceId, String revisionId)
    {
        return getProjectConfiguration(projectId, SourceSpecification.workspaceSourceSpecification(WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.USER, WorkspaceAccessType.CONFLICT_RESOLUTION)), revisionId);
    }

    @Deprecated
    default ProjectConfiguration getGroupWorkspaceWithConflictResolutionRevisionProjectConfiguration(String projectId, String workspaceId, String revisionId)
    {
        return getProjectConfiguration(projectId, SourceSpecification.workspaceSourceSpecification(WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.GROUP, WorkspaceAccessType.CONFLICT_RESOLUTION)), revisionId);
    }

    @Deprecated
    default ProjectConfiguration getWorkspaceWithConflictResolutionRevisionProjectConfiguration(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
        if (!(sourceSpecification instanceof WorkspaceSourceSpecification) || (((WorkspaceSourceSpecification) sourceSpecification).getWorkspaceSpecification().getAccessType() != WorkspaceAccessType.CONFLICT_RESOLUTION))
        {
            throw new IllegalArgumentException("Not a conflict resolution workspace source specification: " + sourceSpecification);
        }
        return getProjectConfiguration(projectId, sourceSpecification, revisionId);
    }

    @Deprecated
    default ProjectConfiguration getVersionProjectConfiguration(String projectId, String versionId)
    {
        return getProjectConfiguration(projectId, SourceSpecification.versionSourceSpecification(versionId));
    }

    @Deprecated
    default ProjectConfiguration getVersionProjectConfiguration(String projectId, VersionId versionId)
    {
        return getProjectConfiguration(projectId, SourceSpecification.versionSourceSpecification(versionId));
    }

    @Deprecated
    default ProjectConfiguration getReviewFromProjectConfiguration(String projectId, VersionId patchReleaseVersionId, String reviewId)
    {
        return getReviewFromProjectConfiguration(projectId, reviewId);
    }

    @Deprecated
    default ProjectConfiguration getReviewToProjectConfiguration(String projectId, VersionId patchReleaseVersionId, String reviewId)
    {
        return getReviewToProjectConfiguration(projectId, reviewId);
    }

    @Deprecated
    default Revision updateProjectConfiguration(String projectId, SourceSpecification sourceSpecification, String message, ProjectConfigurationUpdater updater)
    {
        return updateProjectConfiguration(projectId, (WorkspaceSourceSpecification) sourceSpecification, message, updater);
    }

    @Deprecated
    default Revision updateProjectConfiguration(String projectId, String workspaceId, WorkspaceType workspaceType, String message, ProjectConfigurationUpdater updater)
    {
        return updateProjectConfiguration(projectId, SourceSpecification.workspaceSourceSpecification(WorkspaceSpecification.newWorkspaceSpecification(workspaceId, workspaceType)), message, updater);
    }

    @Deprecated
    default Revision updateProjectConfigurationForWorkspaceWithConflictResolution(String projectId, String workspaceId, String message, ProjectConfigurationUpdater updater)
    {
        return updateProjectConfiguration(projectId, SourceSpecification.workspaceSourceSpecification(WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.USER, WorkspaceAccessType.CONFLICT_RESOLUTION)), message, updater);
    }

    @Deprecated
    default List<ArtifactTypeGenerationConfiguration> getProjectAvailableArtifactGenerations(String projectId, VersionId patchReleaseVersionId)
    {
        return getAvailableArtifactGenerations(projectId, SourceSpecification.newSourceSpecification(patchReleaseVersionId));
    }

    @Deprecated
    default List<ArtifactTypeGenerationConfiguration> getProjectAvailableArtifactGenerations(String projectId)
    {
        return getAvailableArtifactGenerations(projectId, SourceSpecification.projectSourceSpecification());
    }

    @Deprecated
    default List<ArtifactTypeGenerationConfiguration> getRevisionAvailableArtifactGenerations(String projectId, String revisionId)
    {
        return getAvailableArtifactGenerations(projectId, SourceSpecification.projectSourceSpecification(), revisionId);
    }

    @Deprecated
    default List<ArtifactTypeGenerationConfiguration> getWorkspaceRevisionAvailableArtifactGenerations(String projectId, String workspaceId, WorkspaceType workspaceType, String revisionId)
    {
        return getAvailableArtifactGenerations(projectId, SourceSpecification.workspaceSourceSpecification(WorkspaceSpecification.newWorkspaceSpecification(workspaceId, workspaceType, WorkspaceAccessType.WORKSPACE)), revisionId);
    }

    @Deprecated
    default List<ArtifactTypeGenerationConfiguration> getUserWorkspaceRevisionAvailableArtifactGenerations(String projectId, String workspaceId, String revisionId)
    {
        return getWorkspaceRevisionAvailableArtifactGenerations(projectId, workspaceId, WorkspaceType.USER, revisionId);
    }

    @Deprecated
    default List<ArtifactTypeGenerationConfiguration> getGroupWorkspaceRevisionAvailableArtifactGenerations(String projectId, String workspaceId, String revisionId)
    {
        return getWorkspaceRevisionAvailableArtifactGenerations(projectId, workspaceId, WorkspaceType.GROUP, revisionId);
    }

    @Deprecated
    default List<ArtifactTypeGenerationConfiguration> getWorkspaceRevisionAvailableArtifactGenerations(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
        if (!(sourceSpecification instanceof WorkspaceSourceSpecification))
        {
            throw new IllegalArgumentException("Not a workspace source specification: " + sourceSpecification);
        }
        return getAvailableArtifactGenerations(projectId, sourceSpecification, revisionId);
    }

    @Deprecated
    default List<ArtifactTypeGenerationConfiguration> getUserWorkspaceAvailableArtifactGenerations(String projectId, String workspaceId)
    {
        return getUserWorkspaceRevisionAvailableArtifactGenerations(projectId, workspaceId, null);
    }

    @Deprecated
    default List<ArtifactTypeGenerationConfiguration> getGroupWorkspaceAvailableArtifactGenerations(String projectId, String workspaceId)
    {
        return getGroupWorkspaceRevisionAvailableArtifactGenerations(projectId, workspaceId, null);
    }

    @Deprecated
    default List<ArtifactTypeGenerationConfiguration> getWorkspaceAvailableArtifactGenerations(String projectId, SourceSpecification sourceSpecification)
    {
        return getWorkspaceRevisionAvailableArtifactGenerations(projectId, sourceSpecification, null);
    }

    @Deprecated
    default List<ArtifactTypeGenerationConfiguration> getVersionAvailableArtifactGenerations(String projectId, String versionId)
    {
        return getAvailableArtifactGenerations(projectId, SourceSpecification.versionSourceSpecification(versionId));
    }

    @Deprecated
    default List<ArtifactTypeGenerationConfiguration> getLatestAvailableArtifactGenerations()
    {
        return Collections.emptyList();
    }
}
