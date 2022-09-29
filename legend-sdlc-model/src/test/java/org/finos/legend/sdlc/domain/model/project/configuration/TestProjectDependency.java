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

import java.util.Comparator;

public class TestProjectDependency
{
    @Test
    public void testEquals()
    {
        ProjectDependency testProject123 = ProjectDependency.newProjectDependency("test-project", "1.2.3");
        Assert.assertEquals(testProject123, testProject123);
        Assert.assertEquals(testProject123, ProjectDependency.newProjectDependency("test-project", "1.2.3"));
        Assert.assertNotEquals(testProject123, ProjectDependency.newProjectDependency("other-project", "1.2.3"));
        Assert.assertNotEquals(testProject123, ProjectDependency.newProjectDependency("test-project", "1.2.4"));
    }

    @Test
    public void testToProjectDependencyString()
    {
        Assert.assertEquals("test-project:1.2.3", ProjectDependency.newProjectDependency("test-project", "1.2.3").toDependencyString());
        Assert.assertEquals("test-project:1.2.3", ProjectDependency.newProjectDependency("test-project","1.2.3").toDependencyString(':'));
        Assert.assertEquals("test-project/1.2.3", ProjectDependency.newProjectDependency("test-project", "1.2.3").toDependencyString('/'));
        Assert.assertEquals("other-project_0.0.1", ProjectDependency.newProjectDependency("other-project", "0.0.1").toDependencyString('_'));
    }

    @Test
    public void testParseProjectDependency()
    {
        // backward compatibility
        Assert.assertEquals(ProjectDependency.newProjectDependency("test-project", "1.2.3"), ProjectDependency.parseProjectDependency("test-project:1.2.3"));
        Assert.assertEquals(ProjectDependency.newProjectDependency("test-project", "1.2.3"), ProjectDependency.parseProjectDependency("test-project:1.2.3", ':'));
        Assert.assertEquals(ProjectDependency.newProjectDependency("test-project", "1.2.3"), ProjectDependency.parseProjectDependency("test-project/1.2.3", '/'));
        Assert.assertEquals(ProjectDependency.newProjectDependency("other-project", "0.0.1"), ProjectDependency.parseProjectDependency("other-project_0.0.1", '_'));

        //new way to store dependency
        Assert.assertEquals(ProjectDependency.newProjectDependency("org.finos.legend.sdlc.test:testproject0", "0.0.1"), ProjectDependency.parseProjectDependency("org.finos.legend.sdlc.test:testproject0:0.0.1"));
        Assert.assertEquals(ProjectDependency.newProjectDependency("org.finos.legend.sdlc.test:testproject0", "0.0.1"), ProjectDependency.parseProjectDependency("org.finos.legend.sdlc.test:testproject0_0.0.1", '_'));
        Assert.assertEquals(ProjectDependency.newProjectDependency("org.finos.legend.sdlc.test:testproject0", "0.0.1"), ProjectDependency.parseProjectDependency("org.finos.legend.sdlc.test:testproject0/0.0.1", '/'));
    }

    @Test
    public void testDefaultComparator()
    {
        Comparator<ProjectDependency> comparator = ProjectDependency.getDefaultComparator();
        ProjectDependency testProject123 = ProjectDependency.newProjectDependency("test-project", "1.2.3");

        TestTools.assertCompareTo(0, testProject123, testProject123, comparator);
        TestTools.assertCompareTo(0, testProject123, ProjectDependency.newProjectDependency("test-project", "1.2.3"), comparator);

        TestTools.assertCompareTo(1, testProject123, ProjectDependency.newProjectDependency("test-project", "1.2.2"), comparator);
        TestTools.assertCompareTo(1, testProject123, ProjectDependency.newProjectDependency("other-project", "1.2.2"), comparator);
        TestTools.assertCompareTo(1, testProject123, ProjectDependency.newProjectDependency("other-project", "1.2.3"), comparator);
        TestTools.assertCompareTo(1, testProject123, ProjectDependency.newProjectDependency("other-project", "9.10.11"), comparator);

        TestTools.assertCompareTo(-1, testProject123, ProjectDependency.newProjectDependency("test-project", "2.0.0"), comparator);
        TestTools.assertCompareTo(-1, testProject123, ProjectDependency.newProjectDependency("zest-project", "1.2.3"), comparator);

        TestTools.assertCompareTo(-1, testProject123, ProjectDependency.newProjectDependency(null, "1.2.3"), comparator);
        TestTools.assertCompareTo(-1, testProject123, ProjectDependency.newProjectDependency("test-project", null), comparator);
    }
}
