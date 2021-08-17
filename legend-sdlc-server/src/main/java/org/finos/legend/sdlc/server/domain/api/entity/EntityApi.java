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

import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;

import javax.ws.rs.core.Response.Status;

public interface EntityApi
{
    // Entity access

    EntityAccessContext getProjectEntityAccessContext(String projectId);

    EntityAccessContext getProjectRevisionEntityAccessContext(String projectId, String revisionId);

    default EntityAccessContext getUserWorkspaceEntityAccessContext(String projectId, String workspaceId)
    {
        return this.getWorkspaceEntityAccessContext(projectId, workspaceId, WorkspaceType.USER);
    }

    default EntityAccessContext getGroupWorkspaceEntityAccessContext(String projectId, String workspaceId)
    {
        return this.getWorkspaceEntityAccessContext(projectId, workspaceId, WorkspaceType.GROUP);
    }

    // for backward compatibility
    @Deprecated
    default EntityAccessContext getWorkspaceEntityAccessContext(String projectId, String workspaceId)
    {
        return getWorkspaceEntityAccessContext(projectId, workspaceId, WorkspaceType.USER);
    }

    EntityAccessContext getWorkspaceEntityAccessContext(String projectId, String workspaceId, WorkspaceType workspaceType);

    default EntityAccessContext getBackupUserWorkspaceEntityAccessContext(String projectId, String workspaceId)
    {
        return this.getBackupWorkspaceEntityAccessContext(projectId, workspaceId, WorkspaceType.USER);
    }

    default EntityAccessContext getBackupGroupWorkspaceEntityAccessContext(String projectId, String workspaceId)
    {
        return this.getBackupWorkspaceEntityAccessContext(projectId, workspaceId, WorkspaceType.GROUP);
    }

    EntityAccessContext getBackupWorkspaceEntityAccessContext(String projectId, String workspaceId, WorkspaceType workspaceType);

    default EntityAccessContext getUserWorkspaceWithConflictResolutionEntityAccessContext(String projectId, String workspaceId)
    {
        return this.getWorkspaceWithConflictResolutionEntityAccessContext(projectId, workspaceId, WorkspaceType.USER);
    }

    default EntityAccessContext getGroupWorkspaceWithConflictResolutionEntityAccessContext(String projectId, String workspaceId)
    {
        return this.getWorkspaceWithConflictResolutionEntityAccessContext(projectId, workspaceId, WorkspaceType.GROUP);
    }

    EntityAccessContext getWorkspaceWithConflictResolutionEntityAccessContext(String projectId, String workspaceId, WorkspaceType workspaceType);

    default EntityAccessContext getUserWorkspaceRevisionEntityAccessContext(String projectId, String workspaceId, String revisionId)
    {
        return this.getWorkspaceRevisionEntityAccessContext(projectId, workspaceId, WorkspaceType.USER, revisionId);
    }

    default EntityAccessContext getGroupWorkspaceRevisionEntityAccessContext(String projectId, String workspaceId, String revisionId)
    {
        return this.getWorkspaceRevisionEntityAccessContext(projectId, workspaceId, WorkspaceType.GROUP, revisionId);
    }

    // for backward compatibility
    @Deprecated
    default EntityAccessContext getWorkspaceRevisionEntityAccessContext(String projectId, String workspaceId, String revisionId)
    {
        return getWorkspaceRevisionEntityAccessContext(projectId, workspaceId, WorkspaceType.USER, revisionId);
    }

    EntityAccessContext getWorkspaceRevisionEntityAccessContext(String projectId, String workspaceId, WorkspaceType workspaceType, String revisionId);

    default EntityAccessContext getBackupUserWorkspaceRevisionEntityAccessContext(String projectId, String workspaceId, String revisionId)
    {
        return this.getBackupWorkspaceRevisionEntityAccessContext(projectId, workspaceId, WorkspaceType.USER, revisionId);
    }

    default EntityAccessContext getBackupGroupWorkspaceRevisionEntityAccessContext(String projectId, String workspaceId, String revisionId)
    {
        return this.getBackupWorkspaceRevisionEntityAccessContext(projectId, workspaceId, WorkspaceType.GROUP, revisionId);
    }

    EntityAccessContext getBackupWorkspaceRevisionEntityAccessContext(String projectId, String workspaceId, WorkspaceType workspaceType, String revisionId);

    default EntityAccessContext getUserWorkspaceWithConflictResolutionRevisionEntityAccessContext(String projectId, String workspaceId, String revisionId)
    {
        return this.getWorkspaceWithConflictResolutionRevisionEntityAccessContext(projectId, workspaceId, WorkspaceType.USER, revisionId);
    }

    default EntityAccessContext getGroupWorkspaceWithConflictResolutionRevisionEntityAccessContext(String projectId, String workspaceId, String revisionId)
    {
        return this.getWorkspaceWithConflictResolutionRevisionEntityAccessContext(projectId, workspaceId, WorkspaceType.GROUP, revisionId);
    }

    EntityAccessContext getWorkspaceWithConflictResolutionRevisionEntityAccessContext(String projectId, String workspaceId, WorkspaceType workspaceType, String revisionId);

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

    EntityAccessContext getReviewFromEntityAccessContext(String projectId, String reviewId);

    EntityAccessContext getReviewToEntityAccessContext(String projectId, String reviewId);

    EntityAccessContext getVersionEntityAccessContext(String projectId, VersionId versionId);

    // Entity modification

    default EntityModificationContext getUserWorkspaceEntityModificationContext(String projectId, String workspaceId)
    {
        return this.getWorkspaceEntityModificationContext(projectId, workspaceId, WorkspaceType.USER);
    }

    default EntityModificationContext getGroupWorkspaceEntityModificationContext(String projectId, String workspaceId)
    {
        return this.getWorkspaceEntityModificationContext(projectId, workspaceId, WorkspaceType.GROUP);
    }

    EntityModificationContext getWorkspaceEntityModificationContext(String projectId, String workspaceId, WorkspaceType workspaceType);

    EntityModificationContext getWorkspaceWithConflictResolutionEntityModificationContext(String projectId, String workspaceId);
}
