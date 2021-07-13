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

import org.eclipse.collections.api.IntIterable;
import org.eclipse.collections.api.LazyIntIterable;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

public class TestProjectStructureFactory
{
    @Test
    public void testFactoryWithV0_V11()
    {
        assertFactoryWithV0(ProjectStructureFactory.newFactory(Lists.mutable.with(new ProjectStructureV0Factory(), new ProjectStructureV11Factory())));
    }

    @Test
    public void testFactoryServiceLoaderThisClassLoader()
    {
        testFactoryFromServiceLoader(getClass().getClassLoader());
    }

    @Test
    public void testFactoryServiceLoaderProjectStructureClassLoader()
    {
        testFactoryFromServiceLoader(ProjectStructure.class.getClassLoader());
    }

    protected void testFactoryFromServiceLoader(ClassLoader classLoader)
    {
        testFactoryFromServiceLoader(ProjectStructureFactory.newFactory(classLoader));
    }

    protected void testFactoryFromServiceLoader(ProjectStructureFactory factory)
    {
        assertFactoryWithV0(factory);
    }

    protected void assertSupportsVersions(ProjectStructureFactory factory, int... versions)
    {
        assertSupportsVersions(factory, IntLists.mutable.with(versions));
    }

    protected void assertSupportsVersions(ProjectStructureFactory factory, IntIterable versions)
    {
        LazyIntIterable unsupported = versions.asLazy().reject(factory::supportsVersion);
        if (unsupported.notEmpty())
        {
            Assert.fail(unsupported.toSortedList().makeString("Unsupported versions: ", ", ", ""));
        }
        Assert.assertEquals(versions.max(), factory.getLatestVersion());
    }

    private void assertFactoryWithV0(ProjectStructureFactory factory)
    {
        assertSupportsVersions(factory, 0, 11);

        ProjectStructure structure = factory.newProjectStructure(null, null);
        Assert.assertEquals(0, structure.getVersion());
        Assert.assertTrue(structure instanceof ProjectStructureV0Factory.ProjectStructureV0);
    }
}
