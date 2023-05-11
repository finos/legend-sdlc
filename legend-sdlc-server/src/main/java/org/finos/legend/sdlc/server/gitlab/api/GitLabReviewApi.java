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

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.review.Approval;
import org.finos.legend.sdlc.domain.model.review.Review;
import org.finos.legend.sdlc.domain.model.review.ReviewState;
import org.finos.legend.sdlc.server.domain.api.review.ReviewApi;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.GitLabConfiguration;
import org.finos.legend.sdlc.server.gitlab.GitLabProjectId;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.gitlab.tools.PagerTools;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.project.ProjectStructure;
import org.finos.legend.sdlc.server.tools.BackgroundTaskProcessor;
import org.finos.legend.sdlc.server.tools.CallUntil;
import org.finos.legend.sdlc.server.tools.StringTools;
import org.gitlab4j.api.Constants.MergeRequestScope;
import org.gitlab4j.api.Constants.StateEvent;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.MergeRequestApi;
import org.gitlab4j.api.RepositoryApi;
import org.gitlab4j.api.models.Branch;
import org.gitlab4j.api.models.Commit;
import org.gitlab4j.api.models.DiffRef;
import org.gitlab4j.api.models.MergeRequest;
import org.gitlab4j.api.models.MergeRequestFilter;
import org.gitlab4j.api.models.MergeRequestParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.ws.rs.core.Response.Status;

public class GitLabReviewApi extends GitLabApiWithFileAccess implements ReviewApi
{
    private static final Logger LOGGER = LoggerFactory.getLogger(GitLabReviewApi.class);

    @Inject
    public GitLabReviewApi(GitLabConfiguration gitLabConfiguration, GitLabUserContext userContext, BackgroundTaskProcessor backgroundTaskProcessor)
    {
        super(gitLabConfiguration, userContext, backgroundTaskProcessor);
    }

    @Override
    public List<Review> getReviews(String projectId, String patchReleaseVersion, ReviewState state, Iterable<String> revisionIds, BiPredicate<String, WorkspaceType> workspaceIdAndTypePredicate, Instant since, Instant until, Integer limit)
    {
       return getReviews(projectId, patchReleaseVersion, state, revisionIds, workspaceIdAndTypePredicate, since, until, limit);
    }

    @Override
    public List<Review> getReviews(boolean assignedToMe, boolean authoredByMe, List<String> labels, BiPredicate<String, WorkspaceType> workspaceIdAndTypePredicate, ReviewState state, Instant since, Instant until, Integer limit)
    {
        if (assignedToMe && authoredByMe)
        {
            throw new LegendSDLCServerException("assignedToMe and authoredByMe may not both be true", Status.BAD_REQUEST);
        }

        MergeRequestFilter mergeRequestFilter = withMergeRequestLabels(withMergeRequestFilters(new MergeRequestFilter(), state, since, until).withScope(assignedToMe ? MergeRequestScope.ASSIGNED_TO_ME : (authoredByMe ? MergeRequestScope.CREATED_BY_ME : MergeRequestScope.ALL)), labels);
        return addReviewFilters(getReviewStream(mergeRequestFilter), state, workspaceIdAndTypePredicate, since, until, limit).collect(Collectors.toList());
    }

    private Stream<Review> getReviewStream(MergeRequestFilter mergeRequestFilter)
    {
        MutableIntObjectMap<String> pIdTodefaultBranch = IntObjectMaps.mutable.empty();

        try
        {
            return PagerTools.stream(withRetries(() -> getGitLabApi().getMergeRequestApi().getMergeRequests(mergeRequestFilter, ITEMS_PER_PAGE)))
                    .filter(mr ->
                    {
                        String defaultBranch = pIdTodefaultBranch.getIfAbsentPutWithKey(mr.getProjectId(), pid -> getDefaultBranch(GitLabProjectId.newProjectId(this.getGitLabConfiguration().getProjectIdPrefix(), pid)));
                        return isReviewMergeRequest(mr, defaultBranch, null);
                    })
                      .map(mr -> fromGitLabMergeRequest(GitLabProjectId.newProjectId(this.getGitLabConfiguration().getProjectIdPrefix(), mr.getProjectId()).toString(), null, mr));
        }
        catch (Exception e)
        {
            throw buildException(e,
                () -> "User " + getCurrentUser() + " is not allowed to get reviews",
                null,
                () -> "Error getting reviews");
        }
    }

