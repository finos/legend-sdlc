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
 * Substantial test resource class for Revision API tests shared by the docker-based and server-based GitLab tests.
 */
public class GitLabRevisionApiTestResource
{
    private final GitLabWorkspaceApi gitLabWorkspaceApi;
    private final GitLabProjectApi gitLabProjectApi;
    private final GitLabEntityApi gitLabEntityApi;
    private final GitLabRevisionApi gitLabRevisionApi;
    private final GitLabPatchApi gitlabPatchApi;
    private final GitLabVersionApi gitlabVersionApi;

    private static final Logger LOGGER = LoggerFactory.getLogger(GitLabRevisionApiTestResource.class);

    public GitLabRevisionApiTestResource(GitLabWorkspaceApi gitLabWorkspaceApi, GitLabProjectApi gitLabProjectApi, GitLabEntityApi gitLabEntityApi, GitLabRevisionApi gitLabRevisionApi, GitLabPatchApi gitlabPatchAPi, GitLabVersionApi gitlabVersionApi)
    {
        this.gitLabWorkspaceApi = gitLabWorkspaceApi;
        this.gitLabProjectApi = gitLabProjectApi;
        this.gitLabEntityApi = gitLabEntityApi;
        this.gitLabRevisionApi = gitLabRevisionApi;
        this.gitlabPatchApi = gitlabPatchAPi;
        this.gitlabVersionApi = gitlabVersionApi;
    }

    public void runUserWorkspaceRevisionTest()
    {
        String projectName = "RevisionTestProjectOne";
        String description = "A test project.";
        String groupId = "org.finos.sdlc.test";
        String artifactId = "revisiontestprojone";
        List<String> tags = Lists.mutable.with("doe", "moffitt", AbstractGitLabServerApiTest.INTEGRATION_TEST_PROJECT_TAG);
        String workspaceOneId = "testworkspaceone";

        Project createdProject = gitLabProjectApi.createProject(projectName, description, ProjectType.MANAGED, groupId, artifactId, tags);

        Assert.assertNotNull(createdProject);
        Assert.assertEquals(projectName, createdProject.getName());
        Assert.assertEquals(description, createdProject.getDescription());
        Assert.assertEquals(Sets.mutable.withAll(tags), Sets.mutable.withAll(createdProject.getTags()));

        String projectId = createdProject.getProjectId();
        Workspace createdWorkspaceOne = gitLabWorkspaceApi.newUserWorkspace(projectId, workspaceOneId);

        Assert.assertNotNull(createdWorkspaceOne);
        Assert.assertEquals(workspaceOneId, createdWorkspaceOne.getWorkspaceId());
        Assert.assertEquals(projectId, createdWorkspaceOne.getProjectId());
        Assert.assertNotNull(createdWorkspaceOne.getUserId());

        List<Revision> workspaceRevisions = gitLabRevisionApi.getUserWorkspaceRevisionContext(projectId, workspaceOneId).getRevisions();

        Assert.assertNotNull(workspaceRevisions);
        Assert.assertEquals(1, workspaceRevisions.size());
        Revision createProjectStructureRevision = workspaceRevisions.get(0);
        Assert.assertEquals("Build project structure", createProjectStructureRevision.getMessage());
        Assert.assertNotNull(createProjectStructureRevision.getId());
        Assert.assertNotNull(createProjectStructureRevision.getAuthorName());
        Assert.assertNotNull(createProjectStructureRevision.getCommitterName());
        Assert.assertNotNull(createProjectStructureRevision.getAuthoredTimestamp());
        Assert.assertNotNull(createProjectStructureRevision.getCommittedTimestamp());

        String entityPath = "test::entity";
        String entityPackagePath = "test";
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

        List<Revision> updatedWorkspaceRevisions = gitLabRevisionApi.getUserWorkspaceRevisionContext(projectId, workspaceOneId).getRevisions();
        Revision currentRevision = gitLabRevisionApi.getUserWorkspaceRevisionContext(projectId, workspaceOneId).getCurrentRevision();

        Assert.assertNotNull(updatedWorkspaceRevisions);
        Assert.assertEquals(2, updatedWorkspaceRevisions.size());
        Assert.assertEquals("initial entity", currentRevision.getMessage());
        Assert.assertNotNull(currentRevision.getId());
        Assert.assertNotNull(currentRevision.getAuthorName());
        Assert.assertNotNull(currentRevision.getCommitterName());
        Assert.assertNotNull(currentRevision.getAuthoredTimestamp());
        Assert.assertNotNull(currentRevision.getCommittedTimestamp());

        List<Revision> entityRevisions = gitLabRevisionApi.getUserWorkspaceEntityRevisionContext(projectId, workspaceOneId, entityPath).getRevisions();
        Revision currentEntityRevision = gitLabRevisionApi.getUserWorkspaceEntityRevisionContext(projectId, workspaceOneId, entityPath).getCurrentRevision();

        Assert.assertNotNull(entityRevisions);
        Assert.assertEquals(1, entityRevisions.size());
        Assert.assertEquals("initial entity", currentEntityRevision.getMessage());
        Assert.assertNotNull(currentEntityRevision.getId());
        Assert.assertNotNull(currentEntityRevision.getAuthorName());
        Assert.assertNotNull(currentEntityRevision.getCommitterName());
        Assert.assertNotNull(currentEntityRevision.getAuthoredTimestamp());
        Assert.assertNotNull(currentEntityRevision.getCommittedTimestamp());

        List<Revision> packageRevisions = gitLabRevisionApi.getUserWorkspacePackageRevisionContext(projectId, workspaceOneId, entityPackagePath).getRevisions();
        Revision currentPackageRevision = gitLabRevisionApi.getUserWorkspacePackageRevisionContext(projectId, workspaceOneId, entityPackagePath).getCurrentRevision();

        Assert.assertNotNull(packageRevisions);
        Assert.assertEquals(1, packageRevisions.size());
        Assert.assertEquals("initial entity", currentPackageRevision.getMessage());
        Assert.assertNotNull(currentPackageRevision.getId());
        Assert.assertNotNull(currentPackageRevision.getAuthorName());
        Assert.assertNotNull(currentPackageRevision.getCommitterName());
        Assert.assertNotNull(currentPackageRevision.getAuthoredTimestamp());
        Assert.assertNotNull(currentPackageRevision.getCommittedTimestamp());
    }

