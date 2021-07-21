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

package org.finos.legend.sdlc.server.gitlab.api;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.project.Project;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.domain.model.review.Review;
import org.finos.legend.sdlc.domain.model.review.ReviewState;
import org.finos.legend.sdlc.server.gitlab.GitLabProjectId;
import org.finos.legend.sdlc.server.gitlab.api.server.AbstractGitLabServerApiTest;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.tools.CallUntil;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.MergeRequestApi;
import org.gitlab4j.api.RepositoryApi;
import org.gitlab4j.api.models.Branch;
import org.gitlab4j.api.models.MergeRequest;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Substantial test resource class for Entity API tests shared by the docker-based and server-based GitLab tests.
 */
public class GitLabEntityApiTestResource
{
    private final GitLabProjectApi gitLabProjectApi;
    private final GitLabWorkspaceApi gitLabWorkspaceApi;
    private final GitLabEntityApi gitLabEntityApi;
    private final GitLabReviewApi gitLabCommitterReviewApi;
    private final GitLabReviewApi gitLabApproverReviewApi;

    private final GitLabUserContext gitLabMemberUserContext;

    private static final Logger LOGGER = LoggerFactory.getLogger(GitLabEntityApiTestResource.class);

    public GitLabEntityApiTestResource(GitLabProjectApi gitLabProjectApi, GitLabWorkspaceApi gitLabWorkspaceApi, GitLabEntityApi gitLabEntityApi, GitLabReviewApi gitLabCommitterReviewApi, GitLabReviewApi gitLabApproverReviewApi, GitLabUserContext gitLabMemberUserContext)
    {
        this.gitLabProjectApi = gitLabProjectApi;
        this.gitLabWorkspaceApi = gitLabWorkspaceApi;
        this.gitLabEntityApi = gitLabEntityApi;
        this.gitLabCommitterReviewApi = gitLabCommitterReviewApi;
        this.gitLabApproverReviewApi = gitLabApproverReviewApi;
        this.gitLabMemberUserContext = gitLabMemberUserContext;
    }

