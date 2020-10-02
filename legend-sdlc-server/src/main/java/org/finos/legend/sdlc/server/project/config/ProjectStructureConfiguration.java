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

package org.finos.legend.sdlc.server.project.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.finos.legend.sdlc.server.project.extension.ProjectStructureExtension;
import org.finos.legend.sdlc.server.project.extension.ProjectStructureExtensionProvider;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ProjectStructureConfiguration
{
    private final Set<Integer> demisedVersions;
    private final ProjectStructureExtensionProvider extensionProvider;
    private final List<ProjectStructureExtension> extensions;
    private final ProjectCreationConfiguration projectCreationConfig;

    private ProjectStructureConfiguration(Set<Integer> demisedVersions, ProjectStructureExtensionProvider extensionProvider, List<ProjectStructureExtension> extensions, ProjectCreationConfiguration projectCreationConfig)
    {
        if ((extensionProvider != null) && (extensions != null) && !extensions.isEmpty())
        {
            throw new IllegalArgumentException("May not specify both extensionProvider and extensions");
        }

        this.demisedVersions = (demisedVersions == null) ? Collections.emptySet() : demisedVersions;
        this.extensionProvider = extensionProvider;
        this.extensions = (extensions == null) ? Collections.emptyList() : extensions;
        this.projectCreationConfig = projectCreationConfig;
    }

    public Set<Integer> getDemisedVersions()
    {
        return this.demisedVersions;
    }

    public ProjectStructureExtensionProvider getProjectStructureExtensionProvider()
    {
        return this.extensionProvider;
    }

    public List<ProjectStructureExtension> getProjectStructureExtensions()
    {
        return this.extensions;
    }

    public ProjectCreationConfiguration getProjectCreationConfiguration()
    {
        return this.projectCreationConfig;
    }

    @JsonCreator
    public static ProjectStructureConfiguration newConfiguration(@JsonProperty("demisedVersions") Set<Integer> demisedVersions, @JsonProperty("extensionProvider") ProjectStructureExtensionProvider extensionProvider, @JsonProperty("extensions") List<ProjectStructureExtension> extensions, @JsonProperty("projectCreation") ProjectCreationConfiguration projectCreationConfig)
    {
        return new ProjectStructureConfiguration(demisedVersions, extensionProvider, extensions, projectCreationConfig);
    }

    public static ProjectStructureConfiguration emptyConfiguration()
    {
        return new ProjectStructureConfiguration(Collections.emptySet(), null, Collections.emptyList(), null);
    }

    public static ObjectMapper configureObjectMapper(ObjectMapper objectMapper)
    {
        @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.WRAPPER_OBJECT)
        abstract class WrapperMixin
        {
        }

        return objectMapper
                .addMixIn(ProjectStructureExtension.class, WrapperMixin.class)
                .addMixIn(ProjectStructureExtensionProvider.class, WrapperMixin.class);
    }
}
