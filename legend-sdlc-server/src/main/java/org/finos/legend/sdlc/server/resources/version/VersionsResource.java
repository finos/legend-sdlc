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

package org.finos.legend.sdlc.server.resources.version;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.version.Version;
import org.finos.legend.sdlc.server.application.version.CreateVersionCommand;
import org.finos.legend.sdlc.server.config.LegendSDLCServerFeaturesConfiguration;
import org.finos.legend.sdlc.backend.api.project.ProjectConfigurationApi;
import org.finos.legend.sdlc.project.source.SourceSpecification;
import org.finos.legend.sdlc.backend.api.version.VersionApi;
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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/projects/{projectId}/versions")
@Tag(name = "Versions")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class VersionsResource extends BaseResource
{
    private final VersionApi versionApi;
    private final ProjectConfigurationApi projectConfigurationApi;
    private final LegendSDLCServerFeaturesConfiguration featuresConfiguration;

    @Inject
    public VersionsResource(LegendSDLCServerFeaturesConfiguration featuresConfiguration, ProjectConfigurationApi projectConfigurationApi, VersionApi versionApi)
    {
        this.featuresConfiguration = featuresConfiguration;
        this.projectConfigurationApi = projectConfigurationApi;
        this.versionApi = versionApi;
    }

    @GET
    @Operation(summary = "Get all versions for a project")
    public List<Version> getVersions(@PathParam("projectId") String projectId,
                                     @QueryParam("major")
                                     @Parameter(description = "Exact major version (trumps minimum or maximum)") Integer majorVersion,
                                     @QueryParam("minMajor")
                                     @Parameter(description = "Minimum major version (only used if exact major version is not supplied)") Integer minMajorVersion,
                                     @QueryParam("maxMajor")
                                     @Parameter(description = "Maximum major version (only used if exact major version is not supplied)") Integer maxMajorVersion,
                                     @QueryParam("minor")
                                     @Parameter(description = "Exact minor version (trumps minimum or maximum)") Integer minorVersion,
                                     @QueryParam("minMinor")
                                     @Parameter(description = "Minimum minor version (only used if exact minor version is not supplied)") Integer minMinorVersion,
                                     @QueryParam("maxMinor")
                                     @Parameter(description = "Maximum minor version (only used if exact minor version is not supplied)") Integer maxMinorVersion,
                                     @QueryParam("patch")
                                     @Parameter(description = "Exact patch version (trumps minimum or maximum)") Integer patchVersion,
                                     @QueryParam("minPatch")
                                     @Parameter(description = "Minimum patch version (only used if exact patch version is not supplied)") Integer minPatchVersion,
                                     @QueryParam("maxPatch")
                                     @Parameter(description = "Maximum patch version (only used if exact patch version is not supplied)") Integer maxPatchVersion)
    {
        return executeWithLogging(
                "getting versions for project " + projectId,
                () -> this.versionApi.getVersions(projectId,
                        (majorVersion == null) ? minMajorVersion : majorVersion,
                        (majorVersion == null) ? maxMajorVersion : majorVersion,
                        (minorVersion == null) ? minMinorVersion : minorVersion,
                        (minorVersion == null) ? maxMinorVersion : minorVersion,
                        (patchVersion == null) ? minPatchVersion : patchVersion,
                        (patchVersion == null) ? maxPatchVersion : patchVersion)
        );
    }

    @GET
    @Path("latest")
    @Operation(summary = "Get the latest version of a project")
    public Version getLatestVersion(@PathParam("projectId") String projectId,
                                    @QueryParam("major")
                                    @Parameter(description = "Exact major version (trumps minimum or maximum)") Integer majorVersion,
                                    @QueryParam("minMajor")
                                    @Parameter(description = "Minimum major version (only used if exact major version is not supplied)") Integer minMajorVersion,
                                    @QueryParam("maxMajor")
                                    @Parameter(description = "Maximum major version (only used if exact major version is not supplied)") Integer maxMajorVersion,
                                    @QueryParam("minor")
                                    @Parameter(description = "Exact minor version (trumps minimum or maximum)") Integer minorVersion,
                                    @QueryParam("minMinor")
                                    @Parameter(description = "Minimum minor version (only used if exact minor version is not supplied)") Integer minMinorVersion,
                                    @QueryParam("maxMinor")
                                    @Parameter(description = "Maximum minor version (only used if exact minor version is not supplied)") Integer maxMinorVersion,
                                    @QueryParam("patch")
                                    @Parameter(description = "Exact patch version (trumps minimum or maximum)") Integer patchVersion,
                                    @QueryParam("minPatch")
                                    @Parameter(description = "Minimum patch version (only used if exact patch version is not supplied)") Integer minPatchVersion,
                                    @QueryParam("maxPatch")
                                    @Parameter(description = "Maximum patch version (only used if exact patch version is not supplied)") Integer maxPatchVersion)
    {
        return executeWithLogging(
                "getting the latest version of project " + projectId,
                () -> this.versionApi.getLatestVersion(projectId,
                        (majorVersion == null) ? minMajorVersion : majorVersion,
                        (majorVersion == null) ? maxMajorVersion : majorVersion,
                        (minorVersion == null) ? minMinorVersion : minorVersion,
                        (minorVersion == null) ? maxMinorVersion : minorVersion,
                        (patchVersion == null) ? minPatchVersion : patchVersion,
                        (patchVersion == null) ? maxPatchVersion : patchVersion)
        );
    }

    @GET
    @Path("{versionId}")
    @Operation(summary = "Get a version of a project")
    public Version getVersion(@PathParam("projectId") String projectId, @PathParam("versionId") String versionId)
    {
        return executeWithLogging(
                "getting version " + versionId + " of project " + projectId,
                () -> this.versionApi.getVersion(projectId, versionId)
        );
    }

    @POST
    @Operation(summary = "Create a new version of a project")
    public Version createVersion(@PathParam("projectId") String projectId, CreateVersionCommand command)
    {
        LegendSDLCServerException.validateNonNull(command, "Input required to create version");
        if (!this.featuresConfiguration.canCreateVersion)
        {
            throw new LegendSDLCServerException("Server does not support creating project version(s)", Response.Status.METHOD_NOT_ALLOWED);
        }
        ProjectType type = this.projectConfigurationApi.getProjectConfiguration(projectId, SourceSpecification.projectSourceSpecification()).getProjectType();
        if (type == ProjectType.EMBEDDED)
        {
            throw new LegendSDLCServerException("Creating a version of a project of type " + type + " is not allowed", Response.Status.CONFLICT);
        }
        return executeWithLogging(
                "creating new " + command.getVersionType().name().toLowerCase() + " version",
                () -> this.versionApi.newVersion(projectId, command.getVersionType(), command.getRevisionId(), command.getNotes())
        );
    }
}
