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

package org.finos.legend.sdlc.server.resources.workflow.project.group;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.workflow.WorkflowJob;
import org.finos.legend.sdlc.domain.model.workflow.WorkflowJobStatus;
import org.finos.legend.sdlc.backend.api.workflow.WorkflowJobApi;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.resources.BaseResource;

import java.util.List;
import java.util.Set;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/projects/{projectId}/groupWorkspaces/{workspaceId}/workflows/{workflowId}/jobs")
@Tag(name = "Workflows")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class GroupWorkspaceWorkflowJobsResource extends BaseResource
{
    private final WorkflowJobApi workflowJobApi;

    @Inject
    public GroupWorkspaceWorkflowJobsResource(WorkflowJobApi workflowJobApi)
    {
        this.workflowJobApi = workflowJobApi;
    }

    @GET
    @Operation(summary = "Get jobs for a workflow for a group workspace", description = "Get jobs for a workflow. If status is provided, then only workflow jobs with the given status are returned. Otherwise, all workflows are returned. If status is UNKNOWN, results are undefined.")
    public List<WorkflowJob> getWorkflowJobs(@PathParam("projectId") String projectId,
                                             @PathParam("workspaceId") String workspaceId,
                                             @PathParam("workflowId") String workflowId,
                                             @QueryParam("status") @Parameter(description = "Only include workflow jobs with one of the given statuses") Set<WorkflowJobStatus> statuses)
    {
        return executeWithLogging(
                "getting workflow jobs for group workspace " + workspaceId + " in project " + projectId,
                () -> this.workflowJobApi.getWorkspaceWorkflowJobAccessContext(projectId, workspaceId, WorkspaceType.GROUP, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE).getWorkflowJobs(workflowId, statuses)
        );
    }

    @GET
    @Path("{workflowJobId}")
    @Operation(summary = "Get a workflow job in a group workspace")
    public WorkflowJob getWorkflowJob(@PathParam("projectId") String projectId,
                                      @PathParam("workspaceId") String workspaceId,
                                      @PathParam("workflowId") String workflowId,
                                      @PathParam("workflowJobId") String workflowJobId)
    {
        return executeWithLogging(
                "getting workflow job " + workflowJobId + " for group workspace " + workspaceId + " in project " + projectId,
                () -> this.workflowJobApi.getWorkspaceWorkflowJobAccessContext(projectId, workspaceId, WorkspaceType.GROUP, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE).getWorkflowJob(workflowId, workflowJobId)
        );
    }

    @GET
    @Path("{workflowJobId}/logs")
    @Operation(summary = "Get a workflow job log")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getWorkflowJobLogs(@PathParam("projectId") String projectId,
                                       @PathParam("workspaceId") String workspaceId,
                                       @PathParam("workflowId") String workflowId,
                                       @PathParam("workflowJobId") String workflowJobId)
    {
        return executeWithLogging(
                "getting workflow job logs " + workflowJobId + " for group workspace " + workspaceId + " in project " + projectId,
                () ->
                {
                    String logs = this.workflowJobApi.getWorkspaceWorkflowJobAccessContext(projectId, workspaceId, WorkspaceType.GROUP, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE).getWorkflowJobLog(workflowId, workflowJobId);
                    return Response.ok(logs)
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + workflowJobId + ".log\"")
                            .build();
                }
        );
    }

    @POST
    @Path("{workflowJobId}/run")
    @Operation(summary = "Run a manual workflow job")
    public WorkflowJob runWorkflowJob(@PathParam("projectId") String projectId,
                                      @PathParam("workspaceId") String workspaceId,
                                      @PathParam("workflowId") String workflowId,
                                      @PathParam("workflowJobId") String workflowJobId)
    {
        return executeWithLogging(
                "running workflow job " + workflowJobId + " for group workspace " + workspaceId + " in project " + projectId,
                () -> this.workflowJobApi.getWorkspaceWorkflowJobAccessContext(projectId, workspaceId, WorkspaceType.GROUP, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE).runWorkflowJob(workflowId, workflowJobId)
        );
    }

    @POST
    @Path("{workflowJobId}/retry")
    @Operation(summary = "Retry a failed workflow job")
    public WorkflowJob retryWorkflowJob(@PathParam("projectId") String projectId,
                                        @PathParam("workspaceId") String workspaceId,
                                        @PathParam("workflowId") String workflowId,
                                        @PathParam("workflowJobId") String workflowJobId)
    {
        return executeWithLogging(
                "retrying workflow job " + workflowJobId + " for group workspace " + workspaceId + " in project " + projectId,
                () -> this.workflowJobApi.getWorkspaceWorkflowJobAccessContext(projectId, workspaceId, WorkspaceType.GROUP, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE).retryWorkflowJob(workflowId, workflowJobId)
        );
    }


    @POST
    @Path("{workflowJobId}/cancel")
    @Operation(summary = "Cancel a workflow job that is in progress")
    public WorkflowJob cancelWorkflowJob(@PathParam("projectId") String projectId,
                                         @PathParam("workspaceId") String workspaceId,
                                         @PathParam("workflowId") String workflowId,
                                         @PathParam("workflowJobId") String workflowJobId)
    {
        return executeWithLogging(
                "canceling workflow job " + workflowJobId + " for group workspace " + workspaceId + " in project " + projectId,
                () -> this.workflowJobApi.getWorkspaceWorkflowJobAccessContext(projectId, workspaceId, WorkspaceType.GROUP, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE).cancelWorkflowJob(workflowId, workflowJobId)
        );
    }
}
