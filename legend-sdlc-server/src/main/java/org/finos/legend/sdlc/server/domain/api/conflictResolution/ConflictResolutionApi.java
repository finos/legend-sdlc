// Copyright 2026 Goldman Sachs
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
import org.finos.legend.sdlc.project.source.SourceSpecification;
import org.finos.legend.sdlc.project.source.WorkspaceSourceSpecification;
import org.finos.legend.sdlc.project.workspace.WorkspaceSpecification;
import org.finos.legend.sdlc.server.application.entity.PerformChangesCommand;

/**
 * @deprecated Retained temporarily for backward compatibility. Use
 * {@link org.finos.legend.sdlc.backend.api.conflictresolution.ConflictResolutionApi} instead. The
 * {@link PerformChangesCommand}-based methods live on this bridge only: the backend-neutral API takes the
 * message, entity changes, and reference revision id directly.
 */
@Deprecated
public interface ConflictResolutionApi extends org.finos.legend.sdlc.backend.api.conflictresolution.ConflictResolutionApi
{
    /**
     * @deprecated Use
     * {@link #acceptConflictResolution(String, WorkspaceSpecification, String, java.util.List, String)} instead.
     */
    @Deprecated
    default void acceptConflictResolution(String projectId, WorkspaceSpecification workspaceSpecification, PerformChangesCommand command)
    {
        acceptConflictResolution(projectId, workspaceSpecification, command.getMessage(), command.getEntityChanges(), command.getRevisionId());
    }

    /**
     * @deprecated Use
     * {@link #acceptConflictResolution(String, WorkspaceSpecification, String, java.util.List, String)} instead.
     */
    @Deprecated
    default void acceptConflictResolutionInUserWorkspace(String projectId, String workspaceId, PerformChangesCommand command)
    {
        acceptConflictResolution(projectId, WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.USER), command);
    }

    /**
     * @deprecated Use
     * {@link #acceptConflictResolution(String, WorkspaceSpecification, String, java.util.List, String)} instead.
     */
    @Deprecated
    default void acceptConflictResolutionInGroupWorkspace(String projectId, String workspaceId, PerformChangesCommand command)
    {
        acceptConflictResolution(projectId, WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.GROUP), command);
    }

    /**
     * @deprecated Use
     * {@link #acceptConflictResolution(String, WorkspaceSpecification, String, java.util.List, String)} instead.
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
