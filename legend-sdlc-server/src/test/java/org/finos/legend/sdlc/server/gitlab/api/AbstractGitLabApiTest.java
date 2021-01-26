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

import org.finos.legend.sdlc.domain.model.project.Project;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.resources.BaseResource;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.User;

import java.util.Optional;

public class AbstractGitLabApiTest extends BaseResource
{
    static final String TEST_LOGIN_USERNAME = "Oski";
    static final String TEST_LOGIN_PASSWORD = "FiatLux";
    static final String TEST_HOST_URL = "http://localhost:8090";

    /**
     * Get the test Legend-SDLC Project instance for the calling test class.
     *
     * @return the test Project instance for the calling test class
     */
    protected static Project getTestProject()
    {
        return null;
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
            GitLabApi gitLabApi = GitLabApi.oauth2Login(TEST_HOST_URL, adminUserName, adminPassWord, null, null, true);
            Optional<User> optionalUser = gitLabApi.getUserApi().getOptionalUser(TEST_LOGIN_USERNAME);
            if (!optionalUser.isPresent())
            {
                User userSettings = new User()
                        .withUsername(TEST_LOGIN_USERNAME)
                        .withEmail(TEST_LOGIN_USERNAME + "@testUser.org")
                        .withName("Oski Bear")
                        .withSkipConfirmation(true)
                        .withIsAdmin(true);
                gitLabApi.getUserApi().createUser(userSettings, TEST_LOGIN_PASSWORD, false);
                System.out.format("Created %s user (%s)%n", userSettings.getName(), userSettings.getUsername());
            }
        }
        catch (GitLabApiException exception)
        {
            exception.printStackTrace();
            throw new LegendSDLCServerException("Cannot create proper user for authentication: " + exception.getMessage());
        }
    }
}
