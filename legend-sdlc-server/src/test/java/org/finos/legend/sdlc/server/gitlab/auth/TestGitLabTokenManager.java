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
import org.finos.legend.sdlc.server.auth.Token;
import org.finos.legend.sdlc.server.gitlab.mode.GitLabMode;
import org.finos.legend.sdlc.server.gitlab.mode.GitLabModeInfo;
import org.finos.legend.sdlc.server.gitlab.mode.GitLabModeInfos;
import org.gitlab4j.api.Constants.TokenType;
import org.junit.Assert;
import org.junit.Test;

import java.util.regex.Pattern;

public class TestGitLabTokenManager
{
    private static final Pattern TOKEN_PATTERN = Pattern.compile("^[-_a-zA-Z0-9]+$");
    private static final GitLabModeInfos MODE_INFOS = GitLabModeInfos.fromInfos(
            GitLabModeInfo.newModeInfo(GitLabMode.UAT, "https", "prod.host.name", null, "7891d9ee73e90ccb004fec490bf74c5946cbaa1d73226eca81399546835fe28c", "abcdef", "http://some.url.com/uat"),
            GitLabModeInfo.newModeInfo(GitLabMode.PROD, "https", "uat.host.name", null, "9de0524aa018f079ad594d11548294f3c9133fcc782321fcc1675956a265fd17", "fedcba", "http://some.url.com/prod"));

    @Test
    public void testEncoding_Empty()
    {
        GitLabTokenManager tokenManager = GitLabTokenManager.newTokenManager(MODE_INFOS);
        assertEncoding(tokenManager);
    }

    @Test
    public void testEncoding_OneOAuthToken()
    {
        GitLabTokenManager tokenManager = GitLabTokenManager.newTokenManager(MODE_INFOS);
        GitLabToken token = GitLabToken.newGitLabToken(TokenType.OAUTH2_ACCESS,"6f220d4f523d89d832316b8a7052a57de97d863c2d2a6564694561ba1af88875");
        tokenManager.putGitLabToken(GitLabMode.UAT, token);
        assertEncoding(tokenManager);
    }

    @Test
    public void testEncoding_OnePrivateAccessToken()
    {
        GitLabTokenManager tokenManager = GitLabTokenManager.newTokenManager(MODE_INFOS);
        GitLabToken token = GitLabToken.newGitLabToken(TokenType.PRIVATE,"qQi7UzyxxxTtQbHhSq9");
        tokenManager.putGitLabToken(GitLabMode.UAT, token);
        assertEncoding(tokenManager);
    }

    @Test
    public void testEncoding_TwoOAuthTokens()
    {
        GitLabTokenManager tokenManager = GitLabTokenManager.newTokenManager(MODE_INFOS);

        GitLabToken firstToken = GitLabToken.newGitLabToken(TokenType.OAUTH2_ACCESS,"6f220d4f523d89d832316b8a7052a57de97d863c2d2a6564694561ba1af88875");
        tokenManager.putGitLabToken(GitLabMode.UAT, firstToken);

        GitLabToken secondToken = GitLabToken.newGitLabToken(TokenType.OAUTH2_ACCESS,"fd223a30b565240bcb98c9db3a27c57ab3e500348ea0ba568cd374b56ddc496a");
        tokenManager.putGitLabToken(GitLabMode.PROD, secondToken);
        assertEncoding(tokenManager);
    }

    @Test
    public void testEncoding_TwoPrivateAccessTokens()
    {
        GitLabTokenManager tokenManager = GitLabTokenManager.newTokenManager(MODE_INFOS);

        GitLabToken firstToken = GitLabToken.newGitLabToken(TokenType.PRIVATE,"qQi7UzyxxxTtQbHhSq9");
        tokenManager.putGitLabToken(GitLabMode.UAT, firstToken);

        GitLabToken secondToken = GitLabToken.newGitLabToken(TokenType.PRIVATE,"zCret1-ZHonvSHQsy95s");
        tokenManager.putGitLabToken(GitLabMode.PROD, secondToken);
        assertEncoding(tokenManager);
    }

    @Test
    public void testEncoding_TwoDiffAccessTokens()
    {
        GitLabTokenManager tokenManager = GitLabTokenManager.newTokenManager(MODE_INFOS);

        GitLabToken oAuthToken = GitLabToken.newGitLabToken(TokenType.OAUTH2_ACCESS,"6f220d4f523d89d832316b8a7052a57de97d863c2d2a6564694561ba1af88875");
        tokenManager.putGitLabToken(GitLabMode.UAT, oAuthToken);

        GitLabToken privateToken = GitLabToken.newGitLabToken(TokenType.PRIVATE,"zCret1-ZHonvxxxy95s");
        tokenManager.putGitLabToken(GitLabMode.PROD, privateToken);
        assertEncoding(tokenManager);
    }

    @Test
    public void testClear()
    {
        GitLabTokenManager tokenManager = GitLabTokenManager.newTokenManager(MODE_INFOS);
        GitLabToken uatToken = GitLabToken.newGitLabToken(TokenType.OAUTH2_ACCESS,"6f220d4f523d89d832316b8a7052a57de97d863c2d2a6564694561ba1af88875");
        GitLabToken prodToken = GitLabToken.newGitLabToken(TokenType.OAUTH2_ACCESS,"fd223a30a565240bcb98c9db3a27c57ab3e500348ea0ea568cd374b56ddc496a");
        tokenManager.putGitLabToken(GitLabMode.UAT, uatToken);
        tokenManager.putGitLabToken(GitLabMode.PROD, prodToken);
        Assert.assertEquals(uatToken, tokenManager.getGitLabToken(GitLabMode.UAT));
        Assert.assertEquals(prodToken, tokenManager.getGitLabToken(GitLabMode.PROD));
        tokenManager.clearGitLabTokens();
        Assert.assertNull(tokenManager.getGitLabToken(GitLabMode.UAT));
        Assert.assertNull(tokenManager.getGitLabToken(GitLabMode.PROD));
    }

    private void assertEncoding(GitLabTokenManager tokenManager)
    {
        Token.TokenBuilder builder = Token.newBuilder();
        tokenManager.encode(builder);
        String token = builder.toTokenString();
        Assert.assertTrue(TOKEN_PATTERN.matcher(token).matches());
        Assert.assertTrue(Base64.isBase64(token));

        GitLabTokenManager newTokenManager = GitLabTokenManager.newTokenManager(MODE_INFOS);
        newTokenManager.putAllFromToken(Token.newReader(token));
        Assert.assertEquals(tokenManager, newTokenManager);
    }
}
