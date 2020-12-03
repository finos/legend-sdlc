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
import io.swagger.annotations.ApiParam;
import org.finos.legend.sdlc.domain.model.project.Project;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.project.accessRole.AccessRole;
import org.finos.legend.sdlc.server.application.project.CreateProjectCommand;
import org.finos.legend.sdlc.server.application.project.ImportProjectCommand;
import org.finos.legend.sdlc.server.application.project.UpdateProjectCommand;
import org.finos.legend.sdlc.server.domain.api.project.ProjectApi;
import org.finos.legend.sdlc.server.domain.api.project.ProjectApi.ImportReport;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Set;

@Path("/projects")
@Api("Projects")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ProjectsResource extends BaseResource
{
    private final ProjectApi projectApi;

    @Inject
    public ProjectsResource(ProjectApi projectApi)
    {
        this.projectApi = projectApi;
    }

    @GET
    @ApiOperation("Get projects")
    public List<Project> getProjects(@QueryParam("search")
                                     @ApiParam("search string") String search,
                                     @QueryParam("user")
                                     @DefaultValue("true")
                                     @ApiParam("only include projects the user is associated with") boolean user,
                                     @QueryParam("tag")
                                     @ApiParam("only include projects with one or more of these tags") Set<String> tags,
                                     @QueryParam("type")
                                     @ApiParam("only include projects of the given types (defaults to all types)") Set<ProjectType> types)
    {
        return execute(
                "getting projects",
                "get projects",
                () -> this.projectApi.getProjects(user, search, tags, types));
    }

    @GET
    @Path("{id}")
    @ApiOperation("Get a specific project by id")
    public Project getProjectById(@PathParam("id") String id)
    {
        return executeWithLogging(
                "getting project " + id,
                this.projectApi::getProject,
                id
        );
    }

    @POST
    @ApiOperation("Create a new project")
    public Project createProject(CreateProjectCommand command)
    {
        LegendSDLCServerException.validateNonNull(command, "Input required to create project");
        return executeWithLogging(
                "creating new project \"" + command.getName() + "\"",
                () -> this.projectApi.createProject(command.getName(), command.getDescription(), command.getType(), command.getGroupId(), command.getArtifactId(), command.getTags())
        );
    }

    @PUT
    @Path("{id}")
    @ApiOperation("Update existing project")
    public void updateProject(@PathParam("id") String id, UpdateProjectCommand command)
    {
        LegendSDLCServerException.validateNonNull(command, "Input required to update project");
        executeWithLogging(
                "updating project " + id,
                () ->
                {
                    String name = command.getName();
                    if (name != null)
                    {
                        this.projectApi.changeProjectName(id, name);
                    }

                    String description = command.getDescription();
                    if (description != null)
                    {
                        this.projectApi.changeProjectDescription(id, description);
                    }

                    List<String> tags = command.getTags();
                    if (tags != null)
                    {
                        this.projectApi.setProjectTags(id, tags);
                    }
                }
        );
    }

    @DELETE
    @Path("{id}")
    @ApiOperation("Delete existing project")
    public void deleteProject(@PathParam("id") String projectId)
    {
        executeWithLogging(
                "deleting project " + projectId,
                this.projectApi::deleteProject,
                projectId
        );
    }

    @POST
    @Path("import")
    @ApiOperation(value = "Import a project", notes = "Import a project which exists in the underlying system. The supplied id need not be the same as what the final project id will be; it need only be sufficient to identify the project in the underlying system.")
    public ImportReport importProject(ImportProjectCommand command)
    {
        LegendSDLCServerException.validateNonNull(command, "Input required to import project");
        return executeWithLogging(
                "importing project " + command.getId() + " (type: " + command.getType() + ", groupId: " + command.getGroupId() + ", artifactId: " + command.getArtifactId() + ")",
                () -> this.projectApi.importProject(command.getId(), command.getType(), command.getGroupId(), command.getArtifactId())
        );
    }

    @GET
    @Path("{id}/userAccessRole/currentUser")
    @ApiOperation("Get project access role for current user")
    public AccessRole getProjectAccessRole(@PathParam("id") String projectId)
    {
        return executeWithLogging(
                "getting project access role",
                () -> this.projectApi.getCurrentUserAccessRole(projectId));
    }
}
