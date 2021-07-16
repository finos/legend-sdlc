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

package org.finos.legend.sdlc.server.gitlab.auth;

import org.finos.legend.server.pac4j.gitlab.GitlabPersonalAccessTokenProfile;

import org.finos.legend.sdlc.server.gitlab.mode.GitLabMode;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.pac4j.core.profile.CommonProfile;

@Ignore
public class TestGitLabPersonalAccessTokenSession extends AbstractTestGitLabSession
{
    private static final GitlabPersonalAccessTokenProfile PROFILE = newProfile("unknownId", "unknownUser", "testUser", "test-gitlab.com");

    protected CommonProfile getProfile()
    {
        return PROFILE;
    }

    private static GitlabPersonalAccessTokenProfile newProfile(String token, String userId, String username, String gitlabHost)
    {
        return new GitlabPersonalAccessTokenProfile(token, userId, username, gitlabHost);
    }
}
