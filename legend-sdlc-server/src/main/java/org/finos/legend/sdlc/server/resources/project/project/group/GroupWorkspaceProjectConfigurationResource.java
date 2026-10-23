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

package org.finos.legend.sdlc.server.resources.project.project.group;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactTypeGenerationConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.server.application.project.UpdateProjectConfigurationCommand;
import org.finos.legend.sdlc.backend.api.project.ProjectConfigurationApi;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.resources.BaseResource;

import java.util.List;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/projects/{projectId}/groupWorkspaces/{workspaceId}/configuration")
@Tag(name = "Project Configuration")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class GroupWorkspaceProjectConfigurationResource extends BaseResource
{
    private final ProjectConfigurationApi projectConfigurationApi;

    @Inject
    public GroupWorkspaceProjectConfigurationResource(ProjectConfigurationApi projectConfigurationApi)
    {
        this.projectConfigurationApi = projectConfigurationApi;
    }

    @GET
    @Operation(summary = "Get the configuration of a project in a group workspace")
    public ProjectConfiguration getWorkspaceProjectConfiguration(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId)
    {
        return executeWithLogging(
                "getting project " + projectId + " configuration in group workspace " + workspaceId,
                () -> this.projectConfigurationApi.getGroupWorkspaceProjectConfiguration(projectId, workspaceId)
        );
    }

    @POST
    @Operation(summary = "Update the project configuration of a project in a group workspace")
    public Revision updateProjectStructureVersion(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId, UpdateProjectConfigurationCommand command)
    {
        LegendSDLCServerException.validateNonNull(command, "Input required to update project structure");
        return executeWithLogging(
                "updating configuration for project " + projectId + " in group workspace " + workspaceId,
                () -> this.projectConfigurationApi.updateProjectConfiguration(projectId, workspaceId, WorkspaceType.GROUP, command.getMessage(), command.getProjectConfigurationUpdater())
        );
    }

    @GET
    @Path("/availableGenerations")
    @Operation(summary = "Get the available generation types of a project in a group workspace")
    public List<ArtifactTypeGenerationConfiguration> getWorkspaceAvailableGenerations(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId)
    {
        return executeWithLogging(
                "getting project " + projectId + " configuration in group workspace " + workspaceId,
                () -> this.projectConfigurationApi.getGroupWorkspaceAvailableArtifactGenerations(projectId, workspaceId)
        );
    }
}
