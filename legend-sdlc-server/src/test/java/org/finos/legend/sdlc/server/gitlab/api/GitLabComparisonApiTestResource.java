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
import org.finos.legend.sdlc.domain.model.comparison.EntityDiff;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.entity.change.EntityChangeType;
import org.finos.legend.sdlc.domain.model.patch.Patch;
import org.finos.legend.sdlc.domain.model.project.Project;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.version.Version;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.domain.api.version.NewVersionType;
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification;
import org.finos.legend.sdlc.server.gitlab.api.server.AbstractGitLabServerApiTest;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Substantial test resource class for Comparison API tests shared by the docker-based and server-based GitLab tests.
 */
public class GitLabComparisonApiTestResource
{
    private final GitLabWorkspaceApi gitLabWorkspaceApi;
    private final GitLabProjectApi gitLabProjectApi;
    private final GitLabEntityApi gitLabEntityApi;
    private final GitLabRevisionApi gitLabRevisionApi;
    private final GitLabComparisonApi gitLabComparisonApi;
    private final GitLabPatchApi gitlabPatchApi;
    private final GitLabVersionApi gitlabVersionApi;

    private static final Logger LOGGER = LoggerFactory.getLogger(GitLabComparisonApiTestResource.class);

    public GitLabComparisonApiTestResource(GitLabWorkspaceApi gitLabWorkspaceApi, GitLabProjectApi gitLabProjectApi, GitLabEntityApi gitLabEntityApi, GitLabRevisionApi gitLabRevisionApi, GitLabComparisonApi gitLabComparisonApi, GitLabPatchApi gitlabPatchAPi, GitLabVersionApi gitlabVersionApi)
    {
        this.gitLabWorkspaceApi = gitLabWorkspaceApi;
        this.gitLabProjectApi = gitLabProjectApi;
        this.gitLabEntityApi = gitLabEntityApi;
        this.gitLabRevisionApi = gitLabRevisionApi;
        this.gitLabComparisonApi = gitLabComparisonApi;
        this.gitlabPatchApi = gitlabPatchAPi;
        this.gitlabVersionApi = gitlabVersionApi;
    }

