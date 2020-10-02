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

public class BaseCommonProfileSession<P extends CommonProfile> extends BaseSession implements CommonProfileSession<P>
{
    private final P profile;

    protected BaseCommonProfileSession(P profile, String userId, Instant creationTime)
    {
        super(userId, creationTime);
        this.profile = Objects.requireNonNull(profile, "profile may not be null");
    }

    @Override
    public P getProfile()
    {
        return this.profile;
    }

    @Override
    public boolean isValid()
    {
        return !this.profile.isExpired();
    }

    @Override
    protected void writeToStringInfo(StringBuilder builder)
    {
        builder.append(" profile=").append(this.profile);
    }
}
