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

package org.finos.legend.sdlc.server.resources.pmcd.project.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.backend.api.entity.EntityApi;
import org.finos.legend.sdlc.project.source.SourceSpecification;
import org.finos.legend.sdlc.project.source.WorkspaceSourceSpecification;
import org.finos.legend.sdlc.backend.api.revision.RevisionApi;
import org.finos.legend.sdlc.project.workspace.WorkspaceSpecification;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.resources.PureModelContextDataResource;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/projects/{projectId}/workspaces/{workspaceId}/pureModelContextData")
@Tag(name = "Pure Model Context")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class WorkspacePureModelContextDataResource extends PureModelContextDataResource
{
    private final EntityApi entityApi;
    private final RevisionApi revisionApi;

    @Inject
    public WorkspacePureModelContextDataResource(EntityApi entityApi, RevisionApi revisionApi)
    {
        this.entityApi = entityApi;
        this.revisionApi = revisionApi;
    }

    @GET
    @Operation(summary = "Get Pure model context data for a user workspace (at the latest revision)")
    public PureModelContextData getPureModelContextData(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId)
    {
        return executeWithLogging(
                "getting Pure model context data for user workspace " + workspaceId + " in project " + projectId,
                () ->
                {
                    WorkspaceSourceSpecification workspaceSourceSpec = SourceSpecification.workspaceSourceSpecification(WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.USER));
                    Revision revision = this.revisionApi.getRevisionContext(projectId, workspaceSourceSpec).getCurrentRevision();
                    if (revision == null)
                    {
                        throw new LegendSDLCServerException("Could not find latest revision for user workspace " + workspaceId + " in project " + projectId + "; project may be corrupt");
                    }
                    return getPureModelContextData(projectId, revision.getId(), this.entityApi.getEntityAccessContext(projectId, workspaceSourceSpec));
                });
    }
}
