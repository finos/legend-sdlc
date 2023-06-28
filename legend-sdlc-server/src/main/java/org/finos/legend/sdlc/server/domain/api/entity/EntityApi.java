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

package org.finos.legend.sdlc.server.domain.api.entity;

import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification;
import org.finos.legend.sdlc.server.domain.api.project.source.WorkspaceSourceSpecification;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSpecification;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider.WorkspaceAccessType;

public interface EntityApi
{
    // Entity access

    EntityAccessContext getEntityAccessContext(String projectId, SourceSpecification sourceSpecification, String revisionId);

    default EntityAccessContext getEntityAccessContext(String projectId, SourceSpecification sourceSpecification)
    {
        return getEntityAccessContext(projectId, sourceSpecification, null);
    }

    EntityAccessContext getReviewFromEntityAccessContext(String projectId, String reviewId);

    EntityAccessContext getReviewToEntityAccessContext(String projectId, String reviewId);

    // Entity modification

    EntityModificationContext getEntityModificationContext(String projectId, WorkspaceSourceSpecification sourceSpecification);

    // Deprecated APIs

    @Deprecated
    default EntityAccessContext getProjectEntityAccessContext(String projectId, VersionId patchReleaseVersionId)
    {
        return getEntityAccessContext(projectId, SourceSpecification.newSourceSpecification(patchReleaseVersionId));
    }

    @Deprecated
    default EntityAccessContext getProjectEntityAccessContext(String projectId)
    {
        return getEntityAccessContext(projectId, SourceSpecification.projectSourceSpecification());
    }

    @Deprecated
    default EntityAccessContext getProjectRevisionEntityAccessContext(String projectId, VersionId patchReleaseVersionId, String revisionId)
    {
        return getEntityAccessContext(projectId, SourceSpecification.newSourceSpecification(patchReleaseVersionId), revisionId);
    }

    @Deprecated
    default EntityAccessContext getProjectRevisionEntityAccessContext(String projectId, String revisionId)
    {
        return getEntityAccessContext(projectId, SourceSpecification.projectSourceSpecification(), revisionId);
    }

    @Deprecated
    default EntityAccessContext getUserWorkspaceEntityAccessContext(String projectId, String workspaceId)
    {
        return getUserWorkspaceRevisionEntityAccessContext(projectId, workspaceId, null);
    }

    @Deprecated
    default EntityAccessContext getGroupWorkspaceEntityAccessContext(String projectId, String workspaceId)
    {
        return getGroupWorkspaceRevisionEntityAccessContext(projectId, workspaceId, null);
    }

    @Deprecated
    default EntityAccessContext getWorkspaceEntityAccessContext(String projectId, String workspaceId)
    {
        return getWorkspaceRevisionEntityAccessContext(projectId, workspaceId, null);
    }

    @Deprecated
    default EntityAccessContext getWorkspaceEntityAccessContext(String projectId, SourceSpecification sourceSpecification)
    {
        return getWorkspaceRevisionEntityAccessContext(projectId, sourceSpecification, null);
    }

    @Deprecated
    default EntityAccessContext getBackupUserWorkspaceEntityAccessContext(String projectId, String workspaceId)
    {
        return getBackupUserWorkspaceRevisionEntityAccessContext(projectId, workspaceId, null);
    }

    @Deprecated
    default EntityAccessContext getBackupGroupWorkspaceEntityAccessContext(String projectId, String workspaceId)
    {
        return getBackupGroupWorkspaceRevisionEntityAccessContext(projectId, workspaceId, null);
    }

    @Deprecated
    default EntityAccessContext getBackupWorkspaceEntityAccessContext(String projectId, SourceSpecification sourceSpecification)
    {
        return getBackupWorkspaceRevisionEntityAccessContext(projectId, sourceSpecification, null);
    }

    @Deprecated
    default EntityAccessContext getUserWorkspaceWithConflictResolutionEntityAccessContext(String projectId, String workspaceId)
    {
        return getUserWorkspaceWithConflictResolutionRevisionEntityAccessContext(projectId, workspaceId, null);
    }

    @Deprecated
    default EntityAccessContext getGroupWorkspaceWithConflictResolutionEntityAccessContext(String projectId, String workspaceId)
    {
        return getGroupWorkspaceWithConflictResolutionRevisionEntityAccessContext(projectId, workspaceId, null);
    }

    @Deprecated
    default EntityAccessContext getWorkspaceWithConflictResolutionEntityAccessContext(String projectId, SourceSpecification sourceSpecification)
    {
        return getWorkspaceWithConflictResolutionRevisionEntityAccessContext(projectId, sourceSpecification, null);
    }

    @Deprecated
    default EntityAccessContext getUserWorkspaceRevisionEntityAccessContext(String projectId, String workspaceId, String revisionId)
    {
        return getWorkspaceRevisionEntityAccessContext(projectId, workspaceId, WorkspaceType.USER, revisionId);
    }

    @Deprecated
    default EntityAccessContext getGroupWorkspaceRevisionEntityAccessContext(String projectId, String workspaceId, String revisionId)
    {
        return getWorkspaceRevisionEntityAccessContext(projectId, workspaceId, WorkspaceType.GROUP, revisionId);
    }

    @Deprecated
    default EntityAccessContext getWorkspaceRevisionEntityAccessContext(String projectId, String workspaceId, String revisionId)
    {
        return getWorkspaceRevisionEntityAccessContext(projectId, workspaceId, WorkspaceType.USER, revisionId);
    }

    @Deprecated
    default EntityAccessContext getWorkspaceRevisionEntityAccessContext(String projectId, String workspaceId, WorkspaceType workspaceType, String revisionId)
    {
        return getEntityAccessContext(projectId, SourceSpecification.workspaceSourceSpecification(WorkspaceSpecification.newWorkspaceSpecification(workspaceId, workspaceType)), revisionId);
    }

