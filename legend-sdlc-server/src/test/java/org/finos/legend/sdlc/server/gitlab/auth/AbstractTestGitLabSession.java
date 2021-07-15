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
import org.finos.legend.sdlc.server.gitlab.mode.GitLabMode;
import org.finos.legend.sdlc.server.gitlab.mode.GitLabModeInfo;
import org.finos.legend.sdlc.server.gitlab.mode.GitLabModeInfos;
import org.gitlab4j.api.Constants.TokenType;
import org.junit.Assert;
import org.junit.Test;
import org.pac4j.core.profile.CommonProfile;

import java.util.regex.Pattern;

public abstract class AbstractTestGitLabSession
{
    private static final Pattern TOKEN_PATTERN = Pattern.compile("^[-_a-zA-Z0-9]+$");
    private static final GitLabModeInfos MODE_INFOS = GitLabModeInfos.fromInfos(
            GitLabModeInfo.newModeInfo(GitLabMode.UAT, "https", "prod.host.name", null, "7891d9ee73e90ccb004fec490af74c5946cbaa1d73226eca81399546835fe28c", "abcdef", "http://some.url.com/uat"),
            GitLabModeInfo.newModeInfo(GitLabMode.PROD, "https", "uat.host.name", null, "9de0524aa018f079ad594d11548294f3b9133fcc782321fcc1675956a265fd17", "fedcba", "http://some.url.com/prod"));

    @Test
    public void testEncoding_Empty()
    {
        GitLabSession session = newSession();
        assertEncoding(session);
    }

    @Test
    public void testEncoding_OneOAuthToken()
    {
        GitLabSession session = newSession();
        GitLabToken uatToken = GitLabToken.newGitLabToken(TokenType.OAUTH2_ACCESS,"6f220d4f523d89d832316b8a7052a57ce97d863c2d2a6564694561ba1af88875");
        session.putGitLabToken(GitLabMode.UAT, uatToken);
        assertEncoding(session);
    }

    @Test
    public void testEncoding_OnePrivateAccessToken()
    {
        GitLabSession session = newSession();
        GitLabToken uatToken = GitLabToken.newGitLabToken(TokenType.PRIVATE,"qQi7UzyxxxTtQbHhSq9");
        session.putGitLabToken(GitLabMode.UAT, uatToken);
        assertEncoding(session);
    }

    @Test
    public void testEncoding_TwoOAuthTokens()
    {
        GitLabSession session = newSession();
        GitLabToken uatToken = GitLabToken.newGitLabToken(TokenType.OAUTH2_ACCESS,"6f220d4f523d89d832316b8a7052a57ce97d863c2d2a6564694561ba1af88875");
        GitLabToken prodToken = GitLabToken.newGitLabToken(TokenType.OAUTH2_ACCESS,"fd223a30a565240bcb98c9db3a27c57ab3e500348ea0ba568cd374b56ddc496a");

        session.putGitLabToken(GitLabMode.UAT, uatToken);
        session.putGitLabToken(GitLabMode.PROD, prodToken);
        assertEncoding(session);
    }

    @Test
    public void testEncoding_TwoPrivateAccessTokens()
    {
        GitLabSession session = newSession();
        GitLabToken uatToken = GitLabToken.newGitLabToken(TokenType.PRIVATE,"qQi7UzyxxxTtQbHhSq9");
        GitLabToken prodToken = GitLabToken.newGitLabToken(TokenType.PRIVATE,"zCret1-ZHonvSHQsy95s");

        session.putGitLabToken(GitLabMode.UAT, uatToken);
        session.putGitLabToken(GitLabMode.PROD, prodToken);
        assertEncoding(session);
    }

    @Test
    public void testEncoding_TwoDiffAccessTokens()
    {
        GitLabSession session = newSession();
        GitLabToken uatToken = GitLabToken.newGitLabToken(TokenType.PRIVATE,"qQi7UzyxxxTtQbHhSq9");
        GitLabToken prodToken = GitLabToken.newGitLabToken(TokenType.OAUTH2_ACCESS,"6f220d4f523d89d832316b8a7052a57de97d863c2d2a6564694561ba1af88875");

        session.putGitLabToken(GitLabMode.UAT, uatToken);
        session.putGitLabToken(GitLabMode.PROD, prodToken);
        assertEncoding(session);
    }

    @Test
    public void testEncoding_TwoDiffTokens_Delay()
    {
        GitLabSession session = newSession();
        GitLabToken uatToken = GitLabToken.newGitLabToken(TokenType.PRIVATE,"qQi7UzyxxxTtQbHhSq9");
        GitLabToken prodToken = GitLabToken.newGitLabToken(TokenType.OAUTH2_ACCESS,"fd223a30a565240bcb98c9db3a27c57ab3e500348ea0ba568cd374b56ddc496a");

        session.putGitLabToken(GitLabMode.UAT, uatToken);
        session.putGitLabToken(GitLabMode.PROD, prodToken);
        assertEncoding(session, 1001L);
    }

    @Test
    public void testClear()
    {
        GitLabSession session = newSession();
        GitLabToken uatToken = GitLabToken.newGitLabToken(TokenType.OAUTH2_ACCESS,"6f220d4f523d89d832316b8a7052a57ce97d863c2d2a6564694561ba1af88875");
        GitLabToken prodToken = GitLabToken.newGitLabToken(TokenType.OAUTH2_ACCESS,"fd223a30a565240bcb98c9db3a27c57ab3e500348ea0ba568cd374b56ddc496a");
        session.putGitLabToken(GitLabMode.UAT, uatToken);
        session.putGitLabToken(GitLabMode.PROD, prodToken);
        Assert.assertEquals(uatToken, session.getGitLabToken(GitLabMode.UAT));
        Assert.assertEquals(prodToken, session.getGitLabToken(GitLabMode.PROD));
        session.clearGitLabTokens();
        Assert.assertNull(session.getGitLabToken(GitLabMode.UAT));
        Assert.assertNull(session.getGitLabToken(GitLabMode.PROD));
    }

    protected abstract CommonProfile getProfile();

    private GitLabSession newSession()
    {
        return GitLabSessionBuilder.newBuilder(MODE_INFOS).withProfile(getProfile()).build();
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
        Assert.assertEquals(session, GitLabSessionBuilder.newBuilder(MODE_INFOS).withProfile(getProfile()).fromToken(token).build());
    }
}
