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

package org.finos.legend.sdlc.server.resources;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/auth")
public class FileSystemAuthCheckResource extends BaseResource
{
    private final HttpServletRequest httpRequest;
    private final HttpServletResponse httpResponse;

    @Inject
    public FileSystemAuthCheckResource(HttpServletRequest httpRequest, HttpServletResponse httpResponse)
    {
        super();
        this.httpRequest = httpRequest;
        this.httpResponse = httpResponse;
    }

    @GET
    @Path("authorized")
    @Produces(MediaType.APPLICATION_JSON)
    public boolean isAuthorized()
    {
        return true;
    }

}
