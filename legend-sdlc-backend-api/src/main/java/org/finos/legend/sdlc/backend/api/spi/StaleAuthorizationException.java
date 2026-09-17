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

/**
 * Thrown by a backend when it discovered mid-operation that the user's authorization material was stale (e.g.
 * an upstream system rejected a stored access token) and has discarded it; retrying the same request is expected
 * to acquire fresh authorization or surface an {@link AuthorizationRequiredException}. The companion of
 * {@link AuthorizationRequiredException} for the retry flow: its redirect target is the request itself, which
 * only the host knows, so it crosses the SPI as a type rather than a URI. The status code is 503 (retry later);
 * the server maps idempotent requests to a redirect back to the same request instead.
 */
public class StaleAuthorizationException extends LegendSDLCException
{
    private static final long serialVersionUID = 1L;

    public StaleAuthorizationException(String message, Throwable cause)
    {
        super(message, 503, cause);
    }
}
