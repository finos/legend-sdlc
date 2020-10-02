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

public class TestMetamodelDependency
{
    @Test
    public void testEquals()
    {
        MetamodelDependency testMetamodel1 = MetamodelDependency.newMetamodelDependency("test-metamodel", 1);
        Assert.assertEquals(testMetamodel1, testMetamodel1);
        Assert.assertEquals(testMetamodel1, MetamodelDependency.newMetamodelDependency("test-metamodel", 1));
        Assert.assertNotEquals(testMetamodel1, MetamodelDependency.newMetamodelDependency("other-metamodel", 1));
        Assert.assertNotEquals(testMetamodel1, MetamodelDependency.newMetamodelDependency("test-metamodel", 2));
    }

    @Test
    public void testCompareTo()
    {
        MetamodelDependency testMetamodel1 = MetamodelDependency.newMetamodelDependency("test-metamodel", 1);

        TestTools.assertCompareTo(0, testMetamodel1, testMetamodel1);
        TestTools.assertCompareTo(0, testMetamodel1, MetamodelDependency.newMetamodelDependency("test-metamodel", 1));

        TestTools.assertCompareTo(1, testMetamodel1, MetamodelDependency.newMetamodelDependency("test-metamodel", 0));
        TestTools.assertCompareTo(1, testMetamodel1, MetamodelDependency.newMetamodelDependency("other-metamodel", 0));
        TestTools.assertCompareTo(1, testMetamodel1, MetamodelDependency.newMetamodelDependency("other-metamodel", 1));
        TestTools.assertCompareTo(1, testMetamodel1, MetamodelDependency.newMetamodelDependency("other-metamodel", 2));

        TestTools.assertCompareTo(-1, testMetamodel1, MetamodelDependency.newMetamodelDependency("test-metamodel", 2));
        TestTools.assertCompareTo(-1, testMetamodel1, MetamodelDependency.newMetamodelDependency("zest-metamodel", 1));
    }

    @Test
    public void testToMetamodelDependencyString()
    {
        Assert.assertEquals("test-metamodel:1", MetamodelDependency.newMetamodelDependency("test-metamodel", 1).toDependencyString());
        Assert.assertEquals("test-metamodel:1", MetamodelDependency.newMetamodelDependency("test-metamodel", 1).toDependencyString(':'));
        Assert.assertEquals("test-metamodel/1", MetamodelDependency.newMetamodelDependency("test-metamodel", 1).toDependencyString('/'));
        Assert.assertEquals("other-metamodel_0", MetamodelDependency.newMetamodelDependency("other-metamodel", 0).toDependencyString('_'));
    }

    @Test
    public void testParseMetamodelDependency()
    {
        Assert.assertEquals(MetamodelDependency.newMetamodelDependency("test-metamodel", 1), MetamodelDependency.parseMetamodelDependency("test-metamodel:1"));
        Assert.assertEquals(MetamodelDependency.newMetamodelDependency("test-metamodel", 1), MetamodelDependency.parseMetamodelDependency("test-metamodel:1", ':'));
        Assert.assertEquals(MetamodelDependency.newMetamodelDependency("test-metamodel", 1), MetamodelDependency.parseMetamodelDependency("test-metamodel/1", '/'));
        Assert.assertEquals(MetamodelDependency.newMetamodelDependency("other-metamodel", 0), MetamodelDependency.parseMetamodelDependency("other-metamodel_0", '_'));
    }
}
