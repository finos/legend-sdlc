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

import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Token encoding of a {@link StateSession}'s state bag: a format marker, the entry count, then sorted key/value
 * pairs. The marker distinguishes the state format from pre-state session cookies (whose next field after the
 * base session fields was backend-specific); a cookie without the marker decodes to an empty state bag, so
 * sessions from before the format change simply re-acquire their state.
 */
class SessionStateCodec
{
    private static final int STATE_FORMAT_MARKER = 0x53544154; // "STAT"

    private SessionStateCodec()
    {
    }

    static Token.TokenBuilder encode(SortedMap<String, String> state, Token.TokenBuilder builder)
    {
        builder.putInt(STATE_FORMAT_MARKER);
        builder.putInt(state.size());
        state.forEach((key, value) ->
        {
            builder.putString(key);
            builder.putString(value);
        });
        return builder;
    }

    static SortedMap<String, String> decode(Token.TokenReader reader)
    {
        TreeMap<String, String> state = new TreeMap<>();
        if (!reader.hasRemaining())
        {
            return state;
        }
        if (reader.getInt() != STATE_FORMAT_MARKER)
        {
            // legacy (pre-state) cookie: whatever follows is a previous format's payload; start with empty state
            return state;
        }
        for (int count = reader.getInt(); count > 0; count--)
        {
            String key = reader.getString();
            String value = reader.getString();
            if (key != null)
            {
                state.put(key, value);
            }
        }
        return state;
    }

    static Map<String, String> unmodifiableView(SortedMap<String, String> state)
    {
        return Collections.unmodifiableSortedMap(state);
    }
}
