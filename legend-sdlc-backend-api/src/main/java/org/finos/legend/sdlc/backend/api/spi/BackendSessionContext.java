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
 * What the host hands {@link Backend#newSession} on behalf of an authenticated user. Authentication protocols
 * (who the user is, how they proved it) are entirely the host's concern; the result crosses the SPI here as
 * data — an identity plus a {@link BackendSessionStateStore state store} for whatever per-user state the
 * backend needs to keep between requests.
 */
public interface BackendSessionContext
{
    /**
     * The authenticated user id. Null if the host has no authenticated user (e.g. anonymous or single-user
     * deployments); backends that require an identity should treat null according to their own contract.
     *
     * @return user id or null
     */
    String getUserId();

    /**
     * Per-user state persisted by the host across requests.
     *
     * @return state store
     */
    BackendSessionStateStore getStateStore();

    /**
     * A typed lookup for per-user auth material the host can offer beyond the identity and the state store —
     * the per-user counterpart of {@link BackendEnvironment#getService}. What is available depends on how the
     * user authenticated to the host; the server publishes {@link javax.security.auth.Subject} for
     * Kerberos-authenticated users, {@link OidcAuthMaterial} for OIDC-authenticated users, and
     * {@link PersonalAccessTokenAuthMaterial} where a personal access token identified the user. Everything
     * offered here is data or a JDK type — the host's authentication framework types never cross the SPI.
     * Default: nothing available.
     *
     * @param serviceType service type
     * @param <T>         service type
     * @return service of the requested type, or null if the host offers none
     */
    default <T> T getService(Class<T> serviceType)
    {
        return null;
    }
}
