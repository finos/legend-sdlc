// Copyright 2020 Goldman Sachs
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
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.server.application.entity.PerformChangesCommand;
import org.finos.legend.sdlc.server.domain.api.entity.EntityApi;
import org.finos.legend.sdlc.server.error.MetadataException;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/projects/{projectId}/workspaces/{workspaceId}/conflictResolution/entityChanges")
@Api("Conflict Resolution")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ConflictResolutionWorkspaceEntityChangesResource extends BaseResource
{
    private final EntityApi entityApi;

    @Inject
    public ConflictResolutionWorkspaceEntityChangesResource(EntityApi entityApi)
    {
        this.entityApi = entityApi;
    }

    @Deprecated
    @POST
    @ApiOperation("Perform entity changes on workspace with conflict resolution")
    public Revision performEntityChanges(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId, PerformChangesCommand command)
    {
        MetadataException.validateNonNull(command, "Input required to perform entity changes");
        return executeWithLogging(
                "performing changes in workspace with conflict resolution " + workspaceId + " for project " + projectId,
                () -> this.entityApi.getWorkspaceWithConflictResolutionEntityModificationContext(projectId, workspaceId).performChanges(command.getEntityChanges(), command.getRevisionId(), command.getMessage())
        );
    }
}
