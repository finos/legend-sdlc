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

package org.finos.legend.sdlc.server.resources.conflictResolution.project;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.backend.api.workspace.WorkspaceApi;
import org.finos.legend.sdlc.server.resources.BaseResource;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/projects/{projectId}/conflictResolution")
@Tag(name = "Conflict Resolution")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ConflictResolutionProjectResource extends BaseResource
{
    private final WorkspaceApi workspaceApi;

    @Inject
    public ConflictResolutionProjectResource(WorkspaceApi workspaceApi)
    {
        this.workspaceApi = workspaceApi;
    }

    @GET
    @Operation(summary = "Get all workspaces with conflict resolution for the project")
    public List<Workspace> getWorkspacesWithConflictResolution(@PathParam("projectId") String projectId)
    {
        return executeWithLogging(
                "getting workspaces with conflict resolution for project " + projectId,
                () -> this.workspaceApi.getWorkspacesWithConflictResolution(projectId)
        );
    }
}
