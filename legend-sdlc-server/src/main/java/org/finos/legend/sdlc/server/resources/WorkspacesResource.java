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

package org.finos.legend.sdlc.server.resources;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceApi;

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
import java.util.List;

@Path("/projects/{projectId}/workspaces")
@Api("Workspaces")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class WorkspacesResource extends BaseResource
{
    private final WorkspaceApi workspaceApi;

    @Inject
    public WorkspacesResource(WorkspaceApi workspaceApi)
    {
        this.workspaceApi = workspaceApi;
    }

    @GET
    @ApiOperation("Get all workspaces for a project")
    public List<Workspace> getUserWorkspaces(@PathParam("projectId") String projectId,
                                             @QueryParam("owned")
                                             @DefaultValue("true")
                                             @ApiParam("Only include workspaces owned by current user") boolean ownedOnly)
    {
        return executeWithLogging(
                "getting " + (ownedOnly ? "user" : "all") + " workspaces for project " + projectId,
                ownedOnly ? this.workspaceApi::getWorkspaces : this.workspaceApi::getAllWorkspaces,
                projectId
        );
    }

    @GET
    @Path("{workspaceId}")
    @ApiOperation("Get a user workspace for a project by id")
    public Workspace getUserWorkspace(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId)
    {
        return executeWithLogging(
                "getting workspace " + workspaceId + " for project " + projectId,
                this.workspaceApi::getWorkspace,
                projectId,
                workspaceId
        );
    }

    @GET
    @Path("{workspaceId}/outdated")
    @ApiOperation("Check if a user workspace is outdated")
    public boolean isWorkspaceOutdated(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId)
    {
        return executeWithLogging(
                "checking if workspace " + workspaceId + " of project " + projectId + " is outdated",
                this.workspaceApi::isWorkspaceOutdated,
                projectId,
                workspaceId
        );
    }

    @GET
    @Path("{workspaceId}/inConflictResolutionMode")
    @ApiOperation("Check if a user workspace is in conflict resolution mode")
    public boolean isWorkspaceInConflictResolutionMode(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId)
    {
        return executeWithLogging(
                "checking if workspace " + workspaceId + " of project " + projectId + " is in conflict resolution mode",
                this.workspaceApi::isWorkspaceInConflictResolutionMode,
                projectId,
                workspaceId
        );
    }

    @POST
    @Path("{workspaceId}")
    @ApiOperation("Create a new user workspace")
    public Workspace createUserWorkspace(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId)
    {
        return execute(
                "creating new user workspace " + workspaceId + " for project " + projectId,
                "create new user workspace",
                this.workspaceApi::newUserWorkspace,
                projectId,
                workspaceId
        );
    }

    @DELETE
    @Path("{workspaceId}")
    @ApiOperation("Delete a workspace")
    public void deleteWorkspace(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId)
    {
        executeWithLogging(
                "deleting workspace " + workspaceId + " for project " + projectId,
                this.workspaceApi::deleteWorkspace,
                projectId,
                workspaceId
        );
    }

    @POST
    @Path("{workspaceId}/update")
    @ApiOperation("Update a workspace")
    public WorkspaceApi.WorkspaceUpdateReport updateWorkspace(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId)
    {
        return executeWithLogging(
                "updating workspace " + workspaceId + " in project " + projectId + " to latest revision",
                () -> this.workspaceApi.updateWorkspace(projectId, workspaceId)
        );
    }
}
