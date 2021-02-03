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
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.server.gitlab.GitLabConfiguration;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.project.config.ProjectStructureConfiguration;
import org.finos.legend.sdlc.server.tools.BackgroundTaskProcessor;
import org.gitlab4j.api.GitLabApiException;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class IntegrationTestGitLabWorkspaceApis extends AbstractGitLabApiTest
{
    private static GitLabRevisionApi gitLabRevisionApi;
    private static GitLabWorkspaceApi gitLabWorkspaceApi;
    private static GitLabProjectApi gitLabProjectApi;

    @BeforeClass
    public static void setup() throws GitLabApiException
    {
        setUpWorkspaceApi();
    }

    @Test
    public void testCreateWorkspace()
    {
        String projectName = "WorkspaceTestProject";
        String description = "A test project.";
        ProjectType projectType = ProjectType.PROTOTYPE;
        String groupId = "org.finos.sdlc.test";
        String artifactId = "worktestproj";
        List<String> tags = Lists.mutable.with("doe", "moffitt");
        String workspaceId = "testworkspace";

        Project createdProject = gitLabProjectApi.createProject(projectName, description, projectType, groupId, artifactId, tags);

        assertNotNull(createdProject);
        assertEquals(projectName, createdProject.getName());
        assertEquals(description, createdProject.getDescription());
        assertEquals(projectType, createdProject.getProjectType());
        assertEquals(Sets.mutable.withAll(tags), Sets.mutable.withAll(createdProject.getTags()));

        String projectId = createdProject.getProjectId();
        Workspace createdWorkspace = gitLabWorkspaceApi.newWorkspace(projectId, workspaceId);

        assertNotNull(createdWorkspace);
        assertEquals(workspaceId, createdWorkspace.getWorkspaceId());
        assertEquals(projectId, createdWorkspace.getProjectId());
    }

    /**
     * Authenticates with OAuth2 and instantiate the test SDLC GitLabWorkspaceApi.
     */
    private static void setUpWorkspaceApi()
    {
        GitLabConfiguration gitLabConfig = GitLabConfiguration.newGitLabConfiguration(null, null, null, null, null);
        ProjectStructureConfiguration projectStructureConfig = ProjectStructureConfiguration.emptyConfiguration();
        GitLabUserContext gitLabUserContext = prepareGitLabOwnerUserContext();

        gitLabProjectApi = new GitLabProjectApi(gitLabConfig, gitLabUserContext, projectStructureConfig, null, null, new BackgroundTaskProcessor(1));
        gitLabRevisionApi = new GitLabRevisionApi(gitLabUserContext, new BackgroundTaskProcessor(1));
        gitLabWorkspaceApi = new GitLabWorkspaceApi(gitLabUserContext, gitLabRevisionApi, new BackgroundTaskProcessor(1));
    }
}
