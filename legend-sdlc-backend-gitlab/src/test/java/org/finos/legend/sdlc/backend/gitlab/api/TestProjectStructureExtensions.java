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

package org.finos.legend.sdlc.backend.gitlab.api;

import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider;
import org.finos.legend.sdlc.project.files.ProjectFileOperation;
import org.finos.legend.sdlc.project.structure.extension.ProjectStructureExtension;
import org.finos.legend.sdlc.project.structure.extension.ProjectStructureExtensionProvider;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Test fixture: a project structure extension writing a fixed set of files, and a provider over it. The
 * server's {@code DefaultProjectStructureExtension} served this role before the extraction; concrete extension
 * classes are deployment configuration and stay in the server, so the tests carry their own.
 */
public class TestProjectStructureExtensions
{
    private TestProjectStructureExtensions()
    {
    }

    public static ProjectStructureExtension newFileExtension(int projectStructureVersion, int extensionVersion, Map<String, String> projectFiles)
    {
        return new ProjectStructureExtension()
        {
            @Override
            public int getProjectStructureVersion()
            {
                return projectStructureVersion;
            }

            @Override
            public int getVersion()
            {
                return extensionVersion;
            }

            @Override
            public void collectUpdateProjectConfigurationOperations(ProjectConfiguration oldConfig, ProjectConfiguration newConfig, ProjectFileAccessProvider.FileAccessContext fileAccessContext, Consumer<ProjectFileOperation> operationConsumer)
            {
                projectFiles.forEach((path, content) ->
                {
                    byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
                    ProjectFileAccessProvider.ProjectFile file = fileAccessContext.getFile(path);
                    if (file == null)
                    {
                        operationConsumer.accept(ProjectFileOperation.addFile(path, bytes));
                    }
                    else if (!Arrays.equals(bytes, file.getContentAsBytes()))
                    {
                        operationConsumer.accept(ProjectFileOperation.modifyFile(path, bytes));
                    }
                });
            }
        };
    }

    public static ProjectStructureExtensionProvider providerFor(ProjectStructureExtension extension)
    {
        return new ProjectStructureExtensionProvider()
        {
            @Override
            public Integer getLatestVersionForProjectStructureVersion(int projectStructureVersion)
            {
                return (projectStructureVersion == extension.getProjectStructureVersion()) ? extension.getVersion() : null;
            }

            @Override
            public ProjectStructureExtension getProjectStructureExtension(int projectStructureVersion, int projectStructureExtensionVersion)
            {
                if ((projectStructureVersion != extension.getProjectStructureVersion()) || (projectStructureExtensionVersion != extension.getVersion()))
                {
                    throw new IllegalArgumentException("Unknown project structure extension: structure version " + projectStructureVersion + ", extension version " + projectStructureExtensionVersion);
                }
                return extension;
            }
        };
    }
}
