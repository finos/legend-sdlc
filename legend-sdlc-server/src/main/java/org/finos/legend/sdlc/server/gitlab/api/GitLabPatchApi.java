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
import org.finos.legend.sdlc.domain.model.review.Review;
import org.finos.legend.sdlc.domain.model.review.ReviewState;
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
import org.gitlab4j.api.Pager;
import org.gitlab4j.api.RepositoryApi;
import org.gitlab4j.api.models.Branch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.util.List;

public class GitLabPatchApi extends GitLabApiWithFileAccess implements PatchApi
{
    private static final Logger LOGGER = LoggerFactory.getLogger(GitLabPatchApi.class);

    @Inject
    public GitLabPatchApi(GitLabConfiguration gitLabConfiguration, GitLabUserContext userContext, BackgroundTaskProcessor backgroundTaskProcessor)
    {
        super(gitLabConfiguration, userContext, backgroundTaskProcessor);
    }

    private Patch patchBranchToPatch(String projectId, Branch patchBranch)
    {
        if (patchBranch != null)
        {
           return fromPatchBranchName(projectId, patchBranch.getName());
        }
        return null;
    }

    @Override
    public Patch newPatch(String projectId, Version sourcePatchReleaseVersion)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(sourcePatchReleaseVersion, "sorcePatchReleaseId may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);

        if (this.getProjectConfiguration(projectId, sourcePatchReleaseVersion.getId()) ==  null)
        {
            throw new LegendSDLCServerException("Project structure has not been set up", Response.Status.CONFLICT);
        }
        VersionId targetVersionId = sourcePatchReleaseVersion.getId().nextPatchVersion();
        Version targetVersion = null;
        if (targetVersion != null)
        {
            throw new LegendSDLCServerException(targetVersionId + "already exists", Response.Status.CONFLICT);
        }

        // Check if the patch branch they want to create exists or not
        if (isPatchReleaseBranchPresent(gitLabProjectId, targetVersionId.toVersionIdString()))
        {
            throw new LegendSDLCServerException("Patch release branch for " + targetVersionId.toVersionIdString() + " exists already", Response.Status.CONFLICT);
        }

        // Create new patch release branch
        Branch branch;
        try
        {
            branch = GitLabApiTools.createProtectedBranchFromSourceTagAndVerify(getGitLabApi(), gitLabProjectId.getGitLabId(), getPatchReleaseBranchName(targetVersionId.toVersionIdString()), RELEASE_TAG_PREFIX + sourcePatchReleaseVersion.getId().toVersionIdString(), 30, 1_000);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to create patch release " + targetVersionId.toVersionIdString() + " in project " + projectId,
                    () -> "Unknown project: " + projectId,
                    () -> "Error creating patch release " + targetVersionId.toVersionIdString() + " in project " + projectId);
        }
        if (branch == null)
        {
            throw new LegendSDLCServerException("Failed to create patch release " + targetVersionId + " in project " + projectId);
        }

