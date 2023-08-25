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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactTypeGenerationConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.server.domain.api.project.ProjectConfigurationApi;
import org.finos.legend.sdlc.server.resources.BaseResource;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/projects/{projectId}/groupWorkspaces/{workspaceId}/revisions/{revisionId}/configuration")
@Api("Project Configuration")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class GroupWorkspaceRevisionProjectConfigurationResource extends BaseResource
{
    private final ProjectConfigurationApi projectConfigurationApi;

    @Inject
    public GroupWorkspaceRevisionProjectConfigurationResource(ProjectConfigurationApi projectConfigurationApi)
    {
        this.projectConfigurationApi = projectConfigurationApi;
    }

    @GET
    @ApiOperation("Get the configuration for a revision of a project in a group workspace at a revision")
    public ProjectConfiguration getWorkspaceRevisionProjectConfiguration(@PathParam("projectId") String projectId,
                                                                         @PathParam("workspaceId") String workspaceId,
                                                                         @PathParam("revisionId") @ApiParam("Including aliases: head, latest, current, base") String revisionId)
    {
        return executeWithLogging(
                "getting project " + projectId + " configuration in group workspace " + workspaceId + " at revision " + revisionId,
                () -> this.projectConfigurationApi.getGroupWorkspaceRevisionProjectConfiguration(projectId, workspaceId, revisionId)
        );
    }

    @GET
    @Path("/availableGenerations")
    @ApiOperation("Get the available generation types of a project in a group workspace at a revision")
    public List<ArtifactTypeGenerationConfiguration> getProjectAvailableArtifactGeneration(@PathParam("projectId") String projectId,
                                                                                           @PathParam("workspaceId") String workspaceId,
                                                                                           @PathParam("revisionId") @ApiParam("Including aliases: head, latest, current, base") String revisionId)
    {
        return executeWithLogging(
                "getting project " + projectId + " available generations in group workspace " + workspaceId + " at revision " + revisionId,
                () -> this.projectConfigurationApi.getGroupWorkspaceRevisionAvailableArtifactGenerations(projectId, workspaceId, revisionId)
        );
    }
}
