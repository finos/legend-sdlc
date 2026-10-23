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

package org.finos.legend.sdlc.server.resources.backup.project.group;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.backend.api.backup.BackupApi;
import org.finos.legend.sdlc.backend.api.workspace.WorkspaceApi;
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

@Path("/projects/{projectId}/groupWorkspaces/{workspaceId}/backup")
@Tag(name = "Backup")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class GroupBackupWorkspaceResource extends BaseResource
{
    private final BackupApi backupApi;
    private final WorkspaceApi workspaceApi;

    @Inject
    public GroupBackupWorkspaceResource(BackupApi backupApi, WorkspaceApi workspaceApi)
    {
        this.backupApi = backupApi;
        this.workspaceApi = workspaceApi;
    }

    @GET
    @Operation(summary = "Get a backup group workspace by id")
    public Workspace getGroupWorkspace(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId)
    {
        return executeWithLogging(
                "getting backup group workspace " + workspaceId + " for project " + projectId,
                this.workspaceApi::getBackupGroupWorkspace,
                projectId,
                workspaceId
        );
    }

    @GET
    @Path("outdated")
    @Operation(summary = "Check if a backup group workspace is outdated")
    public boolean isWorkspaceOutdated(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId)
    {
        return executeWithLogging(
                "checking if backup group workspace " + workspaceId + " of project " + projectId + " is outdated",
                this.workspaceApi::isBackupGroupWorkspaceOutdated,
                projectId,
                workspaceId
        );
    }

    @DELETE
    @Operation(summary = "Discard a backup group workspace")
    public void discardBackup(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId)
    {
        executeWithLogging(
                "discarding backup group workspace " + workspaceId + " in project " + projectId,
                this.backupApi::discardBackupGroupWorkspace,
                projectId,
                workspaceId
        );
    }

    @POST
    @Path("recover")
    @Operation(summary = "Recover the group workspace from backup")
    public void recoverBackup(@PathParam("projectId") String projectId,
                              @PathParam("workspaceId") String workspaceId,
                              @QueryParam("forceRecovery") @Parameter(description = "Whether to override the workspace if it exists with the backup") boolean forceRecovery)
    {
        executeWithLogging(
                forceRecovery ? "force " : "" + "recovering group workspace " + workspaceId + " from backup in project " + projectId,
                () -> this.backupApi.recoverBackupGroupWorkspace(projectId, workspaceId, forceRecovery)
        );
    }
}