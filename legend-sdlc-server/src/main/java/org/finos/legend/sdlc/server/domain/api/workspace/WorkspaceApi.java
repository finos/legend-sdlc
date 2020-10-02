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

package org.finos.legend.sdlc.server.domain.api.workspace;

import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;

import java.util.List;

public interface WorkspaceApi
{
    /**
     * Get all workspaces in the given project for the current user.
     *
     * @param projectId project id
     * @return user workspaces in the current project
     */
    List<Workspace> getWorkspaces(String projectId);

    List<Workspace> getWorkspacesWithConflictResolution(String projectId);

    List<Workspace> getBackupWorkspaces(String projectId);

    /**
     * Get all workspaces in the given project for all users.
     *
     * @param projectId project id
     * @return all workspaces in the current project
     */
    List<Workspace> getAllWorkspaces(String projectId);

    /**
     * Get a workspace in the given project for the current user.
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     * @return user workspace
     */
    Workspace getWorkspace(String projectId, String workspaceId);

    Workspace getWorkspaceWithConflictResolution(String projectId, String workspaceId);

    Workspace getBackupWorkspace(String projectId, String workspaceId);

    /**
     * Check if a workspace is outdated. i.e. if the workspace base revision is the latest revision in project.
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     * @return flag indicating if a workspace is outdated
     */
    boolean isWorkspaceOutdated(String projectId, String workspaceId);

    boolean isWorkspaceWithConflictResolutionOutdated(String projectId, String workspaceId);

    boolean isBackupWorkspaceOutdated(String projectId, String workspaceId);

    /**
     * Check if a workspace is in conflict resolution mode
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     * @return flag indicating if a workspace is in conflict resolution mode
     */
    boolean isWorkspaceInConflictResolutionMode(String projectId, String workspaceId);

    /**
     * Create a new workspace in the given project for the current user.
     *
     * @param projectId   project id
     * @param workspaceId new workspace id
     * @return new workspace
     */
    Workspace newWorkspace(String projectId, String workspaceId);

    /**
     * Delete the given workspace.
     *
     * @param projectId   project id
     * @param workspaceId id of workspace to delete
     */
    void deleteWorkspace(String projectId, String workspaceId);

    /**
     * Update the workspace with the latest committed changes. Potentially, this needs to handle conflict resolution.
     *
     * @param projectId   project id
     * @param workspaceId id of workspace to update
     * @return a workspace update report
     */
    WorkspaceUpdateReport updateWorkspace(String projectId, String workspaceId);

    interface WorkspaceUpdateReport
    {
        WorkspaceUpdateReportStatus getStatus();

        /**
         * The project revision ID where the updated or conflict resolution workspace stems from.
         */
        String getWorkspaceMergeBaseRevisionId();

        /**
         * The current revision ID of the updated or conflict resolution workspace.
         */
        String getWorkspaceRevisionId();
    }

    enum WorkspaceUpdateReportStatus
    {
        NO_OP, UPDATED, CONFLICT
    }
}
