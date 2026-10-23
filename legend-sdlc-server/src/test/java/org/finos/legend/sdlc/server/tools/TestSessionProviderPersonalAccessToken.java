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

package org.finos.legend.sdlc.server.tools;

import org.finos.legend.sdlc.server.auth.Session;
import org.junit.Assert;
import org.junit.Test;
import org.pac4j.core.context.CallContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.credentials.Credentials;
import org.pac4j.core.credentials.TokenCredentials;
import org.pac4j.core.credentials.authenticator.Authenticator;
import org.pac4j.core.credentials.extractor.CredentialsExtractor;
import org.pac4j.core.profile.creator.ProfileCreator;

import java.util.Optional;

/**
 * Fake collaborators rather than a live GitLab: the real GitLab authenticator and profile creator
 * only reject by throwing, never with the empty {@code Optional} pac4j's interfaces allow.
 */
public class TestSessionProviderPersonalAccessToken
{
    private static final Credentials CREDENTIALS = new TokenCredentials("a-token");

    private static final CredentialsExtractor EXTRACTS_A_TOKEN = context -> Optional.of(CREDENTIALS);

    private static final Authenticator UNREACHABLE_AUTHENTICATOR = (context, credentials) ->
    {
        throw new AssertionError("Authenticator must not be reached when nothing was extracted");
    };

    private static final ProfileCreator UNREACHABLE_PROFILE_CREATOR = (context, credentials) ->
    {
        throw new AssertionError("Profile creator must not be reached for rejected credentials");
    };

    @Test
    public void testRejectsWhenAuthenticatorReturnsEmpty()
    {
        Assert.assertNull(
                "An authenticator that rejects by returning an empty Optional must not yield a session",
                invoke(EXTRACTS_A_TOKEN, (context, credentials) -> Optional.empty(), UNREACHABLE_PROFILE_CREATOR));
    }

    @Test
    public void testRejectsWhenAuthenticatorThrows()
    {
        Assert.assertNull(
                "An authenticator that rejects by throwing must not yield a session",
                invoke(EXTRACTS_A_TOKEN,
                        (context, credentials) ->
                        {
                            throw new RuntimeException("invalid token");
                        },
                        UNREACHABLE_PROFILE_CREATOR));
    }

    @Test
    public void testRejectsWhenNoTokenIsPresent()
    {
        Assert.assertNull(
                "A request with no token must not yield a session",
                invoke(context -> Optional.empty(), UNREACHABLE_AUTHENTICATOR, UNREACHABLE_PROFILE_CREATOR));
    }

    @Test
    public void testRejectsWhenProfileCreatorReturnsEmpty()
    {
        Assert.assertNull(
                "A profile creator that declines must not yield a session",
                invoke(EXTRACTS_A_TOKEN,
                        (context, credentials) -> Optional.of(CREDENTIALS),
                        (context, credentials) -> Optional.empty()));
    }

    private static Session invoke(CredentialsExtractor extractor, Authenticator authenticator, ProfileCreator creator)
    {
        CallContext callContext = new CallContext(null, (SessionStore) null);
        return SessionProvider.getSessionUsingGitlabPersonalAccessToken(callContext, extractor, authenticator, creator);
    }
}
