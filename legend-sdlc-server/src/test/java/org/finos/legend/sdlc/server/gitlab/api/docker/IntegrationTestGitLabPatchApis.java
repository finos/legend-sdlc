// Copyright 2023 Goldman Sachs
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
import org.finos.legend.sdlc.server.gitlab.api.GitLabPatchApi;
import org.finos.legend.sdlc.server.gitlab.api.GitLabPatchApiTestResource;
import org.finos.legend.sdlc.server.gitlab.api.GitLabProjectApi;
import org.finos.legend.sdlc.server.gitlab.api.GitLabProjectApiTestResource;
import org.finos.legend.sdlc.server.gitlab.api.GitLabRevisionApi;
import org.finos.legend.sdlc.server.gitlab.api.GitLabVersionApi;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.project.config.ProjectStructureConfiguration;
import org.junit.BeforeClass;
import org.junit.Test;

public class IntegrationTestGitLabPatchApis extends AbstractGitLabApiTest
{
    private static GitLabPatchApiTestResource gitLabPatchApiTestResource;

    @BeforeClass
    public static void setup() throws LegendSDLCServerException
    {
        setUpPatchApi();
    }

    @Test
    public void testCreatePatch() throws LegendSDLCServerException
    {
        gitLabPatchApiTestResource.runCreatePatchTest();
    }

    @Test
    public void testGetPatches() throws LegendSDLCServerException
    {
        gitLabPatchApiTestResource.runGetPatchesTest();
    }

    /**
     * Authenticates with OAuth2 and instantiate the test SDLC GitLabProjectApi.
     *
     * @throws LegendSDLCServerException if cannot authenticate to GitLab.
     */
    private static void setUpPatchApi() throws LegendSDLCServerException
    {
        GitLabUserContext gitLabMemberUserContext = prepareGitLabMemberUserContext();
        GitLabConfiguration gitLabConfig = GitLabConfiguration.newGitLabConfiguration(null, null, null, null, null, null);
        ProjectStructureConfiguration projectStructureConfig = ProjectStructureConfiguration.emptyConfiguration();
        GitLabUserContext gitLabUserContext = prepareGitLabOwnerUserContext();

        GitLabProjectApi gitLabProjectApi = new GitLabProjectApi(gitLabConfig, gitLabUserContext, projectStructureConfig, null, backgroundTaskProcessor, null);
        GitLabPatchApi gitLabPatchApi = new GitLabPatchApi(gitLabConfig, gitLabUserContext, backgroundTaskProcessor);
        GitLabRevisionApi gitLabRevisionApi = new GitLabRevisionApi(gitLabConfig, gitLabMemberUserContext, backgroundTaskProcessor);
        GitLabVersionApi gitLabVersionApi = new GitLabVersionApi(gitLabConfig, gitLabMemberUserContext, backgroundTaskProcessor);

        gitLabPatchApiTestResource = new GitLabPatchApiTestResource(gitLabPatchApi, gitLabProjectApi, gitLabRevisionApi, gitLabVersionApi);
    }
}