    public void runGroupWorkspaceRevisionTest()
    {
        String projectName = "RevisionTestProjectTwo";
        String description = "A test project.";
        String groupId = "org.finos.sdlc.test";
        String artifactId = "revisiontestprojtwo";
        List<String> tags = Lists.mutable.with("doe", "moffitt", AbstractGitLabServerApiTest.INTEGRATION_TEST_PROJECT_TAG);
        String workspaceOneId = "testworkspaceone";

        Project createdProject = gitLabProjectApi.createProject(projectName, description, ProjectType.MANAGED, groupId, artifactId, tags);

        Assert.assertNotNull(createdProject);
        Assert.assertEquals(projectName, createdProject.getName());
        Assert.assertEquals(description, createdProject.getDescription());
        Assert.assertEquals(Sets.mutable.withAll(tags), Sets.mutable.withAll(createdProject.getTags()));

        String projectId = createdProject.getProjectId();
        Workspace createdWorkspaceOne = gitLabWorkspaceApi.newGroupWorkspace(projectId, workspaceOneId);

        Assert.assertNotNull(createdWorkspaceOne);
        Assert.assertEquals(workspaceOneId, createdWorkspaceOne.getWorkspaceId());
        Assert.assertEquals(projectId, createdWorkspaceOne.getProjectId());
        Assert.assertNull(createdWorkspaceOne.getUserId());

        List<Revision> workspaceRevisions = gitLabRevisionApi.getGroupWorkspaceRevisionContext(projectId, workspaceOneId).getRevisions();

        Assert.assertNotNull(workspaceRevisions);
        Assert.assertEquals(1, workspaceRevisions.size());
        Revision createProjectStructureRevision = workspaceRevisions.get(0);
        Assert.assertEquals("Build project structure", createProjectStructureRevision.getMessage());
        Assert.assertNotNull(createProjectStructureRevision.getId());
        Assert.assertNotNull(createProjectStructureRevision.getAuthorName());
        Assert.assertNotNull(createProjectStructureRevision.getCommitterName());
        Assert.assertNotNull(createProjectStructureRevision.getAuthoredTimestamp());
        Assert.assertNotNull(createProjectStructureRevision.getCommittedTimestamp());

        String entityPath = "test::entity";
        String entityPackagePath = "test";
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

        List<Revision> updatedWorkspaceRevisions = gitLabRevisionApi.getGroupWorkspaceRevisionContext(projectId, workspaceOneId).getRevisions();
        Revision currentRevision = gitLabRevisionApi.getGroupWorkspaceRevisionContext(projectId, workspaceOneId).getCurrentRevision();

        Assert.assertNotNull(updatedWorkspaceRevisions);
        Assert.assertEquals(2, updatedWorkspaceRevisions.size());
        Assert.assertEquals("initial entity", currentRevision.getMessage());
        Assert.assertNotNull(currentRevision.getId());
        Assert.assertNotNull(currentRevision.getAuthorName());
        Assert.assertNotNull(currentRevision.getCommitterName());
        Assert.assertNotNull(currentRevision.getAuthoredTimestamp());
        Assert.assertNotNull(currentRevision.getCommittedTimestamp());

        List<Revision> entityRevisions = gitLabRevisionApi.getGroupWorkspaceEntityRevisionContext(projectId, workspaceOneId, entityPath).getRevisions();
        Revision currentEntityRevision = gitLabRevisionApi.getGroupWorkspaceEntityRevisionContext(projectId, workspaceOneId, entityPath).getCurrentRevision();

        Assert.assertNotNull(entityRevisions);
        Assert.assertEquals(1, entityRevisions.size());
        Assert.assertEquals("initial entity", currentEntityRevision.getMessage());
        Assert.assertNotNull(currentEntityRevision.getId());
        Assert.assertNotNull(currentEntityRevision.getAuthorName());
        Assert.assertNotNull(currentEntityRevision.getCommitterName());
        Assert.assertNotNull(currentEntityRevision.getAuthoredTimestamp());
        Assert.assertNotNull(currentEntityRevision.getCommittedTimestamp());

        List<Revision> packageRevisions = gitLabRevisionApi.getGroupWorkspacePackageRevisionContext(projectId, workspaceOneId, entityPackagePath).getRevisions();
        Revision currentPackageRevision = gitLabRevisionApi.getGroupWorkspacePackageRevisionContext(projectId, workspaceOneId, entityPackagePath).getCurrentRevision();

        Assert.assertNotNull(packageRevisions);
        Assert.assertEquals(1, packageRevisions.size());
        Assert.assertEquals("initial entity", currentPackageRevision.getMessage());
        Assert.assertNotNull(currentPackageRevision.getId());
        Assert.assertNotNull(currentPackageRevision.getAuthorName());
        Assert.assertNotNull(currentPackageRevision.getCommitterName());
        Assert.assertNotNull(currentPackageRevision.getAuthoredTimestamp());
        Assert.assertNotNull(currentPackageRevision.getCommittedTimestamp());
    }

