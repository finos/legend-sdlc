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

package org.finos.legend.sdlc.server.resources.revision.project.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.project.source.SourceSpecification;
import org.finos.legend.sdlc.backend.api.revision.RevisionApi;
import org.finos.legend.sdlc.project.workspace.WorkspaceSpecification;
import org.finos.legend.sdlc.server.resources.BaseResource;
import org.finos.legend.sdlc.server.time.EndInstant;
import org.finos.legend.sdlc.server.time.ResolvedInstant;
import org.finos.legend.sdlc.server.time.StartInstant;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/projects/{projectId}/workspaces/{workspaceId}/entities/{path}/revisions")
@Tag(name = "Revisions")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class WorkspaceEntityRevisionsResource extends BaseResource
{
    private final RevisionApi revisionApi;

    @Inject
    public WorkspaceEntityRevisionsResource(RevisionApi revisionApi)
    {
        this.revisionApi = revisionApi;
    }

    @GET
    @Operation(summary = "Get all revisions for an entity in a user workspace")
    public List<Revision> getRevisions(@PathParam("projectId") String projectId,
                                       @PathParam("workspaceId") String workspaceId,
                                       @PathParam("path") String path,
                                       @QueryParam("since") StartInstant since,
                                       @QueryParam("until") EndInstant until,
                                       @QueryParam("limit") @Parameter(description = "If not provided or the provided value is non-positive, no filtering will be applied") Integer limit)
    {
        return executeWithLogging(
                "getting revisions for entity " + path + " in user workspace " + workspaceId + " for project " + projectId,
                () -> this.revisionApi.getEntityRevisionContext(projectId, SourceSpecification.workspaceSourceSpecification(WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.USER)), path).getRevisions(null, ResolvedInstant.getResolvedInstantIfNonNull(since), ResolvedInstant.getResolvedInstantIfNonNull(until), limit)
        );
    }

    @GET
    @Path("{revisionId}")
    @Operation(summary = "Get a revision of an entity in a user workspace")
    public Revision getRevision(@PathParam("projectId") String projectId,
                                @PathParam("workspaceId") String workspaceId,
                                @PathParam("path") String path,
                                @PathParam("revisionId") @Parameter(description = "Including aliases: head, latest, current, base") String revisionId)
    {
        return executeWithLogging(
                "getting revision " + revisionId + " for entity " + path + " in user workspace " + workspaceId + " for project " + projectId,
                () -> this.revisionApi.getEntityRevisionContext(projectId, SourceSpecification.workspaceSourceSpecification(WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.USER)), path).getRevision(revisionId)
        );
    }
}
