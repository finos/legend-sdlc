// Copyright 2023 Goldman Sachs
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

package org.finos.legend.sdlc.test.junit;

import org.finos.legend.engine.protocol.pure.m3.PackageableElement;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.generation.GeneratedJavaCode;
import org.finos.legend.sdlc.protocol.pure.v1.EntityToPureConverter;
import org.finos.legend.sdlc.serialization.EntityLoader;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;

abstract class AbstractTestClassGeneratorTest<T extends PackageableElement, G extends AbstractTestClassGenerator> extends AbstractGenerationTest
{
    private static EntityToPureConverter ENTITY_CONVERTER;

    @BeforeClass
    public static void setUp()
    {
        ENTITY_CONVERTER = new EntityToPureConverter();
    }

    @AfterClass
    public static void tearDown()
    {
        ENTITY_CONVERTER = null;
    }

    @Test
    public void testInvalidPackagePrefix()
    {
        for (String invalidPackage : Arrays.asList("", "not a valid package", "abc.def.123", "abc.def.h#j", "abc.de+.f", "abc.", ".", ".abc.def", "abc..def", "other.test.package", "some.class.pkg"))
        {
            IllegalArgumentException e = Assert.assertThrows(IllegalArgumentException.class, () -> newGenerator(invalidPackage));
            Assert.assertEquals("Invalid package name: \"" + invalidPackage + "\"", e.getMessage());
        }
    }

    protected void testGeneration(String expectedClassName, String expectedCodeResource, String packagePrefix, String entityPath)
    {
        T element = loadElement(entityPath);
        GeneratedJavaCode generated = withElement(newGenerator(packagePrefix), element).generate();
        assertGeneratedJavaCode(expectedClassName, loadTextResource(expectedCodeResource), generated);
    }

    protected abstract G newGenerator(String packagePrefix);

    protected abstract G withElement(G generator, T element);

    @SuppressWarnings("unchecked")
    protected T loadElement(String path)
    {
        return (T) ENTITY_CONVERTER.fromEntity(loadEntity(path));
    }

    private Entity loadEntity(String path)
    {
        Entity entity;
        try (EntityLoader entityLoader = EntityLoader.newEntityLoader(Thread.currentThread().getContextClassLoader()))
        {
            entity = entityLoader.getEntity(path);
        }
        catch (Exception e)
        {
            throw new RuntimeException("Error loading entity: " + path, e);
        }
        if (entity == null)
        {
            throw new RuntimeException("Could not find entity: " + path);
        }
        return entity;
    }
}
