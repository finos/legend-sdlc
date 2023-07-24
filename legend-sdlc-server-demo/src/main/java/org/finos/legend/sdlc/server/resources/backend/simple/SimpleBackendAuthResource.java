// Copyright 2023 Goldman Sachs
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

package org.finos.legend.sdlc.server.resources.backend.simple;

import io.swagger.annotations.ApiParam;
import org.finos.legend.sdlc.server.resources.BaseResource;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.Collections;
import java.util.Set;
import java.util.regex.Pattern;

@Path("/auth")
public class SimpleBackendAuthResource extends BaseResource
{
    private static final Pattern TERMS_OF_SERVICE_MESSAGE_PATTERN = Pattern.compile("terms\\s++of\\s++service", Pattern.CASE_INSENSITIVE);

    @Inject
    public SimpleBackendAuthResource()
    {
        super();
    }

    @GET
    @Path("callback")
    public Object callback(@QueryParam("code") String code, @QueryParam("state") String state)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @GET
    @Path("authorize")
    @Produces(MediaType.TEXT_HTML)
    public String authorize(@QueryParam("redirect_uri") @ApiParam("URI to redirect to when authorization is complete") String redirectUri)
    {
        return executeWithLogging("authorizing", () ->
        {
            return "<html><h1>Success</h1></html>";
        });
    }

    @GET
    @Path("termsOfServiceAcceptance")
    @Produces(MediaType.APPLICATION_JSON)
    // NOTE: we have to return a set for backward compatibility reason
    public Set<String> termsOfServiceAcceptance()
    {
        return Collections.emptySet();
    }
}
