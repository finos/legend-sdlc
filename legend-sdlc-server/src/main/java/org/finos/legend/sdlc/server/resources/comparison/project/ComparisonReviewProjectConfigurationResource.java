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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.server.domain.api.project.ProjectConfigurationApi;
import org.finos.legend.sdlc.server.resources.EntityAccessResource;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;


@Path("/projects/{projectId}/reviews/{reviewId}/comparison")
@Api("Comparison")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ComparisonReviewProjectConfigurationResource extends EntityAccessResource
{
    private final ProjectConfigurationApi projectConfigurationApi;

    @Inject
    public ComparisonReviewProjectConfigurationResource(ProjectConfigurationApi projectConfigurationApi)
    {
        this.projectConfigurationApi = projectConfigurationApi;
    }

    @GET
    @Path("from/configuration")
    @ApiOperation("Get [from] project configuration for a given review")
    public ProjectConfiguration getReviewFromProjectConfiguration(@PathParam("projectId") String projectId, @PathParam("reviewId") String reviewId)
    {
        return executeWithLogging(
                "getting [from] project configuration for review " + reviewId + " of project " + projectId,
                () -> this.projectConfigurationApi.getReviewFromProjectConfiguration(projectId, reviewId)
        );
    }

    @GET
    @Path("to/configuration")
    @ApiOperation("Get [to] project configuration for a given review")
    public ProjectConfiguration getReviewToProjectConfiguration(@PathParam("projectId") String projectId, @PathParam("reviewId") String reviewId)
    {
        return executeWithLogging(
                "getting [to] project configuration for review " + reviewId + " of project " + projectId,
                () -> this.projectConfigurationApi.getReviewToProjectConfiguration(projectId, reviewId)
        );
    }

}