    public void runEntitiesInNormalWorkflowTest() throws GitLabApiException
    {
        String projectName = "CommitFlowTestProject";
        String description = "A test project.";
        ProjectType projectType = ProjectType.PRODUCTION;
        String groupId = "org.finos.sdlc.test";
        String artifactId = "entitytestproj";
        List<String> tags = Lists.mutable.with("doe", "moffitt", AbstractGitLabServerApiTest.INTEGRATION_TEST_PROJECT_TAG);
        String workspaceName = "entitytestworkspace";

        Project createdProject = gitLabProjectApi.createProject(projectName, description, projectType, groupId, artifactId, tags);

        String projectId = createdProject.getProjectId();
        Workspace createdWorkspace = gitLabWorkspaceApi.newUserWorkspace(projectId, workspaceName); // TODO

        String workspaceId = createdWorkspace.getWorkspaceId();
        List<Entity> initialWorkspaceEntities = gitLabEntityApi.getWorkspaceEntityAccessContext(projectId, workspaceId).getEntities(null, null, null);
        List<Entity> initialProjectEntities = gitLabEntityApi.getProjectEntityAccessContext(projectId).getEntities(null, null, null);

        Assert.assertEquals(Collections.emptyList(), initialWorkspaceEntities);
        Assert.assertEquals(Collections.emptyList(), initialProjectEntities);

        String entityPath = "test::entity";
        String classifierPath = "meta::test::mathematicsDepartment";
        Map<String, String> entityContentMap = Maps.mutable.with(
                "package", "test",
                "name", "entity",
                "math-113", "abstract-algebra",
                "math-185", "complex-analysis");
        gitLabEntityApi.getWorkspaceEntityModificationContext(projectId, workspaceId).createEntity(entityPath, classifierPath, entityContentMap, "initial entity");
        List<Entity> modifiedWorkspaceEntities = gitLabEntityApi.getWorkspaceEntityAccessContext(projectId, workspaceId).getEntities(null, null, null);
        List<Entity> modifiedProjectEntities = gitLabEntityApi.getProjectEntityAccessContext(projectId).getEntities(null, null, null);

        Assert.assertNotNull(modifiedWorkspaceEntities);
        Assert.assertEquals(Collections.emptyList(), modifiedProjectEntities);
        Assert.assertEquals(1, modifiedWorkspaceEntities.size());
        Entity initalEntity = modifiedWorkspaceEntities.get(0);
        Assert.assertEquals(initalEntity.getPath(), entityPath);
        Assert.assertEquals(initalEntity.getClassifierPath(), classifierPath);
        Assert.assertEquals(initalEntity.getContent(), entityContentMap);

        Review testReview = gitLabCommitterReviewApi.createReview(projectId, workspaceId, "Add Courses.", "add two math courses");
        String reviewId = testReview.getId();
        Review approvedReview = gitLabApproverReviewApi.approveReview(projectId, reviewId);

        Assert.assertNotNull(approvedReview);
        Assert.assertEquals(reviewId, approvedReview.getId());
        Assert.assertEquals(ReviewState.OPEN, approvedReview.getState());

        GitLabProjectId sdlcGitLabProjectId = GitLabProjectId.parseProjectId(projectId);
        MergeRequestApi mergeRequestApi = gitLabMemberUserContext.getGitLabAPI(sdlcGitLabProjectId.getGitLabMode()).getMergeRequestApi();
        int parsedMergeRequestId = Integer.parseInt(reviewId);
        int gitlabProjectId = sdlcGitLabProjectId.getGitLabId();

        String requiredStatus = "can_be_merged";
        CallUntil<MergeRequest, GitLabApiException> callUntil = CallUntil.callUntil(
                () -> mergeRequestApi.getMergeRequest(gitlabProjectId, parsedMergeRequestId),
                mr -> requiredStatus.equals(mr.getMergeStatus()),
                10,
                500);
        if (!callUntil.succeeded())
        {
            throw new RuntimeException("Merge request " + approvedReview.getId() + " still does not have status \"" + requiredStatus + "\" after " + callUntil.getTryCount() + " tries");
        }
        LOGGER.info("Waited {} times for merge to have status \"{}\"", callUntil.getTryCount(), requiredStatus);

        gitLabCommitterReviewApi.commitReview(projectId, reviewId, "add two math courses");

        String requiredMergedStatus = "merged";
        CallUntil<MergeRequest, GitLabApiException> callUntilMerged = CallUntil.callUntil(
                () -> mergeRequestApi.getMergeRequest(gitlabProjectId, parsedMergeRequestId),
                mr -> requiredMergedStatus.equals(mr.getState()),
                10,
                500);
        if (!callUntilMerged.succeeded())
        {
            throw new RuntimeException("Merge request " + reviewId + " still does not have state \"" + requiredMergedStatus + "\" after " + callUntilMerged.getTryCount() + " tries");
        }
        LOGGER.info("Waited {} times for merge request to have state \"{}\"", callUntilMerged.getTryCount(), requiredMergedStatus);

        RepositoryApi repositoryApi = gitLabMemberUserContext.getGitLabAPI(sdlcGitLabProjectId.getGitLabMode()).getRepositoryApi();
        CallUntil<List<Branch>, GitLabApiException> callUntilBranchDeleted = CallUntil.callUntil(
                () -> repositoryApi.getBranches(sdlcGitLabProjectId.getGitLabId()),
                GitLabEntityApiTestResource::hasOnlyMasterBranch,
                15,
                1000);
        if (!callUntilBranchDeleted.succeeded())
        {
            // Warn instead of throwing exception since we cannot manage time expectation on GitLab to reflect branch deletion.
            LOGGER.warn("Branch is still not deleted post merge after {} tries", callUntilBranchDeleted.getTryCount());
        }
        LOGGER.info("Waited {} times for branch to be deleted post merge", callUntilBranchDeleted.getTryCount());

        List<Entity> postCommitProjectEntities = gitLabEntityApi.getProjectEntityAccessContext(projectId).getEntities(null, null, null);

        Assert.assertNotNull(postCommitProjectEntities);
        Assert.assertEquals(1, postCommitProjectEntities.size());
        Entity projectEntity = postCommitProjectEntities.get(0);
        Assert.assertEquals(projectEntity.getPath(), entityPath);
        Assert.assertEquals(projectEntity.getClassifierPath(), classifierPath);
        Assert.assertEquals(projectEntity.getContent(), entityContentMap);
    }

    public GitLabProjectApi getGitLabProjectApi()
    {
        return gitLabProjectApi;
    }

    private static boolean hasOnlyMasterBranch(List<Branch> branchList)
    {
        return GitLabApiTestSetupUtil.hasOnlyBranchesWithNames(branchList, Lists.mutable.with("master"));
    }
}
