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

package org.finos.legend.sdlc.server.resources.workspace.project.group;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.backend.api.workspace.WorkspaceApi;
import org.finos.legend.sdlc.server.resources.BaseResource;

import java.util.List;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/projects/{projectId}/groupWorkspaces")
@Tag(name = "Workspaces")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class GroupWorkspacesResource extends BaseResource
{
    private final WorkspaceApi workspaceApi;

    @Inject
    public GroupWorkspacesResource(WorkspaceApi workspaceApi)
    {
        this.workspaceApi = workspaceApi;
    }

    @GET
    @Operation(summary = "Get all group workspaces for a project")
    public List<Workspace> getGroupWorkspaces(@PathParam("projectId") String projectId,
                                              @QueryParam("includeUserWorkspaces")
                                              @DefaultValue("false")
                                              @Parameter(description = "include user workspaces owned by current user") boolean includeUserWorkspaces)
    {
        return executeWithLogging(
                "getting all group" + (includeUserWorkspaces ? " and user" : "") + " workspaces for project " + projectId,
                () -> includeUserWorkspaces ? this.workspaceApi.getAllWorkspaces(projectId) : this.workspaceApi.getGroupWorkspaces(projectId)
        );
    }

    @GET
    @Path("{workspaceId}")
    @Operation(summary = "Get a group workspace for a project by id")
    public Workspace getGroupWorkspace(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId)
    {
        return executeWithLogging(
                "getting group workspace " + workspaceId + " for project " + projectId,
                this.workspaceApi::getGroupWorkspace,
                projectId,
                workspaceId
        );
    }

    @GET
    @Path("{workspaceId}/outdated")
    @Operation(summary = "Check if a group workspace is outdated")
    public boolean isGroupWorkspaceOutdated(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId)
    {
        return executeWithLogging(
                "checking if group workspace " + workspaceId + " of project " + projectId + " is outdated",
                this.workspaceApi::isGroupWorkspaceOutdated,
                projectId,
                workspaceId
        );
    }

    @GET
    @Path("{workspaceId}/inConflictResolutionMode")
    @Operation(summary = "Check if a group workspace is in conflict resolution mode")
    public boolean isGroupWorkspaceInConflictResolutionMode(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId)
    {
        return executeWithLogging(
                "checking if group workspace " + workspaceId + " of project " + projectId + " is in conflict resolution mode",
                this.workspaceApi::isGroupWorkspaceInConflictResolutionMode,
                projectId,
                workspaceId
        );
    }

    @POST
    @Path("{workspaceId}")
    @Operation(summary = "Create a new group workspace")
    public Workspace createGroupWorkspace(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId)
    {
        return execute(
                "creating new group workspace " + workspaceId + " for project " + projectId,
                "create new group workspace",
                this.workspaceApi::newGroupWorkspace,
                projectId,
                workspaceId
        );
    }

    @DELETE
    @Path("{workspaceId}")
    @Operation(summary = "Delete a group workspace")
    public void deleteGroupWorkspace(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId)
    {
        executeWithLogging(
                "deleting group workspace " + workspaceId + " for project " + projectId,
                this.workspaceApi::deleteGroupWorkspace,
                projectId,
                workspaceId
        );
    }

    @POST
    @Path("{workspaceId}/update")
    @Operation(summary = "Update a group workspace")
    public WorkspaceApi.WorkspaceUpdateReport updateGroupWorkspace(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId)
    {
        return executeWithLogging(
                "updating group workspace " + workspaceId + " in project " + projectId + " to latest revision",
                () -> this.workspaceApi.updateGroupWorkspace(projectId, workspaceId)
        );
    }
}