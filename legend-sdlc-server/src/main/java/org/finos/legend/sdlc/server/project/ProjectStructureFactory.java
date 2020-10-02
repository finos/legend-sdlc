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

package org.finos.legend.sdlc.server.project;

import org.eclipse.collections.api.map.primitive.IntObjectMap;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectStructureVersion;

import java.util.ServiceLoader;

public class ProjectStructureFactory
{
    private final IntObjectMap<ProjectStructureVersionFactory> projectStructureFactories;

    private ProjectStructureFactory(IntObjectMap<ProjectStructureVersionFactory> projectStructureFactories)
    {
        this.projectStructureFactories = projectStructureFactories;
    }

    public int getLatestVersion()
    {
        return this.projectStructureFactories.keySet().max();
    }

    public boolean supportsVersion(int version)
    {
        return this.projectStructureFactories.containsKey(version);
    }

    public ProjectStructure newProjectStructure(ProjectConfiguration projectConfiguration)
    {
        int version = getVersion(projectConfiguration);
        ProjectStructureVersionFactory factory = this.projectStructureFactories.get(version);
        if (factory == null)
        {
            throw new IllegalArgumentException("Unknown project structure version: " + version);
        }
        return factory.newProjectStructure(projectConfiguration);
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
        return newFactory(ServiceLoader.load(ProjectStructureVersionFactory.class, classLoader));
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
