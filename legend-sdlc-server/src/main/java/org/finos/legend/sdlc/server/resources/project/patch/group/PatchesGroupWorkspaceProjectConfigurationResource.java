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

package org.finos.legend.sdlc.server.resources.project.patch.group;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactTypeGenerationConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.application.project.UpdateProjectConfigurationCommand;
import org.finos.legend.sdlc.server.domain.api.project.ProjectConfigurationApi;
import org.finos.legend.sdlc.server.domain.api.project.SourceSpecification;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.resources.BaseResource;

import java.util.List;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/projects/{projectId}/patches/{patchReleaseVersionId}/groupWorkspaces/{workspaceId}/configuration")
@Api("Project Configuration")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PatchesGroupWorkspaceProjectConfigurationResource extends BaseResource
{
    private final ProjectConfigurationApi projectConfigurationApi;

    @Inject
    public PatchesGroupWorkspaceProjectConfigurationResource(ProjectConfigurationApi projectConfigurationApi)
    {
        this.projectConfigurationApi = projectConfigurationApi;
    }

    @GET
    @ApiOperation("Get the configuration of a project in a group workspace for patch release version for patch release version")
    public ProjectConfiguration getWorkspaceProjectConfiguration(@PathParam("projectId") String projectId, @PathParam("patchReleaseVersionId") String patchReleaseVersionId, @PathParam("workspaceId") String workspaceId)
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
                "getting project " + projectId + " configuration in group workspace " + workspaceId + " for patch release version " + patchReleaseVersionId,
                () -> this.projectConfigurationApi.getWorkspaceProjectConfiguration(projectId, SourceSpecification.newGroupWorkspaceSourceSpecification(workspaceId, versionId))
        );
    }

    @POST
    @ApiOperation("Update the project configuration of a project in a group workspace for patch release version")
    public Revision updateProjectStructureVersion(@PathParam("projectId") String projectId, @PathParam("patchReleaseVersionId") String patchReleaseVersionId, @PathParam("workspaceId") String workspaceId, UpdateProjectConfigurationCommand command)
    {
        LegendSDLCServerException.validateNonNull(command, "Input required to update project structure");
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
                "updating configuration for project " + projectId + " in group workspace " + workspaceId + " for patch release version " + patchReleaseVersionId,
                () -> this.projectConfigurationApi.updateProjectConfiguration(projectId, SourceSpecification.newGroupWorkspaceSourceSpecification(workspaceId, versionId), command.getMessage(), command.getProjectConfigurationUpdater())
        );
    }

    @GET
    @Path("/availableGenerations")
    @ApiOperation("Get the available generation types of a project in a group workspace for patch release version")
    public List<ArtifactTypeGenerationConfiguration> getWorkspaceAvailableGenerations(@PathParam("projectId") String projectId, @PathParam("patchReleaseVersionId") String patchReleaseVersionId, @PathParam("workspaceId") String workspaceId)
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
                "getting project " + projectId + " configuration in group workspace " + workspaceId + " for patch release version " + patchReleaseVersionId,
                () -> this.projectConfigurationApi.getWorkspaceAvailableArtifactGenerations(projectId, SourceSpecification.newGroupWorkspaceSourceSpecification(workspaceId, versionId))
        );
    }
}
