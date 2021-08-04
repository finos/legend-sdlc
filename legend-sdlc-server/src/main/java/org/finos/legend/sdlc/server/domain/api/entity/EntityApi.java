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
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;

import javax.ws.rs.core.Response.Status;

public interface EntityApi
{
    // Entity access

    EntityAccessContext getProjectEntityAccessContext(String projectId);

    EntityAccessContext getProjectRevisionEntityAccessContext(String projectId, String revisionId);

    EntityAccessContext getUserWorkspaceEntityAccessContext(String projectId, String workspaceId);

    EntityAccessContext getGroupWorkspaceEntityAccessContext(String projectId, String workspaceId);

    EntityAccessContext getWorkspaceEntityAccessContext(String projectId, String workspaceId, boolean isGroupWorkspace);

    EntityAccessContext getBackupWorkspaceEntityAccessContext(String projectId, String workspaceId);

    EntityAccessContext getWorkspaceWithConflictResolutionEntityAccessContext(String projectId, String workspaceId);

    EntityAccessContext getUserWorkspaceRevisionEntityAccessContext(String projectId, String workspaceId, String revisionId);

    EntityAccessContext getGroupWorkspaceRevisionEntityAccessContext(String projectId, String workspaceId, String revisionId);

    EntityAccessContext getWorkspaceRevisionEntityAccessContext(String projectId, String workspaceId, boolean isGroupWorkspace, String revisionId);

    EntityAccessContext getBackupWorkspaceRevisionEntityAccessContext(String projectId, String workspaceId, String revisionId);

    EntityAccessContext getWorkspaceWithConflictResolutionRevisionEntityAccessContext(String projectId, String workspaceId, String revisionId);

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

    EntityModificationContext getUserWorkspaceEntityModificationContext(String projectId, String workspaceId);

    EntityModificationContext getGroupWorkspaceEntityModificationContext(String projectId, String workspaceId);

    EntityModificationContext getWorkspaceEntityModificationContext(String projectId, String workspaceId, boolean isGroupWorkspace);

    EntityModificationContext getWorkspaceWithConflictResolutionEntityModificationContext(String projectId, String workspaceId);
}
