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


    @Test
    protected void testAllSupportedTokenTypes()
    {
        GitLabSession session = newSession();

        //private token
        GitLabToken privateAccessToken = GitLabToken.newPrivateAccessToken(PROFILE.getPersonalAccessToken());
        Assert.assertEquals("Private access token should be added to GitLabUser Session",  privateAccessToken, session.getGitLabToken(GitLabMode.PROD));

        session.clearGitLabTokens();

        //oauth toke
        GitLabToken oauthToken = GitLabToken.newOAuthToken("6f220d4f523d89d832316b8a7052a57de97d863c2d2a6564694561ba1af88875");
        session.putGitLabToken(GitLabMode.PROD, oauthToken);
        Assert.assertTrue("OAuth token shouldn't be allowed in GitLabUser Session", session.getGitLabToken(GitLabMode.PROD) == null);
    }
}
