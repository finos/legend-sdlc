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
import org.finos.legend.sdlc.domain.model.patch.Patch;
import org.finos.legend.sdlc.domain.model.project.Project;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.domain.model.version.Version;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.domain.api.version.NewVersionType;
import org.finos.legend.sdlc.server.domain.api.project.SourceSpecification;
import org.finos.legend.sdlc.server.gitlab.api.server.AbstractGitLabServerApiTest;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Substantial test resource class for Workspace API tests shared by the docker-based and server-based GitLab tests.
 */
public class GitLabProjectConfigurationApiTestResource
{
    private final GitLabWorkspaceApi gitLabWorkspaceApi;
    private final GitLabProjectApi gitLabProjectApi;
    private final GitLabProjectConfigurationApi gitLabProjectConfigurationApi;
    private final GitLabPatchApi gitlabPatchApi;
    private final GitLabVersionApi gitlabVersionApi;
    private final GitLabRevisionApi gitLabRevisionApi;


    private static final Logger LOGGER = LoggerFactory.getLogger(GitLabProjectConfigurationApiTestResource.class);

    public GitLabProjectConfigurationApiTestResource(GitLabWorkspaceApi gitLabWorkspaceApi, GitLabProjectApi gitLabProjectApi, GitLabProjectConfigurationApi gitLabProjectConfigurationApi,GitLabPatchApi gitlabPatchAPi, GitLabVersionApi gitlabVersionApi, GitLabRevisionApi gitLabRevisionApi)
    {
        this.gitLabWorkspaceApi = gitLabWorkspaceApi;
        this.gitLabProjectApi = gitLabProjectApi;
        this.gitLabProjectConfigurationApi = gitLabProjectConfigurationApi;
        this.gitlabPatchApi = gitlabPatchAPi;
        this.gitlabVersionApi = gitlabVersionApi;
        this.gitLabRevisionApi = gitLabRevisionApi;
    }

    public void runUserAndGroupWorkspaceProjectConfigurationTest()
    {
        String projectName = "PCTestProjectOne";
        String description = "A test project.";
        String groupId = "org.finos.sdlc.test";
        String artifactId = "pctestprojone";
        List<String> tags = Lists.mutable.with("doe", "moffitt", AbstractGitLabServerApiTest.INTEGRATION_TEST_PROJECT_TAG);
        String workspaceOneId = "testworkspaceone";
        String workspaceTwoId = "testworkspacetwo";

        Project createdProject = gitLabProjectApi.createProject(projectName, description, ProjectType.MANAGED, groupId, artifactId, tags);

        Assert.assertNotNull(createdProject);
        Assert.assertEquals(projectName, createdProject.getName());
        Assert.assertEquals(description, createdProject.getDescription());
        Assert.assertEquals(Sets.mutable.withAll(tags), Sets.mutable.withAll(createdProject.getTags()));
        Assert.assertNull(createdProject.getProjectType());

        String projectId = createdProject.getProjectId();
        Workspace createdWorkspaceOne = gitLabWorkspaceApi.newUserWorkspace(projectId, workspaceOneId);

        Assert.assertNotNull(createdWorkspaceOne);
        Assert.assertEquals(workspaceOneId, createdWorkspaceOne.getWorkspaceId());
        Assert.assertEquals(projectId, createdWorkspaceOne.getProjectId());
        Assert.assertNotNull(createdWorkspaceOne.getUserId());

        ProjectConfiguration projectConfiguration = gitLabProjectConfigurationApi.getUserWorkspaceProjectConfiguration(projectId, workspaceOneId);

        Assert.assertNotNull(projectConfiguration);
        Assert.assertEquals(ProjectType.MANAGED, projectConfiguration.getProjectType());
        Assert.assertEquals(projectConfiguration.getArtifactId(), artifactId);
        Assert.assertEquals(projectConfiguration.getGroupId(), groupId);

        Workspace createdWorkspaceTwo = gitLabWorkspaceApi.newGroupWorkspace(projectId, workspaceTwoId);

        Assert.assertNotNull(createdWorkspaceTwo);
        Assert.assertEquals(workspaceTwoId, createdWorkspaceTwo.getWorkspaceId());
        Assert.assertEquals(projectId, createdWorkspaceTwo.getProjectId());
        Assert.assertNull(createdWorkspaceTwo.getUserId());

        ProjectConfiguration projectConfigurationTwo = gitLabProjectConfigurationApi.getGroupWorkspaceProjectConfiguration(projectId, workspaceTwoId);

        Assert.assertNotNull(projectConfigurationTwo);
        Assert.assertEquals(projectConfigurationTwo.getArtifactId(), artifactId);
        Assert.assertEquals(projectConfigurationTwo.getGroupId(), groupId);
    }

