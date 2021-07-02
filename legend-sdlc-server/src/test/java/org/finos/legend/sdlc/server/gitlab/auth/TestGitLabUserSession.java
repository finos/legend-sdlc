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

import org.finos.legend.sdlc.server.gitlab.mode.GitLabMode;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.profile.jwt.AbstractJwtProfile;

@Ignore
public class TestGitLabUserSession extends AbstractTestGitLabSession
{
    private static final AbstractJwtProfile PROFILE = newProfile("unknownId");

    protected CommonProfile getProfile()
    {
        return PROFILE;
    }

    //TODO: add profile implementation for GitLabUserProfile
    private static AbstractJwtProfile newProfile(String id)
    {
        return null;
    }


    @Test
    protected void testAllSupportedTokenTypes()
    {
        GitLabMode mode = GitLabMode.UAT;
        GitLabSession session = createSessionWithToken(mode, GitLabToken.newOAuthToken("6f220d4f523d89d832316b8a7052a57de97d863c2d2a6564694561ba1af88875"));
        Assert.assertTrue("OAuth token shouldn't be allowed in GitLabUser Session", session.getGitLabToken(mode) == null);

        GitLabToken privateAccessToken = GitLabToken.newPrivateAccessToken("qQi7UzyxxxTtQbHhSq9");
        session = createSessionWithToken(mode, privateAccessToken);
        Assert.assertEquals("Private access token should be added to GitLabUser Session",  privateAccessToken, session.getGitLabToken(mode));
    }
}
