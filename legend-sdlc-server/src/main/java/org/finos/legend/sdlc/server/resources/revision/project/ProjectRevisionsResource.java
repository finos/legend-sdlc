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

package org.finos.legend.sdlc.server.resources.revision.project;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.revision.RevisionStatus;
import org.finos.legend.sdlc.server.domain.api.revision.RevisionApi;
import org.finos.legend.sdlc.server.resources.BaseResource;
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

@Path("/projects/{projectId}/revisions")
@Api("Revisions")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ProjectRevisionsResource extends BaseResource
{
    private final RevisionApi revisionApi;

    @Inject
    public ProjectRevisionsResource(RevisionApi revisionApi)
    {
        this.revisionApi = revisionApi;
    }

    @GET
    @ApiOperation("Get all revisions for the project")
    public List<Revision> getRevisions(@PathParam("projectId") String projectId,
                                       @QueryParam("since") StartInstant since,
                                       @QueryParam("until") EndInstant until,
                                       @QueryParam("limit") @ApiParam("If not provided or the provided value is non-positive, no filtering will be applied") Integer limit)
    {
        return executeWithLogging(
                "getting revisions for project " + projectId,
                () -> this.revisionApi.getProjectRevisionContext(projectId).getRevisions(null, ResolvedInstant.getResolvedInstantIfNonNull(since), ResolvedInstant.getResolvedInstantIfNonNull(until), limit)
        );
    }

    @GET
    @Path("{revisionId}")
    @ApiOperation("Get a revision of the project")
    public Revision getRevision(@PathParam("projectId") String projectId,
                                @PathParam("revisionId") @ApiParam("Including aliases: head, latest, current, base") String revisionId)
    {
        return executeWithLogging(
                "getting revision " + revisionId + " for project " + projectId,
                () -> this.revisionApi.getProjectRevisionContext(projectId).getRevision(revisionId)
        );
    }

    @GET
    @Path("{revisionId}/status")
    @ApiOperation("Get the status a revision of the project")
    public RevisionStatus getRevisionStatus(@PathParam("projectId") String projectId,
                                            @PathParam("revisionId") @ApiParam("Including aliases: head, latest, current, base") String revisionId)
    {
        return executeWithLogging(
                "getting the status revision " + revisionId + " for project " + projectId,
                () -> this.revisionApi.getRevisionStatus(projectId, revisionId)
        );
    }
}
