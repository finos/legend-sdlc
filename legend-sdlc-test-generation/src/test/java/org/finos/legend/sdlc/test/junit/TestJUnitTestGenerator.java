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

import org.eclipse.collections.api.factory.SortedMaps;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.generation.GeneratedJavaCode;
import org.finos.legend.sdlc.generation.GeneratedText;
import org.finos.legend.sdlc.serialization.EntityLoader;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;

public class TestJUnitTestGenerator extends AbstractGenerationTest
{
    private static EntityLoader ENTITY_LOADER;

    @BeforeClass
    public static void setUp()
    {
        ENTITY_LOADER = EntityLoader.newEntityLoader(Thread.currentThread().getContextClassLoader());
    }

    @AfterClass
    public static void cleanUp() throws Exception
    {
        if (ENTITY_LOADER != null)
        {
            ENTITY_LOADER.close();
            ENTITY_LOADER = null;
        }
    }

    @Test
    public void testInvalidRootPackage()
    {
        for (String invalidPackage : Arrays.asList("", "not a valid package", "abc.def.123", "abc.def.h#j", "abc.de+.f", "abc.", ".", ".abc.def", "abc..def", "other.test.package", "some.class.pkg"))
        {
            IllegalArgumentException e = Assert.assertThrows(IllegalArgumentException.class, () -> JUnitTestGenerator.newGenerator(invalidPackage));
            Assert.assertEquals("Invalid root package: \"" + invalidPackage + "\"", e.getMessage());
        }
    }

    @Test
    public void testRelationalMapping()
    {
        testGeneration(
                null,
                "execution::RelationalMapping",
                "generated/java/execution/TestRelationalMapping.java");
        testGeneration(
                "org.finos.legend.sdlc.test.junit.junit4",
                "execution::RelationalMapping",
                "generated/java/org/finos/legend/sdlc/test/junit/junit4/execution/TestRelationalMapping.java");
        testGeneration(
                "other.test.pkg",
                "execution::RelationalMapping",
                "generated/java/other/test/pkg/execution/TestRelationalMapping.java");
    }

    @Test
    public void testSingleQuoteInResultM2M()
    {
        testGeneration(
                null,
                "legend::demo::SingleQuoteInResultM2M",
                "generated/java/legend/demo/TestSingleQuoteInResultM2M.java");
        testGeneration(
                "org.finos.legend.sdlc.test.junit.junit4",
                "legend::demo::SingleQuoteInResultM2M",
                "generated/java/org/finos/legend/sdlc/test/junit/junit4/legend/demo/TestSingleQuoteInResultM2M.java");
        testGeneration(
                "other.test.pkg",
                "legend::demo::SingleQuoteInResultM2M",
                "generated/java/other/test/pkg/legend/demo/TestSingleQuoteInResultM2M.java");
    }

    @Test
    public void testSourceToTargetM2M()
    {
        testGeneration(
                null,
                "model::mapping::SourceToTargetM2M",
                "generated/java/model/mapping/TestSourceToTargetM2M.java");
        testGeneration(
                "org.finos.legend.sdlc.test.junit.junit4",
                "model::mapping::SourceToTargetM2M",
                "generated/java/org/finos/legend/sdlc/test/junit/junit4/model/mapping/TestSourceToTargetM2M.java");
        testGeneration(
                "other.test.pkg",
                "model::mapping::SourceToTargetM2M",
                "generated/java/other/test/pkg/model/mapping/TestSourceToTargetM2M.java");
    }

    @Test
    public void testTestService()
    {
        testGeneration(
                null,
                "testTestSuites::TestService",
                "generated/java/testTestSuites/TestTestService.java");
        testGeneration(
                "org.finos.legend.sdlc.test.junit.junit4",
                "testTestSuites::TestService",
                "generated/java/org/finos/legend/sdlc/test/junit/junit4/testTestSuites/TestTestService.java");
        testGeneration(
                "other.test.pkg",
                "testTestSuites::TestService",
                "generated/java/other/test/pkg/testTestSuites/TestTestService.java");
    }

    @Test
    public void testTestService2()
    {
        testGeneration(
                null,
                "testTestSuites::TestService2",
                "generated/java/testTestSuites/TestTestService2.java");
        testGeneration(
                "org.finos.legend.sdlc.test.junit.junit4",
                "testTestSuites::TestService2",
                "generated/java/org/finos/legend/sdlc/test/junit/junit4/testTestSuites/TestTestService2.java");
        testGeneration(
                "other.test.pkg",
                "testTestSuites::TestService2",
                "generated/java/other/test/pkg/testTestSuites/TestTestService2.java");
    }

