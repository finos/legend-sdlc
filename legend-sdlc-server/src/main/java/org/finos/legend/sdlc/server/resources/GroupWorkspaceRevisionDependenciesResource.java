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
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.server.domain.api.dependency.DependenciesApi;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.Set;

@Path("/projects/{projectId}/groupWorkspaces/{workspaceId}/revisions/{revisionId}/upstreamProjects")
@Api("Dependencies")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class GroupWorkspaceRevisionDependenciesResource extends BaseResource
{
    private final DependenciesApi dependenciesApi;

    @Inject
    public GroupWorkspaceRevisionDependenciesResource(DependenciesApi dependenciesApi)
    {
        this.dependenciesApi = dependenciesApi;
    }

    @GET
    @ApiOperation("Get projects that the current group workspace revision depends on. Use transitive=true for transitive dependencies.")
    public Set<ProjectDependency> getUpstreamDependencies(@PathParam("projectId") String projectId,
                                                          @PathParam("workspaceId") String workspaceId,
                                                          @PathParam("revisionId") String revisionId,
                                                          @QueryParam("transitive") @DefaultValue("false") boolean transitive)
    {
        return executeWithLogging(
                "getting upstream dependencies of project " + projectId + ", group workspace " + workspaceId + ", revision " + revisionId + " (fetch transitively = " + transitive + ")",
                () -> this.dependenciesApi.getGroupWorkspaceRevisionUpstreamProjects(projectId, workspaceId, revisionId, transitive)
        );
    }
}
