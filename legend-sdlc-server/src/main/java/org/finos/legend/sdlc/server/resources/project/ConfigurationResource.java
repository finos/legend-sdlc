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

package org.finos.legend.sdlc.server.resources.project;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.finos.legend.sdlc.backend.api.spi.Backend;
import org.finos.legend.sdlc.backend.api.spi.BackendCapability;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactTypeGenerationConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ConfigurationProperty;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectStructureVersion;
import org.finos.legend.sdlc.backend.api.project.ProjectConfigurationApi;
import org.finos.legend.sdlc.project.structure.ProjectStructure;
import org.finos.legend.sdlc.project.structure.ProjectStructureFactory;
import org.finos.legend.sdlc.project.structure.ProjectStructureVersionFactory;
import org.finos.legend.sdlc.project.structure.extension.ProjectStructureExtension;
import org.finos.legend.sdlc.project.structure.extension.ProjectStructureExtensionProvider;
import org.finos.legend.sdlc.server.resources.BaseResource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Api("Project Configuration")
@Path("/configuration")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ConfigurationResource extends BaseResource
{
    private final ProjectConfigurationApi projectConfigurationApi;
    private final Provider<Backend> backend;
    private final ProjectStructureExtensionProvider extensionProvider;

    @Inject
    public ConfigurationResource(ProjectConfigurationApi projectConfigurationApi, Provider<Backend> backend, ProjectStructureExtensionProvider extensionProvider)
    {
        this.projectConfigurationApi = projectConfigurationApi;
        this.backend = backend;
        this.extensionProvider = extensionProvider;
    }

    @GET
    @Path("/latestProjectStructureVersion")
    @ApiOperation("Get the latest project structure version and extension version")
    public ProjectStructureVersion getLatestProjectStructureVersion()
    {
        return this.projectConfigurationApi.getLatestProjectStructureVersion();
    }

    @GET
    @Path("/latestAvailableGenerations")
    @ApiOperation("Get available generations for the latest version")
    @Deprecated
    public List<ArtifactTypeGenerationConfiguration> getLatestAvailableGenerations()
    {
        return this.projectConfigurationApi.getLatestAvailableArtifactGenerations();
    }

    @GET
    @Path("/capabilities")
    @ApiOperation("Get the backend type and the capabilities this deployment supports")
    public CapabilitiesInfo getCapabilities()
    {
        return executeWithLogging("getting backend capabilities", () -> new CapabilitiesInfo(this.backend.get().getType(), this.backend.get().getCapabilities()));
    }

    @GET
    @Path("/projectStructureVersions")
    @ApiOperation("Describe the available project structure versions, their extension versions, and the configuration properties each declares")
    public List<ProjectStructureVersionInfo> getProjectStructureVersions()
    {
        return executeWithLogging("describing project structure versions", () ->
        {
            ProjectStructureFactory factory = ProjectStructure.getDefaultProjectStructureFactory();
            List<ProjectStructureVersionInfo> result = new ArrayList<>();
            for (int version = 0; version <= factory.getLatestVersion(); version++)
            {
                ProjectStructureVersionFactory versionFactory = factory.getVersionFactory(version);
                if (versionFactory != null)
                {
                    List<ExtensionVersionInfo> extensionVersions = new ArrayList<>();
                    Integer latestExtensionVersion = this.extensionProvider.getLatestVersionForProjectStructureVersion(version);
                    if (latestExtensionVersion != null)
                    {
                        for (int extensionVersion = 1; extensionVersion <= latestExtensionVersion; extensionVersion++)
                        {
                            ProjectStructureExtension extension = this.extensionProvider.getProjectStructureExtension(version, extensionVersion);
                            extensionVersions.add(new ExtensionVersionInfo(extensionVersion, extension.getConfigurationProperties()));
                        }
                    }
                    result.add(new ProjectStructureVersionInfo(version, versionFactory.getConfigurationProperties(), extensionVersions));
                }
            }
            return result;
        });
    }

    public static class CapabilitiesInfo
    {
        private final String backendType;
        private final Set<BackendCapability> capabilities;

        CapabilitiesInfo(String backendType, Set<BackendCapability> capabilities)
        {
            this.backendType = backendType;
            this.capabilities = capabilities;
        }

        public String getBackendType()
        {
            return this.backendType;
        }

        public Set<BackendCapability> getCapabilities()
        {
            return this.capabilities;
        }
    }

    public static class ProjectStructureVersionInfo
    {
        private final int version;
        private final List<ConfigurationProperty> configurationProperties;
        private final List<ExtensionVersionInfo> extensionVersions;

        ProjectStructureVersionInfo(int version, List<ConfigurationProperty> configurationProperties, List<ExtensionVersionInfo> extensionVersions)
        {
            this.version = version;
            this.configurationProperties = configurationProperties;
            this.extensionVersions = extensionVersions;
        }

        public int getVersion()
        {
            return this.version;
        }

        public List<ConfigurationProperty> getConfigurationProperties()
        {
            return this.configurationProperties;
        }

        public List<ExtensionVersionInfo> getExtensionVersions()
        {
            return this.extensionVersions;
        }
    }

    public static class ExtensionVersionInfo
    {
        private final int version;
        private final List<ConfigurationProperty> configurationProperties;

        ExtensionVersionInfo(int version, List<ConfigurationProperty> configurationProperties)
        {
            this.version = version;
            this.configurationProperties = configurationProperties;
        }

        public int getVersion()
        {
            return this.version;
        }

        public List<ConfigurationProperty> getConfigurationProperties()
        {
            return this.configurationProperties;
        }
    }
}