    private MergeRequestFilter withMergeRequestLabels(MergeRequestFilter mergeRequestFilter, List<String> labels)
    {
        if (labels != null && !labels.isEmpty())
        {
            mergeRequestFilter.setLabels(labels);
        }

        return mergeRequestFilter;
    }

    @Override
    public Review getReview(String projectId, String patchReleaseVersion, String reviewId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");
        try
        {
            GitLabProjectId gitLabProjectId = parseProjectId(projectId);
            MergeRequest mergeRequest = getReviewMergeRequest(getGitLabApi().getMergeRequestApi(), gitLabProjectId, patchReleaseVersion, reviewId);
            return fromGitLabMergeRequest(projectId, patchReleaseVersion, mergeRequest);
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
    public Review createReview(String projectId, String patchReleaseVersion, String workspaceId, WorkspaceType workspaceType, String title, String description, List<String> labels)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceId, "workspaceId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceType, "workspaceType may not be null");
        LegendSDLCServerException.validateNonNull(title, "title may not be null");
        LegendSDLCServerException.validateNonNull(description, "description may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);

        // checking if target branch exists or not
        if (patchReleaseVersion != null && !isPatchReleaseBranchPresent(gitLabProjectId, patchReleaseVersion))
        {
            throw new LegendSDLCServerException("Target patch release branch " + getPatchReleaseBranchName(patchReleaseVersion) + " for which you want to create review does not exist");
        }

        ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType = ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE;
        try
        {
            validateProjectConfigurationForCreateOrCommit(getProjectConfiguration(projectId, patchReleaseVersion, workspaceId, null, workspaceType, workspaceAccessType));
            String workspaceBranchName = getWorkspaceBranchName(workspaceId, workspaceType, workspaceAccessType, patchReleaseVersion);
            // TODO should we check for other merge requests for this workspace?
            MergeRequest mergeRequest = getGitLabApi().getMergeRequestApi().createMergeRequest(gitLabProjectId.getGitLabId(), workspaceBranchName, getSourceBranch(gitLabProjectId, patchReleaseVersion), title, description, null, null, (labels == null || labels.isEmpty()) ? null : labels.toArray(new String[0]), null, true);
            return fromGitLabMergeRequest(projectId, patchReleaseVersion, mergeRequest);
        }
        catch (Exception e)
        {
            throw buildException(e,
                () -> "User " + getCurrentUser() + " is not allowed to submit changes from " + workspaceType.getLabel() + " " + workspaceAccessType.getLabel() + " " + workspaceId + " in project " + projectId + " for review",
                () -> "Unknown " + workspaceType.getLabel() + " " + workspaceAccessType.getLabel() + " (" + workspaceId + ") or project (" + projectId + ")",
                () -> "Error submitting changes from " + workspaceType.getLabel() + " " + workspaceAccessType.getLabel() + " " + workspaceId + " in project " + projectId + " for review");
        }
    }

    @Override
    public Review closeReview(String projectId, String patchReleaseVersion, String reviewId)
    {
       return closeReview(projectId, patchReleaseVersion, reviewId);
    }

    @Override
    public Review reopenReview(String projectId, String patchReleaseVersion, String reviewId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        MergeRequestApi mergeRequestApi = getGitLabApi().getMergeRequestApi();
        MergeRequest mergeRequest = getReviewMergeRequest(mergeRequestApi, gitLabProjectId, patchReleaseVersion, reviewId);
        validateMergeRequestReviewState(mergeRequest, ReviewState.CLOSED);
        try
        {
            MergeRequest reopenMergeRequest = updateMergeRequestState(mergeRequestApi, gitLabProjectId, patchReleaseVersion, mergeRequest, StateEvent.REOPEN);
            return fromGitLabMergeRequest(projectId, patchReleaseVersion, reopenMergeRequest);
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
    public Review approveReview(String projectId, String patchReleaseVersion, String reviewId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        MergeRequestApi mergeRequestApi = getGitLabApi().getMergeRequestApi();
        MergeRequest mergeRequest = getReviewMergeRequest(mergeRequestApi, gitLabProjectId, patchReleaseVersion, reviewId);
        try
        {
            MergeRequest approvalMergeRequest = mergeRequestApi.approveMergeRequest(gitLabProjectId.getGitLabId(), mergeRequest.getIid(), mergeRequest.getSha());
            // The MergeRequest that comes back from the approveMergeRequest call is not adequate for
            // creating a Review, as most relevant properties are null. The only useful thing we get
            // from it is the last update time.
            mergeRequest.setUpdatedAt(approvalMergeRequest.getUpdatedAt());
            return fromGitLabMergeRequest(projectId, patchReleaseVersion, mergeRequest);
        }
        catch (GitLabApiException e)
        {
            switch (e.getHttpStatus())
            {
                // Status 401 (Unauthorized) can indicate either that the user is not properly authenticated or that the user is not a valid approver for the merge request.
                case 401:
                case 403:
                {
                    StringBuilder builder = new StringBuilder().append("User ").append(getCurrentUser()).append(" is not allowed to approve review ").append(reviewId).append(" in project ").append(projectId);
                    String url = mergeRequest.getWebUrl();
                    if (url != null)
                    {
                        builder.append(" (see ").append(url).append(" for more details)");
                    }
                    StringTools.appendThrowableMessageIfPresent(builder, e);
                    throw new LegendSDLCServerException("User " + getCurrentUser() + " is not allowed to approve review " + reviewId + " in project " + projectId, Status.FORBIDDEN, e);
                }
                case 404:
                {
                    throw new LegendSDLCServerException("Unknown review in project " + projectId + ": " + reviewId, Status.NOT_FOUND, e);
                }
                default:
                {
                    StringBuilder builder = new StringBuilder("Error approving review ").append(reviewId).append(" in project ").append(projectId);
                    StringTools.appendThrowableMessageIfPresent(builder, e);
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
            StringTools.appendThrowableMessageIfPresent(builder, e);
            throw new LegendSDLCServerException(builder.toString(), e);
        }
    }

    @Override
    public Review revokeReviewApproval(String projectId, String patchReleaseVersion, String reviewId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        MergeRequestApi mergeRequestApi = getGitLabApi().getMergeRequestApi();
        MergeRequest mergeRequest = getReviewMergeRequest(mergeRequestApi, gitLabProjectId, patchReleaseVersion, reviewId);
        try
        {
            MergeRequest revokeApprovalMergeRequest = mergeRequestApi.unapproveMergeRequest(gitLabProjectId.getGitLabId(), mergeRequest.getIid());
            // The MergeRequest that comes back from the unapproveMergeRequest call is not adequate
            // for creating a Review, as most relevant properties are null. The only useful thing we
            // get from it is the last update time.
            mergeRequest.setUpdatedAt(revokeApprovalMergeRequest.getUpdatedAt());
            return fromGitLabMergeRequest(projectId, patchReleaseVersion, mergeRequest);
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
    public Review rejectReview(String projectId, String patchReleaseVersion, String reviewId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        MergeRequestApi mergeRequestApi = getGitLabApi().getMergeRequestApi();
        MergeRequest mergeRequest = getReviewMergeRequest(mergeRequestApi, gitLabProjectId, patchReleaseVersion, reviewId);
        validateMergeRequestReviewState(mergeRequest, ReviewState.OPEN);
        try
        {
            MergeRequest rejectMergeRequest = updateMergeRequestState(mergeRequestApi, gitLabProjectId, patchReleaseVersion, mergeRequest, StateEvent.CLOSE);
            return fromGitLabMergeRequest(projectId, patchReleaseVersion, rejectMergeRequest);
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
    public Approval getReviewApproval(String projectId, String patchReleaseVersion, String reviewId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");
        try
        {
            GitLabProjectId gitLabProjectId = parseProjectId(projectId);
            MergeRequest mergeRequest = getReviewMergeRequestApprovals(getGitLabApi().getMergeRequestApi(), gitLabProjectId, reviewId);
            return fromGitLabMergeRequest(mergeRequest);
        }
        catch (Exception e)
        {
            throw buildException(e,
                () -> "User " + getCurrentUser() + " is not allowed to get approval details for review " + reviewId + " in project " + projectId,
                () -> "Unknown review (" + reviewId + ") or project (" + projectId + ")",
                () -> "Error getting approval details for review " + reviewId + " in project " + projectId);
        }
    }

    @Override
    public Review commitReview(String projectId, String patchReleasVersion, String reviewId, String message)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");
        LegendSDLCServerException.validateNonNull(message, "message may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        MergeRequestApi mergeRequestApi = getGitLabApi().getMergeRequestApi();

        // Find the merge request
        MergeRequest mergeRequest = getReviewMergeRequest(mergeRequestApi, gitLabProjectId, patchReleasVersion, reviewId);

        // Validate that the merge request is ready to be merged

        // Check that the state is open
        validateMergeRequestReviewState(mergeRequest, ReviewState.OPEN);

        // Check that there are no approvals still required
        Integer approvalsLeft = mergeRequest.getApprovalsLeft();
        if ((approvalsLeft != null) && (approvalsLeft > 0))
        {
            throw new LegendSDLCServerException("Review " + reviewId + " in project " + projectId + " still requires " + approvalsLeft + " approvals", Status.CONFLICT);
        }

        // Validate the project configuration
        WorkspaceInfo workspaceInfo = parseWorkspaceBranchName(mergeRequest.getSourceBranch(), patchReleasVersion);
        if (workspaceInfo == null)
        {
            throw new LegendSDLCServerException("Error committing review " + reviewId + " in project " + projectId + ": could not find workspace information");
        }
        ProjectConfiguration projectConfig = getProjectConfiguration(projectId, workspaceInfo, null);
        validateProjectConfigurationForCreateOrCommit(projectConfig);

        // TODO add more validations

        // Accept
        try
        {
            return fromGitLabMergeRequest(projectId, patchReleasVersion, mergeRequestApi.acceptMergeRequest(gitLabProjectId.getGitLabId(), mergeRequest.getIid(), message, true, null, null));
        }
        catch (GitLabApiException e)
        {
            switch (e.getHttpStatus())
            {
                case 401:
                case 403:
                {
                    // Status 401 (Unauthorized) can indicate either that the user is not properly authenticated or that the user does not have permission to merge (which can be for lack of entitlements or for other reasons)
                    // Status 403 (Forbidden) should not occur, but if it does it likely indicates the user does not have permission to merge
                    StringBuilder builder = new StringBuilder("User ").append(getCurrentUser()).append(" is not allowed to commit changes from review ").append(reviewId).append(" in project ").append(projectId);
                    String url = mergeRequest.getWebUrl();
                    if (url != null)
                    {
                        builder.append(" (see ").append(url).append(" for more details)");
                    }
                    StringTools.appendThrowableMessageIfPresent(builder, e);
                    throw new LegendSDLCServerException(builder.toString(), Status.FORBIDDEN, e);
                }
                case 404:
                {
                    // This shouldn't happen, as we already verified the merge request exists
                    throw new LegendSDLCServerException("Unknown review in project " + projectId + ": " + reviewId, Status.NOT_FOUND, e);
                }
                case 405:
                {
                    // Status 405 (Method Not Allowed) indicates the merge request could not be accepted because it's not in an appropriate state (i.e., work in progress, closed, pipeline pending completion, or failed while requiring success)
                    StringBuilder builder = new StringBuilder("Review ").append(reviewId).append(" in project ").append(projectId).append(" is not in a committable state");
                    String url = mergeRequest.getWebUrl();
                    if (url != null)
                    {
                        builder.append(" (see ").append(url).append(" for more details)");
                    }
                    StringTools.appendThrowableMessageIfPresent(builder, e);
                    throw new LegendSDLCServerException(builder.toString(), Status.CONFLICT, e);
                }
                case 406:
                {
                    // Status 406 (Not Acceptable) indicates the merge could not occur because of a conflict
                    StringBuilder builder = new StringBuilder("Could not commit review ").append(reviewId).append(" in project ").append(projectId).append(" because of a conflict");
                    String url = mergeRequest.getWebUrl();
                    if (url != null)
                    {
                        builder.append(" (see ").append(url).append(" for more details)");
                    }
                    StringTools.appendThrowableMessageIfPresent(builder, e);
                    throw new LegendSDLCServerException(builder.toString(), Status.CONFLICT, e);
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
    public ReviewUpdateStatus getReviewUpdateStatus(String projectId, String patchReleaseVersion, String reviewId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        GitLabApi gitLabApi = getGitLabApi();
        MergeRequest mergeRequest = getReviewMergeRequest(gitLabApi.getMergeRequestApi(), gitLabProjectId, patchReleaseVersion, reviewId);
        if (!(isOpen(mergeRequest) || isLocked(mergeRequest)))
        {
            throw new LegendSDLCServerException("Cannot get update status for review " + mergeRequest.getIid() + " in project " + projectId + ": state is " + getReviewState(mergeRequest), Status.CONFLICT);
        }
        return getReviewUpdateStatus(gitLabProjectId, gitLabApi, mergeRequest);
    }

    @Override
    public ReviewUpdateStatus updateReview(String projectId, String patchReleaseVersion, String reviewId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        GitLabApi gitLabApi = getGitLabApi();
        MergeRequestApi mergeRequestApi = gitLabApi.getMergeRequestApi();

        // Check the current status of the review
        MergeRequest initialMergeRequest = getReviewMergeRequest(mergeRequestApi, gitLabProjectId, patchReleaseVersion, reviewId);
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

    @Override
    public Review editReview(String projectId, String patchReleaseVersion, String reviewId, String title, String description, List<String> labels)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");
        LegendSDLCServerException.validateNonNull(title, "title may not be null");
        LegendSDLCServerException.validateNonNull(description, "description may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        GitLabApi gitLabApi = getGitLabApi();
        MergeRequestApi mergeRequestApi = gitLabApi.getMergeRequestApi();

        MergeRequest mergeRequest = getReviewMergeRequest(mergeRequestApi, gitLabProjectId, patchReleaseVersion, reviewId);
        if (!isOpen(mergeRequest))
        {
            throw new LegendSDLCServerException("Only open reviews can be edited: state of review " + mergeRequest.getIid() + " in project " + gitLabProjectId.toString() + " is " + getReviewState(mergeRequest));
        }
        try
        {
            MergeRequestParams mergeRequestParams = new MergeRequestParams().withTitle(title).withDescription(description);
            if (labels != null)
            {
                mergeRequestParams.withLabels(labels);
            }

            MergeRequest editedRequest = mergeRequestApi.updateMergeRequest(gitLabProjectId.getGitLabId(), mergeRequest.getIid(), mergeRequestParams);
            return fromGitLabMergeRequest(projectId, patchReleaseVersion, editedRequest);
        }
        catch (Exception e)
        {
            throw buildException(e,
                () -> "User " + getCurrentUser() + " is not allowed to edit review " + reviewId + " in project " + projectId,
                () -> "Unknown review in project " + projectId + ": " + reviewId,
                () -> "Error editing review " + reviewId + " in project " + projectId);

        }
    }

    private void validateProjectConfigurationForCreateOrCommit(ProjectConfiguration projectConfiguration)
    {
        if (projectConfiguration != null)
        {
            List<ProjectDependency> dependencies = projectConfiguration.getProjectDependencies();
            if ((dependencies != null) && !dependencies.isEmpty())
            {
                MutableList<ProjectDependency> invalidDependencies = Iterate.reject(dependencies, ProjectStructure::isProperProjectDependency, Lists.mutable.empty());
                if (invalidDependencies.notEmpty())
                {
                    throw new LegendSDLCServerException(invalidDependencies.makeString("Cannot create a review with the following dependencies: ", ", ", ""), Status.CONFLICT);
                }
            }
        }
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
}
