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
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSpecification;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;

import javax.ws.rs.core.Response.Status;

public interface EntityApi
{
    // Entity access

    EntityAccessContext getProjectEntityAccessContext(String projectId, VersionId patchReleaseVersionId);

    default EntityAccessContext getProjectEntityAccessContext(String projectId)
    {
        return this.getProjectEntityAccessContext(projectId, null);
    }

    EntityAccessContext getProjectRevisionEntityAccessContext(String projectId, VersionId patchReleaseVersionId, String revisionId);

    default EntityAccessContext getProjectRevisionEntityAccessContext(String projectId, String revisionId)
    {
        return this.getProjectRevisionEntityAccessContext(projectId, null, revisionId);
    }

    default EntityAccessContext getUserWorkspaceEntityAccessContext(String projectId, String workspaceId)
    {
        return this.getWorkspaceEntityAccessContext(projectId, WorkspaceSpecification.newUserWorkspaceSpecification(workspaceId));
    }

    default EntityAccessContext getGroupWorkspaceEntityAccessContext(String projectId, String workspaceId)
    {
        return this.getWorkspaceEntityAccessContext(projectId, WorkspaceSpecification.newGroupWorkspaceSpecification(workspaceId));
    }

    // for backward compatibility
    @Deprecated
    default EntityAccessContext getWorkspaceEntityAccessContext(String projectId, String workspaceId)
    {
        return getWorkspaceEntityAccessContext(projectId, WorkspaceSpecification.newUserWorkspaceSpecification(workspaceId));
    }

    EntityAccessContext getWorkspaceEntityAccessContext(String projectId, WorkspaceSpecification workspaceSpecification);

    default EntityAccessContext getBackupUserWorkspaceEntityAccessContext(String projectId, String workspaceId)
    {
        return this.getBackupWorkspaceEntityAccessContext(projectId, WorkspaceSpecification.newUserWorkspaceSpecification(workspaceId));
    }

    default EntityAccessContext getBackupGroupWorkspaceEntityAccessContext(String projectId, String workspaceId)
    {
        return this.getBackupWorkspaceEntityAccessContext(projectId, WorkspaceSpecification.newGroupWorkspaceSpecification(workspaceId));
    }

    EntityAccessContext getBackupWorkspaceEntityAccessContext(String projectId, WorkspaceSpecification workspaceSpecification);

    default EntityAccessContext getUserWorkspaceWithConflictResolutionEntityAccessContext(String projectId, String workspaceId)
    {
        return this.getWorkspaceWithConflictResolutionEntityAccessContext(projectId, WorkspaceSpecification.newUserWorkspaceSpecification(workspaceId));
    }

    default EntityAccessContext getGroupWorkspaceWithConflictResolutionEntityAccessContext(String projectId, String workspaceId)
    {
        return this.getWorkspaceWithConflictResolutionEntityAccessContext(projectId, WorkspaceSpecification.newGroupWorkspaceSpecification(workspaceId));
    }

    EntityAccessContext getWorkspaceWithConflictResolutionEntityAccessContext(String projectId, WorkspaceSpecification workspaceSpecification);

    default EntityAccessContext getUserWorkspaceRevisionEntityAccessContext(String projectId, String workspaceId, String revisionId)
    {
        return this.getWorkspaceRevisionEntityAccessContext(projectId, WorkspaceSpecification.newUserWorkspaceSpecification(workspaceId), revisionId);
    }

    default EntityAccessContext getGroupWorkspaceRevisionEntityAccessContext(String projectId, String workspaceId, String revisionId)
    {
        return this.getWorkspaceRevisionEntityAccessContext(projectId, WorkspaceSpecification.newGroupWorkspaceSpecification(workspaceId), revisionId);
    }

    // for backward compatibility
    @Deprecated
    default EntityAccessContext getWorkspaceRevisionEntityAccessContext(String projectId, String workspaceId, String revisionId)
    {
        return getWorkspaceRevisionEntityAccessContext(projectId, WorkspaceSpecification.newUserWorkspaceSpecification(workspaceId), revisionId);
    }

