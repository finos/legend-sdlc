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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.finos.legend.sdlc.backend.api.spi.BackendEnvironment;
import org.finos.legend.sdlc.backend.api.tools.BackgroundTaskProcessor;
import org.finos.legend.sdlc.project.structure.ProjectStructurePlatformExtensions;
import org.finos.legend.sdlc.project.structure.extension.ProjectStructureExtension;
import org.finos.legend.sdlc.project.structure.extension.ProjectStructureExtensionProvider;

import java.util.Collections;

/**
 * Minimal deployment environment for driving the file-system backend in tests: no extensions, a plain object
 * mapper, a single-thread task processor.
 */
class TestFileSystemBackendEnvironment implements BackendEnvironment
{
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BackgroundTaskProcessor taskProcessor = new BackgroundTaskProcessor(1);

    @Override
    public ObjectMapper getObjectMapper()
    {
        return this.objectMapper;
    }

    @Override
    public BackgroundTaskProcessor getTaskProcessor()
    {
        return this.taskProcessor;
    }

    @Override
    public ProjectStructureExtensionProvider getProjectStructureExtensionProvider()
    {
        return new ProjectStructureExtensionProvider()
        {
            @Override
            public Integer getLatestVersionForProjectStructureVersion(int projectStructureVersion)
            {
                return null;
            }

            @Override
            public ProjectStructureExtension getProjectStructureExtension(int projectStructureVersion, int projectStructureExtensionVersion)
            {
                return null;
            }
        };
    }

    @Override
    public ProjectStructurePlatformExtensions getProjectStructurePlatformExtensions()
    {
        return ProjectStructurePlatformExtensions.newPlatformExtensions(Collections.emptyList(), Collections.emptyList());
    }
}
