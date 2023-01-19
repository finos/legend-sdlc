// Copyright 2021 Goldman Sachs
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

package org.finos.legend.sdlc.versions;

import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.serialization.EntityLoader;
import org.finos.legend.sdlc.tools.entity.EntityPaths;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TestEntityPathTransformer
{
    @Test
    public void testEmptyTransformer()
    {
        EntityPathTransformer transformer = EntityPathTransformer.newTransformer(Function.identity());
        Assert.assertEquals(0, transformer.getEntityCount());
        Assert.assertEquals(Collections.emptyList(), transformer.getEntities());
        Assert.assertEquals(Collections.emptyList(), transformer.transformEntities());
    }

    @Test
    public void testAddEntity() throws Exception
    {
        List<Entity> entities;
        try  (EntityLoader entityLoader = EntityLoader.newEntityLoader(getTestResourcesDirectory()))
        {
            entities = entityLoader.getAllEntities().collect(Collectors.toList());
        }
        Assert.assertFalse(entities.isEmpty());

        EntityPathTransformer transformer = EntityPathTransformer.newTransformer(Function.identity());
        Assert.assertEquals(0, transformer.getEntityCount());
        Assert.assertEquals(Collections.emptyList(), transformer.getEntities());
        for (int i = 0; i < entities.size(); i++)
        {
            transformer.addEntity(entities.get(i));
            Assert.assertEquals(i + 1, transformer.getEntityCount());
            Assert.assertEquals(entities.subList(0, i + 1), transformer.getEntities());
        }
    }

    @Test
    public void testAddEntities() throws Exception
    {
        List<Entity> entities;
        try (EntityLoader entityLoader = EntityLoader.newEntityLoader(getTestResourcesDirectory()))
        {
            entities = entityLoader.getAllEntities().collect(Collectors.toList());
        }
        Assert.assertFalse(entities.isEmpty());

        EntityPathTransformer transformer = EntityPathTransformer.newTransformer(Function.identity());
        Assert.assertEquals(0, transformer.getEntityCount());
        Assert.assertEquals(Collections.emptyList(), transformer.getEntities());
        transformer.addEntities(entities);
        Assert.assertEquals(entities.size(), transformer.getEntityCount());
        Assert.assertEquals(entities, transformer.getEntities());
    }

    @Test
    public void testIdentityTransformation() throws Exception
    {
        List<Entity> transformedEntities;
        List<Entity> expectedEntities;
        try (EntityLoader entityLoader = EntityLoader.newEntityLoader(getTestResourcesDirectory()))
        {

            EntityPathTransformer transformer = EntityPathTransformer.newTransformer(Function.identity());
            transformer.addEntities(entityLoader.getAllEntities());
            transformedEntities = transformer.transformEntities();
            expectedEntities = entityLoader.getAllEntities().collect(Collectors.toList());
        }
        EntityTransformationTestTools.assertEntitiesEquivalent(expectedEntities, transformedEntities);
    }

    @Test
    public void testPrefixTransformation() throws Exception
    {
        List<Entity> entities;
        try (EntityLoader entityLoader = EntityLoader.newEntityLoader(getTestResourcesDirectory()))
        {
            entities = entityLoader.getAllEntities().collect(Collectors.toList());
        }

        String prefix = "test::v1_2_3::";
        EntityPathTransformer transformer = EntityPathTransformer.newTransformer(prefix::concat);
        transformer.addEntities(entities);
        List<Entity> transformedEntities = transformer.transformEntities();

        List<Entity> expectedEntities = EntityTransformationTestTools.transformEntities(entities, prefix::concat);
        EntityTransformationTestTools.assertEntitiesEquivalent(expectedEntities, transformedEntities);
    }

    @Test
    public void testReversePackageTransformation() throws Exception
    {
        List<Entity> entities;
        try (EntityLoader entityLoader = EntityLoader.newEntityLoader(getTestResourcesDirectory()))
        {
            entities = entityLoader.getAllEntities().collect(Collectors.toList());
        }

        Function<String, String> pathTransform = p ->
        {
            StringBuilder newPath = new StringBuilder(p.length());
            String[] elts = p.split(EntityPaths.PACKAGE_SEPARATOR);
            for (int i = elts.length - 2; i >= 0; i--)
            {
                newPath.append(elts[i]).append(EntityPaths.PACKAGE_SEPARATOR);
            }
            return newPath.append(elts[elts.length - 1]).toString();
        };
        EntityPathTransformer transformer = EntityPathTransformer.newTransformer(pathTransform);
        transformer.addEntities(entities);
        List<Entity> transformedEntities = transformer.transformEntities();
        transformedEntities.sort(Comparator.comparing(Entity::getPath));

        List<Entity> expectedEntities = EntityTransformationTestTools.transformEntities(entities, pathTransform);
        expectedEntities.sort(Comparator.comparing(Entity::getPath));
        EntityTransformationTestTools.assertEntitiesEquivalent(expectedEntities, transformedEntities);
    }

    @Test
    public void testInvalidTransformation() throws Exception
    {
        Entity oneEntity;
        try (EntityLoader entityLoader = EntityLoader.newEntityLoader(getTestResourcesDirectory()))
        {
            oneEntity = entityLoader.getAllEntities().findAny().get();
        }

        String invalidPrefix = "test::v1.2.3::";
        try
        {
            EntityPathTransformer.newTransformer(invalidPrefix::concat).addEntity(oneEntity).transformEntities();
            Assert.fail("expected exception");
        }
        catch (RuntimeException e)
        {
            Assert.assertEquals("Invalid transformation for \"" + oneEntity.getPath() + "\": \"" + invalidPrefix + oneEntity.getPath() + "\"", e.getMessage());
        }

        try
        {
            EntityPathTransformer.newTransformer(s -> null).addEntity(oneEntity).transformEntities();
            Assert.fail("expected exception");
        }
        catch (RuntimeException e)
        {
            Assert.assertEquals("Invalid transformation for \"" + oneEntity.getPath() + "\": null", e.getMessage());
        }

        try
        {
            EntityPathTransformer.newTransformer(s -> "null").addEntity(oneEntity).transformEntities();
            Assert.fail("expected exception");
        }
        catch (RuntimeException e)
        {
            Assert.assertEquals("Invalid transformation for \"" + oneEntity.getPath() + "\": \"null\"", e.getMessage());
        }
    }

    private Path getTestResourcesDirectory()
    {
        return EntityTransformationTestTools.getResource(getClass().getClassLoader(), "entity-path-transformer-test-resources");
    }
}
