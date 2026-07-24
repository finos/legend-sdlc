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

import org.finos.legend.sdlc.error.LegendSDLCException;

import java.net.URI;
import java.util.Objects;

/**
 * Thrown by a backend when an operation requires the user to complete an interactive authorization flow (e.g.
 * an OAuth authorization) before it can proceed. Carries the URI at which the user can authorize.
 * <p>
 * The status code is 403; the host decides how to surface the flow (the server redirects with 302 where a
 * redirect is allowed, and otherwise returns 403 with the authorization URI in the body). Auth protocols stay in
 * the host; this exception is how the need for interaction crosses the SPI as data.
 */
public class AuthorizationRequiredException extends LegendSDLCException
{
    private static final long serialVersionUID = 1L;

    private final URI authorizationUri;

    public AuthorizationRequiredException(URI authorizationUri)
    {
        super("Authorization required: " + Objects.requireNonNull(authorizationUri, "authorizationUri may not be null"), 403);
        this.authorizationUri = authorizationUri;
    }

    public URI getAuthorizationUri()
    {
        return this.authorizationUri;
    }
}
