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

package org.finos.legend.sdlc.server.resources.backup.patch.group;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.domain.api.backup.BackupApi;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceApi;
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification;
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
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/projects/{projectId}/patches/{patchReleaseVersionId}/groupWorkspaces/{workspaceId}/backup")
@Api("Backup")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class BackupPatchesGroupWorkspaceResource extends BaseResource
{
    private final BackupApi backupApi;
    private final WorkspaceApi workspaceApi;

    @Inject
    public BackupPatchesGroupWorkspaceResource(BackupApi backupApi, WorkspaceApi workspaceApi)
    {
        this.backupApi = backupApi;
        this.workspaceApi = workspaceApi;
    }

    @GET
    @ApiOperation("Get a backup group workspace by id for patch release version")
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
                "getting backup group workspace " + workspaceId + " for project " + projectId + " for patch release version " + patchReleaseVersionId,
                () -> this.workspaceApi.getBackupWorkspace(projectId, SourceSpecification.newGroupWorkspaceSourceSpecification(workspaceId, versionId))
        );
    }

    @GET
    @Path("outdated")
    @ApiOperation("Check if a backup group workspace is outdated for patch release version")
    public boolean isWorkspaceOutdated(@PathParam("projectId") String projectId, @PathParam("patchReleaseVersionId") String patchReleaseVersionId, @PathParam("workspaceId") String workspaceId)
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
                "checking if backup group workspace " + workspaceId + " of project " + projectId + " for patch release version " + patchReleaseVersionId + " is outdated",
                () -> this.workspaceApi.isBackupWorkspaceOutdated(projectId, SourceSpecification.newGroupWorkspaceSourceSpecification(workspaceId, versionId))
        );
    }

    @DELETE
    @ApiOperation("Discard a backup group workspace for patch release version")
    public void discardBackup(@PathParam("projectId") String projectId, @PathParam("patchReleaseVersionId") String patchReleaseVersionId, @PathParam("workspaceId") String workspaceId)
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
                "discarding backup group workspace " + workspaceId + " in project " + projectId + " for patch release version " + patchReleaseVersionId,
                () -> this.backupApi.discardBackupWorkspace(projectId, SourceSpecification.newGroupWorkspaceSourceSpecification(workspaceId, versionId))
        );
    }

    @POST
    @Path("recover")
    @ApiOperation("Recover the group workspace from backup for patch release version")
    public void recoverBackup(@PathParam("projectId") String projectId,
                              @PathParam("patchReleaseVersionId") String patchReleaseVersionId,
                              @PathParam("workspaceId") String workspaceId,
                              @QueryParam("forceRecovery") @ApiParam("Whether to override the workspace if it exists with the backup") boolean forceRecovery)
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
                forceRecovery ? "force " : "" + "recovering group workspace " + workspaceId + " from backup in project " + projectId + " for patch release version " + patchReleaseVersionId,
                () -> this.backupApi.recoverBackupWorkspace(projectId, SourceSpecification.newGroupWorkspaceSourceSpecification(workspaceId, versionId), forceRecovery)
        );
    }
}