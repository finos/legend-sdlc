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
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;

import java.util.List;
import java.util.Set;

public interface WorkspaceApi
{
    /**
     * Get all user workspaces in the given project for the current user.
     *
     * @param projectId project id
     * @return user workspaces in the current project
     */
    List<Workspace> getUserWorkspaces(String projectId);

    /**
     * Get all group workspaces of desired type(s) in the given project for the current user.
     *
     * @param projectId project id
     * @return group workspaces in the current project
     */
    List<Workspace> getGroupWorkspaces(String projectId);

    /**
     * Get all workspaces of desired type(s) in the given project for the current user.
     *
     * @param projectId project id
     * @param workspaceAccessTypes set of workspace access types in scope
     * @return all workspaces of desired type(s) in the current project for the current user
     */
    List<Workspace> getWorkspaces(String projectId, Set<ProjectFileAccessProvider.WorkspaceAccessType> workspaceAccessTypes);

    List<Workspace> getWorkspacesWithConflictResolution(String projectId);

    List<Workspace> getBackupWorkspaces(String projectId);

    /**
     * Get all user workspaces in the given project for all users.
     *
     * @param projectId project id
     * @return all user workspaces in the current project
     */
    List<Workspace> getAllUserWorkspaces(String projectId);

    /**
     * Get all workspaces in the given project for all users.
     *
     * @param projectId project id
     * @return all workspaces in the current project
     */
    List<Workspace> getAllWorkspaces(String projectId);

    /**
     * Get all workspaces of desired type(s) in the given project for all users.
     *
     * @param projectId project id
     * @param workspaceAccessTypes set of workspace access types in scope
     * @return all workspaces of desired type(s) in the current project
     */
    List<Workspace> getAllWorkspaces(String projectId, Set<ProjectFileAccessProvider.WorkspaceAccessType> workspaceAccessTypes);

    /**
     * Get a workspace in the given project for the current user.
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     * @return user workspace
     */
    Workspace getUserWorkspace(String projectId, String workspaceId);

    /**
     * Get a workspace in the given project for the current user.
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     * @return user workspace
     */
    Workspace getGroupWorkspace(String projectId, String workspaceId);

    /**
     * Get a workspace in the given project for the current user.
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     * @param isGroup is group workspace
     * @return user workspace
     */
    Workspace getWorkspace(String projectId, String workspaceId, boolean isGroup);

    Workspace getWorkspaceWithConflictResolution(String projectId, String workspaceId);

    Workspace getBackupWorkspace(String projectId, String workspaceId);

    /**
     * Check if a user workspace is outdated. i.e. if the workspace base revision is the latest revision in project.
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     * @return flag indicating if a user workspace is outdated
     */
    boolean isUserWorkspaceOutdated(String projectId, String workspaceId);

    /**
     * Check if a group workspace is outdated. i.e. if the workspace base revision is the latest revision in project.
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     * @return flag indicating if a group workspace is outdated
     */
    boolean isGroupWorkspaceOutdated(String projectId, String workspaceId);

    /**
     * Check if a workspace is outdated. i.e. if the workspace base revision is the latest revision in project.
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     * @param isGroup is group workspace
     * @return flag indicating if a workspace is outdated
     */
    boolean isWorkspaceOutdated(String projectId, String workspaceId, boolean isGroup);

    boolean isWorkspaceWithConflictResolutionOutdated(String projectId, String workspaceId);

    boolean isBackupWorkspaceOutdated(String projectId, String workspaceId);

    /**
     * Check if a user workspace is in conflict resolution mode
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     * @return flag indicating if a user workspace is in conflict resolution mode
     */
    boolean isUserWorkspaceInConflictResolutionMode(String projectId, String workspaceId);

    /**
     * Check if a group workspace is in conflict resolution mode
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     * @return flag indicating if a group workspace is in conflict resolution mode
     */
    boolean isGroupWorkspaceInConflictResolutionMode(String projectId, String workspaceId);

    /**
     * Check if a workspace is in conflict resolution mode
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     * @param isGroup is group workspace
     * @return flag indicating if a workspace is in conflict resolution mode
     */
    boolean isWorkspaceInConflictResolutionMode(String projectId, String workspaceId, boolean isGroup);

    /**
     * Create a new user workspace in the given project for the current user.
     *
     * @param projectId   project id
     * @param workspaceId new workspace id
     * @return new workspace
     */
    Workspace newUserWorkspace(String projectId, String workspaceId);

    /**
     * Create a new group workspace in the given project for the current user.
     *
     * @param projectId   project id
     * @param workspaceId new workspace id
     * @return new workspace
     */
    Workspace newGroupWorkspace(String projectId, String workspaceId);

    /**
     * Create a new workspace in the given project for the current user.
     *
     * @param projectId   project id
     * @param workspaceId new workspace id
     * @param isGroup is group workspace
     * @return new workspace
     */
    Workspace newWorkspace(String projectId, String workspaceId, boolean isGroup);

    /**
     * Delete the given user workspace.
     *
     * @param projectId   project id
     * @param workspaceId id of workspace to delete
     */
    void deleteUserWorkspace(String projectId, String workspaceId);

    /**
     * Delete the given workspace.
     *
     * @param projectId   project id
     * @param workspaceId id of workspace to delete
     */
    void deleteGroupWorkspace(String projectId, String workspaceId);

    /**
     * Delete the given workspace.
     *
     * @param projectId   project id
     * @param workspaceId id of workspace to delete
     * @param isGroup is group workspace
     */
    void deleteWorkspace(String projectId, String workspaceId, boolean isGroup);

    /**
     * Update the user workspace with the latest committed changes. Potentially, this needs to handle conflict resolution.
     *
     * @param projectId   project id
     * @param workspaceId id of workspace to update
     * @return a workspace update report
     */
    WorkspaceUpdateReport updateUserWorkspace(String projectId, String workspaceId);

    /**
     * Update the group workspace with the latest committed changes. Potentially, this needs to handle conflict resolution.
     *
     * @param projectId   project id
     * @param workspaceId id of workspace to update
     * @return a workspace update report
     */
    WorkspaceUpdateReport updateGroupWorkspace(String projectId, String workspaceId);

    /**
     * Update the workspace with the latest committed changes. Potentially, this needs to handle conflict resolution.
     *
     * @param projectId   project id
     * @param workspaceId id of workspace to update
     * @param isGroup is group workspace
     * @return a workspace update report
     */
    WorkspaceUpdateReport updateWorkspace(String projectId, String workspaceId, boolean isGroup);

    interface WorkspaceUpdateReport
    {
        WorkspaceUpdateReportStatus getStatus();

        /**
         * Get the project revision id where the updated or conflict resolution workspace stems from.
         *
         * @return merge base revision id of the workspace
         */
        String getWorkspaceMergeBaseRevisionId();

        /**
         * Get the current revision id of the updated or conflict resolution workspace.
         *
         * @return current workspace revision id
         */
        String getWorkspaceRevisionId();
    }

    enum WorkspaceUpdateReportStatus
    {
        NO_OP, UPDATED, CONFLICT
    }
}
