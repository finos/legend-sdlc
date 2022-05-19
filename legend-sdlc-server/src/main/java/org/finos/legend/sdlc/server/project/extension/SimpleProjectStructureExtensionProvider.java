// Copyright 2022 Goldman Sachs
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

package org.finos.legend.sdlc.server.project.extension;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.primitive.IntObjectMap;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import org.eclipse.collections.impl.utility.ListIterate;
import org.finos.legend.sdlc.server.project.ProjectStructure;
import org.finos.legend.sdlc.server.project.config.ProjectFileConfiguration;

import java.io.InputStream;
import java.io.Reader;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * A {@link ProjectStructureExtensionProvider} with a simple YAML configuration. The configuration consists of a list of
 * project structure versions, each of which contains a list of extensions. Extensions are numbered according to their
 * position in the list (starting from 1). By default, extensions are configured according to
 * {@link SimpleExtensionConfiguration SimpleExtensionConfiguration}, but this itself can be configured in the
 * {@link Builder Builder}.
 */
public class SimpleProjectStructureExtensionProvider extends BaseProjectStructureExtensionProvider
{
    private final IntObjectMap<? extends ListIterable<? extends ProjectStructureExtension>> extensions;

    protected SimpleProjectStructureExtensionProvider(Builder builder)
    {
        this.extensions = builder.computeExtensions();
    }

    @Override
    public Integer getLatestVersionForProjectStructureVersion(int projectStructureVersion)
    {
        ListIterable<? extends ProjectStructureExtension> versionExtensions = this.extensions.get(projectStructureVersion);
        return (versionExtensions == null) ? null : versionExtensions.size();
    }

    @Override
    public ProjectStructureExtension getProjectStructureExtension(int projectStructureVersion, int projectStructureExtensionVersion)
    {
        if (projectStructureExtensionVersion > 0)
        {
            ListIterable<? extends ProjectStructureExtension> versionExtensions = this.extensions.get(projectStructureVersion);
            if ((versionExtensions != null) && (projectStructureExtensionVersion <= versionExtensions.size()))
            {
                return versionExtensions.get(projectStructureExtensionVersion - 1);
            }
        }
        return null;
    }

    public interface ExtensionConfiguration
    {
        ProjectStructureExtension build(int projectStructureVersion, int extensionVersion);
    }

    protected static class SimpleExtensionConfiguration implements ExtensionConfiguration
    {
        private final Map<String, ProjectFileConfiguration> files;

        protected SimpleExtensionConfiguration(Map<String, ProjectFileConfiguration> files)
        {
            this.files = files;
        }

        @Override
        public ProjectStructureExtension build(int projectStructureVersion, int extensionVersion)
        {
            return DefaultProjectStructureExtension.newProjectStructureExtensionFromConfig(projectStructureVersion, extensionVersion, this.files);
        }

        @JsonCreator
        public static SimpleExtensionConfiguration newConfig(@JsonProperty("files") Map<String, ProjectFileConfiguration> files)
        {
            return new SimpleExtensionConfiguration(files);
        }
    }

    public static Builder newBuilder()
    {
        return newBuilder(null);
    }

    public static Builder newBuilder(Class<? extends ExtensionConfiguration> extensionConfigClass)
    {
        return new Builder((extensionConfigClass == null) ? SimpleExtensionConfiguration.class : extensionConfigClass);
    }

    public static class Builder
    {
        private final MutableList<ProjectStructureVersionConfig> versionConfigs = Lists.mutable.empty();
        private final ObjectReader objectReader;

        private Builder(Class<? extends ExtensionConfiguration> extensionConfigClass)
        {
            YAMLMapper mapper = YAMLMapper.builder()
                    .addModule(new SimpleModule().addAbstractTypeMapping(ExtensionConfiguration.class, extensionConfigClass))
                    .disable(StreamReadFeature.AUTO_CLOSE_SOURCE)
                    .build();
            this.objectReader = mapper.readerFor(mapper.getTypeFactory().constructCollectionType(List.class, ProjectStructureVersionConfig.class));
        }

        public SimpleProjectStructureExtensionProvider build()
        {
            return new SimpleProjectStructureExtensionProvider(this);
        }

        public Builder withConfigFromResource(String resourceName)
        {
            return withConfig(null, resourceName);
        }