    public void runUserWorkspaceComparisonTest()
    {
        String projectName = "ComparisonTestProjectOne";
        String description = "A test project.";
        String groupId = "org.finos.sdlc.test";
        String artifactId = "comptestprojone";
        List<String> tags = Lists.mutable.with("doe", "moffitt", AbstractGitLabServerApiTest.INTEGRATION_TEST_PROJECT_TAG);
        String workspaceOneId = "testworkspaceone";

        Project createdProject = gitLabProjectApi.createProject(projectName, description, ProjectType.MANAGED, groupId, artifactId, tags);

        Assert.assertNotNull(createdProject);
        Assert.assertEquals(projectName, createdProject.getName());
        Assert.assertEquals(description, createdProject.getDescription());
        Assert.assertEquals(Sets.mutable.withAll(tags), Sets.mutable.withAll(createdProject.getTags()));

        String projectId = createdProject.getProjectId();
        Workspace createdWorkspaceOne = gitLabWorkspaceApi.newUserWorkspace(projectId, workspaceOneId);
        Revision fromRevision = gitLabRevisionApi.getUserWorkspaceRevisionContext(projectId, workspaceOneId).getCurrentRevision();

        Assert.assertNotNull(createdWorkspaceOne);
        Assert.assertEquals(workspaceOneId, createdWorkspaceOne.getWorkspaceId());
        Assert.assertEquals(projectId, createdWorkspaceOne.getProjectId());
        Assert.assertNotNull(createdWorkspaceOne.getUserId());

        String entityPath = "test::entity";
        String classifierPath = "meta::test::mathematicsDepartment";
        Map<String, String> entityContentMap = Maps.mutable.with(
                "package", "test",
                "name", "entity",
                "math-113", "abstract-algebra",
                "math-185", "complex-analysis");
        gitLabEntityApi.getUserWorkspaceEntityModificationContext(projectId, workspaceOneId).createEntity(entityPath, classifierPath, entityContentMap, "initial entity");
        List<Entity> modifiedWorkspaceEntities = gitLabEntityApi.getUserWorkspaceEntityAccessContext(projectId, workspaceOneId).getEntities(null, null, null);

        Assert.assertNotNull(modifiedWorkspaceEntities);
        Assert.assertEquals(1, modifiedWorkspaceEntities.size());
        Entity initalEntity = modifiedWorkspaceEntities.get(0);
        Assert.assertEquals(initalEntity.getPath(), entityPath);
        Assert.assertEquals(initalEntity.getClassifierPath(), classifierPath);
        Assert.assertEquals(initalEntity.getContent(), entityContentMap);

        Revision toRevision = gitLabRevisionApi.getUserWorkspaceRevisionContext(projectId, workspaceOneId).getCurrentRevision();
        List<EntityDiff> entityDiffs = gitLabComparisonApi.getUserWorkspaceCreationComparison(projectId, workspaceOneId).getEntityDiffs();
        String fromRevisionId = gitLabComparisonApi.getUserWorkspaceCreationComparison(projectId, workspaceOneId).getFromRevisionId();
        String toRevisionId = gitLabComparisonApi.getUserWorkspaceCreationComparison(projectId, workspaceOneId).getToRevisionId();

        Assert.assertNotNull(fromRevision);
        Assert.assertNotNull(toRevision);
        Assert.assertEquals(fromRevision.getId(), fromRevisionId);
        Assert.assertEquals(toRevision.getId(), toRevisionId);
        Assert.assertNotNull(entityDiffs);
        Assert.assertEquals(1, entityDiffs.size());
        Assert.assertEquals(EntityChangeType.CREATE, entityDiffs.get(0).getEntityChangeType());

        Revision projectFromRevision = gitLabRevisionApi.getProjectRevisionContext(projectId).getCurrentRevision();
        Revision projectToRevision = gitLabRevisionApi.getUserWorkspaceRevisionContext(projectId, workspaceOneId).getCurrentRevision();
        List<EntityDiff> projectEntityDiffs = gitLabComparisonApi.getUserWorkspaceProjectComparison(projectId, workspaceOneId).getEntityDiffs();
        String projectFromRevisionId = gitLabComparisonApi.getUserWorkspaceProjectComparison(projectId, workspaceOneId).getFromRevisionId();
        String projectToRevisionId = gitLabComparisonApi.getUserWorkspaceProjectComparison(projectId, workspaceOneId).getToRevisionId();

        Assert.assertNotNull(projectFromRevision);
        Assert.assertEquals(projectFromRevision.getId(), projectFromRevisionId);
        Assert.assertEquals(projectToRevision.getId(), projectToRevisionId);
        Assert.assertNotNull(projectEntityDiffs);
        Assert.assertEquals(1, projectEntityDiffs.size());
        Assert.assertEquals(EntityChangeType.CREATE, projectEntityDiffs.get(0).getEntityChangeType());
    }