    @Test
    public void testServiceWithoutTests()
    {
        testEmptyGeneration(
                null,
                "testTestSuites::MyServiceHasNoTest");
        testEmptyGeneration(
                "org.finos.legend.sdlc.test.junit.junit4",
                "testTestSuites::MyServiceHasNoTest");
        testEmptyGeneration(
                "other.test.pkg",
                "testTestSuites::MyServiceHasNoTest");
    }

    @Test
    public void testMappingWithoutTests()
    {
        testEmptyGeneration(
                null,
                "model::mapping::public");
        testEmptyGeneration(
                "org.finos.legend.sdlc.test.junit.junit4",
                "model::mapping::public");
        testEmptyGeneration(
                "other.test.pkg",
                "model::mapping::public");
    }

    @Test
    public void testMappingWithTests()
    {
        testGeneration(
                null,
                "testTestSuites::ServiceStoreMapping",
                "generated/java/testTestSuites/TestServiceStoreMapping.java");
        testGeneration(
                "org.finos.legend.sdlc.test.junit.junit4",
                "testTestSuites::ServiceStoreMapping",
                "generated/java/org/finos/legend/sdlc/test/junit/junit4/testTestSuites/TestServiceStoreMapping.java");
        testGeneration(
                "other.test.pkg",
                "testTestSuites::ServiceStoreMapping",
                "generated/java/other/test/pkg/testTestSuites/TestServiceStoreMapping.java");
    }

    @Test
    public void testFunctionTest()
    {
        testGeneration(
                null,
                "model::domain::FunctionTest__String_1_",
                "generated/java/model/domain/TestFunctionTest__String_1_.java");
        testGeneration(
                "org.finos.legend.sdlc.test.junit.junit4",
                "model::domain::FunctionTest__String_1_",
                "generated/java/org/finos/legend/sdlc/test/junit/junit4/model/domain/TestFunctionTest__String_1_.java");
        testGeneration(
                "other.test.pkg",
                "model::domain::FunctionTest__String_1_",
                "generated/java/other/test/pkg/model/domain/TestFunctionTest__String_1_.java");
    }

    @Test
    public void testFunctionWithoutTest()
    {
        testEmptyGeneration(
                null,
                "model::domain::FunctionNoTest__String_$0_1$_");
        testEmptyGeneration(
                "org.finos.legend.sdlc.test.junit.junit4",
                "model::domain::FunctionNoTest__String_$0_1$_");
        testEmptyGeneration(
                "other.test.pkg",
                "model::domain::FunctionNoTest__String_$0_1$_");
    }



    @Test
    public void testNonTestable()
    {
        testEmptyGeneration(
                null,
                "testTestSuites::ServiceStore");
        testEmptyGeneration(
                "org.finos.legend.sdlc.test.junit.junit4",
                "testTestSuites::ServiceStore");
        testEmptyGeneration(
                "other.test.pkg",
                "testTestSuites::ServiceStore");
    }

    private void testEmptyGeneration(String rootPackage, String entityPath)
    {
        testGeneration(rootPackage, entityPath, null);
    }

    private void testGeneration(String rootPackage, String entityPath, String expectedCodeResource)
    {
        Entity entity = ENTITY_LOADER.getEntity(entityPath);
        Assert.assertNotNull(entityPath, entity);
        List<GeneratedJavaCode> generatedCode = JUnitTestGenerator.newGenerator(rootPackage).generateTestClasses(entity);

        if (expectedCodeResource == null)
        {
            Assert.assertEquals(0, generatedCode.size());
        }
        else
        {
            String expectedCode = loadTextResource(expectedCodeResource);
            String expectedClassName = expectedClassName(rootPackage, entityPath);

            Assert.assertEquals(1, generatedCode.size());
            assertGeneratedJavaCode(expectedClassName, expectedCode, generatedCode.get(0));
        }

    }

    private String expectedClassName(String rootPackage, String entityPath)
    {
        String entityPathModified = entityPath.replace("$", "");
        String[] parts = entityPathModified.split("::");
        StringBuilder classNameBuilder = new StringBuilder();

        for (int i = 0; i < parts.length; i++)
        {
            if (i > 0)
            {
                classNameBuilder.append(".");
            }
            if (i == parts.length - 1)
            {
                classNameBuilder.append("Test");
            }
            classNameBuilder.append(parts[i]);
        }
        return (rootPackage == null ? "" : rootPackage + ".") + classNameBuilder;
    }
}