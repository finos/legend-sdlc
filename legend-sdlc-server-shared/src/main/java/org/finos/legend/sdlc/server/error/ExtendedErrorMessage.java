// Copyright 2021 Goldman Sachs
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.jersey.errors.ErrorMessage;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

class ExtendedErrorMessage extends ErrorMessage
{
    private final Instant timestamp;
    private final String stackTrace;

    private ExtendedErrorMessage(int code, String message, String details, String stackTrace, Instant timestamp)
    {
        super(code, message, details);
        this.timestamp = timestamp;
        this.stackTrace = stackTrace;
    }

    @JsonProperty("timestamp")
    public Instant getTimestamp()
    {
        return this.timestamp;
    }

    @JsonProperty("stackTrace")
    public String getStackTrace()
    {
        return this.stackTrace;
    }

    @JsonCreator
    static ExtendedErrorMessage newExtendedErrorMessage(@JsonProperty("code") int code,
                                                        @JsonProperty("message") String message,
                                                        @JsonProperty("details") String details,
                                                        @JsonProperty("stackTrace") String stackTrace,
                                                        @JsonProperty("timestamp") Instant timestamp)
    {
        return new ExtendedErrorMessage(code, message, details, stackTrace, timestamp);
    }

    static ExtendedErrorMessage fromThrowable(Throwable t, boolean includeStackTrace)
    {
        if (t instanceof LegendSDLCServerException)
        {
            return fromLegendSDLCServerException((LegendSDLCServerException) t, includeStackTrace);
        }
        if (t instanceof WebApplicationException)
        {
            return fromWebApplicationException((WebApplicationException) t, includeStackTrace);
        }
        return fromThrowable(t, getDefaultStatusCode(), null, null, includeStackTrace);
    }

    static ExtendedErrorMessage fromLegendSDLCServerException(LegendSDLCServerException e, boolean includeStackTrace)
    {
        return fromThrowable(e, e.getStatus().getStatusCode(), null, null, includeStackTrace);
    }

    static ExtendedErrorMessage fromWebApplicationException(WebApplicationException e, boolean includeStackTrace)
    {
        return fromThrowable(e, e.getResponse().getStatus(), null, null, includeStackTrace);
    }

    static ExtendedErrorMessage fromThrowable(Throwable t, Response.Status status, String message, String details, boolean includeStrackTrace)
    {
        return fromThrowable(t, ((status == null) ? getDefaultStatus() : status).getStatusCode(), message, details, includeStrackTrace);
    }

    static ExtendedErrorMessage fromThrowable(Throwable t, int statusCode, String message, String details, boolean includeStrackTrace)
    {
        Instant timestamp = Instant.now();
        return newExtendedErrorMessage(statusCode, (message == null) ? getMessage(t) : message, details, includeStrackTrace ? getStackTrace(t) : null, timestamp);
    }

    private static String getMessage(Throwable t)
    {
        String message = t.getMessage();
        if (message == null)
        {
            Throwable cause = t.getCause();
            if (cause != null)
            {
                return getMessage(cause);
            }
        }
        return message;
    }

    private static String getStackTrace(Throwable t)
    {
        StringWriter stringWriter = new StringWriter();
        try (PrintWriter printWriter = new PrintWriter(stringWriter))
        {
            t.printStackTrace(printWriter);
        }
        return stringWriter.toString();
    }

    private static Response.Status getDefaultStatus()
    {
        return Response.Status.INTERNAL_SERVER_ERROR;
    }

    private static int getDefaultStatusCode()
    {
        return getDefaultStatus().getStatusCode();
    }
}
