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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.finos.legend.sdlc.domain.model.project.Project;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.project.accessRole.AccessRole;
import org.finos.legend.sdlc.domain.model.project.accessRole.AuthorizableProjectAction;
import org.finos.legend.sdlc.domain.model.project.accessRole.UserPermission;
import org.finos.legend.sdlc.server.application.project.CreateProjectCommand;
import org.finos.legend.sdlc.server.application.project.ImportProjectCommand;
import org.finos.legend.sdlc.server.application.project.UpdateProjectCommand;
import org.finos.legend.sdlc.server.config.LegendSDLCServerFeaturesConfiguration;
import org.finos.legend.sdlc.backend.api.project.ProjectApi;
import org.finos.legend.sdlc.backend.api.project.ProjectApi.ImportReport;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.resources.BaseResource;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Path("/projects")
@Tag(name = "Projects")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ProjectsResource extends BaseResource
{
    private final ProjectApi projectApi;
    private final LegendSDLCServerFeaturesConfiguration featuresConfiguration;

    @Inject
    public ProjectsResource(LegendSDLCServerFeaturesConfiguration featuresConfiguration, ProjectApi projectApi)
    {
        this.featuresConfiguration = featuresConfiguration;
        this.projectApi = projectApi;
    }

    @GET
    @Operation(summary = "Get projects")
    public List<Project> getProjects(@QueryParam("search")
                                     @Parameter(description = "search string") String search,
                                     @QueryParam("user")
                                     @DefaultValue("true")
                                     @Parameter(description = "only include projects the user is associated with") boolean user,
                                     @QueryParam("tag")
                                     @Parameter(description = "only include projects with one or more of these tags") Set<String> tags,
                                     @QueryParam("excludeTag")
                                     @Parameter(description = "exclude projects that have one or more of these tags") Set<String> excludeTags,
                                     @QueryParam("limit")
                                     @Parameter(description = "If not provided or the provided value is non-positive, no filtering will be applied") Integer limit,
                                     // TO BE DEPRECATED (in Swagger 3, we can use the `deprecated` flag)
                                     @QueryParam("type")
                                     @Parameter(hidden = true, description = "DEPRECATED - this parameter will be ignored") Set<ProjectType> types)
    {
        return execute(
            "getting projects",
            "get projects",
            () -> this.projectApi.getProjects(user, search, tags, excludeTags, limit));
    }

    @GET
    @Path("{id}")
    @Operation(summary = "Get a specific project by id")
    public Project getProjectById(@PathParam("id") String id)
    {
        return executeWithLogging(
            "getting project " + id,
            this.projectApi::getProject,
            id
        );
    }

    @POST
    @Operation(summary = "Create a new project")
    public Project createProject(CreateProjectCommand command)
    {
        LegendSDLCServerException.validateNonNull(command, "Input required to create project");
        if (!featuresConfiguration.canCreateProject)
        {
            throw new LegendSDLCServerException("Server does not support creating project(s)",
                Response.Status.METHOD_NOT_ALLOWED);
        }
        return executeWithLogging(
            "creating new project \"" + command.getName() + "\"",
            () -> this.projectApi.createProject(command.getName(),
                command.getDescription(),
                command.getType(),
                command.getGroupId(),
                command.getArtifactId(),
                command.getTags())
        );
    }

    @PUT
    @Path("{id}")
    @Operation(summary = "Update existing project")
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
    @Operation(summary = "Delete existing project")
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
    @Operation(summary = "Import a project", description = "Import a project which exists in the underlying system. The supplied id need not be the same as what the final project id will be; it need only be sufficient to identify the project in the underlying system.")
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
    @Operation(summary = "Get project access role for current user")
    public AccessRole getProjectAccessRole(@PathParam("id") String projectId)
    {
        return executeWithLogging(
            "getting project access role",
            () -> this.projectApi.getCurrentUserAccessRole(projectId));
    }

    @GET
    @Path("{id}/authorizedActions")
    @Operation(summary = "Check if the current user can perform set of actions provided")
    public Set<AuthorizableProjectAction> checkAuthorizedActions(@PathParam("id") String projectId,
                                                                 @Parameter(description = "List of actions to be checked for the current user")
                                                                 @QueryParam("actions") Set<AuthorizableProjectAction> actions)
    {
        LegendSDLCServerException.validateNonNull(projectId, "id may not be null");

        return executeWithLogging(
            "checking if the current user has been authorized to perform the set of actions given",
            () -> this.projectApi.checkUserAuthorizedActions(projectId,
                (actions == null || actions.isEmpty()) ? EnumSet.allOf(AuthorizableProjectAction.class) : actions));
    }

    @GET
    @Path("{id}/authorizedActions/{action}")
    @Operation(summary = "Check if the current user can perform an action ")
    public boolean checkAuthorizedAction(@PathParam("id") String projectId,
                                         @Parameter(description = "action to be checked for the current user")
                                         @QueryParam("action") AuthorizableProjectAction action)
    {
        LegendSDLCServerException.validateNonNull(projectId, "id may not be null");
        LegendSDLCServerException.validateNonNull(action, "action may not be null");

        return executeWithLogging(
            "checking if the current user is authorized to perform the action given",
            () -> this.projectApi.checkUserAuthorizedAction(projectId, action));
    }

    @GET
    @Path("{id}/allUsersAuthorizedActions")
    @Operation(summary = "Get all the users who can perform set of actions provided")
    public Set<UserPermission> getAllUsersAuthorizedActions(@PathParam("id") String projectId,
                                                      @Parameter(description = "List of actions to be checked for the current user")
                                                                 @QueryParam("actions") Set<AuthorizableProjectAction> actions)
    {
        LegendSDLCServerException.validateNonNull(projectId, "id may not be null");

        return executeWithLogging(
                "checking if the current user has been authorized to perform the set of actions given",
                () -> this.projectApi.getAllUsersAuthorizedActions(projectId,
                        (actions == null || actions.isEmpty()) ? EnumSet.allOf(AuthorizableProjectAction.class) : actions));
    }
}
