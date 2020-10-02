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

package org.finos.legend.sdlc.domain.model.project.configuration;

import org.finos.legend.sdlc.domain.model.TestTools;
import org.junit.Assert;
import org.junit.Test;

public class TestProjectStructureVersion
{
    @Test
    public void testEquals()
    {
        for (int i = 0; i < 10; i++)
        {
            Assert.assertEquals(ProjectStructureVersion.newProjectStructureVersion(i), ProjectStructureVersion.newProjectStructureVersion(i));
            Assert.assertNotEquals(ProjectStructureVersion.newProjectStructureVersion(i), ProjectStructureVersion.newProjectStructureVersion(i + 1));
            Assert.assertNotEquals(ProjectStructureVersion.newProjectStructureVersion(i), ProjectStructureVersion.newProjectStructureVersion(i - 1));
            for (int j = 0; j < 10; j++)
            {
                Assert.assertEquals(ProjectStructureVersion.newProjectStructureVersion(i, j), ProjectStructureVersion.newProjectStructureVersion(i, j));
                Assert.assertNotEquals(ProjectStructureVersion.newProjectStructureVersion(i, j), ProjectStructureVersion.newProjectStructureVersion(i, j + 1));
                Assert.assertNotEquals(ProjectStructureVersion.newProjectStructureVersion(i, j), ProjectStructureVersion.newProjectStructureVersion(i, j - 1));
            }
        }
    }

    @Test
    public void testCompareTo()
    {
        for (int i = 0; i < 10; i++)
        {
            TestTools.assertCompareTo(0, ProjectStructureVersion.newProjectStructureVersion(i), ProjectStructureVersion.newProjectStructureVersion(i));
            TestTools.assertCompareTo(-1, ProjectStructureVersion.newProjectStructureVersion(i), ProjectStructureVersion.newProjectStructureVersion(i + 1));
            TestTools.assertCompareTo(1, ProjectStructureVersion.newProjectStructureVersion(i), ProjectStructureVersion.newProjectStructureVersion(i - 1));
            for (int j = 0; j < 10; j++)
            {
                TestTools.assertCompareTo(-1, ProjectStructureVersion.newProjectStructureVersion(i), ProjectStructureVersion.newProjectStructureVersion(i, j));
                TestTools.assertCompareTo(0, ProjectStructureVersion.newProjectStructureVersion(i, j), ProjectStructureVersion.newProjectStructureVersion(i, j));
                TestTools.assertCompareTo(-1, ProjectStructureVersion.newProjectStructureVersion(i, j), ProjectStructureVersion.newProjectStructureVersion(i, j + 1));
                TestTools.assertCompareTo(1, ProjectStructureVersion.newProjectStructureVersion(i, j), ProjectStructureVersion.newProjectStructureVersion(i, j - 1));
            }
        }
    }

    @Test
    public void testToVersionString()
    {
        for (int i = 0; i < 10; i++)
        {
            Assert.assertEquals(Integer.toString(i), ProjectStructureVersion.newProjectStructureVersion(i).toVersionString());
            Assert.assertEquals(Integer.toString(i), ProjectStructureVersion.newProjectStructureVersion(i).toVersionString('-'));
            for (int j = 0; j < 10; j++)
            {
                Assert.assertEquals(i + "." + j, ProjectStructureVersion.newProjectStructureVersion(i, j).toVersionString());
                Assert.assertEquals(i + "-" + j, ProjectStructureVersion.newProjectStructureVersion(i, j).toVersionString('-'));
            }
        }
    }
}
