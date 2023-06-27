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
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import org.eclipse.collections.impl.factory.primitive.IntSets;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.review.Approval;
import org.finos.legend.sdlc.domain.model.review.Review;
import org.finos.legend.sdlc.domain.model.review.ReviewState;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.domain.api.review.ReviewApi;
import org.finos.legend.sdlc.server.domain.api.project.SourceSpecification;
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
import org.gitlab4j.api.CommitsApi;
import org.gitlab4j.api.Constants;
import org.gitlab4j.api.Constants.MergeRequestScope;
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
import org.gitlab4j.api.models.MergeRequestParams;
import org.finos.legend.sdlc.domain.model.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
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
    public List<Review> getReviews(String projectId, VersionId patchReleaseVersionId, ReviewState state, Iterable<String> revisionIds, BiPredicate<String, WorkspaceType> workspaceIdAndTypePredicate, Instant since, Instant until, Integer limit)
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
        Stream<MergeRequest> mergeRequestStream;
        try
        {
            GitLabProjectId gitLabProjectId = parseProjectId(projectId);
            if (!revisionIdSet.isEmpty()) // Do we want to have a check here to know whether revisions belong to the protected branch?
            {
                // TODO: we might want to do this differently since the number of revision IDs can be huge
                // we can have a threshold for which we change our strategy to  to make a single call for
                // merge requests by the other criteria and then filter by revisionIds.
                MutableIntSet mergeRequestIds = IntSets.mutable.empty();
                CommitsApi commitsApi = getGitLabApi().getCommitsApi();
                // Combine all MRs associated with each revision
                mergeRequestStream = revisionIdSet.stream().flatMap(revisionId ->
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
                }).filter(mr -> (mr.getIid() != null) && mergeRequestIds.add(mr.getIid())); // remove duplicates
                Constants.MergeRequestState mergeRequestState = getMergeRequestState(state);
                if (mergeRequestState != Constants.MergeRequestState.ALL)
                {
                    String mergeRequestStateString = mergeRequestState.toString();
                    mergeRequestStream = mergeRequestStream.filter(mr -> mergeRequestStateString.equalsIgnoreCase(mr.getState()));
                }
            }
            else
            {
                // if no revision ID is specified we will use the default merge request API from Gitlab to take advantage of the filter
                MergeRequestFilter mergeRequestFilter = withMergeRequestFilters(new MergeRequestFilter(), state, since, until).withProjectId(gitLabProjectId.getGitLabId());
                mergeRequestStream = PagerTools.stream(withRetries(() -> getGitLabApi().getMergeRequestApi().getMergeRequests(mergeRequestFilter, ITEMS_PER_PAGE)));
            }
            String targetBranch = getSourceBranch(gitLabProjectId, patchReleaseVersionId);
            Stream<Review> stream = mergeRequestStream.filter(mr -> isReviewMergeRequest(mr, targetBranch)).map(mr -> fromGitLabMergeRequest(projectId, patchReleaseVersionId, mr));
            return addReviewFilters(stream, state, workspaceIdAndTypePredicate, since, until, limit).collect(Collectors.toList());
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to get reviews for project " + projectId + ((state == null) ? "" : (" with state " + state)),
                    () -> "Unknown project (" + projectId + ")",
                    () -> "Error getting reviews for project " + projectId + ((state == null) ? "" : (" with state " + state)));

        }
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
                        return isReviewMergeRequest(mr, defaultBranch);
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

    private MergeRequestFilter withMergeRequestFilters(MergeRequestFilter mergeRequestFilter, ReviewState state, Instant since, Instant until)
    {
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

        return mergeRequestFilter.withState(getMergeRequestState(state));
    }

    private MergeRequestFilter withMergeRequestLabels(MergeRequestFilter mergeRequestFilter, List<String> labels)
    {
        if (labels != null && !labels.isEmpty())
        {
            mergeRequestFilter.setLabels(labels);
        }

        return mergeRequestFilter;
    }

    private Stream<Review> addReviewFilters(Stream<Review> stream, ReviewState state, BiPredicate<String, WorkspaceType> workspaceIdAndTypePredicate, Instant since, Instant until, Integer limit)
    {
        return addWorkspaceIdAndTypeFilter(addLimitFilter(addTimeFilter(addStateFilter(stream, state), state, since, until), limit), workspaceIdAndTypePredicate);
    }

    public Stream<Review> addWorkspaceIdAndTypeFilter(Stream<Review> stream, BiPredicate<String, WorkspaceType> workspaceIdAndTypePredicate)
    {
        return workspaceIdAndTypePredicate == null ? stream : stream.filter(r -> workspaceIdAndTypePredicate.test(r.getWorkspaceId(), r.getWorkspaceType()));
    }

    private Stream<Review> addStateFilter(Stream<Review> stream, ReviewState state)
    {
        return (state == null) ? stream : stream.filter(r -> r.getState() == state);
    }

    private Stream<Review> addTimeFilter(Stream<Review> stream, ReviewState state, Instant since, Instant until)
    {
        Predicate<Review> timePredicate = getTimePredicate(state, since, until);
        return (timePredicate == null) ? stream : stream.filter(timePredicate);
    }

    private Stream<Review> addLimitFilter(Stream<Review> stream, Integer limit)
    {
        return ((limit == null) || (limit <= 0)) ? stream : stream.limit(limit);
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
    public Review getReview(String projectId, VersionId patchReleaseVersionId, String reviewId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");
        try
        {
            GitLabProjectId gitLabProjectId = parseProjectId(projectId);
            MergeRequest mergeRequest = getReviewMergeRequest(getGitLabApi().getMergeRequestApi(), gitLabProjectId, patchReleaseVersionId, reviewId);
            return fromGitLabMergeRequest(projectId, patchReleaseVersionId, mergeRequest);
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
    public Review createReview(String projectId, SourceSpecification sourceSpecification, String title, String description, List<String> labels)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(sourceSpecification.getWorkspaceId(), "workspaceId may not be null");
        LegendSDLCServerException.validateNonNull(sourceSpecification.getWorkspaceType(), "workspaceType may not be null");
        LegendSDLCServerException.validateNonNull(title, "title may not be null");
        LegendSDLCServerException.validateNonNull(description, "description may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);

        // checking if target branch exists or not
        if (sourceSpecification.getPatchReleaseVersionId() != null && !isPatchReleaseBranchPresent(gitLabProjectId, sourceSpecification.getPatchReleaseVersionId()))
        {
            throw new LegendSDLCServerException("Target patch release branch " + getPatchReleaseBranchName(sourceSpecification.getPatchReleaseVersionId()) + " for which you want to create review does not exist");
        }

        ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType = ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE;
        try
        {
            validateProjectConfigurationForCreateOrCommit(getProjectConfiguration(projectId, SourceSpecification.newSourceSpecification(sourceSpecification.getWorkspaceId(), sourceSpecification.getWorkspaceType(), workspaceAccessType, sourceSpecification.getPatchReleaseVersionId()), null));
            String workspaceBranchName = getWorkspaceBranchName(SourceSpecification.newSourceSpecification(sourceSpecification.getWorkspaceId(), sourceSpecification.getWorkspaceType(), workspaceAccessType, sourceSpecification.getPatchReleaseVersionId()));
            // TODO should we check for other merge requests for this workspace?
            MergeRequest mergeRequest = getGitLabApi().getMergeRequestApi().createMergeRequest(gitLabProjectId.getGitLabId(), workspaceBranchName, getSourceBranch(gitLabProjectId, sourceSpecification.getPatchReleaseVersionId()), title, description, null, null, (labels == null || labels.isEmpty()) ? null : labels.toArray(new String[0]), null, true);
            return fromGitLabMergeRequest(projectId, sourceSpecification.getPatchReleaseVersionId(), mergeRequest);
        }
        catch (Exception e)
        {
            throw buildException(e,
                () -> "User " + getCurrentUser() + " is not allowed to submit changes from " + sourceSpecification.getWorkspaceType().getLabel() + " " + workspaceAccessType.getLabel() + " " + sourceSpecification.getWorkspaceId() + " in project " + projectId + " for review",
                () -> "Unknown " + sourceSpecification.getWorkspaceType().getLabel() + " " + workspaceAccessType.getLabel() + " (" + sourceSpecification.getWorkspaceId() + ") or project (" + projectId + ")",
                () -> "Error submitting changes from " + sourceSpecification.getWorkspaceType().getLabel() + " " + workspaceAccessType.getLabel() + " " + sourceSpecification.getWorkspaceId() + " in project " + projectId + " for review");
        }
    }

    @Override
    public Review closeReview(String projectId, VersionId patchReleaseVersionId, String reviewId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        MergeRequestApi mergeRequestApi = getGitLabApi().getMergeRequestApi();
        MergeRequest mergeRequest = getReviewMergeRequest(mergeRequestApi, gitLabProjectId, patchReleaseVersionId, reviewId);
        validateMergeRequestReviewState(mergeRequest, ReviewState.OPEN);
        try
        {
            MergeRequest closeMergeRequest = updateMergeRequestState(mergeRequestApi, gitLabProjectId, patchReleaseVersionId, mergeRequest, Constants.StateEvent.CLOSE);
            return fromGitLabMergeRequest(projectId, patchReleaseVersionId, closeMergeRequest);
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
    public Review reopenReview(String projectId, VersionId patchReleaseVersionId, String reviewId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        MergeRequestApi mergeRequestApi = getGitLabApi().getMergeRequestApi();
        MergeRequest mergeRequest = getReviewMergeRequest(mergeRequestApi, gitLabProjectId, patchReleaseVersionId, reviewId);
        validateMergeRequestReviewState(mergeRequest, ReviewState.CLOSED);
        try
        {
            MergeRequest reopenMergeRequest = updateMergeRequestState(mergeRequestApi, gitLabProjectId, patchReleaseVersionId, mergeRequest, StateEvent.REOPEN);
            return fromGitLabMergeRequest(projectId, patchReleaseVersionId, reopenMergeRequest);
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
    public Review approveReview(String projectId, VersionId patchReleaseVersionId, String reviewId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        MergeRequestApi mergeRequestApi = getGitLabApi().getMergeRequestApi();
        MergeRequest mergeRequest = getReviewMergeRequest(mergeRequestApi, gitLabProjectId, patchReleaseVersionId, reviewId);
        try
        {
            MergeRequest approvalMergeRequest = mergeRequestApi.approveMergeRequest(gitLabProjectId.getGitLabId(), mergeRequest.getIid(), mergeRequest.getSha());
            // The MergeRequest that comes back from the approveMergeRequest call is not adequate for
            // creating a Review, as most relevant properties are null. The only useful thing we get
            // from it is the last update time.
            mergeRequest.setUpdatedAt(approvalMergeRequest.getUpdatedAt());
            return fromGitLabMergeRequest(projectId, patchReleaseVersionId, mergeRequest);
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
    public Review revokeReviewApproval(String projectId, VersionId patchReleaseVersionId, String reviewId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        MergeRequestApi mergeRequestApi = getGitLabApi().getMergeRequestApi();
        MergeRequest mergeRequest = getReviewMergeRequest(mergeRequestApi, gitLabProjectId, patchReleaseVersionId, reviewId);
        try
        {
            MergeRequest revokeApprovalMergeRequest = mergeRequestApi.unapproveMergeRequest(gitLabProjectId.getGitLabId(), mergeRequest.getIid());
            // The MergeRequest that comes back from the unapproveMergeRequest call is not adequate
            // for creating a Review, as most relevant properties are null. The only useful thing we
            // get from it is the last update time.
            mergeRequest.setUpdatedAt(revokeApprovalMergeRequest.getUpdatedAt());
            return fromGitLabMergeRequest(projectId, patchReleaseVersionId, mergeRequest);
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
    public Review rejectReview(String projectId, VersionId patchReleaseVersionId, String reviewId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        MergeRequestApi mergeRequestApi = getGitLabApi().getMergeRequestApi();
        MergeRequest mergeRequest = getReviewMergeRequest(mergeRequestApi, gitLabProjectId, patchReleaseVersionId, reviewId);
        validateMergeRequestReviewState(mergeRequest, ReviewState.OPEN);
        try
        {
            MergeRequest rejectMergeRequest = updateMergeRequestState(mergeRequestApi, gitLabProjectId, patchReleaseVersionId, mergeRequest, StateEvent.CLOSE);
            return fromGitLabMergeRequest(projectId, patchReleaseVersionId, rejectMergeRequest);
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
    public Approval getReviewApproval(String projectId, VersionId patchReleaseVersionId, String reviewId)
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
    public Review commitReview(String projectId, VersionId patchReleaseVersionId, String reviewId, String message)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");
        LegendSDLCServerException.validateNonNull(message, "message may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        MergeRequestApi mergeRequestApi = getGitLabApi().getMergeRequestApi();

        // Find the merge request
        MergeRequest mergeRequest = getReviewMergeRequest(mergeRequestApi, gitLabProjectId, patchReleaseVersionId, reviewId);

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
        WorkspaceInfo workspaceInfo = parseWorkspaceBranchName(mergeRequest.getSourceBranch());
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
            return fromGitLabMergeRequest(projectId, patchReleaseVersionId, mergeRequestApi.acceptMergeRequest(gitLabProjectId.getGitLabId(), mergeRequest.getIid(), message, true, null, null));
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
    public ReviewUpdateStatus getReviewUpdateStatus(String projectId, VersionId patchReleaseVersionId, String reviewId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        GitLabApi gitLabApi = getGitLabApi();
        MergeRequest mergeRequest = getReviewMergeRequest(gitLabApi.getMergeRequestApi(), gitLabProjectId, patchReleaseVersionId, reviewId);
        if (!(isOpen(mergeRequest) || isLocked(mergeRequest)))
        {
            throw new LegendSDLCServerException("Cannot get update status for review " + mergeRequest.getIid() + " in project " + projectId + ": state is " + getReviewState(mergeRequest), Status.CONFLICT);
        }
        return getReviewUpdateStatus(gitLabProjectId, gitLabApi, mergeRequest);
    }

    @Override
    public ReviewUpdateStatus updateReview(String projectId, VersionId patchReleaseVersionId, String reviewId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        GitLabApi gitLabApi = getGitLabApi();
        MergeRequestApi mergeRequestApi = gitLabApi.getMergeRequestApi();

        // Check the current status of the review
        MergeRequest initialMergeRequest = getReviewMergeRequest(mergeRequestApi, gitLabProjectId, patchReleaseVersionId, reviewId);
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
    public Review editReview(String projectId, VersionId patchReleaseVersionId, String reviewId, String title, String description, List<String> labels)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");
        LegendSDLCServerException.validateNonNull(title, "title may not be null");
        LegendSDLCServerException.validateNonNull(description, "description may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        GitLabApi gitLabApi = getGitLabApi();
        MergeRequestApi mergeRequestApi = gitLabApi.getMergeRequestApi();

        MergeRequest mergeRequest = getReviewMergeRequest(mergeRequestApi, gitLabProjectId, patchReleaseVersionId, reviewId);
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
            return fromGitLabMergeRequest(projectId, patchReleaseVersionId, editedRequest);
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

    private void validateMergeRequestReviewState(MergeRequest mergeRequest, ReviewState expectedState)
    {
        ReviewState actualState = getReviewState(mergeRequest);
        if (expectedState != actualState)
        {
            throw new LegendSDLCServerException("Review is not " + expectedState.name().toLowerCase() + " (state: " + actualState.name().toLowerCase() + ")", Status.CONFLICT);
        }
    }

    protected MergeRequest updateMergeRequestState(MergeRequestApi mergeRequestApi, GitLabProjectId projectId, VersionId patchReleaseVersionId, MergeRequest mergeRequest, Constants.StateEvent stateEvent) throws GitLabApiException
    {
        return mergeRequestApi.updateMergeRequest(projectId.getGitLabId(), mergeRequest.getIid(), getSourceBranch(projectId, patchReleaseVersionId), null, null, null, stateEvent, null, null, null, null, null, null);
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

    private static Constants.MergeRequestState getMergeRequestState(ReviewState state)
    {
        if (state == null)
        {
            return Constants.MergeRequestState.ALL;
        }
        switch (state)
        {
            case OPEN:
            {
                return Constants.MergeRequestState.OPENED;
            }
            case COMMITTED:
            {
                return Constants.MergeRequestState.MERGED;
            }
            case CLOSED:
            {
                return Constants.MergeRequestState.CLOSED;
            }
            case UNKNOWN:
            {
                return Constants.MergeRequestState.ALL;
            }
            default:
            {
                throw new IllegalArgumentException("Unknown review state: " + state);
            }
        }
    }

    protected static Review fromGitLabMergeRequest(String projectId, VersionId patchReleaseVersionId, MergeRequest mergeRequest)
    {
        if (mergeRequest == null)
        {
            return null;
        }

        String sourceBranchName = mergeRequest.getSourceBranch();
        WorkspaceInfo workspaceInfo = parseWorkspaceBranchName(sourceBranchName);
        if ((workspaceInfo == null) || (workspaceInfo.getWorkspaceAccessType() != ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE))
        {
            return null;
        }
        return newReview(mergeRequest.getIid(), projectId, workspaceInfo, mergeRequest.getTitle(), mergeRequest.getDescription(), mergeRequest.getCreatedAt(), mergeRequest.getUpdatedAt(), mergeRequest.getClosedAt(), mergeRequest.getMergedAt(), mergeRequest.getState(), mergeRequest.getAuthor(), mergeRequest.getMergeCommitSha(), mergeRequest.getWebUrl(), mergeRequest.getLabels());
    }

    private static Review newReview(Integer reviewId, String projectId, WorkspaceInfo workspaceInfo, String title, String description, Date createdAt, Date lastUpdatedAt, Date closedAt, Date committedAt, String reviewState, AbstractUser<?> author, String commitRevisionId, String webURL, List<String> labels)
    {
        return newReview(reviewId, projectId, workspaceInfo.getWorkspaceId(), workspaceInfo.getWorkspaceType(), title, description, createdAt, lastUpdatedAt, closedAt, committedAt, reviewState, author, commitRevisionId, webURL, labels);
    }

    private static Review newReview(Integer reviewId, String projectId, String workspaceId, WorkspaceType workspaceType, String title, String description, Date createdAt, Date lastUpdatedAt, Date closedAt, Date committedAt, String reviewState, AbstractUser<?> author, String commitRevisionId, String webURL, List<String> labels)
    {
        return newReview(toStringIfNotNull(reviewId), projectId, workspaceId, workspaceType, title, description, toInstantIfNotNull(createdAt), toInstantIfNotNull(lastUpdatedAt), toInstantIfNotNull(closedAt), toInstantIfNotNull(committedAt), getReviewState(reviewState), fromGitLabAbstractUser(author), commitRevisionId, webURL, labels);
    }

    private static Review newReview(String reviewId, String projectId, String workspaceId, WorkspaceType workspaceType, String title, String description, Instant createdAt, Instant lastUpdatedAt, Instant closedAt, Instant committedAt, ReviewState reviewState, User author, String commitRevisionId, String webURL, List<String> labels)
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
            public WorkspaceType getWorkspaceType()
            {
                return workspaceType;
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

            @Override
            public List<String> getLabels()
            {
                return labels;
            }
        };
    }

    private static Approval fromGitLabMergeRequest(MergeRequest mergeRequest)
    {
        if ((mergeRequest == null) || (mergeRequest.getApprovedBy() == null))
        {
            return null;
        }
        return newApproval(mergeRequest.getApprovedBy().stream().map(BaseGitLabApi::fromGitLabAbstractUser).collect(Collectors.toList()));
    }

    private static Approval newApproval(List<User> approvedBy)
    {
        return new Approval()
        {
            @Override
            public List<User> getApprovedBy()
            {
                return approvedBy;
            }
        };
    }
}
