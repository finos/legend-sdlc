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
import org.finos.legend.sdlc.server.auth.LegendSDLCWebFilter;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.GitLabAppInfo;
import org.finos.legend.sdlc.server.gitlab.GitLabServerInfo;
import org.finos.legend.sdlc.server.gitlab.api.GitLabProjectApi;
import org.finos.legend.sdlc.server.gitlab.api.TestHttpServletRequest;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabOAuthAuthenticator;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.gitlab.auth.TestGitLabSession;
import org.finos.legend.sdlc.server.gitlab.mode.GitLabMode;
import org.finos.legend.sdlc.server.gitlab.mode.GitLabModeInfo;
import org.finos.legend.sdlc.server.tools.BackgroundTaskProcessor;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.util.List;

/**
 * This test suite is run against the actual GitLab server with testing account setup.
 */
public class AbstractGitLabServerApiTest
{
    // Note: Password for Admin is preset for Maven to start the test container for testing purposes only.
    // Admin and test user(s) will only exist for the container's lifetime.

//    static final String TEST_ADMIN_USERNAME = "root";
//    static final String TEST_ADMIN_PASSWORD = "ac0018BD19066353";
    static final String TEST_OWNER_USERNAME = "";
    static final String TEST_OWNER_PASSWORD = "";

    static final String TEST_ACCESS_TOKEN = "";

    static final String TEST_MEMBER_USERNAME = "";
    static final String TEST_MEMBER_PASSWORD = "";
    static final String TEST_HOST_SCHEME = "https";
    static final String TEST_HOST_HOST = "gitlab.com"; // next.gitlab.com
    static final Integer TEST_HOST_PORT = null;
    static final String TEST_HOST_URL = "https://gitlab.com";

    static final BackgroundTaskProcessor backgroundTaskProcessor = new BackgroundTaskProcessor(1);

    static final Logger LOGGER = LoggerFactory.getLogger(AbstractGitLabServerApiTest.class);

    @BeforeClass
    public static void suiteSetup()
    {
        JerseyGuiceUtils.install((s, serviceLocator) -> null);
//        prepareGitLabUser();
    }

    @AfterClass
    public static void shutDown()
    {
        LOGGER.info("Shutting down backgroundTaskProcessor.");
        backgroundTaskProcessor.shutdown();
        LOGGER.info("Shut down backgroundTaskProcessor.");
    }

//    /**
//     * Create the proper users for authenticating the GitLab operations.
//     */
//    protected static void prepareGitLabUser() throws LegendSDLCServerException
//    {
//        try
//        {
//            GitLabApi rootGitLabApi = GitLabApi.oauth2Login(TEST_HOST_URL, TEST_ADMIN_USERNAME, TEST_ADMIN_PASSWORD, null, null, true);
//            Optional<User> testUser = rootGitLabApi.getUserApi().getOptionalUser(TEST_OWNER_USERNAME);
//            if (!testUser.isPresent())
//            {
//                User userSettings = new User()
//                        .withUsername(TEST_OWNER_USERNAME)
//                        .withEmail(TEST_OWNER_USERNAME + "@testUser.org")
//                        .withName("Owner User")
//                        .withSkipConfirmation(true)
//                        .withIsAdmin(true);
//                rootGitLabApi.getUserApi().createUser(userSettings, TEST_OWNER_PASSWORD, false);
//                LOGGER.info("Created user with name {} and username {}", userSettings.getName(), userSettings.getUsername());
//            }
//            Optional<User> testMember = rootGitLabApi.getUserApi().getOptionalUser(TEST_MEMBER_USERNAME);
//            if (!testMember.isPresent())
//            {
//                User userSettings = new User()
//                        .withUsername(TEST_MEMBER_USERNAME)
//                        .withEmail(TEST_MEMBER_PASSWORD + "@testUser.org")
//                        .withName("Member User")
//                        .withSkipConfirmation(true)
//                        .withIsAdmin(true);
//                rootGitLabApi.getUserApi().createUser(userSettings, TEST_MEMBER_PASSWORD, false);
//                LOGGER.info("Created user with name {} and username {}", userSettings.getName(), userSettings.getUsername());
//            }
//        }
//        catch (GitLabApiException exception)
//        {
//            String errorMsg = exception.getMessage();
//            if (exception.hasValidationErrors())
//            {
//                errorMsg = "Validation error: " + exception.getValidationErrors().toString();
//            }
//            throw new LegendSDLCServerException("Cannot create proper user for authentication: " + errorMsg);
//        }
//    }

