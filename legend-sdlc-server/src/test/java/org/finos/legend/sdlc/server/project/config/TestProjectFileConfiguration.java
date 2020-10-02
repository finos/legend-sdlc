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

package org.finos.legend.sdlc.server.project.config;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public class TestProjectFileConfiguration
{
    private static final String STRING_CONTENT = "bat, bat, come under my hat";
    private static final String PATH_CONTENT = "and I'll give you a slice of bacon";
    private static final String RESOURCE_CONTENT = "and when I bake I'll make you a cake";
    private static final String URL_CONTENT = "if I am not mistaken";

    private static final String PATH_FILENAME = "file.txt";
    private static final String RESOURCE_FILENAME = "resource.txt";
    private static final String URL_FILENAME = "url.txt";

    private static Path TMP_DIR;

    @BeforeClass
    public static void setUp() throws IOException
    {
        TMP_DIR = Files.createTempDirectory("legend_test");
        Files.write(TMP_DIR.resolve(PATH_FILENAME), PATH_CONTENT.getBytes(StandardCharsets.UTF_8));
        Files.write(TMP_DIR.resolve(RESOURCE_FILENAME), RESOURCE_CONTENT.getBytes(StandardCharsets.UTF_8));
        Files.write(TMP_DIR.resolve(URL_FILENAME), URL_CONTENT.getBytes(StandardCharsets.UTF_8));
    }

    @AfterClass
    public static void tearDown() throws IOException
    {
        if ((TMP_DIR != null) && Files.exists(TMP_DIR))
        {
            Files.walkFileTree(TMP_DIR, new FileVisitor<Path>()
            {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                {
                    tryDelete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc)
                {
                    tryDelete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                {
                    tryDelete(dir);
                    return FileVisitResult.CONTINUE;
                }

                private void tryDelete(Path path)
                {
                    try
                    {
                        Files.delete(path);
                    }
                    catch (Exception ignore)
                    {
                        if (!Files.isWritable(path))
                        {
                            path.toFile().setWritable(true);
                        }
                        try
                        {
                            Files.delete(path);
                        }
                        catch (Exception ignore1)
                        {
                            // ignore this exception also
                        }
                    }
                }
            });
        }
    }

    @Test
    public void testResolveFromEmpty() throws IOException
    {
        ProjectFileConfiguration config = ProjectFileConfiguration.newProjectFileConfiguration(null, null, null, null);
        Assert.assertNull(config.resolveContent());
    }

    @Test
    public void testResolveFromContent() throws IOException
    {
        ProjectFileConfiguration config = ProjectFileConfiguration.newProjectFileConfiguration(STRING_CONTENT, null, null, null);
        Assert.assertEquals(STRING_CONTENT, config.resolveContent(null, null));
    }

    @Test
    public void testResolveFromPath() throws IOException
    {
        ProjectFileConfiguration config = ProjectFileConfiguration.newProjectFileConfiguration(null, TMP_DIR.resolve(PATH_FILENAME).toString(), null, null);
        Assert.assertEquals(PATH_CONTENT, config.resolveContent(StandardCharsets.UTF_8));
    }

    @Test(expected = NoSuchFileException.class)
    public void testResolveFromNonExistentPath() throws IOException
    {
        ProjectFileConfiguration config = ProjectFileConfiguration.newProjectFileConfiguration(null, TMP_DIR.resolve("non_existent_file").toString(), null, null);
        config.resolveContent(StandardCharsets.UTF_8);
    }

    @Test
    public void testResolveFromResource() throws IOException
    {
        ClassLoader classLoader = new URLClassLoader(new URL[]{TMP_DIR.toUri().toURL()}, getClass().getClassLoader());

        ProjectFileConfiguration config = ProjectFileConfiguration.newProjectFileConfiguration(null, null, RESOURCE_FILENAME, null);
        Assert.assertEquals(RESOURCE_CONTENT, config.resolveContent(StandardCharsets.UTF_8, classLoader));
    }

    @Test(expected = IOException.class)
    public void testResolveFromNonExistentResource() throws IOException
    {
        ProjectFileConfiguration config = ProjectFileConfiguration.newProjectFileConfiguration(null, null, "non_existent_resource", null);
        config.resolveContent(StandardCharsets.UTF_8);
    }

    @Test
    public void testResolveFromUrl() throws IOException
    {
        ProjectFileConfiguration config = ProjectFileConfiguration.newProjectFileConfiguration(null, null, null, TMP_DIR.resolve(URL_FILENAME).toUri().toURL().toString());
        Assert.assertEquals(URL_CONTENT, config.resolveContent(StandardCharsets.UTF_8));
    }

    @Test(expected = MalformedURLException.class)
    public void testResolveFromMalformedUrl() throws IOException
    {
        ProjectFileConfiguration config = ProjectFileConfiguration.newProjectFileConfiguration(null, null, null, "not a url");
        config.resolveContent(StandardCharsets.UTF_8);
    }


    @Test(expected = IOException.class)
    public void testResolveFromNonExistentUrl() throws IOException
    {
        ProjectFileConfiguration config = ProjectFileConfiguration.newProjectFileConfiguration(null, null, null, TMP_DIR.resolve("non_existent_url").toUri().toURL().toString());
        config.resolveContent(StandardCharsets.UTF_8);
    }

    @Test
    public void testResolutionPrecedence() throws IOException
    {
        ClassLoader classLoader = new URLClassLoader(new URL[]{TMP_DIR.toUri().toURL()}, getClass().getClassLoader());

        ProjectFileConfiguration config = ProjectFileConfiguration.newProjectFileConfiguration(STRING_CONTENT, TMP_DIR.resolve(PATH_FILENAME).toString(), RESOURCE_FILENAME, TMP_DIR.resolve(URL_FILENAME).toUri().toURL().toString());
        Assert.assertEquals(STRING_CONTENT, config.resolveContent(StandardCharsets.UTF_8, classLoader));

        config = ProjectFileConfiguration.newProjectFileConfiguration(null, TMP_DIR.resolve(PATH_FILENAME).toString(), RESOURCE_FILENAME, TMP_DIR.resolve(URL_FILENAME).toUri().toURL().toString());
        Assert.assertEquals(PATH_CONTENT, config.resolveContent(StandardCharsets.UTF_8, classLoader));

        config = ProjectFileConfiguration.newProjectFileConfiguration(null, null, RESOURCE_FILENAME, TMP_DIR.resolve(URL_FILENAME).toUri().toURL().toString());
        Assert.assertEquals(RESOURCE_CONTENT, config.resolveContent(StandardCharsets.UTF_8, classLoader));

        config = ProjectFileConfiguration.newProjectFileConfiguration(null, null, null, TMP_DIR.resolve(URL_FILENAME).toUri().toURL().toString());
        Assert.assertEquals(URL_CONTENT, config.resolveContent(StandardCharsets.UTF_8, classLoader));
    }
}
