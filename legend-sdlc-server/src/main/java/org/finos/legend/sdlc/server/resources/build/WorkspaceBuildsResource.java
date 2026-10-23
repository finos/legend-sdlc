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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.finos.legend.sdlc.domain.model.build.Build;
import org.finos.legend.sdlc.domain.model.build.BuildStatus;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.backend.api.build.BuildApi;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.resources.BaseResource;
import org.finos.legend.sdlc.server.resources.workflow.project.user.WorkspaceWorkflowsResource;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Set;

@Path("/projects/{projectId}/workspaces/{workspaceId}/builds")
@Tag(name = "Builds")
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
    @Operation(summary = "Get builds for a workspace", description = "DEPRECATED: use corresponding Workflows API instead. Get builds for a workspace. If status is provided, then only builds with the given status are returned. Otherwise, all builds are returned. If status is UNKNOWN, results are undefined.")
    @Deprecated
    public List<Build> getBuilds(@PathParam("projectId") String projectId,
                                 @PathParam("workspaceId") String workspaceId,
                                 @QueryParam("revisionId")
                                 @Parameter(description = "Only include builds for one of the given revisions") Set<String> revisionIds,
                                 @QueryParam("status")
                                 @Parameter(description = "Only include builds with one of the given statuses") Set<BuildStatus> statuses,
                                 @QueryParam("limit")
                                 @Parameter(description = "Limit the number of builds returned") Integer limit)
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
    @Operation(summary = "Get a build for a workspace", description = "DEPRECATED: use corresponding Workflows API instead")
    @Deprecated
    public Build getBuild(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId, @PathParam("buildId") String buildId)
    {
        return executeWithLogging(
                "getting build " + buildId + " for workspace " + workspaceId + " in project " + projectId,
                () -> this.buildApi.getWorkspaceBuildAccessContext(projectId, workspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE).getBuild(buildId)
        );
    }
}
