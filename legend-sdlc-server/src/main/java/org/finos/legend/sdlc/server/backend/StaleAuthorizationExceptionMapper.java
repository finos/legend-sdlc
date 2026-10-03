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

import org.finos.legend.sdlc.backend.api.spi.StaleAuthorizationException;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.error.LegendSDLCServerExceptionMapper;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;

/**
 * Maps a backend's {@link StaleAuthorizationException} — stale authorization material was discarded and the
 * request should be retried — to the historical retry pair: a 302 back to the same request for GET, and 503
 * "please retry" otherwise. The redirect target is the current request, which is host knowledge; that is why the
 * condition crosses the SPI as a type rather than a URI.
 * <p>
 * Bound in Guice (not registered directly with Jersey) so the request is reachable through a lazy provider;
 * direct {@code @Context} field injection of request-scoped types fails at servlet initialization under the
 * Guice-HK2 bridge.
 */
@javax.ws.rs.ext.Provider
public class StaleAuthorizationExceptionMapper implements ExceptionMapper<StaleAuthorizationException>
{
    private final LegendSDLCServerExceptionMapper delegate = new LegendSDLCServerExceptionMapper();

    private final Provider<HttpServletRequest> httpRequestProvider;

    @Inject
    public StaleAuthorizationExceptionMapper(Provider<HttpServletRequest> httpRequestProvider)
    {
        this.httpRequestProvider = httpRequestProvider;
    }

    @Override
    public Response toResponse(StaleAuthorizationException exception)
    {
        HttpServletRequest httpRequest = this.httpRequestProvider.get();
        StringBuffer urlBuilder = httpRequest.getRequestURL();
        String queryString = httpRequest.getQueryString();
        if (queryString != null)
        {
            urlBuilder.append('?').append(queryString);
        }
        if ("GET".equalsIgnoreCase(httpRequest.getMethod()))
        {
            return this.delegate.toResponse(new LegendSDLCServerException(urlBuilder.toString(), Status.FOUND, exception));
        }
        return this.delegate.toResponse(new LegendSDLCServerException("Please retry request: " + httpRequest.getMethod() + " " + urlBuilder, Status.SERVICE_UNAVAILABLE, exception));
    }
}
