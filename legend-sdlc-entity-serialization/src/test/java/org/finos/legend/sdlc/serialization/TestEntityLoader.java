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

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.finos.legend.sdlc.domain.model.TestTools;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

abstract class TestEntityLoader
{
    private List<Entity> testEntities;
    private EntityLoader entityLoader;
    private final List<AutoCloseable> closeables = Lists.mutable.empty();

    @Before
    public void setUp() throws IOException
    {
        this.testEntities = getTestEntities();
        Map<String, byte[]> filesByPath = Maps.mutable.ofInitialCapacity(this.testEntities.size());
        EntitySerializer entitySerializer = EntitySerializers.getDefaultJsonSerializer();
        for (Entity entity : this.testEntities)
        {
            String relativeFilePath = "entities/" + entity.getPath().replaceAll("::", "/") + ".json";
            byte[] fileContent = entitySerializer.serializeToBytes(entity);
            filesByPath.put(relativeFilePath, fileContent);
        }
        this.entityLoader = createEntityLoaderFromFiles(filesByPath);
    }

    @After
    public void tearDown() throws Exception
    {
        Exception exception = null;
        try
        {
            this.entityLoader.close();
        }
        catch (Exception e)
        {
            exception = e;
        }
        for (AutoCloseable closeable : this.closeables)
        {
            try
            {
                closeable.close();
            }
            catch (Exception e)
            {
                if (exception == null)
                {
                    exception = e;
                }
                else
                {
                    exception.addSuppressed(e);
                }
            }
        }
        if (exception != null)
        {
            throw exception;
        }
    }

    @Test
    public void testGetEntity()
    {
        // Test that we can get all the expected entities
        for (Entity entity : this.testEntities)
        {
            Entity loadedEntity = this.entityLoader.getEntity(entity.getPath());
            Assert.assertNotNull(entity.getPath(), loadedEntity);
            Assert.assertEquals(entity.getPath(), entity.getPath(), loadedEntity.getPath());
            Assert.assertEquals(entity.getPath(), entity.getClassifierPath(), loadedEntity.getClassifierPath());
            Assert.assertEquals(entity.getPath(), entity.getContent(), loadedEntity.getContent());
        }

        // Test that we can't load non-existent entities
        Entity loadedEntity = this.entityLoader.getEntity("not::an::Entity");
        Assert.assertNull(loadedEntity);
    }

    @Test
    public void testGetAllEntities()
    {
        List<Entity> loadedEntities = this.entityLoader.getAllEntities().collect(Collectors.toList());
        TestTools.assertEntitiesEquivalent(this.testEntities, loadedEntities);
    }

    @Test
    public void testGetEntitiesInPackage()
    {
        Map<String, List<Entity>> entitiesByPackage = Maps.mutable.empty();
        for (Entity entity : this.testEntities)
        {
            String path = entity.getPath();
            for (int index = path.indexOf(':'); index != -1; index = path.indexOf(':', index + 2))
            {
                entitiesByPackage.computeIfAbsent(path.substring(0, index), k -> Lists.mutable.empty()).add(entity);
            }
        }

        entitiesByPackage.forEach((pkg, expectedEntities) ->
        {
            List<Entity> pkgEntities = this.entityLoader.getEntitiesInPackage(pkg).collect(Collectors.toList());
            TestTools.assertEntitiesEquivalent(pkg, expectedEntities, pkgEntities);
        });

        List<Entity> nonExistentPkgEntities = this.entityLoader.getEntitiesInPackage("non::existent::package").collect(Collectors.toList());
        Assert.assertEquals(Collections.emptyList(), nonExistentPkgEntities);
    }

    protected abstract EntityLoader createEntityLoaderFromFiles(Map<String, byte[]> fileContentByPath) throws IOException;

    protected void registerCloseable(AutoCloseable closeable)
    {
        this.closeables.add(closeable);
    }

    private List<Entity> getTestEntities()
    {
        return Arrays.asList(
                TestTools.newClassEntity("EmptyClass", "model::domain::test::empty"),
                TestTools.newClassEntity("EmptyClass2", "model::domain::test::empty"),
                TestTools.newClassEntity("ClassWith1Property", "model::domain::test::notEmpty", TestTools.newProperty("prop1", "String", 0, 1)),
                TestTools.newClassEntity("ClassWith2Properties", "model::domain::test::notEmpty", Arrays.asList(TestTools.newProperty("prop2", "Integer", 1, 1), TestTools.newProperty("prop3", "Date", 0, 1))),
                TestTools.newEnumerationEntity("MusicGenre", "model::domain::test::enums", "CLASSICAL", "DIXIELAND", "COUNTRY", "WESTERN_SWING", "HOKUM", "GOSPEL"),
                TestTools.newEnumerationEntity("ArtMovements", "model::domain::test::enums", "BAUHAUS", "REALISM", "FLUXUS", "IMPRESSIONISM", "NATURALISM")
        );
    }
}
