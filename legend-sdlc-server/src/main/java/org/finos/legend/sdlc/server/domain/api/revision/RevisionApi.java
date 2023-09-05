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

import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.revision.RevisionStatus;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification;
import org.finos.legend.sdlc.server.domain.api.project.source.WorkspaceSourceSpecification;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSpecification;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;

/**
 * Note that all of these APIs support revision ID alias as they all essentially calls getRevision() from RevisionAccessContext
 * which takes into account revision ID alias
 */
public interface RevisionApi
{
    RevisionAccessContext getRevisionContext(String projectId, SourceSpecification sourceSpec);

    RevisionAccessContext getPackageRevisionContext(String projectId, SourceSpecification sourceSpec, String packagePath);

    RevisionAccessContext getEntityRevisionContext(String projectId, SourceSpecification sourceSpec, String entityPath);

    RevisionStatus getRevisionStatus(String projectId, String revisionId);

    // Deprecated APIs

    @Deprecated
    default RevisionAccessContext getProjectRevisionContext(String projectId, VersionId patchReleaseVersionId)
    {
        return getRevisionContext(projectId, (patchReleaseVersionId == null) ? SourceSpecification.projectSourceSpecification() : SourceSpecification.patchSourceSpecification(patchReleaseVersionId));
    }

    @Deprecated
    default RevisionAccessContext getProjectRevisionContext(String projectId)
    {
        return getRevisionContext(projectId, SourceSpecification.projectSourceSpecification());
    }

    @Deprecated
    default RevisionAccessContext getProjectEntityRevisionContext(String projectId, VersionId patchReleaseVersionId, String entityPath)
    {
        return getEntityRevisionContext(projectId, (patchReleaseVersionId == null) ? SourceSpecification.projectSourceSpecification() : SourceSpecification.patchSourceSpecification(patchReleaseVersionId), entityPath);
    }

    @Deprecated
    default RevisionAccessContext getProjectEntityRevisionContext(String projectId, String entityPath)
    {
        return getEntityRevisionContext(projectId, SourceSpecification.projectSourceSpecification(), entityPath);
    }

    @Deprecated
    default RevisionAccessContext getProjectPackageRevisionContext(String projectId, VersionId patchReleaseVersionId, String packagePath)
    {
        return getPackageRevisionContext(projectId, (patchReleaseVersionId == null) ? SourceSpecification.projectSourceSpecification() : SourceSpecification.patchSourceSpecification(patchReleaseVersionId), packagePath);
    }

    @Deprecated
    default RevisionAccessContext getProjectPackageRevisionContext(String projectId, String packagePath)
    {
        return getPackageRevisionContext(projectId, SourceSpecification.projectSourceSpecification(), packagePath);
    }

    @Deprecated
    default RevisionAccessContext getUserWorkspaceRevisionContext(String projectId, String workspaceId)
    {
        return getRevisionContext(projectId, SourceSpecification.newUserWorkspaceSourceSpecification(workspaceId));
    }

    @Deprecated
    default RevisionAccessContext getGroupWorkspaceRevisionContext(String projectId, String workspaceId)
    {
        return getRevisionContext(projectId, SourceSpecification.newGroupWorkspaceSourceSpecification(workspaceId));
    }

    @Deprecated
    default RevisionAccessContext getWorkspaceRevisionContext(String projectId, String workspaceId)
    {
        return getRevisionContext(projectId, SourceSpecification.newUserWorkspaceSourceSpecification(workspaceId));
    }

    @Deprecated
    default RevisionAccessContext getWorkspaceRevisionContext(String projectId, SourceSpecification sourceSpec)
    {
        if (!(sourceSpec instanceof WorkspaceSourceSpecification) ||
                (((WorkspaceSourceSpecification) sourceSpec).getWorkspaceSpecification().getAccessType() != ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE))
        {
            throw new IllegalArgumentException("Invalid source specification (must be workspace specification): " + sourceSpec);
        }
        return getRevisionContext(projectId, sourceSpec);
    }

