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
import org.finos.legend.sdlc.domain.model.project.Project;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.project.accessRole.AuthorizableProjectAction;
import org.finos.legend.sdlc.domain.model.project.accessRole.UserPermission;
import org.finos.legend.sdlc.domain.model.user.User;
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.api.server.AbstractGitLabServerApiTest;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
import org.gitlab4j.api.models.AccessLevel;
import org.gitlab4j.api.models.Member;
import org.gitlab4j.api.models.ProtectedTag;
import org.junit.Assert;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Substantial test resource class for project API tests shared by the docker-based and server-based GitLab tests.
 */
public class GitLabProjectApiTestResource
{
    private final GitLabProjectApi gitLabProjectApi;

    public GitLabProjectApiTestResource(GitLabProjectApi gitLabProjectApi)
    {
        this.gitLabProjectApi = gitLabProjectApi;
    }

    public void runCreateProjectTest() throws LegendSDLCServerException
    {
        String projectName = "TestProjectOne";
        String description = "A test project.";
        String groupId = "org.finos.sdlc.test";
        String artifactId = "testprojone";
        List<String> tags = Lists.mutable.with("doe", "moffitt", AbstractGitLabServerApiTest.INTEGRATION_TEST_PROJECT_TAG);

        Project createdProject = this.gitLabProjectApi.createProject(projectName, description, null, groupId, artifactId, tags);

        Assert.assertNotNull(createdProject);
        Assert.assertEquals(projectName, createdProject.getName());
        Assert.assertEquals(description, createdProject.getDescription());
        Assert.assertNull(createdProject.getProjectType());
        Assert.assertEquals(Sets.mutable.withAll(tags), Sets.mutable.withAll(createdProject.getTags()));

        String projectId = createdProject.getProjectId();
        Assert.assertNotNull(projectId);

        Assert.assertEquals(ProjectType.MANAGED, this.gitLabProjectApi.getProjectConfiguration(projectId, SourceSpecification.projectSourceSpecification()).getProjectType());
        Assert.assertNull(gitLabProjectApi.getProject(projectId).getProjectType());
        Assert.assertNotNull(gitLabProjectApi.getProjectConfiguration(projectId, SourceSpecification.projectSourceSpecification()).getProjectStructureVersion().getExtensionVersion());

        Assert.assertEquals(Sets.mutable.with("/project.json",
                "/PANGRAM.TXT",
                "/pom.xml",
                "/testprojone-entities/pom.xml",
                "/testprojone-entities/src/test/java/org/finos/legend/sdlc/EntityValidationTest.java",
                "/testprojone-file-generation/pom.xml",
                "/testprojone-service-execution/pom.xml",
                "/testprojone-versioned-entities/pom.xml"
        ), this.gitLabProjectApi.getProjectFileAccessProvider().getProjectFileAccessContext(projectId).getFiles().map(ProjectFileAccessProvider.ProjectFile::getPath).collect(Collectors.toSet()));
    }

    public void runCreateManagedProjectTest() throws LegendSDLCServerException
    {
        String projectName = "TestManagedProjectOne";
        String description = "A test project.";
        String groupId = "org.finos.sdlc.test";
        String artifactId = "testprojone";
        List<String> tags = Lists.mutable.with("doe", "moffitt", AbstractGitLabServerApiTest.INTEGRATION_TEST_PROJECT_TAG);

        Project createdProject = this.gitLabProjectApi.createProject(projectName, description, ProjectType.MANAGED, groupId, artifactId, tags);

        Assert.assertNotNull(createdProject);
        Assert.assertEquals(projectName, createdProject.getName());
        Assert.assertEquals(description, createdProject.getDescription());
        Assert.assertNull(createdProject.getProjectType());
        Assert.assertEquals(Sets.mutable.withAll(tags), Sets.mutable.withAll(createdProject.getTags()));

        String projectId = createdProject.getProjectId();
        Assert.assertNotNull(projectId);

        Assert.assertEquals(ProjectType.MANAGED, this.gitLabProjectApi.getProjectConfiguration(projectId, SourceSpecification.projectSourceSpecification()).getProjectType());
        Assert.assertNull(gitLabProjectApi.getProject(projectId).getProjectType());
        Assert.assertNotNull(gitLabProjectApi.getProjectConfiguration(projectId, SourceSpecification.projectSourceSpecification()).getProjectStructureVersion().getExtensionVersion());

        Assert.assertEquals(Sets.mutable.with("/project.json",
                "/PANGRAM.TXT",
                "/pom.xml",
                "/testprojone-entities/pom.xml",
                "/testprojone-entities/src/test/java/org/finos/legend/sdlc/EntityValidationTest.java",
                "/testprojone-file-generation/pom.xml",
                "/testprojone-service-execution/pom.xml",
                "/testprojone-versioned-entities/pom.xml"
        ), this.gitLabProjectApi.getProjectFileAccessProvider().getFileAccessContext(projectId, SourceSpecification.projectSourceSpecification()).getFiles().map(ProjectFileAccessProvider.ProjectFile::getPath).collect(Collectors.toSet()));
    }

