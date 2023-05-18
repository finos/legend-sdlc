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

package org.finos.legend.sdlc.server.domain.api.backup;

import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSpecification;

public interface BackupApi
{
    /**
     * Discard the backup user workspace
     *
     * @param projectId    project id
     * @param workspaceId  id of backup workspace
     */
    default void discardBackupUserWorkspace(String projectId, String workspaceId)
    {
        this.discardBackupWorkspace(projectId, WorkspaceSpecification.newUserWorkspaceSpecification(workspaceId));
    }

    /**
     * Discard the backup group workspace
     *
     * @param projectId    project id
     * @param workspaceId  id of backup workspace
     */
    default void discardBackupGroupWorkspace(String projectId, String workspaceId)
    {
        this.discardBackupWorkspace(projectId, WorkspaceSpecification.newGroupWorkspaceSpecification(workspaceId));
    }

    /**
     * Discard the backup workspace
     *
     * @param projectId           project id
     * @param workspaceSpecification workspace specification
     */
    void discardBackupWorkspace(String projectId, WorkspaceSpecification workspaceSpecification);

    /**
     * Recover the backup user workspace
     *
     * @param projectId      project id
     * @param workspaceId    workspace id
     * @param forceRecovery  flag indicating that if the workspace and its backup both exist, we will override the workspace by its backup
     */
    default void recoverBackupUserWorkspace(String projectId, String workspaceId, boolean forceRecovery)
    {
        this.recoverBackupWorkspace(projectId, WorkspaceSpecification.newUserWorkspaceSpecification(workspaceId), forceRecovery);
    }

    /**
     * Recover the backup group workspace
     *
     * @param projectId      project id
     * @param workspaceId    workspace id
     * @param forceRecovery  flag indicating that if the workspace and its backup both exist, we will override the workspace by its backup
     */
    default void recoverBackupGroupWorkspace(String projectId, String workspaceId, boolean forceRecovery)
    {
        this.recoverBackupWorkspace(projectId, WorkspaceSpecification.newGroupWorkspaceSpecification(workspaceId), forceRecovery);
    }

    /**
     * Recover the backup workspace
     *
     * @param projectId              project id
     * @param workspaceSpecification workspace specification
     * @param forceRecovery          flag indicating that if the workspace and its backup both exist, we will override the workspace by its backup
     */
    void recoverBackupWorkspace(String projectId, WorkspaceSpecification workspaceSpecification, boolean forceRecovery);
}
