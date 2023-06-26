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

package org.finos.legend.sdlc.server.resources.workflow.patch.group;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.domain.model.workflow.WorkflowJob;
import org.finos.legend.sdlc.domain.model.workflow.WorkflowJobStatus;
import org.finos.legend.sdlc.server.domain.api.workflow.WorkflowJobApi;
import org.finos.legend.sdlc.server.domain.api.project.SourceSpecification;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
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

@Path("/projects/{projectId}/patches/{patchReleaseVersionId}/groupWorkspaces/{workspaceId}/workflows/{workflowId}/jobs")
@Api("Workflows")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PatchesGroupWorkspaceWorkflowJobsResource extends BaseResource
{
    private final WorkflowJobApi workflowJobApi;

    @Inject
    public PatchesGroupWorkspaceWorkflowJobsResource(WorkflowJobApi workflowJobApi)
    {
        this.workflowJobApi = workflowJobApi;
    }

    @GET
    @ApiOperation(value = "Get jobs for a workflow for a group workspace for patch release version", notes = "Get jobs for a workflow. If status is provided, then only workflow jobs with the given status are returned. Otherwise, all workflows are returned. If status is UNKNOWN, results are undefined.")
    public List<WorkflowJob> getWorkflowJobs(@PathParam("projectId") String projectId,
                                             @PathParam("patchReleaseVersionId") String patchReleaseVersionId,
                                             @PathParam("workspaceId") String workspaceId,
                                             @PathParam("workflowId") String workflowId,
                                             @QueryParam("status") @ApiParam("Only include workflow jobs with one of the given statuses") Set<WorkflowJobStatus> statuses)
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
                "getting workflow jobs for group workspace " + workspaceId + " in project " + projectId + " for patch release version " + patchReleaseVersionId,
                () -> this.workflowJobApi.getWorkspaceWorkflowJobAccessContext(projectId, SourceSpecification.newSourceSpecification(workspaceId, WorkspaceType.GROUP, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE, versionId)).getWorkflowJobs(workflowId, statuses)
        );
    }

    @GET
    @Path("{workflowJobId}")
    @ApiOperation("Get a workflow job in a group workspace for patch release version")
    public WorkflowJob getWorkflowJob(@PathParam("projectId") String projectId,
                                      @PathParam("patchReleaseVersionId") String patchReleaseVersionId,
                                      @PathParam("workspaceId") String workspaceId,
                                      @PathParam("workflowId") String workflowId,
                                      @PathParam("workflowJobId") String workflowJobId)
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
                "getting workflow job " + workflowJobId + " for group workspace " + workspaceId + " in project " + projectId + " for patch release version " + patchReleaseVersionId,
                () -> this.workflowJobApi.getWorkspaceWorkflowJobAccessContext(projectId, SourceSpecification.newSourceSpecification(workspaceId, WorkspaceType.GROUP, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE, versionId)).getWorkflowJob(workflowId, workflowJobId)
        );
    }

    @GET
    @Path("{workflowJobId}/logs")
    @ApiOperation("Get a workflow job log for patch release version")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getWorkflowJobLogs(@PathParam("projectId") String projectId,
                                       @PathParam("patchReleaseVersionId") String patchReleaseVersionId,
                                       @PathParam("workspaceId") String workspaceId,
                                       @PathParam("workflowId") String workflowId,
                                       @PathParam("workflowJobId") String workflowJobId)
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
                "getting workflow job logs " + workflowJobId + " for group workspace " + workspaceId + " in project " + projectId + " for patch release version " + patchReleaseVersionId,
                () ->
                {
                    String logs = this.workflowJobApi.getWorkspaceWorkflowJobAccessContext(projectId, SourceSpecification.newSourceSpecification(workspaceId, WorkspaceType.GROUP, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE, versionId)).getWorkflowJobLog(workflowId, workflowJobId);
                    return Response.ok(logs)
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + workflowJobId + ".log\"")
                            .build();
                }
        );
    }

    @POST
    @Path("{workflowJobId}/run")
    @ApiOperation("Run a manual workflow job for patch release version")
    public WorkflowJob runWorkflowJob(@PathParam("projectId") String projectId,
                                      @PathParam("patchReleaseVersionId") String patchReleaseVersionId,
                                      @PathParam("workspaceId") String workspaceId,
                                      @PathParam("workflowId") String workflowId,
                                      @PathParam("workflowJobId") String workflowJobId)
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
                "running workflow job " + workflowJobId + " for group workspace " + workspaceId + " in project " + projectId + " for patch release version " + patchReleaseVersionId,
                () -> this.workflowJobApi.getWorkspaceWorkflowJobAccessContext(projectId, SourceSpecification.newSourceSpecification(workspaceId, WorkspaceType.GROUP, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE, versionId)).runWorkflowJob(workflowId, workflowJobId)
        );
    }

    @POST
    @Path("{workflowJobId}/retry")
    @ApiOperation("Retry a failed workflow job for patch release version")
    public WorkflowJob retryWorkflowJob(@PathParam("projectId") String projectId,
                                        @PathParam("patchReleaseVersionId") String patchReleaseVersionId,
                                        @PathParam("workspaceId") String workspaceId,
                                        @PathParam("workflowId") String workflowId,
                                        @PathParam("workflowJobId") String workflowJobId)
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
                "retrying workflow job " + workflowJobId + " for group workspace " + workspaceId + " in project " + projectId + " for patch release version " + patchReleaseVersionId,
                () -> this.workflowJobApi.getWorkspaceWorkflowJobAccessContext(projectId, SourceSpecification.newSourceSpecification(workspaceId, WorkspaceType.GROUP, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE, versionId)).retryWorkflowJob(workflowId, workflowJobId)
        );
    }


    @POST
    @Path("{workflowJobId}/cancel")
    @ApiOperation("Cancel a workflow job that is in progress for patch release version")
    public WorkflowJob cancelWorkflowJob(@PathParam("projectId") String projectId,
                                         @PathParam("patchReleaseVersionId") String patchReleaseVersionId,
                                         @PathParam("workspaceId") String workspaceId,
                                         @PathParam("workflowId") String workflowId,
                                         @PathParam("workflowJobId") String workflowJobId)
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
                "canceling workflow job " + workflowJobId + " for group workspace " + workspaceId + " in project " + projectId + " for patch release version " + patchReleaseVersionId,
                () -> this.workflowJobApi.getWorkspaceWorkflowJobAccessContext(projectId, SourceSpecification.newSourceSpecification(workspaceId, WorkspaceType.GROUP, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE, versionId)).cancelWorkflowJob(workflowId, workflowJobId)
        );
    }
}
