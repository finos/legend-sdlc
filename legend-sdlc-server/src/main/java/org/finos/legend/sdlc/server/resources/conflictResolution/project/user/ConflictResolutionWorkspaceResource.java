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

package org.finos.legend.sdlc.server.resources.conflictResolution.project.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.project.workspace.WorkspaceSpecification;
import org.finos.legend.sdlc.server.application.entity.PerformChangesCommand;
import org.finos.legend.sdlc.backend.api.conflictresolution.ConflictResolutionApi;
import org.finos.legend.sdlc.backend.api.workspace.WorkspaceApi;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.resources.BaseResource;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/projects/{projectId}/workspaces/{workspaceId}/conflictResolution")
@Tag(name = "Conflict Resolution")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ConflictResolutionWorkspaceResource extends BaseResource
{
    private final ConflictResolutionApi conflictResolutionApi;
    private final WorkspaceApi workspaceApi;

    @Inject
    public ConflictResolutionWorkspaceResource(ConflictResolutionApi conflictResolutionApi, WorkspaceApi workspaceApi)
    {
        this.conflictResolutionApi = conflictResolutionApi;
        this.workspaceApi = workspaceApi;
    }

    @GET
    @Operation(summary = "Get a user workspace with conflict resolution by id")
    public Workspace getUserWorkspace(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId)
    {
        return executeWithLogging(
                "getting user workspace with conflict resolution " + workspaceId + " for project " + projectId,
                this.workspaceApi::getUserWorkspaceWithConflictResolution,
                projectId,
                workspaceId
        );
    }

    @GET
    @Path("outdated")
    @Operation(summary = "Check if a user workspace with conflict resolution is outdated")
    public boolean isWorkspaceOutdated(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId)
    {
        return executeWithLogging(
                "checking if user workspace with conflict resolution " + workspaceId + " of project " + projectId + " is outdated",
                this.workspaceApi::isUserWorkspaceWithConflictResolutionOutdated,
                projectId,
                workspaceId
        );
    }

    @DELETE
    @Operation(summary = "Discard a conflict resolution for a user workspace")
    public void discardConflictResolution(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId)
    {
        executeWithLogging(
                "discarding conflict resolution for user workspace " + workspaceId + " in project " + projectId,
                this.conflictResolutionApi::discardConflictResolutionInUserWorkspace,
                projectId,
                workspaceId
        );
    }

    @POST
    @Path("discardChanges")
    @Operation(summary = "Discard all conflict resolution changes for a user workspace")
    public void discardChangesConflictResolution(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId)
    {
        executeWithLogging(
                "discarding all conflict resolution changes for user workspace " + workspaceId + " in project " + projectId,
                this.conflictResolutionApi::discardChangesConflictResolutionInUserWorkspace,
                projectId,
                workspaceId
        );
    }

    @POST
    @Path("accept")
    @Operation(summary = "Accept a conflict resolution for a user workspace")
    public void acceptConflictResolution(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId, PerformChangesCommand command)
    {
        LegendSDLCServerException.validateNonNull(command, "Input required to accept conflict resolution");
        executeWithLogging(
                "accept conflict resolution for user workspace " + workspaceId + " in project " + projectId,
                () -> this.conflictResolutionApi.acceptConflictResolution(projectId, WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.USER), command.getMessage(), command.getEntityChanges(), command.getRevisionId())
        );
    }
}
