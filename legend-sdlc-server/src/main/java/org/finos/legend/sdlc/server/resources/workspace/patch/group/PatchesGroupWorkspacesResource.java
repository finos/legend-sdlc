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

package org.finos.legend.sdlc.server.resources.workspace.patch.group;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.backend.api.workspace.WorkspaceApi;
import org.finos.legend.sdlc.project.source.SourceSpecification;
import org.finos.legend.sdlc.project.workspace.WorkspaceSource;
import org.finos.legend.sdlc.project.workspace.WorkspaceSpecification;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.resources.BaseResource;

import java.util.Collections;
import java.util.EnumSet;
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
import jakarta.ws.rs.core.Response;

@Path("/projects/{projectId}/patches/{patchReleaseVersionId}/groupWorkspaces")
@Tag(name = "Workspaces")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PatchesGroupWorkspacesResource extends BaseResource
{
    private final WorkspaceApi workspaceApi;

    @Inject
    public PatchesGroupWorkspacesResource(WorkspaceApi workspaceApi)
    {
        this.workspaceApi = workspaceApi;
    }

    @GET
    @Operation(summary = "Get all group workspaces for a project for patch release version")
    public List<Workspace> getGroupWorkspaces(@PathParam("projectId") String projectId,
                                              @PathParam("patchReleaseVersionId") String patchReleaseVersionId,
                                              @QueryParam("includeUserWorkspaces")
                                              @DefaultValue("false")
                                              @Parameter(description = "include user workspaces owned by current user") boolean includeUserWorkspaces)
    {
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
                "getting all group" + (includeUserWorkspaces ? " and user" : "") + " workspaces for project " + projectId + " for patch release version " + patchReleaseVersionId,
                () -> includeUserWorkspaces ? this.workspaceApi.getAllWorkspaces(projectId, versionId, EnumSet.allOf(WorkspaceType.class)) : this.workspaceApi.getWorkspaces(projectId, versionId, Collections.singleton(WorkspaceType.GROUP))
        );
    }

    @GET
    @Path("{workspaceId}")
    @Operation(summary = "Get a group workspace for a project by id for patch release version")
    public Workspace getGroupWorkspace(@PathParam("projectId") String projectId, @PathParam("patchReleaseVersionId") String patchReleaseVersionId, @PathParam("workspaceId") String workspaceId)
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
                "getting group workspace " + workspaceId + " for patch release " + patchReleaseVersionId +  " for project " + projectId + " for patch release version " + patchReleaseVersionId,
                () -> this.workspaceApi.getWorkspace(projectId, SourceSpecification.workspaceSourceSpecification(WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.GROUP, WorkspaceSource.patchWorkspaceSource(versionId))))
        );
    }

    @GET
    @Path("{workspaceId}/outdated")
    @Operation(summary = "Check if a group workspace is outdated for patch release version")
    public boolean isGroupWorkspaceOutdated(@PathParam("projectId") String projectId, @PathParam("patchReleaseVersionId") String patchReleaseVersionId, @PathParam("workspaceId") String workspaceId)
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
                "checking if group workspace " + workspaceId + " for patch release " + patchReleaseVersionId + " of project " + projectId + " for patch release version " + patchReleaseVersionId + " is outdated",
                () -> this.workspaceApi.isWorkspaceOutdated(projectId, SourceSpecification.workspaceSourceSpecification(WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.GROUP, WorkspaceSource.patchWorkspaceSource(versionId))))
        );
    }

    @GET
    @Path("{workspaceId}/inConflictResolutionMode")
    @Operation(summary = "Check if a group workspace is in conflict resolution mode for patch release version")
    public boolean isGroupWorkspaceInConflictResolutionMode(@PathParam("projectId") String projectId, @PathParam("patchReleaseVersionId") String patchReleaseVersionId, @PathParam("workspaceId") String workspaceId)
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
                "checking if group workspace " + workspaceId + " for patch release " + patchReleaseVersionId + " of project " + projectId + " for patch release version " + patchReleaseVersionId + " is in conflict resolution mode",
                () -> this.workspaceApi.isWorkspaceInConflictResolutionMode(projectId, SourceSpecification.workspaceSourceSpecification(WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.GROUP, WorkspaceSource.patchWorkspaceSource(versionId))))
        );
    }

    @POST
    @Path("{workspaceId}")
    @Operation(summary = "Create a new group workspace for patch release version")
    public Workspace createGroupWorkspace(@PathParam("projectId") String projectId, @PathParam("patchReleaseVersionId") String patchReleaseVersionId, @PathParam("workspaceId") String workspaceId)
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
                "creating new group workspace " + workspaceId + " for patch release " + patchReleaseVersionId + " for project " + projectId,
                () -> this.workspaceApi.newWorkspace(projectId, SourceSpecification.workspaceSourceSpecification(WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.GROUP, WorkspaceSource.patchWorkspaceSource(versionId))))

        );
    }

    @DELETE
    @Path("{workspaceId}")
    @Operation(summary = "Delete a group workspace for patch release version")
    public void deleteGroupWorkspace(@PathParam("projectId") String projectId, @PathParam("patchReleaseVersionId") String patchReleaseVersionId, @PathParam("workspaceId") String workspaceId)
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
                "deleting group workspace " + workspaceId + " for patch release " + patchReleaseVersionId + " for project " + projectId,
                () -> this.workspaceApi.deleteWorkspace(projectId, SourceSpecification.workspaceSourceSpecification(WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.GROUP, WorkspaceSource.patchWorkspaceSource(versionId))))
        );
    }

    @POST
    @Path("{workspaceId}/update")
    @Operation(summary = "Update a group workspace for patch release version")
    public WorkspaceApi.WorkspaceUpdateReport updateGroupWorkspace(@PathParam("projectId") String projectId, @PathParam("patchReleaseVersionId") String patchReleaseVersionId, @PathParam("workspaceId") String workspaceId)
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
                "updating user workspace " + workspaceId + " in project " + projectId + " for patch release version " + patchReleaseVersionId + " to latest revision",
                () -> this.workspaceApi.updateWorkspace(projectId, SourceSpecification.workspaceSourceSpecification(WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.GROUP, WorkspaceSource.patchWorkspaceSource(versionId))))
        );
    }
}