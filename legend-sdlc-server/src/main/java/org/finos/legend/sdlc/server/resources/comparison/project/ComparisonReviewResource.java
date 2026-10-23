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

package org.finos.legend.sdlc.server.resources.comparison.project;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.finos.legend.sdlc.domain.model.comparison.Comparison;
import org.finos.legend.sdlc.backend.api.comparison.ComparisonApi;
import org.finos.legend.sdlc.server.resources.BaseResource;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/projects/{projectId}/reviews/{reviewId}/comparison")
@Tag(name = "Comparison")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ComparisonReviewResource extends BaseResource
{
    private final ComparisonApi comparisonApi;

    @Inject
    public ComparisonReviewResource(ComparisonApi comparisonApi)
    {
        this.comparisonApi = comparisonApi;
    }

    @GET
    @Operation(summary = "Get comparison for a given review (between the review current workspace revision and current project revision")
    public Comparison getReviewComparison(@PathParam("projectId") String projectId, @PathParam("reviewId") String reviewId)
    {
        return executeWithLogging(
                "getting comparison between the current workspace revision and current project revision for review " + reviewId + " for project " + projectId,
                () -> this.comparisonApi.getReviewComparison(projectId, reviewId)
        );
    }

    @GET
    @Path("/projectLatest")
    @Operation(summary = "Get comparison for a given review (between the review current workspace revision and current project revision")
    public Comparison getReviewProjectLatestComparison(@PathParam("projectId") String projectId, @PathParam("reviewId") String reviewId)
    {
        return executeWithLogging(
                "getting comparison between the current workspace revision and current project revision for review " + reviewId + " for project " + projectId,
                () -> this.comparisonApi.getReviewComparison(projectId, reviewId)
        );
    }

    @GET
    @Path("/workspaceCreation")
    @Operation(summary = "Get comparison for a given review (between the review current workspace revision and review workspace creation revision")
    public Comparison getReviewWorkspaceCreationComparison(@PathParam("projectId") String projectId, @PathParam("reviewId") String reviewId)
    {
        return executeWithLogging(
                "getting comparison between the review current workspace revision and review workspace creation revision for review " + reviewId + " for project " + projectId,
                () -> this.comparisonApi.getReviewWorkspaceCreationComparison(projectId, reviewId)
        );
    }
}
