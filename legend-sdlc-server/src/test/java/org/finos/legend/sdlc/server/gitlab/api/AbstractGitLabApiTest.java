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

import com.googlecode.junittoolbox.SuiteClasses;
import com.googlecode.junittoolbox.WildcardPatternSuite;
import org.finos.legend.sdlc.server.auth.LegendSDLCWebFilter;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.GitLabAppInfo;
import org.finos.legend.sdlc.server.gitlab.GitLabServerInfo;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.gitlab.auth.TestGitLabSession;
import org.finos.legend.sdlc.server.gitlab.mode.GitLabMode;
import org.finos.legend.sdlc.server.gitlab.mode.GitLabModeInfo;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.User;
import org.gitlab4j.api.models.Version;
import org.junit.BeforeClass;
import org.junit.experimental.categories.Categories;
import org.junit.runner.RunWith;

import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

import static org.junit.Assert.assertNotNull;

/**
 * Prepares subclass GitLab integration tests.
 * Only run before the GitLabIntegrationTest group during integration-test phase in-between docker start and stop.
 * Skipped during Junit tests.
 */
@RunWith(WildcardPatternSuite.class)
@SuiteClasses({"**/IntegrationTestGitLab*.class"})
@Categories.IncludeCategory(GitLabIntegrationTest.class)
public class AbstractGitLabApiTest
{
    static final String TEST_LOGIN_USERNAME = "Oski";
    static final String TEST_LOGIN_PASSWORD = "FiatLux19";
    static final String TEST_HOST_SCHEME = "http";
    static final String TEST_HOST_HOST = "localhost";
    static final Integer TEST_HOST_PORT = 8090;
    static final String TEST_HOST_URL = TEST_HOST_SCHEME + "://" + TEST_HOST_HOST + ":" + TEST_HOST_PORT;

    @BeforeClass
    public static void suiteSetup()
    {
        prepareGitLabUser();;
    }

    /**
     * Create the proper user for authenticating the GitLab operations.
     */
    protected static void prepareGitLabUser() throws LegendSDLCServerException
    {
        String adminUserName = "root";
        String adminPassWord = "password";
        try
        {
            GitLabApi rootGitLabApi = GitLabApi.oauth2Login(TEST_HOST_URL, adminUserName, adminPassWord, null, null, true);
            Optional<User> testUser = rootGitLabApi.getUserApi().getOptionalUser(TEST_LOGIN_USERNAME);
            if (!testUser.isPresent())
            {
                User userSettings = new User()
                        .withUsername(TEST_LOGIN_USERNAME)
                        .withEmail(TEST_LOGIN_USERNAME + "@testUser.org")
                        .withName("Oski Bear")
                        .withSkipConfirmation(true)
                        .withIsAdmin(true);
                rootGitLabApi.getUserApi().createUser(userSettings, TEST_LOGIN_PASSWORD, false);
                System.out.format("Created %s user (%s)%n", userSettings.getName(), userSettings.getUsername());
            }
        }
        catch (GitLabApiException exception)
        {
            exception.printStackTrace();
            System.out.println(exception.getValidationErrors().toString());
            throw new LegendSDLCServerException("Cannot create proper user for authentication: " + exception.getMessage());
        }
    }

    /**
     * Authenticates to GitLab and creates a test GitLabUserContext.
     *
     * @return A test GitLabUserContext.
     * @throws LegendSDLCServerException if cannot authenticate to GitLab via OAuth.
     */
    protected static GitLabUserContext prepareGitLabUserContext() throws LegendSDLCServerException
    {
        GitLabMode gitLabMode = GitLabMode.UAT;
        HttpServletRequest httpServletRequest = new TestHttpServletRequest();

        TestGitLabSession session = new TestGitLabSession(TEST_LOGIN_USERNAME);
        GitLabApi oauthGitLabApi;
        Version version;

        try
        {
            oauthGitLabApi = GitLabApi.oauth2Login(TEST_HOST_URL, TEST_LOGIN_USERNAME, TEST_LOGIN_PASSWORD, null, null, true);
            assertNotNull(oauthGitLabApi);
            version = oauthGitLabApi.getVersion();
        }
        catch (GitLabApiException exception)
        {
            throw new LegendSDLCServerException("Cannot instantiate GitLab via OAuth: " + exception.getMessage());
        }

        String oauthToken = oauthGitLabApi.getAuthToken();
        System.out.println("ACCESS_TOKEN: " + oauthToken);
        assertNotNull(version);

        GitLabServerInfo gitLabServerInfo = GitLabServerInfo.newServerInfo(TEST_HOST_SCHEME, TEST_HOST_HOST, TEST_HOST_PORT);
        GitLabAppInfo gitLabAppInfo = GitLabAppInfo.newAppInfo(gitLabServerInfo, null, null, null);
        GitLabModeInfo gitLabModeInfo = GitLabModeInfo.newModeInfo(gitLabMode, gitLabAppInfo);

        session.setAccessToken(oauthToken);
        session.setModeInfo(gitLabModeInfo);
        LegendSDLCWebFilter.setSessionAttributeOnServletRequest(httpServletRequest, session);
        return new GitLabUserContext(httpServletRequest, null);
    }
}
