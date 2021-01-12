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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.Response.Status.Family;
import javax.ws.rs.ext.ExceptionMapper;

abstract class BaseExceptionMapper<T extends Throwable> implements ExceptionMapper<T>
{
    private static final Logger LOGGER = LoggerFactory.getLogger(BaseExceptionMapper.class);

    protected final boolean includeStackTrace;

    protected BaseExceptionMapper(boolean includeStackTrace)
    {
        this.includeStackTrace = includeStackTrace;
    }

    protected Response buildResponse(Status status, ExtendedErrorMessage errorMessage)
    {
        if (status.getStatusCode() != errorMessage.getCode())
        {
            LOGGER.warn("Building response with status code ({}) that does not match error message statue code ({})", status.getStatusCode(), errorMessage.getCode());
        }
        return Response.status(status)
                .entity(errorMessage)
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    protected Response buildDefaultResponse(Throwable t)
    {
        Status status = Status.INTERNAL_SERVER_ERROR;
        ExtendedErrorMessage errorMessage = ExtendedErrorMessage.fromThrowable(t, this.includeStackTrace);
        if (errorMessage.getCode() != status.getStatusCode())
        {
            LOGGER.warn("Building default response for error message with non-default status: {}", errorMessage.getCode());
            Status errorMessageStatus = Status.fromStatusCode(errorMessage.getCode());
            if (errorMessageStatus == null)
            {
                LOGGER.warn("Unknown status code in error message: {}", errorMessage.getCode());
            }
            else
            {
                Family family = errorMessageStatus.getFamily();
                if ((family == Family.CLIENT_ERROR) || (family == Family.SERVER_ERROR))
                {
                    status = errorMessageStatus;
                }
            }
        }
        return buildResponse(status, errorMessage);
    }
}
