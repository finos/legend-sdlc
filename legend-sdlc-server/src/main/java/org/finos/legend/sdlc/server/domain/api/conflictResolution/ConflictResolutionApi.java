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

package org.finos.legend.sdlc.server.domain.api.conflictResolution;

import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.server.application.entity.PerformChangesCommand;

public interface ConflictResolutionApi
{
    /**
     * Discard/Abandon conflict resolution in a user workspace, as a result, we will delete the workspace with conflict resolution that
     * we created when we started conflict resolution.
     *
     * @param projectId   project id
     * @param workspaceId id of workspace with conflict resolution to delete
     */
    default void discardConflictResolutionInGroupWorkspace(String projectId, String workspaceId)
    {
        this.discardConflictResolution(projectId, workspaceId, WorkspaceType.GROUP);
    }

    /**
     * Discard/Abandon conflict resolution in a group workspace, as a result, we will delete the workspace with conflict resolution that
     * we created when we started conflict resolution.
     *
     * @param projectId   project id
     * @param workspaceId id of workspace with conflict resolution to delete
     */
    default void discardConflictResolutionInUserWorkspace(String projectId, String workspaceId)
    {
        this.discardConflictResolution(projectId, workspaceId, WorkspaceType.USER);
    }

    /**
     * Discard/Abandon conflict resolution, as a result, we will delete the workspace with conflict resolution that
     * we created when we started conflict resolution.
     *
     * @param projectId     project id
     * @param workspaceId   id of workspace with conflict resolution to delete
     * @param workspaceType workspace type
     */
    void discardConflictResolution(String projectId, String workspaceId, WorkspaceType workspaceType);

    /**
     * Discard all conflict resolution changes in a user workspace, effectively delete the workspace with conflict resolution and the original
     * workspace and create a new workspace from the current revision of the project.
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     */
    default void discardChangesConflictResolutionInUserWorkspace(String projectId, String workspaceId)
    {
        this.discardConflictResolution(projectId, workspaceId, WorkspaceType.USER);
    }

    /**
     * Discard all conflict resolution changes in a group workspace, effectively delete the workspace with conflict resolution and the original
     * workspace and create a new workspace from the current revision of the project.
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     */
    default void discardChangesConflictResolutionInGroupWorkspace(String projectId, String workspaceId)
    {
        this.discardConflictResolution(projectId, workspaceId, WorkspaceType.GROUP);
    }

    /**
     * Discard all conflict resolution changes, effectively delete the workspace with conflict resolution and the original
     * workspace and create a new workspace from the current revision of the project.
     *
     * @param projectId     project id
     * @param workspaceId   workspace id
     * @param workspaceType workspace type
     */
    void discardChangesConflictResolution(String projectId, String workspaceId, WorkspaceType workspaceType);

    /**
     * Accept the conflict resolution in a user workspace. This will apply the entity changes (to resolve conflicts) and
     * replace the original workspace by the workspace with conflict resolution.
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     * @param command     entity changes to resolve any conflicts
     */
    default void acceptConflictResolutionInUserWorkspace(String projectId, String workspaceId, PerformChangesCommand command)
    {
        this.acceptConflictResolution(projectId, workspaceId, WorkspaceType.USER, command);
    }

    /**
     * Accept the conflict resolution in a group workspace. This will apply the entity changes (to resolve conflicts) and
     * replace the original workspace by the workspace with conflict resolution.
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     * @param command     entity changes to resolve any conflicts
     */
    default void acceptConflictResolutionInGroupWorkspace(String projectId, String workspaceId, PerformChangesCommand command)
    {
        this.acceptConflictResolution(projectId, workspaceId, WorkspaceType.GROUP, command);
    }

    /**
     * Accept the conflict resolution. This will apply the entity changes (to resolve conflicts) and
     * replace the original workspace by the workspace with conflict resolution.
     *
     * @param projectId     project id
     * @param workspaceId   workspace id
     * @param workspaceType workspace type
     * @param command       entity changes to resolve any conflicts
     */
    void acceptConflictResolution(String projectId, String workspaceId, WorkspaceType workspaceType, PerformChangesCommand command);
}
