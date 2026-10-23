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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.backend.api.backup.BackupApi;
import org.finos.legend.sdlc.backend.api.workspace.WorkspaceApi;
import org.finos.legend.sdlc.project.workspace.WorkspaceSource;
import org.finos.legend.sdlc.project.workspace.WorkspaceSpecification;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider.WorkspaceAccessType;
import org.finos.legend.sdlc.server.resources.BaseResource;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/projects/{projectId}/patches/{patchReleaseVersionId}/groupWorkspaces/{workspaceId}/backup")
@Tag(name = "Backup")
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
    @Operation(summary = "Get a backup group workspace by id for patch release version")
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
                () -> this.workspaceApi.getWorkspace(projectId, WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.GROUP, WorkspaceAccessType.BACKUP, WorkspaceSource.patchWorkspaceSource(versionId)))
        );
    }

    @GET
    @Path("outdated")
    @Operation(summary = "Check if a backup group workspace is outdated for patch release version")
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
                () -> this.workspaceApi.isWorkspaceOutdated(projectId, WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.GROUP, WorkspaceAccessType.BACKUP, WorkspaceSource.patchWorkspaceSource(versionId)))
        );
    }

    @DELETE
    @Operation(summary = "Discard a backup group workspace for patch release version")
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
                () -> this.backupApi.discardBackupWorkspace(projectId, WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.GROUP, WorkspaceAccessType.BACKUP, WorkspaceSource.patchWorkspaceSource(versionId)))
        );
    }

    @POST
    @Path("recover")
    @Operation(summary = "Recover the group workspace from backup for patch release version")
    public void recoverBackup(@PathParam("projectId") String projectId,
                              @PathParam("patchReleaseVersionId") String patchReleaseVersionId,
                              @PathParam("workspaceId") String workspaceId,
                              @QueryParam("forceRecovery") @Parameter(description = "Whether to override the workspace if it exists with the backup") boolean forceRecovery)
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
                forceRecovery ? "force " : "recovering group workspace " + workspaceId + " from backup in project " + projectId + " for patch release version " + patchReleaseVersionId,
                () -> this.backupApi.recoverBackupWorkspace(projectId, WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.GROUP, WorkspaceAccessType.BACKUP, WorkspaceSource.patchWorkspaceSource(versionId)), forceRecovery)
        );
    }
}
