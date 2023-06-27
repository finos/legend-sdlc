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
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.domain.api.project.SourceSpecification;

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
        return getWorkspaces(projectId, null, Collections.singleton(WorkspaceType.USER));
    }

    /**
     * Get all group workspaces of desired type(s) in the given project for the current user.
     *
     * @param projectId project id
     * @return group workspaces in the current project
     */
    default List<Workspace> getGroupWorkspaces(String projectId)
    {
        return getWorkspaces(projectId, null, Collections.singleton(WorkspaceType.GROUP));
    }

    /**
     * Get all workspaces of desired type(s) in the given project for the current user.
     *
     * @param projectId project id
     * @param workspaceTypes set of workspace types in scope
     * @param patchReleaseVersionId patch release version branch the workspace has to be created from
     * @return all workspaces of desired type(s) in the current project for the current user
     */
    List<Workspace> getWorkspaces(String projectId, VersionId patchReleaseVersionId, Set<WorkspaceType> workspaceTypes);

    List<Workspace> getWorkspacesWithConflictResolution(String projectId, VersionId patchReleaseVersionId);

    default List<Workspace> getWorkspacesWithConflictResolution(String projectId)
    {
        return this.getWorkspacesWithConflictResolution(projectId, null);
    }

    List<Workspace> getBackupWorkspaces(String projectId, VersionId patchReleaseVersionId);

    default List<Workspace> getBackupWorkspaces(String projectId)
    {
        return this.getBackupWorkspaces(projectId, null);
    }

    /**
     * Get all user workspaces in the given project for all users.
     *
     * @param projectId project id
     * @return all user workspaces in the current project
     */
    default List<Workspace> getAllUserWorkspaces(String projectId)
    {
        return getAllWorkspaces(projectId, null, Collections.singleton(WorkspaceType.USER));
    }

    /**
     * Get all workspaces in the given project for all users.
     *
     * @param projectId project id
     * @return all workspaces in the current project
     */
    default List<Workspace> getAllWorkspaces(String projectId)
    {
        return getAllWorkspaces(projectId, null, EnumSet.allOf(WorkspaceType.class));
    }

    /**
     * Get all workspaces of desired type(s) in the given project for all users.
     *
     * @param projectId project id
     * @param patchReleaseVersionId patch release version
     * @param workspaceTypes set of workspace types in scope
     * @return all workspaces of desired type(s) in the current project
     */
    List<Workspace> getAllWorkspaces(String projectId, VersionId patchReleaseVersionId, Set<WorkspaceType> workspaceTypes);

    /**
     * Get a workspace in the given project for the current user.
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     * @return user workspace
     */
    default Workspace getUserWorkspace(String projectId, String workspaceId)
    {
        return getWorkspace(projectId, SourceSpecification.newUserWorkspaceSourceSpecification(workspaceId));
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
        return getWorkspace(projectId, SourceSpecification.newGroupWorkspaceSourceSpecification(workspaceId));
    }

    /**
     * Get a workspace in the given project for the current user.
     *
     * @param projectId   project id
     * @param sourceSpecification  source specification
     * @return user workspace
     */
    Workspace getWorkspace(String projectId, SourceSpecification sourceSpecification);

    default Workspace getUserWorkspaceWithConflictResolution(String projectId, String workspaceId)
    {
        return getWorkspaceWithConflictResolution(projectId, SourceSpecification.newUserWorkspaceSourceSpecification(workspaceId));
    }

    default Workspace getGroupWorkspaceWithConflictResolution(String projectId, String workspaceId)
    {
        return getWorkspaceWithConflictResolution(projectId, SourceSpecification.newGroupWorkspaceSourceSpecification(workspaceId));
    }

    Workspace getWorkspaceWithConflictResolution(String projectId, SourceSpecification sourceSpecification);

    default Workspace getBackupUserWorkspace(String projectId, String workspaceId)
    {
        return getBackupWorkspace(projectId, SourceSpecification.newUserWorkspaceSourceSpecification(workspaceId));
    }

    default Workspace getBackupGroupWorkspace(String projectId, String workspaceId)
    {
        return getBackupWorkspace(projectId, SourceSpecification.newGroupWorkspaceSourceSpecification(workspaceId));
    }

    Workspace getBackupWorkspace(String projectId, SourceSpecification sourceSpecification);

    /**
     * Check if a user workspace is outdated. i.e. if the workspace base revision is the latest revision in project.
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     * @return flag indicating if a user workspace is outdated
     */
    default boolean isUserWorkspaceOutdated(String projectId, String workspaceId)
    {
        return isWorkspaceOutdated(projectId, SourceSpecification.newUserWorkspaceSourceSpecification(workspaceId));
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
        return isWorkspaceOutdated(projectId, SourceSpecification.newGroupWorkspaceSourceSpecification(workspaceId));
    }

    /**
     * Check if a workspace is outdated. i.e. if the workspace base revision is the latest revision in project.
     *
     * @param projectId   project id
     * @param sourceSpecification  source specification
     * @return flag indicating if a workspace is outdated
     */
    boolean isWorkspaceOutdated(String projectId, SourceSpecification sourceSpecification);

    default boolean isUserWorkspaceWithConflictResolutionOutdated(String projectId, String workspaceId)
    {
        return isWorkspaceWithConflictResolutionOutdated(projectId, SourceSpecification.newUserWorkspaceSourceSpecification(workspaceId));
    }

    default boolean isGroupWorkspaceWithConflictResolutionOutdated(String projectId, String workspaceId)
    {
        return isWorkspaceWithConflictResolutionOutdated(projectId, SourceSpecification.newGroupWorkspaceSourceSpecification(workspaceId));
    }

    boolean isWorkspaceWithConflictResolutionOutdated(String projectId, SourceSpecification sourceSpecification);

    default boolean isBackupUserWorkspaceOutdated(String projectId, String workspaceId)
    {
        return isBackupWorkspaceOutdated(projectId, SourceSpecification.newUserWorkspaceSourceSpecification(workspaceId));
    }

    default boolean isBackupGroupWorkspaceOutdated(String projectId, String workspaceId)
    {
        return isBackupWorkspaceOutdated(projectId, SourceSpecification.newGroupWorkspaceSourceSpecification(workspaceId));
    }

    boolean isBackupWorkspaceOutdated(String projectId, SourceSpecification sourceSpecification);

    /**
     * Check if a user workspace is in conflict resolution mode
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     * @return flag indicating if a user workspace is in conflict resolution mode
     */
    default boolean isUserWorkspaceInConflictResolutionMode(String projectId, String workspaceId)
    {
        return isWorkspaceInConflictResolutionMode(projectId, SourceSpecification.newUserWorkspaceSourceSpecification(workspaceId));
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
        return isWorkspaceInConflictResolutionMode(projectId, SourceSpecification.newGroupWorkspaceSourceSpecification(workspaceId));
    }

    /**
     * Check if a workspace is in conflict resolution mode
     *
     * @param projectId   project id
     * @param sourceSpecification  source specification
     * @return flag indicating if a workspace is in conflict resolution mode
     */
    boolean isWorkspaceInConflictResolutionMode(String projectId, SourceSpecification sourceSpecification);

    /**
     * Create a new user workspace in the given project for the current user.
     *
     * @param projectId   project id
     * @param workspaceId new workspace id
     * @return new workspace
     */
    default Workspace newUserWorkspace(String projectId, String workspaceId)
    {
        return newWorkspace(projectId, SourceSpecification.newUserWorkspaceSourceSpecification(workspaceId));
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
        return newWorkspace(projectId, SourceSpecification.newGroupWorkspaceSourceSpecification(workspaceId));
    }

    /**
     * Create a new workspace in the given project for the current user.
     *
     * @param projectId   project id
     * @param sourceSpecification  source specification
     * @return new workspace
     */
    Workspace newWorkspace(String projectId, SourceSpecification sourceSpecification);

    /**
     * Delete the given user workspace.
     *
     * @param projectId   project id
     * @param workspaceId id of workspace to delete
     */
    default void deleteUserWorkspace(String projectId, String workspaceId)
    {
        deleteWorkspace(projectId, SourceSpecification.newUserWorkspaceSourceSpecification(workspaceId));
    }

    /**
     * Delete the given workspace.
     *
     * @param projectId   project id
     * @param workspaceId id of workspace to delete
     */
    default void deleteGroupWorkspace(String projectId, String workspaceId)
    {
        deleteWorkspace(projectId, SourceSpecification.newGroupWorkspaceSourceSpecification(workspaceId));
    }

    /**
     * Delete the given workspace.
     *
     * @param projectId   project id
     * @param sourceSpecification  source specification
     */
    void deleteWorkspace(String projectId, SourceSpecification sourceSpecification);

    /**
     * Update the user workspace with the latest committed changes. Potentially, this needs to handle conflict resolution.
     *
     * @param projectId   project id
     * @param workspaceId id of workspace to update
     * @return a workspace update report
     */
    default WorkspaceUpdateReport updateUserWorkspace(String projectId, String workspaceId)
    {
        return updateWorkspace(projectId, SourceSpecification.newUserWorkspaceSourceSpecification(workspaceId));
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
        return updateWorkspace(projectId, SourceSpecification.newGroupWorkspaceSourceSpecification(workspaceId));
    }

    /**
     * Update the workspace with the latest committed changes. Potentially, this needs to handle conflict resolution.
     *
     * @param projectId   project id
     * @param sourceSpecification  source specification
     * @return a workspace update report
     */
    WorkspaceUpdateReport updateWorkspace(String projectId, SourceSpecification sourceSpecification);

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
