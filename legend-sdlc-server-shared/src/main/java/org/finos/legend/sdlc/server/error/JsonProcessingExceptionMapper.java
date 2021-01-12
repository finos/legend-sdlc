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

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;

public class JsonProcessingExceptionMapper extends BaseExceptionMapper<JsonProcessingException>
{
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonProcessingExceptionMapper.class);

    public JsonProcessingExceptionMapper(boolean includeStackTrace)
    {
        super(includeStackTrace);
    }

    public JsonProcessingExceptionMapper()
    {
        this(false);
    }

    @Override
    public Response toResponse(JsonProcessingException exception)
    {
        if ((exception instanceof InvalidDefinitionException) || (exception instanceof JsonGenerationException))
        {
            LOGGER.error("Error processing JSON", exception);
            return buildDefaultResponse(exception);
        }

        LOGGER.debug("Unable to process JSON", exception);
        Response.Status status = Response.Status.BAD_REQUEST;
        String message = "Unable to process JSON";
        String details = exception.getMessage();
        return buildResponse(status, ExtendedErrorMessage.fromThrowable(exception, status, message, details, this.includeStackTrace));
    }
}
