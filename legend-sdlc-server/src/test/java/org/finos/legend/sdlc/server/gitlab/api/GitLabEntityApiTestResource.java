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
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.project.Project;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.domain.model.review.Review;
import org.finos.legend.sdlc.domain.model.review.ReviewState;
import org.finos.legend.sdlc.server.gitlab.GitLabProjectId;
import org.finos.legend.sdlc.server.gitlab.api.server.AbstractGitLabServerApiTest;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.MergeRequestApi;
import org.gitlab4j.api.models.MergeRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Substantial test resource class for Entity API tests shared by the docker-based and server-based GitLab tests.
 */
public class GitLabEntityApiTestResource
{
    private final GitLabProjectApi gitLabProjectApi;
    private final GitLabWorkspaceApi gitLabWorkspaceApi;
    private final GitLabRevisionApi gitLabRevisionApi;
    private final GitLabEntityApi gitLabEntityApi;
    private final GitLabReviewApi gitLabCommitterReviewApi;
    private final GitLabReviewApi gitLabApproverReviewApi;

    private final GitLabUserContext gitLabMemberUserContext;

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractGitLabServerApiTest.class);

    public GitLabEntityApiTestResource(GitLabProjectApi gitLabProjectApi, GitLabWorkspaceApi gitLabWorkspaceApi, GitLabRevisionApi gitLabRevisionApi, GitLabEntityApi gitLabEntityApi, GitLabReviewApi gitLabCommitterReviewApi, GitLabReviewApi gitLabApproverReviewApi, GitLabUserContext gitLabMemberUserContext)
    {
        this.gitLabProjectApi = gitLabProjectApi;
        this.gitLabWorkspaceApi = gitLabWorkspaceApi;
        this.gitLabRevisionApi = gitLabRevisionApi;
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
        List<String> tags = Lists.mutable.with("doe", "moffitt");
        String workspaceName = "entitytestworkspace";

        Project createdProject = gitLabProjectApi.createProject(projectName, description, projectType, groupId, artifactId, tags);

        String projectId = createdProject.getProjectId();
        Workspace createdWorkspace = gitLabWorkspaceApi.newWorkspace(projectId, workspaceName);

        String workspaceId = createdWorkspace.getWorkspaceId();
        List<Entity> initialWorkspaceEntities = gitLabEntityApi.getWorkspaceEntityAccessContext(projectId, workspaceId).getEntities(null, null, null);
        List<Entity> initialProjectEntities = gitLabEntityApi.getProjectEntityAccessContext(projectId).getEntities(null, null, null);

        assertEquals(Collections.emptyList(), initialWorkspaceEntities);
        assertEquals(Collections.emptyList(), initialProjectEntities);

        String entityPath = "test::entity";
        String classifierPath = "meta::test::mathematicsDepartment";
        Map<String, String> entityContentMap = new HashMap<>();
        entityContentMap.put("package", "test");
        entityContentMap.put("name", "entity");
        entityContentMap.put("math-113", "abstract-algebra");
        entityContentMap.put("math-185", "complex-analysis");
        gitLabEntityApi.getWorkspaceEntityModificationContext(projectId, workspaceId).createEntity(entityPath, classifierPath, entityContentMap, "initial entity");
        List<Entity> modifiedWorkspaceEntities = gitLabEntityApi.getWorkspaceEntityAccessContext(projectId, workspaceId).getEntities(null, null, null);
        List<Entity> modifiedProjectEntities = gitLabEntityApi.getProjectEntityAccessContext(projectId).getEntities(null, null, null);

        assertNotNull(modifiedWorkspaceEntities);
        assertEquals(Collections.emptyList(), modifiedProjectEntities);
        assertTrue(modifiedWorkspaceEntities.size() == 1);
        Entity initalEntity = modifiedWorkspaceEntities.get(0);
        assertEquals(initalEntity.getPath(), entityPath);
        assertEquals(initalEntity.getClassifierPath(), classifierPath);
        assertEquals(initalEntity.getContent(), entityContentMap);

        Review testReview = gitLabCommitterReviewApi.createReview(projectId, workspaceId, "Add Courses.", "add two math courses");
        String reviewId = testReview.getId();
        Review approvedReview = gitLabApproverReviewApi.approveReview(projectId, reviewId);

        assertNotNull(approvedReview);
        assertEquals(reviewId, approvedReview.getId());
        assertEquals(ReviewState.OPEN, approvedReview.getState());

        GitLabProjectId sdlcGitLabProjectId = GitLabProjectId.parseProjectId(projectId);
        MergeRequestApi mergeRequestApi = gitLabMemberUserContext.getGitLabAPI(sdlcGitLabProjectId.getGitLabMode()).getMergeRequestApi();
        Integer parsedMergeRequestId = Integer.parseInt(reviewId);
        Integer gitlabProjectId = sdlcGitLabProjectId.getGitLabId();
        MergeRequest mergeRequest = mergeRequestApi.getMergeRequest(gitlabProjectId, parsedMergeRequestId);

        LOGGER.info("Start trying to merge the request once valid.");
        int maxTries = 10;
        int totalRetryCount = 0;
        for (int i = 0; !("can_be_merged".equals(mergeRequest.getMergeStatus())) && (i < maxTries); i++)
        {
            try
            {
                Thread.sleep(500);
                mergeRequest = mergeRequestApi.getMergeRequest(gitlabProjectId, parsedMergeRequestId);
                totalRetryCount += 1;
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
        }
        LOGGER.info("Retried merge: {} times", totalRetryCount);

        gitLabCommitterReviewApi.commitReview(projectId, reviewId, "add two math courses");
        List<Entity> newWorkspaceEntities = gitLabEntityApi.getWorkspaceEntityAccessContext(projectId, workspaceId).getEntities(null, null, null);
        List<Entity> postCommitProjectEntities = gitLabEntityApi.getProjectEntityAccessContext(projectId).getEntities(null, null, null);

        assertNotNull(postCommitProjectEntities);
        assertEquals(Collections.emptyList(), newWorkspaceEntities);
        assertTrue(postCommitProjectEntities.size() == 1);
        Entity projectEntity = postCommitProjectEntities.get(0);
        assertEquals(projectEntity.getPath(), entityPath);
        assertEquals(projectEntity.getClassifierPath(), classifierPath);
        assertEquals(projectEntity.getContent(), entityContentMap);
    }

    public GitLabProjectApi getGitLabProjectApi()
    {
        return gitLabProjectApi;
    }
}
