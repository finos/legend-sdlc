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
import org.finos.legend.sdlc.server.domain.api.entity.EntityApi;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Set;

@Path("/projects/{projectId}/groupWorkspaces/{workspaceId}/backup/revisions/{revisionId}/entities")
@Api("Backup")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class GroupBackupWorkspaceRevisionEntitiesResource extends EntityAccessResource
{
    private final EntityApi entityApi;

    @Inject
    public GroupBackupWorkspaceRevisionEntitiesResource(EntityApi entityApi)
    {
        this.entityApi = entityApi;
    }

    @GET
    @ApiOperation("Get entities of the backup group workspace at the revision")
    public List<Entity> getAllEntities(@PathParam("projectId") String projectId,
                                       @PathParam("workspaceId") String workspaceId,
                                       @PathParam("revisionId") @ApiParam("Including aliases: head, latest, current, base") String revisionId,
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
        return executeWithLogging(
                "getting entities in revision " + revisionId + " of backup group workspace " + workspaceId + " for project " + projectId,
                () -> getEntities(this.entityApi.getBackupGroupWorkspaceRevisionEntityAccessContext(projectId, workspaceId, revisionId), classifierPaths, packages, includeSubPackages, nameRegex, stereotypes, taggedValueRegexes)
        );
    }

    @GET
    @Path("{path}")
    @ApiOperation("Get an entity of the backup group workspace at the revision by its path")
    public Entity getEntityByPath(@PathParam("projectId") String projectId, @PathParam("workspaceId") String workspaceId, @PathParam("revisionId") String revisionId, @PathParam("path") String path)
    {
        return executeWithLogging(
                "getting entity " + path + " in revision " + revisionId + " of backup group workspace " + workspaceId + " for project " + projectId,
                () -> this.entityApi.getBackupGroupWorkspaceRevisionEntityAccessContext(projectId, workspaceId, revisionId).getEntity(path)
        );
    }
}