    public void runUserAndGroupWorkspaceProjectConfigurationTestForPatchReleaseVersion()
    {
        String projectName = "PCTestProjectTwo";
        String description = "A test project.";
        String groupId = "org.finos.sdlc.test";
        String artifactId = "pctestprojtwo";
        List<String> tags = Lists.mutable.with("doe", "moffitt", AbstractGitLabServerApiTest.INTEGRATION_TEST_PROJECT_TAG);
        String workspaceOneId = "testworkspaceone";
        String workspaceTwoId = "testworkspacetwo";

        Project createdProject = gitLabProjectApi.createProject(projectName, description, ProjectType.MANAGED, groupId, artifactId, tags);

        Assert.assertNotNull(createdProject);
        Assert.assertEquals(projectName, createdProject.getName());
        Assert.assertEquals(description, createdProject.getDescription());
        Assert.assertEquals(Sets.mutable.withAll(tags), Sets.mutable.withAll(createdProject.getTags()));
        Assert.assertNull(createdProject.getProjectType());

        String projectId = createdProject.getProjectId();
        Version version = gitlabVersionApi.newVersion(projectId, NewVersionType.PATCH, gitLabRevisionApi.getProjectRevisionContext(projectId).getCurrentRevision().getId(), "");
        Patch patch = gitlabPatchApi.newPatch(projectId, version.getId());
        VersionId patchReleaseVersionId = patch.getPatchReleaseVersionId();

        Workspace createdWorkspaceOne = gitLabWorkspaceApi.newWorkspace(projectId, SourceSpecification.newUserWorkspaceSourceSpecification(workspaceOneId, patchReleaseVersionId));

        Assert.assertNotNull(createdWorkspaceOne);
        Assert.assertEquals(workspaceOneId, createdWorkspaceOne.getWorkspaceId());
        Assert.assertEquals(projectId, createdWorkspaceOne.getProjectId());
        Assert.assertNotNull(createdWorkspaceOne.getUserId());

        ProjectConfiguration projectConfiguration = gitLabProjectConfigurationApi.getWorkspaceProjectConfiguration(projectId, SourceSpecification.newUserWorkspaceSourceSpecification(workspaceOneId, patchReleaseVersionId));

        Assert.assertNotNull(projectConfiguration);
        Assert.assertEquals(projectConfiguration.getArtifactId(), artifactId);
        Assert.assertEquals(projectConfiguration.getGroupId(), groupId);

        Workspace createdWorkspaceTwo = gitLabWorkspaceApi.newWorkspace(projectId, SourceSpecification.newGroupWorkspaceSourceSpecification(workspaceTwoId, patchReleaseVersionId));

        Assert.assertNotNull(createdWorkspaceTwo);
        Assert.assertEquals(workspaceTwoId, createdWorkspaceTwo.getWorkspaceId());
        Assert.assertEquals(projectId, createdWorkspaceTwo.getProjectId());
        Assert.assertNull(createdWorkspaceTwo.getUserId());

        ProjectConfiguration projectConfigurationTwo = gitLabProjectConfigurationApi.getWorkspaceProjectConfiguration(projectId, SourceSpecification.newGroupWorkspaceSourceSpecification(workspaceTwoId, patchReleaseVersionId));

        Assert.assertNotNull(projectConfigurationTwo);
        Assert.assertEquals(projectConfigurationTwo.getArtifactId(), artifactId);
        Assert.assertEquals(projectConfigurationTwo.getGroupId(), groupId);
    }

    public void runProjectVersionProjectConfigurationTest()
    {
        String projectName = "PCTestProjectThree";
        String description = "A test project.";
        String groupId = "org.finos.sdlc.test";
        String artifactId = "pctestprojthree";
        List<String> tags = Lists.mutable.with("doe", "moffitt", AbstractGitLabServerApiTest.INTEGRATION_TEST_PROJECT_TAG);

        Project createdProject = gitLabProjectApi.createProject(projectName, description, ProjectType.MANAGED, groupId, artifactId, tags);

        Assert.assertNotNull(createdProject);
        Assert.assertEquals(projectName, createdProject.getName());
        Assert.assertEquals(description, createdProject.getDescription());
        Assert.assertEquals(Sets.mutable.withAll(tags), Sets.mutable.withAll(createdProject.getTags()));
        Assert.assertNull(createdProject.getProjectType());

        String projectId = createdProject.getProjectId();
        Version version = gitlabVersionApi.newVersion(projectId, NewVersionType.PATCH, gitLabRevisionApi.getProjectRevisionContext(projectId).getCurrentRevision().getId(), "");

        ProjectConfiguration projectConfigurationTwo = gitLabProjectConfigurationApi.getVersionProjectConfiguration(projectId, version.getId());

        Assert.assertNotNull(projectConfigurationTwo);
        Assert.assertEquals(projectConfigurationTwo.getArtifactId(), artifactId);
        Assert.assertEquals(projectConfigurationTwo.getGroupId(), groupId);
    }

    public GitLabProjectApi getGitLabProjectApi()
    {
        return gitLabProjectApi;
    }
}
