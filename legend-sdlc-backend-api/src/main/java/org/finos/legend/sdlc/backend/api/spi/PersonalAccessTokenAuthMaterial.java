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

import java.util.Objects;

/**
 * A personal access token the user authenticated to the host with, offered to backends as data via
 * {@link BackendSessionContext#getService}. A backend whose upstream system is the token's host can use the
 * token directly; it decides that by inspecting {@link #getHost()}.
 */
public class PersonalAccessTokenAuthMaterial
{
    private final String host;
    private final String token;

    private PersonalAccessTokenAuthMaterial(String host, String token)
    {
        this.host = host;
        this.token = Objects.requireNonNull(token, "token may not be null");
    }

    /**
     * The host the token belongs to. May be null if the host does not know it.
     *
     * @return token host or null
     */
    public String getHost()
    {
        return this.host;
    }

    /**
     * The token value. Never null.
     *
     * @return token
     */
    public String getToken()
    {
        return this.token;
    }

    public static PersonalAccessTokenAuthMaterial newPersonalAccessTokenAuthMaterial(String host, String token)
    {
        return new PersonalAccessTokenAuthMaterial(host, token);
    }
}