        return patchBranchToPatch(projectId, branch);
    }

    @Override
    public List<Patch> getAllPatches(String projectId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        try
        {
            GitLabProjectId gitLabProjectId = parseProjectId(projectId);
            String branchPrefix = getPatchReleaseBranchName("");
            Pager<Branch> pager = getGitLabApi().getRepositoryApi().getBranches(gitLabProjectId.getGitLabId(), "^" + branchPrefix, ITEMS_PER_PAGE);
            return PagerTools.stream(pager)
                    .filter(branch -> (branch != null) && (branch.getName() != null) && branch.getName().startsWith(branchPrefix))
                    .map(branch -> patchBranchToPatch(projectId, branch))
                    .collect(PagerTools.listCollector(pager));
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to get patch release branches for project " + projectId,
                    () -> "Unknown project: " + projectId,
                    () -> "Error getting get patch release branches for project " + projectId);
        }
    }

    private Patch fromPatchBranchName(String projectId, String branchName)
    {
        int index = branchName.lastIndexOf(BRANCH_DELIMITER);
        String patchReleaseId = branchName.substring(index + 1, branchName.length());
        return new Patch()
        {
            @Override
            public String getProjectId()
            {
                return projectId;
            }

            @Override
            public String getPatchReleaseVersion()
            {
                return patchReleaseId;
            }
        };
    }

    @Override
    public void deletePatch(String projectId, String patchReleaseVersion)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(patchReleaseVersion, "patchReleaseVersion may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        RepositoryApi repositoryApi = getGitLabApi().getRepositoryApi();

        // Delete patch branch
        boolean patchBranchDeleted;
        try
        {
            patchBranchDeleted = GitLabApiTools.deleteBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(), getPatchReleaseBranchName(patchReleaseVersion), 20, 1_000);
            // close merge requests created for patch release branch
            submitBackgroundRetryableTask(() -> closeMergeRequestsCreatedForPatchReleaseBranch(projectId, patchReleaseVersion), 5000L, "close merge requests created for branch " + getPatchReleaseBranchName(patchReleaseVersion));
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to delete patch release branch " + patchReleaseVersion + " in project " + projectId,
                    () -> "Unknown patch release branch " + patchReleaseVersion + " or project (" + projectId + ")",
                    () -> "Error deleting patch release branch " + patchReleaseVersion + " in project " + projectId);
        }
        if (!patchBranchDeleted)
        {
            throw new LegendSDLCServerException("Failed to delete patch release branch " + patchReleaseVersion + " in project " + projectId);
        }
    }

    @Override
    public Version releasePatch(String projectId, String patchReleaseVersion)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(patchReleaseVersion, "patchReleaseVersion may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);

        // check if the version you want to create exists or not
        VersionId targetVersionId;
        try
        {
            targetVersionId = VersionId.parseVersionId(patchReleaseVersion);
        }
        catch (IllegalArgumentException e)
        {
            throw new LegendSDLCServerException(e.getMessage(), Response.Status.BAD_REQUEST, e);
        }
        Version targetVersion = null;
        try
        {
            targetVersion = getVersion(projectId, targetVersionId.getMajorVersion(), targetVersionId.getMinorVersion(), targetVersionId.getPatchVersion());
            throw new LegendSDLCServerException(targetVersionId + "already exists", Response.Status.BAD_REQUEST);
        }
        catch (Exception e)
        {
            LOGGER.warn("Error in creating release tag for the patch relase version ", patchReleaseVersion, projectId, e);
        }
        if (targetVersion != null)
        {
            throw new LegendSDLCServerException(targetVersionId + "already exists", Response.Status.BAD_REQUEST);
        }

        // Check if the patch branch they want to release exists or not
        Branch patchBranch = null;
        try
        {
            patchBranch = getGitLabApi().getRepositoryApi().getBranch(gitLabProjectId.getGitLabId(), getPatchReleaseBranchName(patchReleaseVersion));
        }
        catch (Exception e)
        {
            LOGGER.warn("Error in fetching the patch release branch ", patchReleaseVersion, projectId, e);
        }
        if (patchBranch == null)
        {
            throw new LegendSDLCServerException("Patch release branch for " + patchReleaseVersion + " doesn not exist for the project " + projectId, Response.Status.BAD_REQUEST);
        }
        Version releaseVersion =  newVersion(gitLabProjectId, patchReleaseVersion, patchBranch.getCommit().getId(), VersionId.newVersionId(targetVersionId.getMajorVersion(), targetVersionId.getMinorVersion(), targetVersionId.getPatchVersion()), "");

        // delete the patch release branch and close the MRs created for this branch
        submitBackgroundRetryableTask(() -> GitLabApiTools.deleteBranchAndVerify(getGitLabApi().getRepositoryApi(), gitLabProjectId.getGitLabId(), getPatchReleaseBranchName(patchReleaseVersion), 20, 1_000), 5000L, "delete " + getPatchReleaseBranchName(patchReleaseVersion));
        submitBackgroundRetryableTask(() -> closeMergeRequestsCreatedForPatchReleaseBranch(projectId, patchReleaseVersion), 5000L, "close merge requests created for branch " + getPatchReleaseBranchName(patchReleaseVersion));
        return releaseVersion;
    }

    private boolean closeMergeRequestsCreatedForPatchReleaseBranch(String projectId, String patchReleaseVersion)
    {
        List<Review> reviews = getReviews(projectId, patchReleaseVersion, ReviewState.OPEN, null, null, null, null, null);
        for (Review review : reviews)
        {
            try
            {
                closeReview(projectId, patchReleaseVersion, review.getId());
            }
            catch (Exception e)
            {
                throw e;
            }
        }
        return true;
    }
}