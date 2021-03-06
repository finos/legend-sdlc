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

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

public class TestFSDirectoryURIsEntityLoader extends TestFSDirectoriesEntityLoader
{
    @Override
    protected EntityLoader createEntityLoaderFromFiles(Map<String, byte[]> fileContentByPath) throws IOException
    {
        Path[] dirs = writeToDirectories(fileContentByPath);
        URI[] uris = Arrays.stream(dirs).map(Path::toUri).toArray(URI[]::new);
        return EntityLoader.newEntityLoader(uris);
    }
}
