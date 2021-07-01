package org.finos.legend.sdlc.server.project;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.eclipse.collections.api.factory.Sets;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactType;
import org.finos.legend.sdlc.server.project.ProjectStructureV11Factory.ProjectStructureV11;
import org.finos.legend.sdlc.server.project.maven.MavenProjectStructure;
import org.finos.legend.sdlc.server.project.maven.MultiModuleMavenProjectStructure;
import org.finos.legend.sdlc.server.project.maven.MultiModuleMavenProjectStructure.ModuleConfig;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class TestProjectStructureV11 extends TestMultiGenerationProjectStructure<ProjectStructureV11> {
    @Override
    protected void collectExpectedProjectProperties(ProjectStructureV11 projectStructure, BiConsumer<String, String> propertyConsumer) {
        super.collectExpectedProjectProperties(projectStructure, propertyConsumer);
        propertyConsumer.accept("alloy.version", "2.20.0");
    }

    @Override
    protected void collectExpectedFiles(ProjectStructureV11 projectStructure, BiConsumer<String, String> expectedFilePathAndContentConsumer, Consumer<String> unexpectedFilePathConsumer) {
        super.collectExpectedFiles(projectStructure, expectedFilePathAndContentConsumer, unexpectedFilePathConsumer);
        expectedFilePathAndContentConsumer.accept(projectStructure.getModuleFilePath(projectStructure.getEntitiesModuleName(), ProjectStructureV11.ENTITY_VALIDATION_TEST_FILE_PATH), ProjectStructureV11.getEntityValidationTestCode());
        expectedFilePathAndContentConsumer.accept(projectStructure.getModuleFilePath(projectStructure.getEntitiesModuleName(), ProjectStructureV11.ENTITY_TEST_SUITE_FILE_PATH), ProjectStructureV11.getEntityTestSuiteCode());
    }

    @Override
    protected Map<ArtifactType, List<String>> getExpectedArtifactIdsByType(ProjectStructureV11 projectStructure) {
        Map<ArtifactType, List<String>> map = new EnumMap<>(ArtifactType.class);
        map.put(ArtifactType.entities, Collections.singletonList(projectStructure.getModuleFullName(projectStructure.getEntitiesModuleName())));
        map.put(ArtifactType.versioned_entities, Collections.singletonList(projectStructure.getModuleFullName(MultiModuleMavenProjectStructure.getDefaultModuleName(ArtifactType.versioned_entities))));
        map.put(ArtifactType.service_execution, Collections.singletonList(projectStructure.getModuleFullName(MultiModuleMavenProjectStructure.getDefaultModuleName(ArtifactType.service_execution))));
        map.put(ArtifactType.file_generation, Collections.singletonList(projectStructure.getModuleFullName(MultiModuleMavenProjectStructure.getDefaultModuleName(ArtifactType.file_generation))));
        return map;
    }

    @Override
    protected int getProjectStructureVersion() {
        return 10;
    }

    @Override
    protected Class<ProjectStructureV11> getProjectStructureClass() {
        return ProjectStructureV11.class;
    }

    @Override
    protected Set<ArtifactType> getExpectedSupportedArtifactConfigurationTypes() {
        return Sets.mutable.empty();
    }

    @Override
    protected void collectExpectedEntitiesModelPlugins(ProjectStructureV11 projectStructure, Consumer<Plugin> pluginConsumer) {
        super.collectExpectedEntitiesModelPlugins(projectStructure, pluginConsumer);
//        pluginConsumer.accept((new LegendEntityPluginMavenHelper("platform.alloy", "alloy-metadata-sdlc-entity-maven-plugin","${alloy.version}")).getPlugin(projectStructure, this.fileAccessProvider::getFileAccessContext));
//        pluginConsumer.accept((new LegendModelGenerationPluginMavenHelper("platform.alloy", "alloy-metadata-sdlc-generation-model-maven-plugin", "${alloy.version}")).getPlugin(projectStructure, this.fileAccessProvider::getFileAccessContext));
    }

    @Override
    protected void collectExpectedProjectModelDependencyManagement(ProjectStructureV11 projectStructure, Consumer<Dependency> dependencyManagementConsumer) {
        super.collectExpectedProjectModelDependencyManagement(projectStructure, dependencyManagementConsumer);
        dependencyManagementConsumer.accept(MavenProjectStructure.newMavenDependency("platform.alloy", "alloy-execution-executionPlan-execution-all", "${alloy.version}"));
    }

    @ModuleConfig(artifactType = ArtifactType.service_execution, type = MultiModuleMavenProjectStructure.ModuleConfigType.PLUGINS)
    protected void collectExpectedServiceExecutionModulePlugins(String name, ProjectStructureV11 projectStructure, Consumer<Plugin> pluginConsumer) {
//        pluginConsumer.accept(new LegendServiceExecutionGenerationPluginMavenHelper("platform.alloy", "alloy-metadata-sdlc-service-gen-maven-plugin","${alloy.version}").getPlugin(projectStructure, this.fileAccessProvider::getFileAccessContext));
//        pluginConsumer.accept(new LegendServiceExecutionGenerationPluginMavenHelper("platform.alloy", "alloy-metadata-sdlc-service-gen-maven-plugin","${alloy.version}").getBuildHelperPlugin("3.0.0"));
    }

    @ModuleConfig(artifactType = ArtifactType.service_execution, type = MultiModuleMavenProjectStructure.ModuleConfigType.DEPENDENCIES)
    protected void collectExpectedServiceExecutionModuleDependencies(ProjectStructureV11 projectStructure, Consumer<Dependency> dependencyConsumer) {
        dependencyConsumer.accept(MavenProjectStructure.newMavenDependency("platform.alloy", "alloy-execution-executionPlan-execution-all", null));
    }

    @ModuleConfig(artifactType = ArtifactType.file_generation, type = MultiModuleMavenProjectStructure.ModuleConfigType.PLUGINS)
    protected void collectExpectedFileGenerationModulePlugins(String name, ProjectStructureV11 projectStructure, Consumer<Plugin> pluginConsumer) {
//        pluginConsumer.accept((new LegendFileGenerationPluginMavenHelper("platform.alloy", "alloy-metadata-sdlc-generation-file-maven-plugin","${alloy.version}")).getPlugin(projectStructure, this.fileAccessProvider::getFileAccessContext));
    }

    @ModuleConfig(artifactType = ArtifactType.file_generation, type = MultiModuleMavenProjectStructure.ModuleConfigType.MODULE_DEPENDENCIES)
    public void collectExpectedFileGenerationModuleDependencies(ProjectStructureV11 projectStructure, Consumer<? super String> moduleDependencyConsumer) {
        moduleDependencyConsumer.accept(projectStructure.getEntitiesModuleName());
    }
}

