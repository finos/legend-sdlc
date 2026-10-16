// Copyright 2026 Goldman Sachs
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

package org.finos.legend.sdlc.project.structure.maven;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactType;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependencyExclusion;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class TestMavenProjectDependencies
{
    private static final ProjectDependency DEPENDENCY_WITH_EXCLUSIONS = ProjectDependency.newProjectDependency(
            "org.finos.legend.test:dependency-project",
            "1.2.3",
            Arrays.asList(
                    ProjectDependencyExclusion.newProjectDependencyExclusion("org.finos.legend.test:excluded-project-1"),
                    ProjectDependencyExclusion.newProjectDependencyExclusion("org.finos.legend.other:excluded-project-2")));

    @Test
    public void testEntitiesDependencyExclusions()
    {
        List<Dependency> dependencies = MavenProjectStructure.projectDependencyToMavenDependenciesForType(DEPENDENCY_WITH_EXCLUSIONS, ArtifactType.entities, true).collect(Collectors.toList());
        Assert.assertEquals(1, dependencies.size());
        assertDependency("org.finos.legend.test:dependency-project-entities:1.2.3", dependencies.get(0));
        assertExclusions(Arrays.asList("org.finos.legend.test:excluded-project-1-entities", "org.finos.legend.other:excluded-project-2-entities"), dependencies.get(0));
    }

    @Test
    public void testVersionedEntitiesDependencyExclusions()
    {
        List<Dependency> dependencies = MavenProjectStructure.projectDependencyToMavenDependenciesForType(DEPENDENCY_WITH_EXCLUSIONS, ArtifactType.versioned_entities, false).collect(Collectors.toList());
        Assert.assertEquals(1, dependencies.size());
        assertDependency("org.finos.legend.test:dependency-project-versioned-entities:null", dependencies.get(0));
        assertExclusions(Arrays.asList("org.finos.legend.test:excluded-project-1-versioned-entities", "org.finos.legend.other:excluded-project-2-versioned-entities"), dependencies.get(0));
    }

    @Test
    public void testAllDependenciesExclusions()
    {
        // The parent pom's dependency management carries an entry per default artifact type
        List<Dependency> dependencies = MavenProjectStructure.projectDependencyToAllMavenDependencies(DEPENDENCY_WITH_EXCLUSIONS, true).collect(Collectors.toList());
        Assert.assertEquals(2, dependencies.size());
        assertDependency("org.finos.legend.test:dependency-project-entities:1.2.3", dependencies.get(0));
        assertExclusions(Arrays.asList("org.finos.legend.test:excluded-project-1-entities", "org.finos.legend.other:excluded-project-2-entities"), dependencies.get(0));
        assertDependency("org.finos.legend.test:dependency-project-versioned-entities:1.2.3", dependencies.get(1));
        assertExclusions(Arrays.asList("org.finos.legend.test:excluded-project-1-versioned-entities", "org.finos.legend.other:excluded-project-2-versioned-entities"), dependencies.get(1));
    }

    @Test
    public void testDependencyWithoutExclusions()
    {
        ProjectDependency projectDependency = ProjectDependency.newProjectDependency("org.finos.legend.test:dependency-project", "1.2.3");
        List<Dependency> dependencies = MavenProjectStructure.projectDependencyToMavenDependenciesForType(projectDependency, ArtifactType.entities, true).collect(Collectors.toList());
        Assert.assertEquals(1, dependencies.size());
        assertDependency("org.finos.legend.test:dependency-project-entities:1.2.3", dependencies.get(0));
        assertExclusions(Collections.emptyList(), dependencies.get(0));
    }

    private static void assertDependency(String expectedCoordinates, Dependency dependency)
    {
        Assert.assertEquals(expectedCoordinates, dependency.getGroupId() + ":" + dependency.getArtifactId() + ":" + dependency.getVersion());
    }

    private static void assertExclusions(List<String> expectedCoordinates, Dependency dependency)
    {
        List<Exclusion> exclusions = dependency.getExclusions();
        Assert.assertEquals(expectedCoordinates, exclusions.stream().map(e -> e.getGroupId() + ":" + e.getArtifactId()).collect(Collectors.toList()));
    }
}
