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
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.impl.factory.Maps;
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
 * Substantial test resource class for Workspace API tests shared by the docker-based and server-based GitLab tests.
 */
public class GitLabWorkspaceApiTestResource
{
    private final GitLabWorkspaceApi gitLabWorkspaceApi;
    private final GitLabProjectApi gitLabProjectApi;
    private final GitLabEntityApi gitLabEntityApi;
    private final GitLabReviewApi gitLabCommitterReviewApi;
    private final GitLabReviewApi gitLabApproverReviewApi;

    private final GitLabUserContext gitLabMemberUserContext;

    private static final Logger LOGGER = LoggerFactory.getLogger(GitLabWorkspaceApiTestResource.class);

    public GitLabWorkspaceApiTestResource(GitLabWorkspaceApi gitLabWorkspaceApi, GitLabProjectApi gitLabProjectApi, GitLabEntityApi gitLabEntityApi, GitLabReviewApi gitLabCommitterReviewApi, GitLabReviewApi gitLabApproverReviewApi, GitLabUserContext gitLabMemberUserContext)
    {
        this.gitLabWorkspaceApi = gitLabWorkspaceApi;
        this.gitLabProjectApi = gitLabProjectApi;
        this.gitLabEntityApi = gitLabEntityApi;
        this.gitLabCommitterReviewApi = gitLabCommitterReviewApi;
        this.gitLabApproverReviewApi = gitLabApproverReviewApi;
        this.gitLabMemberUserContext = gitLabMemberUserContext;
    }

    /**
     * Tests Create, Get, GetAll, IsInConflictResolutionMode, InOutdated on user and group workspaces.
     */
    public void runUserAndGroupWorkspaceNormalWorkflowTest()
    {
        String projectName = "WorkspaceTestProjectOne";
        String description = "A test project.";
        ProjectType projectType = ProjectType.PRODUCTION;
        String groupId = "org.finos.sdlc.test";
        String artifactId = "worktestprojone";
        List<String> tags = Lists.mutable.with("doe", "moffitt", AbstractGitLabServerApiTest.INTEGRATION_TEST_PROJECT_TAG);
        String workspaceOneId = "testworkspaceone";
        String workspaceTwoId = "testworkspacetwo";
        String workspaceThreeId = "testworkspacethree";

        Project createdProject = gitLabProjectApi.createProject(projectName, description, projectType, groupId, artifactId, tags);

        Assert.assertNotNull(createdProject);
        Assert.assertEquals(projectName, createdProject.getName());
        Assert.assertEquals(description, createdProject.getDescription());
        Assert.assertEquals(projectType, createdProject.getProjectType());
        Assert.assertEquals(Sets.mutable.withAll(tags), Sets.mutable.withAll(createdProject.getTags()));

        String projectId = createdProject.getProjectId();
        Workspace createdWorkspaceOne = gitLabWorkspaceApi.newUserWorkspace(projectId, workspaceOneId);

        Assert.assertNotNull(createdWorkspaceOne);
        Assert.assertEquals(workspaceOneId, createdWorkspaceOne.getWorkspaceId());
        Assert.assertEquals(projectId, createdWorkspaceOne.getProjectId());
        Assert.assertNotNull(createdWorkspaceOne.getUserId());

        Workspace createdWorkspaceTwo = gitLabWorkspaceApi.newUserWorkspace(projectId, workspaceTwoId);

        Assert.assertNotNull(createdWorkspaceTwo);
        Assert.assertEquals(workspaceTwoId, createdWorkspaceTwo.getWorkspaceId());
        Assert.assertEquals(projectId, createdWorkspaceTwo.getProjectId());
        Assert.assertNotNull(createdWorkspaceTwo.getUserId());

        Workspace createdWorkspaceThree = gitLabWorkspaceApi.newGroupWorkspace(projectId, workspaceThreeId);

        Assert.assertNotNull(createdWorkspaceThree);
        Assert.assertEquals(workspaceThreeId, createdWorkspaceThree.getWorkspaceId());
        Assert.assertEquals(projectId, createdWorkspaceThree.getProjectId());
        Assert.assertNull(createdWorkspaceThree.getUserId());

        List<Workspace> allWorkspaces = gitLabWorkspaceApi.getAllWorkspaces(projectId);
        List<Workspace> allUserWorkspaces = gitLabWorkspaceApi.getAllUserWorkspaces(projectId);
        List<Workspace> allGroupWorkspaces = gitLabWorkspaceApi.getGroupWorkspaces(projectId);

        Assert.assertNotNull(allWorkspaces);
        Assert.assertNotNull(allUserWorkspaces);
        Assert.assertNotNull(allGroupWorkspaces);
        Assert.assertEquals(3, allWorkspaces.size());
        Assert.assertEquals(2, allUserWorkspaces.size());
        Assert.assertEquals(1, allGroupWorkspaces.size());

        Workspace retriedUserWorkspace = gitLabWorkspaceApi.getUserWorkspace(projectId, workspaceOneId);

        Assert.assertNotNull(retriedUserWorkspace);
        Assert.assertEquals(workspaceOneId, retriedUserWorkspace.getWorkspaceId());
        Assert.assertEquals(projectId, retriedUserWorkspace.getProjectId());
        Assert.assertNotNull(retriedUserWorkspace.getUserId());

        Workspace retriedGroupWorkspace = gitLabWorkspaceApi.getGroupWorkspace(projectId, workspaceThreeId);

        Assert.assertNotNull(retriedGroupWorkspace);
        Assert.assertEquals(workspaceThreeId, retriedGroupWorkspace.getWorkspaceId());
        Assert.assertEquals(projectId, retriedGroupWorkspace.getProjectId());
        Assert.assertNull(retriedGroupWorkspace.getUserId());

        boolean isUserWorkspaceInConflictResolution = gitLabWorkspaceApi.isUserWorkspaceInConflictResolutionMode(projectId, workspaceOneId);
        boolean isGroupWorkspaceInConflictResolution = gitLabWorkspaceApi.isGroupWorkspaceInConflictResolutionMode(projectId, workspaceThreeId);

        Assert.assertFalse(isUserWorkspaceInConflictResolution);
        Assert.assertFalse(isGroupWorkspaceInConflictResolution);

        boolean isUserWorkspaceOutdated = gitLabWorkspaceApi.isUserWorkspaceOutdated(projectId, workspaceOneId);
        boolean isGroupWorkspaceOutdated = gitLabWorkspaceApi.isGroupWorkspaceOutdated(projectId, workspaceThreeId);

        Assert.assertFalse(isUserWorkspaceOutdated);
        Assert.assertFalse(isGroupWorkspaceOutdated);
    }

