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

package org.finos.legend.sdlc.project.structure;

import org.finos.legend.sdlc.domain.model.project.configuration.ProjectStructureVersion;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Pins the default-factory contract established when the re-architecture section 4.5 audit was discharged (Phase 6):
 * the default is a lazily materialized immutable snapshot of the version factories on this class's own classpath,
 * shared across callers and safe to reach from many threads, and it does not depend on the thread context
 * classloader.
 */
public class TestDefaultProjectStructureFactory
{
    @Test
    public void testDefaultFactoryIsSharedAndComplete()
    {
        ProjectStructureFactory factory = ProjectStructureFactory.getDefaultFactory();
        Assert.assertSame(factory, ProjectStructureFactory.getDefaultFactory());
        Assert.assertSame(factory, ProjectStructure.getDefaultProjectStructureFactory());
        Assert.assertEquals(13, factory.getLatestVersion());
        for (int version : new int[]{0, 11, 12, 13})
        {
            Assert.assertTrue("expected support for version " + version, factory.supportsVersion(version));
            Assert.assertNotNull(factory.getVersionFactory(version));
        }
    }

    @Test
    public void testDefaultFactoryIgnoresContextClassLoader()
    {
        Thread currentThread = Thread.currentThread();
        ClassLoader previous = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(null);
        try
        {
            ProjectStructureFactory factory = ProjectStructureFactory.getDefaultFactory();
            Assert.assertEquals(13, factory.getLatestVersion());
            Assert.assertNotNull(ProjectStructure.getProjectStructure(SimpleProjectConfiguration.newConfiguration(
                    "test-project", ProjectStructureVersion.newProjectStructureVersion(13), "org.test", "test-artifact", null, null, null)));
        }
        finally
        {
            currentThread.setContextClassLoader(previous);
        }
    }

    @Test
    public void testDefaultFactoryFromManyThreads() throws Exception
    {
        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try
        {
            List<Callable<ProjectStructureFactory>> tasks = new ArrayList<>(threadCount);
            for (int i = 0; i < threadCount; i++)
            {
                tasks.add(ProjectStructureFactory::getDefaultFactory);
            }
            ProjectStructureFactory expected = null;
            for (Future<ProjectStructureFactory> future : executor.invokeAll(tasks))
            {
                ProjectStructureFactory factory = future.get();
                if (expected == null)
                {
                    expected = factory;
                }
                else
                {
                    Assert.assertSame(expected, factory);
                }
            }
        }
        finally
        {
            executor.shutdownNow();
        }
    }
}
