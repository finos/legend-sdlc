// Copyright 2023 Goldman Sachs
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

package org.finos.legend.sdlc.server.resources.revision.patch.user;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.domain.api.revision.RevisionApi;
import org.finos.legend.sdlc.server.domain.api.project.SourceSpecification;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
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
import javax.ws.rs.core.Response;
import java.util.List;

@Path("/projects/{projectId}/patches/{patchReleaseVersionId}/workspaces/{workspaceId}/revisions")
@Api("Revisions")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PatchesWorkspaceRevisionsResource extends BaseResource
{
    private final RevisionApi revisionApi;

    @Inject
    public PatchesWorkspaceRevisionsResource(RevisionApi revisionApi)
    {
        this.revisionApi = revisionApi;
    }

    @GET
    @ApiOperation("Get all revisions for a user workspace for patch release version")
    public List<Revision> getRevisions(@PathParam("projectId") String projectId,
                                       @PathParam("patchReleaseVersionId") String patchReleaseVersionId,
                                       @PathParam("workspaceId") String workspaceId,
                                       @QueryParam("since") StartInstant since,
                                       @QueryParam("until") EndInstant until,
                                       @QueryParam("limit") @ApiParam("If not provided or the provided value is non-positive, no filtering will be applied") Integer limit)
    {
        LegendSDLCServerException.validateNonNull(patchReleaseVersionId, "patchReleaseVersionId may not be null");
        VersionId versionId;
        try
        {
            versionId = VersionId.parseVersionId(patchReleaseVersionId);
        }
        catch (IllegalArgumentException e)
        {
            throw new LegendSDLCServerException(e.getMessage(), Response.Status.BAD_REQUEST, e);
        }
        return executeWithLogging(
                "getting revision for user workspace " + workspaceId + " for project " + projectId + " for patch release version " + patchReleaseVersionId,
                () -> this.revisionApi.getWorkspaceRevisionContext(projectId, SourceSpecification.newUserWorkspaceSourceSpecification(workspaceId, versionId)).getRevisions(null, ResolvedInstant.getResolvedInstantIfNonNull(since), ResolvedInstant.getResolvedInstantIfNonNull(until), limit)
        );
    }

    @GET
    @Path("{revisionId}")
    @ApiOperation("Get a revision of the user workspace for patch release version")
    public Revision getRevision(@PathParam("projectId") String projectId,
                                @PathParam("patchReleaseVersionId") String patchReleaseVersionId,
                                @PathParam("workspaceId") String workspaceId,
                                @PathParam("revisionId") @ApiParam("Including aliases: head, latest, current, base") String revisionId)
    {
        LegendSDLCServerException.validateNonNull(patchReleaseVersionId, "patchReleaseVersionId may not be null");
        VersionId versionId;
        try
        {
            versionId = VersionId.parseVersionId(patchReleaseVersionId);
        }
        catch (IllegalArgumentException e)
        {
            throw new LegendSDLCServerException(e.getMessage(), Response.Status.BAD_REQUEST, e);
        }
        return executeWithLogging(
                "getting revision " + revisionId + " for user workspace " + workspaceId + " for project " + projectId + " for patch release version " + patchReleaseVersionId,
                () -> this.revisionApi.getWorkspaceRevisionContext(projectId, SourceSpecification.newUserWorkspaceSourceSpecification(workspaceId, versionId)).getRevision(revisionId)
        );
    }
}
