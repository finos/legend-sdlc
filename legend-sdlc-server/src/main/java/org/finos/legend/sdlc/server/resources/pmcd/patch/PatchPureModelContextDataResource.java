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

package org.finos.legend.sdlc.server.resources.pmcd.patch;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.backend.api.entity.EntityApi;
import org.finos.legend.sdlc.project.source.SourceSpecification;
import org.finos.legend.sdlc.backend.api.revision.RevisionApi;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.resources.PureModelContextDataResource;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/projects/{projectId}/patches/{patchReleaseVersionId}/pureModelContextData")
@Tag(name = "Pure Model Context")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PatchPureModelContextDataResource extends PureModelContextDataResource
{
    private final EntityApi entityApi;
    private final RevisionApi revisionApi;

    @Inject
    public PatchPureModelContextDataResource(EntityApi entityApi, RevisionApi revisionApi)
    {
        this.entityApi = entityApi;
        this.revisionApi = revisionApi;
    }

    @GET
    @Operation(summary = "Get Pure model context data for a project (at the latest revision) for patch release version")
    public PureModelContextData getPureModelContextData(@PathParam("projectId") String projectId, @PathParam("patchReleaseVersionId") String patchReleaseVersionId)
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
                "getting Pure model context data for project " + projectId,
                () ->
                {
                    Revision revision = this.revisionApi.getRevisionContext(projectId, SourceSpecification.patchSourceSpecification(versionId)).getCurrentRevision();
                    if (revision == null)
                    {
                        throw new LegendSDLCServerException("Could not find latest revision for project " + projectId + "; project may be corrupt" + " for patch release version " + patchReleaseVersionId);
                    }
                    return getPureModelContextData(projectId, revision.getId(), this.entityApi.getEntityAccessContext(projectId, SourceSpecification.patchSourceSpecification(versionId)));
                });
    }
}
