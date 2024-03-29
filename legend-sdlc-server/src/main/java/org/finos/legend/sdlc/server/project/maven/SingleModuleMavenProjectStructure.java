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

package org.finos.legend.sdlc.server.project.maven;

import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactType;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.server.project.ProjectStructurePlatformExtensions;

import java.util.Collection;
import java.util.stream.Stream;

public abstract class SingleModuleMavenProjectStructure extends MavenProjectStructure
{
    protected SingleModuleMavenProjectStructure(ProjectConfiguration projectConfiguration, ProjectStructurePlatformExtensions projectStructurePlatformExtensions)
    {
        super(projectConfiguration, "/src/main/resources/entities", projectStructurePlatformExtensions);
    }

    @Deprecated
    protected SingleModuleMavenProjectStructure(ProjectConfiguration projectConfiguration)
    {
        this(projectConfiguration, null);
    }

    @Override
    protected String getMavenProjectModelPackaging()
    {
        return JAR_PACKAGING;
    }

    @Override
    protected void configureMavenProjectModel(MavenModelConfiguration configuration)
    {
        super.configureMavenProjectModel(configuration);

        // Dependencies
        addJunitDependency(configuration::addDependency);
        addJacksonDependency(configuration::addDependency);
        validateDependencyConflicts(getProjectDependencies(),
                ProjectDependency::getProjectId,
                (id, deps) -> (deps.size() > 1) ? deps.stream().collect(StringBuilder::new, (builder, dep) -> dep.appendVersionIdString(builder.append((builder.length() == 0) ? "multiple versions not allowed: " : ", ")), StringBuilder::append).toString() : null,
                "projects");
        getProjectDependenciesAsMavenDependencies(getSupportedArtifactTypes(), true).forEach(configuration::addDependency);
    }

    @Override
    public Stream<String> getArtifactIdsForType(ArtifactType type)
    {
        return isSupportedArtifactType(type) ? Stream.of(getProjectConfiguration().getArtifactId()) : Stream.empty();
    }

    @Override
    public Stream<String> getArtifactIds(Collection<? extends ArtifactType> types)
    {
        return types.stream().anyMatch(this::isSupportedArtifactType) ? Stream.of(getProjectConfiguration().getArtifactId()) : Stream.empty();
    }
}
