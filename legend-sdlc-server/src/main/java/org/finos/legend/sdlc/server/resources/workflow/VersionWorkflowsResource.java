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

package org.finos.legend.sdlc.server.resources.workflow;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.finos.legend.sdlc.domain.model.workflow.Workflow;
import org.finos.legend.sdlc.domain.model.workflow.WorkflowStatus;
import org.finos.legend.sdlc.backend.api.workflow.WorkflowApi;
import org.finos.legend.sdlc.server.resources.BaseResource;

import java.util.List;
import java.util.Set;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/projects/{projectId}/versions/{versionId}/workflows")
@Tag(name = "Workflows")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class VersionWorkflowsResource extends BaseResource
{
    private final WorkflowApi workflowApi;

    @Inject
    public VersionWorkflowsResource(WorkflowApi workflowApi)
    {
        this.workflowApi = workflowApi;
    }

    @GET
    @Operation(summary = "Get workflows for a version", description = "Get workflows for a version. If status is provided, then only workflows with the given status are returned. Otherwise, all workflows are returned. If status is UNKNOWN, results are undefined.")
    public List<Workflow> getWorkflows(@PathParam("projectId") String projectId,
                                       @PathParam("versionId") String versionId,
                                       @QueryParam("revisionId")
                                       @Parameter(description = "Only include workflows for one of the given revisions") Set<String> revisionIds,
                                       @QueryParam("status")
                                       @Parameter(description = "Only include workflows with one of the given statuses") Set<WorkflowStatus> statuses,
                                       @QueryParam("limit")
                                       @Parameter(description = "Limit the number of workflows returned (if not provided or the provided value is non-positive, no filtering will be applied)") Integer limit)
    {
        return executeWithLogging(
                "getting workflows for version " + versionId + " of project " + projectId,
                () -> this.workflowApi.getVersionWorkflowAccessContext(projectId, versionId).getWorkflows(revisionIds, statuses, limit)
        );
    }

    @GET
    @Path("{workflowId}")
    @Operation(summary = "Get a workflow for a version")
    public Workflow getWorkflow(@PathParam("projectId") String projectId, @PathParam("versionId") String versionId, @PathParam("workflowId") String workflowId)
    {
        return executeWithLogging(
                "getting workflow " + workflowId + " for version " + versionId + " of project " + projectId,
                () -> this.workflowApi.getVersionWorkflowAccessContext(projectId, versionId).getWorkflow(workflowId)
        );
    }
}
