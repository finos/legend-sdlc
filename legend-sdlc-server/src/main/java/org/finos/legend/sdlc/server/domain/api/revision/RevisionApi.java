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

/**
 * Note that all of these APIs support revision ID alias as they all essentially calls getRevision() from RevisionAccessContext
 * which takes into account revision ID alias
 */
public interface RevisionApi
{
    RevisionAccessContext getProjectRevisionContext(String projectId);

    RevisionAccessContext getProjectEntityRevisionContext(String projectId, String entityPath);

    RevisionAccessContext getProjectPackageRevisionContext(String projectId, String packagePath);

    RevisionAccessContext getUserWorkspaceRevisionContext(String projectId, String workspaceId);

    RevisionAccessContext getGroupWorkspaceRevisionContext(String projectId, String workspaceId);

    RevisionAccessContext getWorkspaceRevisionContext(String projectId, String workspaceId, boolean isGroupWorkspace);

    RevisionAccessContext getBackupWorkspaceRevisionContext(String projectId, String workspaceId);

    RevisionAccessContext getUserWorkspaceWithConflictResolutionRevisionContext(String projectId, String workspaceId);

    RevisionAccessContext getGroupWorkspaceWithConflictResolutionRevisionContext(String projectId, String workspaceId);

    RevisionAccessContext getWorkspaceWithConflictResolutionRevisionContext(String projectId, String workspaceId, boolean isGroupWorkspace);

    RevisionAccessContext getUserWorkspaceEntityRevisionContext(String projectId, String workspaceId, String entityPath);

    RevisionAccessContext getGroupWorkspaceEntityRevisionContext(String projectId, String workspaceId, String entityPath);

    RevisionAccessContext getWorkspaceEntityRevisionContext(String projectId, String workspaceId, boolean isGroupWorkspace, String entityPath);

    RevisionAccessContext getUserWorkspacePackageRevisionContext(String projectId, String workspaceId, String packagePath);

    RevisionAccessContext getGroupWorkspacePackageRevisionContext(String projectId, String workspaceId, String packagePath);

    RevisionAccessContext getWorkspacePackageRevisionContext(String projectId, String workspaceId, boolean isGroupWorkspace, String packagePath);

    RevisionStatus getRevisionStatus(String projectId, String revisionId);
}