    public void runUpdateWorkspaceWithRebaseNoConflictTest() throws GitLabApiException //TODO: test group once done
    {
        // Create new workspace from previous HEAD
        String projectName = "WorkspaceTestProjectThree";
        String description = "A test project.";
        ProjectType projectType = ProjectType.PRODUCTION;
        String groupId = "org.finos.sdlc.test";
        String artifactId = "testworkprojthree";
        List<String> tags = Lists.mutable.with("doe", "moffitt", AbstractGitLabServerApiTest.INTEGRATION_TEST_PROJECT_TAG);
        String workspaceName = "workspaceone";

        Project createdProject = gitLabProjectApi.createProject(projectName, description, projectType, groupId, artifactId, tags);

        String projectId = createdProject.getProjectId();
        Workspace createdWorkspace = gitLabWorkspaceApi.newUserWorkspace(projectId, workspaceName);

        String workspaceId = createdWorkspace.getWorkspaceId();
        List<Entity> initialWorkspaceEntities = gitLabEntityApi.getWorkspaceEntityAccessContext(projectId, workspaceId).getEntities(null, null, null);
        List<Entity> initialProjectEntities = gitLabEntityApi.getProjectEntityAccessContext(projectId).getEntities(null, null, null);

        Assert.assertEquals(Collections.emptyList(), initialWorkspaceEntities);
        Assert.assertEquals(Collections.emptyList(), initialProjectEntities);

        // Create another workspace, commit, review, merge to move project HEAD forward -- use workspace two
        String workspaceTwoName = "workspacetwo";
        Workspace createdWorkspaceTwo = gitLabWorkspaceApi.newUserWorkspace(projectId, workspaceTwoName);
        String workspaceTwoId = createdWorkspaceTwo.getWorkspaceId();
        List<Entity> initialWorkspaceTwoEntities = gitLabEntityApi.getWorkspaceEntityAccessContext(projectId, workspaceTwoId).getEntities(null, null, null);

        Assert.assertEquals(Collections.emptyList(), initialWorkspaceTwoEntities);

        String entityPath = "test::entity";
        String classifierPath = "meta::test::mathematicsDepartment";
        Map<String, String> entityContentMap = Maps.mutable.with(
                "package", "test",
                "name", "entity",
                "math-113", "abstract-algebra",
                "math-185", "complex-analysis");
        gitLabEntityApi.getWorkspaceEntityModificationContext(projectId, workspaceTwoId).createEntity(entityPath, classifierPath, entityContentMap, "initial entity");
        List<Entity> modifiedWorkspaceEntities = gitLabEntityApi.getWorkspaceEntityAccessContext(projectId, workspaceTwoId).getEntities(null, null, null);

        Assert.assertNotNull(modifiedWorkspaceEntities);
        Assert.assertEquals(1, modifiedWorkspaceEntities.size());
        Entity initalEntity = modifiedWorkspaceEntities.get(0);
        Assert.assertEquals(initalEntity.getPath(), entityPath);
        Assert.assertEquals(initalEntity.getClassifierPath(), classifierPath);
        Assert.assertEquals(initalEntity.getContent(), entityContentMap);

        Review testReview = gitLabCommitterReviewApi.createReview(projectId, workspaceTwoId, "Add Courses.", "add two math courses");
        String reviewId = testReview.getId();
        Review approvedReview = gitLabApproverReviewApi.approveReview(projectId, reviewId);

        Assert.assertNotNull(approvedReview);
        Assert.assertEquals(reviewId, approvedReview.getId());
        Assert.assertEquals(ReviewState.OPEN, approvedReview.getState());

        GitLabProjectId sdlcGitLabProjectId = GitLabProjectId.parseProjectId(projectId);
        MergeRequestApi mergeRequestApi = gitLabMemberUserContext.getGitLabAPI(sdlcGitLabProjectId.getGitLabMode()).getMergeRequestApi();
        Integer parsedMergeRequestId = Integer.parseInt(reviewId);
        Integer gitlabProjectId = sdlcGitLabProjectId.getGitLabId();

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
                branches -> GitLabApiTestSetupUtil.hasOnlyBranchesWithNames(branches, Lists.mutable.of(workspaceName, "master")),
                15,
                1000);
        if (!callUntilBranchDeleted.succeeded())
        {
            // Warn instead of throwing exception since we cannot manage time expectation on GitLab to reflect branch deletion.
            LOGGER.warn("Branch {} is still not deleted post merge after {} tries", workspaceTwoName, callUntilBranchDeleted.getTryCount());
        }
        LOGGER.info("Waited {} times for branch {} to be deleted post merge", callUntilBranchDeleted.getTryCount(), workspaceTwoName);