    default EntityAccessContext getWorkspaceRevisionEntityAccessContext(String projectId, String workspaceId, WorkspaceType workspaceType, String revisionId)
    {
        return getWorkspaceRevisionEntityAccessContext(projectId, WorkspaceSpecification.newWorkspaceSpecification(workspaceId, workspaceType), revisionId);
    }

    EntityAccessContext getWorkspaceRevisionEntityAccessContext(String projectId, WorkspaceSpecification workspaceSpecificationo, String revisionId);

    default EntityAccessContext getBackupUserWorkspaceRevisionEntityAccessContext(String projectId, String workspaceId, String revisionId)
    {
        return this.getBackupWorkspaceRevisionEntityAccessContext(projectId, WorkspaceSpecification.newUserWorkspaceSpecification(workspaceId), revisionId);
    }

    default EntityAccessContext getBackupGroupWorkspaceRevisionEntityAccessContext(String projectId, String workspaceId, String revisionId)
    {
        return this.getBackupWorkspaceRevisionEntityAccessContext(projectId, WorkspaceSpecification.newGroupWorkspaceSpecification(workspaceId), revisionId);
    }

    EntityAccessContext getBackupWorkspaceRevisionEntityAccessContext(String projectId, WorkspaceSpecification workspaceSpecification, String revisionId);

    default EntityAccessContext getUserWorkspaceWithConflictResolutionRevisionEntityAccessContext(String projectId, String workspaceId, String revisionId)
    {
        return this.getWorkspaceWithConflictResolutionRevisionEntityAccessContext(projectId, WorkspaceSpecification.newUserWorkspaceSpecification(workspaceId), revisionId);
    }

    default EntityAccessContext getGroupWorkspaceWithConflictResolutionRevisionEntityAccessContext(String projectId, String workspaceId, String revisionId)
    {
        return this.getWorkspaceWithConflictResolutionRevisionEntityAccessContext(projectId, WorkspaceSpecification.newGroupWorkspaceSpecification(workspaceId), revisionId);
    }

    EntityAccessContext getWorkspaceWithConflictResolutionRevisionEntityAccessContext(String projectId, WorkspaceSpecification workspaceSpecification, String revisionId);

    default EntityAccessContext getVersionEntityAccessContext(String projectId, String versionIdString)
    {
        VersionId versionId;
        try
        {
            versionId = VersionId.parseVersionId(versionIdString);
        }
        catch (IllegalArgumentException e)
        {
            throw new LegendSDLCServerException(e.getMessage(), Status.BAD_REQUEST, e);
        }
        return getVersionEntityAccessContext(projectId, versionId);
    }

    EntityAccessContext getReviewFromEntityAccessContext(String projectId, VersionId patchReleaseVersionId, String reviewId);

    default EntityAccessContext getReviewFromEntityAccessContext(String projectId, String reviewId)
    {
        return this.getReviewFromEntityAccessContext(projectId, null, reviewId);
    }

    EntityAccessContext getReviewToEntityAccessContext(String projectId, VersionId patchReleaseVersionId, String reviewId);

    default EntityAccessContext getReviewToEntityAccessContext(String projectId, String reviewId)
    {
        return this.getReviewToEntityAccessContext(projectId, null, reviewId);
    }

    EntityAccessContext getVersionEntityAccessContext(String projectId, VersionId versionId);

    // Entity modification

    default EntityModificationContext getUserWorkspaceEntityModificationContext(String projectId, String workspaceId)
    {
        return this.getWorkspaceEntityModificationContext(projectId, WorkspaceSpecification.newUserWorkspaceSpecification(workspaceId));
    }

    default EntityModificationContext getGroupWorkspaceEntityModificationContext(String projectId, String workspaceId)
    {
        return this.getWorkspaceEntityModificationContext(projectId, WorkspaceSpecification.newGroupWorkspaceSpecification(workspaceId));
    }

    EntityModificationContext getWorkspaceEntityModificationContext(String projectId, WorkspaceSpecification workspaceSpecification);

    EntityModificationContext getWorkspaceWithConflictResolutionEntityModificationContext(String projectId, WorkspaceSpecification workspaceSpecification);
}
