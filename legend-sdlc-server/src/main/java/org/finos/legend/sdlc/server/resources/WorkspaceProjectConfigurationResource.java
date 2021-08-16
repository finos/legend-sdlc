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

package org.finos.legend.sdlc.server.resources;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactTypeGenerationConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.server.application.project.UpdateProjectConfigurationCommand;
import org.finos.legend.sdlc.server.domain.api.project.ProjectConfigurationApi;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/projects/{projectId}/workspaces/{workspaceId}/configuration")
@Api("Project Configuration")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class WorkspaceProjectConfigurationResource extends BaseResource
{
    private final ProjectConfigurationApi projectConfigurationApi;

    @Inject
    public WorkspaceProjectConfigurationResource(ProjectConfigurationApi projectConfigurationApi)
    {
        this.projectConfigurationApi = projectConfigurationApi;
    }

    @GET
    @ApiOperation("Get the configuration of a project in a user workspace")
    public ProjectConfiguration getWorkspaceProjectConfiguration(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId)
    {
        return executeWithLogging(
                "getting project " + projectId + " configuration in user workspace " + workspaceId,
                () -> this.projectConfigurationApi.getUserWorkspaceProjectConfiguration(projectId, workspaceId)
        );
    }

    @POST
    @ApiOperation("Update the project configuration of a project in a user workspace")
    public Revision updateProjectStructureVersion(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId, UpdateProjectConfigurationCommand command)
    {
        LegendSDLCServerException.validateNonNull(command, "Input required to update project structure");
        return executeWithLogging(
                "updating configuration for project " + projectId + " in user workspace " + workspaceId,
                () -> this.projectConfigurationApi.updateProjectConfigurationForUserWorkspace(
                        projectId,
                        workspaceId,
                        command.getMessage(),
                        command.getProjectStructureVersion().getVersion(),
                        command.getProjectStructureVersion().getExtensionVersion(),
                        command.getGroupId(),
                        command.getArtifactId(),
                        command.getProjectDependenciesToAdd(),
                        command.getProjectDependenciesToRemove(),
                        command.getArtifactGenerationsToAdd(),
                        command.getArtifactGenerationsNamesToRemove())
        );
    }

    @GET
    @Path("/availableGenerations")
    @ApiOperation("Get the available generation types of a project in a user workspace")
    public List<ArtifactTypeGenerationConfiguration> getWorkspaceAvailableGenerations(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId)
    {
        return executeWithLogging(
                "getting project " + projectId + " configuration in user workspace " + workspaceId,
                () -> this.projectConfigurationApi.getUserWorkspaceAvailableArtifactGenerations(projectId, workspaceId)
        );
    }
}
