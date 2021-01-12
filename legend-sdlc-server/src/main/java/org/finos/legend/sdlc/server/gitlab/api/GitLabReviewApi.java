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

package org.finos.legend.sdlc.server.gitlab.api;

import org.eclipse.collections.api.factory.Sets;
import org.finos.legend.sdlc.domain.model.review.Review;
import org.finos.legend.sdlc.domain.model.review.ReviewState;
import org.finos.legend.sdlc.domain.model.user.User;
import org.finos.legend.sdlc.server.domain.api.review.ReviewApi;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.GitLabProjectId;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.gitlab.tools.PagerTools;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.tools.CallUntil;
import org.finos.legend.sdlc.server.tools.StringTools;
import org.gitlab4j.api.CommitsApi;
import org.gitlab4j.api.Constants.MergeRequestState;
import org.gitlab4j.api.Constants.StateEvent;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.MergeRequestApi;
import org.gitlab4j.api.RepositoryApi;
import org.gitlab4j.api.models.AbstractUser;
import org.gitlab4j.api.models.Branch;
import org.gitlab4j.api.models.Commit;
import org.gitlab4j.api.models.DiffRef;
import org.gitlab4j.api.models.MergeRequest;
import org.gitlab4j.api.models.MergeRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.ws.rs.core.Response.Status;

public class GitLabReviewApi extends BaseGitLabApi implements ReviewApi
{
    private static final Logger LOGGER = LoggerFactory.getLogger(GitLabReviewApi.class);

    @Inject
    public GitLabReviewApi(GitLabUserContext userContext)
    {
        super(userContext);
    }

