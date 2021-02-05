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

import com.squarespace.jersey2.guice.JerseyGuiceUtils;
import org.finos.legend.sdlc.server.auth.LegendSDLCWebFilter;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.GitLabAppInfo;
import org.finos.legend.sdlc.server.gitlab.GitLabServerInfo;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.gitlab.auth.TestGitLabSession;
import org.finos.legend.sdlc.server.gitlab.mode.GitLabMode;
import org.finos.legend.sdlc.server.gitlab.mode.GitLabModeInfo;
import org.finos.legend.sdlc.server.tools.BackgroundTaskProcessor;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.User;
import org.gitlab4j.api.models.Version;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.util.Optional;

import static org.junit.Assert.assertNotNull;

/**
 * Prepares subclass GitLab integration tests.
 * Only run before the GitLab integration tests during integration-test phase in-between docker start and stop.
 * Skipped during Junit tests.
 */
public class AbstractGitLabApiTest
{
    // Note: Password for Admin is preset for Maven to start the test container for testing purposes only.
    // Admin and test user(s) will only exist for the container's lifetime.
    static final String TEST_ADMIN_USERNAME = "root";
    static final String TEST_ADMIN_PASSWORD = "ac0018BD19066353";
    static final String TEST_OWNER_USERNAME = "OwnerUser";
    static final String TEST_OWNER_PASSWORD = generateRandomHexCharString();
    static final String TEST_MEMBER_USERNAME = "MemberUser";
    static final String TEST_MEMBER_PASSWORD = generateRandomHexCharString();
    static final String TEST_HOST_SCHEME = "http";
    static final String TEST_HOST_HOST = "localhost";
    static final Integer TEST_HOST_PORT = 8090;
    static final String TEST_HOST_URL = TEST_HOST_SCHEME + "://" + TEST_HOST_HOST + ":" + TEST_HOST_PORT;

    static final BackgroundTaskProcessor backgroundTaskProcessor = new BackgroundTaskProcessor(1);

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractGitLabApiTest.class);

    @BeforeClass
    public static void suiteSetup()
    {
        JerseyGuiceUtils.install((s, serviceLocator) -> null);
        prepareGitLabUser();
    }

    @AfterClass
    public static void shutDown()
    {
        LOGGER.info("Shutting down backgroundTaskProcessor.");
        backgroundTaskProcessor.shutdown();
        LOGGER.info("Shut down backgroundTaskProcessor.");
    }

    /**
     * Create the proper users for authenticating the GitLab operations.
     */
    protected static void prepareGitLabUser() throws LegendSDLCServerException
    {
        try
        {
            GitLabApi rootGitLabApi = GitLabApi.oauth2Login(TEST_HOST_URL, TEST_ADMIN_USERNAME, TEST_ADMIN_PASSWORD, null, null, true);
            Optional<User> testUser = rootGitLabApi.getUserApi().getOptionalUser(TEST_OWNER_USERNAME);
            if (!testUser.isPresent())
            {
                User userSettings = new User()
                        .withUsername(TEST_OWNER_USERNAME)
                        .withEmail(TEST_OWNER_USERNAME + "@testUser.org")
                        .withName("Owner User")
                        .withSkipConfirmation(true)
                        .withIsAdmin(true);
                rootGitLabApi.getUserApi().createUser(userSettings, TEST_OWNER_PASSWORD, false);
                LOGGER.info("Created user with name {} and username {}", userSettings.getName(), userSettings.getUsername());
            }
            Optional<User> testMember = rootGitLabApi.getUserApi().getOptionalUser(TEST_MEMBER_USERNAME);
            if (!testMember.isPresent())
            {
                User userSettings = new User()
                        .withUsername(TEST_MEMBER_USERNAME)
                        .withEmail(TEST_MEMBER_PASSWORD + "@testUser.org")
                        .withName("Member User")
                        .withSkipConfirmation(true)
                        .withIsAdmin(true);
                rootGitLabApi.getUserApi().createUser(userSettings, TEST_MEMBER_PASSWORD, false);
                LOGGER.info("Created user with name {} and username {}", userSettings.getName(), userSettings.getUsername());
            }
        }
        catch (GitLabApiException exception)
        {
            String errorMsg = exception.getMessage();
            if (exception.hasValidationErrors())
            {
                errorMsg = "Validation error: " + exception.getValidationErrors().toString();
            }
            throw new LegendSDLCServerException("Cannot create proper user for authentication: " + errorMsg);
        }
    }

    /**
     * Authenticates to GitLab and creates a test GitLabUserContext for a project owner.
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
     * @param username the name of user for whom we create this context.
     * @param password the password of user for whom we create this context.
     */
    private static GitLabUserContext prepareGitLabUserContextHelper(String username, String password) throws LegendSDLCServerException
    {
        GitLabMode gitLabMode = GitLabMode.UAT;
        HttpServletRequest httpServletRequest = new TestHttpServletRequest();

        TestGitLabSession session = new TestGitLabSession(username);
        GitLabApi oauthGitLabApi;
        Version version;

        try
        {
            oauthGitLabApi = GitLabApi.oauth2Login(TEST_HOST_URL, username, password, null, null, true);
            assertNotNull(oauthGitLabApi);
            version = oauthGitLabApi.getVersion();
        }
        catch (GitLabApiException exception)
        {
            throw new LegendSDLCServerException("Cannot instantiate GitLab via OAuth: " + exception.getMessage());
        }

        String oauthToken = oauthGitLabApi.getAuthToken();
        LOGGER.info("Retrieved access token: {}", oauthToken);
        assertNotNull(version);

        GitLabServerInfo gitLabServerInfo = GitLabServerInfo.newServerInfo(TEST_HOST_SCHEME, TEST_HOST_HOST, TEST_HOST_PORT);
        GitLabAppInfo gitLabAppInfo = GitLabAppInfo.newAppInfo(gitLabServerInfo, null, null, null);
        GitLabModeInfo gitLabModeInfo = GitLabModeInfo.newModeInfo(gitLabMode, gitLabAppInfo);

        session.setAccessToken(oauthToken);
        session.setModeInfo(gitLabModeInfo);
        LegendSDLCWebFilter.setSessionAttributeOnServletRequest(httpServletRequest, session);
        return new GitLabUserContext(httpServletRequest, null);
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
