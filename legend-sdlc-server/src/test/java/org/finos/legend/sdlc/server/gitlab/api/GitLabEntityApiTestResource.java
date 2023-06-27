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
import org.finos.legend.sdlc.domain.model.patch.Patch;
import org.finos.legend.sdlc.domain.model.project.Project;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.review.Review;
import org.finos.legend.sdlc.domain.model.review.ReviewState;
import org.finos.legend.sdlc.domain.model.version.Version;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.domain.api.version.NewVersionType;
import org.finos.legend.sdlc.server.domain.api.project.SourceSpecification;
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
    private final GitLabPatchApi gitlabPatchApi;
    private final GitLabVersionApi gitlabVersionApi;
    private final GitLabRevisionApi gitLabRevisionApi;

    private final GitLabUserContext gitLabMemberUserContext;

    private static final Logger LOGGER = LoggerFactory.getLogger(GitLabEntityApiTestResource.class);

    public GitLabEntityApiTestResource(GitLabProjectApi gitLabProjectApi, GitLabWorkspaceApi gitLabWorkspaceApi, GitLabEntityApi gitLabEntityApi, GitLabReviewApi gitLabCommitterReviewApi, GitLabReviewApi gitLabApproverReviewApi, GitLabUserContext gitLabMemberUserContext, GitLabPatchApi gitlabPatchAPi, GitLabVersionApi gitlabVersionApi, GitLabRevisionApi gitLabRevisionApi)
    {
        this.gitLabProjectApi = gitLabProjectApi;
        this.gitLabWorkspaceApi = gitLabWorkspaceApi;
        this.gitLabEntityApi = gitLabEntityApi;
        this.gitLabCommitterReviewApi = gitLabCommitterReviewApi;
        this.gitLabApproverReviewApi = gitLabApproverReviewApi;
        this.gitLabMemberUserContext = gitLabMemberUserContext;
        this.gitlabPatchApi = gitlabPatchAPi;
        this.gitlabVersionApi = gitlabVersionApi;
        this.gitLabRevisionApi = gitLabRevisionApi;
    }

    public void runEntitiesInNormalUserWorkspaceWorkflowTest() throws GitLabApiException
    {
        String projectName = "CommitFlowTestProject";
        String description = "A test project.";
        String groupId = "org.finos.sdlc.test";
        String artifactId = "entitytestproj";
        List<String> tags = Lists.mutable.with("doe", "moffitt", AbstractGitLabServerApiTest.INTEGRATION_TEST_PROJECT_TAG);
        String workspaceName = "entitytestworkspace";

        Project createdProject = gitLabProjectApi.createProject(projectName, description, ProjectType.MANAGED, groupId, artifactId, tags);

        String projectId = createdProject.getProjectId();
        Workspace createdWorkspace = gitLabWorkspaceApi.newUserWorkspace(projectId, workspaceName);

        String workspaceId = createdWorkspace.getWorkspaceId();
        List<Entity> initialWorkspaceEntities = gitLabEntityApi.getUserWorkspaceEntityAccessContext(projectId, workspaceId).getEntities(null, null, null);
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
        gitLabEntityApi.getUserWorkspaceEntityModificationContext(projectId, workspaceId).createEntity(entityPath, classifierPath, entityContentMap, "initial entity");
        List<Entity> modifiedWorkspaceEntities = gitLabEntityApi.getUserWorkspaceEntityAccessContext(projectId, workspaceId).getEntities(null, null, null);
        List<Entity> modifiedProjectEntities = gitLabEntityApi.getProjectEntityAccessContext(projectId).getEntities(null, null, null);

        Assert.assertNotNull(modifiedWorkspaceEntities);
        Assert.assertEquals(Collections.emptyList(), modifiedProjectEntities);
        Assert.assertEquals(1, modifiedWorkspaceEntities.size());
        Entity initalEntity = modifiedWorkspaceEntities.get(0);
        Assert.assertEquals(initalEntity.getPath(), entityPath);
        Assert.assertEquals(initalEntity.getClassifierPath(), classifierPath);
        Assert.assertEquals(initalEntity.getContent(), entityContentMap);

        Map<String, String> newEntityContentMap = Maps.mutable.with(
            "package", "test",
            "name", "entity",
            "math-128", "numerical-analysis",
            "math-110", "linear-algebra");
        gitLabEntityApi.getUserWorkspaceEntityModificationContext(projectId, workspaceId).updateEntity(entityPath, classifierPath, newEntityContentMap, "update entity");
        List<Entity> updatedWorkspaceEntities = gitLabEntityApi.getUserWorkspaceEntityAccessContext(projectId, workspaceId).getEntities(null, null, null);

        Assert.assertNotNull(updatedWorkspaceEntities);
        Assert.assertEquals(1, updatedWorkspaceEntities.size());
        Entity updatedEntity = updatedWorkspaceEntities.get(0);
        Assert.assertEquals(updatedEntity.getPath(), entityPath);
        Assert.assertEquals(updatedEntity.getClassifierPath(), classifierPath);
        Assert.assertEquals(updatedEntity.getContent(), newEntityContentMap);

        String entityPathTwo = "testtwo::entitytwo";
        String classifierPathTwo = "meta::test::csDepartment";
        Map<String, String> newEntityContentMapTwo = Maps.mutable.with(
            "package", "testtwo",
            "name", "entitytwo",
            "cs-194", "computational-imaging",
            "cs-189", "machine-learning");
        gitLabEntityApi.getUserWorkspaceEntityModificationContext(projectId, workspaceId).createEntity(entityPathTwo, classifierPathTwo, newEntityContentMapTwo, "second entity");
        List<Entity> postAddWorkspaceEntities = gitLabEntityApi.getUserWorkspaceEntityAccessContext(projectId, workspaceId).getEntities(null, null, null);

        Assert.assertNotNull(postAddWorkspaceEntities);
        Assert.assertEquals(2, postAddWorkspaceEntities.size());

        gitLabEntityApi.getUserWorkspaceEntityModificationContext(projectId, workspaceId).deleteEntity(entityPath, classifierPath);
        List<Entity> postDeleteWorkspaceEntities = gitLabEntityApi.getUserWorkspaceEntityAccessContext(projectId, workspaceId).getEntities(null, null, null);

        Assert.assertNotNull(postDeleteWorkspaceEntities);
        Assert.assertEquals(1, postDeleteWorkspaceEntities.size());
        Entity remainedEntity = postDeleteWorkspaceEntities.get(0);
        Assert.assertEquals(remainedEntity.getPath(), entityPathTwo);
        Assert.assertEquals(remainedEntity.getClassifierPath(), classifierPathTwo);
        Assert.assertEquals(remainedEntity.getContent(), newEntityContentMapTwo);

        List<String> paths = gitLabEntityApi.getUserWorkspaceEntityAccessContext(projectId, workspaceId).getEntityPaths(null, null, null);

        Assert.assertNotNull(paths);
        Assert.assertEquals(1, paths.size());
        Assert.assertEquals(entityPathTwo, paths.get(0));
        List<String> labels = Collections.singletonList("default");

        Review testReview = gitLabCommitterReviewApi.createReview(projectId, workspaceId, WorkspaceType.USER, "Add Courses.", "add two courses", labels);
        String reviewId = testReview.getId();
        Review approvedReview = gitLabApproverReviewApi.approveReview(projectId, reviewId);

        Assert.assertNotNull(approvedReview);
        Assert.assertEquals(reviewId, approvedReview.getId());
        Assert.assertEquals(ReviewState.OPEN, approvedReview.getState());
        Assert.assertEquals(labels, approvedReview.getLabels());

        GitLabProjectId sdlcGitLabProjectId = GitLabProjectId.parseProjectId(projectId);
        MergeRequestApi mergeRequestApi = gitLabMemberUserContext.getGitLabAPI().getMergeRequestApi();
        int parsedMergeRequestId = Integer.parseInt(reviewId);
        int gitlabProjectId = sdlcGitLabProjectId.getGitLabId();

        String requiredStatus = "can_be_merged";
        CallUntil<MergeRequest, GitLabApiException> callUntil = CallUntil.callUntil(
            () -> mergeRequestApi.getMergeRequest(gitlabProjectId, parsedMergeRequestId),
            mr -> requiredStatus.equals(mr.getMergeStatus()),
            20,
            1000);
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

        RepositoryApi repositoryApi = gitLabMemberUserContext.getGitLabAPI().getRepositoryApi();
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
        Assert.assertEquals(projectEntity.getPath(), entityPathTwo);
        Assert.assertEquals(projectEntity.getClassifierPath(), classifierPathTwo);
        Assert.assertEquals(projectEntity.getContent(), newEntityContentMapTwo);
    }

    public void runEntitiesInNormalGroupWorkspaceWorkflowTest() throws GitLabApiException
    {
        String projectName = "CommitFlowTestProjectTwo";
        String description = "A test project.";
        String groupId = "org.finos.sdlc.test";
        String artifactId = "entitytestprojtwo";
        List<String> tags = Lists.mutable.with("doe", "moffitt", AbstractGitLabServerApiTest.INTEGRATION_TEST_PROJECT_TAG);
        String workspaceName = "entitytestworkspace";

        Project createdProject = gitLabProjectApi.createProject(projectName, description, ProjectType.MANAGED, groupId, artifactId, tags);

        String projectId = createdProject.getProjectId();
        Workspace createdWorkspace = gitLabWorkspaceApi.newGroupWorkspace(projectId, workspaceName);

        String workspaceId = createdWorkspace.getWorkspaceId();
        List<Entity> initialWorkspaceEntities = gitLabEntityApi.getGroupWorkspaceEntityAccessContext(projectId, workspaceId).getEntities(null, null, null);
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
        gitLabEntityApi.getGroupWorkspaceEntityModificationContext(projectId, workspaceId).createEntity(entityPath, classifierPath, entityContentMap, "initial entity");
        List<Entity> modifiedWorkspaceEntities = gitLabEntityApi.getGroupWorkspaceEntityAccessContext(projectId, workspaceId).getEntities(null, null, null);
        List<Entity> modifiedProjectEntities = gitLabEntityApi.getProjectEntityAccessContext(projectId).getEntities(null, null, null);

        Assert.assertNotNull(modifiedWorkspaceEntities);
        Assert.assertEquals(Collections.emptyList(), modifiedProjectEntities);
        Assert.assertEquals(1, modifiedWorkspaceEntities.size());
        Entity initalEntity = modifiedWorkspaceEntities.get(0);
        Assert.assertEquals(initalEntity.getPath(), entityPath);
        Assert.assertEquals(initalEntity.getClassifierPath(), classifierPath);
        Assert.assertEquals(initalEntity.getContent(), entityContentMap);

        Map<String, String> newEntityContentMap = Maps.mutable.with(
            "package", "test",
            "name", "entity",
            "math-128", "numerical-analysis",
            "math-110", "linear-algebra");
        gitLabEntityApi.getGroupWorkspaceEntityModificationContext(projectId, workspaceId).updateEntity(entityPath, classifierPath, newEntityContentMap, "update entity");
        List<Entity> updatedWorkspaceEntities = gitLabEntityApi.getGroupWorkspaceEntityAccessContext(projectId, workspaceId).getEntities(null, null, null);

        Assert.assertNotNull(updatedWorkspaceEntities);
        Assert.assertEquals(1, updatedWorkspaceEntities.size());
        Entity updatedEntity = updatedWorkspaceEntities.get(0);
        Assert.assertEquals(updatedEntity.getPath(), entityPath);
        Assert.assertEquals(updatedEntity.getClassifierPath(), classifierPath);
        Assert.assertEquals(updatedEntity.getContent(), newEntityContentMap);

        String entityPathTwo = "testtwo::entitytwo";
        String classifierPathTwo = "meta::test::csDepartment";
        Map<String, String> newEntityContentMapTwo = Maps.mutable.with(
            "package", "testtwo",
            "name", "entitytwo",
            "cs-194", "computational-imaging",
            "cs-189", "machine-learning");
        gitLabEntityApi.getGroupWorkspaceEntityModificationContext(projectId, workspaceId).createEntity(entityPathTwo, classifierPathTwo, newEntityContentMapTwo, "second entity");
        List<Entity> postAddWorkspaceEntities = gitLabEntityApi.getGroupWorkspaceEntityAccessContext(projectId, workspaceId).getEntities(null, null, null);

        Assert.assertNotNull(postAddWorkspaceEntities);
        Assert.assertEquals(2, postAddWorkspaceEntities.size());

        gitLabEntityApi.getGroupWorkspaceEntityModificationContext(projectId, workspaceId).deleteEntity(entityPath, classifierPath);
        List<Entity> postDeleteWorkspaceEntities = gitLabEntityApi.getGroupWorkspaceEntityAccessContext(projectId, workspaceId).getEntities(null, null, null);

        Assert.assertNotNull(postDeleteWorkspaceEntities);
        Assert.assertEquals(1, postDeleteWorkspaceEntities.size());
        Entity remainedEntity = postDeleteWorkspaceEntities.get(0);
        Assert.assertEquals(remainedEntity.getPath(), entityPathTwo);
        Assert.assertEquals(remainedEntity.getClassifierPath(), classifierPathTwo);
        Assert.assertEquals(remainedEntity.getContent(), newEntityContentMapTwo);

        List<String> paths = gitLabEntityApi.getGroupWorkspaceEntityAccessContext(projectId, workspaceId).getEntityPaths(null, null, null);

        Assert.assertNotNull(paths);
        Assert.assertEquals(1, paths.size());
        Assert.assertEquals(entityPathTwo, paths.get(0));

        List<String> labels = Collections.singletonList("default");
        Review testReview = gitLabCommitterReviewApi.createReview(projectId, workspaceId, WorkspaceType.GROUP, "Add Courses.", "add two courses", labels);
        String reviewId = testReview.getId();
        Review approvedReview = gitLabApproverReviewApi.approveReview(projectId, reviewId);

        Assert.assertNotNull(approvedReview);
        Assert.assertEquals(reviewId, approvedReview.getId());
        Assert.assertEquals(ReviewState.OPEN, approvedReview.getState());

        GitLabProjectId sdlcGitLabProjectId = GitLabProjectId.parseProjectId(projectId);
        MergeRequestApi mergeRequestApi = gitLabMemberUserContext.getGitLabAPI().getMergeRequestApi();
        int parsedMergeRequestId = Integer.parseInt(reviewId);
        int gitlabProjectId = sdlcGitLabProjectId.getGitLabId();

        String requiredStatus = "can_be_merged";
        CallUntil<MergeRequest, GitLabApiException> callUntil = CallUntil.callUntil(
            () -> mergeRequestApi.getMergeRequest(gitlabProjectId, parsedMergeRequestId),
            mr -> requiredStatus.equals(mr.getMergeStatus()),
            20,
            1000);
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

        RepositoryApi repositoryApi = gitLabMemberUserContext.getGitLabAPI().getRepositoryApi();
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
        Assert.assertEquals(projectEntity.getPath(), entityPathTwo);
        Assert.assertEquals(projectEntity.getClassifierPath(), classifierPathTwo);
        Assert.assertEquals(projectEntity.getContent(), newEntityContentMapTwo);
    }

    public void runEntitiesInNormalUserWorkspaceWorkflowTestForPatchRelaseVersion() throws GitLabApiException
    {
        String projectName = "CommitFlowTestProjectThree";
        String description = "A test project.";
        String groupId = "org.finos.sdlc.test";
        String artifactId = "entitytestprojthree";
        List<String> tags = Lists.mutable.with("doe", "moffitt", AbstractGitLabServerApiTest.INTEGRATION_TEST_PROJECT_TAG);
        String workspaceName = "entitytestworkspace";

        Project createdProject = gitLabProjectApi.createProject(projectName, description, ProjectType.MANAGED, groupId, artifactId, tags);

        String projectId = createdProject.getProjectId();
        Version version = gitlabVersionApi.newVersion(projectId, NewVersionType.PATCH, gitLabRevisionApi.getProjectRevisionContext(projectId).getCurrentRevision().getId(), "");
        Patch patch = gitlabPatchApi.newPatch(projectId, version.getId());
        VersionId patchReleaseVersionId = patch.getPatchReleaseVersionId();
        SourceSpecification sourceSpecification = SourceSpecification.newUserWorkspaceSourceSpecification(workspaceName, patchReleaseVersionId);

        Workspace createdWorkspace = gitLabWorkspaceApi.newWorkspace(projectId, sourceSpecification);

        String workspaceId = createdWorkspace.getWorkspaceId();
        List<Entity> initialWorkspaceEntities = gitLabEntityApi.getWorkspaceEntityAccessContext(projectId, sourceSpecification).getEntities(null, null, null);
        List<Entity> initialProjectEntities = gitLabEntityApi.getProjectEntityAccessContext(projectId, patchReleaseVersionId).getEntities(null, null, null);

        Assert.assertEquals(Collections.emptyList(), initialWorkspaceEntities);
        Assert.assertEquals(Collections.emptyList(), initialProjectEntities);

        String entityPath = "test::entity";
        String classifierPath = "meta::test::mathematicsDepartment";
        Map<String, String> entityContentMap = Maps.mutable.with(
                "package", "test",
                "name", "entity",
                "math-113", "abstract-algebra",
                "math-185", "complex-analysis");
        gitLabEntityApi.getWorkspaceEntityModificationContext(projectId, sourceSpecification).createEntity(entityPath, classifierPath, entityContentMap, "initial entity");
        List<Entity> modifiedWorkspaceEntities = gitLabEntityApi.getWorkspaceEntityAccessContext(projectId, sourceSpecification).getEntities(null, null, null);
        List<Entity> modifiedProjectEntities = gitLabEntityApi.getProjectEntityAccessContext(projectId, patchReleaseVersionId).getEntities(null, null, null);

        Assert.assertNotNull(modifiedWorkspaceEntities);
        Assert.assertEquals(Collections.emptyList(), modifiedProjectEntities);
        Assert.assertEquals(1, modifiedWorkspaceEntities.size());
        Entity initalEntity = modifiedWorkspaceEntities.get(0);
        Assert.assertEquals(initalEntity.getPath(), entityPath);
        Assert.assertEquals(initalEntity.getClassifierPath(), classifierPath);
        Assert.assertEquals(initalEntity.getContent(), entityContentMap);

        Map<String, String> newEntityContentMap = Maps.mutable.with(
                "package", "test",
                "name", "entity",
                "math-128", "numerical-analysis",
                "math-110", "linear-algebra");
        gitLabEntityApi.getWorkspaceEntityModificationContext(projectId, sourceSpecification).updateEntity(entityPath, classifierPath, newEntityContentMap, "update entity");
        List<Entity> updatedWorkspaceEntities = gitLabEntityApi.getWorkspaceEntityAccessContext(projectId, sourceSpecification).getEntities(null, null, null);

        Assert.assertNotNull(updatedWorkspaceEntities);
        Assert.assertEquals(1, updatedWorkspaceEntities.size());
        Entity updatedEntity = updatedWorkspaceEntities.get(0);
        Assert.assertEquals(updatedEntity.getPath(), entityPath);
        Assert.assertEquals(updatedEntity.getClassifierPath(), classifierPath);
        Assert.assertEquals(updatedEntity.getContent(), newEntityContentMap);

        String entityPathTwo = "testtwo::entitytwo";
        String classifierPathTwo = "meta::test::csDepartment";
        Map<String, String> newEntityContentMapTwo = Maps.mutable.with(
                "package", "testtwo",
                "name", "entitytwo",
                "cs-194", "computational-imaging",
                "cs-189", "machine-learning");
        gitLabEntityApi.getWorkspaceEntityModificationContext(projectId, sourceSpecification).createEntity(entityPathTwo, classifierPathTwo, newEntityContentMapTwo, "second entity");
        List<Entity> postAddWorkspaceEntities = gitLabEntityApi.getWorkspaceEntityAccessContext(projectId, sourceSpecification).getEntities(null, null, null);

        Assert.assertNotNull(postAddWorkspaceEntities);
        Assert.assertEquals(2, postAddWorkspaceEntities.size());

        gitLabEntityApi.getWorkspaceEntityModificationContext(projectId, sourceSpecification).deleteEntity(entityPath, classifierPath);
        List<Entity> postDeleteWorkspaceEntities = gitLabEntityApi.getWorkspaceEntityAccessContext(projectId, sourceSpecification).getEntities(null, null, null);

        Assert.assertNotNull(postDeleteWorkspaceEntities);
        Assert.assertEquals(1, postDeleteWorkspaceEntities.size());
        Entity remainedEntity = postDeleteWorkspaceEntities.get(0);
        Assert.assertEquals(remainedEntity.getPath(), entityPathTwo);
        Assert.assertEquals(remainedEntity.getClassifierPath(), classifierPathTwo);
        Assert.assertEquals(remainedEntity.getContent(), newEntityContentMapTwo);

        List<String> paths = gitLabEntityApi.getWorkspaceEntityAccessContext(projectId, sourceSpecification).getEntityPaths(null, null, null);

        Assert.assertNotNull(paths);
        Assert.assertEquals(1, paths.size());
        Assert.assertEquals(entityPathTwo, paths.get(0));
        List<String> labels = Collections.singletonList("default");

        Review testReview = gitLabCommitterReviewApi.createReview(projectId, sourceSpecification, "Add Courses.", "add two courses", labels);
        String reviewId = testReview.getId();
        Review approvedReview = gitLabApproverReviewApi.approveReview(projectId, patchReleaseVersionId, reviewId);

        Assert.assertNotNull(approvedReview);
        Assert.assertEquals(reviewId, approvedReview.getId());
        Assert.assertEquals(ReviewState.OPEN, approvedReview.getState());
        Assert.assertEquals(labels, approvedReview.getLabels());

        GitLabProjectId sdlcGitLabProjectId = GitLabProjectId.parseProjectId(projectId);
        MergeRequestApi mergeRequestApi = gitLabMemberUserContext.getGitLabAPI().getMergeRequestApi();
        int parsedMergeRequestId = Integer.parseInt(reviewId);
        int gitlabProjectId = sdlcGitLabProjectId.getGitLabId();

        String requiredStatus = "can_be_merged";
        CallUntil<MergeRequest, GitLabApiException> callUntil = CallUntil.callUntil(
                () -> mergeRequestApi.getMergeRequest(gitlabProjectId, parsedMergeRequestId),
                mr -> requiredStatus.equals(mr.getMergeStatus()),
                20,
                1000);
        if (!callUntil.succeeded())
        {
            throw new RuntimeException("Merge request " + approvedReview.getId() + " still does not have status \"" + requiredStatus + "\" after " + callUntil.getTryCount() + " tries");
        }
        LOGGER.info("Waited {} times for merge to have status \"{}\"", callUntil.getTryCount(), requiredStatus);

        gitLabCommitterReviewApi.commitReview(projectId, patchReleaseVersionId, reviewId, "add two math courses");

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

        RepositoryApi repositoryApi = gitLabMemberUserContext.getGitLabAPI().getRepositoryApi();
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

        List<Entity> postCommitProjectEntities = gitLabEntityApi.getProjectEntityAccessContext(projectId, patchReleaseVersionId).getEntities(null, null, null);

        Assert.assertNotNull(postCommitProjectEntities);
        Assert.assertEquals(1, postCommitProjectEntities.size());
        Entity projectEntity = postCommitProjectEntities.get(0);
        Assert.assertEquals(projectEntity.getPath(), entityPathTwo);
        Assert.assertEquals(projectEntity.getClassifierPath(), classifierPathTwo);
        Assert.assertEquals(projectEntity.getContent(), newEntityContentMapTwo);
    }

    public void runEntitiesInNormalGroupWorkspaceWorkflowTestForPatchRelaseVersion() throws GitLabApiException
    {
        String projectName = "CommitFlowTestProjectFour";
        String description = "A test project.";
        String groupId = "org.finos.sdlc.test";
        String artifactId = "entitytestprojfour";
        List<String> tags = Lists.mutable.with("doe", "moffitt", AbstractGitLabServerApiTest.INTEGRATION_TEST_PROJECT_TAG);
        String workspaceName = "entitytestworkspace";

        Project createdProject = gitLabProjectApi.createProject(projectName, description, ProjectType.MANAGED, groupId, artifactId, tags);

        String projectId = createdProject.getProjectId();
        Version version = gitlabVersionApi.newVersion(projectId, NewVersionType.PATCH, gitLabRevisionApi.getProjectRevisionContext(projectId).getCurrentRevision().getId(), "");
        Patch patch = gitlabPatchApi.newPatch(projectId, version.getId());
        VersionId patchReleaseVersionId = patch.getPatchReleaseVersionId();
        SourceSpecification sourceSpecification = SourceSpecification.newGroupWorkspaceSourceSpecification(workspaceName, patchReleaseVersionId);

        Workspace createdWorkspace = gitLabWorkspaceApi.newWorkspace(projectId, sourceSpecification);

        String workspaceId = createdWorkspace.getWorkspaceId();
        List<Entity> initialWorkspaceEntities = gitLabEntityApi.getWorkspaceEntityAccessContext(projectId, sourceSpecification).getEntities(null, null, null);
        List<Entity> initialProjectEntities = gitLabEntityApi.getProjectEntityAccessContext(projectId, patchReleaseVersionId).getEntities(null, null, null);

        Assert.assertEquals(Collections.emptyList(), initialWorkspaceEntities);
        Assert.assertEquals(Collections.emptyList(), initialProjectEntities);

        String entityPath = "test::entity";
        String classifierPath = "meta::test::mathematicsDepartment";
        Map<String, String> entityContentMap = Maps.mutable.with(
                "package", "test",
                "name", "entity",
                "math-113", "abstract-algebra",
                "math-185", "complex-analysis");
        gitLabEntityApi.getWorkspaceEntityModificationContext(projectId, sourceSpecification).createEntity(entityPath, classifierPath, entityContentMap, "initial entity");
        List<Entity> modifiedWorkspaceEntities = gitLabEntityApi.getWorkspaceEntityAccessContext(projectId, sourceSpecification).getEntities(null, null, null);
        List<Entity> modifiedProjectEntities = gitLabEntityApi.getProjectEntityAccessContext(projectId, patchReleaseVersionId).getEntities(null, null, null);

        Assert.assertNotNull(modifiedWorkspaceEntities);
        Assert.assertEquals(Collections.emptyList(), modifiedProjectEntities);
        Assert.assertEquals(1, modifiedWorkspaceEntities.size());
        Entity initalEntity = modifiedWorkspaceEntities.get(0);
        Assert.assertEquals(initalEntity.getPath(), entityPath);
        Assert.assertEquals(initalEntity.getClassifierPath(), classifierPath);
        Assert.assertEquals(initalEntity.getContent(), entityContentMap);

        Map<String, String> newEntityContentMap = Maps.mutable.with(
                "package", "test",
                "name", "entity",
                "math-128", "numerical-analysis",
                "math-110", "linear-algebra");
        gitLabEntityApi.getWorkspaceEntityModificationContext(projectId, sourceSpecification).updateEntity(entityPath, classifierPath, newEntityContentMap, "update entity");
        List<Entity> updatedWorkspaceEntities = gitLabEntityApi.getWorkspaceEntityAccessContext(projectId, sourceSpecification).getEntities(null, null, null);

        Assert.assertNotNull(updatedWorkspaceEntities);
        Assert.assertEquals(1, updatedWorkspaceEntities.size());
        Entity updatedEntity = updatedWorkspaceEntities.get(0);
        Assert.assertEquals(updatedEntity.getPath(), entityPath);
        Assert.assertEquals(updatedEntity.getClassifierPath(), classifierPath);
        Assert.assertEquals(updatedEntity.getContent(), newEntityContentMap);

        String entityPathTwo = "testtwo::entitytwo";
        String classifierPathTwo = "meta::test::csDepartment";
        Map<String, String> newEntityContentMapTwo = Maps.mutable.with(
                "package", "testtwo",
                "name", "entitytwo",
                "cs-194", "computational-imaging",
                "cs-189", "machine-learning");
        gitLabEntityApi.getWorkspaceEntityModificationContext(projectId, sourceSpecification).createEntity(entityPathTwo, classifierPathTwo, newEntityContentMapTwo, "second entity");
        List<Entity> postAddWorkspaceEntities = gitLabEntityApi.getWorkspaceEntityAccessContext(projectId, sourceSpecification).getEntities(null, null, null);

        Assert.assertNotNull(postAddWorkspaceEntities);
        Assert.assertEquals(2, postAddWorkspaceEntities.size());

        gitLabEntityApi.getWorkspaceEntityModificationContext(projectId, sourceSpecification).deleteEntity(entityPath, classifierPath);
        List<Entity> postDeleteWorkspaceEntities = gitLabEntityApi.getWorkspaceEntityAccessContext(projectId, sourceSpecification).getEntities(null, null, null);

        Assert.assertNotNull(postDeleteWorkspaceEntities);
        Assert.assertEquals(1, postDeleteWorkspaceEntities.size());
        Entity remainedEntity = postDeleteWorkspaceEntities.get(0);
        Assert.assertEquals(remainedEntity.getPath(), entityPathTwo);
        Assert.assertEquals(remainedEntity.getClassifierPath(), classifierPathTwo);
        Assert.assertEquals(remainedEntity.getContent(), newEntityContentMapTwo);

        List<String> paths = gitLabEntityApi.getWorkspaceEntityAccessContext(projectId, sourceSpecification).getEntityPaths(null, null, null);

        Assert.assertNotNull(paths);
        Assert.assertEquals(1, paths.size());
        Assert.assertEquals(entityPathTwo, paths.get(0));
        List<String> labels = Collections.singletonList("default");

        Review testReview = gitLabCommitterReviewApi.createReview(projectId, sourceSpecification, "Add Courses.", "add two courses", labels);
        String reviewId = testReview.getId();
        Review approvedReview = gitLabApproverReviewApi.approveReview(projectId, patchReleaseVersionId, reviewId);

        Assert.assertNotNull(approvedReview);
        Assert.assertEquals(reviewId, approvedReview.getId());
        Assert.assertEquals(ReviewState.OPEN, approvedReview.getState());
        Assert.assertEquals(labels, approvedReview.getLabels());

        GitLabProjectId sdlcGitLabProjectId = GitLabProjectId.parseProjectId(projectId);
        MergeRequestApi mergeRequestApi = gitLabMemberUserContext.getGitLabAPI().getMergeRequestApi();
        int parsedMergeRequestId = Integer.parseInt(reviewId);
        int gitlabProjectId = sdlcGitLabProjectId.getGitLabId();

        String requiredStatus = "can_be_merged";
        CallUntil<MergeRequest, GitLabApiException> callUntil = CallUntil.callUntil(
                () -> mergeRequestApi.getMergeRequest(gitlabProjectId, parsedMergeRequestId),
                mr -> requiredStatus.equals(mr.getMergeStatus()),
                20,
                1000);
        if (!callUntil.succeeded())
        {
            throw new RuntimeException("Merge request " + approvedReview.getId() + " still does not have status \"" + requiredStatus + "\" after " + callUntil.getTryCount() + " tries");
        }
        LOGGER.info("Waited {} times for merge to have status \"{}\"", callUntil.getTryCount(), requiredStatus);

        gitLabCommitterReviewApi.commitReview(projectId, patchReleaseVersionId, reviewId, "add two math courses");

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

        RepositoryApi repositoryApi = gitLabMemberUserContext.getGitLabAPI().getRepositoryApi();
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

        List<Entity> postCommitProjectEntities = gitLabEntityApi.getProjectEntityAccessContext(projectId, patchReleaseVersionId).getEntities(null, null, null);

        Assert.assertNotNull(postCommitProjectEntities);
        Assert.assertEquals(1, postCommitProjectEntities.size());
        Entity projectEntity = postCommitProjectEntities.get(0);
        Assert.assertEquals(projectEntity.getPath(), entityPathTwo);
        Assert.assertEquals(projectEntity.getClassifierPath(), classifierPathTwo);
        Assert.assertEquals(projectEntity.getContent(), newEntityContentMapTwo);
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