    @Override
    public List<Review> getReviews(String projectId, ReviewState state, Iterable<String> revisionIds, Instant since, Instant until, Integer limit)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        Set<String> revisionIdSet;
        if (revisionIds == null)
        {
            revisionIdSet = Collections.emptySet();
        }
        else if (revisionIds instanceof Set)
        {
            revisionIdSet = (Set<String>) revisionIds;
        }
        else
        {
            revisionIdSet = Sets.mutable.withAll(revisionIds);
        }
        MergeRequestState mergeRequestState = getMergeRequestState(state);
        Stream<MergeRequest> mergeRequestStream;
        try
        {
            GitLabProjectId gitLabProjectId = parseProjectId(projectId);
            if (!revisionIdSet.isEmpty())
            {
                // TODO: we might want to do this differently since the number of revision IDs can be huge
                // we can have a threshold for which we change our strategy to  to make a single call for
                // merge requests by the other criteria and then filter by revisionIds.
                Set<Integer> mergeRequestIds = Sets.mutable.empty();
                CommitsApi commitsApi = getGitLabApi(gitLabProjectId.getGitLabMode()).getCommitsApi();
                // Combine all MRs associated with each revision
                mergeRequestStream = revisionIdSet.stream()
                        .flatMap(revisionId ->
                        {
                            try
                            {
                                return PagerTools.stream(withRetries(() -> commitsApi.getMergeRequests(gitLabProjectId.getGitLabId(), revisionId, ITEMS_PER_PAGE)));
                            }
                            catch (Exception e)
                            {
                                throw buildException(e,
                                        () -> "User " + getCurrentUser() + " is not allowed to get reviews associated with revision " + revisionId + " for project " + projectId,
                                        () -> "Unknown revision (" + revisionId + ") or project (" + projectId + ")",
                                        () -> "Error getting reviews associated with revision " + revisionId + " for project " + projectId);
                            }
                        })
                        .filter(mr -> mergeRequestIds.add(mr.getIid())); // remove duplicates
                if (mergeRequestState != MergeRequestState.ALL)
                {
                    String mergeRequestStateString = mergeRequestState.toString();
                    mergeRequestStream = mergeRequestStream.filter(mr -> mergeRequestStateString.equalsIgnoreCase(mr.getState()));
                }
            }
            else
            {
                // if no revision ID is specified we will use the default merge request API from Gitlab to take advantage of the filter
                MergeRequestFilter mergeRequestFilter = new MergeRequestFilter()
                        .withProjectId(gitLabProjectId.getGitLabId())
                        .withState(mergeRequestState);
                if ((since != null) && (state != null))
                {
                    switch (state)
                    {
                        case CLOSED:
                        case COMMITTED:
                        {
                            mergeRequestFilter.setUpdatedAfter(Date.from(since));
                            break;
                        }
                        case OPEN:
                        {
                            mergeRequestFilter.setCreatedAfter(Date.from(since));
                            break;
                        }
                        default:
                        {
                            // no filter can be created for other states
                        }
                    }
                }
                if (until != null)
                {
                    mergeRequestFilter.setCreatedBefore(Date.from(until));
                }
                mergeRequestStream = PagerTools.stream(withRetries(() -> getGitLabApi(gitLabProjectId.getGitLabMode()).getMergeRequestApi().getMergeRequests(mergeRequestFilter, ITEMS_PER_PAGE)));
            }
            Stream<Review> stream = mergeRequestStream.filter(BaseGitLabApi::isReviewMergeRequest).map(mr -> fromGitLabMergeRequest(projectId, mr));
            Predicate<Review> timePredicate = getTimePredicate(state, since, until);
            if (timePredicate != null)
            {
                stream = stream.filter(timePredicate);
            }
            boolean limited = (limit != null) && (limit > 0);
            if (limited)
            {
                stream = stream.limit(limit);
            }

            return stream.collect(Collectors.toList());
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to get reviews for project " + projectId + ((state == null) ? "" : (" with state " + state)),
                    () -> "Unknown project (" + projectId + ")",
                    () -> "Error getting reviews for project " + projectId + ((state == null) ? "" : (" with state " + state)));
        }
    }

    private Predicate<Review> getTimePredicate(ReviewState state, Instant since, Instant until)
    {
        if ((since == null) && (until == null))
        {
            return null;
        }

        switch ((state == null) ? ReviewState.UNKNOWN : state)
        {
            case OPEN:
            {
                return review -> isCreatedAtWithinBounds(review, since, until) || isUpdatedAtWithinBounds(review, since, until);
            }
            case CLOSED:
            {
                return review -> isClosedAtWithinBounds(review, since, until) || isUpdatedAtWithinBounds(review, since, until);
            }
            case COMMITTED:
            {
                return review -> isCommittedAtWithinBounds(review, since, until) || isUpdatedAtWithinBounds(review, since, until);
            }
            default:
            {
                return review ->
                {
                    if (isUpdatedAtWithinBounds(review, since, until))
                    {
                        return true;
                    }
                    if (review.getState() == null)
                    {
                        LOGGER.warn("State missing for review {} in project {}; cannot filter based on time", review.getId(), review.getProjectId());
                        return false;
                    }
                    switch (review.getState())
                    {
                        case OPEN:
                        case UNKNOWN:
                        {
                            return isCreatedAtWithinBounds(review, since, until);
                        }
                        case COMMITTED:
                        {
                            return isCommittedAtWithinBounds(review, since, until);
                        }
                        case CLOSED:
                        {
                            return isClosedAtWithinBounds(review, since, until);
                        }
                        default:
                        {
                            LOGGER.warn("Unhandled state for review {} in project {}: {}; cannot filter based on time", review.getId(), review.getState(), review.getProjectId());
                            return false;
                        }
                    }
                };
            }
        }
    }

    @Override
    public Review getReview(String projectId, String reviewId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");
        try
        {
            GitLabProjectId gitLabProjectId = parseProjectId(projectId);
            MergeRequest mergeRequest = getReviewMergeRequest(getGitLabApi(gitLabProjectId.getGitLabMode()).getMergeRequestApi(), gitLabProjectId, reviewId);
            return fromGitLabMergeRequest(projectId, mergeRequest);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to get review " + reviewId + " for project " + projectId,
                    () -> "Unknown review (" + reviewId + ") or project (" + projectId + ")",
                    () -> "Error getting review " + reviewId + " for project " + projectId);
        }
    }

    @Override
    public Review createReview(String projectId, String workspaceId, String title, String description)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceId, "workspaceId may not be null");
        LegendSDLCServerException.validateNonNull(title, "title may not be null");
        LegendSDLCServerException.validateNonNull(description, "description may not be null");
        try
        {
            GitLabProjectId gitLabProjectId = parseProjectId(projectId);
            String workspaceBranchName = getUserWorkspaceBranchName(workspaceId, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE);
            // TODO should we check for other merge requests for this workspace?
            MergeRequest mergeRequest = getGitLabApi(gitLabProjectId.getGitLabMode()).getMergeRequestApi().createMergeRequest(gitLabProjectId.getGitLabId(), workspaceBranchName, MASTER_BRANCH, title, description, null, null, null, null, true);
            return fromGitLabMergeRequest(projectId, mergeRequest);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to submit changes from workspace " + workspaceId + " in project " + projectId + " for review",
                    () -> "Unknown workspace (" + workspaceId + ") or project (" + projectId + ")",
                    () -> "Error submitting changes from workspace " + workspaceId + " in project " + projectId + " for review");
        }
    }

    @Override
    public Review closeReview(String projectId, String reviewId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        MergeRequestApi mergeRequestApi = getGitLabApi(gitLabProjectId.getGitLabMode()).getMergeRequestApi();
        MergeRequest mergeRequest = getReviewMergeRequest(mergeRequestApi, gitLabProjectId, reviewId);
        validateMergeRequestReviewState(mergeRequest, ReviewState.OPEN);
        try
        {
            MergeRequest closeMergeRequest = updateMergeRequestState(mergeRequestApi, gitLabProjectId, mergeRequest, StateEvent.CLOSE);
            return fromGitLabMergeRequest(projectId, closeMergeRequest);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to close review " + reviewId + " in project " + projectId,
                    () -> "Unknown review in project " + projectId + ": " + reviewId,
                    () -> "Error closing review " + reviewId + " in project " + projectId);
        }
    }

    @Override
    public Review reopenReview(String projectId, String reviewId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        MergeRequestApi mergeRequestApi = getGitLabApi(gitLabProjectId.getGitLabMode()).getMergeRequestApi();
        MergeRequest mergeRequest = getReviewMergeRequest(mergeRequestApi, gitLabProjectId, reviewId);
        validateMergeRequestReviewState(mergeRequest, ReviewState.CLOSED);
        try
        {
            MergeRequest reopenMergeRequest = updateMergeRequestState(mergeRequestApi, gitLabProjectId, mergeRequest, StateEvent.REOPEN);
            return fromGitLabMergeRequest(projectId, reopenMergeRequest);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to reopen review " + reviewId + " in project " + projectId,
                    () -> "Unknown review in project " + projectId + ": " + reviewId,
                    () -> "Error reopening review " + reviewId + " in project " + projectId);
        }
    }

    @Override
    public Review approveReview(String projectId, String reviewId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        MergeRequestApi mergeRequestApi = getGitLabApi(gitLabProjectId.getGitLabMode()).getMergeRequestApi();
        MergeRequest mergeRequest = getReviewMergeRequest(mergeRequestApi, gitLabProjectId, reviewId);
        try
        {
            MergeRequest approvalMergeRequest = mergeRequestApi.approveMergeRequest(gitLabProjectId.getGitLabId(), mergeRequest.getIid(), mergeRequest.getSha());
            // The MergeRequest that comes back from the approveMergeRequest call is not adequate for
            // creating a Review, as most relevant properties are null. The only useful thing we get
            // from it is the last update time.
            mergeRequest.setUpdatedAt(approvalMergeRequest.getUpdatedAt());
            return fromGitLabMergeRequest(projectId, mergeRequest);
        }
        catch (GitLabApiException e)
        {
            switch (e.getHttpStatus())
            {
                // Status 401 (Unauthorized) can indicate either that the user is not properly authenticated or that the user is not a valid approver for the merge request.
                case 401:
                case 403:
                {
                    throw new LegendSDLCServerException("User " + getCurrentUser() + " is not allowed to approve review " + reviewId + " in project " + projectId, Status.FORBIDDEN, e);
                }
                case 404:
                {
                    throw new LegendSDLCServerException("Unknown review in project " + projectId + ": " + reviewId, Status.NOT_FOUND, e);
                }
                default:
                {
                    StringBuilder builder = new StringBuilder("Error approving review ").append(reviewId).append(" in project ").append(projectId);
                    String eMessage = e.getMessage();
                    if (eMessage != null)
                    {
                        builder.append(": ").append(eMessage);
                    }
                    throw new LegendSDLCServerException(builder.toString(), e);
                }
            }
        }
        catch (LegendSDLCServerException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            StringBuilder builder = new StringBuilder("Error approving review ").append(reviewId).append(" in project ").append(projectId);
            String eMessage = e.getMessage();
            if (eMessage != null)
            {
                builder.append(": ").append(eMessage);
            }
            throw new LegendSDLCServerException(builder.toString(), e);
        }
    }

    @Override
    public Review revokeReviewApproval(String projectId, String reviewId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        MergeRequestApi mergeRequestApi = getGitLabApi(gitLabProjectId.getGitLabMode()).getMergeRequestApi();
        MergeRequest mergeRequest = getReviewMergeRequest(mergeRequestApi, gitLabProjectId, reviewId);
        try
        {
            MergeRequest revokeApprovalMergeRequest = mergeRequestApi.unapproveMergeRequest(gitLabProjectId.getGitLabId(), mergeRequest.getIid());
            // The MergeRequest that comes back from the unapproveMergeRequest call is not adequate
            // for creating a Review, as most relevant properties are null. The only useful thing we
            // get from it is the last update time.
            mergeRequest.setUpdatedAt(revokeApprovalMergeRequest.getUpdatedAt());
            return fromGitLabMergeRequest(projectId, mergeRequest);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to revoke approval of review " + reviewId + " in project " + projectId,
                    () -> "Unknown review in project " + projectId + ": " + reviewId,
                    () -> "Error revoking review approval " + reviewId + " in project " + projectId);
        }
    }

    @Override
    public Review rejectReview(String projectId, String reviewId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        MergeRequestApi mergeRequestApi = getGitLabApi(gitLabProjectId.getGitLabMode()).getMergeRequestApi();
        MergeRequest mergeRequest = getReviewMergeRequest(mergeRequestApi, gitLabProjectId, reviewId);
        validateMergeRequestReviewState(mergeRequest, ReviewState.OPEN);
        try
        {
            MergeRequest rejectMergeRequest = updateMergeRequestState(mergeRequestApi, gitLabProjectId, mergeRequest, StateEvent.CLOSE);
            return fromGitLabMergeRequest(projectId, rejectMergeRequest);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to reject review " + reviewId + " in project " + projectId,
                    () -> "Unknown review in project " + projectId + ": " + reviewId,
                    () -> "Error rejecting review " + reviewId + " in project " + projectId);
        }
    }

    @Override
    public Review commitReview(String projectId, String reviewId, String message)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");
        LegendSDLCServerException.validateNonNull(message, "message may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        MergeRequestApi mergeRequestApi = getGitLabApi(gitLabProjectId.getGitLabMode()).getMergeRequestApi();

        // Find the merge request
        MergeRequest mergeRequest = getReviewMergeRequest(mergeRequestApi, gitLabProjectId, reviewId);

        // Validate that the merge request is ready to be merged

        // Check that the state is open
        validateMergeRequestReviewState(mergeRequest, ReviewState.OPEN);

        // Check that there are no approvals still required
        Integer approvalsLeft = mergeRequest.getApprovalsLeft();
        if ((approvalsLeft != null) && (approvalsLeft > 0))
        {
            throw new LegendSDLCServerException("Review " + reviewId + " in project " + projectId + " still requires " + approvalsLeft + " approvals", Status.CONFLICT);
        }

        // TODO add more validations

        // Accept
        try
        {
            return fromGitLabMergeRequest(projectId, mergeRequestApi.acceptMergeRequest(gitLabProjectId.getGitLabId(), mergeRequest.getIid(), message, true, null, null));
        }
        catch (GitLabApiException e)
        {
            switch (e.getHttpStatus())
            {
                // Status 401 (Unauthorized) can indicate either that the user is not properly authenticated or that the user is not allowed to merge the request.
                case 401:
                case 403:
                {
                    // This shouldn't happen, but just in case ...
                    throw new LegendSDLCServerException("User " + getCurrentUser() + " is not allowed to commit changes from review " + reviewId + " in project " + projectId, Status.FORBIDDEN, e);
                }
                case 404:
                {
                    // This shouldn't happen, as we already verified the review exists
                    throw new LegendSDLCServerException("Unknown review in project " + projectId + ": " + reviewId, Status.NOT_FOUND, e);
                }
                case 405:
                {
                    // Status 405 (Method Not Allowed) indicates the merge request could not be accepted because it's not in an appropriate state (i.e., work in progress, closed, pipeline pending completion, or failed while requiring success)
                    throw new LegendSDLCServerException("Review " + reviewId + " in project " + projectId + " is not in a committable state; for more details, see: " + mergeRequest.getWebUrl(), Status.CONFLICT, e);
                }
                case 406:
                {
                    // Status 406 (Not Acceptable) indicates the merge could not occur because of a conflict
                    throw new LegendSDLCServerException("Could not commit review " + reviewId + " in project " + projectId + " because of a conflict; for more details, see: " + mergeRequest.getWebUrl(), Status.CONFLICT, e);
                }
                default:
                {
                    StringBuilder builder = new StringBuilder("Error committing changes from review ").append(reviewId).append(" to project ").append(projectId);
                    StringTools.appendThrowableMessageIfPresent(builder, e);
                    LOGGER.warn("Unexpected response status committing changes from review {} to project {}; status {}; message: {}", reviewId, projectId, e.getHttpStatus(), e.getMessage());
                    throw new LegendSDLCServerException(builder.toString(), e);
                }
            }
        }
        catch (LegendSDLCServerException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            StringBuilder builder = new StringBuilder("Error committing changes from review ").append(reviewId).append(" to project ").append(projectId);
            StringTools.appendThrowableMessageIfPresent(builder, e);
            throw new LegendSDLCServerException(builder.toString(), e);
        }
    }

    @Override
    public ReviewUpdateStatus getReviewUpdateStatus(String projectId, String reviewId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        GitLabApi gitLabApi = getGitLabApi(gitLabProjectId.getGitLabMode());
        MergeRequest mergeRequest = getReviewMergeRequest(gitLabApi.getMergeRequestApi(), gitLabProjectId, reviewId);
        if (!(isOpen(mergeRequest) || isLocked(mergeRequest)))
        {
            throw new LegendSDLCServerException("Cannot get update status for review " + mergeRequest.getIid() + " in project " + projectId + ": state is " + getReviewState(mergeRequest), Status.CONFLICT);
        }
        return getReviewUpdateStatus(gitLabProjectId, gitLabApi, mergeRequest);
    }

    @Override
    public ReviewUpdateStatus updateReview(String projectId, String reviewId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        GitLabApi gitLabApi = getGitLabApi(gitLabProjectId.getGitLabMode());
        MergeRequestApi mergeRequestApi = gitLabApi.getMergeRequestApi();

        // Check the current status of the review
        MergeRequest initialMergeRequest = getReviewMergeRequest(mergeRequestApi, gitLabProjectId, reviewId);
        if (!isOpen(initialMergeRequest))
        {
            throw new LegendSDLCServerException("Only open reviews can be updated: state of review " + initialMergeRequest.getIid() + " in project " + projectId + " is " + getReviewState(initialMergeRequest), Status.CONFLICT);
        }
        ReviewUpdateStatus updateStatus = getReviewUpdateStatus(gitLabProjectId, gitLabApi, initialMergeRequest);
        if (updateStatus.isUpdateInProgress() || ((updateStatus.getBaseRevisionId() != null) && updateStatus.getBaseRevisionId().equals(updateStatus.getTargetRevisionId())))
        {
            // Update in progress or already up to date: no need to update
            return updateStatus;
        }

        // Start update attempt
        MergeRequest rebaseMergeRequest;
        try
        {
            CallUntil<MergeRequest, GitLabApiException> callUntil = CallUntil.callUntil(
                    () -> withRetries(() -> mergeRequestApi.rebaseMergeRequest(gitLabProjectId.getGitLabId(), initialMergeRequest.getIid())),
                    MergeRequest::getRebaseInProgress,
                    3,
                    500L);
            if (!callUntil.succeeded())
            {
                throw new LegendSDLCServerException("Failed to start update for review " + reviewId + " in project " + projectId);
            }
            rebaseMergeRequest = callUntil.getResult();
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to update review " + reviewId + " in project " + projectId,
                    () -> "Unknown review in project " + projectId + ": " + reviewId,
                    () -> "Error updating review " + reviewId + " in project " + projectId);
        }
        return getReviewUpdateStatus(gitLabProjectId, gitLabApi, rebaseMergeRequest);
    }

    // assumes the merge request has rebase info
    private ReviewUpdateStatus getReviewUpdateStatus(GitLabProjectId projectId, GitLabApi gitLabApi, MergeRequest mergeRequest)
    {
        boolean updateInProgress = (mergeRequest.getRebaseInProgress() != null) && mergeRequest.getRebaseInProgress();
        String baseRevisionId = updateInProgress ? null : getMergeRequestBaseRevision(projectId, gitLabApi, mergeRequest);
        String targetRevisionId = updateInProgress ? null : getMergeRequestTargetRevision(projectId, gitLabApi, mergeRequest);
        return new ReviewUpdateStatus()
        {
            @Override
            public boolean isUpdateInProgress()
            {
                return updateInProgress;
            }

            @Override
            public String getBaseRevisionId()
            {
                return baseRevisionId;
            }

            @Override
            public String getTargetRevisionId()
            {
                return targetRevisionId;
            }
        };
    }

    private String getMergeRequestBaseRevision(GitLabProjectId projectId, GitLabApi gitLabApi, MergeRequest mergeRequest)
    {
        DiffRef diffRef = mergeRequest.getDiffRefs();
        if (diffRef != null)
        {
            String baseRevisionId = diffRef.getBaseSha();
            if (baseRevisionId != null)
            {
                return baseRevisionId;
            }
        }

        RepositoryApi repositoryApi = gitLabApi.getRepositoryApi();
        String sourceBranchName = mergeRequest.getSourceBranch();
        String targetBranchName = mergeRequest.getTargetBranch();
        Commit mergeBase;
        try
        {
            mergeBase = withRetries(() -> repositoryApi.getMergeBase(projectId.getGitLabId(), Arrays.asList(sourceBranchName, targetBranchName)));
        }
        catch (Exception e)
        {
            LOGGER.error("Error getting merge base for merge request {} in project {} (source branch: {}, target branch: {})", mergeRequest.getIid(), projectId, sourceBranchName, targetBranchName);
            StringBuilder builder = new StringBuilder("Error getting base revision for review ").append(mergeRequest.getIid()).append(" for project ").append(projectId);
            StringTools.appendThrowableMessageIfPresent(builder, e);
            throw new LegendSDLCServerException(builder.toString(), e);
        }
        if ((mergeBase == null) || (mergeBase.getId() == null))
        {
            LOGGER.error("Error getting merge base for merge request {} in project {} (source branch: {}, target branch: {}): {}", mergeRequest.getIid(), projectId, sourceBranchName, targetBranchName, mergeBase);
            throw new LegendSDLCServerException("Error getting base revision for review " + mergeRequest.getIid() + " for project " + projectId);
        }
        return mergeBase.getId();
    }

    private String getMergeRequestTargetRevision(GitLabProjectId projectId, GitLabApi gitLabApi, MergeRequest mergeRequest)
    {
        RepositoryApi repositoryApi = gitLabApi.getRepositoryApi();
        Branch targetBranch;
        try
        {
            targetBranch = withRetries(() -> repositoryApi.getBranch(projectId.getGitLabId(), mergeRequest.getTargetBranch()));
        }
        catch (Exception e)
        {
            LOGGER.error("Error getting target branch head for merge request {} in project {} (target branch: {})", mergeRequest.getIid(), projectId, mergeRequest.getTargetBranch(), e);
            StringBuilder builder = new StringBuilder("Error getting target revision for review ").append(mergeRequest.getIid()).append(" for project ").append(projectId);
            StringTools.appendThrowableMessageIfPresent(builder, e);
            throw new LegendSDLCServerException(builder.toString(), e);
        }
        Commit targetHead = targetBranch.getCommit();
        if ((targetHead == null) || (targetHead.getId() == null))
        {
            LOGGER.error("Error getting target branch head for merge request {} in project {} (target branch: {}): {}", mergeRequest.getIid(), projectId, mergeRequest.getTargetBranch(), targetHead);
            throw new LegendSDLCServerException("Error getting target revision for review " + mergeRequest.getIid() + " for project");
        }
        return targetHead.getId();
    }

    private void validateMergeRequestReviewState(MergeRequest mergeRequest, ReviewState expectedState)
    {
        ReviewState actualState = getReviewState(mergeRequest);
        if (expectedState != actualState)
        {
            throw new LegendSDLCServerException("Review is not " + expectedState.name().toLowerCase() + " (state: " + actualState.name().toLowerCase() + ")", Status.CONFLICT);
        }
    }

    private MergeRequest updateMergeRequestState(MergeRequestApi mergeRequestApi, GitLabProjectId projectId, MergeRequest mergeRequest, StateEvent stateEvent) throws GitLabApiException
    {
        return mergeRequestApi.updateMergeRequest(projectId.getGitLabId(), mergeRequest.getIid(), null, null, null, null, stateEvent, null, null, null, null, null, null);
    }

    private boolean isCreatedAtWithinBounds(Review review, Instant lowerBound, Instant upperBound)
    {
        return isReviewTimeWithinBounds(review, Review::getCreatedAt, lowerBound, upperBound, "Created at");
    }

    private boolean isClosedAtWithinBounds(Review review, Instant lowerBound, Instant upperBound)
    {
        return isReviewTimeWithinBounds(review, Review::getClosedAt, lowerBound, upperBound, "Closed at");
    }

    private boolean isCommittedAtWithinBounds(Review review, Instant lowerBound, Instant upperBound)
    {
        return isReviewTimeWithinBounds(review, Review::getCommittedAt, lowerBound, upperBound, "Committed at");
    }

    private boolean isUpdatedAtWithinBounds(Review review, Instant lowerBound, Instant upperBound)
    {
        return isReviewTimeWithinBounds(review, Review::getLastUpdatedAt, lowerBound, upperBound, "Last updated at");
    }

    private boolean isReviewTimeWithinBounds(Review review, Function<? super Review, ? extends Instant> function, Instant lowerBound, Instant upperBound, String descriptionForLogging)
    {
        Instant time = function.apply(review);
        if (time == null)
        {
            if (descriptionForLogging != null)
            {
                LOGGER.warn("{} time missing for review {} in project {}", descriptionForLogging, review.getId(), review.getProjectId());
            }
            return false;
        }
        return isInstantWithinBounds(time, lowerBound, upperBound);
    }

    private static boolean isInstantWithinBounds(Instant time, Instant lowerBound, Instant upperBound)
    {
        return ((lowerBound == null) || lowerBound.compareTo(time) <= 0) && ((upperBound == null) || upperBound.compareTo(time) >= 0);
    }

    private static MergeRequestState getMergeRequestState(ReviewState state)
    {
        if (state == null)
        {
            return MergeRequestState.ALL;
        }
        switch (state)
        {
            case OPEN:
            {
                return MergeRequestState.OPENED;
            }
            case COMMITTED:
            {
                return MergeRequestState.MERGED;
            }
            case CLOSED:
            {
                return MergeRequestState.CLOSED;
            }
            case UNKNOWN:
            {
                return MergeRequestState.ALL;
            }
            default:
            {
                throw new IllegalArgumentException("Unknown review state: " + state);
            }
        }
    }

    private static Review fromGitLabMergeRequest(String projectId, MergeRequest mergeRequest)
    {
        if (mergeRequest == null)
        {
            return null;
        }

        String sourceBranchName = mergeRequest.getSourceBranch();
        if (!isWorkspaceBranchName(sourceBranchName, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE))
        {
            return null;
        }
        String workspaceId = getWorkspaceIdFromWorkspaceBranchName(sourceBranchName, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE);

        return newReview(mergeRequest.getIid(), projectId, workspaceId, mergeRequest.getTitle(), mergeRequest.getDescription(), mergeRequest.getCreatedAt(), mergeRequest.getUpdatedAt(), mergeRequest.getClosedAt(), mergeRequest.getMergedAt(), mergeRequest.getState(), mergeRequest.getAuthor(), mergeRequest.getMergeCommitSha(), mergeRequest.getWebUrl());
    }

    private static Review newReview(Integer reviewId, String projectId, String workspaceId, String title, String description, Date createdAt, Date lastUpdatedAt, Date closedAt, Date committedAt, String reviewState, AbstractUser<?> author, String commitRevisionId, String webURL)
    {
        return newReview(toStringIfNotNull(reviewId), projectId, workspaceId, title, description, toInstantIfNotNull(createdAt), toInstantIfNotNull(lastUpdatedAt), toInstantIfNotNull(closedAt), toInstantIfNotNull(committedAt), getReviewState(reviewState), fromGitLabAbstractUser(author), commitRevisionId, webURL);
    }

    private static Review newReview(String reviewId, String projectId, String workspaceId, String title, String description, Instant createdAt, Instant lastUpdatedAt, Instant closedAt, Instant committedAt, ReviewState reviewState, User author, String commitRevisionId, String webURL)
    {
        return new Review()
        {
            @Override
            public String getId()
            {
                return reviewId;
            }

            @Override
            public String getProjectId()
            {
                return projectId;
            }

            @Override
            public String getWorkspaceId()
            {
                return workspaceId;
            }

            @Override
            public String getTitle()
            {
                return title;
            }

            @Override
            public String getDescription()
            {
                return description;
            }

            @Override
            public Instant getCreatedAt()
            {
                return createdAt;
            }

            @Override
            public Instant getLastUpdatedAt()
            {
                return lastUpdatedAt;
            }

            @Override
            public Instant getClosedAt()
            {
                return closedAt;
            }

            @Override
            public Instant getCommittedAt()
            {
                return committedAt;
            }

            @Override
            public ReviewState getState()
            {
                return reviewState;
            }

            @Override
            public User getAuthor()
            {
                return author;
            }

            @Override
            public String getCommitRevisionId()
            {
                return commitRevisionId;
            }

            @Override
            public String getWebURL()
            {
                return webURL;
            }
        };
    }
}
