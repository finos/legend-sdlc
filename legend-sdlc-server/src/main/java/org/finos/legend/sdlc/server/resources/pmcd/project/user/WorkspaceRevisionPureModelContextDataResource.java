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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.backend.api.entity.EntityApi;
import org.finos.legend.sdlc.project.source.SourceSpecification;
import org.finos.legend.sdlc.project.workspace.WorkspaceSpecification;
import org.finos.legend.sdlc.server.resources.PureModelContextDataResource;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/projects/{projectId}/workspaces/{workspaceId}/revisions/{revisionId}/pureModelContextData")
@Tag(name = "Pure Model Context")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class WorkspaceRevisionPureModelContextDataResource extends PureModelContextDataResource
{
    private final EntityApi entityApi;

    @Inject
    protected WorkspaceRevisionPureModelContextDataResource(EntityApi entityApi)
    {
        this.entityApi = entityApi;
    }

    @GET
    @Operation(summary = "Get Pure model context data for a user workspace at a revision")
    public PureModelContextData getPureModelContextData(@PathParam("projectId") String projectId,
                                                        @PathParam("workspaceId") String workspaceId,
                                                        @PathParam("revisionId") @Parameter(description = "Including aliases: head, latest, current, base") String revisionId)
    {
        return executeWithLogging(
                "getting Pure model context data for user workspace " + workspaceId + " in project " + projectId + " at revision " + revisionId,
                () -> getPureModelContextData(projectId, revisionId, this.entityApi.getEntityAccessContext(projectId, SourceSpecification.workspaceSourceSpecification(WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.USER)), revisionId))
        );
    }
}
