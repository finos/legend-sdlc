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

import java.util.Map;

/**
 * A session carrying a mutable string-keyed state bag, encoded into the session cookie alongside the identity.
 * This is the server-side vehicle for backend session state: the backend owns the keys and value encodings (it
 * sees the bag through its session state store port); the server persists the bag across requests without
 * knowing what the values mean. Not specific to any backend — the session itself only knows identity and state.
 */
public interface StateSession extends Session
{
    /**
     * Unmodifiable view of the session's state.
     *
     * @return session state
     */
    Map<String, String> getState();

    /**
     * Set the value for a state key. A null value removes the key.
     *
     * @param key   state key
     * @param value value or null to remove
     */
    void putState(String key, String value);
}
