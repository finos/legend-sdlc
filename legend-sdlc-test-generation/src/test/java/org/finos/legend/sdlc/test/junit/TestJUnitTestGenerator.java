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
                "execution.TestRelationalMapping",
                "generated/java/execution/TestRelationalMapping.java",
                null,
                "execution::RelationalMapping");
        testGeneration(
                "org.finos.legend.sdlc.test.junit.junit4.execution.TestRelationalMapping",
                "generated/java/org/finos/legend/sdlc/test/junit/junit4/execution/TestRelationalMapping.java",
                "org.finos.legend.sdlc.test.junit.junit4",
                "execution::RelationalMapping");
        testGeneration(
                "other.test.pkg.execution.TestRelationalMapping",
                "generated/java/other/test/pkg/execution/TestRelationalMapping.java",
                "other.test.pkg",
                "execution::RelationalMapping");
    }

    @Test
    public void testSingleQuoteInResultM2M()
    {
        testGeneration("legend.demo.TestSingleQuoteInResultM2M",
                "generated/java/legend/demo/TestSingleQuoteInResultM2M.java",
                null,
                "legend::demo::SingleQuoteInResultM2M");
        testGeneration("org.finos.legend.sdlc.test.junit.junit4.legend.demo.TestSingleQuoteInResultM2M",
                "generated/java/org/finos/legend/sdlc/test/junit/junit4/legend/demo/TestSingleQuoteInResultM2M.java",
                "org.finos.legend.sdlc.test.junit.junit4",
                "legend::demo::SingleQuoteInResultM2M");
        testGeneration("other.test.pkg.legend.demo.TestSingleQuoteInResultM2M",
                "generated/java/other/test/pkg/legend/demo/TestSingleQuoteInResultM2M.java",
                "other.test.pkg",
                "legend::demo::SingleQuoteInResultM2M");
    }

    @Test
    public void testSourceToTargetM2M()
    {
        testGeneration("model.mapping.TestSourceToTargetM2M",
                "generated/java/model/mapping/TestSourceToTargetM2M.java",
                null,
                "model::mapping::SourceToTargetM2M");
        testGeneration("org.finos.legend.sdlc.test.junit.junit4.model.mapping.TestSourceToTargetM2M",
                "generated/java/org/finos/legend/sdlc/test/junit/junit4/model/mapping/TestSourceToTargetM2M.java",
                "org.finos.legend.sdlc.test.junit.junit4",
                "model::mapping::SourceToTargetM2M");
        testGeneration("other.test.pkg.model.mapping.TestSourceToTargetM2M",
                "generated/java/other/test/pkg/model/mapping/TestSourceToTargetM2M.java",
                "other.test.pkg",
                "model::mapping::SourceToTargetM2M");
    }

    @Test
    public void testTestService()
    {
        testGeneration(
                "testTestSuites.TestTestService",
                "generated/java/testTestSuites/TestTestService.java",
                null,
                "testTestSuites::TestService");
        testGeneration(
                "org.finos.legend.sdlc.test.junit.junit4.testTestSuites.TestTestService",
                "generated/java/org/finos/legend/sdlc/test/junit/junit4/testTestSuites/TestTestService.java",
                "org.finos.legend.sdlc.test.junit.junit4",
                "testTestSuites::TestService");
        testGeneration(
                "other.test.pkg.testTestSuites.TestTestService",
                "generated/java/other/test/pkg/testTestSuites/TestTestService.java",
                "other.test.pkg",
                "testTestSuites::TestService");
    }

    @Test
    public void testTestService2()
    {
        testGeneration(
                "testTestSuites.TestTestService2",
                "generated/java/testTestSuites/TestTestService2.java",
                null,
                "testTestSuites::TestService2");
        testGeneration(
                "org.finos.legend.sdlc.test.junit.junit4.testTestSuites.TestTestService2",
                "generated/java/org/finos/legend/sdlc/test/junit/junit4/testTestSuites/TestTestService2.java",
                "org.finos.legend.sdlc.test.junit.junit4",
                "testTestSuites::TestService2");
        testGeneration(
                "other.test.pkg.testTestSuites.TestTestService2",
                "generated/java/other/test/pkg/testTestSuites/TestTestService2.java",
                "other.test.pkg",
                "testTestSuites::TestService2");
    }

    @Test
    public void testServiceStoreMapping()
    {
        testGeneration(
                "testTestSuites.TestServiceStoreMapping",
                "generated/java/testTestSuites/TestServiceStoreMapping.java",
                null,
                "testTestSuites::ServiceStoreMapping");
        testGeneration(
                "org.finos.legend.sdlc.test.junit.junit4.testTestSuites.TestServiceStoreMapping",
                "generated/java/org/finos/legend/sdlc/test/junit/junit4/testTestSuites/TestServiceStoreMapping.java",
                "org.finos.legend.sdlc.test.junit.junit4",
                "testTestSuites::ServiceStoreMapping");
        testGeneration(
                "other.test.pkg.testTestSuites.TestServiceStoreMapping",
                "generated/java/other/test/pkg/testTestSuites/TestServiceStoreMapping.java",
                "other.test.pkg",
                "testTestSuites::ServiceStoreMapping");
    }

    @Test
    public void testNonTestable()
    {
        testEmptyGeneration(null, "testTestSuites::ServiceStore");
        testEmptyGeneration("org.finos.legend.sdlc.test.junit.junit4", "testTestSuites::ServiceStore");
        testEmptyGeneration("other.test.pkg", "testTestSuites::ServiceStore");
    }

    private void testGeneration(String expectedClassName, String expectedCodeResource, String rootPackage, String entityPath)
    {
        Entity entity = ENTITY_LOADER.getEntity(entityPath);
        Assert.assertNotNull(entityPath, entity);
        String expectedCode = loadTextResource(expectedCodeResource);
        List<GeneratedJavaCode> generatedCode = JUnitTestGenerator.newGenerator(rootPackage).generateTestClasses(entity);
        if (generatedCode.size() == 1)
        {
            assertGeneratedJavaCode(expectedClassName, expectedCode, generatedCode.get(0));
        }
        else
        {
            Assert.assertEquals(Collections.singletonMap(expectedClassName, expectedCode), toJavaCodeMap(generatedCode));
        }
    }

    private void testGeneration(SortedMap<String, String> expectedCode, String rootPackage, String entityPath)
    {
        Entity entity = ENTITY_LOADER.getEntity(entityPath);
        List<GeneratedJavaCode> generatedCode = JUnitTestGenerator.newGenerator(rootPackage).generateTestClasses(entity);
        Assert.assertEquals(expectedCode, toJavaCodeMap(generatedCode));
    }

    private void testEmptyGeneration(String rootPackage, String entityPath)
    {
        testGeneration(Collections.emptySortedMap(), rootPackage, entityPath);
    }

    private SortedMap<String, String> toJavaCodeMap(Iterable<? extends GeneratedJavaCode> generatedJavaCode)
    {
        return Iterate.toMap(generatedJavaCode, GeneratedJavaCode::getClassName, GeneratedText::getText, SortedMaps.mutable.empty());
    }
}
