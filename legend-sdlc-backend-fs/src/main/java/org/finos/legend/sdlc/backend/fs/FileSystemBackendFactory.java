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

import org.finos.legend.sdlc.backend.api.spi.Backend;
import org.finos.legend.sdlc.backend.api.spi.BackendConfiguration;
import org.finos.legend.sdlc.backend.api.spi.BackendEnvironment;
import org.finos.legend.sdlc.backend.api.spi.BackendFactory;

public class FileSystemBackendFactory implements BackendFactory
{
    @Override
    public String getType()
    {
        return FileSystemBackend.TYPE;
    }

    @Override
    public Class<? extends BackendConfiguration> getConfigurationClass()
    {
        return FileSystemBackendConfiguration.class;
    }

    @Override
    public Backend build(BackendConfiguration configuration, BackendEnvironment environment)
    {
        String rootDirectory = ((FileSystemBackendConfiguration) configuration).getRootDirectory();
        if (rootDirectory == null)
        {
            throw new IllegalArgumentException("The " + FileSystemBackend.TYPE + " backend configuration requires a rootDirectory");
        }
        return new FileSystemBackend(rootDirectory, environment);
    }
}
