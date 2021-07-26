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

package org.finos.legend.sdlc.server.resources;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.finos.legend.sdlc.domain.model.comparison.Comparison;
import org.finos.legend.sdlc.server.domain.api.comparison.ComparisonApi;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/projects/{projectId}/workspaces/{workspaceId}/comparison")
@Api("Comparison")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ComparisonWorkspaceResource extends BaseResource
{
    private final ComparisonApi comparisonApi;

    @Inject
    public ComparisonWorkspaceResource(ComparisonApi comparisonApi)
    {
        this.comparisonApi = comparisonApi;
    }

    @GET
    @Path("workspaceCreation")
    @ApiOperation("Get comparison between current user workspace revision and workspace creation revision")
    public Comparison getWorkspaceCreationComparison(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId)
    {
        return executeWithLogging(
                "getting comparison between current user workspace revision and project revision at workspace creation, for user workspace " + workspaceId + " for project " + projectId,
                () -> this.comparisonApi.getUserWorkspaceCreationComparison(projectId, workspaceId)
        );
    }

    @GET
    @Path("projectLatest")
    @ApiOperation("Get comparison between current user workspace revision and current project revision")
    public Comparison getWorkspaceProjectComparison(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId)
    {
        return executeWithLogging(
                "getting comparison between current user workspace revision and current project revision, for user workspace " + workspaceId + " for project " + projectId,
                () -> this.comparisonApi.getUserWorkspaceProjectComparison(projectId, workspaceId)
        );
    }
}
