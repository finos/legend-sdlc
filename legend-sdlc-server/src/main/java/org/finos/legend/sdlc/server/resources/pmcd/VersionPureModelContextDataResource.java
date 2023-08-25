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

package org.finos.legend.sdlc.server.resources.pmcd;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.domain.api.entity.EntityApi;
import org.finos.legend.sdlc.server.resources.PureModelContextDataResource;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/projects/{projectId}/versions/{versionId}/pureModelContextData")
@Api("Pure Model Context")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class VersionPureModelContextDataResource extends PureModelContextDataResource
{
    private final EntityApi entityApi;

    @Inject
    protected VersionPureModelContextDataResource(EntityApi entityApi)
    {
        this.entityApi = entityApi;
    }

    @GET
    @ApiOperation("Get Pure model context data for a version of a project")
    public PureModelContextData getPureModelContextData(@PathParam("projectId") String projectId, @PathParam("versionId") String versionId)
    {
        return executeWithLogging(
                "getting Pure model context data for version " + versionId + " of project " + projectId,
                () -> getPureModelContextData(projectId, VersionId.parseVersionId(versionId).toVersionIdString(), this.entityApi.getVersionEntityAccessContext(projectId, versionId))
        );
    }
}