        List<Entity> postCommitProjectEntities = gitLabEntityApi.getProjectEntityAccessContext(projectId).getEntities(null, null, null);

        Assert.assertNotNull(postCommitProjectEntities);
        Assert.assertEquals(1, postCommitProjectEntities.size());
        Entity projectEntity = postCommitProjectEntities.get(0);
        Assert.assertEquals(projectEntity.getPath(), entityPath);
        Assert.assertEquals(projectEntity.getClassifierPath(), classifierPath);
        Assert.assertEquals(projectEntity.getContent(), entityContentMap);

        // Create changes and make change in workspace branch -- use workspace
        Map<String, String> currentEntityContentMap = Maps.mutable.with(
                "package", "test",
                "name", "entity",
                "math-113", "abstract-algebra",
                "math-185", "complex-analysis");
        gitLabEntityApi.getWorkspaceEntityModificationContext(projectId, workspaceId).createEntity(entityPath, classifierPath, currentEntityContentMap, "initial entity");
        List<Entity> modifiedWorkspaceEntitiesNew = gitLabEntityApi.getWorkspaceEntityAccessContext(projectId, workspaceId).getEntities(null, null, null);

        Assert.assertNotNull(modifiedWorkspaceEntitiesNew);
        Assert.assertEquals(1, modifiedWorkspaceEntities.size());
        Entity initalEntityNew = modifiedWorkspaceEntitiesNew.get(0);
        Assert.assertEquals(initalEntityNew.getPath(), entityPath);
        Assert.assertEquals(initalEntityNew.getClassifierPath(), classifierPath);
        Assert.assertEquals(initalEntityNew.getContent(), currentEntityContentMap);

        // Update workspace branch and trigger rebase
        gitLabWorkspaceApi.updateUserWorkspace(projectId, workspaceId);
        List<Entity> updatedWorkspaceEntities = gitLabEntityApi.getWorkspaceEntityAccessContext(projectId, workspaceId).getEntities(null, null, null);

        Assert.assertNotNull(updatedWorkspaceEntities);
        Assert.assertEquals(1, updatedWorkspaceEntities.size());
        Entity updatedEntity = updatedWorkspaceEntities.get(0);
        Assert.assertEquals(updatedEntity.getPath(), entityPath);
        Assert.assertEquals(updatedEntity.getClassifierPath(), classifierPath);
        Assert.assertEquals(updatedEntity.getContent(), currentEntityContentMap);
    }

    public GitLabProjectApi getGitLabProjectApi()
    {
        return gitLabProjectApi;
    }
}
