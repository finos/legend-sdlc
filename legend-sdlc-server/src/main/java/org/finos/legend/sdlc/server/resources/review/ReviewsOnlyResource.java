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

package org.finos.legend.sdlc.server.resources.review;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.review.Review;
import org.finos.legend.sdlc.domain.model.review.ReviewState;
import org.finos.legend.sdlc.server.domain.api.review.ReviewApi;
import org.finos.legend.sdlc.server.resources.ReviewFilterResource;
import org.finos.legend.sdlc.server.time.EndInstant;
import org.finos.legend.sdlc.server.time.ResolvedInstant;
import org.finos.legend.sdlc.server.time.StartInstant;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
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
public class ReviewsOnlyResource extends ReviewFilterResource
{
    private final ReviewApi reviewApi;

    @Inject
    public ReviewsOnlyResource(ReviewApi reviewApi)
    {
        this.reviewApi = reviewApi;
    }

    @GET
    @ApiOperation(value = "Get reviews across all projects", notes = "Get reviews across all projects. If assignedToMe is set to true only reviews assigned to the user are returned, if authoredByMe is true only reviews authored by me are returned. If state is provided, then only reviews with the given state are returned. Otherwise, all reviews are returned. If state is UNKNOWN, results are undefined.")
    public List<Review> getReviews(@QueryParam("assignedToMe")
                                   @DefaultValue("false")
                                   @ApiParam("Only include reviews assigned to me if true") boolean assignedToMe,
                                   @QueryParam("authoredByMe")
                                   @DefaultValue("true")
                                   @ApiParam("Only include reviews authored/created by me if true") boolean authoredByMe,
                                   @QueryParam("labels")
                                   @ApiParam("Only include reviews that match all the given labels") List<String> labels,
                                   @QueryParam("workspaceIdRegex") @ApiParam("Include reviews with a workspace id matching this regular expression") String workspaceIdRegex,
                                   @QueryParam("workspaceTypes") @ApiParam("Include reviews with any of the given workspace types") Set<WorkspaceType> workspaceTypes,
                                   @QueryParam("state")
                                   @ApiParam("Only include reviews with the given state") ReviewState state,
                                   @QueryParam("since")
                                   @ApiParam("This time limit is interpreted based on the chosen state: for COMMITTED state `since` means committed time, for CLOSED state, it means closed time, for all other case, it means created time") StartInstant since,
                                   @QueryParam("until")
                                   @ApiParam("This time limit is interpreted based on the chosen state: for COMMITTED state `until` means committed time, for CLOSED state, it means closed time, for all other case, it means created time") EndInstant until,
                                   @QueryParam("limit")
                                   @ApiParam("If not provided or the provided value is non-positive, no filtering will be applied") Integer limit,
                                   // TO BE DEPRECATED (in Swagger 3, we can use the `deprecated` flag)
                                   @QueryParam("projectTypes")
                                   @ApiParam(hidden = true, value = "Only include reviews for the given project type") Set<ProjectType> projectTypes)
    {
        return executeWithLogging(
            (state == null) ? ("getting reviews for project type(s) " + projectTypes) : ("getting reviews for project type(s) " + projectTypes + " with state " + state),
            () -> this.reviewApi.getReviews(assignedToMe, authoredByMe, labels, this.getWorkspaceIdAndTypePredicate(workspaceIdRegex, workspaceTypes), state, ResolvedInstant.getResolvedInstantIfNonNull(since), ResolvedInstant.getResolvedInstantIfNonNull(until), limit));
    }
}
