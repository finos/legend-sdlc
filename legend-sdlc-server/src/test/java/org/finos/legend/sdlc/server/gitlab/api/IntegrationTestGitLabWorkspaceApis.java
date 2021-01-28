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
import org.gitlab4j.api.GitLabApiException;
import org.junit.BeforeClass;
import org.junit.Test;

public class IntegrationTestGitLabWorkspaceApis extends AbstractGitLabApiTest
{
    private static GitLabWorkspaceApi gitLabWorkspaceApi;

    @BeforeClass
    public static void setup() throws GitLabApiException
    {
        JerseyGuiceUtils.install((s, serviceLocator) -> null); // TODO: temp solution to handle undeclared dependency
        prepareGitLabUser();
        setUpWorkspaceApi();
    }

    @Test
    public void testCreateWorkspace()
    {

    }

    /**
     * Authenticates with OAuth2 and instantiate the test SDLC GitLabWorkspaceApi.
     */
    private static void setUpWorkspaceApi()
    {

    }
}