    @Deprecated
    default RevisionAccessContext getBackupUserWorkspaceRevisionContext(String projectId, String workspaceId)
    {
        return getRevisionContext(projectId, SourceSpecification.workspaceSourceSpecification(WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.BACKUP)));
    }

    @Deprecated
    default RevisionAccessContext getBackupGroupWorkspaceRevisionContext(String projectId, String workspaceId)
    {
        return getRevisionContext(projectId, SourceSpecification.workspaceSourceSpecification(WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.GROUP, ProjectFileAccessProvider.WorkspaceAccessType.BACKUP)));
    }

    @Deprecated
    default RevisionAccessContext getBackupWorkspaceRevisionContext(String projectId, SourceSpecification sourceSpec)
    {
        if (!(sourceSpec instanceof WorkspaceSourceSpecification) ||
                (((WorkspaceSourceSpecification) sourceSpec).getWorkspaceSpecification().getAccessType() != ProjectFileAccessProvider.WorkspaceAccessType.BACKUP))
        {
            throw new IllegalArgumentException("Invalid source specification (must be backup workspace specification): " + sourceSpec);
        }
        return getRevisionContext(projectId, sourceSpec);
    }

    @Deprecated
    default RevisionAccessContext getUserWorkspaceWithConflictResolutionRevisionContext(String projectId, String workspaceId)
    {
        return getRevisionContext(projectId, SourceSpecification.workspaceSourceSpecification(WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.CONFLICT_RESOLUTION)));
    }

    @Deprecated
    default RevisionAccessContext getGroupWorkspaceWithConflictResolutionRevisionContext(String projectId, String workspaceId)
    {
        return getRevisionContext(projectId, SourceSpecification.workspaceSourceSpecification(WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.GROUP, ProjectFileAccessProvider.WorkspaceAccessType.CONFLICT_RESOLUTION)));
    }

    @Deprecated
    default RevisionAccessContext getWorkspaceWithConflictResolutionRevisionContext(String projectId, SourceSpecification sourceSpec)
    {
        if (!(sourceSpec instanceof WorkspaceSourceSpecification) ||
                (((WorkspaceSourceSpecification) sourceSpec).getWorkspaceSpecification().getAccessType() != ProjectFileAccessProvider.WorkspaceAccessType.CONFLICT_RESOLUTION))
        {
            throw new IllegalArgumentException("Invalid source specification (must be conflict resolution workspace specification): " + sourceSpec);
        }
        return getRevisionContext(projectId, sourceSpec);
    }

    @Deprecated
    default RevisionAccessContext getUserWorkspaceEntityRevisionContext(String projectId, String workspaceId, String entityPath)
    {
        return getEntityRevisionContext(projectId, SourceSpecification.newUserWorkspaceSourceSpecification(workspaceId), entityPath);
    }

    @Deprecated
    default RevisionAccessContext getGroupWorkspaceEntityRevisionContext(String projectId, String workspaceId, String entityPath)
    {
        return getEntityRevisionContext(projectId, SourceSpecification.newGroupWorkspaceSourceSpecification(workspaceId), entityPath);
    }

    @Deprecated
    default RevisionAccessContext getWorkspaceEntityRevisionContext(String projectId, SourceSpecification sourceSpec, String entityPath)
    {
        if (!(sourceSpec instanceof WorkspaceSourceSpecification) ||
                (((WorkspaceSourceSpecification) sourceSpec).getWorkspaceSpecification().getAccessType() != ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE))
        {
            throw new IllegalArgumentException("Invalid source specification (must be workspace specification): " + sourceSpec);
        }
        return getEntityRevisionContext(projectId, sourceSpec, entityPath);
    }

    @Deprecated
    default RevisionAccessContext getUserWorkspacePackageRevisionContext(String projectId, String workspaceId, String packagePath)
    {
        return getPackageRevisionContext(projectId, SourceSpecification.newUserWorkspaceSourceSpecification(workspaceId), packagePath);
    }

    @Deprecated
    default RevisionAccessContext getGroupWorkspacePackageRevisionContext(String projectId, String workspaceId, String packagePath)
    {
        return getPackageRevisionContext(projectId, SourceSpecification.newGroupWorkspaceSourceSpecification(workspaceId), packagePath);
    }

    @Deprecated
    default RevisionAccessContext getWorkspacePackageRevisionContext(String projectId, SourceSpecification sourceSpec, String packagePath)
    {
        if (!(sourceSpec instanceof WorkspaceSourceSpecification) ||
                (((WorkspaceSourceSpecification) sourceSpec).getWorkspaceSpecification().getAccessType() != ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE))
        {
            throw new IllegalArgumentException("Invalid source specification (must be workspace specification): " + sourceSpec);
        }
        return getPackageRevisionContext(projectId, sourceSpec, packagePath);
    }

    @Deprecated
    default RevisionStatus getRevisionStatus(String projectId, VersionId patchReleaseVersionId, String revisionId)
    {
        return getRevisionStatus(projectId, revisionId);
    }
}