    public void runGroupWorkspaceComparisonTest()
    {
        String projectName = "ComparisonTestProjectTwo";
        String description = "A test project.";
        String groupId = "org.finos.sdlc.test";
        String artifactId = "comptestprojtwo";
        List<String> tags = Lists.mutable.with("doe", "moffitt", AbstractGitLabServerApiTest.INTEGRATION_TEST_PROJECT_TAG);
        String workspaceOneId = "testworkspaceone";

        Project createdProject = gitLabProjectApi.createProject(projectName, description, ProjectType.MANAGED, groupId, artifactId, tags);

        Assert.assertNotNull(createdProject);
        Assert.assertEquals(projectName, createdProject.getName());
        Assert.assertEquals(description, createdProject.getDescription());
        Assert.assertEquals(Sets.mutable.withAll(tags), Sets.mutable.withAll(createdProject.getTags()));

        String projectId = createdProject.getProjectId();
        Workspace createdWorkspaceOne = gitLabWorkspaceApi.newGroupWorkspace(projectId, workspaceOneId);
        Revision fromRevision = gitLabRevisionApi.getGroupWorkspaceRevisionContext(projectId, workspaceOneId).getCurrentRevision();

        Assert.assertNotNull(createdWorkspaceOne);
        Assert.assertEquals(workspaceOneId, createdWorkspaceOne.getWorkspaceId());
        Assert.assertEquals(projectId, createdWorkspaceOne.getProjectId());
        Assert.assertNull(createdWorkspaceOne.getUserId());

        String entityPath = "test::entity";
        String classifierPath = "meta::test::mathematicsDepartment";
        Map<String, String> entityContentMap = Maps.mutable.with(
                "package", "test",
                "name", "entity",
                "math-113", "abstract-algebra",
                "math-185", "complex-analysis");
        gitLabEntityApi.getGroupWorkspaceEntityModificationContext(projectId, workspaceOneId).createEntity(entityPath, classifierPath, entityContentMap, "initial entity");
        List<Entity> modifiedWorkspaceEntities = gitLabEntityApi.getGroupWorkspaceEntityAccessContext(projectId, workspaceOneId).getEntities(null, null, null);

        Assert.assertNotNull(modifiedWorkspaceEntities);
        Assert.assertEquals(1, modifiedWorkspaceEntities.size());
        Entity initalEntity = modifiedWorkspaceEntities.get(0);
        Assert.assertEquals(initalEntity.getPath(), entityPath);
        Assert.assertEquals(initalEntity.getClassifierPath(), classifierPath);
        Assert.assertEquals(initalEntity.getContent(), entityContentMap);

        Revision toRevision = gitLabRevisionApi.getGroupWorkspaceRevisionContext(projectId, workspaceOneId).getCurrentRevision();
        List<EntityDiff> entityDiffs = gitLabComparisonApi.getGroupWorkspaceCreationComparison(projectId, workspaceOneId).getEntityDiffs();
        String fromRevisionId = gitLabComparisonApi.getGroupWorkspaceCreationComparison(projectId, workspaceOneId).getFromRevisionId();
        String toRevisionId = gitLabComparisonApi.getGroupWorkspaceCreationComparison(projectId, workspaceOneId).getToRevisionId();

        Assert.assertNotNull(fromRevision);
        Assert.assertNotNull(toRevision);
        Assert.assertEquals(fromRevision.getId(), fromRevisionId);
        Assert.assertEquals(toRevision.getId(), toRevisionId);
        Assert.assertNotNull(entityDiffs);
        Assert.assertEquals(1, entityDiffs.size());
        Assert.assertEquals(EntityChangeType.CREATE, entityDiffs.get(0).getEntityChangeType());

        Revision projectFromRevision = gitLabRevisionApi.getProjectRevisionContext(projectId).getCurrentRevision();
        Revision projectToRevision = gitLabRevisionApi.getGroupWorkspaceRevisionContext(projectId, workspaceOneId).getCurrentRevision();
        List<EntityDiff> projectEntityDiffs = gitLabComparisonApi.getGroupWorkspaceProjectComparison(projectId, workspaceOneId).getEntityDiffs();
        String projectFromRevisionId = gitLabComparisonApi.getGroupWorkspaceProjectComparison(projectId, workspaceOneId).getFromRevisionId();
        String projectToRevisionId = gitLabComparisonApi.getGroupWorkspaceProjectComparison(projectId, workspaceOneId).getToRevisionId();

        Assert.assertNotNull(projectFromRevision);
        Assert.assertEquals(projectFromRevision.getId(), projectFromRevisionId);
        Assert.assertEquals(projectToRevision.getId(), projectToRevisionId);
        Assert.assertNotNull(projectEntityDiffs);
        Assert.assertEquals(1, projectEntityDiffs.size());
        Assert.assertEquals(EntityChangeType.CREATE, projectEntityDiffs.get(0).getEntityChangeType());
    }