    public void runCreateProductionProjectTest() throws LegendSDLCServerException
    {
        String projectName = "TestProductionProjectOne";
        String description = "A test project.";
        String groupId = "org.finos.sdlc.test";
        String artifactId = "testprojone";
        List<String> tags = Lists.mutable.with("doe", "moffitt", AbstractGitLabServerApiTest.INTEGRATION_TEST_PROJECT_TAG);

        LegendSDLCServerException e = Assert.assertThrows(LegendSDLCServerException.class, () -> this.gitLabProjectApi.createProject(projectName, description, ProjectType.PRODUCTION, groupId, artifactId, tags));
        Assert.assertEquals("Invalid type: PRODUCTION", e.getMessage());
    }

    public void runCreateEmbeddedProjectTest() throws LegendSDLCServerException
    {
        String projectName = "TestEmbeddedProjectOne";
        String description = "A test project.";
        String groupId = "org.finos.sdlc.test";
        String artifactId = "testprojone";
        List<String> tags = Lists.mutable.with("doe", "moffitt", AbstractGitLabServerApiTest.INTEGRATION_TEST_PROJECT_TAG);

        Project createdProject = this.gitLabProjectApi.createProject(projectName, description, ProjectType.EMBEDDED, groupId, artifactId, tags);

        Assert.assertNotNull(createdProject);
        Assert.assertEquals(projectName, createdProject.getName());
        Assert.assertEquals(description, createdProject.getDescription());
        Assert.assertNull(createdProject.getProjectType());
        Assert.assertEquals(Sets.mutable.withAll(tags), Sets.mutable.withAll(createdProject.getTags()));

        String projectId = createdProject.getProjectId();
        Assert.assertNotNull(projectId);

        Assert.assertEquals(ProjectType.EMBEDDED, this.gitLabProjectApi.getProjectConfiguration(projectId, SourceSpecification.projectSourceSpecification()).getProjectType());
        Assert.assertNull(gitLabProjectApi.getProject(projectId).getProjectType());
        Assert.assertNull(gitLabProjectApi.getProjectConfiguration(projectId, SourceSpecification.projectSourceSpecification()).getProjectStructureVersion().getExtensionVersion());

        Assert.assertEquals(Sets.mutable.with("/project.json"
        ), this.gitLabProjectApi.getProjectFileAccessProvider().getFileAccessContext(projectId, SourceSpecification.projectSourceSpecification()).getFiles().map(ProjectFileAccessProvider.ProjectFile::getPath).collect(Collectors.toSet()));
    }

    public void runGetProjectTest() throws LegendSDLCServerException
    {
        String projectName = "TestProjectTwo";
        String description = "A test project.";
        String groupId = "org.finos.sdlc.test";
        String artifactId = "testprojtwo";
        List<String> tags = Lists.mutable.with("doe", "moffitt", AbstractGitLabServerApiTest.INTEGRATION_TEST_PROJECT_TAG);

        Project createdProject = this.gitLabProjectApi.createProject(projectName, description, ProjectType.MANAGED, groupId, artifactId, tags);

        Assert.assertNotNull(createdProject);
        Assert.assertEquals(projectName, createdProject.getName());
        Assert.assertEquals(description, createdProject.getDescription());
        Assert.assertNull(createdProject.getProjectType());
        Assert.assertEquals(Sets.mutable.withAll(tags), Sets.mutable.withAll(createdProject.getTags()));

        Project retrievedProject = this.gitLabProjectApi.getProject(createdProject.getProjectId());

        Assert.assertNotNull(retrievedProject);
        Assert.assertEquals(projectName, retrievedProject.getName());
        Assert.assertEquals(description, retrievedProject.getDescription());
        Assert.assertEquals(Sets.mutable.withAll(tags), Sets.mutable.withAll(retrievedProject.getTags()));
    }

