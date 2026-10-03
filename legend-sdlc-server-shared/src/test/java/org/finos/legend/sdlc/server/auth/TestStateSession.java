// Copyright 2026 Goldman Sachs
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

package org.finos.legend.sdlc.server.auth;

import org.junit.Assert;
import org.junit.Test;
import org.pac4j.core.profile.CommonProfile;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;

public class TestStateSession
{
    @Test
    public void testStateRoundTripsThroughCookie()
    {
        CommonProfile profile = newProfile("alice");
        StateSession session = StateSessionBuilder.newBuilder().withProfile(profile).build();
        session.putState("backend.token", "abc123");
        session.putState("backend.refresh", "r-456");

        String cookie = session.encode();
        StateSession decoded = StateSessionBuilder.newBuilder().fromToken(cookie).withProfile(profile).build();

        Assert.assertEquals("alice", decoded.getUserId());
        Assert.assertEquals(session.getCreationTime(), decoded.getCreationTime());
        Assert.assertEquals("abc123", decoded.getState().get("backend.token"));
        Assert.assertEquals("r-456", decoded.getState().get("backend.refresh"));
        Assert.assertEquals(2, decoded.getState().size());
    }

    @Test
    public void testEmptyStateRoundTrips()
    {
        CommonProfile profile = newProfile("bob");
        StateSession session = StateSessionBuilder.newBuilder().withProfile(profile).build();
        StateSession decoded = StateSessionBuilder.newBuilder().fromToken(session.encode()).withProfile(profile).build();
        Assert.assertEquals("bob", decoded.getUserId());
        Assert.assertEquals(Collections.emptyMap(), decoded.getState());
    }

    @Test
    public void testNullValueRemovesKey()
    {
        StateSession session = StateSessionBuilder.newBuilder().withProfile(newProfile("carol")).build();
        session.putState("k", "v");
        Assert.assertEquals("v", session.getState().get("k"));
        session.putState("k", null);
        Assert.assertFalse(session.getState().containsKey("k"));
    }

    @Test
    public void testLegacyFormatCookieDecodesToEmptyState()
    {
        // a pre-state cookie: base session fields followed by a backend-specific payload
        // (as the former GitLab session encoding wrote: a 0/1 presence flag, then token fields)
        Instant creationTime = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(60);
        Token.TokenBuilder legacy = Token.newBuilder();
        legacy.putString("dave");
        legacy.putLong(creationTime.getEpochSecond());
        legacy.putInt(1);
        legacy.putString("someAppId");
        legacy.putString("OAUTH2_ACCESS");
        legacy.putString("tok");
        legacy.putString("refresh");
        legacy.putString(null);

        StateSession decoded = StateSessionBuilder.newBuilder().fromToken(legacy.toTokenString()).withProfile(newProfile("dave")).build();
        Assert.assertEquals("dave", decoded.getUserId());
        Assert.assertEquals(creationTime, decoded.getCreationTime());
        Assert.assertEquals(Collections.emptyMap(), decoded.getState());
    }

    @Test
    public void testKerberosProfileYieldsKerberosStateSession()
    {
        CommonProfile profile = newProfile("eve");
        StateSession session = StateSessionBuilder.newBuilder().withProfile(profile).build();
        Assert.assertTrue(session instanceof CommonProfileStateSession);
        Assert.assertFalse(session instanceof KerberosStateSession);
    }

    private CommonProfile newProfile(String id)
    {
        CommonProfile profile = new CommonProfile();
        profile.setId(id);
        return profile;
    }
}
