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
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.server.domain.api.entity.EntityApi;
import org.finos.legend.sdlc.server.domain.api.revision.RevisionApi;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/projects/{projectId}/groupWorkspaces/{workspaceId}/pureModelContextData")
@Api("Pure Model Context")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class GroupWorkspacePureModelContextDataResource extends PureModelContextDataResource
{
    private final EntityApi entityApi;
    private final RevisionApi revisionApi;

    @Inject
    public GroupWorkspacePureModelContextDataResource(EntityApi entityApi, RevisionApi revisionApi)
    {
        this.entityApi = entityApi;
        this.revisionApi = revisionApi;
    }

    @GET
    @ApiOperation("Get Pure model context data for a group workspace (at the latest revision)")
    public PureModelContextData getPureModelContextData(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId)
    {
        return executeWithLogging(
                "getting Pure model context data for group workspace " + workspaceId + " in project " + projectId,
                () ->
                {
                    Revision revision = this.revisionApi.getGroupWorkspaceRevisionContext(projectId, workspaceId).getCurrentRevision();
                    if (revision == null)
                    {
                        throw new LegendSDLCServerException("Could not find latest revision for group workspace " + workspaceId + " in project " + projectId + "; project may be corrupt");
                    }
                    return getPureModelContextData(projectId, revision.getId(), this.entityApi.getGroupWorkspaceEntityAccessContext(projectId, workspaceId));
                });
    }
}
