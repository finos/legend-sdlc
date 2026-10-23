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

package org.finos.legend.sdlc.server.resources.project.project;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactTypeGenerationConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.backend.api.project.ProjectConfigurationApi;
import org.finos.legend.sdlc.project.source.SourceSpecification;
import org.finos.legend.sdlc.backend.api.project.ProjectConfigurationStatusReport;
import org.finos.legend.sdlc.server.resources.BaseResource;

import java.util.List;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/projects/{projectId}/configuration")
@Tag(name = "Project Configuration")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ProjectConfigurationResource extends BaseResource
{
    private final ProjectConfigurationApi projectConfigurationApi;

    @Inject
    public ProjectConfigurationResource(ProjectConfigurationApi projectConfigurationApi)
    {
        this.projectConfigurationApi = projectConfigurationApi;
    }

    @GET
    @Operation(summary = "Get the configuration of a project")
    public ProjectConfiguration getProjectProjectConfiguration(@PathParam("projectId") String projectId)
    {
        return executeWithLogging(
                "getting project " + projectId + " configuration",
                () -> this.projectConfigurationApi.getProjectConfiguration(projectId, SourceSpecification.projectSourceSpecification())
        );
    }

    @GET
    @Path("/availableGenerations")
    @Operation(summary = "Get the available generation types of a project")
    public List<ArtifactTypeGenerationConfiguration> getProjectSupportedArtifactGeneration(@PathParam("projectId") String projectId)
    {
        return executeWithLogging(
                "getting project " + projectId + " available generations configurations",
                () -> this.projectConfigurationApi.getAvailableArtifactGenerations(projectId, SourceSpecification.projectSourceSpecification())
        );
    }

    @GET
    @Path("/projectConfigurationStatus")
    @Operation(summary = "Returns the project configuration status report")
    public ProjectConfigurationStatusReport checkProjectConfigurationStatus(@PathParam("projectId") String projectId)
    {
        return executeWithLogging(
                "checking if project " + projectId + " is configured",
                () -> this.projectConfigurationApi.getProjectConfigurationStatus(projectId)
        );
    }
}
