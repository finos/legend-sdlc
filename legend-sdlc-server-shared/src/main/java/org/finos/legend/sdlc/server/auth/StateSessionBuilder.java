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

import org.finos.legend.server.pac4j.kerberos.KerberosProfile;
import org.pac4j.core.profile.CommonProfile;

import java.time.Instant;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Builder for {@link StateSession}s: identity from a pac4j profile, state decoded from a session cookie (empty
 * for cookies from before the state format, per {@link SessionStateCodec}). Which profile types create sessions
 * at all is the web filter's policy, not the builder's.
 */
public class StateSessionBuilder extends SessionBuilder<StateSession>
{
    private CommonProfile profile;
    private SortedMap<String, String> state = new TreeMap<>();

    private StateSessionBuilder()
    {
    }

    public CommonProfile getProfile()
    {
        return this.profile;
    }

    public StateSessionBuilder withProfile(CommonProfile profile)
    {
        this.profile = profile;
        if ((profile != null) && (getUserId() == null))
        {
            withUserId(profile.getId());
        }
        return this;
    }

    @Override
    public StateSessionBuilder withUserId(String userId)
    {
        super.withUserId(userId);
        return this;
    }

    @Override
    public StateSessionBuilder withCreationTime(Instant creationTime)
    {
        super.withCreationTime(creationTime);
        return this;
    }

    @Override
    public StateSessionBuilder fromToken(String tokenString)
    {
        super.fromToken(tokenString);
        return this;
    }

    @Override
    public StateSessionBuilder fromToken(Token.TokenReader reader)
    {
        super.fromToken(reader);
        this.state = SessionStateCodec.decode(reader);
        return this;
    }

    @Override
    public StateSessionBuilder reset()
    {
        super.reset();
        this.profile = null;
        this.state = new TreeMap<>();
        return this;
    }

    @Override
    public void validate()
    {
        super.validate();
        CommonProfile currentProfile = getProfile();
        if (currentProfile == null)
        {
            throw new IllegalStateException("profile may not be null");
        }
        if (!Objects.equals(currentProfile.getId(), getUserId()))
        {
            throw new IllegalStateException("User id (" + getUserId() + ") does not match profile: " + currentProfile);
        }
    }

    @Override
    protected StateSession newSession()
    {
        if (this.profile instanceof KerberosProfile)
        {
            return new KerberosStateSession((KerberosProfile) this.profile, getUserId(), getCreationTime(), this.state);
        }
        return new CommonProfileStateSession(this.profile, getUserId(), getCreationTime(), this.state);
    }

    public static StateSessionBuilder newBuilder()
    {
        return new StateSessionBuilder();
    }
}
