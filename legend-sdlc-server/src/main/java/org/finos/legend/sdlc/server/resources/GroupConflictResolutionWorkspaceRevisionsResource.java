// Copyright 2021 Goldman Sachs
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
import io.swagger.annotations.ApiParam;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.server.domain.api.revision.RevisionApi;
import org.finos.legend.sdlc.server.time.EndInstant;
import org.finos.legend.sdlc.server.time.ResolvedInstant;
import org.finos.legend.sdlc.server.time.StartInstant;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/projects/{projectId}/groupWorkspaces/{workspaceId}/conflictResolution/revisions")
@Api("Conflict Resolution")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class GroupConflictResolutionWorkspaceRevisionsResource extends BaseResource
{
    private final RevisionApi revisionApi;

    @Inject
    public GroupConflictResolutionWorkspaceRevisionsResource(RevisionApi revisionApi)
    {
        this.revisionApi = revisionApi;
    }

    @GET
    @ApiOperation("Get all revisions for a group workspace with conflict resolution")
    public List<Revision> getRevisions(@PathParam("projectId") String projectId,
                                       @PathParam("workspaceId") String workspaceId,
                                       @QueryParam("since") StartInstant since,
                                       @QueryParam("until") EndInstant until,
                                       @QueryParam("limit") Integer limit)
    {
        return executeWithLogging(
                "getting revision for group workspace with conflict resolution " + workspaceId + " for project " + projectId,
                () -> this.revisionApi.getGroupWorkspaceWithConflictResolutionRevisionContext(projectId, workspaceId).getRevisions(null, ResolvedInstant.getResolvedInstantIfNonNull(since), ResolvedInstant.getResolvedInstantIfNonNull(until), limit)
        );
    }

    @GET
    @Path("{revisionId}")
    @ApiOperation("Get a revision of the group workspace with conflict resolution")
    public Revision getRevision(@PathParam("projectId") String projectId,
                                @PathParam("workspaceId") String workspaceId,
                                @PathParam("revisionId") @ApiParam("Including aliases: head, latest, current, base") String revisionId)
    {
        return executeWithLogging(
                "getting revision " + revisionId + " for group workspace with conflict resolution " + workspaceId + " for project " + projectId,
                () -> this.revisionApi.getGroupWorkspaceWithConflictResolutionRevisionContext(projectId, workspaceId).getRevision(revisionId)
        );
    }
}
