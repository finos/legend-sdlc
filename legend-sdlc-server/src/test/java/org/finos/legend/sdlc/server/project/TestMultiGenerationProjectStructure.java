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
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactType;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification;
import org.finos.legend.sdlc.server.project.maven.LegendVersionPackagePluginMavenHelper;
import org.finos.legend.sdlc.server.project.maven.MavenProjectStructure;
import org.finos.legend.sdlc.server.project.maven.MultiModuleMavenProjectStructure;
import org.finos.legend.sdlc.server.project.maven.MultiModuleMavenProjectStructure.ModuleConfig;
import org.junit.Assert;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public abstract class TestMultiGenerationProjectStructure<T extends MultiModuleMavenProjectStructure> extends TestMultiModuleMavenProjectStructure<T>
{
    private static final String LEGEND_PURE_GROUP_ID = "org.finos.legend.pure";
    private static final String LEGEND_PURE_CODE_JAVA_COMPILED_CORE = "legend-pure-code-java-compiled-core";

    @Override
    protected void collectExpectedFiles(T projectStructure, BiConsumer<String, String> expectedFilePathAndContentConsumer, Consumer<String> unexpectedFilePathConsumer)
    {
        super.collectExpectedFiles(projectStructure, expectedFilePathAndContentConsumer, unexpectedFilePathConsumer);
        unexpectedFilePathConsumer.accept("/test-model-java/pom.xml");
    }

    @Override
    protected void collectExpectedProjectModelDependencyManagement(T projectStructure, Consumer<Dependency> dependencyManagementConsumer)
    {
        super.collectExpectedProjectModelDependencyManagement(projectStructure, dependencyManagementConsumer);
        Dependency dependency =  LEGEND_TEST_UTILS_MAVEN_HELPER.getDependency(true);
        dependency.addExclusion(MavenProjectStructure.newMavenExclusion(LEGEND_PURE_GROUP_ID, LEGEND_PURE_CODE_JAVA_COMPILED_CORE));
        dependencyManagementConsumer.accept(dependency);
    }

    @Override
    protected void collectExpectedEntitiesModelDependencies(T projectStructure, Consumer<Dependency> dependencyConsumer)
    {
        super.collectExpectedEntitiesModelDependencies(projectStructure, dependencyConsumer);
        dependencyConsumer.accept(LEGEND_TEST_UTILS_MAVEN_HELPER.getDependency(false));
    }

    @Override
    protected void assertMultiFormatGenerationStateValid(String projectId, String workspaceId, String revisionId, ArtifactType artifactType)
    {
        ProjectConfiguration configuration = ProjectStructure.getProjectConfiguration(projectId, SourceSpecification.newSourceSpecification(workspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), revisionId, this.fileAccessProvider);
        Assert.assertNotNull(configuration);

        T projectStructure = (T) ProjectStructure.getProjectStructure(configuration);
        List<String> generationModuleName = projectStructure.getModuleNamesForType(artifactType).collect(Collectors.toList());
        Assert.assertFalse(artifactType.name() + " module does not exists", generationModuleName.isEmpty());
        Assert.assertTrue(generationModuleName.stream().allMatch(name -> projectStructure.getOtherModulesNames().contains(name)));

        Model mavenModel = MavenProjectStructure.getProjectMavenModel(this.fileAccessProvider.getFileAccessContext(projectId, workspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE, revisionId));
        Assert.assertNotNull(mavenModel);
        List<String> moduleNames = mavenModel.getModules();
        Assert.assertFalse(moduleNames.isEmpty());
        Assert.assertTrue(generationModuleName.stream().allMatch(name -> moduleNames.contains(projectStructure.getModuleFullName(name))));
        projectStructure.getModuleNamesForType(artifactType).forEach(name -> moduleNames.contains(projectStructure.getModuleFullName(name)));

        Map<String, Map<MultiModuleMavenProjectStructure.ModuleConfigType, Method>> expectedConfigMethods = getExpectedConfigMethods();

        for (String otherModuleName : projectStructure.getModuleNamesForType(artifactType).collect(Collectors.toList()))
        {
            Model otherModuleMavenModel = projectStructure.getModuleMavenModel(otherModuleName, this.fileAccessProvider.getFileAccessContext(projectId, workspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE, revisionId));
            Assert.assertNotNull(otherModuleName, otherModuleMavenModel);
            assertMavenOtherModuleModelValid(otherModuleName, otherModuleMavenModel, projectStructure,
                    expectedConfigMethods.getOrDefault(MultiModuleMavenProjectStructure.getModuleConfigName(projectStructure.getOtherModuleArtifactType(otherModuleName)), Collections.emptyMap()));
        }
    }

    @ModuleConfig(artifactType = ArtifactType.versioned_entities, type = MultiModuleMavenProjectStructure.ModuleConfigType.PLUGINS)
    protected void collectExpectedVersionPackageModulePlugins(String name, T projectStructure, Consumer<Plugin> pluginConsumer)
    {
        String entitiesDirectory = "${project.parent.basedir}" + projectStructure.getModuleFullName(projectStructure.getEntitiesModuleName()) + "/target/classes";
        pluginConsumer.accept(new LegendVersionPackagePluginMavenHelper("org.finos.legend.sdlc", "legend-sdlc-version-package-maven-plugin","${platform.legend-engine.version}", Collections.singletonList(entitiesDirectory), null).getPlugin(projectStructure));
    }

    @ModuleConfig(artifactType = ArtifactType.versioned_entities, type = MultiModuleMavenProjectStructure.ModuleConfigType.DEPENDENCIES)
    protected void collectExpectedVersionPackageModuleDependencies(T projectStructure, Consumer<Dependency> dependencyConsumer)
    {
        List<ProjectDependency> projectDependencies = projectStructure.getProjectConfiguration().getProjectDependencies();
        if (projectDependencies != null)
        {
            projectDependencies.stream().flatMap(pd -> MavenProjectStructure.projectDependencyToMavenDependenciesForType(pd, ArtifactType.versioned_entities, !projectStructure.usesDependencyManagement())).forEach(dependencyConsumer);
        }
    }

    @ModuleConfig(artifactType = ArtifactType.javaCode, type = MultiModuleMavenProjectStructure.ModuleConfigType.MODULE_DEPENDENCIES)
    protected void collectExpectedJavaModuleModuleDependencies(T projectStructure, Consumer<? super String> moduleDependencyConsumer)
    {
        moduleDependencyConsumer.accept("entities");
    }

}