        public Builder withConfig(ClassLoader classLoader, String resourceName)
        {
            URL url = ((classLoader == null) ? Thread.currentThread().getContextClassLoader() : classLoader).getResource(resourceName);
            if (url == null)
            {
                throw new RuntimeException("Error loading configuration from resource \"" + resourceName + "\": cannot find resource");
            }
            try
            {
                return withConfigs(this.objectReader.readValue(url));
            }
            catch (Exception e)
            {
                throw new RuntimeException("Error loading configuration from resource \"" + resourceName + "\" (" + url + ")", e);
            }
        }

        public Builder withConfig(URL url)
        {
            try
            {
                return withConfigs(this.objectReader.readValue(url));
            }
            catch (Exception e)
            {
                throw new RuntimeException("Error loading configuration from URL " + url, e);
            }
        }

        public Builder withConfig(String configString)
        {
            try
            {
                return withConfigs(this.objectReader.readValue(configString));
            }
            catch (Exception e)
            {
                throw new RuntimeException("Error loading configuration from string", e);
            }
        }

        public Builder withConfig(InputStream stream)
        {
            try
            {
                return withConfigs(this.objectReader.readValue(stream));
            }
            catch (Exception e)
            {
                throw new RuntimeException("Error loading configuration from InputStream", e);
            }
        }

        public Builder withConfig(Reader reader)
        {
            try
            {
                return withConfigs(this.objectReader.readValue(reader));
            }
            catch (Exception e)
            {
                throw new RuntimeException("Error loading configuration from Reader", e);
            }
        }

        private Builder withConfigs(Collection<? extends ProjectStructureVersionConfig> configs)
        {
            this.versionConfigs.addAll(configs);
            return this;
        }

        private IntObjectMap<ListIterable<ProjectStructureExtension>> computeExtensions()
        {
            if (this.versionConfigs.isEmpty())
            {
                return IntObjectMaps.immutable.empty();
            }

            int latestProjectStructureVersion = ProjectStructure.getLatestProjectStructureVersion();
            MutableIntObjectMap<ListIterable<ProjectStructureExtension>> result = IntObjectMaps.mutable.ofInitialCapacity(this.versionConfigs.size());
            this.versionConfigs.forEach(config ->
            {
                if ((config.projectStructureVersion < 0) || (config.projectStructureVersion > latestProjectStructureVersion))
                {
                    throw new RuntimeException("Invalid project structure version (must be in range [0.." + latestProjectStructureVersion + "]): " + config.projectStructureVersion);
                }
                if (result.put(config.projectStructureVersion, config.build()) != null)
                {
                    throw new RuntimeException("Multiple configurations for project structure version " + config.projectStructureVersion);
                }
            });
            return result;
        }
    }

    private static class ProjectStructureVersionConfig
    {
        private final int projectStructureVersion;
        private final List<ExtensionConfiguration> extensionConfigs;

        private ProjectStructureVersionConfig(int projectStructureVersion, List<ExtensionConfiguration> extensionConfigs)
        {
            this.projectStructureVersion = projectStructureVersion;
            this.extensionConfigs = extensionConfigs;
        }

        ListIterable<ProjectStructureExtension> build()
        {
            MutableList<ProjectStructureExtension> extensions = Lists.mutable.ofInitialCapacity(this.extensionConfigs.size());
            ListIterate.forEachWithIndex(this.extensionConfigs, (exConfig, index) -> extensions.add(buildExtension(exConfig, index + 1)));
            return extensions;
        }

        private ProjectStructureExtension buildExtension(ExtensionConfiguration extensionConfig, int extensionVersion)
        {
            ProjectStructureExtension extension = extensionConfig.build(this.projectStructureVersion, extensionVersion);
            if ((extension.getProjectStructureVersion() != this.projectStructureVersion) || (extension.getVersion() != extensionVersion))
            {
                throw new RuntimeException("Project structure extension built with project structure version " + this.projectStructureVersion + " and extension version " + extensionVersion +
                        " has project structure version " + extension.getProjectStructureVersion() + " and extension version " + extension.getVersion());
            }
            return extension;
        }

        @JsonCreator
        static ProjectStructureVersionConfig newConfig(@JsonProperty("projectStructureVersion") int projectStructureVersion, @JsonProperty("extensions") List<ExtensionConfiguration> extensionConfigs)
        {
            return new ProjectStructureVersionConfig(projectStructureVersion, extensionConfigs);
        }
    }
}
