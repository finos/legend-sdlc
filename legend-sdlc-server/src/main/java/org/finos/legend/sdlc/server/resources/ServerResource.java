// Copyright 2022 Goldman Sachs
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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.finos.legend.sdlc.server.BaseServer.ServerInfo;
import org.finos.legend.sdlc.server.config.LegendSDLCServerFeaturesConfiguration;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Api("Server")
@Path("/server")
@Produces(MediaType.APPLICATION_JSON)
public class ServerResource
{
    private final ServerInfo serverInfo;
    private final LegendSDLCServerFeaturesConfiguration featuresConfiguration;

    @Inject
    public ServerResource(ServerInfo serverInfo, LegendSDLCServerFeaturesConfiguration featuresConfiguration)
    {
        this.serverInfo = serverInfo;
        this.featuresConfiguration = featuresConfiguration;
    }

    @GET
    @Path("/info")
    @ApiOperation("Provides server information")
    public ServerInfo getServerInfo()
    {
        return this.serverInfo;
    }

    @GET
    @Path("/features")
    @ApiOperation("Provides server features information")
    public LegendSDLCServerFeaturesConfiguration getServerFeaturesInfo()
    {
        return this.featuresConfiguration;
    }
}
