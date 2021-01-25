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

import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.gitlab.mode.GitLabMode;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Project;
import org.junit.Test;

import java.util.List;

public class TestDockerGitLabApi
{
    @Test
    public void testDockerCreateProject() throws GitLabApiException
    {
        // replace with context with dummy request.
        GitLabUserContext gitLabUserContext = new GitLabUserContext(null, null);
        GitLabApi gitLabApi = gitLabUserContext.getGitLabAPI(GitLabMode.UAT); // same step as in endpoints
        List<Project> projects = gitLabApi.getProjectApi().getProjects();

        // GitLabApi gitLabApi = new GitLabApi("http://your.gitlab.server.com", "YOUR_PERSONAL_ACCESS_TOKEN");
    }
}
