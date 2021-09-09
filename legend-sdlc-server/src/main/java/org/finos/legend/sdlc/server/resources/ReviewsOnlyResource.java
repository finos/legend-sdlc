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
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.review.Review;
import org.finos.legend.sdlc.domain.model.review.ReviewState;
import org.finos.legend.sdlc.server.domain.api.review.ReviewApi;
import org.finos.legend.sdlc.server.time.EndInstant;
import org.finos.legend.sdlc.server.time.ResolvedInstant;
import org.finos.legend.sdlc.server.time.StartInstant;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Set;

@Path("/reviews")
@Api("Reviews")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ReviewsOnlyResource extends BaseResource
{
    private final ReviewApi reviewApi;

    @Inject
    public ReviewsOnlyResource(ReviewApi reviewApi)
    {
        this.reviewApi = reviewApi;
    }


    @GET
    @ApiOperation(value = "Get reviews across all projects", notes = "Get reviews across all projects. If state is provided, then only reviews with the given state are returned. Otherwise, all reviews are returned. If state is UNKNOWN, results are undefined.")
    public List<Review> getReviews(@QueryParam("projectTypes") @ApiParam("If not provided or the provided. valid project types would be used") Set<ProjectType> projectTypes,
                                   @QueryParam("assignedToMe") @ApiParam("show reviews assigned to me, would be set to true is not selected") Boolean  assignedToMe,
                                   @QueryParam("authoredByMe") @ApiParam("show reviews authored by user, would be set to false is not selected") Boolean  authoredByMe,
                                   @QueryParam("assignee") @ApiParam("show reviews assigned to a particular user") Integer  assignee,
                                   @QueryParam("author") @ApiParam("show reviews authored by user") Integer  author,
                                   @QueryParam("state") @ApiParam("Only include reviews with the given state") ReviewState state,
                                   @QueryParam("since") @ApiParam("This time limit is interpreted based on the chosen state: for COMMITTED state `since` means committed time, for CLOSED state, it means closed time, for all other case, it means created time") StartInstant since,
                                   @QueryParam("until") @ApiParam("This time limit is interpreted based on the chosen state: for COMMITTED state `until` means committed time, for CLOSED state, it means closed time, for all other case, it means created time") EndInstant until,
                                   @QueryParam("limit") @ApiParam("If not provided or the provided value is non-positive, no filtering will be applied") Integer limit)
    {
        return executeWithLogging(
                (state == null) ? ("getting reviews for project type(s) "  + projectTypes) : ("getting reviews for project type" + projectTypes + " with state " + state),
                () -> this.reviewApi.getReviews(
                        projectTypes,
                        (assignedToMe == null) ? false : assignedToMe,
                        (authoredByMe == null) ? false : authoredByMe,
                        assignee,
                        author,
                        state,
                        ResolvedInstant.getResolvedInstantIfNonNull(since),
                        ResolvedInstant.getResolvedInstantIfNonNull(until),
                        limit)
        );
    }
}
