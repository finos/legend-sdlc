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
import org.finos.legend.sdlc.server.gitlab.GitLabConfiguration;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.gitlab.tools.GitLabApiTools;
import org.finos.legend.sdlc.server.project.config.ProjectStructureConfiguration;
import org.gitlab4j.api.GitLabApiException;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class IntegrationTestGitLabEntityApis extends AbstractGitLabApiTest
{
    private static GitLabProjectApi gitLabProjectApi;
    private static GitLabWorkspaceApi gitLabWorkspaceApi;
    private static GitLabRevisionApi gitLabRevisionApi;
    private static GitLabEntityApi gitLabEntityApi;
    private static GitLabReviewApi gitLabCommitterReviewApi;
    private static GitLabReviewApi gitLabApproverReviewApi;

    @BeforeClass
    public static void setup() throws GitLabApiException
    {
        setUpEntityApi();
    }

    @Test
    public void testEntitiesInNormalWorkflow() throws GitLabApiException {
        String projectName = "CommitFlowTestProject";
        String description = "A test project.";
        ProjectType projectType = ProjectType.PROTOTYPE;
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

        GitLabApiTools.callWithRetries(() -> gitLabCommitterReviewApi.commitReview(projectId, reviewId, "add two math courses"), 5, 20);

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

    /**
     * Authenticates with OAuth2 and instantiate the test SDLC GitLabEntityApi.
     */
    private static void setUpEntityApi()
    {
        GitLabUserContext gitLabOwnerUserContext = prepareGitLabOwnerUserContext();
        GitLabUserContext gitLabMemberUserContext = prepareGitLabMemberUserContext();
        GitLabConfiguration gitLabConfig = GitLabConfiguration.newGitLabConfiguration(null, null, null, null, null);
        ProjectStructureConfiguration projectStructureConfig = ProjectStructureConfiguration.emptyConfiguration();

        gitLabProjectApi = new GitLabProjectApi(gitLabConfig, gitLabOwnerUserContext, projectStructureConfig, null, null, backgroundTaskProcessor);
        gitLabRevisionApi = new GitLabRevisionApi(gitLabMemberUserContext, backgroundTaskProcessor);
        gitLabWorkspaceApi = new GitLabWorkspaceApi(gitLabMemberUserContext, gitLabRevisionApi, backgroundTaskProcessor);
        gitLabEntityApi = new GitLabEntityApi(gitLabMemberUserContext, backgroundTaskProcessor);
        gitLabCommitterReviewApi = new GitLabReviewApi(gitLabMemberUserContext);
        gitLabApproverReviewApi = new GitLabReviewApi(gitLabOwnerUserContext);
    }
}
