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

package org.finos.legend.sdlc.generation.service;

import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.testing.MojoRule;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.Service;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.protocol.pure.v1.EntityToPureConverter;
import org.finos.legend.sdlc.serialization.EntityLoader;
import org.finos.legend.sdlc.serialization.EntitySerializers;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestServicesGenerationMojo
{
    private static final String GOAL = "generate-service-executions";
    private static final String SERVICE_CLASSIFIER = "meta::legend::service::metamodel::Service";

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Rule
    public MojoRule mojoRule = new MojoRule();

    @Test
    public void testEmptyEntitiesDirectory() throws Exception
    {
        File[] emptyEntityDirs = {this.tempFolder.newFolder("empty1"), this.tempFolder.newFolder("empty2")};
        File projectDir = buildSingleModuleProject("project", "org.finos.test", "test-project", "1.0.0", null, null, emptyEntityDirs, null, null, null, "org.finos.test.test_project", null, null);

        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);

        File outputDir = new File(mavenProject.getBuild().getOutputDirectory());
        assertDirectoryEmpty(outputDir);
        executeMojo(projectDir, emptyEntityDirs);
        assertDirectoryEmpty(outputDir);
    }

    @Test
    public void testNonExistentEntitiesDirectory() throws Exception
    {
        File tempPath = this.tempFolder.getRoot();
        File[] nonExistentEntityDirectories = {new File(tempPath, "nonexistent1"), new File(tempPath, "nonexistent2")};
        File projectDir = buildSingleModuleProject("project", "org.finos.test", "test-project", "1.0.0", null, null, nonExistentEntityDirectories, null, null, null, "org.finos.test.test_project", null, null);

        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);

        File outputDir = new File(mavenProject.getBuild().getOutputDirectory());
        assertDirectoryEmpty(outputDir);
        executeMojo(projectDir, nonExistentEntityDirectories);
        assertDirectoryEmpty(outputDir);
    }

    @Test
    public void testNoServices() throws Exception
    {
        File entitiesDir = this.tempFolder.newFolder("testEntities");
        try (EntityLoader testEntities = getTestEntities())
        {
            testEntities.getAllEntities()
                    .filter(p -> !SERVICE_CLASSIFIER.equals(p.getClassifierPath()))
                    .forEach(e -> writeEntityToDirectory(entitiesDir.toPath(), e));
        }
        File projectDir = buildSingleModuleProject("project", "org.finos.test", "test-project", "1.0.0", null, null, new File[]{entitiesDir}, null, null, null, "org.finos.test.test_project", null, null);

        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);

        File outputDir = new File(mavenProject.getBuild().getOutputDirectory());
        assertDirectoryEmpty(outputDir);
        executeMojo(projectDir, entitiesDir);
        assertDirectoryEmpty(outputDir);
    }

    @Test
    public void testWithServices() throws Exception
    {
        File entitiesDir = this.tempFolder.newFolder("testEntities");
        List<Entity> entities;
        try (EntityLoader testEntities = getTestEntities())
        {
            entities = testEntities.getAllEntities().collect(Collectors.toList());
        }
        Assert.assertEquals(5, entities.stream().filter(this::isServiceEntity).count());
        entities.forEach(e -> writeEntityToDirectory(entitiesDir.toPath(), e));
        File projectDir = buildSingleModuleProject("project", "org.finos.test", "test-project", "1.0.0", null, null, new File[]{entitiesDir}, null, null, null, "org.finos.test.test_project", null, null);

        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);

        Path outputDir = Paths.get(mavenProject.getBuild().getOutputDirectory());
        Path generatedSourceDir = Paths.get(mavenProject.getBuild().getDirectory()).resolve("generated-sources");
        assertDirectoryEmpty(outputDir);
        executeMojo(projectDir, entitiesDir);

        String separator = outputDir.getFileSystem().getSeparator();

        Set<String> expectedServicePlanPaths = entities.stream()
                .filter(this::isServiceEntity)
                .map(e -> "plans"  + separator + e.getPath().replace("::", separator) + ".json")
                .collect(Collectors.toSet());
        Set<String> actualOutputFiles = getFileStream(outputDir, true).map(Path::toString).collect(Collectors.toSet());
        Assert.assertEquals(Collections.emptyList(), expectedServicePlanPaths.stream().filter(p -> !actualOutputFiles.contains(p)).sorted().collect(Collectors.toList()));

        Set<String> expectedServiceClassJavaPaths = entities.stream()
                .filter(this::isServiceEntity)
                .map(e ->  e.getPath().replace("::", separator) + ".java")
                .collect(Collectors.toSet());
        Set<String> actualGeneratedSourceFiles = getFileStream(generatedSourceDir, true).map(Path::toString).collect(Collectors.toSet());
        Assert.assertEquals(Collections.emptyList(), expectedServiceClassJavaPaths.stream().filter(p -> !actualGeneratedSourceFiles.contains(p)).sorted().collect(Collectors.toList()));
    }

    private boolean isServiceEntity(Entity entity)
    {
        EntityToPureConverter converter = new EntityToPureConverter();
        return converter.fromEntityIfPossible(entity).filter(e -> e instanceof Service).isPresent();
    }

    private void executeMojo(File projectDir, File... entityDirectories) throws Exception
    {
        URL[] urls = Arrays.stream(entityDirectories)
                .map(File::toURI)
                .map(uri ->
                {
                    try
                    {
                        return uri.toURL();
                    }
                    catch (MalformedURLException e)
                    {
                        throw new RuntimeException(e);
                    }
                })
                .toArray(URL[]::new);
        Thread currentThread = Thread.currentThread();
        ClassLoader currentClassLoader = currentThread.getContextClassLoader();
        URLClassLoader newClassLoader = new URLClassLoader(urls, currentClassLoader);
        currentThread.setContextClassLoader(newClassLoader);
        try
        {
            this.mojoRule.executeMojo(projectDir, GOAL);
        }
        finally
        {
            currentThread.setContextClassLoader(currentClassLoader);
        }
    }

    private EntityLoader getTestEntities()
    {
        try
        {
            return EntityLoader.newEntityLoader(Paths.get(Objects.requireNonNull(getClass().getClassLoader().getResource("org/finos/legend/sdlc/generation/service")).toURI()));
        }
        catch (URISyntaxException e)
        {
            throw new RuntimeException(e);
        }
    }

    private void writeEntityToDirectory(Path directory, Entity entity)
    {
        Path entityFilePath = directory.resolve("entities").resolve(entity.getPath().replace("::", "/") + ".json");
        try
        {
            Files.createDirectories(entityFilePath.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(entityFilePath, StandardCharsets.UTF_8))
            {
                EntitySerializers.getDefaultJsonSerializer().serialize(entity, writer);
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException("Error writing " + entity.getPath() + " to " + entityFilePath, e);
        }
    }

    private File buildSingleModuleProject(String projectDirName, String groupId, String artifactId, String version, Set<String> includePaths, Set<String> includePackages, File[] includeDirectories, Set<String> excludePaths, Set<String> excludePackages, File[] excludeDirectories, String packagePrefix, File javaSourceOutputDirectory, File resourceOutputDirectory) throws IOException
    {
        Model mavenModel = buildMavenModelWithPlugin(groupId, artifactId, version, includePaths, includePackages, includeDirectories, excludePaths, excludePackages, excludeDirectories, packagePrefix, javaSourceOutputDirectory, resourceOutputDirectory);
        return buildProject(projectDirName, mavenModel);
    }

    private File buildProject(String projectDirName, Model mainModel, Model... childModels) throws IOException
    {
        File projectDir = this.tempFolder.newFolder(projectDirName);
        serializeMavenModel(projectDir, mainModel);
        for (Model childModel : childModels)
        {
            serializeMavenModel(new File(projectDir, childModel.getArtifactId()), childModel);
        }
        return projectDir;
    }

    private void serializeMavenModel(File projectDir, Model mavenModel) throws IOException
    {
        serializeMavenModel(projectDir.toPath(), mavenModel);
    }

    private void serializeMavenModel(Path projectDir, Model mavenModel) throws IOException
    {
        Files.createDirectories(projectDir);
        try (Writer writer = Files.newBufferedWriter(projectDir.resolve("pom.xml"), StandardCharsets.UTF_8))
        {
            new MavenXpp3Writer().write(writer, mavenModel);
        }
    }

    private Model buildMavenModel(String groupId, String artifactId, String version, String packaging)
    {
        Model mavenModel = new Model();
        mavenModel.setModelVersion("4.0.0");
        mavenModel.setModelEncoding(StandardCharsets.UTF_8.name());
        mavenModel.setGroupId(groupId);
        mavenModel.setArtifactId(artifactId);
        mavenModel.setVersion(version);
        mavenModel.setPackaging(packaging);
        return mavenModel;
    }

    private Model buildMavenModelWithPlugin(String groupId, String artifactId, String version, Set<String> includePaths, Set<String> includePackages, File[] includeDirectories, Set<String> excludePaths, Set<String> excludePackages, File[] excludeDirectories, String packagePrefix, File javaSourceOutputDirectory, File resourceOutputDirectory)
    {
        Model mavenModel = buildMavenModel(groupId, artifactId, version, null);
        Build build = new Build();
        build.addPlugin(buildPlugin(buildServicesSpecification(includePaths, includePackages, includeDirectories), buildServicesSpecification(excludePaths, excludePackages, excludeDirectories), packagePrefix, javaSourceOutputDirectory, resourceOutputDirectory));
        mavenModel.setBuild(build);
        return mavenModel;
    }

    private ServicesGenerationMojo.ServicesSpecification buildServicesSpecification(Set<String> paths, Set<String> packages, File[] directories)
    {
        if ((paths == null) && (packages == null) && (directories == null))
        {
            return null;
        }

        ServicesGenerationMojo.ServicesSpecification spec = new ServicesGenerationMojo.ServicesSpecification();
        if (paths != null)
        {
            spec.servicePaths = paths;
        }
        if (packages != null)
        {
            spec.packages = packages;
        }
        if (directories != null)
        {
            spec.directories = directories;
        }
        return spec;
    }

    private Plugin buildPlugin(ServicesGenerationMojo.ServicesSpecification inclusions, ServicesGenerationMojo.ServicesSpecification exclusions, String packagePrefix, File javaSourceOutputDirectory, File resourceOutputDirectory)
    {
        Plugin plugin = new Plugin();
        plugin.setGroupId("platform.legend");
        plugin.setArtifactId("legend-sdlc-generation-service-maven-plugin");

        // config
        Xpp3Dom configuration = newXpp3Dom("configuration", null, null);
        plugin.setConfiguration(configuration);

        if (inclusions != null)
        {
            Xpp3Dom includesElement = buildServicesSpecification("inclusions", inclusions);
            configuration.addChild(includesElement);
        }
        if (exclusions != null)
        {
            Xpp3Dom excludesElement = buildServicesSpecification("exclusions", exclusions);
            configuration.addChild(excludesElement);
        }
        if (packagePrefix != null)
        {
            newXpp3Dom("packagePrefix", packagePrefix, configuration);
        }
        if (javaSourceOutputDirectory != null)
        {
            newXpp3Dom("javaSourceOutputDirectory", javaSourceOutputDirectory.getAbsolutePath(), configuration);
        }
        if (resourceOutputDirectory != null)
        {
            newXpp3Dom("resourceOutputDirectory", resourceOutputDirectory.getAbsolutePath(), configuration);
        }

        // execution
        PluginExecution execution = new PluginExecution();
        plugin.addExecution(execution);
        execution.setPhase("generate-sources");
        execution.getGoals().add(GOAL);

        return plugin;
    }

    private Xpp3Dom buildServicesSpecification(String name, ServicesGenerationMojo.ServicesSpecification servicesSpec)
    {
        Xpp3Dom element = newXpp3Dom(name, null, null);
        if (servicesSpec.servicePaths != null)
        {
            Xpp3Dom servicePaths = newXpp3Dom("servicePaths", null, element);
            servicesSpec.servicePaths.forEach(p -> newXpp3Dom("servicePath", p, servicePaths));
        }
        if (servicesSpec.packages != null)
        {
            Xpp3Dom packages = newXpp3Dom("packages", null, element);
            servicesSpec.packages.forEach(p -> newXpp3Dom("package", p, packages));
        }
        if (servicesSpec.directories != null)
        {
            Xpp3Dom directories = newXpp3Dom("directories", null, element);
            Arrays.stream(servicesSpec.directories).forEach(d -> newXpp3Dom("directory", d.getAbsolutePath(), directories));
        }
        return element;
    }

    private Xpp3Dom newXpp3Dom(String name, String value, Xpp3Dom parent)
    {
        Xpp3Dom element = new Xpp3Dom(name);
        if (value != null)
        {
            element.setValue(value);
        }
        if (parent != null)
        {
            parent.addChild(element);
        }
        return element;
    }

    private static void assertDirectoryEmpty(File directory)
    {
        assertDirectoryEmpty(directory.toPath());
    }

    private static void assertDirectoryEmpty(Path directory)
    {
        try (Stream<Path> fileStream = getFileStream(directory))
        {
            Assert.assertTrue("Expected " + directory + " to be empty", !fileStream.findAny().isPresent());
        }
    }

    private static Stream<Path> getFileStream(File directory)
    {
        return getFileStream(directory.toPath());
    }

    private static Stream<Path> getFileStream(Path directory)
    {
        return getFileStream(directory, false);
    }

    private static Stream<Path> getFileStream(File directory, boolean relativePaths)
    {
        return getFileStream(directory.toPath(), relativePaths);
    }

    private static Stream<Path> getFileStream(Path directory, boolean relativePaths)
    {
        if (Files.notExists(directory))
        {
            return Stream.empty();
        }
        Stream<Path> stream;
        try
        {
            stream = Files.walk(directory).filter(Files::isRegularFile);
        }
        catch (IOException e)
        {
            throw new RuntimeException("Error walking directory: " + directory, e);
        }
        if (relativePaths)
        {
            stream = stream.map(directory::relativize);
        }
        return stream;
    }
}
