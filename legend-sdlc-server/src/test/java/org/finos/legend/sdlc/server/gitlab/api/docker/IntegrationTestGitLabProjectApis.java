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

import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.GitLabConfiguration;
import org.finos.legend.sdlc.server.gitlab.api.GitLabProjectApi;
import org.finos.legend.sdlc.server.gitlab.api.GitLabProjectApiTestResource;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.project.config.ProjectStructureConfiguration;
import org.junit.BeforeClass;
import org.junit.Test;

public class IntegrationTestGitLabProjectApis extends AbstractGitLabApiTest
{
    private static GitLabProjectApiTestResource gitLabProjectApiTestResource;

    @BeforeClass
    public static void setup() throws LegendSDLCServerException
    {
        setUpProjectApi();
    }

    @Test
    public void testCreateProject() throws LegendSDLCServerException
    {
        gitLabProjectApiTestResource.runCreateProjectTest();
    }

    @Test
    public void testGetProject() throws LegendSDLCServerException
    {
        gitLabProjectApiTestResource.runGetProjectTest();
    }

    @Test
    public void testUpdateProject()
    {
        gitLabProjectApiTestResource.runUpdateProjectTest();
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
        GitLabUserContext gitLabUserContext = prepareGitLabOwnerUserContext();

        GitLabProjectApi gitLabProjectApi = new GitLabProjectApi(gitLabConfig, gitLabUserContext, projectStructureConfig, null, null, backgroundTaskProcessor, null);
        gitLabProjectApiTestResource = new GitLabProjectApiTestResource(gitLabProjectApi);
    }
}
