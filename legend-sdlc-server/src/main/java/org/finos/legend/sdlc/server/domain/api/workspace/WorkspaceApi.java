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
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;

import java.util.Collections;
import java.util.EnumSet;
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
    default List<Workspace> getUserWorkspaces(String projectId)
    {
        return this.getWorkspaces(projectId, Collections.unmodifiableSet(EnumSet.of(WorkspaceType.USER)));
    }

    /**
     * Get all group workspaces of desired type(s) in the given project for the current user.
     *
     * @param projectId project id
     * @return group workspaces in the current project
     */
    default List<Workspace> getGroupWorkspaces(String projectId)
    {
        return this.getWorkspaces(projectId, Collections.unmodifiableSet(EnumSet.of(WorkspaceType.GROUP)));
    }

    /**
     * Get all workspaces of desired type(s) in the given project for the current user.
     *
     * @param projectId project id
     * @param workspaceTypes set of workspace types in scope
     * @return all workspaces of desired type(s) in the current project for the current user
     */
    List<Workspace> getWorkspaces(String projectId, Set<WorkspaceType> workspaceTypes);

    List<Workspace> getWorkspacesWithConflictResolution(String projectId);

    List<Workspace> getBackupWorkspaces(String projectId);

    /**
     * Get all user workspaces in the given project for all users.
     *
     * @param projectId project id
     * @return all user workspaces in the current project
     */
    default List<Workspace> getAllUserWorkspaces(String projectId)
    {
        return this.getAllWorkspaces(projectId, Collections.unmodifiableSet(EnumSet.of(WorkspaceType.USER)));
    }

    /**
     * Get all workspaces in the given project for all users.
     *
     * @param projectId project id
     * @return all workspaces in the current project
     */
    default List<Workspace> getAllWorkspaces(String projectId)
    {
        return this.getAllWorkspaces(projectId, Collections.unmodifiableSet(EnumSet.of(WorkspaceType.USER, WorkspaceType.GROUP)));
    }

    /**
     * Get all workspaces of desired type(s) in the given project for all users.
     *
     * @param projectId project id
     * @param workspaceTypes set of workspace types in scope
     * @return all workspaces of desired type(s) in the current project
     */
    List<Workspace> getAllWorkspaces(String projectId, Set<WorkspaceType> workspaceTypes);

    /**
     * Get a workspace in the given project for the current user.
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     * @return user workspace
     */
    default Workspace getUserWorkspace(String projectId, String workspaceId)
    {
        return this.getWorkspace(projectId, workspaceId, WorkspaceType.USER);
    }

    /**
     * Get a workspace in the given project for the current user.
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     * @return user workspace
     */
    default Workspace getGroupWorkspace(String projectId, String workspaceId)
    {
        return this.getWorkspace(projectId, workspaceId, WorkspaceType.GROUP);
    }

    /**
     * Get a workspace in the given project for the current user.
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     * @param workspaceType workspace type
     * @return user workspace
     */
    Workspace getWorkspace(String projectId, String workspaceId, WorkspaceType workspaceType);

    default Workspace getUserWorkspaceWithConflictResolution(String projectId, String workspaceId)
    {
        return this.getWorkspaceWithConflictResolution(projectId, workspaceId, WorkspaceType.USER);
    }

    default Workspace getGroupWorkspaceWithConflictResolution(String projectId, String workspaceId)
    {
        return this.getWorkspaceWithConflictResolution(projectId, workspaceId, WorkspaceType.GROUP);
    }

    Workspace getWorkspaceWithConflictResolution(String projectId, String workspaceId, WorkspaceType workspaceType);

    default Workspace getBackupUserWorkspace(String projectId, String workspaceId)
    {
        return this.getBackupWorkspace(projectId, workspaceId, WorkspaceType.USER);
    }

    default Workspace getBackupGroupWorkspace(String projectId, String workspaceId)
    {
        return this.getBackupWorkspace(projectId, workspaceId, WorkspaceType.GROUP);
    }

    Workspace getBackupWorkspace(String projectId, String workspaceId, WorkspaceType workspaceType);

    /**
     * Check if a user workspace is outdated. i.e. if the workspace base revision is the latest revision in project.
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     * @return flag indicating if a user workspace is outdated
     */
    default boolean isUserWorkspaceOutdated(String projectId, String workspaceId)
    {
        return this.isWorkspaceOutdated(projectId, workspaceId, WorkspaceType.USER);
    }

    /**
     * Check if a group workspace is outdated. i.e. if the workspace base revision is the latest revision in project.
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     * @return flag indicating if a group workspace is outdated
     */
    default boolean isGroupWorkspaceOutdated(String projectId, String workspaceId)
    {
        return this.isWorkspaceOutdated(projectId, workspaceId, WorkspaceType.GROUP);
    }

    /**
     * Check if a workspace is outdated. i.e. if the workspace base revision is the latest revision in project.
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     * @param workspaceType workspace type
     * @return flag indicating if a workspace is outdated
     */
    boolean isWorkspaceOutdated(String projectId, String workspaceId, WorkspaceType workspaceType);

    default boolean isUserWorkspaceWithConflictResolutionOutdated(String projectId, String workspaceId)
    {
        return this.isWorkspaceWithConflictResolutionOutdated(projectId, workspaceId, WorkspaceType.USER);
    }

    default boolean isGroupWorkspaceWithConflictResolutionOutdated(String projectId, String workspaceId)
    {
        return this.isWorkspaceWithConflictResolutionOutdated(projectId, workspaceId, WorkspaceType.GROUP);
    }

    boolean isWorkspaceWithConflictResolutionOutdated(String projectId, String workspaceId, WorkspaceType workspaceType);

    default boolean isBackupUserWorkspaceOutdated(String projectId, String workspaceId)
    {
        return this.isBackupWorkspaceOutdated(projectId, workspaceId, WorkspaceType.USER);
    }

    default boolean isBackupGroupWorkspaceOutdated(String projectId, String workspaceId)
    {
        return this.isBackupWorkspaceOutdated(projectId, workspaceId, WorkspaceType.GROUP);
    }

    boolean isBackupWorkspaceOutdated(String projectId, String workspaceId, WorkspaceType workspaceType);

    /**
     * Check if a user workspace is in conflict resolution mode
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     * @return flag indicating if a user workspace is in conflict resolution mode
     */
    default boolean isUserWorkspaceInConflictResolutionMode(String projectId, String workspaceId)
    {
        return this.isWorkspaceInConflictResolutionMode(projectId, workspaceId, WorkspaceType.USER);
    }

    /**
     * Check if a group workspace is in conflict resolution mode
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     * @return flag indicating if a group workspace is in conflict resolution mode
     */
    default boolean isGroupWorkspaceInConflictResolutionMode(String projectId, String workspaceId)
    {
        return this.isWorkspaceInConflictResolutionMode(projectId, workspaceId, WorkspaceType.GROUP);
    }

    /**
     * Check if a workspace is in conflict resolution mode
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     * @param workspaceType workspace type
     * @return flag indicating if a workspace is in conflict resolution mode
     */
    boolean isWorkspaceInConflictResolutionMode(String projectId, String workspaceId, WorkspaceType workspaceType);

    /**
     * Create a new user workspace in the given project for the current user.
     *
     * @param projectId   project id
     * @param workspaceId new workspace id
     * @return new workspace
     */
    default Workspace newUserWorkspace(String projectId, String workspaceId)
    {
        return this.newWorkspace(projectId, workspaceId, WorkspaceType.USER);
    }

    /**
     * Create a new group workspace in the given project for the current user.
     *
     * @param projectId   project id
     * @param workspaceId new workspace id
     * @return new workspace
     */
    default Workspace newGroupWorkspace(String projectId, String workspaceId)
    {
        return this.newWorkspace(projectId, workspaceId, WorkspaceType.GROUP);
    }

    /**
     * Create a new workspace in the given project for the current user.
     *
     * @param projectId   project id
     * @param workspaceId new workspace id
     * @param workspaceType workspace type
     * @return new workspace
     */
    Workspace newWorkspace(String projectId, String workspaceId, WorkspaceType workspaceType);

    /**
     * Delete the given user workspace.
     *
     * @param projectId   project id
     * @param workspaceId id of workspace to delete
     */
    default void deleteUserWorkspace(String projectId, String workspaceId)
    {
        this.deleteWorkspace(projectId, workspaceId, WorkspaceType.USER);
    }

    /**
     * Delete the given workspace.
     *
     * @param projectId   project id
     * @param workspaceId id of workspace to delete
     */
    default void deleteGroupWorkspace(String projectId, String workspaceId)
    {
        this.deleteWorkspace(projectId, workspaceId, WorkspaceType.GROUP);
    }

    /**
     * Delete the given workspace.
     *
     * @param projectId   project id
     * @param workspaceId id of workspace to delete
     * @param workspaceType workspace type
     */
    void deleteWorkspace(String projectId, String workspaceId, WorkspaceType workspaceType);

    /**
     * Update the user workspace with the latest committed changes. Potentially, this needs to handle conflict resolution.
     *
     * @param projectId   project id
     * @param workspaceId id of workspace to update
     * @return a workspace update report
     */
    default WorkspaceUpdateReport updateUserWorkspace(String projectId, String workspaceId)
    {
        return this.updateWorkspace(projectId, workspaceId, WorkspaceType.USER);
    }

    /**
     * Update the group workspace with the latest committed changes. Potentially, this needs to handle conflict resolution.
     *
     * @param projectId   project id
     * @param workspaceId id of workspace to update
     * @return a workspace update report
     */
    default WorkspaceUpdateReport updateGroupWorkspace(String projectId, String workspaceId)
    {
        return this.updateWorkspace(projectId, workspaceId, WorkspaceType.GROUP);
    }

    /**
     * Update the workspace with the latest committed changes. Potentially, this needs to handle conflict resolution.
     *
     * @param projectId   project id
     * @param workspaceId id of workspace to update
     * @param workspaceType workspace type
     * @return a workspace update report
     */
    WorkspaceUpdateReport updateWorkspace(String projectId, String workspaceId, WorkspaceType workspaceType);

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
