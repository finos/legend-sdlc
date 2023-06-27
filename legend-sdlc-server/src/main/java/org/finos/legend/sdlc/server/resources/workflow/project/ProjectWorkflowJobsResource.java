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

package org.finos.legend.sdlc.server.resources.workflow.project;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.finos.legend.sdlc.domain.model.workflow.WorkflowJob;
import org.finos.legend.sdlc.domain.model.workflow.WorkflowJobStatus;
import org.finos.legend.sdlc.server.domain.api.workflow.WorkflowJobApi;
import org.finos.legend.sdlc.server.resources.BaseResource;

import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/projects/{projectId}/workflows/{workflowId}/jobs")
@Api("Workflows")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ProjectWorkflowJobsResource extends BaseResource
{
    private final WorkflowJobApi workflowJobApi;

    @Inject
    public ProjectWorkflowJobsResource(WorkflowJobApi workflowJobApi)
    {
        this.workflowJobApi = workflowJobApi;
    }

    @GET
    @ApiOperation(value = "Get jobs for a workflow", notes = "Get jobs for a workflow. If status is provided, then only workflow jobs with the given status are returned. Otherwise, all workflows are returned. If status is UNKNOWN, results are undefined.")
    public List<WorkflowJob> getWorkflowJobs(@PathParam("projectId") String projectId,
                                             @PathParam("workflowId") String workflowId,
                                             @QueryParam("status") @ApiParam("Only include workflow jobs with one of the given statuses") Set<WorkflowJobStatus> statuses)
    {
        return executeWithLogging(
                "getting workflow jobs for project " + projectId,
                () -> this.workflowJobApi.getProjectWorkflowJobAccessContext(projectId).getWorkflowJobs(workflowId, statuses)
        );
    }

    @GET
    @Path("{workflowJobId}")
    @ApiOperation("Get a workflow job")
    public WorkflowJob getWorkflowJob(@PathParam("projectId") String projectId,
                                      @PathParam("workflowId") String workflowId,
                                      @PathParam("workflowJobId") String workflowJobId)
    {
        return executeWithLogging(
                "getting workflow job " + workflowJobId + " for project " + projectId,
                () -> this.workflowJobApi.getProjectWorkflowJobAccessContext(projectId).getWorkflowJob(workflowId, workflowJobId)
        );
    }

    @GET
    @Path("{workflowJobId}/logs")
    @ApiOperation("Get a workflow job log")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getWorkflowJobLogs(@PathParam("projectId") String projectId,
                                       @PathParam("workflowId") String workflowId,
                                       @PathParam("workflowJobId") String workflowJobId)
    {
        return executeWithLogging(
                "getting workflow job logs " + workflowJobId + " for project " + projectId,
                () ->
                {
                    String logs = this.workflowJobApi.getProjectWorkflowJobAccessContext(projectId).getWorkflowJobLog(workflowId, workflowJobId);
                    return Response.ok(logs)
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + workflowJobId + ".log\"")
                            .build();
                }
        );
    }

    @POST
    @Path("{workflowJobId}/run")
    @ApiOperation("Run a manual workflow job")
    public WorkflowJob runWorkflowJob(@PathParam("projectId") String projectId,
                                      @PathParam("workflowId") String workflowId,
                                      @PathParam("workflowJobId") String workflowJobId)
    {
        return executeWithLogging(
                "running workflow job " + workflowJobId + " for project " + projectId,
                () -> this.workflowJobApi.getProjectWorkflowJobAccessContext(projectId).runWorkflowJob(workflowId, workflowJobId)
        );
    }

    @POST
    @Path("{workflowJobId}/retry")
    @ApiOperation("Retry a failed workflow job")
    public WorkflowJob retryWorkflowJob(@PathParam("projectId") String projectId,
                                        @PathParam("workflowId") String workflowId,
                                        @PathParam("workflowJobId") String workflowJobId)
    {
        return executeWithLogging(
                "retrying workflow job " + workflowJobId + " for project " + projectId,
                () -> this.workflowJobApi.getProjectWorkflowJobAccessContext(projectId).retryWorkflowJob(workflowId, workflowJobId)
        );
    }


    @POST
    @Path("{workflowJobId}/cancel")
    @ApiOperation("Cancel a workflow job that is in progress")
    public WorkflowJob cancelWorkflowJob(@PathParam("projectId") String projectId,
                                         @PathParam("workflowId") String workflowId,
                                         @PathParam("workflowJobId") String workflowJobId)
    {
        return executeWithLogging(
                "canceling workflow job " + workflowJobId + " for project " + projectId,
                () -> this.workflowJobApi.getProjectWorkflowJobAccessContext(projectId).cancelWorkflowJob(workflowId, workflowJobId)
        );
    }
}
