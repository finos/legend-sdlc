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

package org.finos.legend.sdlc.server.auth;

import org.pac4j.core.profile.CommonProfile;

import java.time.Instant;
import java.util.Objects;

public abstract class CommonProfileSessionBuilder<P extends CommonProfile, T extends CommonProfileSession<P>> extends SessionBuilder<T>
{
    private P profile;

    public P getProfile()
    {
        return this.profile;
    }

    public CommonProfileSessionBuilder<P, T> withProfile(P profile)
    {
        this.profile = profile;
        if ((profile != null) && (getUserId() == null))
        {
            withUserId(profile.getId());
        }
        return this;
    }

    @Override
    public CommonProfileSessionBuilder<P, T> withUserId(String userId)
    {
        super.withUserId(userId);
        return this;
    }

    @Override
    public CommonProfileSessionBuilder<P, T> withCreationTime(Instant creationTime)
    {
        super.withCreationTime(creationTime);
        return this;
    }

    @Override
    public CommonProfileSessionBuilder<P, T> fromToken(String tokenString)
    {
        super.fromToken(tokenString);
        return this;
    }

    @Override
    public CommonProfileSessionBuilder<P, T> fromToken(Token.TokenReader reader)
    {
        super.fromToken(reader);
        return this;
    }

    @Override
    public CommonProfileSessionBuilder<P, T> reset()
    {
        super.reset();
        this.profile = null;
        return this;
    }

    @Override
    public void validate()
    {
        super.validate();
        P p = getProfile();
        if (p == null)
        {
            throw new IllegalStateException("profile may not be null");
        }
        if (!Objects.equals(p.getId(), getUserId()))
        {
            throw new IllegalStateException("User id (" + getUserId() + ") does not match profile: " + p);
        }
    }
}
