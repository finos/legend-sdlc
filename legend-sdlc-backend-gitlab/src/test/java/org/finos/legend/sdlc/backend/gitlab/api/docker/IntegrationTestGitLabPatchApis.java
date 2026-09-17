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

package org.finos.legend.sdlc.backend.gitlab.api.docker;

import org.finos.legend.sdlc.error.LegendSDLCException;
import org.finos.legend.sdlc.backend.gitlab.GitLabConfiguration;
import org.finos.legend.sdlc.backend.gitlab.api.GitLabPatchApi;
import org.finos.legend.sdlc.backend.gitlab.api.GitLabPatchApiTestResource;
import org.finos.legend.sdlc.backend.gitlab.api.GitLabProjectApi;
import org.finos.legend.sdlc.backend.gitlab.api.GitLabProjectApiTestResource;
import org.finos.legend.sdlc.backend.gitlab.api.GitLabRevisionApi;
import org.finos.legend.sdlc.backend.gitlab.api.GitLabVersionApi;
import org.finos.legend.sdlc.backend.gitlab.auth.GitLabUserContext;
import org.junit.BeforeClass;
import org.junit.Test;

public class IntegrationTestGitLabPatchApis extends AbstractGitLabApiTest
{
    private static GitLabPatchApiTestResource gitLabPatchApiTestResource;

    @BeforeClass
    public static void setup() throws LegendSDLCException
    {
        setUpPatchApi();
    }

    @Test
    public void testGetPatch() throws LegendSDLCException
    {
        gitLabPatchApiTestResource.runGetPatchTest();
    }

    @Test
    public void testCreatePatch() throws LegendSDLCException
    {
        gitLabPatchApiTestResource.runCreatePatchTest();
    }

    @Test
    public void testGetPatches() throws LegendSDLCException
    {
        gitLabPatchApiTestResource.runGetPatchesTest();
    }

    /**
     * Authenticates with OAuth2 and instantiate the test SDLC GitLabProjectApi.
     *
     * @throws LegendSDLCException if cannot authenticate to GitLab.
     */
    private static void setUpPatchApi() throws LegendSDLCException
    {
        GitLabUserContext gitLabMemberUserContext = prepareGitLabMemberUserContext();
        GitLabConfiguration gitLabConfig = GitLabConfiguration.newGitLabConfiguration(null, null, null, null, null, null);
        GitLabUserContext gitLabUserContext = prepareGitLabOwnerUserContext();

        GitLabProjectApi gitLabProjectApi = new GitLabProjectApi(gitLabConfig, gitLabUserContext, null, null, backgroundTaskProcessor, null);
        GitLabPatchApi gitLabPatchApi = new GitLabPatchApi(gitLabConfig, gitLabUserContext, backgroundTaskProcessor);
        GitLabRevisionApi gitLabRevisionApi = new GitLabRevisionApi(gitLabConfig, gitLabMemberUserContext, backgroundTaskProcessor);
        GitLabVersionApi gitLabVersionApi = new GitLabVersionApi(gitLabConfig, gitLabMemberUserContext, backgroundTaskProcessor);

        gitLabPatchApiTestResource = new GitLabPatchApiTestResource(gitLabPatchApi, gitLabProjectApi, gitLabRevisionApi, gitLabVersionApi);
    }
}
