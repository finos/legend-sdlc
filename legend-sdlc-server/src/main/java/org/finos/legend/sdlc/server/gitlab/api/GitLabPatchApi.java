// Copyright 2023 Goldman Sachs
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

import org.finos.legend.sdlc.domain.model.patch.Patch;
import org.finos.legend.sdlc.domain.model.version.Version;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.domain.api.patch.PatchApi;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.GitLabConfiguration;
import org.finos.legend.sdlc.server.gitlab.GitLabProjectId;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.gitlab.tools.GitLabApiTools;
import org.finos.legend.sdlc.server.gitlab.tools.PagerTools;
import org.finos.legend.sdlc.server.tools.BackgroundTaskProcessor;
import org.gitlab4j.api.Constants;
import org.gitlab4j.api.Pager;
import org.gitlab4j.api.RepositoryApi;
import org.gitlab4j.api.models.Branch;
import org.gitlab4j.api.models.MergeRequest;
import org.gitlab4j.api.models.MergeRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.ws.rs.core.Response;

public class GitLabPatchApi extends GitLabApiWithFileAccess implements PatchApi
{
    private static final Logger LOGGER = LoggerFactory.getLogger(GitLabPatchApi.class);

    @Inject
    public GitLabPatchApi(GitLabConfiguration gitLabConfiguration, GitLabUserContext userContext, BackgroundTaskProcessor backgroundTaskProcessor)
    {
        super(gitLabConfiguration, userContext, backgroundTaskProcessor);
    }

    @Override
    public Patch newPatch(String projectId, VersionId sourceVersionId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(sourceVersionId, "source version may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);

        boolean sourceTagExists;
        try
        {
            sourceTagExists = GitLabApiTools.tagExists(getGitLabApi(), gitLabProjectId.getGitLabId(), buildVersionTagName(sourceVersionId));
        }
        catch (Exception e)
        {
            throw new LegendSDLCServerException("Error in fetching version " + sourceVersionId.toVersionIdString() + " for project " + projectId, e);
        }
        if (!sourceTagExists)
        {
            throw new LegendSDLCServerException("Source version " + sourceVersionId.toVersionIdString() + " does not exist", Response.Status.BAD_REQUEST);
        }

        VersionId targetVersionId = sourceVersionId.nextPatchVersion();
        boolean targetTagExists;
        try
        {
            targetTagExists = GitLabApiTools.tagExists(getGitLabApi(), gitLabProjectId.getGitLabId(), buildVersionTagName(targetVersionId));
        }
        catch (Exception e)
        {
            throw new LegendSDLCServerException("Error in fetching version " + targetVersionId.toVersionIdString() + " for project " + projectId, e);
        }
        if (targetTagExists)
        {
            throw new LegendSDLCServerException("Target version " + targetVersionId.toVersionIdString() + " already exists", Response.Status.BAD_REQUEST);
        }

        // Check if the patch branch they want to create exists or not
        if (isPatchReleaseBranchPresent(gitLabProjectId, targetVersionId))
        {
            throw new LegendSDLCServerException("Patch " + targetVersionId.toVersionIdString() + " already exists", Response.Status.CONFLICT);
        }

        // Create new patch release branch
        Branch branch;
        try
        {
            branch = GitLabApiTools.createProtectedBranchFromSourceTagAndVerify(getGitLabApi(), gitLabProjectId.getGitLabId(), getPatchReleaseBranchName(targetVersionId), buildVersionTagName(sourceVersionId), 30, 1_000);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to create patch release " + targetVersionId.toVersionIdString() + " in project " + projectId,
                    () -> "Unknown project: " + projectId,
                    () -> "Error creating patch " + targetVersionId.toVersionIdString() + " in project " + projectId);
        }
        if (branch == null)
        {
            throw new LegendSDLCServerException("Failed to create patch " + targetVersionId.toVersionIdString() + " in project " + projectId);
        }

        return fromPatchBranchName(projectId, branch.getName());
    }

