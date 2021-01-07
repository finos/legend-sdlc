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

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Map;

public class TestZipFSEntityLoader extends TestEntityLoaderWithZipFile
{
    @Override
    protected EntityLoader createEntityLoaderFromFiles(Map<String, byte[]> fileContentByPath) throws IOException
    {
        Path zipPath = writeToTempZipFile(fileContentByPath);
        FileSystem zipFS = FileSystems.newFileSystem(zipPath, (ClassLoader) null);
        registerCloseable(zipFS);
        return EntityLoader.newEntityLoader(zipFS.getPath(zipFS.getSeparator()));
    }

    @Override
    protected boolean isJar()
    {
        return false;
    }
}
