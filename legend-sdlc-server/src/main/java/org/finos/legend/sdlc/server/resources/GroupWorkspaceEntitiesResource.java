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

package org.finos.legend.sdlc.server.resources;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.server.application.entity.CreateOrUpdateEntityCommand;
import org.finos.legend.sdlc.server.application.entity.DeleteEntitiesCommand;
import org.finos.legend.sdlc.server.application.entity.DeleteEntityCommand;
import org.finos.legend.sdlc.server.application.entity.UpdateEntitiesCommand;
import org.finos.legend.sdlc.server.domain.api.entity.EntityApi;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Set;

@Path("/projects/{projectId}/groupWorkspaces/{workspaceId}/entities")
@Api("Entities")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class GroupWorkspaceEntitiesResource extends EntityAccessResource
{
    private final EntityApi entityApi;

    @Inject
    public GroupWorkspaceEntitiesResource(EntityApi entityApi)
    {
        this.entityApi = entityApi;
    }

    @GET
    @ApiOperation("Get entities of the group workspace")
    public List<Entity> getAllEntities(@PathParam("projectId") String projectId,
                                       @PathParam("workspaceId") String workspaceId,
                                       @QueryParam("classifierPath")
                                       @ApiParam("Only include entities with one of these classifier paths.") Set<String> classifierPaths,
                                       @QueryParam("package")
                                       @ApiParam("Only include entities in one of these packages. If includeSubPackages is true (which it is by default), then entities in subpackages are also included. Otherwise, only entities directly in one of the packages are included.") Set<String> packages,
                                       @QueryParam("includeSubPackages")
                                       @DefaultValue("true")
                                       @ApiParam("Whether to include entities from subpackages or only directly in one of the given packages. This is ignored if no packages are supplied.") boolean includeSubPackages,
                                       @QueryParam("name")
                                       @ApiParam("Only include entities with a name matching this regular expression.") String nameRegex,
                                       @QueryParam("stereotype")
                                       @ApiParam("Only include entities with one of these stereotypes. The syntax is PROFILE.NAME, where PROFILE is the full path of the Profile that owns the Stereotype.") Set<String> stereotypes,
                                       @QueryParam("taggedValue")
                                       @ApiParam("Only include entities with a matching tagged value. The syntax is PROFILE.NAME/REGEX, where PROFILE is the full path of the Profile that owns the Tag, NAME is the name of the Tag, and REGEX is a regular expression to match against the value.") List<String> taggedValueRegexes)
    {
        return execute(
                "getting entities in group workspace " + workspaceId + " for project " + projectId,
                "get entities of the group workspace",
                () -> getEntities(this.entityApi.getGroupWorkspaceEntityAccessContext(projectId, workspaceId), classifierPaths, packages, includeSubPackages, nameRegex, stereotypes, taggedValueRegexes)
        );
    }

    @DELETE
    @ApiOperation(value = "Delete multiple entities in a group workspace", notes = "Delete multiple entities. If the list of entities to delete is null, all entities will be deleted.")
    public Revision deleteEntities(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId, DeleteEntitiesCommand command)
    {
        List<String> entityPathsToDelete = command.getEntitiesToDelete();
        return (entityPathsToDelete == null) ?
                executeWithLogging(
                        "deleting all entities in group workspace " + workspaceId + " for project " + projectId,
                        () -> this.entityApi.getGroupWorkspaceEntityModificationContext(projectId, workspaceId).deleteAllEntities(command.getMessage())
                ) :
                executeWithLogging(
                        "deleting " + entityPathsToDelete.size() + " entities in group workspace " + workspaceId + " for project " + projectId,
                        () -> this.entityApi.getGroupWorkspaceEntityModificationContext(projectId, workspaceId).deleteEntities(entityPathsToDelete, command.getMessage())
                );
    }

    @POST
    @ApiOperation(value = "Update entities in a group workspace", notes = "Update entities with new definitions. If replace is true, then all entities are replaced. This means that existing entities are deleted unless a new definition is supplied.")
    public Revision updateEntities(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId, UpdateEntitiesCommand command)
    {
        LegendSDLCServerException.validateNonNull(command, "Input required to update entities");
        return execute(
                "updating entities in group workspace " + workspaceId + " for project " + projectId,
                "update entities",
                () -> this.entityApi.getGroupWorkspaceEntityModificationContext(projectId, workspaceId).updateEntities(command.getEntities(), command.isReplace(), command.getMessage())
        );
    }

    @GET
    @Path("{path}")
    @ApiOperation("Get an entity of the group workspace by its path")
    public Entity getEntityByPath(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId, @PathParam("path") String path)
    {
        return executeWithLogging(
                "getting entity " + path + " in group workspace " + workspaceId + " for project " + projectId,
                () -> this.entityApi.getGroupWorkspaceEntityAccessContext(projectId, workspaceId).getEntity(path)
        );
    }

    @POST
    @Path("{path}")
    @ApiOperation("Create a new entity or update an existing entity in a group workspace")
    public Revision createOrUpdateEntity(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId, @PathParam("path") String path, CreateOrUpdateEntityCommand command)
    {
        LegendSDLCServerException.validateNonNull(command, "Input required to create or update an entity");
        return executeWithLogging(
                "Creating or updating entity " + path + " in group workspace " + workspaceId + " for project " + projectId,
                () -> this.entityApi.getGroupWorkspaceEntityModificationContext(projectId, workspaceId).createOrUpdateEntity(path, command.getClassifierPath(), command.getContent(), command.getMessage())
        );
    }

    @DELETE
    @Path("{path}")
    @ApiOperation("Delete an entity in a group workspace")
    public Revision deleteEntity(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId, @PathParam("path") String path, DeleteEntityCommand command)
    {
        LegendSDLCServerException.validateNonNull(command, "Input required to delete an entity");
        return executeWithLogging(
                "deleting entity " + path + " in group workspace " + workspaceId + " for project " + projectId,
                () -> this.entityApi.getGroupWorkspaceEntityModificationContext(projectId, workspaceId).deleteEntity(path, command.getMessage())
        );
    }
}