    @Override
    public List<Patch> getPatches(String projectId, Integer minMajorVersion, Integer maxMajorVersion, Integer minMinorVersion, Integer maxMinorVersion, Integer minPatchVersion, Integer maxPatchVersion)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        try
        {
            GitLabProjectId gitLabProjectId = parseProjectId(projectId);
            String branchPrefix = getPatchReleaseBranchPrefix();
            Pager<Branch> pager = getGitLabApi().getRepositoryApi().getBranches(gitLabProjectId.getGitLabId(), "^" + branchPrefix, ITEMS_PER_PAGE);
            Stream<Patch> stream = PagerTools.stream(pager)
                    .filter(branch -> (branch != null) && (branch.getName() != null) && branch.getName().startsWith(branchPrefix))
                    .map(branch -> fromPatchBranchName(projectId, branch.getName()));
            // major version constraint
            if ((minMajorVersion != null) && (maxMajorVersion != null))
            {
                int minMajorVersionInt = minMajorVersion;
                int maxMajorVersionInt = maxMajorVersion;
                if (minMajorVersionInt == maxMajorVersionInt)
                {
                    stream = stream.filter(p -> p.getPatchReleaseVersionId().getMajorVersion() == minMajorVersionInt);
                }
                else
                {
                    stream = stream.filter(p ->
                    {
                        int majorVersion = p.getPatchReleaseVersionId().getMajorVersion();
                        return (minMajorVersionInt <= majorVersion) && (majorVersion <= maxMajorVersionInt);
                    });
                }
            }
            else if (minMajorVersion != null)
            {
                int minMajorVersionInt = minMajorVersion;
                stream = stream.filter(p -> p.getPatchReleaseVersionId().getMajorVersion() >= minMajorVersionInt);
            }
            else if (maxMajorVersion != null)
            {
                int maxMajorVersionInt = maxMajorVersion;
                stream = stream.filter(p -> p.getPatchReleaseVersionId().getMajorVersion() <= maxMajorVersionInt);
            }

            // minor version constraint
            if ((minMinorVersion != null) && (maxMinorVersion != null))
            {
                int minMinorVersionInt = minMinorVersion;
                int maxMinorVersionInt = maxMinorVersion;
                if (minMinorVersionInt == maxMinorVersionInt)
                {
                    stream = stream.filter(p -> p.getPatchReleaseVersionId().getMinorVersion() == minMinorVersionInt);
                }
                else
                {
                    stream = stream.filter(p ->
                    {
                        int minorVersion = p.getPatchReleaseVersionId().getMinorVersion();
                        return (minMinorVersionInt <= minorVersion) && (minorVersion <= maxMinorVersionInt);
                    });
                }
            }
            else if (minMinorVersion != null)
            {
                int minMinorVersionInt = minMinorVersion;
                stream = stream.filter(p -> p.getPatchReleaseVersionId().getMinorVersion() >= minMinorVersionInt);
            }
            else if (maxMinorVersion != null)
            {
                int maxMinorVersionInt = maxMinorVersion;
                stream = stream.filter(p -> p.getPatchReleaseVersionId().getMinorVersion() <= maxMinorVersionInt);
            }


            // patch version constraint
            if ((minPatchVersion != null) && (maxPatchVersion != null))
            {
                int minPatchVersionInt = minPatchVersion;
                int maxPatchVersionInt = maxPatchVersion;
                if (minPatchVersionInt == maxPatchVersionInt)
                {
                    stream = stream.filter(p -> p.getPatchReleaseVersionId().getPatchVersion() == minPatchVersionInt);
                }
                else
                {
                    stream = stream.filter(p ->
                    {
                        int patchVersion = p.getPatchReleaseVersionId().getPatchVersion();
                        return (minPatchVersionInt <= patchVersion) && (patchVersion <= maxPatchVersionInt);
                    });
                }
            }
            else if (minPatchVersion != null)
            {
                int minPatchVersionInt = minPatchVersion;
                stream = stream.filter(p -> p.getPatchReleaseVersionId().getPatchVersion() >= minPatchVersionInt);
            }
            else if (maxPatchVersion != null)
            {
                int maxPatchVersionInt = maxPatchVersion;
                stream = stream.filter(p -> p.getPatchReleaseVersionId().getPatchVersion() <= maxPatchVersionInt);
            }
            return stream.collect(Collectors.toList());
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to get patches for project " + projectId,
                    () -> "Unknown project: " + projectId,
                    () -> "Error getting get patches for project " + projectId);
        }
    }

    @Override
    public void deletePatch(String projectId, VersionId patchReleaseVersionId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(patchReleaseVersionId, "patchReleaseVersionId may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        RepositoryApi repositoryApi = getGitLabApi().getRepositoryApi();

        // Delete patch branch
        boolean patchBranchDeleted;
        try
        {
            patchBranchDeleted = GitLabApiTools.deleteBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(), getPatchReleaseBranchName(patchReleaseVersionId), 20, 1_000);
            // close merge requests created for patch release branch
            submitBackgroundRetryableTask(() -> closeMergeRequestsCreatedForPatchReleaseBranch(gitLabProjectId, patchReleaseVersionId), 5000L, "close merge requests created for branch " + getPatchReleaseBranchName(patchReleaseVersionId));
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to delete patch " + patchReleaseVersionId.toVersionIdString() + " in project " + projectId,
                    () -> "Unknown patch " + patchReleaseVersionId.toVersionIdString() + " or project (" + projectId + ")",
                    () -> "Error deleting patch " + patchReleaseVersionId.toVersionIdString() + " in project " + projectId);
        }
        if (!patchBranchDeleted)
        {
            throw new LegendSDLCServerException("Failed to delete patch " + patchReleaseVersionId.toVersionIdString() + " in project " + projectId);
        }
    }

    @Override
    public Version releasePatch(String projectId, VersionId patchReleaseVersionId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(patchReleaseVersionId, "patchReleaseVersionId may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);

        // check if the version you want to create exists or not
        boolean targetTagExists;
        try
        {
            targetTagExists = GitLabApiTools.tagExists(getGitLabApi(), gitLabProjectId.getGitLabId(), buildVersionTagName(patchReleaseVersionId));
        }
        catch (Exception e)
        {
            throw new LegendSDLCServerException("Error in fetching version " + patchReleaseVersionId.toVersionIdString() + " for project " + projectId, Response.Status.BAD_REQUEST, e);
        }
        if (targetTagExists)
        {
            throw new LegendSDLCServerException("Target version " + patchReleaseVersionId.toVersionIdString() + " already exists", Response.Status.BAD_REQUEST);
        }

        // Check if the patch branch we want to release exists or not
        Branch patchBranch = null;
        try
        {
            patchBranch = getGitLabApi().getRepositoryApi().getBranch(gitLabProjectId.getGitLabId(), getPatchReleaseBranchName(patchReleaseVersionId));
        }
        catch (Exception e)
        {
            throw new LegendSDLCServerException("Error in fetching the patch release branch " + patchReleaseVersionId.toVersionIdString() + " for project " + projectId, Response.Status.BAD_REQUEST, e);
        }
        if (patchBranch == null)
        {
            throw new LegendSDLCServerException("Patch " + patchReleaseVersionId.toVersionIdString() + " does not exist in the project " + projectId, Response.Status.BAD_REQUEST);
        }
        Version releaseVersion =  newVersion(gitLabProjectId, patchReleaseVersionId, patchBranch.getCommit().getId(), VersionId.newVersionId(patchReleaseVersionId.getMajorVersion(), patchReleaseVersionId.getMinorVersion(), patchReleaseVersionId.getPatchVersion()), "");

        // delete the patch release branch and close the MRs created for this branch
        submitBackgroundRetryableTask(() -> GitLabApiTools.deleteBranchAndVerify(getGitLabApi().getRepositoryApi(), gitLabProjectId.getGitLabId(), getPatchReleaseBranchName(patchReleaseVersionId), 20, 1_000), 5000L, "delete " + getPatchReleaseBranchName(patchReleaseVersionId));
        submitBackgroundRetryableTask(() -> closeMergeRequestsCreatedForPatchReleaseBranch(gitLabProjectId, patchReleaseVersionId), 5000L, "close merge requests created for branch " + getPatchReleaseBranchName(patchReleaseVersionId));
        return releaseVersion;
    }

    private boolean closeMergeRequestsCreatedForPatchReleaseBranch(GitLabProjectId projectId, VersionId patchReleaseVersionId)
    {
        String branchName = getPatchReleaseBranchName(patchReleaseVersionId);
        List<MergeRequest> mergeRequests;
        try
        {
           MergeRequestFilter mergeRequestFilter = new MergeRequestFilter().withTargetBranch(branchName).withState(Constants.MergeRequestState.OPENED);
           mergeRequests = PagerTools.stream(withRetries(() -> getGitLabApi().getMergeRequestApi().getMergeRequests(mergeRequestFilter, ITEMS_PER_PAGE))).collect(Collectors.toList());
        }
        catch (Exception e)
        {
            if (GitLabApiTools.isRetryableGitLabApiException(e))
            {
                return false;
            }
            LOGGER.warn("Unable to fetch merge requests for target branch {} of project {}", branchName, projectId, e);
            return true;
        }
        boolean shouldRetry = false;
        for (MergeRequest mergeRequest : mergeRequests)
        {
            try
            {
                getGitLabApi().getMergeRequestApi().updateMergeRequest(projectId.getGitLabId(), mergeRequest.getIid(), branchName, null, null, null, Constants.StateEvent.CLOSE, null, null, null, null, null, null);
            }
            catch (Exception e)
            {
                LOGGER.warn("Unable to close merge request {} of project {}", mergeRequest.getIid(), projectId, e);
                if (GitLabApiTools.isRetryableGitLabApiException(e))
                {
                    shouldRetry = true;
                }
            }
        }
        return !shouldRetry;
    }
}