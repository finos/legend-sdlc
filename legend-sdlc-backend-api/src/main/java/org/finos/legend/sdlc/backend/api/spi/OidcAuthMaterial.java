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

import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * OpenID Connect token material from the user's authentication to the host, offered to backends as data via
 * {@link BackendSessionContext#getService}. A backend whose upstream system is also the deployment's OIDC issuer
 * (e.g. a GitLab that is both the storage backend and the identity provider) can use the access token directly
 * instead of running its own authorization flow; it decides that by inspecting {@link #getIssuer()} and
 * {@link #getScopes()}.
 */
public class OidcAuthMaterial
{
    private final String issuer;
    private final String accessToken;
    private final Set<String> scopes;
    private final String refreshToken;
    private final Instant expiration;

    private OidcAuthMaterial(String issuer, String accessToken, Set<String> scopes, String refreshToken, Instant expiration)
    {
        this.issuer = issuer;
        this.accessToken = Objects.requireNonNull(accessToken, "accessToken may not be null");
        this.scopes = (scopes == null) ? Collections.emptySet() : Collections.unmodifiableSet(scopes);
        this.refreshToken = refreshToken;
        this.expiration = expiration;
    }

    /**
     * The token's issuer. May be null if the host does not know it.
     *
     * @return issuer or null
     */
    public String getIssuer()
    {
        return this.issuer;
    }

    /**
     * The access token value. Never null.
     *
     * @return access token
     */
    public String getAccessToken()
    {
        return this.accessToken;
    }

    /**
     * The scopes granted to the access token. Never null; possibly empty (which may mean the host does not know
     * the scopes).
     *
     * @return granted scopes
     */
    public Set<String> getScopes()
    {
        return this.scopes;
    }

    /**
     * The refresh token, if the host has one.
     *
     * @return refresh token or null
     */
    public String getRefreshToken()
    {
        return this.refreshToken;
    }

    /**
     * When the access token expires, if the host knows.
     *
     * @return expiration or null
     */
    public Instant getExpiration()
    {
        return this.expiration;
    }

    public static OidcAuthMaterial newOidcAuthMaterial(String issuer, String accessToken, Set<String> scopes, String refreshToken, Instant expiration)
    {
        return new OidcAuthMaterial(issuer, accessToken, scopes, refreshToken, expiration);
    }
}
