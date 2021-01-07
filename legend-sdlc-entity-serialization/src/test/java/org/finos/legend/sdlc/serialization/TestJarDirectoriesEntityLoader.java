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

package org.finos.legend.sdlc.serialization;

import org.eclipse.collections.api.factory.Maps;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Map;

public class TestJarDirectoriesEntityLoader extends TestEntityLoaderWithZipFile
{
    private static final int DIR_COUNT = 3;
    private static final String DIR_PREFIX = "dir";

    @Override
    protected boolean isJar()
    {
        return true;
    }

    @Override
    protected EntityLoader createEntityLoaderFromFiles(Map<String, byte[]> fileContentByPath) throws IOException
    {
        Path zipPath = writeToTempZipFile(addDirectories(fileContentByPath));
        return EntityLoader.newEntityLoader(getDirectoryURIs(zipPath));
    }

    private Map<String, byte[]> addDirectories(Map<String, byte[]> fileContentByPath)
    {
        Map<String, byte[]> fileContentInDirectories = Maps.mutable.ofInitialCapacity(fileContentByPath.size());
        int i = 0;
        for (Map.Entry<String, byte[]> entry : fileContentByPath.entrySet())
        {
            fileContentInDirectories.put(DIR_PREFIX + (i % DIR_COUNT) + "/" + entry.getKey(), entry.getValue());
            i++;
        }
        return fileContentInDirectories;
    }

    private URI[] getDirectoryURIs(Path zipPath)
    {
        String zipURIString = zipPath.toUri().toString();
        URI[] uris = new URI[DIR_COUNT];
        for (int i = 0; i < DIR_COUNT; i++)
        {
            try
            {
                URI uri = new URI("jar:" + zipURIString + "!/" + DIR_PREFIX + i);
                uris[i] = uri;
            }
            catch (URISyntaxException e)
            {
                throw new RuntimeException(e);
            }
        }
        return uris;
    }
}