    public void runUserWorkspaceComparisonTestForPatchReleaseVersion()
    {
        String projectName = "ComparisonTestProjectThree";
        String description = "A test project.";
        String groupId = "org.finos.sdlc.test";
        String artifactId = "comptestprojthree";
        List<String> tags = Lists.mutable.with("doe", "moffitt", AbstractGitLabServerApiTest.INTEGRATION_TEST_PROJECT_TAG);
        String workspaceOneId = "testworkspaceone";

        Project createdProject = gitLabProjectApi.createProject(projectName, description, ProjectType.MANAGED, groupId, artifactId, tags);

        Assert.assertNotNull(createdProject);
        Assert.assertEquals(projectName, createdProject.getName());
        Assert.assertEquals(description, createdProject.getDescription());
        Assert.assertEquals(Sets.mutable.withAll(tags), Sets.mutable.withAll(createdProject.getTags()));

        String projectId = createdProject.getProjectId();
        Version version = gitlabVersionApi.newVersion(projectId, NewVersionType.PATCH, gitLabRevisionApi.getProjectRevisionContext(projectId).getCurrentRevision().getId(), "");
        Patch patch = gitlabPatchApi.newPatch(projectId, version.getId());
        VersionId patchReleaseVersionId = patch.getPatchReleaseVersionId();
        SourceSpecification sourceSpecification = SourceSpecification.newUserWorkspaceSourceSpecification(workspaceOneId, patchReleaseVersionId);

        Workspace createdWorkspaceOne = gitLabWorkspaceApi.newWorkspace(projectId, sourceSpecification);
        Revision fromRevision = gitLabRevisionApi.getWorkspaceRevisionContext(projectId, sourceSpecification).getCurrentRevision();

        Assert.assertNotNull(createdWorkspaceOne);
        Assert.assertEquals(workspaceOneId, createdWorkspaceOne.getWorkspaceId());
        Assert.assertEquals(projectId, createdWorkspaceOne.getProjectId());
        Assert.assertNotNull(createdWorkspaceOne.getUserId());

        String entityPath = "test::entity";
        String classifierPath = "meta::test::mathematicsDepartment";
        Map<String, String> entityContentMap = Maps.mutable.with(
                "package", "test",
                "name", "entity",
                "math-113", "abstract-algebra",
                "math-185", "complex-analysis");
        gitLabEntityApi.getWorkspaceEntityModificationContext(projectId, sourceSpecification).createEntity(entityPath, classifierPath, entityContentMap, "initial entity");
        List<Entity> modifiedWorkspaceEntities = gitLabEntityApi.getWorkspaceEntityAccessContext(projectId, sourceSpecification).getEntities(null, null, null);

        Assert.assertNotNull(modifiedWorkspaceEntities);
        Assert.assertEquals(1, modifiedWorkspaceEntities.size());
        Entity initalEntity = modifiedWorkspaceEntities.get(0);
        Assert.assertEquals(initalEntity.getPath(), entityPath);
        Assert.assertEquals(initalEntity.getClassifierPath(), classifierPath);
        Assert.assertEquals(initalEntity.getContent(), entityContentMap);

        Revision toRevision = gitLabRevisionApi.getWorkspaceRevisionContext(projectId, sourceSpecification).getCurrentRevision();
        List<EntityDiff> entityDiffs = gitLabComparisonApi.getWorkspaceCreationComparison(projectId, SourceSpecification.newUserWorkspaceSourceSpecification(workspaceOneId, patchReleaseVersionId)).getEntityDiffs();
        String fromRevisionId = gitLabComparisonApi.getWorkspaceCreationComparison(projectId, SourceSpecification.newUserWorkspaceSourceSpecification(workspaceOneId, patchReleaseVersionId)).getFromRevisionId();
        String toRevisionId = gitLabComparisonApi.getWorkspaceCreationComparison(projectId, SourceSpecification.newUserWorkspaceSourceSpecification(workspaceOneId, patchReleaseVersionId)).getToRevisionId();

        Assert.assertNotNull(fromRevision);
        Assert.assertNotNull(toRevision);
        Assert.assertEquals(fromRevision.getId(), fromRevisionId);
        Assert.assertEquals(toRevision.getId(), toRevisionId);
        Assert.assertNotNull(entityDiffs);
        Assert.assertEquals(1, entityDiffs.size());
        Assert.assertEquals(EntityChangeType.CREATE, entityDiffs.get(0).getEntityChangeType());

        Revision projectFromRevision = gitLabRevisionApi.getProjectRevisionContext(projectId, patchReleaseVersionId).getCurrentRevision();
        Revision projectToRevision = gitLabRevisionApi.getWorkspaceRevisionContext(projectId, sourceSpecification).getCurrentRevision();
        List<EntityDiff> projectEntityDiffs = gitLabComparisonApi.getWorkspaceProjectComparison(projectId, SourceSpecification.newUserWorkspaceSourceSpecification(workspaceOneId, patchReleaseVersionId)).getEntityDiffs();
        String projectFromRevisionId = gitLabComparisonApi.getWorkspaceProjectComparison(projectId, SourceSpecification.newUserWorkspaceSourceSpecification(workspaceOneId, patchReleaseVersionId)).getFromRevisionId();
        String projectToRevisionId = gitLabComparisonApi.getWorkspaceProjectComparison(projectId, SourceSpecification.newUserWorkspaceSourceSpecification(workspaceOneId, patchReleaseVersionId)).getToRevisionId();

        Assert.assertNotNull(projectFromRevision);
        Assert.assertEquals(projectFromRevision.getId(), projectFromRevisionId);
        Assert.assertEquals(projectToRevision.getId(), projectToRevisionId);
        Assert.assertNotNull(projectEntityDiffs);
        Assert.assertEquals(1, projectEntityDiffs.size());
        Assert.assertEquals(EntityChangeType.CREATE, projectEntityDiffs.get(0).getEntityChangeType());
    }

