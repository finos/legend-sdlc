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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.finos.legend.sdlc.server.BaseServer.ServerInfo;
import org.finos.legend.sdlc.server.config.LegendSDLCServerFeaturesConfiguration;
import org.finos.legend.sdlc.project.structure.ProjectStructurePlatformExtensions;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Tag(name = "Server")
@Path("/server")
@Produces(MediaType.APPLICATION_JSON)
public class ServerResource
{
    private final ServerInfo serverInfo;
    private final LegendSDLCServerFeaturesConfiguration featuresConfiguration;
    private final ProjectStructurePlatformExtensions projectStructurePlatformExtensions;

    @Inject
    public ServerResource(ServerInfo serverInfo,
                          LegendSDLCServerFeaturesConfiguration featuresConfiguration,
                          ProjectStructurePlatformExtensions projectStructurePlatformExtensions)
    {
        this.serverInfo = serverInfo;
        this.featuresConfiguration = featuresConfiguration;
        this.projectStructurePlatformExtensions = projectStructurePlatformExtensions;
    }

    @GET
    @Path("/info")
    @Operation(summary = "Provides server information")
    public ServerInfo getServerInfo()
    {
        return this.serverInfo;
    }

    @GET
    @Path("/features")
    @Operation(summary = "Provides server features information")
    public LegendSDLCServerFeaturesConfiguration getServerFeaturesInfo()
    {
        return this.featuresConfiguration;
    }

    @GET
    @Path("/platforms")
    @Operation(summary = "Provides platforms information")
    public List<ProjectStructurePlatformExtensions.Platform> getPlatformsInfo()
    {
        return this.projectStructurePlatformExtensions.getPlatforms();
    }
}
