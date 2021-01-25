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

import org.eclipse.collections.api.factory.Lists;
import org.finos.legend.sdlc.domain.model.project.Project;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.server.auth.LegendSDLCWebFilter;
import org.finos.legend.sdlc.server.auth.Session;
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
//    private static WorkspaceApi workspaceApi;
//    private static GitLabApi oauthGitLabApi;

    @BeforeClass
    public static void setup() throws GitLabApiException
    {
        //        workspaceApi = new GitLabWorkspaceApi(null, null, null);
    }

//    @Test
//    public void testCreateNewWorkspace()
//    {
//        String workspaceId = "DunderMifflin";
//        String projectId = "UAT-802";
//        Workspace workspace = execute(
//                "getting workspace " + workspaceId + " for project " + projectId,
//                "create new workspace",
//                workspaceApi::newWorkspace,
//                projectId,
//                workspaceId
//        );
//    }

    @Test
    public void testCreateProject() throws GitLabApiException { // TODO: remove exception to catch block

        String projectName = "Test Project";
        String description = "A test project.";
        ProjectType projectType = ProjectType.PROTOTYPE;
        String groupId = "testGroup";
        String artifactId = "testproj";
        Iterable<String> tags = Lists.mutable.empty();

        HttpServletRequest httpServletRequest = new TestHttpServletRequest();

//        GitLabSessionBuilder.newBuilder(MODE_INFOS).withProfile(getProfile()).build();
//        Session session = new GitLabSessionBuilder().withText("test text").withNumber(333888777444222L).withUserId("slothrop").build();

        Session testGitLabSession = new TestGitLabSession();

        GitLabApi oauthGitLabApi = GitLabApi.oauth2Login(TEST_HOST_URL, TEST_LOGIN_USERNAME, TEST_LOGIN_PASSWORD, null, null, true);
        assertNotNull(oauthGitLabApi);
        Version version = oauthGitLabApi.getVersion();

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
