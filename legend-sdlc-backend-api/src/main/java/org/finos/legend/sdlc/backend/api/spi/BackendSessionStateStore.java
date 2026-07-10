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

package org.finos.legend.sdlc.backend.api.spi;

/**
 * Per-user state a backend may persist across requests — for example, OAuth access and refresh tokens. The host
 * supplies the implementation (the server backs it with its session store and cookie write-back); the backend
 * owns the keys and value encodings it uses. Values are strings so the host can persist them without knowing
 * their meaning.
 * <p>
 * Implementations need not be thread-safe; a store instance belongs to one {@link BackendSessionContext}.
 */
public interface BackendSessionStateStore
{
    /**
     * Get the value for a key. Returns null if the key is absent.
     *
     * @param key state key
     * @return value or null
     */
    String get(String key);

    /**
     * Set the value for a key, persisting it for future sessions of the same user. A null value removes the key.
     *
     * @param key   state key
     * @param value value or null to remove
     */
    void put(String key, String value);
}
