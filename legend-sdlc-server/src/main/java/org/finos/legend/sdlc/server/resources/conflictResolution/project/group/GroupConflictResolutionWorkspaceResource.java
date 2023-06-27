// Copyright 2021 Goldman Sachs
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

package org.finos.legend.sdlc.server.resources.conflictResolution.project.group;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.server.application.entity.PerformChangesCommand;
import org.finos.legend.sdlc.server.domain.api.conflictResolution.ConflictResolutionApi;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceApi;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.resources.BaseResource;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/projects/{projectId}/groupWorkspaces/{workspaceId}/conflictResolution")
@Api("Conflict Resolution")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class GroupConflictResolutionWorkspaceResource extends BaseResource
{
    private final ConflictResolutionApi conflictResolutionApi;
    private final WorkspaceApi workspaceApi;

    @Inject
    public GroupConflictResolutionWorkspaceResource(ConflictResolutionApi conflictResolutionApi, WorkspaceApi workspaceApi)
    {
        this.conflictResolutionApi = conflictResolutionApi;
        this.workspaceApi = workspaceApi;
    }

    @GET
    @ApiOperation("Get a group workspace with conflict resolution by id")
    public Workspace getGroupWorkspace(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId)
    {
        return executeWithLogging(
                "getting group workspace with conflict resolution " + workspaceId + " for project " + projectId,
                this.workspaceApi::getGroupWorkspaceWithConflictResolution,
                projectId,
                workspaceId
        );
    }

    @GET
    @Path("outdated")
    @ApiOperation("Check if a group workspace with conflict resolution is outdated")
    public boolean isWorkspaceOutdated(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId)
    {
        return executeWithLogging(
                "checking if group workspace with conflict resolution " + workspaceId + " of project " + projectId + " is outdated",
                this.workspaceApi::isGroupWorkspaceWithConflictResolutionOutdated,
                projectId,
                workspaceId
        );
    }

    @DELETE
    @ApiOperation("Discard a conflict resolution for a group workspace")
    public void discardConflictResolution(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId)
    {
        executeWithLogging(
                "discarding conflict resolution for group workspace " + workspaceId + " in project " + projectId,
                this.conflictResolutionApi::discardConflictResolutionInGroupWorkspace,
                projectId,
                workspaceId
        );
    }

    @POST
    @Path("discardChanges")
    @ApiOperation("Discard all conflict resolution changes for a group workspace")
    public void discardChangesConflictResolution(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId)
    {
        executeWithLogging(
                "discarding all conflict resolution changes for group workspace " + workspaceId + " in project " + projectId,
                this.conflictResolutionApi::discardChangesConflictResolutionInGroupWorkspace,
                projectId,
                workspaceId
        );
    }

    @POST
    @Path("accept")
    @ApiOperation("Accept a conflict resolution for a group workspace")
    public void acceptConflictResolution(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId, PerformChangesCommand command)
    {
        LegendSDLCServerException.validateNonNull(command, "Input required to accept conflict resolution");
        executeWithLogging(
                "accept conflict resolution for group workspace " + workspaceId + " in project " + projectId,
                () -> this.conflictResolutionApi.acceptConflictResolutionInGroupWorkspace(projectId, workspaceId, command)
        );
    }
}