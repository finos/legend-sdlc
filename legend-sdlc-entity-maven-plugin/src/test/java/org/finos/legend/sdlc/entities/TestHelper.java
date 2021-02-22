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

package org.finos.legend.sdlc.entities;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.MutableList;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.serialization.EntityLoader;
import org.finos.legend.sdlc.serialization.EntitySerializers;
import org.finos.legend.sdlc.serialization.EntityTextSerializer;
import org.junit.Assert;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

class TestHelper
{
    static Path getPathFromResource(String resourceName)
    {
        URL url = TestHelper.class.getClassLoader().getResource(resourceName);
        if (url == null)
        {
            throw new RuntimeException("Could not find: " + resourceName);
        }
        try
        {
            return Paths.get(url.toURI());
        }
        catch (URISyntaxException e)
        {
            throw new RuntimeException(e);
        }
    }

    static void copyResourceDirectoryTree(String resourceName, Path targetDir) throws IOException
    {
        copyResourceDirectoryTree(resourceName, targetDir, null);
    }

    static void copyResourceDirectoryTree(String resourceName, Path targetDir, Predicate<? super Path> fileFilter) throws IOException
    {
        Path resourcePath = TestHelper.getPathFromResource(resourceName);
        copyDirectoryTree(resourcePath, targetDir, fileFilter);
    }

    static void copyDirectoryTree(Path sourceDir, Path targetDir) throws IOException
    {
        copyDirectoryTree(sourceDir, targetDir, null);
    }

    static void copyDirectoryTree(Path sourceDir, Path targetDir, Predicate<? super Path> fileFilter) throws IOException
    {
        if (!Files.isDirectory(sourceDir))
        {
            throw new IllegalArgumentException("Not a directory: " + sourceDir);
        }
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>()
        {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException
            {
                Files.createDirectories(targetDir.resolve(sourceDir.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
            {
                if ((fileFilter == null) || fileFilter.test(file))
                {
                    Files.copy(file, targetDir.resolve(sourceDir.relativize(file)));
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    static Map<String, Entity> loadEntitiesFromResource(String resourceName)
    {
        Path directory = getPathFromResource(resourceName);
        if (!Files.isDirectory(directory))
        {
            throw new RuntimeException("Not a directory: " + directory);
        }
        return loadEntities(directory);
    }

    static Map<String, Entity> loadEntities(Path directory)
    {
        Map<String, Entity> entities = Maps.mutable.empty();
        try (EntityLoader entityLoader = EntityLoader.newEntityLoader(directory))
        {
            entityLoader.getAllEntities().forEach(e ->
            {
                Entity old = entities.put(e.getPath(), e);
                if (old != null)
                {
                    throw new RuntimeException("Conflict for entity path: " + e.getPath());
                }
            });
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
        return entities;
    }

    static void assertEntitiesByPathEqual(Map<String, Entity> expectedEntitiesByPath, Map<String, Entity> actualEntitiesByPath)
    {
        Assert.assertEquals(expectedEntitiesByPath.keySet(), actualEntitiesByPath.keySet());
        expectedEntitiesByPath.forEach((path, expectedEntity) ->
        {
            Entity actualEntity = actualEntitiesByPath.get(path);
            TestHelper.assertEntityEquals(expectedEntity, actualEntity);
        });

    }

    static void assertEntityEquals(Entity expected, Entity actual)
    {
        String expectedString;
        String actualString;
        try
        {
            EntityTextSerializer serializer = EntitySerializers.getDefaultJsonSerializer();
            expectedString = serializer.serializeToString(expected);
            actualString = serializer.serializeToString(actual);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        Assert.assertEquals(expectedString, actualString);
    }

    static void assertDirectoryEmptyOrNonExistent(Path directory)
    {
        if (!Files.notExists(directory))
        {
            assertDirectoryEmpty(directory);
        }
    }

    static void assertDirectoryEmpty(Path directory)
    {
        if (!Files.isDirectory(directory))
        {
            throw new RuntimeException("Not a directory: " + directory);
        }

        MutableList<Path> paths = Lists.mutable.empty();
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(directory))
        {
            paths.addAllIterable(dirStream);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        Assert.assertEquals(Collections.emptyList(), paths);
    }

    static void assertDirectoryTreeFilePaths(Set<Path> expectedFilePaths, Path directory)
    {
        if (!Files.isDirectory(directory))
        {
            throw new RuntimeException("Not a directory: " + directory);
        }

        Set<Path> actualFiles;
        try
        {
            actualFiles = Files.walk(directory).filter(Files::isRegularFile).map(directory::relativize).collect(Collectors.toSet());
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        Assert.assertEquals(expectedFilePaths, actualFiles);
    }
}
