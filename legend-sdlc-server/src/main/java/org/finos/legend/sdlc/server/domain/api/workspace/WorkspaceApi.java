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
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification;
import org.finos.legend.sdlc.server.domain.api.project.source.WorkspaceSourceSpecification;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider.WorkspaceAccessType;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public interface WorkspaceApi
{
    /**
     * Get a workspace in the given project.
     *
     * @param projectId              project id
     * @param workspaceSpecification workspace specification
     * @return workspace
     */
    Workspace getWorkspace(String projectId, WorkspaceSpecification workspaceSpecification);

    /**
     * Get all workspaces in the given project for the current user subject to the given filters.
     *
     * @param projectId   project id
     * @param types       set of workspace types in scope; null means all types
     * @param accessTypes set of workspace access types in scope; null means all access types
     * @param sources     set of workspace sources in scope; null means all sources
     * @return workspaces
     */
    List<Workspace> getWorkspaces(String projectId, Set<WorkspaceType> types, Set<WorkspaceAccessType> accessTypes, Set<WorkspaceSource> sources);

    /**
     * Get all workspaces in the given project for all users subject to the given filters.
     *
     * @param projectId   project id
     * @param types       set of workspace types in scope
     * @param accessTypes set of workspace access types in scope; null means all types
     * @param sources     set of workspace sources in scope
     * @return workspaces
     */
    List<Workspace> getAllWorkspaces(String projectId, Set<WorkspaceType> types, Set<WorkspaceAccessType> accessTypes, Set<WorkspaceSource> sources);

    /**
     * Create a new workspace in the given project. If the workspace is a user workspace, then it will be for the
     * current user.
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     * @param type        workspace type
     * @param source      workspace source
     * @return new workspace
     */
    Workspace newWorkspace(String projectId, String workspaceId, WorkspaceType type, WorkspaceSource source);

    /**
     * Delete the given workspace.
     *
     * @param projectId              project id
     * @param workspaceSpecification workspace specification
     */
    void deleteWorkspace(String projectId, WorkspaceSpecification workspaceSpecification);

    /**
     * Check if a workspace is outdated. i.e. if the workspace base revision is the latest revision in project.
     *
     * @param projectId              project id
     * @param workspaceSpecification workspace specification
     * @return flag indicating if a workspace is outdated
     */
    boolean isWorkspaceOutdated(String projectId, WorkspaceSpecification workspaceSpecification);

    /**
     * Check if a workspace is in conflict resolution mode
     *
     * @param projectId              project id
     * @param workspaceSpecification workspace specification
     * @return flag indicating if a workspace is in conflict resolution mode
     */
    boolean isWorkspaceInConflictResolutionMode(String projectId, WorkspaceSpecification workspaceSpecification);

    /**
     * Update the workspace with the latest committed changes. Potentially, this needs to handle conflict resolution.
     *
     * @param projectId              project id
     * @param workspaceSpecification workspace specification
     * @return a workspace update report
     */
    WorkspaceUpdateReport updateWorkspace(String projectId, WorkspaceSpecification workspaceSpecification);

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

    // Deprecated APIs

    /**
     * Get all user workspaces in the given project for the current user.
     *
     * @param projectId project id
     * @return user workspaces in the current project
     */
    @Deprecated
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
    @Deprecated
    default List<Workspace> getGroupWorkspaces(String projectId)
    {
        return getWorkspaces(projectId, null, Collections.singleton(WorkspaceType.GROUP));
    }

    /**
     * Get all workspaces of desired type(s) in the given project for the current user.
     *
     * @param projectId             project id
     * @param workspaceTypes        set of workspace types in scope
     * @param patchReleaseVersionId patch release version branch the workspace has to be created from
     * @return all workspaces of desired type(s) in the current project for the current user
     */
    @Deprecated
    default List<Workspace> getWorkspaces(String projectId, VersionId patchReleaseVersionId, Set<WorkspaceType> workspaceTypes)
    {
        return getWorkspaces(projectId, workspaceTypes, Collections.singleton(WorkspaceAccessType.WORKSPACE), Collections.singleton((patchReleaseVersionId == null) ? WorkspaceSource.projectWorkspaceSource() : WorkspaceSource.patchWorkspaceSource(patchReleaseVersionId)));
    }

    @Deprecated
    default List<Workspace> getWorkspacesWithConflictResolution(String projectId, VersionId patchReleaseVersionId)
    {
        return getWorkspaces(projectId, Collections.singleton(WorkspaceType.USER), Collections.singleton(WorkspaceAccessType.CONFLICT_RESOLUTION), Collections.singleton((patchReleaseVersionId == null) ? WorkspaceSource.projectWorkspaceSource() : WorkspaceSource.patchWorkspaceSource(patchReleaseVersionId)));
    }

    @Deprecated
    default List<Workspace> getWorkspacesWithConflictResolution(String projectId)
    {
        return getWorkspacesWithConflictResolution(projectId, null);
    }

    @Deprecated
    default List<Workspace> getBackupWorkspaces(String projectId, VersionId patchReleaseVersionId)
    {
        return getWorkspaces(projectId, Collections.singleton(WorkspaceType.USER), Collections.singleton(WorkspaceAccessType.BACKUP), Collections.singleton((patchReleaseVersionId == null) ? WorkspaceSource.projectWorkspaceSource() : WorkspaceSource.patchWorkspaceSource(patchReleaseVersionId)));
    }

    @Deprecated
    default List<Workspace> getBackupWorkspaces(String projectId)
    {
        return getBackupWorkspaces(projectId, null);
    }

    /**
     * Get all user workspaces in the given project for all users.
     *
     * @param projectId project id
     * @return all user workspaces in the current project
     */
    @Deprecated
    default List<Workspace> getAllUserWorkspaces(String projectId)
    {
        return getAllWorkspaces(projectId, Collections.singleton(WorkspaceType.USER), null, Collections.singleton(WorkspaceSource.projectWorkspaceSource()));
    }

    /**
     * Get all workspaces in the given project for all users.
     *
     * @param projectId project id
     * @return all workspaces in the current project
     */
    @Deprecated
    default List<Workspace> getAllWorkspaces(String projectId)
    {
        return getAllWorkspaces(projectId, null, null, Collections.singleton(WorkspaceSource.projectWorkspaceSource()));
    }

    /**
     * Get all workspaces of desired type(s) in the given project for all users.
     *
     * @param projectId             project id
     * @param patchReleaseVersionId patch release version
     * @param workspaceTypes        set of workspace types in scope
     * @return all workspaces of desired type(s) in the current project
     */
    @Deprecated
    default List<Workspace> getAllWorkspaces(String projectId, VersionId patchReleaseVersionId, Set<WorkspaceType> workspaceTypes)
    {
        return getAllWorkspaces(projectId, workspaceTypes, Collections.singleton(WorkspaceAccessType.WORKSPACE), Collections.singleton((patchReleaseVersionId == null) ? WorkspaceSource.projectWorkspaceSource() : WorkspaceSource.patchWorkspaceSource(patchReleaseVersionId)));
    }

    /**
     * Get a workspace in the given project for the current user.
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     * @return user workspace
     */
    @Deprecated
    default Workspace getUserWorkspace(String projectId, String workspaceId)
    {
        return getWorkspace(projectId, WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.USER));
    }

    /**
     * Get a workspace in the given project for the current user.
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     * @return user workspace
     */
    @Deprecated
    default Workspace getGroupWorkspace(String projectId, String workspaceId)
    {
        return getWorkspace(projectId, WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.GROUP));
    }

    /**
     * Get a workspace in the given project for the current user.
     *
     * @param projectId           project id
     * @param sourceSpecification source specification
     * @return user workspace
     */
    @Deprecated
    default Workspace getWorkspace(String projectId, SourceSpecification sourceSpecification)
    {
        if (!(sourceSpecification instanceof WorkspaceSourceSpecification))
        {
            throw new IllegalArgumentException("Not a workspace source specification: " + sourceSpecification);
        }
        return getWorkspace(projectId, ((WorkspaceSourceSpecification) sourceSpecification).getWorkspaceSpecification());
    }

    @Deprecated
    default Workspace getUserWorkspaceWithConflictResolution(String projectId, String workspaceId)
    {
        return getWorkspace(projectId, WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.USER, WorkspaceAccessType.CONFLICT_RESOLUTION));
    }

    @Deprecated
    default Workspace getGroupWorkspaceWithConflictResolution(String projectId, String workspaceId)
    {
        return getWorkspace(projectId, WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.GROUP, WorkspaceAccessType.CONFLICT_RESOLUTION));
    }

    @Deprecated
    default Workspace getWorkspaceWithConflictResolution(String projectId, SourceSpecification sourceSpecification)
    {
        if (!(sourceSpecification instanceof WorkspaceSourceSpecification) || (((WorkspaceSourceSpecification) sourceSpecification).getWorkspaceSpecification().getAccessType() != WorkspaceAccessType.CONFLICT_RESOLUTION))
        {
            throw new IllegalArgumentException("Not a conflict resolution workspace source specification: " + sourceSpecification);
        }
        return getWorkspace(projectId, ((WorkspaceSourceSpecification) sourceSpecification).getWorkspaceSpecification());
    }

    @Deprecated
    default Workspace getBackupUserWorkspace(String projectId, String workspaceId)
    {
        return getWorkspace(projectId, WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.USER, WorkspaceAccessType.BACKUP));
    }

    @Deprecated
    default Workspace getBackupGroupWorkspace(String projectId, String workspaceId)
    {
        return getWorkspace(projectId, WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.GROUP, WorkspaceAccessType.BACKUP));
    }

    @Deprecated
    default Workspace getBackupWorkspace(String projectId, SourceSpecification sourceSpecification)
    {
        if (!(sourceSpecification instanceof WorkspaceSourceSpecification) || (((WorkspaceSourceSpecification) sourceSpecification).getWorkspaceSpecification().getAccessType() != WorkspaceAccessType.BACKUP))
        {
            throw new IllegalArgumentException("Not a backup workspace source specification: " + sourceSpecification);
        }
        return getWorkspace(projectId, ((WorkspaceSourceSpecification) sourceSpecification).getWorkspaceSpecification());
    }

    /**
     * Check if a user workspace is outdated. i.e. if the workspace base revision is the latest revision in project.
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     * @return flag indicating if a user workspace is outdated
     */
    @Deprecated
    default boolean isUserWorkspaceOutdated(String projectId, String workspaceId)
    {
        return isWorkspaceOutdated(projectId, WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.USER));
    }

    /**
     * Check if a group workspace is outdated. i.e. if the workspace base revision is the latest revision in project.
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     * @return flag indicating if a group workspace is outdated
     */
    @Deprecated
    default boolean isGroupWorkspaceOutdated(String projectId, String workspaceId)
    {
        return isWorkspaceOutdated(projectId, WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.GROUP));
    }

    /**
     * Check if a workspace is outdated. i.e. if the workspace base revision is the latest revision in project.
     *
     * @param projectId           project id
     * @param sourceSpecification source specification
     * @return flag indicating if a workspace is outdated
     */
    @Deprecated
    default boolean isWorkspaceOutdated(String projectId, SourceSpecification sourceSpecification)
    {
        if (!(sourceSpecification instanceof WorkspaceSourceSpecification))
        {
            throw new IllegalArgumentException("Not a workspace source specification: " + sourceSpecification);
        }
        return isWorkspaceOutdated(projectId, ((WorkspaceSourceSpecification) sourceSpecification).getWorkspaceSpecification());
    }

    @Deprecated
    default boolean isUserWorkspaceWithConflictResolutionOutdated(String projectId, String workspaceId)
    {
        return isWorkspaceOutdated(projectId, WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.USER, WorkspaceAccessType.CONFLICT_RESOLUTION));
    }

    @Deprecated
    default boolean isGroupWorkspaceWithConflictResolutionOutdated(String projectId, String workspaceId)
    {
        return isWorkspaceOutdated(projectId, WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.GROUP, WorkspaceAccessType.CONFLICT_RESOLUTION));
    }

    @Deprecated
    default boolean isWorkspaceWithConflictResolutionOutdated(String projectId, SourceSpecification sourceSpecification)
    {
        if (!(sourceSpecification instanceof WorkspaceSourceSpecification) || (((WorkspaceSourceSpecification) sourceSpecification).getWorkspaceSpecification().getAccessType() != WorkspaceAccessType.CONFLICT_RESOLUTION))
        {
            throw new IllegalArgumentException("Not a conflict resolution workspace source specification: " + sourceSpecification);
        }
        return isWorkspaceOutdated(projectId, ((WorkspaceSourceSpecification) sourceSpecification).getWorkspaceSpecification());
    }

    @Deprecated
    default boolean isBackupUserWorkspaceOutdated(String projectId, String workspaceId)
    {
        return isWorkspaceOutdated(projectId, WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.USER, WorkspaceAccessType.BACKUP));
    }

    @Deprecated
    default boolean isBackupGroupWorkspaceOutdated(String projectId, String workspaceId)
    {
        return isWorkspaceOutdated(projectId, WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.GROUP, WorkspaceAccessType.BACKUP));
    }

    @Deprecated
    default boolean isBackupWorkspaceOutdated(String projectId, SourceSpecification sourceSpecification)
    {
        if (!(sourceSpecification instanceof WorkspaceSourceSpecification) || (((WorkspaceSourceSpecification) sourceSpecification).getWorkspaceSpecification().getAccessType() != WorkspaceAccessType.BACKUP))
        {
            throw new IllegalArgumentException("Not a backup workspace source specification: " + sourceSpecification);
        }
        return isWorkspaceOutdated(projectId, ((WorkspaceSourceSpecification) sourceSpecification).getWorkspaceSpecification());
    }

    /**
     * Check if a user workspace is in conflict resolution mode
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     * @return flag indicating if a user workspace is in conflict resolution mode
     */
    @Deprecated
    default boolean isUserWorkspaceInConflictResolutionMode(String projectId, String workspaceId)
    {
        return isWorkspaceInConflictResolutionMode(projectId, WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.USER));
    }

    /**
     * Check if a group workspace is in conflict resolution mode
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     * @return flag indicating if a group workspace is in conflict resolution mode
     */
    @Deprecated
    default boolean isGroupWorkspaceInConflictResolutionMode(String projectId, String workspaceId)
    {
        return isWorkspaceInConflictResolutionMode(projectId, WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.GROUP));
    }

    /**
     * Check if a workspace is in conflict resolution mode
     *
     * @param projectId           project id
     * @param sourceSpecification source specification
     * @return flag indicating if a workspace is in conflict resolution mode
     */
    @Deprecated
    default boolean isWorkspaceInConflictResolutionMode(String projectId, SourceSpecification sourceSpecification)
    {
        if (!(sourceSpecification instanceof WorkspaceSourceSpecification))
        {
            throw new IllegalArgumentException("Not a workspace source specification: " + sourceSpecification);
        }
        return isWorkspaceInConflictResolutionMode(projectId, ((WorkspaceSourceSpecification) sourceSpecification).getWorkspaceSpecification());
    }

    /**
     * Create a new user workspace in the given project for the current user.
     *
     * @param projectId   project id
     * @param workspaceId new workspace id
     * @return new workspace
     */
    @Deprecated
    default Workspace newUserWorkspace(String projectId, String workspaceId)
    {
        return newWorkspace(projectId, workspaceId, WorkspaceType.USER, WorkspaceSource.projectWorkspaceSource());
    }

    /**
     * Create a new group workspace in the given project for the current user.
     *
     * @param projectId   project id
     * @param workspaceId new workspace id
     * @return new workspace
     */
    @Deprecated
    default Workspace newGroupWorkspace(String projectId, String workspaceId)
    {
        return newWorkspace(projectId, workspaceId, WorkspaceType.GROUP, WorkspaceSource.projectWorkspaceSource());
    }

    /**
     * Create a new workspace in the given project for the current user.
     *
     * @param projectId           project id
     * @param sourceSpecification source specification
     * @return new workspace
     */
    @Deprecated
    default Workspace newWorkspace(String projectId, SourceSpecification sourceSpecification)
    {
        WorkspaceSpecification workspaceSpec = ((WorkspaceSourceSpecification) sourceSpecification).getWorkspaceSpecification();
        return newWorkspace(projectId, workspaceSpec.getId(), workspaceSpec.getType(), workspaceSpec.getSource());
    }

    /**
     * Delete the given user workspace.
     *
     * @param projectId   project id
     * @param workspaceId id of workspace to delete
     */
    @Deprecated
    default void deleteUserWorkspace(String projectId, String workspaceId)
    {
        deleteWorkspace(projectId, WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.USER));
    }

    /**
     * Delete the given workspace.
     *
     * @param projectId   project id
     * @param workspaceId id of workspace to delete
     */
    @Deprecated
    default void deleteGroupWorkspace(String projectId, String workspaceId)
    {
        deleteWorkspace(projectId, WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.GROUP));
    }

    /**
     * Delete the given workspace.
     *
     * @param projectId           project id
     * @param sourceSpecification source specification
     */
    @Deprecated
    default void deleteWorkspace(String projectId, SourceSpecification sourceSpecification)
    {
        deleteWorkspace(projectId, ((WorkspaceSourceSpecification) sourceSpecification).getWorkspaceSpecification());
    }

    /**
     * Update the user workspace with the latest committed changes. Potentially, this needs to handle conflict resolution.
     *
     * @param projectId   project id
     * @param workspaceId id of workspace to update
     * @return a workspace update report
     */
    @Deprecated
    default WorkspaceUpdateReport updateUserWorkspace(String projectId, String workspaceId)
    {
        return updateWorkspace(projectId, WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.USER));
    }

    /**
     * Update the group workspace with the latest committed changes. Potentially, this needs to handle conflict resolution.
     *
     * @param projectId   project id
     * @param workspaceId id of workspace to update
     * @return a workspace update report
     */
    @Deprecated
    default WorkspaceUpdateReport updateGroupWorkspace(String projectId, String workspaceId)
    {
        return updateWorkspace(projectId, WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.GROUP));
    }

    /**
     * Update the workspace with the latest committed changes. Potentially, this needs to handle conflict resolution.
     *
     * @param projectId           project id
     * @param sourceSpecification source specification
     * @return a workspace update report
     */
    @Deprecated
    default WorkspaceUpdateReport updateWorkspace(String projectId, SourceSpecification sourceSpecification)
    {
        return updateWorkspace(projectId, ((WorkspaceSourceSpecification) sourceSpecification).getWorkspaceSpecification());
    }
}
