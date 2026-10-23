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

package org.finos.legend.sdlc.server.resources.project.project.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactTypeGenerationConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.backend.api.project.ProjectConfigurationApi;
import org.finos.legend.sdlc.server.resources.BaseResource;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/projects/{projectId}/workspaces/{workspaceId}/revisions/{revisionId}/configuration")
@Tag(name = "Project Configuration")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class WorkspaceRevisionProjectConfigurationResource extends BaseResource
{
    private final ProjectConfigurationApi projectConfigurationApi;

    @Inject
    public WorkspaceRevisionProjectConfigurationResource(ProjectConfigurationApi projectConfigurationApi)
    {
        this.projectConfigurationApi = projectConfigurationApi;
    }

    @GET
    @Operation(summary = "Get the configuration for a revision of a project in a user workspace at a revision")
    public ProjectConfiguration getWorkspaceRevisionProjectConfiguration(@PathParam("projectId") String projectId,
                                                                         @PathParam("workspaceId") String workspaceId,
                                                                         @PathParam("revisionId") @Parameter(description = "Including aliases: head, latest, current, base") String revisionId)
    {
        return executeWithLogging(
                "getting project " + projectId + " configuration in user workspace " + workspaceId + " at revision " + revisionId,
                () -> this.projectConfigurationApi.getUserWorkspaceRevisionProjectConfiguration(projectId, workspaceId, revisionId)
        );
    }

    @GET
    @Path("/availableGenerations")
    @Operation(summary = "Get the available generation types of a project in a user workspace at a revision")
    public List<ArtifactTypeGenerationConfiguration> getProjectAvailableArtifactGeneration(@PathParam("projectId") String projectId,
                                                                                           @PathParam("workspaceId") String workspaceId,
                                                                                           @PathParam("revisionId") @Parameter(description = "Including aliases: head, latest, current, base") String revisionId)
    {
        return executeWithLogging(
                "getting project " + projectId + " available generations in user workspace " + workspaceId + " at revision " + revisionId,
                () -> this.projectConfigurationApi.getUserWorkspaceRevisionAvailableArtifactGenerations(projectId, workspaceId, revisionId)
        );
    }
}
