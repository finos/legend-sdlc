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
import org.finos.legend.sdlc.server.project.ProjectStructureV0Factory.ProjectStructureV0;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TestProjectStructureV0 extends TestProjectStructure<ProjectStructureV0>
{
    @Override
    protected int getProjectStructureVersion()
    {
        return 0;
    }

    @Override
    protected Class<ProjectStructureV0> getProjectStructureClass()
    {
        return ProjectStructureV0.class;
    }

    @Override
    protected Map<ArtifactType, List<String>> getExpectedArtifactIdsByType(ProjectStructureV0 projectStructure)
    {
        ProjectConfiguration configuration = projectStructure.getProjectConfiguration();
        return (configuration == null) ? Collections.emptyMap() : Collections.singletonMap(ArtifactType.entities, Collections.singletonList(configuration.getArtifactId()));
    }

    @Override
    protected void assertMultiformatGenerationStateValid(String projectId, String workspaceId, String revisionId, ArtifactType jsonSchema)
    {
    }

    @Override
    protected Set<ArtifactType> getExpectedSupportedArtifactConfigurationTypes()
    {
        return Collections.emptySet();
    }
}
