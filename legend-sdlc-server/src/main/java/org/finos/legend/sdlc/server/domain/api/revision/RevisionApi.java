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

package org.finos.legend.sdlc.server.domain.api.revision;

import org.finos.legend.sdlc.domain.model.revision.RevisionStatus;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;

/**
 * Note that all of these APIs support revision ID alias as they all essentially calls getRevision() from RevisionAccessContext
 * which takes into account revision ID alias
 */
public interface RevisionApi
{
    RevisionAccessContext getProjectRevisionContext(String projectId);

    RevisionAccessContext getProjectEntityRevisionContext(String projectId, String entityPath);

    RevisionAccessContext getProjectPackageRevisionContext(String projectId, String packagePath);

    default RevisionAccessContext getUserWorkspaceRevisionContext(String projectId, String workspaceId)
    {
        return this.getWorkspaceRevisionContext(projectId, workspaceId, WorkspaceType.USER);
    }

    default RevisionAccessContext getGroupWorkspaceRevisionContext(String projectId, String workspaceId)
    {
        return this.getWorkspaceRevisionContext(projectId, workspaceId, WorkspaceType.GROUP);
    }

    // for backward compatibility
    @Deprecated
    default RevisionAccessContext getWorkspaceRevisionContext(String projectId, String workspaceId)
    {
        return getWorkspaceRevisionContext(projectId, workspaceId, WorkspaceType.USER);
    }

    RevisionAccessContext getWorkspaceRevisionContext(String projectId, String workspaceId, WorkspaceType workspaceType);

    default RevisionAccessContext getBackupUserWorkspaceRevisionContext(String projectId, String workspaceId)
    {
        return this.getBackupWorkspaceRevisionContext(projectId, workspaceId, WorkspaceType.USER);
    }

    default RevisionAccessContext getBackupGroupWorkspaceRevisionContext(String projectId, String workspaceId)
    {
        return this.getBackupWorkspaceRevisionContext(projectId, workspaceId, WorkspaceType.GROUP);
    }

    RevisionAccessContext getBackupWorkspaceRevisionContext(String projectId, String workspaceId, WorkspaceType workspaceType);

    default RevisionAccessContext getUserWorkspaceWithConflictResolutionRevisionContext(String projectId, String workspaceId)
    {
        return this.getWorkspaceWithConflictResolutionRevisionContext(projectId, workspaceId, WorkspaceType.USER);
    }

    default RevisionAccessContext getGroupWorkspaceWithConflictResolutionRevisionContext(String projectId, String workspaceId)
    {
        return this.getWorkspaceWithConflictResolutionRevisionContext(projectId, workspaceId, WorkspaceType.GROUP);
    }

    RevisionAccessContext getWorkspaceWithConflictResolutionRevisionContext(String projectId, String workspaceId, WorkspaceType workspaceType);

    default RevisionAccessContext getUserWorkspaceEntityRevisionContext(String projectId, String workspaceId, String entityPath)
    {
        return this.getWorkspaceEntityRevisionContext(projectId, workspaceId, WorkspaceType.USER, entityPath);
    }

    default RevisionAccessContext getGroupWorkspaceEntityRevisionContext(String projectId, String workspaceId, String entityPath)
    {
        return this.getWorkspaceEntityRevisionContext(projectId, workspaceId, WorkspaceType.GROUP, entityPath);
    }

    RevisionAccessContext getWorkspaceEntityRevisionContext(String projectId, String workspaceId, WorkspaceType workspaceType, String entityPath);

    default RevisionAccessContext getUserWorkspacePackageRevisionContext(String projectId, String workspaceId, String packagePath)
    {
        return this.getWorkspacePackageRevisionContext(projectId, workspaceId, WorkspaceType.USER, packagePath);
    }

    default RevisionAccessContext getGroupWorkspacePackageRevisionContext(String projectId, String workspaceId, String packagePath)
    {
        return this.getWorkspacePackageRevisionContext(projectId, workspaceId, WorkspaceType.GROUP, packagePath);
    }

    RevisionAccessContext getWorkspacePackageRevisionContext(String projectId, String workspaceId, WorkspaceType workspaceType, String packagePath);

    RevisionStatus getRevisionStatus(String projectId, String revisionId);
}