    @Deprecated
    default EntityAccessContext getWorkspaceRevisionEntityAccessContext(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
        if (!(sourceSpecification instanceof WorkspaceSourceSpecification))
        {
            throw new IllegalArgumentException("Not a workspace source specification: " + sourceSpecification);
        }
        return getEntityAccessContext(projectId, sourceSpecification, revisionId);
    }

    @Deprecated
    default EntityAccessContext getBackupUserWorkspaceRevisionEntityAccessContext(String projectId, String workspaceId, String revisionId)
    {
        return getEntityAccessContext(projectId, SourceSpecification.workspaceSourceSpecification(WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.USER, WorkspaceAccessType.BACKUP)), revisionId);
    }

    @Deprecated
    default EntityAccessContext getBackupGroupWorkspaceRevisionEntityAccessContext(String projectId, String workspaceId, String revisionId)
    {
        return getEntityAccessContext(projectId, SourceSpecification.workspaceSourceSpecification(WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.GROUP, WorkspaceAccessType.BACKUP)), revisionId);
    }

    @Deprecated
    default EntityAccessContext getBackupWorkspaceRevisionEntityAccessContext(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
        if (!(sourceSpecification instanceof WorkspaceSourceSpecification) || (((WorkspaceSourceSpecification) sourceSpecification).getWorkspaceSpecification().getAccessType() != WorkspaceAccessType.BACKUP))
        {
            throw new IllegalArgumentException("Not a backup workspace source specification: " + sourceSpecification);
        }
        return getEntityAccessContext(projectId, sourceSpecification, revisionId);
    }

    @Deprecated
    default EntityAccessContext getUserWorkspaceWithConflictResolutionRevisionEntityAccessContext(String projectId, String workspaceId, String revisionId)
    {
        return getEntityAccessContext(projectId, SourceSpecification.workspaceSourceSpecification(WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.USER, WorkspaceAccessType.CONFLICT_RESOLUTION)), revisionId);
    }

    @Deprecated
    default EntityAccessContext getGroupWorkspaceWithConflictResolutionRevisionEntityAccessContext(String projectId, String workspaceId, String revisionId)
    {
        return getEntityAccessContext(projectId, SourceSpecification.workspaceSourceSpecification(WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.GROUP, WorkspaceAccessType.CONFLICT_RESOLUTION)), revisionId);
    }

    @Deprecated
    default EntityAccessContext getWorkspaceWithConflictResolutionRevisionEntityAccessContext(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
        if (!(sourceSpecification instanceof WorkspaceSourceSpecification) || (((WorkspaceSourceSpecification) sourceSpecification).getWorkspaceSpecification().getAccessType() != WorkspaceAccessType.CONFLICT_RESOLUTION))
        {
            throw new IllegalArgumentException("Not a conflict resolution workspace source specification: " + sourceSpecification);
        }
        return getEntityAccessContext(projectId, sourceSpecification, revisionId);
    }

    @Deprecated
    default EntityAccessContext getReviewFromEntityAccessContext(String projectId, VersionId patchReleaseVersionId, String reviewId)
    {
        return getReviewFromEntityAccessContext(projectId, reviewId);
    }

    @Deprecated
    default EntityAccessContext getReviewToEntityAccessContext(String projectId, VersionId patchReleaseVersionId, String reviewId)
    {
        return getReviewToEntityAccessContext(projectId, reviewId);
    }

    @Deprecated
    default EntityAccessContext getVersionEntityAccessContext(String projectId, String versionIdString)
    {
        return getEntityAccessContext(projectId, SourceSpecification.versionSourceSpecification(versionIdString));
    }

    @Deprecated
    default EntityAccessContext getVersionEntityAccessContext(String projectId, VersionId versionId)
    {
        return getEntityAccessContext(projectId, SourceSpecification.versionSourceSpecification(versionId));
    }

    @Deprecated
    default EntityModificationContext getUserWorkspaceEntityModificationContext(String projectId, String workspaceId)
    {
        return getEntityModificationContext(projectId, SourceSpecification.workspaceSourceSpecification(WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.USER, WorkspaceAccessType.WORKSPACE)));
    }

    @Deprecated
    default EntityModificationContext getGroupWorkspaceEntityModificationContext(String projectId, String workspaceId)
    {
        return getEntityModificationContext(projectId, SourceSpecification.workspaceSourceSpecification(WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.GROUP, WorkspaceAccessType.WORKSPACE)));
    }

    @Deprecated
    default EntityModificationContext getWorkspaceEntityModificationContext(String projectId, SourceSpecification sourceSpecification)
    {
        if (!(sourceSpecification instanceof WorkspaceSourceSpecification))
        {
            throw new IllegalArgumentException("Not a workspace source specification: " + sourceSpecification);
        }
        return getEntityModificationContext(projectId, (WorkspaceSourceSpecification) sourceSpecification);
    }

    @Deprecated
    default EntityModificationContext getWorkspaceWithConflictResolutionEntityModificationContext(String projectId, SourceSpecification sourceSpecification)
    {
        if (!(sourceSpecification instanceof WorkspaceSourceSpecification) || (((WorkspaceSourceSpecification) sourceSpecification).getWorkspaceSpecification().getAccessType() != WorkspaceAccessType.CONFLICT_RESOLUTION))
        {
            throw new IllegalArgumentException("Not a conflict resolution workspace source specification: " + sourceSpecification);
        }
        return getEntityModificationContext(projectId, (WorkspaceSourceSpecification) sourceSpecification);
    }
}
