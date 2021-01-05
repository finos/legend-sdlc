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

package org.finos.legend.sdlc.serialization;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Collections;
import java.util.stream.Collectors;

public class TestEmptyEntityLoader
{
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testEntityLoaderNullPathArray()
    {
        testEmptyEntityLoader(EntityLoader.newEntityLoader((Path[])null));
    }

    @Test
    public void testEntityLoaderEmptyPathArray()
    {
        testEmptyEntityLoader(EntityLoader.newEntityLoader(new Path[0]));
    }

    @Test
    public void testEntityLoaderNullFileArray()
    {
        testEmptyEntityLoader(EntityLoader.newEntityLoader((File[])null));
    }

    @Test
    public void testEntityLoaderEmptyFileArray()
    {
        testEmptyEntityLoader(EntityLoader.newEntityLoader(new File[0]));
    }

    @Test
    public void testEmptyDirectories() throws IOException
    {
        testEmptyEntityLoader(EntityLoader.newEntityLoader(this.tempFolder.newFolder(), this.tempFolder.newFolder()));
    }

    @Test
    public void testNonExistentDirectories()
    {
        Path rootDir = this.tempFolder.getRoot().toPath();
        testEmptyEntityLoader(EntityLoader.newEntityLoader(rootDir.resolve("do"), rootDir.resolve("not"), rootDir.resolve("exist")));
    }

    @Test
    public void testClassLoaderWithNoEntities()
    {
        testEmptyEntityLoader(EntityLoader.newEntityLoader(new URLClassLoader(new URL[0])));
    }

    private void testEmptyEntityLoader(EntityLoader entityLoader)
    {
        Assert.assertEquals(Collections.emptyList(), entityLoader.getAllEntities().collect(Collectors.toList()));
        Assert.assertNull(entityLoader.getEntity("some::nonexistent::Entity"));
        Assert.assertEquals(Collections.emptyList(), entityLoader.getEntitiesInPackage("some::nonexistent::package").collect(Collectors.toList()));
    }
}
