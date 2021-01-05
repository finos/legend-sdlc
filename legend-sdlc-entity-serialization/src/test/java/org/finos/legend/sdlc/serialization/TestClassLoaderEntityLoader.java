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
import org.eclipse.collections.api.factory.Sets;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

public class TestClassLoaderEntityLoader extends TestEntityLoader
{
    private static final String JAR_NAME_FORMAT_STRING = "model%d.jar";
    private static final String DIR_NAME_FORMAT_STRING = "dir%d";

    private static final int JAR_COUNT = 2;
    private static final int DIR_COUNT = 2;
    private static final int TOTAL_COUNT = JAR_COUNT + DIR_COUNT;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Override
    protected EntityLoader createEntityLoaderFromFiles(Map<String, byte[]> fileContentByPath) throws IOException
    {
        Path root = this.tempFolder.getRoot().toPath();
        URL[] urls = new URL[TOTAL_COUNT];
        JarOutputStream[] jarStreams = new JarOutputStream[JAR_COUNT];
        List<Set<String>> addedDirs = Lists.mutable.ofInitialCapacity(JAR_COUNT);
        try (JarOutputStream jarStream0 = new JarOutputStream(Files.newOutputStream(root.resolve(String.format(JAR_NAME_FORMAT_STRING, 0))), new Manifest());
             JarOutputStream jarStream1 = new JarOutputStream(Files.newOutputStream(root.resolve(String.format(JAR_NAME_FORMAT_STRING, 1))), new Manifest()))
        {
            jarStreams[0] = jarStream0;
            jarStreams[1] = jarStream1;
            for (int i = 0; i < JAR_COUNT; i++)
            {
                Path jarPath = root.resolve(String.format(JAR_NAME_FORMAT_STRING, i));
                urls[i] = jarPath.toUri().toURL();
                addedDirs.add(Sets.mutable.empty());
            }
            for (int i = JAR_COUNT; i < TOTAL_COUNT; i++)
            {
                Path dirPath = root.resolve(String.format(DIR_NAME_FORMAT_STRING, i));
                Files.createDirectories(dirPath); // we have to create the directory for the URL to be generated correctly
                urls[i] = dirPath.toUri().toURL();
            }

            int i = 0;
            for (Map.Entry<String, byte[]> entry : fileContentByPath.entrySet())
            {
                String relativeFilePath = entry.getKey();
                int iMod = i % TOTAL_COUNT;
                if (iMod < JAR_COUNT)
                {
                    JarOutputStream jarStream = jarStreams[iMod];
                    for (int j = relativeFilePath.indexOf('/'); j != -1; j = relativeFilePath.indexOf('/', j + 1))
                    {
                        String dir = relativeFilePath.substring(0, j + 1);
                        if (addedDirs.get(iMod).add(dir))
                        {
                            jarStream.putNextEntry(new ZipEntry(dir));
                            jarStream.closeEntry();
                        }
                    }
                    jarStream.putNextEntry(new ZipEntry(relativeFilePath));
                    jarStream.write(entry.getValue());
                    jarStream.closeEntry();
                }
                else
                {
                    Path filePath = root.resolve(String.format(DIR_NAME_FORMAT_STRING, iMod)).resolve(relativeFilePath);
                    Files.createDirectories(filePath.getParent());
                    Files.write(filePath, entry.getValue());
                }
                i++;
            }
        }

        ClassLoader classLoader = URLClassLoader.newInstance(urls);
        return EntityLoader.newEntityLoader(classLoader);
    }
}
