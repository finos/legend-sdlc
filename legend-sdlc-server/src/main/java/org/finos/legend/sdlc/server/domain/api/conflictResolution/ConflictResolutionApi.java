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
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification;
import org.finos.legend.sdlc.server.domain.api.project.source.WorkspaceSourceSpecification;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSpecification;

public interface ConflictResolutionApi
{
    /**
     * Discard/Abandon conflict resolution, as a result, we will delete the workspace with conflict resolution that
     * we created when we started conflict resolution.
     *
     * @param projectId              project id
     * @param workspaceSpecification workspace specification
     */
    void discardConflictResolution(String projectId, WorkspaceSpecification workspaceSpecification);

    /**
     * Discard all conflict resolution changes, effectively delete the workspace with conflict resolution and the original
     * workspace and create a new workspace from the current revision of the project.
     *
     * @param projectId              project id
     * @param workspaceSpecification workspace specification
     */
    void discardChangesConflictResolution(String projectId, WorkspaceSpecification workspaceSpecification);

    /**
     * Accept the conflict resolution. This will apply the entity changes (to resolve conflicts) and
     * replace the original workspace by the workspace with conflict resolution.
     *
     * @param projectId              project id
     * @param workspaceSpecification workspace specification
     * @param command                entity changes to resolve any conflicts
     */
    void acceptConflictResolution(String projectId, WorkspaceSpecification workspaceSpecification, PerformChangesCommand command);

    // Deprecated APIs

    /**
     * Discard/Abandon conflict resolution in a user workspace, as a result, we will delete the workspace with conflict resolution that
     * we created when we started conflict resolution.
     *
     * @param projectId   project id
     * @param workspaceId id of workspace with conflict resolution to delete
     */
    @Deprecated
    default void discardConflictResolutionInGroupWorkspace(String projectId, String workspaceId)
    {
        discardConflictResolution(projectId, WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.GROUP));
    }

    /**
     * Discard/Abandon conflict resolution in a group workspace, as a result, we will delete the workspace with conflict resolution that
     * we created when we started conflict resolution.
     *
     * @param projectId   project id
     * @param workspaceId id of workspace with conflict resolution to delete
     */
    @Deprecated
    default void discardConflictResolutionInUserWorkspace(String projectId, String workspaceId)
    {
        discardConflictResolution(projectId, WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.USER));
    }

    /**
     * Discard/Abandon conflict resolution, as a result, we will delete the workspace with conflict resolution that
     * we created when we started conflict resolution.
     *
     * @param projectId           project id
     * @param sourceSpecification source specification
     */
    @Deprecated
    default void discardConflictResolution(String projectId, SourceSpecification sourceSpecification)
    {
        if (!(sourceSpecification instanceof WorkspaceSourceSpecification))
        {
            throw new IllegalArgumentException("Not a workspace source specification: " + sourceSpecification);
        }
        discardConflictResolution(projectId, ((WorkspaceSourceSpecification) sourceSpecification).getWorkspaceSpecification());
    }

    /**
     * Discard all conflict resolution changes in a user workspace, effectively delete the workspace with conflict resolution and the original
     * workspace and create a new workspace from the current revision of the project.
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     */
    @Deprecated
    default void discardChangesConflictResolutionInUserWorkspace(String projectId, String workspaceId)
    {
        discardChangesConflictResolution(projectId, WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.USER));
    }

    /**
     * Discard all conflict resolution changes in a group workspace, effectively delete the workspace with conflict resolution and the original
     * workspace and create a new workspace from the current revision of the project.
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     */
    @Deprecated
    default void discardChangesConflictResolutionInGroupWorkspace(String projectId, String workspaceId)
    {
        discardChangesConflictResolution(projectId, WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.GROUP));
    }

    /**
     * Discard all conflict resolution changes, effectively delete the workspace with conflict resolution and the original
     * workspace and create a new workspace from the current revision of the project.
     *
     * @param projectId           project id
     * @param sourceSpecification source specification
     */
    @Deprecated
    default void discardChangesConflictResolution(String projectId, SourceSpecification sourceSpecification)
    {
        if (!(sourceSpecification instanceof WorkspaceSourceSpecification))
        {
            throw new IllegalArgumentException("Not a workspace source specification: " + sourceSpecification);
        }
        discardChangesConflictResolution(projectId, ((WorkspaceSourceSpecification) sourceSpecification).getWorkspaceSpecification());
    }

    /**
     * Accept the conflict resolution in a user workspace. This will apply the entity changes (to resolve conflicts) and
     * replace the original workspace by the workspace with conflict resolution.
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     * @param command     entity changes to resolve any conflicts
     */
    @Deprecated
    default void acceptConflictResolutionInUserWorkspace(String projectId, String workspaceId, PerformChangesCommand command)
    {
        acceptConflictResolution(projectId, WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.USER), command);
    }

    /**
     * Accept the conflict resolution in a group workspace. This will apply the entity changes (to resolve conflicts) and
     * replace the original workspace by the workspace with conflict resolution.
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     * @param command     entity changes to resolve any conflicts
     */
    @Deprecated
    default void acceptConflictResolutionInGroupWorkspace(String projectId, String workspaceId, PerformChangesCommand command)
    {
        acceptConflictResolution(projectId, WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.GROUP), command);
    }

    /**
     * Accept the conflict resolution. This will apply the entity changes (to resolve conflicts) and
     * replace the original workspace by the workspace with conflict resolution.
     *
     * @param projectId           project id
     * @param sourceSpecification source specification
     * @param command             entity changes to resolve any conflicts
     */
    @Deprecated
    default void acceptConflictResolution(String projectId, SourceSpecification sourceSpecification, PerformChangesCommand command)
    {
        if (!(sourceSpecification instanceof WorkspaceSourceSpecification))
        {
            throw new IllegalArgumentException("Not a workspace source specification: " + sourceSpecification);
        }
        acceptConflictResolution(projectId, ((WorkspaceSourceSpecification) sourceSpecification).getWorkspaceSpecification(), command);
    }
}
