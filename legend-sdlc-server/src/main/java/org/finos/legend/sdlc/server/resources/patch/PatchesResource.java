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

package org.finos.legend.sdlc.server.resources.patch;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.finos.legend.sdlc.domain.model.patch.Patch;
import org.finos.legend.sdlc.domain.model.version.Version;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.backend.api.patch.PatchApi;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.resources.BaseResource;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

@Path("/projects/{projectId}/patches")
@Tag(name = "Patches")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PatchesResource extends BaseResource
{
    private final PatchApi patchApi;

    @Inject
    public PatchesResource(PatchApi patchApi)
    {
        this.patchApi = patchApi;
    }

    @POST
    @Operation(summary = "Create a new patch release")
    public Patch createPatchReleaseBranch(@PathParam("projectId") String projectId, @RequestBody(description = "Source version for the patch release") String sourceVersionId)
    {
        VersionId versionId;
        try
        {
            versionId = VersionId.parseVersionId(sourceVersionId);
        }
        catch (IllegalArgumentException e)
        {
            throw new LegendSDLCServerException(e.getMessage(), Response.Status.BAD_REQUEST, e);
        }
        return executeWithLogging(
                "creating new patch release " + versionId.nextPatchVersion() + " for project " + projectId,
                () ->   this.patchApi.newPatch(projectId, versionId)
        );
    }

    @GET
    @Operation(summary = "Get all patch release branches for a project")
    public List<Patch> getpatchReleaseBranches(@PathParam("projectId") String projectId,
                                               @QueryParam("major")
                                               @Parameter(description = "Exact major version (trumps minimum or maximum)") Integer majorVersion,
                                               @QueryParam("minMajor")
                                                   @Parameter(description = "Minimum major version (only used if exact major version is not supplied)") Integer minMajorVersion,
                                               @QueryParam("maxMajor")
                                                   @Parameter(description = "Maximum major version (only used if exact major version is not supplied)") Integer maxMajorVersion,
                                               @QueryParam("minor")
                                                   @Parameter(description = "Exact minor version (trumps minimum or maximum)") Integer minorVersion,
                                               @QueryParam("minMinor")
                                                   @Parameter(description = "Minimum minor version (only used if exact minor version is not supplied)") Integer minMinorVersion,
                                               @QueryParam("maxMinor")
                                                   @Parameter(description = "Maximum minor version (only used if exact minor version is not supplied)") Integer maxMinorVersion,
                                               @QueryParam("patch")
                                                   @Parameter(description = "Exact patch version (trumps minimum or maximum)") Integer patchVersion,
                                               @QueryParam("minPatch")
                                                   @Parameter(description = "Minimum patch version (only used if exact patch version is not supplied)") Integer minPatchVersion,
                                               @QueryParam("maxPatch")
                                                   @Parameter(description = "Maximum patch version (only used if exact patch version is not supplied)") Integer maxPatchVersion)
    {
        return executeWithLogging(
                "getting patch release branches for project " + projectId,
                () -> this.patchApi.getPatches(projectId,
                        (majorVersion == null) ? minMajorVersion : majorVersion,
                        (majorVersion == null) ? maxMajorVersion : majorVersion,
                        (minorVersion == null) ? minMinorVersion : minorVersion,
                        (minorVersion == null) ? maxMinorVersion : minorVersion,
                        (patchVersion == null) ? minPatchVersion : patchVersion,
                        (patchVersion == null) ? maxPatchVersion : patchVersion)
        );
    }

    @GET
    @Path("{patchReleaseVersionId}")
    @Operation(summary = "Get a specific patch")
    public Patch getPatchReleaseBranch(@PathParam("projectId") String projectId, @PathParam("patchReleaseVersionId") String patchReleaseVersionId)
    {
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
                "getting patch " + patchReleaseVersionId + " for project " + projectId,
                () -> this.patchApi.getPatch(projectId, versionId)
        );
    }

    @DELETE
    @Path("{patchReleaseVersionId}")
    @Operation(summary = "Delete a patch release branch")
    public void deletePatchReleaseBranch(@PathParam("projectId") String projectId, @PathParam("patchReleaseVersionId") String patchReleaseVersionId)
    {
        VersionId versionId;
        try
        {
            versionId = VersionId.parseVersionId(patchReleaseVersionId);
        }
        catch (IllegalArgumentException e)
        {
            throw new LegendSDLCServerException(e.getMessage(), Response.Status.BAD_REQUEST, e);
        }
        executeWithLogging(
                "deleting patch release branch " + patchReleaseVersionId + " for project " + projectId,
                () -> this.patchApi.deletePatch(projectId, versionId)
        );
    }

    @POST
    @Path("{patchReleaseVersionId}/release")
    @Operation(summary = "Release patch release branch")
    public Version createVersion(@PathParam("projectId") String projectId, @PathParam("patchReleaseVersionId") String patchReleaseVersionId)
    {
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
                "creating new " + patchReleaseVersionId + " version",
                () -> this.patchApi.releasePatch(projectId, versionId)
        );
    }
}