    /**
     * Authenticates to GitLab and creates a test GitLabUserContext for a project owner.
     *
     * @return A test GitLabUserContext for a project owner.
     * @throws LegendSDLCServerException if cannot authenticate to GitLab via OAuth.
     */
    protected static GitLabUserContext prepareGitLabOwnerUserContext() throws LegendSDLCServerException
    {
        return prepareGitLabUserContextHelper(TEST_OWNER_USERNAME, TEST_OWNER_PASSWORD, TEST_ACCESS_TOKEN);
    }

    /**
     * Authenticates to GitLab and creates a test GitLabUserContext for a project member.
     *
     * @return A test GitLabUserContext for a project member.
     * @throws LegendSDLCServerException if cannot authenticate to GitLab via OAuth.
     */
    protected static GitLabUserContext prepareGitLabMemberUserContext() throws LegendSDLCServerException
    {
        return prepareGitLabUserContextHelper(TEST_MEMBER_USERNAME, TEST_MEMBER_PASSWORD, TEST_ACCESS_TOKEN);
    }

    /**
     * Authenticates to GitLab and creates a test GitLabUserContext.
     * @param username the name of user for whom we create this context.
     * @param password the password of user for whom we create this context.
     */
    private static GitLabUserContext prepareGitLabUserContextHelper(String username, String password, String token) throws LegendSDLCServerException
    {
        GitLabMode gitLabMode = GitLabMode.PROD;
        HttpServletRequest httpServletRequest = new TestHttpServletRequest();

        TestGitLabSession session = new TestGitLabSession(username);
        String oauthToken;

        GitLabServerInfo gitLabServerInfo = GitLabServerInfo.newServerInfo(TEST_HOST_SCHEME, TEST_HOST_HOST, TEST_HOST_PORT);
        // TODO: 127.0.0.1:7075/api/pac4j/login/callback
        GitLabAppInfo gitLabAppInfo = GitLabAppInfo.newAppInfo(gitLabServerInfo, "4404bb50f01295b8646a7f7435cbf338732d3910ec40c033ef97047de55086db", "424b02c52d21acf2b8c622b27cb7a1e48e5e93e17d03d0098bc4b4614169cd8d", "https://127.0.0.1:7075/api/auth/callback"); // "https://127.0.0.1:7075/api/auth/callback" "https://127.0.0.1:7075/api/pac4j/login/callback"
        GitLabModeInfo gitLabModeInfo = GitLabModeInfo.newModeInfo(gitLabMode, gitLabAppInfo);

        session.setModeInfo(gitLabModeInfo);

        try
        {
//            oauthToken = "66b080cebaefda106a330138e1daabe54925fd45afdbf220de3d9527a1946320";
            oauthToken = GitLabOAuthAuthenticator.newAuthenticator(session.getModeInfo(gitLabMode)).getOAuthTokenForTestSession(username, password); //"66b080cebaefda106a330138e1daabe54925fd45afdbf220de3d9527a1946320";
            System.out.println("oauth token: " + oauthToken);
        }
        catch (Exception e)
        {
            System.out.println("exception caught.");
            throw new LegendSDLCServerException("Cannot instantiate GitLab via OAuth: " + e.getMessage(), e);
        }
        LOGGER.info("Retrieved access token: {}", oauthToken);
        session.setAccessToken(oauthToken);

        LegendSDLCWebFilter.setSessionAttributeOnServletRequest(httpServletRequest, session);
        return new GitLabUserContext(httpServletRequest, null);
    }

    /**
     * Clean up all potential test projects.
     */
    protected static void cleanUpTestProjects(GitLabProjectApi gitLabProjectApi)
    {
        List<Project> projectsToBeCleaned = gitLabProjectApi.getProjects(true, "", Lists.mutable.empty(), Lists.mutable.empty());
        for (Project project : projectsToBeCleaned)
        {
            System.out.println("project id: " + project.getProjectId());
            System.out.println("project name: " + project.getName());
            gitLabProjectApi.deleteProject(project.getProjectId());
        }
    }

    /**
     * A helper method to generate random strings of hex chars of length 16.
     * @return The generated random string.
     */
    private static String generateRandomHexCharString()
    {
        SecureRandom secureRandom = new SecureRandom();
        return String.format("%016x", secureRandom.nextLong());
    }
}
