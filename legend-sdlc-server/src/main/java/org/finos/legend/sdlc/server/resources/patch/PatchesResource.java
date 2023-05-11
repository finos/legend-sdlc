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
import org.finos.legend.sdlc.domain.model.patch.Patch;
import org.finos.legend.sdlc.domain.model.version.Version;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.application.version.CreatePatchReleaseVersionCommand;
import org.finos.legend.sdlc.server.domain.api.patch.PatchApi;
import org.finos.legend.sdlc.server.resources.BaseResource;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
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
    public Patch createPatchReleaseBranch(@PathParam("projectId") String projectId, CreatePatchReleaseVersionCommand sourcePatchReleaseVersionCommand)
    {
        Version sourcePatchReleaseVersion = new Version()
        {
            @Override
            public VersionId getId()
            {
                return new VersionId()
                {
                    @Override
                    public int getMajorVersion()
                    {
                        return sourcePatchReleaseVersionCommand.getMajorVersion();
                    }

                    @Override
                    public int getMinorVersion()
                    {
                        return sourcePatchReleaseVersionCommand.getMinorVersion();
                    }

                    @Override
                    public int getPatchVersion()
                    {
                        return sourcePatchReleaseVersionCommand.getPatchVersion();
                    }
                };
            }

            @Override
            public String getProjectId()
            {
                return sourcePatchReleaseVersionCommand.getProjectId();
            }

            @Override
            public String getRevisionId()
            {
                return sourcePatchReleaseVersionCommand.getRevisionId();
            }

            @Override
            public String getNotes()
            {
                return sourcePatchReleaseVersionCommand.getNotes();
            }
        };
        return executeWithLogging(
                "creating new patch release " + sourcePatchReleaseVersion.getId().nextPatchVersion() + " for project " + projectId,
                () ->   this.patchApi.newPatch(projectId, sourcePatchReleaseVersion)
        );
    }

    @GET
    @ApiOperation("Get all patch release branches for a project")
    public List<Patch> getpatchReleaseBranches(@PathParam("projectId") String projectId)
    {
        return executeWithLogging(
                "getting patch release branches for project " + projectId,
                () -> this.patchApi.getAllPatches(projectId)
        );
    }

    @DELETE
    @Path("{patchReleaseVersion}")
    @ApiOperation("Delete a patch release branch")
    public void deletePatchReleaseBranch(@PathParam("projectId") String projectId, @PathParam("patchReleaseVersion") String patchReleaseVersion)
    {
        executeWithLogging(
                "deleting patch release branch " + patchReleaseVersion + " for project " + projectId,
                () -> this.patchApi.deletePatch(projectId, patchReleaseVersion)
        );
    }

    @POST
    @Path("{patchReleaseVersion}/release")
    @ApiOperation("Release patch release branch")
    public Version createVersion(@PathParam("projectId") String projectId, @PathParam("patchReleaseVersion") String patchReleaseVersion)
    {
        return executeWithLogging(
                "creating new " + patchReleaseVersion + " version",
                () -> this.patchApi.releasePatch(projectId, patchReleaseVersion)
        );
    }
}
