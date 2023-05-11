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

package org.finos.legend.sdlc.server.resources.project.patch.user;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactTypeGenerationConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.server.application.project.UpdateProjectConfigurationCommand;
import org.finos.legend.sdlc.server.domain.api.project.ProjectConfigurationApi;
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

@Path("/projects/{projectId}/patches/{patchReleaseVersion}/workspaces/{workspaceId}/configuration")
@Api("Project Configuration")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PatchesWorkspaceProjectConfigurationResource extends BaseResource
{
    private final ProjectConfigurationApi projectConfigurationApi;

    @Inject
    public PatchesWorkspaceProjectConfigurationResource(ProjectConfigurationApi projectConfigurationApi)
    {
        this.projectConfigurationApi = projectConfigurationApi;
    }

    @GET
    @ApiOperation("Get the configuration of a project in a workspace for patch release version for patch release version")
    public ProjectConfiguration getWorkspaceProjectConfiguration(@PathParam("projectId") String projectId, @PathParam("patchReleaseVersion") String patchReleaseVersion, @PathParam("workspaceId") String workspaceId)
    {
        LegendSDLCServerException.validateNonNull(patchReleaseVersion, "patchReleaseVersion may not be null");
        return executeWithLogging(
                "getting project " + projectId + " configuration in workspace " + workspaceId + " for patch release version " + patchReleaseVersion,
                () -> this.projectConfigurationApi.getWorkspaceProjectConfiguration(projectId, patchReleaseVersion, workspaceId, WorkspaceType.USER)
        );
    }

    @POST
    @ApiOperation("Update the project configuration of a project in a workspace for patch release version")
    public Revision updateProjectStructureVersion(@PathParam("projectId") String projectId, @PathParam("patchReleaseVersion") String patchReleaseVersion, @PathParam("workspaceId") String workspaceId, UpdateProjectConfigurationCommand command)
    {
        LegendSDLCServerException.validateNonNull(command, "Input required to update project structure");
        LegendSDLCServerException.validateNonNull(patchReleaseVersion, "patchReleaseVersion may not be null");
        return executeWithLogging(
                "updating configuration for project " + projectId + " in workspace " + workspaceId + " for patch release version " + patchReleaseVersion,
                () -> this.projectConfigurationApi.updateProjectConfiguration(projectId, patchReleaseVersion, workspaceId, WorkspaceType.USER, command.getMessage(), command.getProjectConfigurationUpdater())
        );
    }

    @GET
    @Path("/availableGenerations")
    @ApiOperation("Get the available generation types of a project in a workspace for patch release version")
    public List<ArtifactTypeGenerationConfiguration> getWorkspaceAvailableGenerations(@PathParam("projectId") String projectId, @PathParam("patchReleaseVersion") String patchReleaseVersion, @PathParam("workspaceId") String workspaceId)
    {
        LegendSDLCServerException.validateNonNull(patchReleaseVersion, "patchReleaseVersion may not be null");
        return executeWithLogging(
                "getting project " + projectId + " configuration in workspace " + workspaceId + " for patch release version " + patchReleaseVersion,
                () -> this.projectConfigurationApi.getWorkspaceAvailableArtifactGenerations(projectId, patchReleaseVersion, workspaceId, WorkspaceType.USER)
        );
    }
}
