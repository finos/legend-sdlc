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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.finos.legend.sdlc.domain.model.patch.Patch;
import org.finos.legend.sdlc.domain.model.version.Version;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.domain.api.patch.PatchApi;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.resources.BaseResource;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

@Path("/projects/{projectId}/patches")
@Api("Patches")
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
    @ApiOperation("Create a new patch release")
    public Patch createPatchReleaseBranch(@PathParam("projectId") String projectId, @ApiParam("Source version for the patch release")  String sourceVersionId)
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
    @ApiOperation("Get all patch release branches for a project")
    public List<Patch> getpatchReleaseBranches(@PathParam("projectId") String projectId,
                                               @QueryParam("major")
                                               @ApiParam("Exact major version (trumps minimum or maximum)") Integer majorVersion,
                                               @QueryParam("minMajor")
                                                   @ApiParam("Minimum major version (only used if exact major version is not supplied)") Integer minMajorVersion,
                                               @QueryParam("maxMajor")
                                                   @ApiParam("Maximum major version (only used if exact major version is not supplied)") Integer maxMajorVersion,
                                               @QueryParam("minor")
                                                   @ApiParam("Exact minor version (trumps minimum or maximum)") Integer minorVersion,
                                               @QueryParam("minMinor")
                                                   @ApiParam("Minimum minor version (only used if exact minor version is not supplied)") Integer minMinorVersion,
                                               @QueryParam("maxMinor")
                                                   @ApiParam("Maximum minor version (only used if exact minor version is not supplied)") Integer maxMinorVersion,
                                               @QueryParam("patch")
                                                   @ApiParam("Exact patch version (trumps minimum or maximum)") Integer patchVersion,
                                               @QueryParam("minPatch")
                                                   @ApiParam("Minimum patch version (only used if exact patch version is not supplied)") Integer minPatchVersion,
                                               @QueryParam("maxPatch")
                                                   @ApiParam("Maximum patch version (only used if exact patch version is not supplied)") Integer maxPatchVersion)
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

    @DELETE
    @Path("{patchReleaseVersionId}")
    @ApiOperation("Delete a patch release branch")
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
    @ApiOperation("Release patch release branch")
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
