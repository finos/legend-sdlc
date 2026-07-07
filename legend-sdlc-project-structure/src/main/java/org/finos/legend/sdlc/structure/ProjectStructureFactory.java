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

package org.finos.legend.sdlc.structure;

import org.eclipse.collections.api.map.primitive.IntObjectMap;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectStructureVersion;
import org.finos.legend.sdlc.tools.StringTools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Consumer;

public class ProjectStructureFactory
{
    private final IntObjectMap<ProjectStructureVersionFactory> projectStructureFactories;
    private final int latestVersion;

    private ProjectStructureFactory(IntObjectMap<ProjectStructureVersionFactory> projectStructureFactories)
    {
        this.projectStructureFactories = projectStructureFactories;
        this.latestVersion = projectStructureFactories.keySet().max();
    }

    public int getLatestVersion()
    {
        return this.latestVersion;
    }

    public boolean supportsVersion(int version)
    {
        return this.projectStructureFactories.containsKey(version);
    }

    public ProjectStructure newProjectStructure(ProjectConfiguration projectConfiguration, ProjectStructurePlatformExtensions projectStructurePlatformExtensions)
    {
        int version = getVersion(projectConfiguration);
        ProjectStructureVersionFactory factory = this.projectStructureFactories.get(version);
        if (factory == null)
        {
            throw new IllegalArgumentException("Unknown project structure version: " + version);
        }
        return factory.newProjectStructure(projectConfiguration, projectStructurePlatformExtensions);
    }

    private int getVersion(ProjectConfiguration projectConfiguration)
    {
        if (projectConfiguration != null)
        {
            ProjectStructureVersion projectStructureVersion = projectConfiguration.getProjectStructureVersion();
            if (projectStructureVersion != null)
            {
                return projectStructureVersion.getVersion();
            }
        }
        return 0;
    }

    public static ProjectStructureFactory newFactory(Iterable<? extends ProjectStructureVersionFactory> versionFactories)
    {
        IntObjectMap<ProjectStructureVersionFactory> index = indexVersionFactories(versionFactories);
        return new ProjectStructureFactory(index);
    }

    public static ProjectStructureFactory newFactory(ClassLoader classLoader)
    {
        List<ProjectStructureVersionFactory> factories = new ArrayList<>();
        ServiceLoader.load(ProjectStructureVersionFactory.class, classLoader).forEach(factories::add);
        loadLegacyKeyedVersionFactories(classLoader, factories);
        return newFactory(factories);
    }

    /**
     * Dual-keyed lookup (re-architecture, section 5): version factories registered under the pre-relocation service
     * key {@code META-INF/services/org.finos.legend.sdlc.server.project.ProjectStructureVersionFactory} are also
     * loaded, as release-timing slack for external factory jars that have recompiled against the relocated classes
     * but not yet re-keyed their service registration. Entries whose class is already registered under the new key
     * are skipped. This lookup is deprecated from birth: it is removed together with the deprecation bridges.
     */
    private static void loadLegacyKeyedVersionFactories(ClassLoader classLoader, List<ProjectStructureVersionFactory> factories)
    {
        String legacyResourceName = "META-INF/services/org.finos.legend.sdlc.server.project.ProjectStructureVersionFactory";
        Set<String> registeredClassNames = new HashSet<>();
        factories.forEach(f -> registeredClassNames.add(f.getClass().getName()));
        Consumer<String> loadClassName = className ->
        {
            if (registeredClassNames.add(className))
            {
                try
                {
                    factories.add(classLoader.loadClass(className).asSubclass(ProjectStructureVersionFactory.class).getDeclaredConstructor().newInstance());
                }
                catch (ReflectiveOperationException | ClassCastException e)
                {
                    throw new RuntimeException(StringTools.appendThrowableMessageIfPresent("Error loading project structure version factory \"" + className + "\" registered under the legacy service key", e), e);
                }
            }
        };
        try
        {
            Enumeration<URL> resources = classLoader.getResources(legacyResourceName);
            while (resources.hasMoreElements())
            {
                URL url = resources.nextElement();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8)))
                {
                    String line;
                    while ((line = reader.readLine()) != null)
                    {
                        int commentStart = line.indexOf('#');
                        String className = ((commentStart == -1) ? line : line.substring(0, commentStart)).trim();
                        if (!className.isEmpty())
                        {
                            loadClassName.accept(className);
                        }
                    }
                }
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(StringTools.appendThrowableMessageIfPresent("Error loading project structure version factories registered under the legacy service key", e), e);
        }
    }

    private static IntObjectMap<ProjectStructureVersionFactory> indexVersionFactories(Iterable<? extends ProjectStructureVersionFactory> versionFactories)
    {
        MutableIntObjectMap<ProjectStructureVersionFactory> factories = IntObjectMaps.mutable.empty();
        versionFactories.forEach(versionFactory ->
        {
            ProjectStructureVersionFactory old = factories.put(versionFactory.getVersion(), versionFactory);
            if (old != null)
            {
                throw new IllegalArgumentException("Multiple factories for version: " + versionFactory.getVersion());
            }
        });
        return factories;
    }
}
