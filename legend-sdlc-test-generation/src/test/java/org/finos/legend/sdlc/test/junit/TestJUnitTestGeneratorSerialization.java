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

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.SortedMaps;
import org.eclipse.collections.impl.utility.ArrayIterate;
import org.finos.legend.sdlc.serialization.EntityLoader;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.SortedMap;
import java.util.stream.Stream;

public class TestJUnitTestGeneratorSerialization extends AbstractGenerationTest
{
    @ClassRule
    public static final TemporaryFolder TMP_DIR = new TemporaryFolder();

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
    public void testWriteTestClassesWithoutRootPackage() throws IOException
    {
        testWriteTestClasses(null, "generated/java/execution/TestRelationalMapping.java",
                "generated/java/execution/TestRelationalMapping.java",
                "generated/java/legend/demo/TestSingleQuoteInResultM2M.java",
                "generated/java/model/mapping/TestSourceToTargetM2M.java",
                "generated/java/testTestSuites/TestTestService.java",
                "generated/java/testTestSuites/TestTestService2.java",
                "generated/java/testTestSuites/TestServiceStoreMapping.java",
                "generated/java/testTestSuites/TestMyServiceIsVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVery_38d23576.java",
                "generated/java/testTestSuites/TestMyServiceIsââVeryââVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryââââVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryV_a0bb3f4b.java",
                "generated/java/model/domain/TestFunctionTest__String_1_.java");
    }

    @Test
    public void testWriteTestClasses() throws IOException
    {
        testWriteTestClasses("org.finos.legend.sdlc.test.junit.junit4",
                "generated/java/org/finos/legend/sdlc/test/junit/junit4/execution/TestRelationalMapping.java",
                "generated/java/org/finos/legend/sdlc/test/junit/junit4/legend/demo/TestSingleQuoteInResultM2M.java",
                "generated/java/org/finos/legend/sdlc/test/junit/junit4/model/mapping/TestSourceToTargetM2M.java",
                "generated/java/org/finos/legend/sdlc/test/junit/junit4/testTestSuites/TestTestService.java",
                "generated/java/org/finos/legend/sdlc/test/junit/junit4/testTestSuites/TestTestService2.java",
                "generated/java/org/finos/legend/sdlc/test/junit/junit4/testTestSuites/TestServiceStoreMapping.java",
                "generated/java/org/finos/legend/sdlc/test/junit/junit4/testTestSuites/TestMyServiceIsVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVery_38d23576.java",
                "generated/java/org/finos/legend/sdlc/test/junit/junit4/testTestSuites/TestMyServiceIsââVeryââVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryââââVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryV_a0bb3f4b.java",
                "generated/java/org/finos/legend/sdlc/test/junit/junit4/model/domain/TestFunctionTest__String_1_.java");
    }

    @Test
    public void testWriteTestClassesOtherPackage() throws IOException
    {
        testWriteTestClasses("other.test.pkg",
                "generated/java/other/test/pkg/execution/TestRelationalMapping.java",
                "generated/java/other/test/pkg/legend/demo/TestSingleQuoteInResultM2M.java",
                "generated/java/other/test/pkg/model/mapping/TestSourceToTargetM2M.java",
                "generated/java/other/test/pkg/testTestSuites/TestTestService.java",
                "generated/java/other/test/pkg/testTestSuites/TestTestService2.java",
                "generated/java/other/test/pkg/testTestSuites/TestServiceStoreMapping.java",
                "generated/java/other/test/pkg/testTestSuites/TestMyServiceIsVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVery_38d23576.java",
                "generated/java/other/test/pkg/testTestSuites/TestMyServiceIsââVeryââVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryââââVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryV_a0bb3f4b.java",
                "generated/java/other/test/pkg/model/domain/TestFunctionTest__String_1_.java");
    }

    private void testWriteTestClasses(String rootPackage, String... expectedResources) throws IOException
    {
        // Prepare expected
        SortedMap<String, String> expected = SortedMaps.mutable.empty();
        ArrayIterate.forEach(expectedResources, resourceName ->
        {
            String relativePath = resourceName.substring("generated/java/".length());
            String text = loadTextResource(resourceName);
            expected.put(relativePath, text);
        });

        // Generate
        JUnitTestGenerator generator = JUnitTestGenerator.newGenerator(rootPackage);
        Path outputDir = TMP_DIR.newFolder().toPath();
        List<Path> reportedPaths = generator.writeTestClasses(outputDir, ENTITY_LOADER.getAllEntities());
        List<Path> foundPaths = Lists.mutable.empty();
        SortedMap<String, String> actual = SortedMaps.mutable.empty();
        try (Stream<Path> stream = Files.walk(outputDir))
        {
            stream.forEach(path ->
            {
                try
                {
                    if (Files.isRegularFile(path))
                    {
                        foundPaths.add(path);
                        String relativePath = outputDir.relativize(path).toString().replace(path.getFileSystem().getSeparator(), "/");
                        String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                        Assert.assertTrue("Generated file name exceeds 255 byte limit", path.getFileName().toString().getBytes(StandardCharsets.UTF_8).length < 255);
                        actual.put(relativePath, text);
                    }
                }
                catch (IOException e)
                {
                    throw new UncheckedIOException(e);
                }
            });
        }

        // Assert we found what we expected
        Assert.assertEquals(String.valueOf(rootPackage), expected, actual);

        // Assert the set of reported paths is accurate
        reportedPaths.sort(Comparator.naturalOrder());
        foundPaths.sort(Comparator.naturalOrder());
        Assert.assertEquals(reportedPaths, foundPaths);
    }
}
