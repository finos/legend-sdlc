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

package org.finos.legend.sdlc.server.resources.workspace.project.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.backend.api.workspace.WorkspaceApi;
import org.finos.legend.sdlc.server.resources.BaseResource;

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
import java.util.List;

@Path("/projects/{projectId}/workspaces")
@Tag(name = "Workspaces")
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
    @Operation(summary = "Get all user workspaces for a project")
    public List<Workspace> getUserWorkspaces(@PathParam("projectId") String projectId,
                                             @QueryParam("owned")
                                             @DefaultValue("true")
                                             @Parameter(description = "Only include workspaces owned by current user") boolean ownedOnly)
    {
        return executeWithLogging(
                "getting " + (ownedOnly ? "user" : "all") + " workspaces for project " + projectId,
                ownedOnly ? this.workspaceApi::getUserWorkspaces : this.workspaceApi::getAllUserWorkspaces,
                projectId
        );
    }

    @GET
    @Path("{workspaceId}")
    @Operation(summary = "Get a user workspace for a project by id")
    public Workspace getUserWorkspace(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId)
    {
        return executeWithLogging(
                "getting user workspace " + workspaceId + " for project " + projectId,
                this.workspaceApi::getUserWorkspace,
                projectId,
                workspaceId
        );
    }

    @GET
    @Path("{workspaceId}/outdated")
    @Operation(summary = "Check if a user workspace is outdated")
    public boolean isUserWorkspaceOutdated(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId)
    {
        return executeWithLogging(
                "checking if user workspace " + workspaceId + " of project " + projectId + " is outdated",
                this.workspaceApi::isUserWorkspaceOutdated,
                projectId,
                workspaceId
        );
    }

    @GET
    @Path("{workspaceId}/inConflictResolutionMode")
    @Operation(summary = "Check if a user workspace is in conflict resolution mode")
    public boolean isUserWorkspaceInConflictResolutionMode(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId)
    {
        return executeWithLogging(
                "checking if user workspace " + workspaceId + " of project " + projectId + " is in conflict resolution mode",
                this.workspaceApi::isUserWorkspaceInConflictResolutionMode,
                projectId,
                workspaceId
        );
    }

    @POST
    @Path("{workspaceId}")
    @Operation(summary = "Create a new user workspace")
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
    @Operation(summary = "Delete a user workspace")
    public void deleteUserWorkspace(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId)
    {
        executeWithLogging(
                "deleting user workspace " + workspaceId + " for project " + projectId,
                this.workspaceApi::deleteUserWorkspace,
                projectId,
                workspaceId
        );
    }

    @POST
    @Path("{workspaceId}/update")
    @Operation(summary = "Update a user workspace")
    public WorkspaceApi.WorkspaceUpdateReport updateUserWorkspace(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId)
    {
        return executeWithLogging(
                "updating user workspace " + workspaceId + " in project " + projectId + " to latest revision",
                () -> this.workspaceApi.updateUserWorkspace(projectId, workspaceId)
        );
    }
}
