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

import org.finos.legend.sdlc.backend.api.spi.UnsupportedCapabilityException;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * Maps an exercised-but-undeclared backend capability to 501 with a structured body naming the capability and
 * the backend type. More specific than the generic {@code LegendSDLCException} mapper, so it wins selection.
 */
@Provider
public class UnsupportedCapabilityExceptionMapper implements ExceptionMapper<UnsupportedCapabilityException>
{
    @Override
    public Response toResponse(UnsupportedCapabilityException exception)
    {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("capability", exception.getCapability().name());
        body.put("backendType", exception.getBackendType());
        body.put("message", exception.getMessage());
        return Response.status(exception.getStatusCode()).type(MediaType.APPLICATION_JSON_TYPE).entity(body).build();
    }
}
