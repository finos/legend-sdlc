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

import com.squarespace.jersey2.guice.JerseyGuiceUtils;
import org.eclipse.collections.api.factory.Lists;
import org.finos.legend.sdlc.domain.model.entity.Entity;
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
import org.junit.experimental.categories.Category;

import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(GitLabIntegrationTest.class)
public class IntegrationTestGitLabEntityApis extends AbstractGitLabApiTest
{
    private static GitLabProjectApi gitLabProjectApi;
    private static GitLabWorkspaceApi gitLabWorkspaceApi;
    private static GitLabRevisionApi gitLabRevisionApi;
    private static GitLabEntityApi gitLabEntityApi;

    @BeforeClass
    public static void setup() throws GitLabApiException
    {
        JerseyGuiceUtils.install((s, serviceLocator) -> null); // TODO: temp solution to handle undeclared dependency
        setUpEntityApi();
    }

    @Test
    public void testEntitiesInNormalWorkflow()
    {
        String projectName = "CommitFlowTestProject";
        String description = "A test project.";
        ProjectType projectType = ProjectType.PROTOTYPE;
        String groupId = "EntityTestGroup";
        String artifactId = "entitytestproj";
        List<String> tags = Lists.mutable.with("doe", "moffitt");
        String workspaceName = "entitytestworkspace";

        Project createdProject = gitLabProjectApi.createProject(projectName, description, projectType, groupId, artifactId, tags);

        String projectId = createdProject.getProjectId();
        Workspace createdWorkspace = gitLabWorkspaceApi.newWorkspace(projectId, workspaceName);

        String workspaceId = createdWorkspace.getWorkspaceId();
        List<Entity> initialEntities = gitLabEntityApi.getWorkspaceEntityAccessContext(projectId, workspaceId).getEntities(null, null, null);

        assertNotNull(initialEntities);
        assertTrue(initialEntities.isEmpty());
    }

    /**
     * Authenticates with OAuth2 and instantiate the test SDLC GitLabEntityApi.
     */
    private static void setUpEntityApi()
    {
        GitLabUserContext gitLabUserContext = prepareGitLabUserContext();
        GitLabConfiguration gitLabConfig = GitLabConfiguration.newGitLabConfiguration(null, null, null, null, null);
        ProjectStructureConfiguration projectStructureConfig = ProjectStructureConfiguration.emptyConfiguration();

        gitLabProjectApi = new GitLabProjectApi(gitLabConfig, gitLabUserContext, projectStructureConfig, null, null, new BackgroundTaskProcessor(1));
        gitLabRevisionApi = new GitLabRevisionApi(gitLabUserContext, new BackgroundTaskProcessor(1));
        gitLabWorkspaceApi = new GitLabWorkspaceApi(gitLabUserContext, gitLabRevisionApi, new BackgroundTaskProcessor(1));
        gitLabEntityApi = new GitLabEntityApi(gitLabUserContext, new BackgroundTaskProcessor(1));
    }
}
