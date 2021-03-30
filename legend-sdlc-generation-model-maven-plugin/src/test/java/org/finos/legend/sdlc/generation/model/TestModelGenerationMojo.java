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

package org.finos.legend.sdlc.generation.model;

import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.testing.MojoRule;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.collections.impl.utility.ArrayIterate;
import org.finos.legend.sdlc.domain.model.entity.Entity;
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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

public class TestModelGenerationMojo
{
    private static final String GOAL = "generate-model-generations";

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Rule
    public MojoRule mojoRule = new MojoRule();

    @Test
    public void testEmptyEntitiesDirectory() throws Exception
    {
        File[] emptyEntityDirs = {this.tempFolder.newFolder("empty1"), this.tempFolder.newFolder("empty2")};
        File projectDir = buildSingleModuleProject("project", "com.gs.test", "test-project", "1.0.0", emptyEntityDirs);

        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);

        File outputDir = new File(mavenProject.getBuild().getOutputDirectory());
        assertDirectoryEmpty(outputDir);
        executeMojo(projectDir, emptyEntityDirs);
        assertDirectoryEmpty(outputDir);
    }

    @Test
    public void testNonExistentEntityDirectories() throws Exception
    {
        File tempPath = this.tempFolder.getRoot();
        File[] nonExistentEntityDirectories = {new File(tempPath, "nonexistent1"), new File(tempPath, "nonexistent2")};
        File projectDir = buildSingleModuleProject("project", "com.gs.test", "test-project", "1.0.0", nonExistentEntityDirectories);
        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);

        File outputDir = new File(mavenProject.getBuild().getOutputDirectory());
        assertDirectoryEmpty(outputDir);
        this.mojoRule.executeMojo(projectDir, GOAL);
        assertDirectoryEmpty(outputDir);
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

    private File buildSingleModuleProject(String projectDirName, String groupId, String artifactId, String version, File... includedDirectories) throws IOException
    {
        Model mavenModel = buildMavenModelWithPlugin(groupId, artifactId, version, includedDirectories, null, null, null, null, null, null);
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

    private Model buildMavenModelWithPlugin(String groupId, String artifactId, String version, File[] includedDirectories, Set<String> includedPaths, Set<String> includedPackages, File[] excludedDirectories, Set<String> excludedPaths, Set<String> excludedPackages, File outputDirectory)
    {
        Model mavenModel = buildMavenModel(groupId, artifactId, version, null);
        Build build = new Build();
        build.addPlugin(buildPlugin(includedDirectories, includedPaths, includedPackages, excludedDirectories, excludedPaths, excludedPackages, outputDirectory));
        mavenModel.setBuild(build);
        return mavenModel;
    }

    private Plugin buildPlugin(File[] includedDirectories, Set<String> includedPaths, Set<String> includedPackages, File[] excludedDirectories, Set<String> excludedPaths, Set<String> excludedPackages, File outputDirectory)
    {
        Plugin plugin = new Plugin();
        plugin.setGroupId("platform.alloy");
        plugin.setArtifactId("legend-sdlc-generation-model-maven-plugin");
        // config
        Xpp3Dom configuration = newXpp3Dom("configuration", null, null);
        plugin.setConfiguration(configuration);

        // inclusions
        if ((includedDirectories != null) || (includedPaths != null) || (includedPackages != null))
        {
            Xpp3Dom inclusions = buildGenerationSpecFilter("inclusions", includedDirectories, includedPaths, includedPackages);
            configuration.addChild(inclusions);
        }

        // exclusions
        if ((excludedDirectories != null) || (excludedPaths != null) || (excludedPackages != null))
        {
            Xpp3Dom exclusions = buildGenerationSpecFilter("exclusions", excludedDirectories, excludedPaths, excludedPackages);
            configuration.addChild(exclusions);
        }

        // output
        if (outputDirectory != null)
        {
            newXpp3Dom("outputDirectory", outputDirectory.getAbsolutePath(), configuration);
        }

        // execution
        PluginExecution execution = new PluginExecution();
        plugin.addExecution(execution);
        execution.setPhase("compile");
        execution.getGoals().add(GOAL);
        return plugin;
    }

    private Xpp3Dom buildGenerationSpecFilter(String name, File[] directories, Set<String> paths, Set<String> packages)
    {
        Xpp3Dom element = newXpp3Dom(name, null, null);
        if (directories != null)
        {
            Xpp3Dom directoriesDom = newXpp3Dom("directories", null, element);
            ArrayIterate.forEach(directories, d -> newXpp3Dom("directory", d.getAbsolutePath(), directoriesDom));
        }
        if (paths != null)
        {
            Xpp3Dom pathsDom = newXpp3Dom("paths", null, element);
            paths.forEach(p -> newXpp3Dom("path", p, pathsDom));
        }
        if (packages != null)
        {
            Xpp3Dom packagesDom = newXpp3Dom("packages", null, element);
            packages.forEach(p -> newXpp3Dom("package", p, packagesDom));
        }
        return element;
    }


    private void serializeMavenModel(Path projectDir, Model mavenModel) throws IOException
    {
        Files.createDirectories(projectDir);
        try (Writer writer = Files.newBufferedWriter(projectDir.resolve("pom.xml"), StandardCharsets.UTF_8))
        {
            new MavenXpp3Writer().write(writer, mavenModel);
        }
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

    protected void assertDirectoryEmpty(File directory) throws IOException
    {
        assertDirectoryEmpty(directory.toPath());
    }

    private void assertDirectoryEmpty(Path directory) throws IOException
    {
        if (Files.exists(directory))
        {
            try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(directory))
            {
                if (dirStream.iterator().hasNext())
                {
                    Assert.fail("Expected " + directory + " to be empty");
                }
            }
        }
    }

    private EntityLoader getEntities(String directory)
    {
        try
        {
            return getEntities(Paths.get(Objects.requireNonNull(getClass().getClassLoader().getResource(directory)).toURI()));
        }
        catch (URISyntaxException e)
        {
            throw new RuntimeException(e);
        }
    }

    private EntityLoader getEntities(Path directory)
    {
        return EntityLoader.newEntityLoader(directory);
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
