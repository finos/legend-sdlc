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

import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactType;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider.FileAccessContext;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class ProjectStructureV0Factory extends ProjectStructureVersionFactory
{
    @Override
    public int getVersion()
    {
        return 0;
    }

    @Override
    protected ProjectStructure createProjectStructure(ProjectConfiguration projectConfiguration)
    {
        return new ProjectStructureV0(projectConfiguration);
    }

    @Override
    protected boolean isNullProjectConfigurationAllowed()
    {
        return true;
    }

    public static class ProjectStructureV0 extends ProjectStructure
    {
        private static final Set<ArtifactType> SUPPORTED_ARTIFACT_TYPES = Collections.unmodifiableSet(EnumSet.of(ArtifactType.entities));

        private ProjectStructureV0(ProjectConfiguration projectConfiguration)
        {
            super(projectConfiguration, "/entities");
        }

        @Override
        public int getVersion()
        {
            return 0;
        }

        @Override
        public void collectUpdateProjectConfigurationOperations(ProjectStructure oldStructure, FileAccessContext fileAccessContext, BiFunction<String, VersionId, FileAccessContext> versionFileAccessContextProvider, Consumer<ProjectFileOperation> operationConsumer)
        {
            if (oldStructure.getVersion() != 0)
            {
                throw new IllegalArgumentException("Cannot update from project structure version " + oldStructure.getVersion() + " to " + getVersion());
            }
        }

        @Override
        public Set<ArtifactType> getSupportedArtifactTypes()
        {
            return SUPPORTED_ARTIFACT_TYPES;
        }

        @Override
        public Stream<String> getArtifactIdsForType(ArtifactType type)
        {
            if (isSupportedArtifactType(type))
            {
                ProjectConfiguration configuration = getProjectConfiguration();
                if (configuration != null && configuration.getArtifactId() != null)
                {
                    return Stream.of(configuration.getArtifactId());
                }
            }
            return Stream.empty();
        }
    }
}
