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

package org.finos.legend.sdlc.server.resources.review.project;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.review.Approval;
import org.finos.legend.sdlc.domain.model.review.Review;
import org.finos.legend.sdlc.domain.model.review.ReviewState;
import org.finos.legend.sdlc.server.application.review.CommitReviewCommand;
import org.finos.legend.sdlc.server.application.review.CreateReviewCommand;
import org.finos.legend.sdlc.server.application.review.EditReviewCommand;
import org.finos.legend.sdlc.server.domain.api.review.ReviewApi;
import org.finos.legend.sdlc.server.domain.api.review.ReviewApi.ReviewUpdateStatus;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.resources.ReviewFilterResource;
import org.finos.legend.sdlc.server.time.EndInstant;
import org.finos.legend.sdlc.server.time.ResolvedInstant;
import org.finos.legend.sdlc.server.time.StartInstant;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

@Path("/projects/{projectId}/reviews")
@Api("Reviews")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ReviewsResource extends ReviewFilterResource
{
    private final ReviewApi reviewApi;

    @Inject
    public ReviewsResource(ReviewApi reviewApi)
    {
        this.reviewApi = reviewApi;
    }

    @GET
    @ApiOperation(value = "Get reviews for a project", notes = "Get reviews for a project. If state is provided, then only reviews with the given state are returned. Otherwise, all reviews are returned. If state is UNKNOWN, results are undefined.")
    public List<Review> getReviews(@PathParam("projectId") String projectId,
                                   @QueryParam("state") @ApiParam("Only include reviews with the given state") ReviewState state,
                                   @QueryParam("revisionIds") @ApiParam("List of revision IDs that any of the reviews are associated to") Set<String> revisionIds,
                                   @QueryParam("workspaceIdRegex") @ApiParam("Include reviews with a workspace id matching this regular expression") String workspaceIdRegex,
                                   @QueryParam("workspaceTypes") @ApiParam("Include reviews with any of the given workspace types") Set<WorkspaceType> workspaceTypes,
                                   @QueryParam("since") @ApiParam("This time limit is interpreted based on the chosen state: for COMMITTED state `since` means committed time, for CLOSED state, it means closed time, for all other case, it means created time") StartInstant since,
                                   @QueryParam("until") @ApiParam("This time limit is interpreted based on the chosen state: for COMMITTED state `until` means committed time, for CLOSED state, it means closed time, for all other case, it means created time") EndInstant until,
                                   @QueryParam("limit") @ApiParam("If not provided or the provided value is non-positive, no filtering will be applied") Integer limit)
    {
        return executeWithLogging(
                (state == null) ? ("getting reviews for project " + projectId) : ("getting reviews for project " + projectId + " with state " + state),
                () -> this.reviewApi.getReviews(projectId, state, revisionIds, this.getWorkspaceIdAndTypePredicate(workspaceIdRegex, workspaceTypes), ResolvedInstant.getResolvedInstantIfNonNull(since), ResolvedInstant.getResolvedInstantIfNonNull(until), limit)
        );
    }

    @GET
    @Path("{reviewId}")
    @ApiOperation("Get a review")
    public Review getReview(@PathParam("projectId") String projectId, @PathParam("reviewId") String reviewId)
    {
        return executeWithLogging(
                "getting review " + reviewId + " for project " + projectId,
                () -> this.reviewApi.getReview(projectId, reviewId)
        );
    }

    @GET
    @Path("{reviewId}/outdated")
    @ApiOperation(value = "Check if a review is outdated", notes = "Check if an open review is outdated, i.e., if it is not based on the latest project revision. Returns false if an update is in progress. Returns a 409 status if the review is not open.")
    public boolean isReviewOutdated(@PathParam("projectId") String projectId, @PathParam("reviewId") String reviewId)
    {
        return executeWithLogging(
                "checking if review " + reviewId + " for project " + projectId + " is outdated",
                () ->
                {
                    ReviewUpdateStatus status = this.reviewApi.getReviewUpdateStatus(projectId, reviewId);
                    if (status.isUpdateInProgress())
                    {
                        return false;
                    }

                    String baseRevision = status.getBaseRevisionId();
                    String targetRevision = status.getTargetRevisionId();
                    return (baseRevision != null) && (targetRevision != null) && !baseRevision.equals(targetRevision);
                }
        );
    }

    @POST
    @ApiOperation("Create a review")
    public Review createReview(@PathParam("projectId") String projectId, CreateReviewCommand command)
    {
        LegendSDLCServerException.validateNonNull(command, "Input required to create review");
        return execute(
                "creating review \"" + command.getTitle() + "\" in project " + projectId,
                "create a review",
                () -> this.reviewApi.createReview(projectId, command.getWorkspaceId(), Optional.ofNullable(command.getWorkspaceType()).orElse(WorkspaceType.USER), command.getTitle(), command.getDescription(), command.getLabels())
        );
    }

    @POST
    @Path("{reviewId}/close")
    @ApiOperation(value = "Close a review", notes = "Close a review. This is only valid if the review is open.")
    public Review closeReview(@PathParam("projectId") String projectId, @PathParam("reviewId") String reviewId)
    {
        return executeWithLogging(
                "closing review " + reviewId + " for project " + projectId,
                () -> this.reviewApi.closeReview(projectId, reviewId)
        );
    }

    @POST
    @Path("{reviewId}/reopen")
    @ApiOperation(value = "Reopen a closed review", notes = "Reopen a review. This is only valid if the review is closed.")
    public Review reopenReview(@PathParam("projectId") String projectId, @PathParam("reviewId") String reviewId)
    {
        return executeWithLogging(
                "reopening review " + reviewId + " for project " + projectId,
                () -> this.reviewApi.reopenReview(projectId, reviewId)
        );
    }

    @POST
    @Path("{reviewId}/approve")
    @ApiOperation(value = "Approve a review", notes = "Approve a review. This is only valid if the review is open.")
    public Review approveReview(@PathParam("projectId") String projectId, @PathParam("reviewId") String reviewId)
    {
        return executeWithLogging(
                "approving review " + reviewId + " for project " + projectId,
                () -> this.reviewApi.approveReview(projectId, reviewId)
        );
    }

    @POST
    @Path("{reviewId}/revokeApproval")
    @ApiOperation(value = "Revoke approval of a review", notes = "Revoke approval of a review. This is only valid if the review is open and you have approved it.")
    public Review revokeReviewApproval(@PathParam("projectId") String projectId, @PathParam("reviewId") String reviewId)
    {
        return executeWithLogging(
                "revoking review approval " + reviewId + " for project " + projectId,
                () -> this.reviewApi.revokeReviewApproval(projectId, reviewId)
        );
    }

    @POST
    @Path("{reviewId}/reject")
    @ApiOperation(value = "Reject a review", notes = "Reject a review. This is only valid if the review is open. It may cause the review to be closed.")
    public Review rejectReview(@PathParam("projectId") String projectId, @PathParam("reviewId") String reviewId)
    {
        return executeWithLogging(
                "rejecting review " + reviewId + " for project " + projectId,
                () -> this.reviewApi.rejectReview(projectId, reviewId)
        );
    }

    @GET
    @Path("{reviewId}/approval")
    @ApiOperation("Get approval information for a review")
    public Approval getReviewApproval(@PathParam("projectId") String projectId, @PathParam("reviewId") String reviewId)
    {
        return executeWithLogging(
            "getting approval details for review " + reviewId + " in project " + projectId,
                () -> this.reviewApi.getReviewApproval(projectId, reviewId)
        );
    }

    @POST
    @Path("{reviewId}/commit")
    @ApiOperation(value = "Commit an approved review", notes = "Commit changes from a review. This is only valid if the review is open and has sufficient approvals.")
    public Review commitReview(@PathParam("projectId") String projectId, @PathParam("reviewId") String reviewId, CommitReviewCommand command)
    {
        LegendSDLCServerException.validateNonNull(command, "Input required to commit review");
        return executeWithLogging(
                "committing review " + reviewId + " for project " + projectId,
                () -> this.reviewApi.commitReview(projectId, reviewId, command.getMessage())
        );
    }

    @GET
    @Path("{reviewId}/updateStatus")
    @ApiOperation(value = "Get the update status of a review", notes = "Get the update status for an open review. If the review is not open, returns a 409 status. If an update is in progress, then the base revision will be null.")
    public ReviewUpdateStatus getReviewUpdateStatus(@PathParam("projectId") String projectId, @PathParam("reviewId") String reviewId)
    {
        return executeWithLogging(
                "getting update status for review " + reviewId + " for project " + projectId,
                () -> this.reviewApi.getReviewUpdateStatus(projectId, reviewId)
        );
    }

    @POST
    @Path("{reviewId}/update")
    @ApiOperation(value = "Update a review", notes = "Try to update an open review. That is, try to bring the review up to date with the latest revision of the project. If the review is not open, this will return a 409 status. This does not wait for the update to complete. It starts the update and returns an initial status. Call updateStatus for subsequent updates. If an update is already in progress or if the review is already up to date, this returns the current update status but is otherwise a no-op. Note that it is not always possible to update a review. In case the update fails, the review will be left in the pre-update state.")
    public ReviewUpdateStatus updateReview(@PathParam("projectId") String projectId, @PathParam("reviewId") String reviewId)
    {
        return executeWithLogging(
                "updating review " + reviewId + " for project " + projectId,
                () -> this.reviewApi.updateReview(projectId, reviewId)
        );
    }

    @POST
    @Path("{reviewId}/edit")
    @ApiOperation(value = "Edit a review", notes = "Edit an open review. This updates the title, description and the labels of the review")
    public Review editReview(@PathParam("projectId") String projectId, @PathParam("reviewId") String reviewId, EditReviewCommand command)
    {
        LegendSDLCServerException.validateNonNull(command, "Input required to create review");
        return execute(
                "editing review " + reviewId + " in project " + projectId,
                "edit a review",
                () -> this.reviewApi.editReview(projectId, reviewId, command.getTitle(), command.getDescription(), command.getLabels())
        );
    }
}
