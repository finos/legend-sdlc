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

package org.finos.legend.sdlc.server.gitlab.api.server;

import com.squarespace.jersey2.guice.JerseyGuiceUtils;
import org.eclipse.collections.api.factory.Lists;
import org.finos.legend.sdlc.domain.model.project.Project;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.api.GitLabApiTestSetupUtil;
import org.finos.legend.sdlc.server.gitlab.api.GitLabProjectApi;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.tools.BackgroundTaskProcessor;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * This test suite is run against the actual GitLab server with testing account setup. The suite is skipped by default.
 * If wishing to run these tests, please run maven verify with the test-gitlab-com maven profile
 */
public class AbstractGitLabServerApiTest
{
    // Note that for the gitlab.com -based tests, the test member and owner are of the same GitLab account
    // thus have the same credentials, yet used to create separate GitLab API instances for merge request related tests.
    static final String TEST_OWNER_USERNAME = System.getenv("GITLAB_INTEGRATION_TEST_USERNAME");
    static final String TEST_OWNER_PASSWORD = System.getenv("GITLAB_INTEGRATION_TEST_PASSWORD");
    static final String TEST_MEMBER_USERNAME = TEST_OWNER_USERNAME;
    static final String TEST_MEMBER_PASSWORD = TEST_OWNER_PASSWORD;
    static final String TEST_HOST_SCHEME = "https";
    static final String TEST_HOST_HOST = "gitlab.com";
    static final Integer TEST_HOST_PORT = null;
    static final String TEST_HOST_URL = "https://gitlab.com";
    public static final String INTEGRATION_TEST_PROJECT_TAG = "gitlab_integration_tests";

    static BackgroundTaskProcessor backgroundTaskProcessor;

    static final Logger LOGGER = LoggerFactory.getLogger(AbstractGitLabServerApiTest.class);

    @BeforeClass
    public static void suiteSetup()
    {
        JerseyGuiceUtils.install((s, serviceLocator) -> null);
        backgroundTaskProcessor = new BackgroundTaskProcessor(1);
    }

    @AfterClass
    public static void shutDown()
    {
        if (backgroundTaskProcessor != null)
        {
            LOGGER.info("Shutting down backgroundTaskProcessor.");
            backgroundTaskProcessor.shutdown();
            LOGGER.info("Shut down backgroundTaskProcessor.");
        }
    }

    /**
     * Authenticates to GitLab and creates a test GitLabUserContext for a project owner.
     * Please note that for the server test, separate instances of GitLabApis are necessary for owner and member for the same test account.
     *
     * @return A test GitLabUserContext for a project owner.
     * @throws LegendSDLCServerException if cannot authenticate to GitLab via OAuth.
     */
    protected static GitLabUserContext prepareGitLabOwnerUserContext() throws LegendSDLCServerException
    {
        return prepareGitLabUserContextHelper(TEST_OWNER_USERNAME, TEST_OWNER_PASSWORD);
    }

    /**
     * Authenticates to GitLab and creates a test GitLabUserContext for a project member.
     *
     * @return A test GitLabUserContext for a project member.
     * @throws LegendSDLCServerException if cannot authenticate to GitLab via OAuth.
     */
    protected static GitLabUserContext prepareGitLabMemberUserContext() throws LegendSDLCServerException
    {
        return prepareGitLabUserContextHelper(TEST_MEMBER_USERNAME, TEST_MEMBER_PASSWORD);
    }

    /**
     * Authenticates to GitLab and creates a test GitLabUserContext.
     *
     * @param username the name of user for whom we create this context.
     * @param password the password of user for whom we create this context.
     */
    private static GitLabUserContext prepareGitLabUserContextHelper(String username, String password) throws LegendSDLCServerException
    {
        return GitLabApiTestSetupUtil.prepareGitLabUserContextHelper(username, password, TEST_HOST_URL, TEST_HOST_SCHEME, TEST_HOST_HOST, TEST_HOST_PORT);
    }

    /**
     * Clean up all potential test projects.
     */
    protected static void cleanUpTestProjects(GitLabProjectApi gitLabProjectApi)
    {
        List<Project> projectsToBeCleaned = gitLabProjectApi.getProjects(true, "", Lists.mutable.with(INTEGRATION_TEST_PROJECT_TAG), Lists.mutable.empty());
        for (Project project : projectsToBeCleaned)
        {
            String projectId = project.getProjectId();
            String projectName = project.getName();

            LOGGER.info("Deleting test project id: {}, name: {}", projectId, projectName);
            try
            {
                gitLabProjectApi.deleteProject(projectId);
            }
            catch (Exception e)
            {
                LOGGER.warn("Failed to delete project id {}, name: {} during cleanup.", projectId, projectName, e);
            }
            LOGGER.info("Handled test project id: {}, name: {}", projectId, projectName);
        }
    }
}
