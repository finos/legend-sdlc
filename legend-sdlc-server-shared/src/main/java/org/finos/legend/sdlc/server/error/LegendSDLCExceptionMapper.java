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

package org.finos.legend.sdlc.server.error;

import org.finos.legend.sdlc.error.LegendSDLCException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.Response.Status.Family;
import javax.ws.rs.ext.Provider;

/**
 * Maps {@link LegendSDLCException} (the framework-free base exception thrown by code below the server layer) to HTTP
 * responses, with the same semantics as {@link LegendSDLCServerExceptionMapper} has for the deprecated server
 * exception. That mapper remains registered and, being more specific, still handles the deprecated subclass.
 */
@Provider
public class LegendSDLCExceptionMapper extends BaseExceptionMapper<LegendSDLCException>
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LegendSDLCExceptionMapper.class);

    public LegendSDLCExceptionMapper(boolean includeStackTrace)
    {
        super(includeStackTrace);
    }

    public LegendSDLCExceptionMapper()
    {
        this(false);
    }

    @Override
    public Response toResponse(LegendSDLCException exception)
    {
        int statusCode = exception.getStatusCode();
        switch (Family.familyOf(statusCode))
        {
            case CLIENT_ERROR:
            {
                // we do not return a stack trace for client errors, regardless of the value of includeStackTrace
                return buildResponse(statusCode, ExtendedErrorMessage.fromLegendSDLCException(exception, false));
            }
            case SERVER_ERROR:
            {
                return buildResponse(statusCode, ExtendedErrorMessage.fromLegendSDLCException(exception, this.includeStackTrace));
            }
            case REDIRECTION:
            {
                // TODO consider cases other than 301, 302, 303, and 307
                URI redirectLocation = getRedirectLocation(exception);
                if (redirectLocation != null)
                {
                    return Response.status(statusCode)
                            .location(redirectLocation)
                            .build();
                }

                // Could not get a redirect location from an exception indicating there should be a redirect
                // Send back an internal server error instead
                LOGGER.warn("Could not get redirect URI from exception with status: {}", statusCode);
                return buildResponse(Status.INTERNAL_SERVER_ERROR, ExtendedErrorMessage.fromLegendSDLCException(exception, this.includeStackTrace));
            }
            default:
            {
                // Exception with non-error HTTP status
                // Send back an internal server error instead
                LOGGER.warn("Exception with non-error HTTP status: {}", statusCode);
                return buildResponse(Status.INTERNAL_SERVER_ERROR, ExtendedErrorMessage.fromLegendSDLCException(exception, this.includeStackTrace));
            }
        }
    }

    private static URI getRedirectLocation(LegendSDLCException exception)
    {
        String message = exception.getMessage();
        if (message == null)
        {
            LOGGER.warn("A LegendSDLCException with status {} should have the redirect location as its message, found null", exception.getStatusCode());
            return null;
        }

        try
        {
            return new URI(message);
        }
        catch (URISyntaxException e)
        {
            LOGGER.warn("Invalid redirect location for LegendSDLCException with status {}: {}", exception.getStatusCode(), message);
            return null;
        }
    }
}
