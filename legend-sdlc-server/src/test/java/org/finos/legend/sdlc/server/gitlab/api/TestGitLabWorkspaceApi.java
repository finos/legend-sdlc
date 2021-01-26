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

import com.squarespace.jersey2.guice.JerseyGuiceUtils;
import org.eclipse.collections.api.factory.Lists;
import org.finos.legend.sdlc.domain.model.project.Project;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.server.auth.LegendSDLCWebFilter;
import org.finos.legend.sdlc.server.auth.Session;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.gitlab.auth.TestGitLabSession;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Version;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;

import static org.junit.Assert.assertNotNull;

public class TestGitLabWorkspaceApi extends AbstractGitLabApiTest
{
    @BeforeClass
    public static void setup() throws GitLabApiException
    {
        JerseyGuiceUtils.install((s, serviceLocator) -> null); // TODO: temp solution to handle undeclared dependency
        prepareGitLabUser();
    }

    @Test
    public void testCreateProject() throws LegendSDLCServerException {

        String projectName = "Test Project";
        String description = "A test project.";
        ProjectType projectType = ProjectType.PROTOTYPE;
        String groupId = "testGroup";
        String artifactId = "testproj";
        Iterable<String> tags = Lists.mutable.empty();

        HttpServletRequest httpServletRequest = new TestHttpServletRequest();

        Session testGitLabSession = new TestGitLabSession();
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

        ((TestGitLabSession) testGitLabSession).setAccessToken(oauthToken);
        LegendSDLCWebFilter.setSessionAttributeOnServletRequest(httpServletRequest, testGitLabSession);
        GitLabUserContext gitLabUserContext = new GitLabUserContext(httpServletRequest, null);
        GitLabProjectApi gitLabProjectApi = new GitLabProjectApi(null, gitLabUserContext, null, null, null, null); // TODO: change back

        Project createdProject = gitLabProjectApi.createProject(projectName, description, projectType, groupId, artifactId, tags);
    }
}
