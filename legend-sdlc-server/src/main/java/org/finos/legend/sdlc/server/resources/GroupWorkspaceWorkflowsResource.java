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

package org.finos.legend.sdlc.server.resources;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.finos.legend.sdlc.domain.model.workflow.Workflow;
import org.finos.legend.sdlc.domain.model.workflow.WorkflowStatus;
import org.finos.legend.sdlc.server.domain.api.workflow.WorkflowApi;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;

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

@Path("/projects/{projectId}/groupWorkspaces/{workspaceId}/workflows")
@Api("Workflows")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class GroupWorkspaceWorkflowsResource extends BaseResource
{
    private final WorkflowApi workflowApi;

    @Inject
    public GroupWorkspaceWorkflowsResource(WorkflowApi workflowApi) {
        this.workflowApi = workflowApi;
    }

    @GET
    @ApiOperation(value = "Get workflows for a group workspace", notes = "Get workflows for a group workspace. If status is provided, then only workflows with the given status are returned. Otherwise, all workflows are returned. If status is UNKNOWN, results are undefined.")
    public List<Workflow> getWorkflows(@PathParam("projectId") String projectId,
                                       @PathParam("workspaceId") String workspaceId,
                                       @QueryParam("revisionId")
                                       @ApiParam("Only include workflows for one of the given revisions") Set<String> revisionIds,
                                       @QueryParam("status")
                                       @ApiParam("Only include workflows with one of the given statuses") Set<WorkflowStatus> statuses,
                                       @QueryParam("limit")
                                       @ApiParam("Limit the number of workflows returned") Integer limit)
    {
        return executeWithLogging(
                "getting workflows for group workspace " + workspaceId + " in project " + projectId,
                () -> this.workflowApi.getWorkspaceWorkflowAccessContext(projectId, workspaceId, ProjectFileAccessProvider.WorkspaceAccessType.GROUP).getWorkflows(revisionIds, statuses, limit)
        );
    }

    @GET
    @Path("{workflowId}")
    @ApiOperation("Get a workflow for a group workspace")
    public Workflow getWorkflow(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId, @PathParam("workflowId") String workflowId)
    {
        return executeWithLogging(
                "getting workflow " + workflowId + " for group workspace " + workspaceId + " in project " + projectId,
                () -> this.workflowApi.getWorkspaceWorkflowAccessContext(projectId, workspaceId, ProjectFileAccessProvider.WorkspaceAccessType.GROUP).getWorkflow(workflowId)
        );
    }
}