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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceApi;
import org.finos.legend.sdlc.server.resources.BaseResource;

import java.util.List;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

@Path("/projects/{projectId}/groupWorkspaces")
@Api("Workspaces")
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
    @ApiOperation("Get all group workspaces for a project")
    public List<Workspace> getGroupWorkspaces(@PathParam("projectId") String projectId,
                                              @QueryParam("includeUserWorkspaces")
                                              @DefaultValue("false")
                                              @ApiParam("include user workspaces owned by current user") boolean includeUserWorkspaces)
    {
        return executeWithLogging(
                "getting all group" + (includeUserWorkspaces ? " and user" : "") + " workspaces for project " + projectId,
                () -> includeUserWorkspaces ? this.workspaceApi.getAllWorkspaces(projectId) : this.workspaceApi.getGroupWorkspaces(projectId)
        );
    }

    @GET
    @Path("{workspaceId}")
    @ApiOperation("Get a group workspace for a project by id")
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
    @ApiOperation("Check if a group workspace is outdated")
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
    @ApiOperation("Check if a group workspace is in conflict resolution mode")
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
    @ApiOperation("Create a new group workspace")
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
    @ApiOperation("Delete a group workspace")
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
    @ApiOperation("Update a group workspace")
    public WorkspaceApi.WorkspaceUpdateReport updateGroupWorkspace(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId)
    {
        return executeWithLogging(
                "updating group workspace " + workspaceId + " in project " + projectId + " to latest revision",
                () -> this.workspaceApi.updateGroupWorkspace(projectId, workspaceId)
        );
    }
}