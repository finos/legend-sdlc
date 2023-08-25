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

package org.finos.legend.sdlc.server.resources.backup.project.user;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.server.domain.api.backup.BackupApi;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceApi;
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

@Path("/projects/{projectId}/workspaces/{workspaceId}/backup")
@Api("Backup")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class BackupWorkspaceResource extends BaseResource
{
    private final BackupApi backupApi;
    private final WorkspaceApi workspaceApi;

    @Inject
    public BackupWorkspaceResource(BackupApi backupApi, WorkspaceApi workspaceApi)
    {
        this.backupApi = backupApi;
        this.workspaceApi = workspaceApi;
    }

    @GET
    @ApiOperation("Get a backup user workspace by id")
    public Workspace getUserWorkspace(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId)
    {
        return executeWithLogging(
                "getting backup user workspace " + workspaceId + " for project " + projectId,
                this.workspaceApi::getBackupUserWorkspace,
                projectId,
                workspaceId
        );
    }

    @GET
    @Path("outdated")
    @ApiOperation("Check if a backup user workspace is outdated")
    public boolean isWorkspaceOutdated(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId)
    {
        return executeWithLogging(
                "checking if backup user workspace " + workspaceId + " of project " + projectId + " is outdated",
                this.workspaceApi::isBackupUserWorkspaceOutdated,
                projectId,
                workspaceId
        );
    }

    @DELETE
    @ApiOperation("Discard a backup user workspace")
    public void discardBackup(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId)
    {
        executeWithLogging(
                "discarding backup user workspace " + workspaceId + " in project " + projectId,
                this.backupApi::discardBackupUserWorkspace,
                projectId,
                workspaceId
        );
    }

    @POST
    @Path("recover")
    @ApiOperation("Recover the user workspace from backup")
    public void recoverBackup(@PathParam("projectId") String projectId,
                              @PathParam("workspaceId") String workspaceId,
                              @QueryParam("forceRecovery") @ApiParam("Whether to override the workspace if it exists with the backup") boolean forceRecovery)
    {
        executeWithLogging(
                forceRecovery ? "force " : "" + "recovering user workspace " + workspaceId + " from backup in project " + projectId,
                () -> this.backupApi.recoverBackupUserWorkspace(projectId, workspaceId, forceRecovery)
        );
    }
}
