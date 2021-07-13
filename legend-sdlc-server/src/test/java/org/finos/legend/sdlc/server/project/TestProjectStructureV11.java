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

package org.finos.legend.sdlc.server.project;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactType;
import org.finos.legend.sdlc.server.project.ProjectStructureV11Factory.ProjectStructureV11;
import org.finos.legend.sdlc.server.project.maven.LegendEntityPluginMavenHelper;
import org.finos.legend.sdlc.server.project.maven.LegendModelGenerationPluginMavenHelper;
import org.finos.legend.sdlc.server.project.maven.MavenProjectStructure;
import org.finos.legend.sdlc.server.project.maven.MultiModuleMavenProjectStructure;
import org.finos.legend.sdlc.server.project.maven.LegendServiceExecutionGenerationPluginMavenHelper;
import org.finos.legend.sdlc.server.project.maven.LegendFileGenerationPluginMavenHelper;
import org.finos.legend.sdlc.server.project.maven.MultiModuleMavenProjectStructure.ModuleConfig;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.EnumMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class TestProjectStructureV11 extends TestMultiGenerationProjectStructure<ProjectStructureV11>
{

    private Dependency getGenerationDependency()
    {
        return MavenProjectStructure.newMavenDependency("org.finos.legend.engine", "legend-engine-extensions-collection-generation", "${platform.legend-engine.version}");
    }

    private Dependency getSerializerDependency()
    {
        return MavenProjectStructure.newMavenDependency("org.finos.legend.sdlc", "legend-sdlc-extensions-collection-entity-serializer", "${platform.legend-sdlc.version}");
    }

    @Override
    protected void collectExpectedProjectProperties(ProjectStructureV11 projectStructure, BiConsumer<String, String> propertyConsumer)
    {
        super.collectExpectedProjectProperties(projectStructure, propertyConsumer);
        propertyConsumer.accept("platform.legend-engine.version", "2.37.0");
        propertyConsumer.accept("platform.legend-sdlc.version", "0.40.0");
    }

    @Override
    protected void collectExpectedFiles(ProjectStructureV11 projectStructure, BiConsumer<String, String> expectedFilePathAndContentConsumer, Consumer<String> unexpectedFilePathConsumer)
    {
        super.collectExpectedFiles(projectStructure, expectedFilePathAndContentConsumer, unexpectedFilePathConsumer);
        expectedFilePathAndContentConsumer.accept(projectStructure.getModuleFilePath(projectStructure.getEntitiesModuleName(), ProjectStructureV11.ENTITY_VALIDATION_TEST_FILE_PATH), ProjectStructureV11.getEntityValidationTestCode());
        expectedFilePathAndContentConsumer.accept(projectStructure.getModuleFilePath(projectStructure.getEntitiesModuleName(), ProjectStructureV11.ENTITY_TEST_SUITE_FILE_PATH), ProjectStructureV11.getEntityTestSuiteCode());
    }

    @Override
    protected Map<ArtifactType, List<String>> getExpectedArtifactIdsByType(ProjectStructureV11 projectStructure)
    {
        Map<ArtifactType, List<String>> map = new EnumMap<>(ArtifactType.class);
        map.put(ArtifactType.entities, Collections.singletonList(projectStructure.getModuleFullName(projectStructure.getEntitiesModuleName())));
        map.put(ArtifactType.versioned_entities, Collections.singletonList(projectStructure.getModuleFullName(MultiModuleMavenProjectStructure.getDefaultModuleName(ArtifactType.versioned_entities))));
        map.put(ArtifactType.service_execution, Collections.singletonList(projectStructure.getModuleFullName(MultiModuleMavenProjectStructure.getDefaultModuleName(ArtifactType.service_execution))));
        map.put(ArtifactType.file_generation, Collections.singletonList(projectStructure.getModuleFullName(MultiModuleMavenProjectStructure.getDefaultModuleName(ArtifactType.file_generation))));
        return map;
    }

    @Override
    protected int getProjectStructureVersion()
    {
        return 11;
    }

    @Override
    protected Class<ProjectStructureV11> getProjectStructureClass()
    {
        return ProjectStructureV11.class;
    }

    @Override
    protected Set<ArtifactType> getExpectedSupportedArtifactConfigurationTypes()
    {
        return Sets.mutable.empty();
    }


    @Override
    protected void collectExpectedEntitiesModelDependencies(ProjectStructureV11 projectStructure, Consumer<Dependency> dependencyConsumer)
    {
        super.collectExpectedEntitiesModelDependencies(projectStructure, dependencyConsumer);
        dependencyConsumer.accept(LEGEND_TEST_UTILS_MAVEN_HELPER.getDependency(false));
        Dependency executionDependency = projectStructure.newMavenDependency("org.finos.legend.engine", "legend-engine-extensions-collection-execution", null);
        executionDependency.setScope("test");
        dependencyConsumer.accept(executionDependency);
        Dependency generationDependency = MavenProjectStructure.newMavenDependency("org.finos.legend.engine", "legend-engine-extensions-collection-generation", null);
        generationDependency.setScope("test");
        dependencyConsumer.accept(generationDependency);
    }


    @Override
    protected void collectExpectedEntitiesModelPlugins(ProjectStructureV11 projectStructure, Consumer<Plugin> pluginConsumer)
    {
        super.collectExpectedEntitiesModelPlugins(projectStructure, pluginConsumer);
        pluginConsumer.accept((new LegendEntityPluginMavenHelper("org.finos.legend.sdlc", "legend-sdlc-entity-maven-plugin","${platform.legend-sdlc.version}", Lists.immutable.with(getGenerationDependency(), getSerializerDependency()).toList())).getPlugin(projectStructure, this.fileAccessProvider::getFileAccessContext));
        pluginConsumer.accept((new LegendModelGenerationPluginMavenHelper("org.finos.legend.sdlc", "legend-sdlc-generation-model-maven-plugin", "${platform.legend-sdlc.version}", getGenerationDependency())).getPlugin(projectStructure, this.fileAccessProvider::getFileAccessContext));
    }

    @Override
    protected void collectExpectedProjectModelDependencyManagement(ProjectStructureV11 projectStructure, Consumer<Dependency> dependencyManagementConsumer)
    {
        super.collectExpectedProjectModelDependencyManagement(projectStructure, dependencyManagementConsumer);
        dependencyManagementConsumer.accept(MavenProjectStructure.newMavenDependency("org.finos.legend.engine", "legend-engine-extensions-collection-execution", "${platform.legend-engine.version}"));
        dependencyManagementConsumer.accept(MavenProjectStructure.newMavenDependency("org.finos.legend.engine", "legend-engine-extensions-collection-generation", "${platform.legend-engine.version}"));
    }

    @ModuleConfig(artifactType = ArtifactType.service_execution, type = MultiModuleMavenProjectStructure.ModuleConfigType.PLUGINS)
    protected void collectExpectedServiceExecutionModulePlugins(String name, ProjectStructureV11 projectStructure, Consumer<Plugin> pluginConsumer)
    {
        pluginConsumer.accept(new LegendServiceExecutionGenerationPluginMavenHelper("org.finos.legend.sdlc", "legend-sdlc-generation-service-maven-plugin","${platform.legend-sdlc.version}", getGenerationDependency()).getPlugin(projectStructure, this.fileAccessProvider::getFileAccessContext));
        pluginConsumer.accept(new LegendServiceExecutionGenerationPluginMavenHelper("org.finos.legend.sdlc", "legend-sdlc-generation-service-maven-plugin","${platform.legend-sdlc.version}", getGenerationDependency()).getBuildHelperPlugin("3.0.0"));
    }

    @ModuleConfig(artifactType = ArtifactType.service_execution, type = MultiModuleMavenProjectStructure.ModuleConfigType.DEPENDENCIES)
    protected void collectExpectedServiceExecutionModuleDependencies(ProjectStructureV11 projectStructure, Consumer<Dependency> dependencyConsumer)
    {
        dependencyConsumer.accept(MavenProjectStructure.newMavenDependency("org.finos.legend.engine",  "legend-engine-extensions-collection-execution", null));
    }

    @ModuleConfig(artifactType = ArtifactType.file_generation, type = MultiModuleMavenProjectStructure.ModuleConfigType.PLUGINS)
    protected void collectExpectedFileGenerationModulePlugins(String name, ProjectStructureV11 projectStructure, Consumer<Plugin> pluginConsumer)
    {
        pluginConsumer.accept((new LegendFileGenerationPluginMavenHelper("org.finos.legend.sdlc", "legend-sdlc-generation-file-maven-plugin","${platform.legend-sdlc.version}", getGenerationDependency())).getPlugin(projectStructure, this.fileAccessProvider::getFileAccessContext));
    }

}