    public void runUserWorkspaceRevisionTestForPatchReleaseVersion()
    {
        String projectName = "RevisionTestProjectThree";
        String description = "A test project.";
        String groupId = "org.finos.sdlc.test";
        String artifactId = "revisiontestprojthree";
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

        Assert.assertNotNull(createdWorkspaceOne);
        Assert.assertEquals(workspaceOneId, createdWorkspaceOne.getWorkspaceId());
        Assert.assertEquals(projectId, createdWorkspaceOne.getProjectId());
        Assert.assertNotNull(createdWorkspaceOne.getUserId());

        List<Revision> workspaceRevisions = gitLabRevisionApi.getWorkspaceRevisionContext(projectId, sourceSpecification).getRevisions();

        Assert.assertNotNull(workspaceRevisions);
        Assert.assertEquals(1, workspaceRevisions.size());
        Revision createProjectStructureRevision = workspaceRevisions.get(0);
        Assert.assertEquals("Build project structure", createProjectStructureRevision.getMessage());
        Assert.assertNotNull(createProjectStructureRevision.getId());
        Assert.assertNotNull(createProjectStructureRevision.getAuthorName());
        Assert.assertNotNull(createProjectStructureRevision.getCommitterName());
        Assert.assertNotNull(createProjectStructureRevision.getAuthoredTimestamp());
        Assert.assertNotNull(createProjectStructureRevision.getCommittedTimestamp());

        String entityPath = "test::entity";
        String entityPackagePath = "test";
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

        List<Revision> updatedWorkspaceRevisions = gitLabRevisionApi.getWorkspaceRevisionContext(projectId, sourceSpecification).getRevisions();
        Revision currentRevision = gitLabRevisionApi.getWorkspaceRevisionContext(projectId, sourceSpecification).getCurrentRevision();

        Assert.assertNotNull(updatedWorkspaceRevisions);
        Assert.assertEquals(2, updatedWorkspaceRevisions.size());
        Assert.assertEquals("initial entity", currentRevision.getMessage());
        Assert.assertNotNull(currentRevision.getId());
        Assert.assertNotNull(currentRevision.getAuthorName());
        Assert.assertNotNull(currentRevision.getCommitterName());
        Assert.assertNotNull(currentRevision.getAuthoredTimestamp());
        Assert.assertNotNull(currentRevision.getCommittedTimestamp());

        List<Revision> entityRevisions = gitLabRevisionApi.getWorkspaceEntityRevisionContext(projectId, sourceSpecification, entityPath).getRevisions();
        Revision currentEntityRevision = gitLabRevisionApi.getWorkspaceEntityRevisionContext(projectId, sourceSpecification, entityPath).getCurrentRevision();

        Assert.assertNotNull(entityRevisions);
        Assert.assertEquals(1, entityRevisions.size());
        Assert.assertEquals("initial entity", currentEntityRevision.getMessage());
        Assert.assertNotNull(currentEntityRevision.getId());
        Assert.assertNotNull(currentEntityRevision.getAuthorName());
        Assert.assertNotNull(currentEntityRevision.getCommitterName());
        Assert.assertNotNull(currentEntityRevision.getAuthoredTimestamp());
        Assert.assertNotNull(currentEntityRevision.getCommittedTimestamp());

        List<Revision> packageRevisions = gitLabRevisionApi.getWorkspacePackageRevisionContext(projectId, sourceSpecification, entityPackagePath).getRevisions();
        Revision currentPackageRevision = gitLabRevisionApi.getWorkspacePackageRevisionContext(projectId, sourceSpecification, entityPackagePath).getCurrentRevision();

        Assert.assertNotNull(packageRevisions);
        Assert.assertEquals(1, packageRevisions.size());
        Assert.assertEquals("initial entity", currentPackageRevision.getMessage());
        Assert.assertNotNull(currentPackageRevision.getId());
        Assert.assertNotNull(currentPackageRevision.getAuthorName());
        Assert.assertNotNull(currentPackageRevision.getCommitterName());
        Assert.assertNotNull(currentPackageRevision.getAuthoredTimestamp());
        Assert.assertNotNull(currentPackageRevision.getCommittedTimestamp());
    }

