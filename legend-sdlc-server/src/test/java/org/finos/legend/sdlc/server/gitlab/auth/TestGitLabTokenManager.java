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

import org.finos.legend.sdlc.backend.api.spi.BackendSessionStateStore;
import org.finos.legend.sdlc.server.gitlab.GitLabAppInfo;
import org.finos.legend.sdlc.server.gitlab.GitLabServerInfo;
import org.gitlab4j.api.Constants.TokenType;
import org.junit.Assert;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class TestGitLabTokenManager
{
    private static final GitLabAppInfo appInfo = GitLabAppInfo.newAppInfo(GitLabServerInfo.newServerInfo("https",
        "prod.host.name",
        null), "7891d9ee73e90ccb004fec490af74c5946cbaa1d73226eca81399546835fe28c", "abcdef", "http://some.url.com/uat");

    @Test
    public void testTokenRoundTripsThroughStateStore()
    {
        BackendSessionStateStore store = newStore();
        GitLabTokenManager tokenManager = GitLabTokenManager.newTokenManager(appInfo, store);
        GitLabToken token = GitLabToken.newGitLabToken(TokenType.OAUTH2_ACCESS,
            "6f220d4f523d89d832316b8a7052a57de97d863c2d2a6564694561ba1af88875");
        tokenManager.setGitLabToken(token);
        tokenManager.setRefreshToken("refresh-1");
        tokenManager.setTokenExpiry(LocalDateTime.now().plusHours(1));

        GitLabTokenManager reread = GitLabTokenManager.newTokenManager(appInfo, store);
        Assert.assertEquals(token, reread.getGitLabToken());
        Assert.assertEquals("refresh-1", reread.getRefreshToken());
        Assert.assertFalse(reread.shouldRefreshToken());
    }

    @Test
    public void testStateIsKeyedToTheApplication()
    {
        BackendSessionStateStore store = newStore();
        GitLabTokenManager tokenManager = GitLabTokenManager.newTokenManager(appInfo, store);
        tokenManager.setGitLabToken(GitLabToken.newGitLabToken(TokenType.OAUTH2_ACCESS, "someToken"));

        GitLabAppInfo otherAppInfo = GitLabAppInfo.newAppInfo(appInfo.getServerInfo(), "anotherAppId", "secret", "http://some.url.com/other");
        GitLabTokenManager otherAppManager = GitLabTokenManager.newTokenManager(otherAppInfo, store);
        Assert.assertNull(otherAppManager.getGitLabToken());
    }

    @Test
    public void testPrivateTokenIsNeverRefreshed()
    {
        GitLabTokenManager tokenManager = GitLabTokenManager.newTokenManager(appInfo, newStore());
        tokenManager.setGitLabToken(GitLabToken.newGitLabToken(TokenType.PRIVATE, "qQi7UzyxxxTtQbHhSq9"));
        Assert.assertFalse(tokenManager.shouldRefreshToken());
    }

    @Test
    public void testClear()
    {
        BackendSessionStateStore store = newStore();
        GitLabTokenManager tokenManager = GitLabTokenManager.newTokenManager(appInfo, store);
        GitLabToken token = GitLabToken.newGitLabToken(TokenType.OAUTH2_ACCESS,
            "6f220d4f523d89d832316b8a7052a57de97d863c2d2a6564694561ba1af88875");
        tokenManager.setGitLabToken(token);
        Assert.assertEquals(token, tokenManager.getGitLabToken());
        tokenManager.clearGitLabToken();
        Assert.assertNull(tokenManager.getGitLabToken());
        Assert.assertNull(GitLabTokenManager.newTokenManager(appInfo, store).getGitLabToken());
    }

    @Test
    public void testExpiry()
    {
        GitLabTokenManager tokenManager = GitLabTokenManager.newTokenManager(appInfo, newStore());
        tokenManager.setGitLabToken(GitLabToken.newGitLabToken(TokenType.OAUTH2_ACCESS, "someToken"));
        tokenManager.setTokenExpiry(LocalDateTime.now().minusSeconds(1));
        Assert.assertTrue(tokenManager.shouldRefreshToken());
    }

    @Test
    public void testExactExpiryFromTokenResponse()
    {
        GitLabTokenManager tokenManager = GitLabTokenManager.newTokenManager(appInfo, newStore());
        LocalDateTime expiry = LocalDateTime.now().plusHours(2);
        tokenManager.setTokenResponse(new GitLabTokenResponse(GitLabToken.newGitLabToken(TokenType.OAUTH2_ACCESS, "someToken"), "refresh", null, expiry));
        Assert.assertFalse(tokenManager.shouldRefreshToken());
        Assert.assertEquals("refresh", tokenManager.getRefreshToken());
    }

    private BackendSessionStateStore newStore()
    {
        Map<String, String> state = new HashMap<>();
        return new BackendSessionStateStore()
        {
            @Override
            public String get(String key)
            {
                return state.get(key);
            }

            @Override
            public void put(String key, String value)
            {
                if (value == null)
                {
                    state.remove(key);
                }
                else
                {
                    state.put(key, value);
                }
            }
        };
    }
}
