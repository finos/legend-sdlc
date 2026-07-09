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

package org.finos.legend.sdlc.domain.model.project.configuration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.finos.legend.sdlc.domain.model.project.ProjectType;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface ProjectConfiguration
{
    String getProjectId();

    default ProjectType getProjectType()
    {
        return null;
    }

    ProjectStructureVersion getProjectStructureVersion();

    default List<PlatformConfiguration> getPlatformConfigurations()
    {
        return null;
    }

    String getGroupId();

    String getArtifactId();

    List<ProjectDependency> getProjectDependencies();

    List<MetamodelDependency> getMetamodelDependencies();

    /**
     * Configuration values scoped to the project's structure version: a namespaced bag of typed option values that
     * only the owning structure version interprets. This is where structure-version options (including the legacy
     * flat booleans, see {@link #getRunDependencyTests()} and {@link #getProduceShadedServiceJar()}) live; do not
     * add further top-level option accessors to this interface. The bag is not yet persisted to project.json in its
     * namespaced form; the project-structure-configuration-options plan owns that migration, so the accessor is
     * excluded from serialization for now.
     */
    @JsonIgnore
    default Map<String, Object> getStructureConfiguration()
    {
        return Collections.emptyMap();
    }

    /**
     * Configuration values scoped to the project's structure extension (which is deployment-specific): a namespaced
     * bag of typed option values that only the owning extension interprets. Like
     * {@link #getStructureConfiguration()}, not yet persisted in namespaced form.
     */
    @JsonIgnore
    default Map<String, Object> getExtensionConfiguration()
    {
        return Collections.emptyMap();
    }

    /**
     * @deprecated This is an option of the owning structure version, not a project-configuration concern: read it
     * from {@link #getStructureConfiguration()} under the key {@code "runDependencyTests"}. The getter is retained
     * temporarily for compatibility.
     */
    @Deprecated
    default Boolean getRunDependencyTests()
    {
        return (Boolean) getStructureConfiguration().get("runDependencyTests");
    }

    /**
     * @deprecated This is an option of the owning structure version, not a project-configuration concern: read it
     * from {@link #getStructureConfiguration()} under the key {@code "produceShadedServiceJar"}. The getter is
     * retained temporarily for compatibility.
     */
    @Deprecated
    default Boolean getProduceShadedServiceJar()
    {
        return (Boolean) getStructureConfiguration().get("produceShadedServiceJar");
    }

    @Deprecated
    default List<ArtifactGeneration> getArtifactGenerations()
    {
        return Collections.emptyList();
    }
}