    public void runGroupWorkspaceRevisionTestForPatchReleaseVersion()
    {
        String projectName = "RevisionTestProjectFour";
        String description = "A test project.";
        String groupId = "org.finos.sdlc.test";
        String artifactId = "revisiontestprojfour";
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

        Assert.assertNotNull(createdWorkspaceOne);
        Assert.assertEquals(workspaceOneId, createdWorkspaceOne.getWorkspaceId());
        Assert.assertEquals(projectId, createdWorkspaceOne.getProjectId());
        Assert.assertNull(createdWorkspaceOne.getUserId());

        List<Revision> workspaceRevisions = gitLabRevisionApi.getWorkspaceRevisionContext(projectId, sourceSpecification).getRevisions();

        Assert.assertNotNull(workspaceRevisions);
        Assert.assertEquals(1, workspaceRevisions.size());
        Revision createProjectStructureRevision = workspaceRevisions.get(0);
        Assert.assertEquals("Build project structure", createProjectStructureRevision.getMessage());
        Assert.assertNotNull(createProjectStructureRevision.getId());
        Assert.assertNotNull(createProjectStructureRevision.getAuthorName());
        Assert.assertNotNull(createProjectStructureRevision.getCommitterName());
        Assert.assertNotNull(createProjectStructureRevision.getAuthoredTimestamp());
        Assert.assertNotNull(createProjectStructureRevision.getCommittedTimestamp());

        String entityPath = "test::entity";
        String entityPackagePath = "test";
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

        List<Revision> updatedWorkspaceRevisions = gitLabRevisionApi.getWorkspaceRevisionContext(projectId, sourceSpecification).getRevisions();
        Revision currentRevision = gitLabRevisionApi.getWorkspaceRevisionContext(projectId, sourceSpecification).getCurrentRevision();

        Assert.assertNotNull(updatedWorkspaceRevisions);
        Assert.assertEquals(2, updatedWorkspaceRevisions.size());
        Assert.assertEquals("initial entity", currentRevision.getMessage());
        Assert.assertNotNull(currentRevision.getId());
        Assert.assertNotNull(currentRevision.getAuthorName());
        Assert.assertNotNull(currentRevision.getCommitterName());
        Assert.assertNotNull(currentRevision.getAuthoredTimestamp());
        Assert.assertNotNull(currentRevision.getCommittedTimestamp());

        List<Revision> entityRevisions = gitLabRevisionApi.getWorkspaceEntityRevisionContext(projectId, sourceSpecification, entityPath).getRevisions();
        Revision currentEntityRevision = gitLabRevisionApi.getWorkspaceEntityRevisionContext(projectId, sourceSpecification, entityPath).getCurrentRevision();

        Assert.assertNotNull(entityRevisions);
        Assert.assertEquals(1, entityRevisions.size());
        Assert.assertEquals("initial entity", currentEntityRevision.getMessage());
        Assert.assertNotNull(currentEntityRevision.getId());
        Assert.assertNotNull(currentEntityRevision.getAuthorName());
        Assert.assertNotNull(currentEntityRevision.getCommitterName());
        Assert.assertNotNull(currentEntityRevision.getAuthoredTimestamp());
        Assert.assertNotNull(currentEntityRevision.getCommittedTimestamp());

        List<Revision> packageRevisions = gitLabRevisionApi.getWorkspacePackageRevisionContext(projectId, sourceSpecification, entityPackagePath).getRevisions();
        Revision currentPackageRevision = gitLabRevisionApi.getWorkspacePackageRevisionContext(projectId, sourceSpecification, entityPackagePath).getCurrentRevision();

        Assert.assertNotNull(packageRevisions);
        Assert.assertEquals(1, packageRevisions.size());
        Assert.assertEquals("initial entity", currentPackageRevision.getMessage());
        Assert.assertNotNull(currentPackageRevision.getId());
        Assert.assertNotNull(currentPackageRevision.getAuthorName());
        Assert.assertNotNull(currentPackageRevision.getCommitterName());
        Assert.assertNotNull(currentPackageRevision.getAuthoredTimestamp());
        Assert.assertNotNull(currentPackageRevision.getCommittedTimestamp());
    }

    public GitLabProjectApi getGitLabProjectApi()
    {
        return gitLabProjectApi;
    }
}