    public void runGroupWorkspaceComparisonTestForPatchReleaseVersion()
    {
        String projectName = "ComparisonTestProjectFour";
        String description = "A test project.";
        String groupId = "org.finos.sdlc.test";
        String artifactId = "comptestprojfour";
        List<String> tags = Lists.mutable.with("doe", "moffitt", AbstractGitLabServerApiTest.INTEGRATION_TEST_PROJECT_TAG);
        String workspaceOneId = "testworkspaceone";

        Project createdProject = gitLabProjectApi.createProject(projectName, description, ProjectType.MANAGED, groupId, artifactId, tags);

        Assert.assertNotNull(createdProject);
        Assert.assertEquals(projectName, createdProject.getName());
        Assert.assertEquals(description, createdProject.getDescription());
        Assert.assertEquals(Sets.mutable.withAll(tags), Sets.mutable.withAll(createdProject.getTags()));

        String projectId = createdProject.getProjectId();
        Version version = gitlabVersionApi.newVersion(projectId, NewVersionType.PATCH, gitLabRevisionApi.getProjectRevisionContext(projectId).getCurrentRevision().getId(), "");
        Patch patch = gitlabPatchApi.newPatch(projectId, version.getId());
        VersionId patchReleaseVersionId = patch.getPatchReleaseVersionId();
        SourceSpecification sourceSpecification = SourceSpecification.newGroupWorkspaceSourceSpecification(workspaceOneId, patchReleaseVersionId);

        Workspace createdWorkspaceOne = gitLabWorkspaceApi.newWorkspace(projectId, sourceSpecification);
        Revision fromRevision = gitLabRevisionApi.getWorkspaceRevisionContext(projectId, sourceSpecification).getCurrentRevision();

        Assert.assertNotNull(createdWorkspaceOne);
        Assert.assertEquals(workspaceOneId, createdWorkspaceOne.getWorkspaceId());
        Assert.assertEquals(projectId, createdWorkspaceOne.getProjectId());
        Assert.assertNull(createdWorkspaceOne.getUserId());

        String entityPath = "test::entity";
        String classifierPath = "meta::test::mathematicsDepartment";
        Map<String, String> entityContentMap = Maps.mutable.with(
                "package", "test",
                "name", "entity",
                "math-113", "abstract-algebra",
                "math-185", "complex-analysis");
        gitLabEntityApi.getWorkspaceEntityModificationContext(projectId, sourceSpecification).createEntity(entityPath, classifierPath, entityContentMap, "initial entity");
        List<Entity> modifiedWorkspaceEntities = gitLabEntityApi.getWorkspaceEntityAccessContext(projectId, sourceSpecification).getEntities(null, null, null);

        Assert.assertNotNull(modifiedWorkspaceEntities);
        Assert.assertEquals(1, modifiedWorkspaceEntities.size());
        Entity initalEntity = modifiedWorkspaceEntities.get(0);
        Assert.assertEquals(initalEntity.getPath(), entityPath);
        Assert.assertEquals(initalEntity.getClassifierPath(), classifierPath);
        Assert.assertEquals(initalEntity.getContent(), entityContentMap);

        Revision toRevision = gitLabRevisionApi.getWorkspaceRevisionContext(projectId, sourceSpecification).getCurrentRevision();
        List<EntityDiff> entityDiffs = gitLabComparisonApi.getWorkspaceCreationComparison(projectId, SourceSpecification.newGroupWorkspaceSourceSpecification(workspaceOneId, patchReleaseVersionId)).getEntityDiffs();
        String fromRevisionId = gitLabComparisonApi.getWorkspaceCreationComparison(projectId, SourceSpecification.newGroupWorkspaceSourceSpecification(workspaceOneId, patchReleaseVersionId)).getFromRevisionId();
        String toRevisionId = gitLabComparisonApi.getWorkspaceCreationComparison(projectId, SourceSpecification.newGroupWorkspaceSourceSpecification(workspaceOneId, patchReleaseVersionId)).getToRevisionId();

        Assert.assertNotNull(fromRevision);
        Assert.assertNotNull(toRevision);
        Assert.assertEquals(fromRevision.getId(), fromRevisionId);
        Assert.assertEquals(toRevision.getId(), toRevisionId);
        Assert.assertNotNull(entityDiffs);
        Assert.assertEquals(1, entityDiffs.size());
        Assert.assertEquals(EntityChangeType.CREATE, entityDiffs.get(0).getEntityChangeType());

        Revision projectFromRevision = gitLabRevisionApi.getProjectRevisionContext(projectId, patchReleaseVersionId).getCurrentRevision();
        Revision projectToRevision = gitLabRevisionApi.getWorkspaceRevisionContext(projectId, sourceSpecification).getCurrentRevision();
        List<EntityDiff> projectEntityDiffs = gitLabComparisonApi.getWorkspaceProjectComparison(projectId, SourceSpecification.newGroupWorkspaceSourceSpecification(workspaceOneId, patchReleaseVersionId)).getEntityDiffs();
        String projectFromRevisionId = gitLabComparisonApi.getWorkspaceProjectComparison(projectId, SourceSpecification.newGroupWorkspaceSourceSpecification(workspaceOneId, patchReleaseVersionId)).getFromRevisionId();
        String projectToRevisionId = gitLabComparisonApi.getWorkspaceProjectComparison(projectId, SourceSpecification.newGroupWorkspaceSourceSpecification(workspaceOneId, patchReleaseVersionId)).getToRevisionId();

        Assert.assertNotNull(projectFromRevision);
        Assert.assertEquals(projectFromRevision.getId(), projectFromRevisionId);
        Assert.assertEquals(projectToRevision.getId(), projectToRevisionId);
        Assert.assertNotNull(projectEntityDiffs);
        Assert.assertEquals(1, projectEntityDiffs.size());
        Assert.assertEquals(EntityChangeType.CREATE, projectEntityDiffs.get(0).getEntityChangeType());
    }

    public GitLabProjectApi getGitLabProjectApi()
    {
        return gitLabProjectApi;
    }
}
