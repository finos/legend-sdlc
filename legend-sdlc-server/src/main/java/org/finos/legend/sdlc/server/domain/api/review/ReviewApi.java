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

package org.finos.legend.sdlc.server.domain.api.review;

import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.review.Review;
import org.finos.legend.sdlc.domain.model.review.ReviewState;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface ReviewApi
{
    /**
     * Get a particular review for the given project.
     *
     * @param projectId project id
     * @param reviewId  review id
     * @return review
     */
    Review getReview(String projectId, String reviewId);

    /**
     * Get all reviews for the given project with the given state.
     * If state is null, all reviews are returned. Results are undefined for state {@link ReviewState#UNKNOWN}.
     * For each unique revision ID provided, we will get the reviews associated with it, compile all into a list and
     * remove duplicates to form a single list of reviews to return (with other constraints applied on top of that).
     * Time filter range (since/until) is inclusive.
     * If the limit equals to 0, effectively no limit is applied.
     *
     * @param projectId   project id
     * @param state       review state
     * @param revisionIds a set of revision IDs, with each we will get the reviews are associated
     * @param since       this time limit is interpreted based on the chosen state, for example: if only committed reviews are fetched, 'since' will concern the commited time
     * @param until       this time limit is interpreted based on the chosen state, for example: if only committed reviews are fetched, 'since' will concern the commited time
     * @param limit       maximum number of reviews to get
     * @return reviews
     */
    List<Review> getReviews(String projectId, ReviewState state, Iterable<String> revisionIds, Instant since, Instant until, Integer limit);

    /**
     * Get reviews across all projects with the given state and labels.
     * If assignedToMe or authoredByMe is null the default value of false would be set
     * if labels is empty, all reviews are returned
     * If state is null, all reviews are returned. Results are undefined for state {@link ReviewState#UNKNOWN}.
     * remove duplicates to form a single list of reviews to return (with other constraints applied on top of that).
     * Time filter range (since/until) is inclusive.
     * If the limit equals to 0, effectively no limit is applied.
     * if no projectType is provided the default mode of the system would be selected
     * @param projectTypes the project type for which the reviews would be returned
     * @param assignedToMe whether to return only reviews assigned to me
     * @param authoredByMe whether to return only reviews authored by me
     * @param labels      labels to apply, return only reviews that match all the labels
     * @param state       review state
     * @param since       this time limit is interpreted based on the chosen state, for example: if only committed reviews are fetched, 'since' will concern the commited time
     * @param until       this time limit is interpreted based on the chosen state, for example: if only committed reviews are fetched, 'since' will concern the commited time
     * @param limit       maximum number of reviews to get
     * @return reviews
     */
    List<Review> getReviews(Set<ProjectType> projectTypes, boolean assignedToMe, boolean authoredByMe, List<String> labels,  ReviewState state, Instant since, Instant until, Integer limit);

    /**
     * Create a review for changes from the given workspace.
     *
     * @param projectId     project id
     * @param workspaceId   workspace id
     * @param workspaceType workspace type
     * @param title         review title
     * @param description   review description
     * @param labels        review labels
     * @return new review
     */
    Review createReview(String projectId, String workspaceId, WorkspaceType workspaceType, String title, String description, List<String> labels);

    /**
     * Close a review. This is only valid if the review is open.
     *
     * @param projectId project id
     * @param reviewId  review id
     * @return updated review
     */
    Review closeReview(String projectId, String reviewId);

    /**
     * Reopen a review. This is only valid if the review is closed.
     *
     * @param projectId project id
     * @param reviewId  review id
     * @return updated review
     */
    Review reopenReview(String projectId, String reviewId);

    /**
     * Approve a review. This is only valid if the review is open.
     *
     * @param projectId project id
     * @param reviewId  review id
     * @return updated review
     */
    Review approveReview(String projectId, String reviewId);

    /**
     * Revoke approval of a review. This is only valid if the review
     * is open and the user has previously approved it.
     *
     * @param projectId project id
     * @param reviewId  review id
     * @return updated review
     */
    Review revokeReviewApproval(String projectId, String reviewId);

    /**
     * Reject a review. This is only valid if the review is open. It
     * may cause the review to be closed.
     *
     * @param projectId project id
     * @param reviewId  review id
     * @return updated review
     */
    Review rejectReview(String projectId, String reviewId);

    /**
     * Commit changes from a review. This is only valid if the
     * review is open and has sufficient approvals.
     *
     * @param projectId project id
     * @param reviewId  review id
     * @param message   commit message
     * @return committed review
     */
    Review commitReview(String projectId, String reviewId, String message);

    /**
     * Get the current update status of an open review. See {@link ReviewUpdateStatus}
     * for more information on the meaning of the return value. If the review is not
     * open, this method will throw an exception.
     *
     * @param projectId project id
     * @param reviewId  review id
     * @return update status
     */
    ReviewUpdateStatus getReviewUpdateStatus(String projectId, String reviewId);

    /**
     * Try to update an open review. That is, try to bring the review up to date
     * with the latest revision of the project. If the review is not open, this
     * method will throw an exception.
     * <p>
     * This method does not wait for the update to complete. It starts the update
     * and returns an initial status. Use {@link #getReviewUpdateStatus} for
     * subsequent status checks.
     * <p>
     * If an update is already in progress or if the review is already up to
     * date, this returns the current update status but is otherwise a no-op.
     * <p>
     * It is not always possible to update a review. In case the update fails,
     * the review will be left in the pre-update state.
     *
     * @param projectId project id
     * @param reviewId  review id
     * @return update status
     */
    ReviewUpdateStatus updateReview(String projectId, String reviewId);

    /**
     * Edit review, update the review  title, description and labels
     * @param projectId     project id
     * @param reviewId      review id
     * @param title         updated Title
     * @param description   description
     * @param labels        review labels
     * @return edited review
     */
    Review editReview(String projectId, String reviewId, String title, String description, List<String> labels);

    interface ReviewUpdateStatus
    {
        /**
         * Whether an update is currently in progress. If true, then other
         * status values may be null.
         *
         * @return whether an update is currently in progress
         */
        boolean isUpdateInProgress();

        /**
         * Get the revision the review is based on. Returns null if there is an
         * update in progress.
         *
         * @return base revision or null
         */
        String getBaseRevisionId();

        /**
         * Get the revision of the target of the review. Generally, this is the
         * latest project revision.
         *
         * @return target head revision
         */
        String getTargetRevisionId();
    }
}
