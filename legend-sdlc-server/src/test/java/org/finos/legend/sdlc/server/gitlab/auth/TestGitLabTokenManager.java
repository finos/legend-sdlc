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
import org.finos.legend.sdlc.server.gitlab.GitLabAppInfo;
import org.finos.legend.sdlc.server.gitlab.GitLabServerInfo;
import org.gitlab4j.api.Constants.TokenType;
import org.junit.Assert;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

public class TestGitLabTokenManager
{
    private static final Pattern TOKEN_PATTERN = Pattern.compile("^[-_a-zA-Z0-9]+$");
    private static final GitLabAppInfo appInfo = GitLabAppInfo.newAppInfo(GitLabServerInfo.newServerInfo("https",
        "prod.host.name",
        null), "7891d9ee73e90ccb004fec490af74c5946cbaa1d73226eca81399546835fe28c", "abcdef", "http://some.url.com/uat");

    @Test
    public void testEncoding_Empty()
    {
        GitLabTokenManager tokenManager = GitLabTokenManager.newTokenManager(appInfo);
        assertEncoding(tokenManager);
    }

    @Test
    public void testEncoding_OneOAuthToken()
    {
        GitLabTokenManager tokenManager = GitLabTokenManager.newTokenManager(appInfo);
        GitLabToken token = GitLabToken.newGitLabToken(TokenType.OAUTH2_ACCESS,
            "6f220d4f523d89d832316b8a7052a57de97d863c2d2a6564694561ba1af88875");
        tokenManager.setGitLabToken(token);
        assertEncoding(tokenManager);
    }

    @Test
    public void testEncoding_OneOAuthRefreshToken()
    {
        GitLabTokenManager tokenManager = GitLabTokenManager.newTokenManager(appInfo);
        String refreshToken = "6f220d4f523d89d832316b8a7052a57de97d863c2d2a6564694561ba1af88875";
        tokenManager.setRefreshToken(refreshToken);
        assertEncoding(tokenManager);
    }

    @Test
    public void testEncoding_OnePrivateAccessToken()
    {
        GitLabTokenManager tokenManager = GitLabTokenManager.newTokenManager(appInfo);
        GitLabToken token = GitLabToken.newGitLabToken(TokenType.PRIVATE, "qQi7UzyxxxTtQbHhSq9");
        tokenManager.setGitLabToken(token);
        assertEncoding(tokenManager);
    }

    @Test
    public void testClear()
    {
        GitLabTokenManager tokenManager = GitLabTokenManager.newTokenManager(appInfo);
        GitLabToken token = GitLabToken.newGitLabToken(TokenType.OAUTH2_ACCESS,
            "6f220d4f523d89d832316b8a7052a57de97d863c2d2a6564694561ba1af88875");
        tokenManager.setGitLabToken(token);
        Assert.assertEquals(token, tokenManager.getGitLabToken());
        tokenManager.clearGitLabToken();
        Assert.assertNull(tokenManager.getGitLabToken());
    }

    @Test
    public void testExpiry()
    {
        GitLabTokenManager tokenManager = GitLabTokenManager.newTokenManager(appInfo);
        tokenManager.setTokenExpiry(LocalDateTime.now().minusSeconds(1));
        Assert.assertTrue(tokenManager.shouldRefreshToken());
    }

    private void assertEncoding(GitLabTokenManager tokenManager)
    {
        Token.TokenBuilder builder = Token.newBuilder();
        tokenManager.encode(builder);
        String token = builder.toTokenString();
        Assert.assertTrue(TOKEN_PATTERN.matcher(token).matches());
        Assert.assertTrue(Base64.isBase64(token));

        GitLabTokenManager newTokenManager = GitLabTokenManager.newTokenManager(appInfo);
        newTokenManager.decodeAndSetToken(Token.newReader(token));
        Assert.assertEquals(tokenManager, newTokenManager);
    }
}
