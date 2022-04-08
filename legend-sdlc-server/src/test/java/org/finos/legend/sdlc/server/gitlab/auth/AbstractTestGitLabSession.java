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

import org.apache.commons.codec.binary.Base64;
import org.finos.legend.sdlc.server.gitlab.GitLabAppInfo;
import org.finos.legend.sdlc.server.gitlab.GitLabServerInfo;
import org.gitlab4j.api.Constants.TokenType;
import org.junit.Assert;
import org.junit.Test;
import org.pac4j.core.profile.CommonProfile;

import java.util.regex.Pattern;

public abstract class AbstractTestGitLabSession
{
    private static final Pattern TOKEN_PATTERN = Pattern.compile("^[-_a-zA-Z0-9]+$");
    private static final GitLabAppInfo appInfo = GitLabAppInfo.newAppInfo(GitLabServerInfo.newServerInfo("https", "prod.host.name", null), "7891d9ee73e90ccb004fec490af74c5946cbaa1d73226eca81399546835fe28c", "abcdef", "http://some.url.com/uat");

    @Test
    public void testEncoding_Empty()
    {
        GitLabSession session = newSession();
        assertEncoding(session);
    }

    @Test
    public void testEncoding_OAuthToken()
    {
        GitLabSession session = newSession();
        session.setGitLabToken(GitLabToken.newGitLabToken(TokenType.OAUTH2_ACCESS, "6f220d4f523d89d832316b8a7052a57ce97d863c2d2a6564694561ba1af88875"));
        assertEncoding(session);
    }

    @Test
    public void testEncoding_PrivateAccessToken()
    {
        GitLabSession session = newSession();
        session.setGitLabToken(GitLabToken.newGitLabToken(TokenType.PRIVATE, "qQi7UzyxxxTtQbHhSq9"));
        assertEncoding(session);
    }

    @Test
    public void testEncoding_Token_WithDelay()
    {
        GitLabSession session = newSession();
        session.setGitLabToken(GitLabToken.newGitLabToken(TokenType.PRIVATE, "qQi7UzyxxxTtQbHhSq9"));
        // NOTE: here the delay must be greater than 1 second
        // This test is to make sure we actually get the timestamp from the token
        // Since when we create the session, if we don't pass in the timestamp, it will use the current timestamp down to the second
        // so if we don't put a delay, we can't be sure if the timestamp is actually extracted from the token or not.
        assertEncoding(session, 1001L);
    }

    @Test
    public void testClearGitLabToken()
    {
        GitLabSession session = newSession();
        session.setGitLabToken(GitLabToken.newGitLabToken(TokenType.OAUTH2_ACCESS, "6f220d4f523d89d832316b8a7052a57ce97d863c2d2a6564694561ba1af88875"));
        session.clearGitLabToken();
        Assert.assertNull(session.getGitLabToken());
    }

    protected abstract CommonProfile getProfile();

    private GitLabSession newSession()
    {
        return GitLabSessionBuilder.newBuilder(appInfo).withProfile(getProfile()).build();
    }

    private void assertEncoding(GitLabSession session)
    {
        assertEncoding(session, 0L);
    }

    private void assertEncoding(GitLabSession session, long delayInMillisBeforeDecoding)
    {
        String token = session.encode();
        Assert.assertTrue(TOKEN_PATTERN.matcher(token).matches());
        Assert.assertTrue(Base64.isBase64(token));
        if (delayInMillisBeforeDecoding > 0)
        {
            try
            {
                Thread.sleep(delayInMillisBeforeDecoding);
            }
            catch (InterruptedException e)
            {
                throw new RuntimeException("Delay interrupted", e);
            }
        }
        Assert.assertEquals(session, GitLabSessionBuilder.newBuilder(appInfo).withProfile(getProfile()).fromToken(token).build());
    }
}