    public void runUpdateProjectTest()
    {
        String projectName = "TestProjectThree";
        String description = "A test project.";
        String groupId = "org.finos.sdlc.test";
        String artifactId = "testprojthree";
        List<String> tags = Lists.mutable.with("doe", "moffitt", AbstractGitLabServerApiTest.INTEGRATION_TEST_PROJECT_TAG);

        Project createdProject = this.gitLabProjectApi.createProject(projectName, description, ProjectType.MANAGED, groupId, artifactId, tags);

        Assert.assertNotNull(createdProject);
        Assert.assertEquals(projectName, createdProject.getName());
        Assert.assertEquals(description, createdProject.getDescription());
        Assert.assertNull(createdProject.getProjectType());
        Assert.assertEquals(Sets.mutable.withAll(tags), Sets.mutable.withAll(createdProject.getTags()));

        String projectId = createdProject.getProjectId();
        String newProjectName = "TestProjectThreeMod";
        String newProjectDescription = "A modified test project.";
        List<String> newTags = Lists.mutable.with("doe", "moffitt", "main-stacks", AbstractGitLabServerApiTest.INTEGRATION_TEST_PROJECT_TAG);
        List<String> tagsToAdd = Lists.mutable.with("bancroft");
        List<String> tagsToRemove = Lists.mutable.with("doe", "moffitt");
        List<String> expectedTags = Lists.mutable.with("main-stacks", "bancroft", AbstractGitLabServerApiTest.INTEGRATION_TEST_PROJECT_TAG);

        this.gitLabProjectApi.changeProjectName(projectId, newProjectName);
        this.gitLabProjectApi.changeProjectDescription(projectId, newProjectDescription);
        this.gitLabProjectApi.setProjectTags(projectId, newTags);
        this.gitLabProjectApi.updateProjectTags(projectId, tagsToRemove, tagsToAdd);

        Project reRetrievedProject = this.gitLabProjectApi.getProject(projectId);

        Assert.assertEquals(newProjectName, reRetrievedProject.getName());
        Assert.assertEquals(newProjectDescription, reRetrievedProject.getDescription());
        Assert.assertEquals(Sets.mutable.withAll(expectedTags), Sets.mutable.withAll(reRetrievedProject.getTags()));
    }

    public void runGetAllUsersAuthorizedActions()
    {
        Set<AuthorizableProjectAction> actionSet = Collections.singleton(AuthorizableProjectAction.COMMIT_REVIEW);
        List<ProtectedTag> protectedTags = null;
        List<Member> members = new ArrayList<>();
        members.add(new Member().withAccessLevel(AccessLevel.MAINTAINER).withName("haveAccess").withId(1));
        members.add(new Member().withAccessLevel(AccessLevel.NONE).withName("noAccess").withId(2));
        Set<UserPermission> users = this.gitLabProjectApi.processUserAuthorizedActions(protectedTags, members, actionSet);
        Set<UserPermission> expectedUsers = new HashSet<>();
        expectedUsers.add(createUserPermission("1", "haveAccess", Collections.singleton(AuthorizableProjectAction.COMMIT_REVIEW)));
        expectedUsers.add(createUserPermission("2", "noAccess", Collections.emptySet()));
        Assert.assertEquals("List of authorized users", users, expectedUsers);
    }

    private UserPermission createUserPermission(String userId, String name, Set<AuthorizableProjectAction>  authorizableProjectActions)
    {
        return new UserPermission()
        {
            @Override
            public User getUser()
            {
                return new User()
                {
                    @Override
                    public String getUserId()
                    {
                        return userId;
                    }

                    @Override
                    public String getName()
                    {
                        return name;
                    }
                };
            }

            @Override
            public Set<AuthorizableProjectAction> getAuhorizedProjectAction()
            {
                return authorizableProjectActions;
            }
        };
    }

    public GitLabProjectApi getGitLabProjectApi()
    {
        return this.gitLabProjectApi;
    }
}
