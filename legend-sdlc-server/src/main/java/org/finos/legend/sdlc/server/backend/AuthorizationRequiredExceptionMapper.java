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

package org.finos.legend.sdlc.server.backend;

import org.finos.legend.sdlc.backend.api.spi.AuthorizationRequiredException;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.error.LegendSDLCServerExceptionMapper;
import org.finos.legend.sdlc.server.resources.auth.AuthResource;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * Maps a backend's {@link AuthorizationRequiredException} to 403 with the historical
 * {@code {"message":"Authorization required","auth_uri":"/auth/authorize"}} body — the client is directed to the
 * generic authorization endpoint, where redirects are allowed and {@link AuthResource} converts the same
 * exception into a 302 to the backend's authorization URI. More specific than the generic
 * {@code LegendSDLCException} mapper, so it wins selection; delegating to the server exception mapper keeps the
 * body shape identical to what the pre-extraction GitLab code produced.
 */
@Provider
public class AuthorizationRequiredExceptionMapper implements ExceptionMapper<AuthorizationRequiredException>
{
    public static final String AUTHORIZATION_REQUIRED_BODY = "{\"message\":\"Authorization required\",\"auth_uri\":\"/auth/authorize\"}";

    private final LegendSDLCServerExceptionMapper delegate = new LegendSDLCServerExceptionMapper();

    @Override
    public Response toResponse(AuthorizationRequiredException exception)
    {
        return this.delegate.toResponse(new LegendSDLCServerException(AUTHORIZATION_REQUIRED_BODY, Status.FORBIDDEN, exception));
    }
}
