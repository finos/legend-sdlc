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

package org.finos.legend.sdlc.backend.gitlab.api.server;

import org.finos.legend.sdlc.error.LegendSDLCException;
import org.finos.legend.sdlc.backend.gitlab.GitLabConfiguration;
import org.finos.legend.sdlc.backend.gitlab.api.GitLabEntityApi;
import org.finos.legend.sdlc.backend.gitlab.api.GitLabEntityApiTestResource;
import org.finos.legend.sdlc.backend.gitlab.api.GitLabPatchApi;
import org.finos.legend.sdlc.backend.gitlab.api.GitLabProjectApi;
import org.finos.legend.sdlc.backend.gitlab.api.GitLabReviewApi;
import org.finos.legend.sdlc.backend.gitlab.api.GitLabRevisionApi;
import org.finos.legend.sdlc.backend.gitlab.api.GitLabVersionApi;
import org.finos.legend.sdlc.backend.gitlab.api.GitLabWorkspaceApi;
import org.finos.legend.sdlc.backend.gitlab.auth.GitLabUserContext;
import org.gitlab4j.api.GitLabApiException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestGitLabServerEntityApis extends AbstractGitLabServerApiTest
{
    private static GitLabEntityApiTestResource gitLabEntityApiTestResource;

    @BeforeClass
    public static void setup() throws GitLabApiException
    {
        setUpEntityApi();
        cleanUpTestProjects(gitLabEntityApiTestResource.getGitLabProjectApi());
    }

    @AfterClass
    public static void teardown() throws LegendSDLCException
    {
        if (gitLabEntityApiTestResource != null)
        {
            cleanUpTestProjects(gitLabEntityApiTestResource.getGitLabProjectApi());
        }
    }

    @Test
    public void testEntitiesInNormalUserWorkspaceWorkflow() throws GitLabApiException
    {
        gitLabEntityApiTestResource.runEntitiesInNormalUserWorkspaceWorkflowTest();
    }

    @Test
    public void testEntitiesInNormalGroupWorkspaceWorkflow() throws GitLabApiException
    {
        gitLabEntityApiTestResource.runEntitiesInNormalGroupWorkspaceWorkflowTest();
    }

    @Test
    public void testEntitiesInNormalUserWorkspaceWorkflowForPatchReleaseVersion() throws GitLabApiException
    {
        gitLabEntityApiTestResource.runEntitiesInNormalUserWorkspaceWorkflowTestForPatchRelaseVersion();
    }


    @Test
    public void testEntitiesInNormalGroupWorkspaceWorkflowForPatchReleaseVersion() throws GitLabApiException
    {
        gitLabEntityApiTestResource.runEntitiesInNormalGroupWorkspaceWorkflowTestForPatchRelaseVersion();
    }

    /**
     * Authenticates with OAuth2 and instantiate the test resource.
     */
    private static void setUpEntityApi()
    {
        GitLabUserContext gitLabMemberUserContext = prepareGitLabMemberUserContext();
        GitLabUserContext gitLabOwnerUserContext = prepareGitLabOwnerUserContext();
        GitLabConfiguration gitLabConfig = GitLabConfiguration.newGitLabConfiguration(null, null, null, null, null, GitLabConfiguration.NewProjectVisibility.PRIVATE);

        GitLabProjectApi gitLabProjectApi = new GitLabProjectApi(gitLabConfig, gitLabOwnerUserContext, null, null, backgroundTaskProcessor, null);
        GitLabRevisionApi gitLabRevisionApi = new GitLabRevisionApi(gitLabConfig, gitLabMemberUserContext, backgroundTaskProcessor);
        GitLabWorkspaceApi gitLabWorkspaceApi = new GitLabWorkspaceApi(gitLabConfig, gitLabMemberUserContext, gitLabProjectApi, gitLabRevisionApi, backgroundTaskProcessor);
        GitLabEntityApi gitLabEntityApi = new GitLabEntityApi(gitLabConfig, gitLabMemberUserContext, backgroundTaskProcessor);
        GitLabReviewApi gitLabCommitterReviewApi = new GitLabReviewApi(gitLabConfig, gitLabMemberUserContext, backgroundTaskProcessor);
        GitLabReviewApi gitLabApproverReviewApi = new GitLabReviewApi(gitLabConfig, gitLabOwnerUserContext, backgroundTaskProcessor);
        GitLabPatchApi gitLabPatchApi = new GitLabPatchApi(gitLabConfig, gitLabMemberUserContext, backgroundTaskProcessor);
        GitLabVersionApi gitLabVersionApi = new GitLabVersionApi(gitLabConfig, gitLabMemberUserContext, backgroundTaskProcessor);

        gitLabEntityApiTestResource = new GitLabEntityApiTestResource(gitLabProjectApi, gitLabWorkspaceApi, gitLabEntityApi, gitLabCommitterReviewApi, gitLabApproverReviewApi, gitLabMemberUserContext, gitLabPatchApi, gitLabVersionApi, gitLabRevisionApi);
    }
}
