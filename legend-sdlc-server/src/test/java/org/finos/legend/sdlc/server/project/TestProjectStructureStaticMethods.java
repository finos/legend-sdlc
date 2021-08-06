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
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectStructureVersion;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class TestProjectStructureStaticMethods
{
    private static final ImmutableIntSet UNPUBLISHED_VERSION = IntSets.immutable.with(1,2,3,4,5,6,7,8,9,10);

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
    public void testGetLatestProjectStructureVersion()
    {
        Assert.assertEquals(11, ProjectStructure.getLatestProjectStructureVersion());
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
            ProjectConfiguration projectConfig = new SimpleProjectConfiguration("ProjectId", ProjectType.PROTOTYPE,
                    ProjectStructureVersion.newProjectStructureVersion(i), "some.group.id", "some-artifact-id",
                    Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
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
        for (ProjectType projectType : ProjectType.values())
        {
            ProjectConfiguration config = ProjectStructure.getDefaultProjectConfiguration(projectId, projectType);
            Assert.assertNotNull(config);
            Assert.assertEquals(0, config.getProjectStructureVersion().getVersion());
            Assert.assertNull(config.getProjectStructureVersion().getExtensionVersion());
            Assert.assertEquals(projectId, config.getProjectId());
            Assert.assertSame(projectType, config.getProjectType());
        }
    }
}
