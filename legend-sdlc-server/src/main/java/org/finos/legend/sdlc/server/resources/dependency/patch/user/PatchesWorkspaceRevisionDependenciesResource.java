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

package org.finos.legend.sdlc.server.resources.dependency.patch.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.backend.api.dependency.DependenciesApi;
import org.finos.legend.sdlc.project.source.SourceSpecification;
import org.finos.legend.sdlc.project.workspace.WorkspaceSource;
import org.finos.legend.sdlc.project.workspace.WorkspaceSpecification;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.resources.BaseResource;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Set;

@Path("/projects/{projectId}/patches/{patchReleaseVersionId}/workspaces/{workspaceId}/revisions/{revisionId}/upstreamProjects")
@Tag(name = "Dependencies")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PatchesWorkspaceRevisionDependenciesResource extends BaseResource
{
    private final DependenciesApi dependenciesApi;

    @Inject
    public PatchesWorkspaceRevisionDependenciesResource(DependenciesApi dependenciesApi)
    {
        this.dependenciesApi = dependenciesApi;
    }

    @GET
    @Operation(summary = "Get projects that the current user workspace revision depends on. Use transitive=true for transitive dependencies for patch release version.")
    public Set<ProjectDependency> getUpstreamDependencies(@PathParam("projectId") String projectId,
                                                          @PathParam("patchReleaseVersionId") String patchReleaseVersionId,
                                                          @PathParam("workspaceId") String workspaceId,
                                                          @PathParam("revisionId") String revisionId,
                                                          @QueryParam("transitive") @DefaultValue("false") boolean transitive)
    {
        LegendSDLCServerException.validateNonNull(patchReleaseVersionId, "patchReleaseVersionId may not be null");
        VersionId versionId;
        try
        {
            versionId = VersionId.parseVersionId(patchReleaseVersionId);
        }
        catch (IllegalArgumentException e)
        {
            throw new LegendSDLCServerException(e.getMessage(), Response.Status.BAD_REQUEST, e);
        }
        return executeWithLogging(
                "getting upstream dependencies of project " + projectId + " for patch release version " + patchReleaseVersionId + ", user workspace " + workspaceId + ", revision " + revisionId + " (fetch transitively = " + transitive + ")",
                () -> this.dependenciesApi.getWorkspaceRevisionUpstreamProjects(projectId, SourceSpecification.workspaceSourceSpecification(WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.USER, WorkspaceSource.patchWorkspaceSource(versionId))), revisionId, transitive)
        );
    }
}
