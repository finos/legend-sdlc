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

package org.finos.legend.sdlc.server.project.extension;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.collections.api.factory.Maps;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.project.ProjectFileOperation;
import org.finos.legend.sdlc.server.project.ProjectStructure;
import org.finos.legend.sdlc.server.project.config.ProjectFileConfiguration;
import org.finos.legend.sdlc.server.tools.StringTools;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DefaultProjectStructureExtension implements ProjectStructureExtension
{
    private final int projectStructureVersion;
    private final int extensionVersion;
    private final Map<String, String> projectFiles;

    protected DefaultProjectStructureExtension(int projectStructureVersion, int extensionVersion, Map<String, String> projectFiles)
    {
        if ((projectStructureVersion < 0) || (projectStructureVersion > ProjectStructure.getLatestProjectStructureVersion()))
        {
            throw new IllegalStateException("Invalid project structure version: " + projectStructureVersion);
        }
        if (extensionVersion < 0)
        {
            throw new IllegalStateException("Invalid version: " + extensionVersion);
        }

        this.projectStructureVersion = projectStructureVersion;
        this.extensionVersion = extensionVersion;
        this.projectFiles = (projectFiles == null) ? Collections.emptyMap() : projectFiles;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }

        if ((other == null) || (this.getClass() != other.getClass()))
        {
            return false;
        }

        DefaultProjectStructureExtension that = (DefaultProjectStructureExtension)other;
        return (this.projectStructureVersion == that.projectStructureVersion) &&
                (this.extensionVersion == that.extensionVersion) &&
                this.projectFiles.equals(that.projectFiles);
    }

    @Override
    public int hashCode()
    {
        return (89 * this.projectStructureVersion) + this.extensionVersion;
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder("<").append(getClass().getName())
                .append(" projectStructureVersion=").append(this.projectStructureVersion)
                .append(" extensionVersion=").append(this.extensionVersion);
        if (!this.projectFiles.isEmpty())
        {
            builder.append(" projectFiles=").append(this.projectFiles.keySet());
        }
        builder.append('>');
        return builder.toString();
    }

    @Override
    public int getVersion()
    {
        return this.extensionVersion;
    }

    @Override
    public int getProjectStructureVersion()
    {
        return this.projectStructureVersion;
    }

    @Override
    public void collectUpdateProjectConfigurationOperations(ProjectConfiguration oldConfig, ProjectConfiguration newConfig, ProjectFileAccessProvider.FileAccessContext fileAccessContext, Consumer<ProjectFileOperation> operationConsumer)
    {
        this.projectFiles.forEach((path, content) -> updateFile(path, content, fileAccessContext, operationConsumer));
    }

    protected void updateFile(String filePath, String content, ProjectFileAccessProvider.FileAccessContext fileAccessContext, Consumer<ProjectFileOperation> operationConsumer)
    {
        updateFile(filePath, (content == null) ? null : content.getBytes(StandardCharsets.UTF_8), fileAccessContext, operationConsumer);
    }

    protected void updateFile(String filePath, byte[] content, ProjectFileAccessProvider.FileAccessContext fileAccessContext, Consumer<ProjectFileOperation> operationConsumer)
    {
        ProjectFileAccessProvider.ProjectFile file = fileAccessContext.getFile(filePath);
        if (content == null)
        {
            if (file != null)
            {
                operationConsumer.accept(ProjectFileOperation.deleteFile(filePath));
            }
        }
        else if (file == null)
        {
            operationConsumer.accept(ProjectFileOperation.addFile(filePath, content));
        }
        else if (!Arrays.equals(content, file.getContentAsBytes()))
        {
            operationConsumer.accept(ProjectFileOperation.modifyFile(filePath, content));
        }
    }

    public static DefaultProjectStructureExtension newProjectStructureExtension(int projectStructureVersion, int extensionVersion, Map<String, String> projectFiles)
    {
        return new DefaultProjectStructureExtension(projectStructureVersion, extensionVersion, canonicalizeProjectFiles(projectFiles, projectStructureVersion, extensionVersion));
    }

    @JsonCreator
    public static DefaultProjectStructureExtension newProjectStructureExtensionFromConfig(@JsonProperty("projectStructureVersion") int projectStructureVersion, @JsonProperty("extensionVersion") int extensionVersion, @JsonProperty("files") Map<String, ProjectFileConfiguration> projectFileConfigurations)
    {
        Map<String, String> projectFiles = computeProjectFiles(projectFileConfigurations, projectStructureVersion, extensionVersion);
        return new DefaultProjectStructureExtension(projectStructureVersion, extensionVersion, projectFiles);
    }

    protected static Map<String, String> computeProjectFiles(Map<String, ProjectFileConfiguration> projectFileConfigurations, int projectStructureVersion, int extensionVersion)
    {
        if ((projectFileConfigurations == null) || projectFileConfigurations.isEmpty())
        {
            return Collections.emptyMap();
        }

        Map<String, String> newProjectFiles = Maps.mutable.ofInitialCapacity(projectFileConfigurations.size());
        projectFileConfigurations.forEach((path, config) ->
        {
            String content;
            try
            {
                content = config.resolveContent(StandardCharsets.UTF_8);
            }
            catch (Exception e)
            {
                StringBuilder builder = new StringBuilder("Error resolving content for file \"").append(path).append("\" for project structure version ").append(projectStructureVersion).append(", extension ").append(extensionVersion);
                StringTools.appendThrowableMessageIfPresent(builder, e);
                throw new RuntimeException(builder.toString(), e);
            }
            if (content == null)
            {
                throw new RuntimeException("Could not get content for file \"" + path + "\" for project structure version " + projectStructureVersion + ", extension " + extensionVersion);
            }
            newProjectFiles.put(path, content);
        });
        return canonicalizeProjectFilesInPlace(newProjectFiles, projectStructureVersion, extensionVersion);
    }

    protected static Map<String, String> canonicalizeProjectFiles(Map<String, String> projectFiles, int projectStructureVersion, int extensionVersion)
    {
        return ((projectFiles == null) || projectFiles.isEmpty()) ? Collections.emptyMap() : canonicalizeProjectFilesInPlace(Maps.mutable.withMap(projectFiles), projectStructureVersion, extensionVersion);
    }

    private static Map<String, String> canonicalizeProjectFilesInPlace(Map<String, String> projectFiles, int projectStructureVersion, int extensionVersion)
    {
        List<String> nonCanonicalPaths = projectFiles.keySet().stream().filter(p -> !isPathCanonical(p)).collect(Collectors.toList());
        for (String path : nonCanonicalPaths)
        {
            String canonicalPath = canonicalizePath(path);
            String content = projectFiles.remove(path);
            String canonicalPathContent = projectFiles.put(canonicalPath, content);
            if (canonicalPathContent != null)
            {
                throw new RuntimeException("Multiple definitions for \"" + canonicalPath + "\" for project structure version " + projectStructureVersion + ", extension " + extensionVersion + ": one for \"" + canonicalPath + "\" and another for \"" + path + "\"");
            }
        }
        return projectFiles;
    }

    private static boolean isPathCanonical(String path)
    {
        return (path != null) && !path.isEmpty() && (path.charAt(0) == '/');
    }

    private static String canonicalizePath(String path)
    {
        return ((path == null) || path.isEmpty()) ? "/" : ("/" + path);
    }
}
