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

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

@Provider
public class CatchAllExceptionMapper extends BaseExceptionMapper<Throwable>
{
    public CatchAllExceptionMapper(boolean includeStackTrace)
    {
        super(includeStackTrace);
    }

    public CatchAllExceptionMapper()
    {
        this(false);
    }

    @Override
    public Response toResponse(Throwable t)
    {
        return (t instanceof WebApplicationException) ? toResponse((WebApplicationException) t) : buildDefaultResponse(t);
    }

    private Response toResponse(WebApplicationException e)
    {
        Response response = e.getResponse();
        if (Response.Status.Family.REDIRECTION == response.getStatusInfo().getFamily())
        {
            return response;
        }
        return Response.fromResponse(response)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(ExtendedErrorMessage.fromWebApplicationException(e, this.includeStackTrace))
                .build();
    }
}
