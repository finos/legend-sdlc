// Copyright 2020 Goldman Sachs
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
import org.finos.legend.sdlc.domain.model.project.Project;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.GitLabConfiguration;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.project.config.ProjectStructureConfiguration;
import org.finos.legend.sdlc.server.tools.BackgroundTaskProcessor;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(GitLabIntegrationTest.class)
public class IntegrationTestGitLabProjectApis extends AbstractGitLabApiTest
{
    private static GitLabProjectApi gitLabProjectApi;

    @BeforeClass
    public static void setup() throws LegendSDLCServerException
    {
        setUpProjectApi();
    }

    @AfterClass
    public static void tearDown()
    {
        cleanUpProjectApi();
    }

    @Test
    public void testCreateProject() throws LegendSDLCServerException
    {
        String projectName = "TestProjectOne";
        String description = "A test project.";
        ProjectType projectType = ProjectType.PROTOTYPE;
        String groupId = "testGroup";
        String artifactId = "testprojone";
        List<String> tags = Lists.mutable.with("doe", "moffitt");

        Project createdProject = gitLabProjectApi.createProject(projectName, description, projectType, groupId, artifactId, tags);

        assertNotNull(createdProject);
        assertEquals(projectName, createdProject.getName());
        assertEquals(description, createdProject.getDescription());
        assertEquals(projectType, createdProject.getProjectType());
        assertEquals(tags, createdProject.getTags());
    }

    @Test
    public void testGetProject() throws LegendSDLCServerException
    {
        String projectName = "TestProjectTwo";
        String description = "A test project.";
        ProjectType projectType = ProjectType.PROTOTYPE;
        String groupId = "testGroup";
        String artifactId = "testprojtwo";
        List<String> tags = Lists.mutable.with("doe", "moffitt");

        Project createdProject = gitLabProjectApi.createProject(projectName, description, projectType, groupId, artifactId, tags);

        assertNotNull(createdProject);
        assertEquals(projectName, createdProject.getName());
        assertEquals(description, createdProject.getDescription());
        assertEquals(projectType, createdProject.getProjectType());
        assertEquals(tags, createdProject.getTags());

        Project retrievedProject = gitLabProjectApi.getProject(createdProject.getProjectId());

        assertNotNull(retrievedProject);
        assertEquals(projectName, retrievedProject.getName());
        assertEquals(description, retrievedProject.getDescription());
        assertEquals(projectType, retrievedProject.getProjectType());
        assertEquals(tags, retrievedProject.getTags());
    }

    @Test
    public void testUpdateProject()
    {
        String projectName = "TestProjectThree";
        String description = "A test project.";
        ProjectType projectType = ProjectType.PROTOTYPE;
        String groupId = "testGroup";
        String artifactId = "testprojthree";
        List<String> tags = Lists.mutable.with("doe", "moffitt");

        Project createdProject = gitLabProjectApi.createProject(projectName, description, projectType, groupId, artifactId, tags);

        assertNotNull(createdProject);
        assertEquals(projectName, createdProject.getName());
        assertEquals(description, createdProject.getDescription());
        assertEquals(projectType, createdProject.getProjectType());
        assertEquals(tags, createdProject.getTags());

        String projectId = createdProject.getProjectId();
        String newProjectName = "TestProjectThreeMod";
        String newProjectDescription = "A modified test project.";
        List<String> newTags = Lists.mutable.with("doe", "moffitt", "main-stacks");
        List<String> tagsToAdd = Lists.mutable.with("bancroft");
        List<String> tagsToRemove = Lists.mutable.with("doe", "moffitt");
        List<String> expectedTags = Lists.mutable.with("main-stacks", "bancroft");

        gitLabProjectApi.changeProjectName(projectId, newProjectName);
        gitLabProjectApi.changeProjectDescription(projectId, newProjectDescription);
        gitLabProjectApi.setProjectTags(projectId, newTags);
        gitLabProjectApi.updateProjectTags(projectId, tagsToRemove, tagsToAdd);

        Project reRetrievedProject = gitLabProjectApi.getProject(projectId);

        assertEquals(newProjectName, reRetrievedProject.getName());
        assertEquals(newProjectDescription, reRetrievedProject.getDescription());
        assertTrue(checkTagEquals(expectedTags, reRetrievedProject.getTags()));
    }

    /**
     * Authenticates with OAuth2 and instantiate the test SDLC GitLabProjectApi.
     *
     * @throws LegendSDLCServerException if cannot authenticates to GitLab.
     */
    private static void setUpProjectApi() throws LegendSDLCServerException
    {
        GitLabConfiguration gitLabConfig = GitLabConfiguration.newGitLabConfiguration(null, null, null, null, null);
        ProjectStructureConfiguration projectStructureConfig = ProjectStructureConfiguration.emptyConfiguration();
        GitLabUserContext gitLabUserContext = prepareGitLabUserContext();

        gitLabProjectApi = new GitLabProjectApi(gitLabConfig, gitLabUserContext, projectStructureConfig, null, null, new BackgroundTaskProcessor(1));
    }

    /**
     * Cleans up the transient data in instantiated test GitLabProjectApi.
     */
    private static void cleanUpProjectApi()
    {
    }

    /**
     * Util method that compares two project tag lists equality.
     *
     * @return true if and only if the tag lists have the same contents.
     */
    private boolean checkTagEquals(List<String> tagOne, List<String> tagTwo)
    {
        return tagOne.size() == tagTwo.size() && tagOne.containsAll(tagTwo) && tagTwo.containsAll(tagOne);
    }
}