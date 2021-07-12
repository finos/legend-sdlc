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

package org.finos.legend.sdlc.server.project.extension;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import org.finos.legend.sdlc.server.project.ProjectStructure;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.function.IntUnaryOperator;

public class TestDefaultProjectStructureExtensionProvider
{
    private int latestProjectStructureVersion;

    @Before
    public void setUp()
    {
        this.latestProjectStructureVersion = ProjectStructure.getLatestProjectStructureVersion();
    }

    @Test
    public void testEmpty()
    {
        DefaultProjectStructureExtensionProvider provider = DefaultProjectStructureExtensionProvider.fromExtensions();
        for (int i = 0; i <= this.latestProjectStructureVersion; i++)
        {
            Assert.assertNull(provider.getLatestVersionForProjectStructureVersion(i));
        }
    }

    @Test
    public void testGetLatestProjectStructureExtension()
    {
        IntUnaryOperator latestExtensionVersion = psv -> (this.latestProjectStructureVersion * 17) + 21;
        List<ProjectStructureExtension> extensions = Lists.mutable.empty();
        for (int i = 0; i <= this.latestProjectStructureVersion; i++)
        {
            for (int j = latestExtensionVersion.applyAsInt(i); j >= 0; j -= 21)
            {
                extensions.add(newProjectStructureExtension(i, j));
            }
        }

        DefaultProjectStructureExtensionProvider provider = DefaultProjectStructureExtensionProvider.fromExtensions(extensions);
        for (int i = 0; i <= this.latestProjectStructureVersion; i++)
        {
            Integer latest = latestExtensionVersion.applyAsInt(i);
            Assert.assertEquals(Integer.toString(i), latest, provider.getLatestVersionForProjectStructureVersion(i));
        }
    }

    @Test
    public void testGetProjectStructureExtension()
    {
        IntUnaryOperator latestExtensionVersion = psv -> (this.latestProjectStructureVersion - psv) + 1;
        MutableIntObjectMap<MutableIntObjectMap<ProjectStructureExtension>> extensionsByVersions = IntObjectMaps.mutable.empty();
        List<ProjectStructureExtension> allExtensions = Lists.mutable.empty();
        for (int i = 0; i <= this.latestProjectStructureVersion; i++)
        {
            for (int j = 0, latest = latestExtensionVersion.applyAsInt(i); j <= latest; j++)
            {
                ProjectStructureExtension extension = newProjectStructureExtension(i, j);
                extensionsByVersions.getIfAbsentPut(i, IntObjectMaps.mutable::empty).put(j, extension);
                allExtensions.add(extension);
            }
        }

        DefaultProjectStructureExtensionProvider provider = DefaultProjectStructureExtensionProvider.fromExtensions(allExtensions);
        for (int i = 0; i <= this.latestProjectStructureVersion; i++)
        {
            for (int j = 0, latest = latestExtensionVersion.applyAsInt(i); j <= latest; j++)
            {
                ProjectStructureExtension expected = extensionsByVersions.getIfAbsent(i, IntObjectMaps.mutable::empty).get(j);
                Assert.assertSame(i + "." + j, expected, provider.getProjectStructureExtension(i, j));
            }
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testConflictingVersions()
    {
        DefaultProjectStructureExtensionProvider.fromExtensions(newProjectStructureExtension(12, 2), newProjectStructureExtension(12, 2));
    }

    private ProjectStructureExtension newProjectStructureExtension(int projectStructureVersion, int extensionVersion)
    {
        return DefaultProjectStructureExtension.newProjectStructureExtension(projectStructureVersion, extensionVersion, null);
    }
}
