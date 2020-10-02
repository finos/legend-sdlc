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

import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class TestFSDirectoriesEntityLoader extends TestEntityLoader
{
    private static final int DIR_COUNT = 3;
    private static final String DIR_PREFIX = "dir";

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Override
    protected EntityLoader createEntityLoaderFromFiles(Map<String, byte[]> fileContentByPath) throws IOException
    {
        Path root = this.tempFolder.getRoot().toPath();
        int i = 0;
        for (Map.Entry<String, byte[]> entry : fileContentByPath.entrySet())
        {
            Path filePath = root.resolve(DIR_PREFIX + (i % DIR_COUNT)).resolve(entry.getKey());
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, entry.getValue());
            i++;
        }
        Path[] dirs = new Path[DIR_COUNT];
        for (int d = 0; d < DIR_COUNT; d++)
        {
            dirs[d] = root.resolve(DIR_PREFIX + d);
        }
        return EntityLoader.newEntityLoader(dirs);
    }
}
