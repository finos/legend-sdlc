// Copyright 2020 Goldman Sachs
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

import io.dropwizard.jersey.errors.ErrorMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class MetadataExceptionMapper implements ExceptionMapper<MetadataException>
{
    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataExceptionMapper.class);

    @Override
    public Response toResponse(MetadataException exception)
    {
        Status status = exception.getStatus();
        ResponseBuilder builder = Response.status(status);
        switch (status.getFamily())
        {
            case CLIENT_ERROR:
            case SERVER_ERROR:
            {
                builder.entity(buildErrorMessage(exception))
                        .type(MediaType.APPLICATION_JSON);
                break;
            }
            case REDIRECTION:
            {
                // TODO consider cases other than 301, 302, 303, and 307
                URI redirectLocation = getRedirectLocation(exception);
                if (redirectLocation != null)
                {
                    builder.location(redirectLocation);
                }
                else
                {
                    // Could not get a redirect location from an exception indicating there should be a redirect
                    // Send back an internal server error instead
                    builder.status(Status.INTERNAL_SERVER_ERROR)
                            .entity(buildErrorMessage(exception))
                            .type(MediaType.APPLICATION_JSON);
                }
                break;
            }
            default:
            {
                // Exception with non-error HTTP status
                // Send back an internal server error instead
                LOGGER.warn("Exception with non-error HTTP status: {}", status);
                builder.status(Status.INTERNAL_SERVER_ERROR)
                        .entity(buildErrorMessage(exception))
                        .type(MediaType.APPLICATION_JSON);
                break;
            }
        }
        return builder.build();
    }

    private ErrorMessage buildErrorMessage(MetadataException exception)
    {
        return new ErrorMessage(exception.getStatus().getStatusCode(), getMessage(exception));
    }

    private String getMessage(MetadataException exception)
    {
        String message = exception.getMessage();
        if (message == null)
        {
            for (Throwable cause = exception.getCause(); (message == null) && (cause != null); cause = cause.getCause())
            {
                message = cause.getMessage();
            }
        }
        return message;
    }

    private URI getRedirectLocation(MetadataException exception)
    {
        String message = exception.getMessage();
        if (message == null)
        {
            LOGGER.warn("A MetadataException with status {} should have the redirect location as its message, found null", exception.getStatus());
            return null;
        }

        try
        {
            return new URI(message);
        }
        catch (URISyntaxException e)
        {
            LOGGER.warn("Invalid redirect location for MetadataException with status {}: {}", exception.getStatus(), message);
            return null;
        }
    }
}
