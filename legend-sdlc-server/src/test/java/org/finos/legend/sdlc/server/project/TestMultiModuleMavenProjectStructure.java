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
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactType;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.server.project.maven.LegendTestUtilsMavenHelper;
import org.finos.legend.sdlc.server.project.maven.MavenProjectStructure;
import org.finos.legend.sdlc.server.project.maven.MultiModuleMavenProjectStructure;
import org.finos.legend.sdlc.server.project.maven.MultiModuleMavenProjectStructure.ModuleConfigType;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class TestMultiModuleMavenProjectStructure<T extends MultiModuleMavenProjectStructure> extends TestMavenProjectStructure<T>
{
    protected static final LegendTestUtilsMavenHelper LEGEND_TEST_UTILS_MAVEN_HELPER = new LegendTestUtilsMavenHelper("org.finos.legend.sdlc", "legend-sdlc-test-utils", "${platform.legend-sdlc.version}");

    @Test
    public void testModuleNames_Production()
    {
        testModuleNames(ProjectType.PRODUCTION);
    }

    @Test
    public void testModuleNames_Prototype()
    {
        testModuleNames(ProjectType.PROTOTYPE);
    }

    protected void testModuleNames(ProjectType projectType)
    {
        MultiModuleMavenProjectStructure projectStructure = buildProjectStructure(projectType);

        Assert.assertTrue(projectStructure.getEntitiesModuleName(), MultiModuleMavenProjectStructure.isValidModuleName(projectStructure.getEntitiesModuleName()));
        Assert.assertEquals(1, projectStructure.getArtifactIdsForType(ArtifactType.entities).count());
        Assert.assertEquals(1, projectStructure.getArtifactIdsForType(ArtifactType.versioned_entities).count());
        Assert.assertEquals(projectStructure.getModuleFullName(projectStructure.getEntitiesModuleName()), projectStructure.getArtifactIdsForType(ArtifactType.entities).findFirst().get());
        // TODO what about the versioned-entities module name?
        Assert.assertEquals("invalid other module names", Collections.emptyList(), projectStructure.getOtherModulesNames().stream().filter(mn -> !MultiModuleMavenProjectStructure.isValidModuleName(mn)).collect(Collectors.toList()));
    }

    @Override
    protected void assertStateValid(T projectStructure, String projectId, String workspaceId, String revisionId)
    {
        super.assertStateValid(projectStructure, projectId, workspaceId, revisionId);
        ProjectFileAccessProvider.FileAccessContext fileAccessContext = this.fileAccessProvider.getFileAccessContext(projectId, workspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE, revisionId);
        Model entitiesMavenModel = projectStructure.getModuleMavenModel(projectStructure.getEntitiesModuleName(), fileAccessContext);
        Assert.assertNotNull(projectStructure.getEntitiesModuleName(), entitiesMavenModel);
        assertMavenEntitiesModelValid(entitiesMavenModel, projectStructure);

        Map<ArtifactType,List<String>> expectedArtifacts = getExpectedArtifactIdsByType(projectStructure);
        expectedArtifacts.keySet().forEach(artifactType ->
        {
            List<String> projectArtifactsForType = projectStructure.getArtifactIdsForType(artifactType).collect(Collectors.toList());
            Assert.assertEquals("same size",expectedArtifacts.get(artifactType).size(),projectArtifactsForType.size());
            Assert.assertTrue("projects artifacts contains all expected",projectArtifactsForType.containsAll(expectedArtifacts.get(artifactType)));
            Assert.assertTrue("expected contains all project artifacts",expectedArtifacts.get(artifactType).containsAll(projectArtifactsForType));

        });
        Map<String, Map<ModuleConfigType, Method>> expectedConfigMethods = getExpectedConfigMethods();
        for (String otherModuleName : projectStructure.getOtherModulesNames())
        {
            Model otherModuleMavenModel = projectStructure.getModuleMavenModel(otherModuleName, fileAccessContext);
            Assert.assertNotNull(otherModuleName, otherModuleMavenModel);
            assertMavenOtherModuleModelValid(otherModuleName, otherModuleMavenModel, projectStructure,
                    expectedConfigMethods.getOrDefault(MultiModuleMavenProjectStructure.getModuleConfigName(projectStructure.getOtherModuleArtifactType(otherModuleName)), Collections.emptyMap()));
        }
    }

    protected void collectExpectedOtherModuleNames(T projectStructure, Consumer<String> otherModuleNameConsumer)
    {
        otherModuleNameConsumer.accept(MultiModuleMavenProjectStructure.getDefaultModuleName(ArtifactType.versioned_entities));
    }

    @Override
    protected void assertMavenProjectModelValid(Model mavenModel, T projectStructure)
    {
        super.assertMavenProjectModelValid(mavenModel, projectStructure);
        Assert.assertEquals(MavenProjectStructure.POM_PACKAGING, mavenModel.getPackaging());

        Set<String> expectedModules = Stream.concat(Stream.of(projectStructure.getEntitiesModuleName()), projectStructure.getOtherModulesNames().stream())
                .map(projectStructure::getModuleFullName)
                .collect(Collectors.toSet());
        Assert.assertEquals(expectedModules, Sets.mutable.withAll(mavenModel.getModules()));
    }

    @Override
    protected void collectExpectedProjectModelDependencyManagement(T projectStructure, Consumer<Dependency> dependencyManagementConsumer)
    {
        ProjectConfiguration projectConfig = projectStructure.getProjectConfiguration();
        if (projectStructure.usesDependencyManagement())
        {
            List<ProjectDependency> projectDependencies = projectConfig.getProjectDependencies();
            if (projectDependencies != null)
            {
                projectDependencies.stream().flatMap(pd -> MavenProjectStructure.projectDependencyToAllMavenDependencies(pd, this.fileAccessProvider::getFileAccessContext, true)).forEach(dependencyManagementConsumer);
            }
            projectStructure.addJunitDependency(dependencyManagementConsumer, true);
            projectStructure.addJacksonDependency(dependencyManagementConsumer, true);
        }
        dependencyManagementConsumer.accept(MavenProjectStructure.newMavenDependency(projectConfig.getGroupId(), projectStructure.getModuleFullName(projectStructure.getEntitiesModuleName()), "0.0.1-SNAPSHOT"));
        projectStructure.getOtherModulesNames().stream().map(m -> MavenProjectStructure.newMavenDependency(projectConfig.getGroupId(), projectStructure.getModuleFullName(m), "0.0.1-SNAPSHOT")).forEach(dependencyManagementConsumer);
    }

    protected void collectExpectedProjectModelModules(T projectStructure, Consumer<String> moduleConsumer)
    {
        moduleConsumer.accept(projectStructure.getModuleFullName(projectStructure.getEntitiesModuleName()));
        projectStructure.getOtherModulesNames().stream().map(projectStructure::getModuleFullName).forEach(moduleConsumer);
    }

    protected void assertMavenEntitiesModelValid(Model mavenEntitiesModel, T projectStructure)
    {
        assertMavenModelValid(
                projectStructure.getEntitiesModuleName(),
                mavenEntitiesModel,
                projectStructure,
                ps -> ps.getProjectConfiguration().getGroupId(),
                ps -> ps.getModuleFullName(ps.getEntitiesModuleName()),
                this::getExpectedParent,
                this::collectExpectedEntitiesModelProperties,
                this::collectExpectedEntitiesModelDependencies,
                this::collectExpectedEntitiesModelPlugins,
                this::collectExpectedEntitiesModelDependencyManagement,
                null);
    }

    protected void assertMavenOtherModuleModelValid(String otherModuleName, Model mavenOtherModuleModel, T projectStructure, Map<ModuleConfigType, Method> moduleExpectedConfigMethods)
    {
        BiConsumer<? super T, BiConsumer<String, String>> propertyCollector = (ps, c) -> invokeExpectedConfigMethod(moduleExpectedConfigMethods.get(ModuleConfigType.PROPERTIES), ps, c);
        BiConsumer<? super T, Consumer<Dependency>> dependencyCollector = (ps, c) ->
        {
            invokeExpectedConfigMethod(moduleExpectedConfigMethods.get(ModuleConfigType.DEPENDENCIES), ps, c);
            invokeExpectedConfigMethod(moduleExpectedConfigMethods.get(ModuleConfigType.MODULE_DEPENDENCIES), ps, (Consumer<String>) mn -> c.accept(ps.getModuleWithNoVersionDependency(mn)));
        };
        BiConsumer<? super T, Consumer<Plugin>> pluginCollector = (ps, c) -> invokeExpectedConfigMethod(moduleExpectedConfigMethods.get(ModuleConfigType.PLUGINS), otherModuleName, ps, c);
        assertMavenModelValid(
                otherModuleName,
                mavenOtherModuleModel,
                projectStructure,
                ps -> ps.getProjectConfiguration().getGroupId(),
                ps -> ps.getModuleFullName(otherModuleName),
                this::getExpectedParent,
                propertyCollector,
                dependencyCollector,
                pluginCollector,
                null,
                null);
    }

    protected void collectExpectedEntitiesModelProperties(T projectStructure, BiConsumer<String, String> propertyConsumer)
    {
    }

    protected void collectExpectedEntitiesModelDependencies(T projectStructure, Consumer<Dependency> dependencyConsumer)
    {
        boolean includeVersions = !projectStructure.usesDependencyManagement();
        projectStructure.addJunitDependency(dependencyConsumer, includeVersions);
        projectStructure.addJacksonDependency(dependencyConsumer, includeVersions);
        List<ProjectDependency> projectDependencies = projectStructure.getProjectConfiguration().getProjectDependencies();
        if (projectDependencies != null)
        {
            projectDependencies.stream().flatMap(pd -> MavenProjectStructure.projectDependencyToMavenDependenciesForType(pd, this.fileAccessProvider::getFileAccessContext, ArtifactType.entities, includeVersions)).forEach(dependencyConsumer);
        }
    }

    protected void collectExpectedEntitiesModelPlugins(T projectStructure, Consumer<Plugin> pluginConsumer)
    {
    }

    protected void collectExpectedEntitiesModelDependencyManagement(T projectStructure, Consumer<Dependency> dependencyManagementConsumer)
    {
    }

    protected Parent getExpectedParent(T projectStructure)
    {
        Parent parent = new Parent();
        parent.setGroupId(projectStructure.getProjectConfiguration().getGroupId());
        parent.setArtifactId(projectStructure.getProjectConfiguration().getArtifactId());
        parent.setVersion(getMavenModelProjectVersion());
        return parent;
    }

    protected Map<String, Map<ModuleConfigType, Method>> getExpectedConfigMethods()
    {
        Map<String, Map<ModuleConfigType, Method>> moduleConfigMethods = Maps.mutable.empty();
        for (Class<?> cls = getClass(); (cls != TestMultiModuleMavenProjectStructure.class) && (cls != null); cls = cls.getSuperclass())
        {
            for (Method method : cls.getDeclaredMethods())
            {
                MultiModuleMavenProjectStructure.ModuleConfig moduleConfig = method.getAnnotation(MultiModuleMavenProjectStructure.ModuleConfig.class);
                if (moduleConfig != null)
                {
                    moduleConfigMethods.computeIfAbsent(MultiModuleMavenProjectStructure.getModuleConfigName(moduleConfig), n -> new EnumMap<>(ModuleConfigType.class)).putIfAbsent(moduleConfig.type(), method);
                }
            }
        }
        return moduleConfigMethods;
    }

    private <U> void invokeExpectedConfigMethod(Method method, T projectStructure, U consumer)
    {
        invokeExpectedConfigMethod(method, null, projectStructure, consumer);
    }

    private <U> void invokeExpectedConfigMethod(Method method, String name, T projectStructure, U consumer)
    {
        if (method != null)
        {
            try
            {
                if (name != null)
                {
                    method.invoke(this, name, projectStructure, consumer);
                }
                else
                {
                    method.invoke(this, projectStructure, consumer);
                }
            }
            catch (ReflectiveOperationException e)
            {
                throw new RuntimeException(e);
            }
        }
    }
}
