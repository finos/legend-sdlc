// Copyright 2026 Goldman Sachs
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

package org.finos.legend.sdlc.backend.fs;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.finos.legend.sdlc.backend.api.spi.BackendConfiguration;

/**
 * Configuration for the file-system backend:
 *
 * <pre>
 * backend:
 *   type: fileSystem
 *   rootDirectory: /path/under/which/project/repositories/live
 * </pre>
 */
public class FileSystemBackendConfiguration extends BackendConfiguration
{
    private final String rootDirectory;

    private FileSystemBackendConfiguration(String rootDirectory)
    {
        this.rootDirectory = rootDirectory;
    }

    public String getRootDirectory()
    {
        return this.rootDirectory;
    }

    @JsonCreator
    public static FileSystemBackendConfiguration newConfiguration(@JsonProperty("rootDirectory") String rootDirectory)
    {
        return new FileSystemBackendConfiguration(rootDirectory);
    }
}
