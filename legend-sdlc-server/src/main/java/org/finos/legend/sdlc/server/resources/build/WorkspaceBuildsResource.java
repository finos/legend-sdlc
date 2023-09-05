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

package org.finos.legend.sdlc.server.resources.build;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.finos.legend.sdlc.domain.model.build.Build;
import org.finos.legend.sdlc.domain.model.build.BuildStatus;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.server.domain.api.build.BuildApi;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.resources.BaseResource;
import org.finos.legend.sdlc.server.resources.workflow.project.user.WorkspaceWorkflowsResource;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Set;

@Path("/projects/{projectId}/workspaces/{workspaceId}/builds")
@Api("Builds")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class WorkspaceBuildsResource extends BaseResource
{
    private final BuildApi buildApi;

    @Inject
    public WorkspaceBuildsResource(BuildApi buildApi)
    {
        this.buildApi = buildApi;
    }

    /**
     * @deprecated Use {@link WorkspaceWorkflowsResource#getWorkflows} instead
     */
    @GET
    @ApiOperation(value = "Get builds for a workspace", notes = "DEPRECATED: use corresponding Workflows API instead. Get builds for a workspace. If status is provided, then only builds with the given status are returned. Otherwise, all builds are returned. If status is UNKNOWN, results are undefined.")
    @Deprecated
    public List<Build> getBuilds(@PathParam("projectId") String projectId,
                                 @PathParam("workspaceId") String workspaceId,
                                 @QueryParam("revisionId")
                                 @ApiParam("Only include builds for one of the given revisions") Set<String> revisionIds,
                                 @QueryParam("status")
                                 @ApiParam("Only include builds with one of the given statuses") Set<BuildStatus> statuses,
                                 @QueryParam("limit")
                                 @ApiParam("Limit the number of builds returned") Integer limit)
    {
        return executeWithLogging(
                "getting builds for workspace " + workspaceId + " in project " + projectId,
                () -> this.buildApi.getWorkspaceBuildAccessContext(projectId, workspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE).getBuilds(revisionIds, statuses, limit)
        );
    }

    /**
     * @deprecated Use {@link WorkspaceWorkflowsResource#getWorkflow} instead
     */
    @GET
    @Path("{buildId}")
    @ApiOperation(value = "Get a build for a workspace", notes = "DEPRECATED: use corresponding Workflows API instead")
    @Deprecated
    public Build getBuild(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId, @PathParam("buildId") String buildId)
    {
        return executeWithLogging(
                "getting build " + buildId + " for workspace " + workspaceId + " in project " + projectId,
                () -> this.buildApi.getWorkspaceBuildAccessContext(projectId, workspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE).getBuild(buildId)
        );
    }
}
