// Copyright 2023 Goldman Sachs
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

package org.finos.legend.sdlc.server.resources.workspace.patch.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.backend.api.workspace.WorkspaceApi;
import org.finos.legend.sdlc.project.workspace.WorkspaceSource;
import org.finos.legend.sdlc.project.workspace.WorkspaceSpecification;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
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
import jakarta.ws.rs.core.Response;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

@Path("/projects/{projectId}/patches/{patchReleaseVersionId}/workspaces")
@Tag(name = "Workspaces")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PatchesWorkspacesResource extends BaseResource
{
    private final WorkspaceApi workspaceApi;

    @Inject
    public PatchesWorkspacesResource(WorkspaceApi workspaceApi)
    {
        this.workspaceApi = workspaceApi;
    }

    @GET
    @Operation(summary = "Get all user workspaces for a project for patch release version")
    public List<Workspace> getUserWorkspaces(@PathParam("projectId") String projectId,
                                             @PathParam("patchReleaseVersionId") String patchReleaseVersionId,
                                             @QueryParam("owned")
                                             @DefaultValue("true")
                                             @Parameter(description = "Only include workspaces owned by current user") boolean ownedOnly)
    {
        LegendSDLCServerException.validateNonNull(patchReleaseVersionId, "patchReleaseVersionId may not be null");
        VersionId versionId;
        try
        {
            versionId = VersionId.parseVersionId(patchReleaseVersionId);
        }
        catch (IllegalArgumentException e)
        {
            throw new LegendSDLCServerException(e.getMessage(), Response.Status.BAD_REQUEST, e);
        }
        return executeWithLogging(
                "getting " + (ownedOnly ? "user" : "all") + " workspaces for patch release version " + patchReleaseVersionId + " for project " + projectId,
                () -> ownedOnly ? this.workspaceApi.getWorkspaces(projectId, versionId, Collections.singleton(WorkspaceType.USER)) : this.workspaceApi.getAllWorkspaces(projectId, versionId, EnumSet.allOf(WorkspaceType.class))
        );
    }

    @GET
    @Path("{workspaceId}")
    @Operation(summary = "Get a user workspace for a project by id for patch release version")
    public Workspace getUserWorkspace(@PathParam("projectId") String projectId, @PathParam("patchReleaseVersionId") String patchReleaseVersionId, @PathParam("workspaceId") String workspaceId)
    {
        LegendSDLCServerException.validateNonNull(patchReleaseVersionId, "patchReleaseVersionId may not be null");
        VersionId versionId;
        try
        {
            versionId = VersionId.parseVersionId(patchReleaseVersionId);
        }
        catch (IllegalArgumentException e)
        {
            throw new LegendSDLCServerException(e.getMessage(), Response.Status.BAD_REQUEST, e);
        }
        return executeWithLogging(
                "getting user workspace " + workspaceId + " for patch release " + patchReleaseVersionId +  " for project " + projectId,
                () -> this.workspaceApi.getWorkspace(projectId, WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.USER, WorkspaceSource.patchWorkspaceSource(versionId)))
        );
    }

    @GET
    @Path("{workspaceId}/outdated")
    @Operation(summary = "Check if a user workspace is outdated for patch release version")
    public boolean isUserWorkspaceOutdated(@PathParam("projectId") String projectId, @PathParam("patchReleaseVersionId") String patchReleaseVersionId, @PathParam("workspaceId") String workspaceId)
    {
        LegendSDLCServerException.validateNonNull(patchReleaseVersionId, "patchReleaseVersionId may not be null");
        VersionId versionId;
        try
        {
            versionId = VersionId.parseVersionId(patchReleaseVersionId);
        }
        catch (IllegalArgumentException e)
        {
            throw new LegendSDLCServerException(e.getMessage(), Response.Status.BAD_REQUEST, e);
        }
        return executeWithLogging(
                "checking if user workspace " + workspaceId + " for patch release " + patchReleaseVersionId + " of project " + projectId + " is outdated",
                () -> this.workspaceApi.isWorkspaceOutdated(projectId, WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.USER, WorkspaceSource.patchWorkspaceSource(versionId)))
        );
    }

    @GET
    @Path("{workspaceId}/inConflictResolutionMode")
    @Operation(summary = "Check if a user workspace is in conflict resolution mode for patch release version")
    public boolean isUserWorkspaceInConflictResolutionMode(@PathParam("projectId") String projectId, @PathParam("patchReleaseVersionId") String patchReleaseVersionId, @PathParam("workspaceId") String workspaceId)
    {
        LegendSDLCServerException.validateNonNull(patchReleaseVersionId, "patchReleaseVersionId may not be null");
        VersionId versionId;
        try
        {
            versionId = VersionId.parseVersionId(patchReleaseVersionId);
        }
        catch (IllegalArgumentException e)
        {
            throw new LegendSDLCServerException(e.getMessage(), Response.Status.BAD_REQUEST, e);
        }
        return executeWithLogging(
                "checking if user workspace " + workspaceId + " for patch release " + patchReleaseVersionId + " of project " + projectId + " is in conflict resolution mode",
                () -> this.workspaceApi.isWorkspaceInConflictResolutionMode(projectId, WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.USER, WorkspaceSource.patchWorkspaceSource(versionId)))
        );
    }

    @POST
    @Path("{workspaceId}")
    @Operation(summary = "Create a new user workspace for patch release version")
    public Workspace createUserWorkspace(@PathParam("projectId") String projectId, @PathParam("patchReleaseVersionId") String patchReleaseVersionId, @PathParam("workspaceId") String workspaceId)
    {
        LegendSDLCServerException.validateNonNull(patchReleaseVersionId, "patchReleaseVersionId may not be null");
        VersionId versionId;
        try
        {
            versionId = VersionId.parseVersionId(patchReleaseVersionId);
        }
        catch (IllegalArgumentException e)
        {
            throw new LegendSDLCServerException(e.getMessage(), Response.Status.BAD_REQUEST, e);
        }
        return executeWithLogging(
                "creating new user workspace " + workspaceId + " for patch release " + patchReleaseVersionId + " for project " + projectId,
                () -> this.workspaceApi.newWorkspace(projectId, workspaceId, WorkspaceType.USER, WorkspaceSource.patchWorkspaceSource(versionId))

        );
    }

    @DELETE
    @Path("{workspaceId}")
    @Operation(summary = "Delete a user workspace for patch release version")
    public void deleteUserWorkspace(@PathParam("projectId") String projectId, @PathParam("patchReleaseVersionId") String patchReleaseVersionId, @PathParam("workspaceId") String workspaceId)
    {
        LegendSDLCServerException.validateNonNull(patchReleaseVersionId, "patchReleaseVersionId may not be null");
        VersionId versionId;
        try
        {
            versionId = VersionId.parseVersionId(patchReleaseVersionId);
        }
        catch (IllegalArgumentException e)
        {
            throw new LegendSDLCServerException(e.getMessage(), Response.Status.BAD_REQUEST, e);
        }
        executeWithLogging(
                "deleting user workspace " + workspaceId + " for patch release " + patchReleaseVersionId + " for project " + projectId,
                () -> this.workspaceApi.deleteWorkspace(projectId, WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.USER, WorkspaceSource.patchWorkspaceSource(versionId)))
        );
    }

    @POST
    @Path("{workspaceId}/update")
    @Operation(summary = "Update a user workspace for patch release version")
    public WorkspaceApi.WorkspaceUpdateReport updateUserWorkspace(@PathParam("projectId") String projectId, @PathParam("patchReleaseVersionId") String patchReleaseVersionId, @PathParam("workspaceId") String workspaceId)
    {
        LegendSDLCServerException.validateNonNull(patchReleaseVersionId, "patchReleaseVersionId may not be null");
        VersionId versionId;
        try
        {
            versionId = VersionId.parseVersionId(patchReleaseVersionId);
        }
        catch (IllegalArgumentException e)
        {
            throw new LegendSDLCServerException(e.getMessage(), Response.Status.BAD_REQUEST, e);
        }
        return executeWithLogging(
                "updating user workspace " + workspaceId + " in project " + projectId + " to latest revision",
                () -> this.workspaceApi.updateWorkspace(projectId, WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.USER, WorkspaceSource.patchWorkspaceSource(versionId)))
        );
    }
}
