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

import org.finos.legend.sdlc.server.resources.BaseResource;
import org.gitlab4j.api.models.Project;

public class AbstractGitLabApiTest extends BaseResource
{
    static final String TEST_LOGIN_USERNAME = TestUtils.getProperty("TEST_LOGIN_USERNAME");
    static final String TEST_LOGIN_PASSWORD = TestUtils.getProperty("TEST_LOGIN_PASSWORD");
    static final String TEST_HOST_URL = TestUtils.getProperty("TEST_HOST_URL");
    static final String TEST_PRIVATE_TOKEN = TestUtils.getProperty("TEST_PRIVATE_TOKEN");

    /**
     * Get the test Project instance for the calling test class.
     *
     * @return the test Project instance for the calling test class
     */
    protected static Project getTestProject()
    {
        return null;
    }
}
