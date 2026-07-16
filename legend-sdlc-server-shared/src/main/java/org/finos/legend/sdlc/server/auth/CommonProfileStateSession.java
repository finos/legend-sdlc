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

import org.pac4j.core.profile.CommonProfile;

import java.time.Instant;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The standard {@link StateSession} over a pac4j profile.
 */
public class CommonProfileStateSession extends BaseCommonProfileSession<CommonProfile> implements StateSession
{
    private static final long serialVersionUID = 1L;

    private final SortedMap<String, String> state;

    protected CommonProfileStateSession(CommonProfile profile, String userId, Instant creationTime, SortedMap<String, String> state)
    {
        super(profile, userId, creationTime);
        this.state = (state == null) ? new TreeMap<>() : state;
    }

    @Override
    public Map<String, String> getState()
    {
        return SessionStateCodec.unmodifiableView(this.state);
    }

    @Override
    public void putState(String key, String value)
    {
        if (value == null)
        {
            this.state.remove(key);
        }
        else
        {
            this.state.put(key, value);
        }
    }

    @Override
    public Token.TokenBuilder encode(Token.TokenBuilder builder)
    {
        return SessionStateCodec.encode(this.state, super.encode(builder));
    }

    @Override
    protected void writeToStringInfo(StringBuilder builder)
    {
        super.writeToStringInfo(builder);
        builder.append(" stateKeys=").append(this.state.keySet());
    }
}
