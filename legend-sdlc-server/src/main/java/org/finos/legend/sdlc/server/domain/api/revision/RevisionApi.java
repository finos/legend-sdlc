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
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSpecification;

/**
 * Note that all of these APIs support revision ID alias as they all essentially calls getRevision() from RevisionAccessContext
 * which takes into account revision ID alias
 */
public interface RevisionApi
{
    RevisionAccessContext getProjectRevisionContext(String projectId, VersionId patchReleaseVersionId);

    default RevisionAccessContext getProjectRevisionContext(String projectId)
    {
        return this.getProjectRevisionContext(projectId, null);
    }

    RevisionAccessContext getProjectEntityRevisionContext(String projectId, VersionId patchReleaseVersionId, String entityPath);

    default RevisionAccessContext getProjectEntityRevisionContext(String projectId, String entityPath)
    {
        return this.getProjectEntityRevisionContext(projectId, null, entityPath);
    }

    RevisionAccessContext getProjectPackageRevisionContext(String projectId, VersionId patchReleaseVersionId, String packagePath);

    default RevisionAccessContext getProjectPackageRevisionContext(String projectId, String packagePath)
    {
        return this.getProjectPackageRevisionContext(projectId, null, packagePath);
    }

    default RevisionAccessContext getUserWorkspaceRevisionContext(String projectId, String workspaceId)
    {
        return this.getWorkspaceRevisionContext(projectId, WorkspaceSpecification.newUserWorkspaceSpecification(workspaceId));
    }

    default RevisionAccessContext getGroupWorkspaceRevisionContext(String projectId, String workspaceId)
    {
        return this.getWorkspaceRevisionContext(projectId, WorkspaceSpecification.newGroupWorkspaceSpecification(workspaceId));
    }

    // for backward compatibility
    @Deprecated
    default RevisionAccessContext getWorkspaceRevisionContext(String projectId, String workspaceId)
    {
        return getWorkspaceRevisionContext(projectId, WorkspaceSpecification.newUserWorkspaceSpecification(workspaceId));
    }

    RevisionAccessContext getWorkspaceRevisionContext(String projectId, WorkspaceSpecification workspaceSpecification);

    default RevisionAccessContext getBackupUserWorkspaceRevisionContext(String projectId, String workspaceId)
    {
        return this.getBackupWorkspaceRevisionContext(projectId, WorkspaceSpecification.newUserWorkspaceSpecification(workspaceId));
    }

    default RevisionAccessContext getBackupGroupWorkspaceRevisionContext(String projectId, String workspaceId)
    {
        return this.getBackupWorkspaceRevisionContext(projectId, WorkspaceSpecification.newGroupWorkspaceSpecification(workspaceId));
    }

    RevisionAccessContext getBackupWorkspaceRevisionContext(String projectId, WorkspaceSpecification workspaceSpecification);

    default RevisionAccessContext getUserWorkspaceWithConflictResolutionRevisionContext(String projectId, String workspaceId)
    {
        return this.getWorkspaceWithConflictResolutionRevisionContext(projectId, WorkspaceSpecification.newUserWorkspaceSpecification(workspaceId));
    }

    default RevisionAccessContext getGroupWorkspaceWithConflictResolutionRevisionContext(String projectId, String workspaceId)
    {
        return this.getWorkspaceWithConflictResolutionRevisionContext(projectId, WorkspaceSpecification.newGroupWorkspaceSpecification(workspaceId));
    }

    RevisionAccessContext getWorkspaceWithConflictResolutionRevisionContext(String projectId, WorkspaceSpecification workspaceSpecification);

    default RevisionAccessContext getUserWorkspaceEntityRevisionContext(String projectId, String workspaceId, String entityPath)
    {
        return this.getWorkspaceEntityRevisionContext(projectId, WorkspaceSpecification.newUserWorkspaceSpecification(workspaceId), entityPath);
    }

    default RevisionAccessContext getGroupWorkspaceEntityRevisionContext(String projectId, String workspaceId, String entityPath)
    {
        return this.getWorkspaceEntityRevisionContext(projectId, WorkspaceSpecification.newGroupWorkspaceSpecification(workspaceId), entityPath);
    }

    RevisionAccessContext getWorkspaceEntityRevisionContext(String projectId, WorkspaceSpecification workspaceSpecification, String entityPath);

    default RevisionAccessContext getUserWorkspacePackageRevisionContext(String projectId, String workspaceId, String packagePath)
    {
        return this.getWorkspacePackageRevisionContext(projectId, WorkspaceSpecification.newUserWorkspaceSpecification(workspaceId), packagePath);
    }

    default RevisionAccessContext getGroupWorkspacePackageRevisionContext(String projectId, String workspaceId, String packagePath)
    {
        return this.getWorkspacePackageRevisionContext(projectId, WorkspaceSpecification.newGroupWorkspaceSpecification(workspaceId), packagePath);
    }

    RevisionAccessContext getWorkspacePackageRevisionContext(String projectId, WorkspaceSpecification workspaceSpecification, String packagePath);

    RevisionStatus getRevisionStatus(String projectId, VersionId patchReleaseVersionId, String revisionId);

    default RevisionStatus getRevisionStatus(String projectId, String revisionId)
    {
        return this.getRevisionStatus(projectId, null, revisionId);
    }
}
