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
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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
        ProjectType projectType = ProjectType.PRODUCTION;
        String groupId = "org.finos.sdlc.test";
        String artifactId = "testprojone";
        List<String> tags = Lists.mutable.with("doe", "moffitt", "gitlab-integration-tests");

        Project createdProject = gitLabProjectApi.createProject(projectName, description, projectType, groupId, artifactId, tags);

        assertNotNull(createdProject);
        assertEquals(projectName, createdProject.getName());
        assertEquals(description, createdProject.getDescription());
        assertEquals(projectType, createdProject.getProjectType());
        assertEquals(Sets.mutable.withAll(tags), Sets.mutable.withAll(createdProject.getTags()));
    }

    public void runGetProjectTest() throws LegendSDLCServerException
    {
        String projectName = "TestProjectTwo";
        String description = "A test project.";
        ProjectType projectType = ProjectType.PRODUCTION;
        String groupId = "org.finos.sdlc.test";
        String artifactId = "testprojtwo";
        List<String> tags = Lists.mutable.with("doe", "moffitt", "gitlab-integration-tests");

        Project createdProject = gitLabProjectApi.createProject(projectName, description, projectType, groupId, artifactId, tags);

        assertNotNull(createdProject);
        assertEquals(projectName, createdProject.getName());
        assertEquals(description, createdProject.getDescription());
        assertEquals(projectType, createdProject.getProjectType());
        assertEquals(Sets.mutable.withAll(tags), Sets.mutable.withAll(createdProject.getTags()));

        Project retrievedProject = gitLabProjectApi.getProject(createdProject.getProjectId());

        assertNotNull(retrievedProject);
        assertEquals(projectName, retrievedProject.getName());
        assertEquals(description, retrievedProject.getDescription());
        assertEquals(projectType, retrievedProject.getProjectType());
        assertEquals(Sets.mutable.withAll(tags), Sets.mutable.withAll(retrievedProject.getTags()));
    }

    public void runUpdateProjectTest()
    {
        String projectName = "TestProjectThree";
        String description = "A test project.";
        ProjectType projectType = ProjectType.PRODUCTION;
        String groupId = "org.finos.sdlc.test";
        String artifactId = "testprojthree";
        List<String> tags = Lists.mutable.with("doe", "moffitt", "gitlab-integration-tests");

        Project createdProject = gitLabProjectApi.createProject(projectName, description, projectType, groupId, artifactId, tags);

        assertNotNull(createdProject);
        assertEquals(projectName, createdProject.getName());
        assertEquals(description, createdProject.getDescription());
        assertEquals(projectType, createdProject.getProjectType());
        assertEquals(Sets.mutable.withAll(tags), Sets.mutable.withAll(createdProject.getTags()));

        String projectId = createdProject.getProjectId();
        String newProjectName = "TestProjectThreeMod";
        String newProjectDescription = "A modified test project.";
        List<String> newTags = Lists.mutable.with("doe", "moffitt", "main-stacks", "gitlab-integration-tests");
        List<String> tagsToAdd = Lists.mutable.with("bancroft");
        List<String> tagsToRemove = Lists.mutable.with("doe", "moffitt");
        List<String> expectedTags = Lists.mutable.with("main-stacks", "bancroft", "gitlab-integration-tests");

        gitLabProjectApi.changeProjectName(projectId, newProjectName);
        gitLabProjectApi.changeProjectDescription(projectId, newProjectDescription);
        gitLabProjectApi.setProjectTags(projectId, newTags);
        gitLabProjectApi.updateProjectTags(projectId, tagsToRemove, tagsToAdd);

        Project reRetrievedProject = gitLabProjectApi.getProject(projectId);

        assertEquals(newProjectName, reRetrievedProject.getName());
        assertEquals(newProjectDescription, reRetrievedProject.getDescription());
        assertEquals(Sets.mutable.withAll(expectedTags), Sets.mutable.withAll(reRetrievedProject.getTags()));
    }

    public GitLabProjectApi getGitLabProjectApi()
    {
        return gitLabProjectApi;
    }
}
