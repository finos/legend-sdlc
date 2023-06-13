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

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.set.primitive.ImmutableIntSet;
import org.eclipse.collections.impl.factory.primitive.IntSets;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectStructureVersion;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class TestProjectStructureStaticMethods
{
    private static final ImmutableIntSet UNPUBLISHED_VERSION = IntSets.immutable.with(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

    @Test
    public void testIsValidGroupId()
    {
        String[] validGroupIds = {"org.finos.legend.sdlc", "org.finos", "valid.group.id"};
        for (String groupId : validGroupIds)
        {
            Assert.assertTrue(groupId, ProjectStructure.isValidGroupId(groupId));
        }

        String[] invalidGroupIds = {null, "", "Not A Group Id", "model::domain"};
        for (String groupId : invalidGroupIds)
        {
            Assert.assertFalse(groupId, ProjectStructure.isValidGroupId(groupId));
        }
    }

    @Test
    public void testIsValidArtifactId()
    {
        String[] validArtifactIds = {"valid-artifact-id", "validartifactid", "this2that", "model4u", "a-bad-artifact-name"};
        for (String artifactId : validArtifactIds)
        {
            Assert.assertTrue(artifactId, ProjectStructure.isValidArtifactId(artifactId));
        }

        String[] invalidArtifactIds = {null, "", "Not An Artifact Id", "valid.group.id", "1model", "MyModel"};
        for (String artifactId : invalidArtifactIds)
        {
            Assert.assertFalse(artifactId, ProjectStructure.isValidArtifactId(artifactId));
        }
    }

    @Test
    public void testIsStrictVersionId()
    {
        String[] strictIds = {"0.0.0", "0.0.1", "0.1.0", "1.0.0", "1.2.3", "9876543210.1234567890.1357924680"};
        for (String id : strictIds)
        {
            Assert.assertTrue(id, ProjectStructure.isStrictVersionId(id));
        }

        String[] nonStrictIds = {null, "", ".", "..", "abc", "master-SNAPSHOT", "1.0.0-SNAPSHOT", "-1.2.3", "1.-2.3", "1.2.3.4.5.6.7.8.9", "5", "5.2", "5_4", "1.8.0_202"};
        for (String id : nonStrictIds)
        {
            Assert.assertFalse(id, ProjectStructure.isStrictVersionId(id));
        }
    }

    @Test
    public void testIsProperProjectDependency()
    {
        ProjectDependency[] properDependencies = {ProjectDependency.newProjectDependency("project", "1.2.3"), ProjectDependency.newProjectDependency("abc", "3.5.7")};
        for (ProjectDependency dependency : properDependencies)
        {
            Assert.assertTrue(String.valueOf(dependency), ProjectStructure.isProperProjectDependency(dependency));
        }

        ProjectDependency[] otherDependencies = {null,
                ProjectDependency.newProjectDependency(null, null),
                ProjectDependency.newProjectDependency("project", null),
                ProjectDependency.newProjectDependency(null, "1.2.3"),
                ProjectDependency.newProjectDependency("project", "master-SNAPSHOT"),
                ProjectDependency.newProjectDependency("", "1.2.3")};
        for (ProjectDependency dependency : otherDependencies)
        {
            Assert.assertFalse(String.valueOf(dependency), ProjectStructure.isProperProjectDependency(dependency));
        }
    }

    @Test
    public void testGetLatestProjectStructureVersion()
    {
        Assert.assertEquals(13, ProjectStructure.getLatestProjectStructureVersion());
    }

    @Test
    public void testGetProjectStructure()
    {
        List<Integer> missingVersions = Lists.mutable.empty();
        List<Integer> badVersions = Lists.mutable.empty();
        for (int i = 0; i <= ProjectStructure.getLatestProjectStructureVersion(); i++)
        {
            if (UNPUBLISHED_VERSION.contains(i))
            {
                continue;
            }
            ProjectConfiguration projectConfig = SimpleProjectConfiguration.newConfiguration("ProjectId", ProjectStructureVersion.newProjectStructureVersion(i), "some.group.id", "some-artifact-id", Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
            ProjectStructure structure;
            try
            {
                structure = ProjectStructure.getProjectStructure(projectConfig);
            }
            catch (Exception ignore)
            {
                structure = null;
            }
            if (structure == null)
            {
                missingVersions.add(i);
            }
            else if (structure.getVersion() != i)
            {
                badVersions.add(i);
            }
        }
        if (!missingVersions.isEmpty() || !badVersions.isEmpty())
        {
            StringBuilder builder = new StringBuilder();
            if (!missingVersions.isEmpty())
            {
                builder.append(missingVersions.stream().map(Object::toString).collect(Collectors.joining(", ", "Could not find versions: ", badVersions.isEmpty() ? "" : "\n")));
            }
            if (!badVersions.isEmpty())
            {
                builder.append(badVersions.stream().map(Object::toString).collect(Collectors.joining(", ", "Versions with wrong version number: ", "")));
            }
            Assert.fail(builder.toString());
        }
    }

    @Test
    public void testGetDefaultProjectConfiguration()
    {
        String projectId = "pid";
        ProjectConfiguration config = ProjectStructure.getDefaultProjectConfiguration(projectId);
        Assert.assertNotNull(config);
        Assert.assertEquals(0, config.getProjectStructureVersion().getVersion());
        Assert.assertNull(config.getProjectStructureVersion().getExtensionVersion());
        Assert.assertEquals(projectId, config.getProjectId());
        Assert.assertEquals(ProjectType.MANAGED, config.getProjectType());
    }
}
