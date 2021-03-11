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

package org.finos.legend.sdlc.server.gitlab.api.docker;

import org.finos.legend.sdlc.server.gitlab.GitLabConfiguration;
import org.finos.legend.sdlc.server.gitlab.api.GitLabProjectApi;
import org.finos.legend.sdlc.server.gitlab.api.GitLabRevisionApi;
import org.finos.legend.sdlc.server.gitlab.api.GitLabWorkspaceApi;
import org.finos.legend.sdlc.server.gitlab.api.GitLabWorkspaceApiTestResource;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.project.config.ProjectStructureConfiguration;
import org.gitlab4j.api.GitLabApiException;
import org.junit.BeforeClass;
import org.junit.Test;

public class IntegrationTestGitLabWorkspaceApis extends AbstractGitLabApiTest
{
    private static GitLabWorkspaceApiTestResource gitLabWorkspaceApiTestResource;

    @BeforeClass
    public static void setup() throws GitLabApiException
    {
        setUpWorkspaceApi();
    }

    @Test
    public void testCreateWorkspace()
    {
        gitLabWorkspaceApiTestResource.runCreateWorkspaceTest();
    }

    /**
     * Authenticates with OAuth2 and instantiate the test SDLC GitLabWorkspaceApi.
     */
    private static void setUpWorkspaceApi()
    {
        GitLabConfiguration gitLabConfig = GitLabConfiguration.newGitLabConfiguration(null, null, null, null, null);
        ProjectStructureConfiguration projectStructureConfig = ProjectStructureConfiguration.emptyConfiguration();
        GitLabUserContext gitLabUserContext = prepareGitLabOwnerUserContext();

        GitLabProjectApi gitLabProjectApi = new GitLabProjectApi(gitLabConfig, gitLabUserContext, projectStructureConfig, null, null, backgroundTaskProcessor);
        GitLabRevisionApi gitLabRevisionApi = new GitLabRevisionApi(gitLabUserContext, backgroundTaskProcessor);
        GitLabWorkspaceApi gitLabWorkspaceApi = new GitLabWorkspaceApi(gitLabUserContext, gitLabRevisionApi, backgroundTaskProcessor);
        gitLabWorkspaceApiTestResource = new GitLabWorkspaceApiTestResource(gitLabRevisionApi, gitLabWorkspaceApi, gitLabProjectApi);
    }
}
