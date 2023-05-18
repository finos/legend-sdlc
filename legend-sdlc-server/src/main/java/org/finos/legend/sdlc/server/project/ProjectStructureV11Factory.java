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
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.ImmutableMap;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactType;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.serialization.EntitySerializer;
import org.finos.legend.sdlc.serialization.EntitySerializers;
import org.finos.legend.sdlc.server.project.extension.UpdateProjectStructureExtension;
import org.finos.legend.sdlc.server.project.maven.LegendEntityPluginMavenHelper;
import org.finos.legend.sdlc.server.project.maven.LegendFileGenerationPluginMavenHelper;
import org.finos.legend.sdlc.server.project.maven.LegendModelGenerationPluginMavenHelper;
import org.finos.legend.sdlc.server.project.maven.LegendServiceExecutionGenerationPluginMavenHelper;
import org.finos.legend.sdlc.server.project.maven.LegendTestUtilsMavenHelper;
import org.finos.legend.sdlc.server.project.maven.LegendVersionPackagePluginMavenHelper;
import org.finos.legend.sdlc.server.project.maven.MultiModuleMavenProjectStructure;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class ProjectStructureV11Factory extends ProjectStructureVersionFactory
{
    @Override
    public int getVersion()
    {
        return 11;
    }

    @Override
    protected ProjectStructure createProjectStructure(ProjectConfiguration projectConfiguration, ProjectStructurePlatformExtensions projectStructurePlatformExtensions)
    {
        return new ProjectStructureV11(projectConfiguration, projectStructurePlatformExtensions);
    }

    public static class ProjectStructureV11 extends MultiModuleMavenProjectStructure
    {
        public static final String ENTITY_VALIDATION_TEST_FILE_PATH = "/src/test/java/org/finos/legend/sdlc/EntityValidationTest.java";
        public static final String ENTITY_TEST_SUITE_FILE_PATH = "/src/test/java/org/finos/legend/sdlc/EntityTestSuite.java";

        private static final String ENTITIES_MODULE_NAME = "entities";
        private static final ImmutableList<String> ENTITY_SERIALIZERS = Lists.immutable.with("pure", "legend");

        private static final Set<ArtifactType> SUPPORTED_ARTIFACT_TYPES = Collections.unmodifiableSet(EnumSet.of(ArtifactType.entities, ArtifactType.versioned_entities, ArtifactType.service_execution, ArtifactType.file_generation));
        private static final ImmutableMap<String, ArtifactType> OTHER_MODULES = Maps.immutable.with(
                getDefaultModuleName(ArtifactType.versioned_entities), ArtifactType.versioned_entities,
                getDefaultModuleName(ArtifactType.service_execution), ArtifactType.service_execution,
                getDefaultModuleName(ArtifactType.file_generation), ArtifactType.file_generation
        );

        // LEGEND SDLC
        private static final String LEGEND_SDLC_GROUP_ID = "org.finos.legend.sdlc";
        private static final String LEGEND_SDLC_VERSION = "0.40.0";
        private static final String LEGEND_SDLC_PROPERTY = "platform.legend-sdlc.version";
        private static final String LEGEND_SDLC_PROPERTY_REFERENCE = getPropertyReference(LEGEND_SDLC_PROPERTY);

        // LEGEND ENGINE
        private static final String LEGEND_ENGINE_GROUP_ID = "org.finos.legend.engine";
        private static final String LEGEND_ENGINE_VERSION = "2.37.0";
        private static final String LEGEND_ENGINE_PROPERTY = "platform.legend-engine.version";
        private static final String LEGEND_ENGINE_PROPERTY_REFERENCE = getPropertyReference(LEGEND_ENGINE_PROPERTY);
        private static final String LEGEND_SDLC_VERSION_PLUGIN = "legend-sdlc-version-package-maven-plugin";

        // EXTENSIONS COLLECTION
        private static final String GENERATION_EXTENSIONS_COLLECTION_KEY = "generation";
        private static final String EXECUTION_EXTENSIONS_COLLECTION_KEY = "execution";
        private static final String SERIALIZER_EXTENSIONS_COLLECTION_KEY = "serializer";
        private static final String DEFAULT_GENERATION_EXTENSION_ARTIFACT_ID = "legend-engine-extensions-collection-generation";
        private static final String DEFAULT_EXECUTION_EXTENSION_ARTIFACT_ID = "legend-engine-extensions-collection-execution";
        private static final String DEFAULT_SERIALIZER_EXTENSION_ARTIFACT_ID = "legend-sdlc-extensions-collection-entity-serializer";

        private static final ImmutableMap<String, MavenCoordinates> DEFAULT_EXTENSIONS_COLLECTION = Maps.immutable.with(
                GENERATION_EXTENSIONS_COLLECTION_KEY, new MavenCoordinates(LEGEND_ENGINE_GROUP_ID, DEFAULT_GENERATION_EXTENSION_ARTIFACT_ID, LEGEND_ENGINE_PROPERTY_REFERENCE),
                EXECUTION_EXTENSIONS_COLLECTION_KEY, new MavenCoordinates(LEGEND_ENGINE_GROUP_ID, DEFAULT_EXECUTION_EXTENSION_ARTIFACT_ID, LEGEND_ENGINE_PROPERTY_REFERENCE),
                SERIALIZER_EXTENSIONS_COLLECTION_KEY, new MavenCoordinates(LEGEND_SDLC_GROUP_ID, DEFAULT_SERIALIZER_EXTENSION_ARTIFACT_ID, LEGEND_SDLC_PROPERTY_REFERENCE)
        );

        // Plugin Helpers
        private final LegendEntityPluginMavenHelper legendEntityPluginMavenHelper;
        private final LegendTestUtilsMavenHelper legendTestUtilsMavenHelper;
        private final LegendServiceExecutionGenerationPluginMavenHelper legendServiceExecutionGenerationPluginMavenHelper;
        private final LegendModelGenerationPluginMavenHelper legendModelGenerationPluginMavenHelper;
        private final LegendFileGenerationPluginMavenHelper legendFileGenerationPluginMavenHelper;

        // Test Utils Exclusion
        private static final String LEGEND_PURE_GROUP_ID = "org.finos.legend.pure";
        private static final String LEGEND_PURE_CODE_JAVA_COMPILED_CORE = "legend-pure-code-java-compiled-core";

        private ProjectStructureV11(ProjectConfiguration projectConfiguration, ProjectStructurePlatformExtensions projectStructurePlatformExtensions)
        {
            super(projectConfiguration, ENTITIES_MODULE_NAME, getEntitySourceDirectories(projectConfiguration), OTHER_MODULES.castToMap(), false, projectStructurePlatformExtensions);
            Dependency generationExtensionsCollection = getExtensionsCollectionDependency(GENERATION_EXTENSIONS_COLLECTION_KEY, true, false);
            Dependency serializerExtensionsCollection = getExtensionsCollectionDependency(SERIALIZER_EXTENSIONS_COLLECTION_KEY, true, false);
            this.legendEntityPluginMavenHelper = new LegendEntityPluginMavenHelper(LEGEND_SDLC_GROUP_ID, "legend-sdlc-entity-maven-plugin", LEGEND_SDLC_PROPERTY_REFERENCE, generationExtensionsCollection, serializerExtensionsCollection);
            this.legendTestUtilsMavenHelper = new LegendTestUtilsMavenHelper(LEGEND_SDLC_GROUP_ID, "legend-sdlc-test-utils", LEGEND_SDLC_PROPERTY_REFERENCE);
            this.legendServiceExecutionGenerationPluginMavenHelper = new LegendServiceExecutionGenerationPluginMavenHelper(LEGEND_SDLC_GROUP_ID, "legend-sdlc-generation-service-maven-plugin", LEGEND_SDLC_PROPERTY_REFERENCE, generationExtensionsCollection);
            this.legendModelGenerationPluginMavenHelper = new LegendModelGenerationPluginMavenHelper(LEGEND_SDLC_GROUP_ID, "legend-sdlc-generation-model-maven-plugin", LEGEND_SDLC_PROPERTY_REFERENCE, generationExtensionsCollection);
            this.legendFileGenerationPluginMavenHelper = new LegendFileGenerationPluginMavenHelper(LEGEND_SDLC_GROUP_ID, "legend-sdlc-generation-file-maven-plugin", LEGEND_SDLC_PROPERTY_REFERENCE, generationExtensionsCollection);
        }

        private Dependency getExtensionsCollectionDependency(String extensionName, boolean includeVersion, boolean scopeTest)
        {
            Dependency dependency = getOverrideExtensionsCollectionDependency(extensionName, includeVersion, scopeTest);
            if (dependency == null)
            {
                MavenCoordinates mavenCoordinates = DEFAULT_EXTENSIONS_COLLECTION.get(extensionName);
                String groupId = mavenCoordinates.getGroupId();
                String artifactId = mavenCoordinates.getArtifactId();
                String version = includeVersion ? mavenCoordinates.getVersion() : null;
                return scopeTest ? newMavenTestDependency(groupId, artifactId, version) : newMavenDependency(groupId, artifactId, version);
            }
            return dependency;
        }

        private Dependency getOverrideExtensionsCollectionDependency(String extensionName, boolean includeVersion, boolean scopeTest)
        {
            if (getProjectStructureExtensions() != null && getProjectStructureExtensions().containsExtension(extensionName))
            {
                ProjectStructurePlatformExtensions.ExtensionsCollection extensionsCollection = getProjectStructureExtensions().getExtensionsCollection(extensionName);
                ProjectStructurePlatformExtensions.Platform platform = getProjectStructureExtensions().getPlatform(extensionsCollection.getPlatform());
                String groupId = platform.getGroupId();
                String artifactId = extensionsCollection.getArtifactId();
                String versionId = includeVersion ? getPlatformPropertyReference(platform.getName()) : null;
                return scopeTest ? newMavenTestDependency(groupId, artifactId, versionId) : newMavenDependency(groupId, artifactId, versionId);
            }
            return null;
        }

        @Override
        protected String getJacksonVersion()
        {
            return "2.10.5";
        }

        @Override
        protected String getMavenSourcePluginVersion()
        {
            return "3.2.0";
        }

        @Override
        protected void collectUpdateProjectConfigurationOperations(ProjectStructure oldStructure, ProjectFileAccessProvider.FileAccessContext fileAccessContext, Consumer<ProjectFileOperation> operationConsumer)
        {

            super.collectUpdateProjectConfigurationOperations(oldStructure, fileAccessContext, operationConsumer);

            String entitiesModuleName = getEntitiesModuleName();
            int oldVersion = oldStructure.getVersion();
            String entityValidationTestCode = getEntityValidationTestCode();
            String entityTestSuiteCode = getEntityTestSuiteCode();
            MutableList<UpdateProjectStructureExtension> extensions = Lists.mutable.withAll(ServiceLoader.load(UpdateProjectStructureExtension.class));
            String entityValidationTestPath = extensions.flatCollect(e -> e.getExtraVersionEntityValidationPaths(oldVersion)).getFirstOptional().orElse(null);
            String entityTestSuiteFilePath = extensions.flatCollect(e -> e.getExtraTestSuiteFilePaths(oldVersion)).getFirstOptional().orElse(null);
            switch (oldVersion)
            {
                case 0:
                {
                    addOrModifyModuleFile(entitiesModuleName, ENTITY_VALIDATION_TEST_FILE_PATH, entityValidationTestCode, fileAccessContext, operationConsumer);
                    addOrModifyModuleFile(entitiesModuleName, ENTITY_TEST_SUITE_FILE_PATH, entityTestSuiteCode, fileAccessContext, operationConsumer);
                    break;
                }
                case 1:
                case 2:
                {
                    moveOrAddOrModifyModuleFile(oldStructure, null, entityValidationTestPath, entitiesModuleName, ENTITY_VALIDATION_TEST_FILE_PATH, entityValidationTestCode, fileAccessContext, operationConsumer);
                    addOrModifyModuleFile(entitiesModuleName, ENTITY_TEST_SUITE_FILE_PATH, entityTestSuiteCode, fileAccessContext, operationConsumer);
                    break;
                }
                case 3:
                case 4:
                {
                    moveOrAddOrModifyModuleFile(oldStructure, null, entityValidationTestPath, entitiesModuleName, ENTITY_VALIDATION_TEST_FILE_PATH, entityValidationTestCode, fileAccessContext, operationConsumer);
                    moveOrAddOrModifyModuleFile(oldStructure, null, entityTestSuiteFilePath, entitiesModuleName, ENTITY_TEST_SUITE_FILE_PATH, entityTestSuiteCode, fileAccessContext, operationConsumer);
                    break;
                }
                case 5:
                case 6:
                case 7:
                case 8:
                case 9:
                case 10:
                {
                    String oldEntitiesModuleName = ((MultiModuleMavenProjectStructure) oldStructure).getEntitiesModuleName();
                    moveOrAddOrModifyModuleFile(oldStructure, oldEntitiesModuleName, entityValidationTestPath, entitiesModuleName, ENTITY_VALIDATION_TEST_FILE_PATH, entityValidationTestCode, fileAccessContext, operationConsumer);
                    moveOrAddOrModifyModuleFile(oldStructure, oldEntitiesModuleName, entityTestSuiteFilePath, entitiesModuleName, ENTITY_TEST_SUITE_FILE_PATH, entityTestSuiteCode, fileAccessContext, operationConsumer);
                    break;
                }
                case 11:
                {
                    String oldEntitiesModuleName = ((MultiModuleMavenProjectStructure) oldStructure).getEntitiesModuleName();
                    moveOrAddOrModifyModuleFile(oldStructure, oldEntitiesModuleName, ENTITY_VALIDATION_TEST_FILE_PATH, entitiesModuleName, ENTITY_VALIDATION_TEST_FILE_PATH, entityValidationTestCode, fileAccessContext, operationConsumer);
                    moveOrAddOrModifyModuleFile(oldStructure, oldEntitiesModuleName, ENTITY_TEST_SUITE_FILE_PATH, entitiesModuleName, ENTITY_TEST_SUITE_FILE_PATH, entityTestSuiteCode, fileAccessContext, operationConsumer);
                    break;
                }
                default:
                {
                    // do nothing
                }
            }
        }

        @Override
        public Stream<String> getModuleNamesForType(ArtifactType type)
        {
            switch (type)
            {
                case entities:
                {
                    return Stream.of(getEntitiesModuleName());
                }
                case service_execution:
                case versioned_entities:
                case file_generation:
                {
                    return Stream.of(getDefaultModuleName(type));
                }
                default:
                {
                    return Stream.empty();
                }
            }
        }

        @Override
        public Set<ArtifactType> getSupportedArtifactTypes()
        {
            return SUPPORTED_ARTIFACT_TYPES;
        }

        @Override
        protected void configureMavenProjectModel(MavenModelConfiguration configuration)
        {
            super.configureMavenProjectModel(configuration);

            // Properties
            configuration.setPropertyIfAbsent(LEGEND_SDLC_PROPERTY, LEGEND_SDLC_VERSION);
            configuration.setPropertyIfAbsent(LEGEND_ENGINE_PROPERTY, LEGEND_ENGINE_VERSION);

            // Dependency Management
            configuration.addDependencyManagement(getLegendTestUtilsDependencyWithExclusion());
            configuration.addDependencyManagement(getExtensionsCollectionDependency(GENERATION_EXTENSIONS_COLLECTION_KEY, true, false));
            configuration.addDependencyManagement(getExtensionsCollectionDependency(EXECUTION_EXTENSIONS_COLLECTION_KEY, true, false));

            // Plugin Management
            configuration.addPluginManagement(legendEntityPluginMavenHelper.getPluginManagementPlugin(this));
            configuration.addPluginManagement(legendModelGenerationPluginMavenHelper.getPluginManagementPlugin(this));
            configuration.addPluginManagement(legendFileGenerationPluginMavenHelper.getPluginManagementPlugin(this));
            configuration.addPluginManagement(legendServiceExecutionGenerationPluginMavenHelper.getPluginManagementPlugin(this));
            configuration.addPluginManagement(new LegendVersionPackagePluginMavenHelper(LEGEND_SDLC_GROUP_ID, LEGEND_SDLC_VERSION_PLUGIN, LEGEND_SDLC_PROPERTY_REFERENCE, null, null).getPluginManagementPlugin(this));
        }

        private Dependency getLegendTestUtilsDependencyWithExclusion()
        {
            Dependency dependency = this.legendTestUtilsMavenHelper.getDependency(true);
            dependency.addExclusion(newMavenExclusion(LEGEND_PURE_GROUP_ID, LEGEND_PURE_CODE_JAVA_COMPILED_CORE));
            return dependency;
        }

        @Override
        protected void configureEntitiesModule(MavenModelConfiguration configuration)
        {
            super.configureEntitiesModule(configuration);

            // Dependencies
            configuration.addDependency(this.legendTestUtilsMavenHelper.getDependency(false));
            configuration.addDependency(getExtensionsCollectionDependency(GENERATION_EXTENSIONS_COLLECTION_KEY, false, true));
            configuration.addDependency(getExtensionsCollectionDependency(EXECUTION_EXTENSIONS_COLLECTION_KEY, false, true));

            // Plugins
            configuration.addPlugin(this.legendEntityPluginMavenHelper.getPlugin(this));
            configuration.addPlugin(this.legendModelGenerationPluginMavenHelper.getPlugin(this));
            configuration.addPlugin(this.legendTestUtilsMavenHelper.getMavenSurefirePlugin(false));
        }

        @Override
        protected void configureOtherModule(ArtifactType type, String name, MavenModelConfiguration configuration)
        {
            switch (type)
            {
                case service_execution:
                {
                    configureServiceExecutionModule(configuration);
                    break;
                }
                case file_generation:
                {
                    configureFileGenerationModule(configuration);
                    break;
                }
                case versioned_entities:
                {
                    configureVersionedEntitiesModule(configuration);
                    break;
                }
                default:
                {
                    // No configuration
                }
            }
        }

        private void configureServiceExecutionModule(MavenModelConfiguration configuration)
        {
            configuration.addPlugin(this.legendServiceExecutionGenerationPluginMavenHelper.getPlugin(this));
            configuration.addPlugin(this.legendServiceExecutionGenerationPluginMavenHelper.getBuildHelperPlugin("3.0.0"));

            configuration.addDependency(getExtensionsCollectionDependency(EXECUTION_EXTENSIONS_COLLECTION_KEY, false, false));
        }

        private void configureFileGenerationModule(MavenModelConfiguration configuration)
        {
            configuration.addPlugin(this.legendFileGenerationPluginMavenHelper.getPlugin(this));
        }

        private void configureVersionedEntitiesModule(MavenModelConfiguration configuration)
        {
            String entityInputDirectory = "${project.parent.basedir}/" + getModuleFullName(getEntitiesModuleName()) + "/target/classes";
            configuration.addPlugin(new LegendVersionPackagePluginMavenHelper(LEGEND_SDLC_GROUP_ID, LEGEND_SDLC_VERSION_PLUGIN, LEGEND_SDLC_PROPERTY_REFERENCE, Collections.singletonList(entityInputDirectory), null).getPlugin(this));

            getProjectDependenciesAsMavenDependencies(ArtifactType.versioned_entities, true).forEach(configuration::addDependency);
        }

        // public for testing
        public static String getEntityValidationTestCode()
        {
            return loadJavaTestCode(4, "EntityValidationTest");
        }

        // public for testing
        public static String getEntityTestSuiteCode()
        {
            return loadJavaTestCode(4, "EntityTestSuite");
        }

        private static List<EntitySourceDirectory> getEntitySourceDirectories(ProjectConfiguration projectConfiguration)
        {
            Map<String, EntitySerializer> serializers = EntitySerializers.getAvailableSerializersByName();
            return getDefaultEntitySourceDirectoriesForSerializers(projectConfiguration, ENTITIES_MODULE_NAME, ENTITY_SERIALIZERS.collectIf(serializers::containsKey, serializers::get).castToList());
        }
    }
}