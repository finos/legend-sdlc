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

package org.finos.legend.sdlc.generation.file;

import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.testing.MojoRule;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.impl.utility.ArrayIterate;
import org.eclipse.collections.impl.utility.ListIterate;
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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestFileGenerationMojo
{
    private static final Pattern LINE_BREAK = Pattern.compile("\\R");
    private static final String GENERATION_SPECIFICATION_CLASSIFIER_PATH = "meta::pure::generation::metamodel::GenerationSpecification";

    private static final String GOAL = "generate-file-generations";

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Rule
    public MojoRule mojoRule = new MojoRule();

    @Test
    public void testEmptyEntitiesDirectory() throws Exception
    {
        File[] emptyEntityDirs = {this.tempFolder.newFolder("empty1"), this.tempFolder.newFolder("empty2")};
        File projectDir = buildSingleModuleProject("project", "org.finos.test", "test-project", "1.0.0", emptyEntityDirs);

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
        File projectDir = buildSingleModuleProject("project", "org.finos.test", "test-project", "1.0.0", nonExistentEntityDirectories);
        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);

        File outputDir = new File(mavenProject.getBuild().getOutputDirectory());
        assertDirectoryEmpty(outputDir);
        this.mojoRule.executeMojo(projectDir, GOAL);
        assertDirectoryEmpty(outputDir);
    }

    @Test
    public void testNoGenerationSpecification() throws Exception
    {
        File entitiesDir = this.tempFolder.newFolder("testEntities");
        List<Entity> entities;
        try (EntityLoader testEntities = getEntities("org/finos/legend/sdlc/generation/file/allFormats"))
        {
            entities = testEntities.getAllEntities()
                    .filter(p -> !GENERATION_SPECIFICATION_CLASSIFIER_PATH.equals(p.getClassifierPath()))
                    .collect(Collectors.toList());
        }
        Assert.assertEquals(13, entities.size());
        entities.forEach(e -> writeEntityToDirectory(entitiesDir.toPath(), e));
        File projectDir = buildSingleModuleProject("project", "org.finos.test", "test-project", "1.0.0", entitiesDir);
        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);
        File outputDir = new File(mavenProject.getBuild().getOutputDirectory());
        assertDirectoryEmpty(outputDir);
        executeMojo(projectDir, entitiesDir);
        assertDirectoryEmpty(outputDir);
    }

    @Test
    public void testGenerationSpecification() throws Exception
    {
        Path expectedPath = getPath("org/finos/legend/sdlc/generation/file/allFormats/outputs");
        File entitiesDir = this.tempFolder.newFolder("testEntities");
        List<Entity> entities;
        try (EntityLoader testEntities = getEntities("org/finos/legend/sdlc/generation/file/allFormats"))
        {
            entities = testEntities.getAllEntities().collect(Collectors.toList());
        }
        Assert.assertEquals(14, entities.size());
        entities.forEach(e -> writeEntityToDirectory(entitiesDir.toPath(), e));
        File projectDir = buildSingleModuleProject("project", "org.finos.test", "test-project", "1.0.0", entitiesDir);
        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);
        Path outputDir = Paths.get(mavenProject.getBuild().getOutputDirectory());
        Path generatedSourceDir = Paths.get(mavenProject.getBuild().getDirectory()).resolve("classes");
        assertDirectoryEmpty(outputDir);
        executeMojo(projectDir, entitiesDir);
        Set<String> actualGeneratedSourceFiles = getFileStream(generatedSourceDir, true).map(Path::toString).collect(Collectors.toSet());
        Assert.assertEquals(22, actualGeneratedSourceFiles.size());
        verifyDirsAreEqual(generatedSourceDir, expectedPath);
    }

    private static class FileDiff
    {
        String fileName;
        String expected;
        String actual;

        FileDiff(String fileName, String expected, String actual)
        {
            this.fileName = fileName;
            this.expected = expected;
            this.actual = actual;
        }

        String getActual()
        {
            return actual;
        }

        String getExpected()
        {
            return expected;
        }

        String getFileName()
        {
            return fileName;
        }

        String getErrorMessage()
        {
            return "\nExpected:\n" + this.getExpected() + "\n Actual:\n" + this.getActual() + "\n for file " + this.getFileName();
        }
    }

    private static void verifyDirsAreEqual(Path actualPath, Path expectedPath) throws IOException
    {
        List<FileDiff> fileDiffs = Lists.mutable.empty();
        Files.walkFileTree(actualPath, new SimpleFileVisitor<Path>()
        {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
            {
                FileVisitResult result = super.visitFile(file, attrs);
                // get the relative file name from path "actualPath"
                Path relativize = actualPath.relativize(file);
                // construct the path for the counterpart file in "expectedPath"
                Path fileInOther = expectedPath.resolve(relativize);

                String expectedContent = new String(Files.readAllBytes(fileInOther), StandardCharsets.UTF_8);
                String actualContent = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                // FIXME: order is not preserved for cdm so we do not assert on the file content
                if (!"rosettaTypes.txt".equals(file.getFileName().toString()) && !sameContent(expectedContent, actualContent))
                {
                    fileDiffs.add(new FileDiff(file.getFileName().toString(), expectedContent, actualContent));
                }
                return result;
            }
        });
        if (fileDiffs.size() > 0)
        {
            throw new Error("Found " + fileDiffs.size() + " differences in files: " + ListIterate.collect(fileDiffs, FileDiff::getErrorMessage).makeString(","));
        }
    }

    private static boolean sameContent(String expectedContent, String actual)
    {
        // normalize line breaks so that tests behave the same regardless of OS
        return LINE_BREAK.matcher(expectedContent).replaceAll("\n").equals(LINE_BREAK.matcher(actual).replaceAll("\n"));
    }

    @Test
    public void testDifferentIncludeDirectories() throws Exception
    {
        File includedDirectory = this.tempFolder.newFolder("includedDirectories");
        File entitySourceDirectories = this.tempFolder.newFolder("entitySourceDirectories");
        List<Entity> entities;
        try (EntityLoader testEntities = getEntities("org/finos/legend/sdlc/generation/file/allFormats"))
        {
            entities = testEntities.getAllEntities()
                    .collect(Collectors.toList());
        }
        Assert.assertEquals(14, entities.size());
        entities.forEach(e -> writeEntityToDirectory(entitySourceDirectories.toPath(), e));
        entities.forEach(e -> writeEntityToDirectory(includedDirectory.toPath(), e));
        File projectDir = buildSingleModuleProject("project", "org.finos.test", "test-project", "1.0.0", includedDirectory);
        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);
        Path outputDir = Paths.get(mavenProject.getBuild().getOutputDirectory());
        Path generatedSourceDir = Paths.get(mavenProject.getBuild().getDirectory()).resolve("classes");
        assertDirectoryEmpty(outputDir);
        executeMojo(projectDir, entitySourceDirectories);
        Set<String> actualGeneratedSourceFiles = getFileStream(generatedSourceDir, true).map(Path::toString).collect(Collectors.toSet());
        Assert.assertEquals(22, actualGeneratedSourceFiles.size());
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
        return buildSingleModuleProject(projectDirName, groupId, artifactId, version, includedDirectories, null, null, null, null, null, null);
    }

    private File buildSingleModuleProject(String projectDirName, String groupId, String artifactId, String version, File[] includedDirectories, Set<String> includedPaths, Set<String> includedPackages, File[] excludedDirectories, Set<String> excludedPaths, Set<String> excludedPackages, File outputDirectory) throws IOException
    {
        Model mavenModel = buildMavenModelWithPlugin(groupId, artifactId, version, includedDirectories, includedPaths, includedPackages, excludedDirectories, excludedPaths, excludedPackages, outputDirectory);
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
        plugin.setGroupId("org.finos.legend.sdlc");
        plugin.setArtifactId("legend-sdlc-generation-file-maven-plugin");
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
        execution.setPhase("generate-sources");
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
            return getEntities(getPath(directory));
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private Path getPath(String directory)
    {
        try
        {
            return Paths.get(Objects.requireNonNull(getClass().getClassLoader().getResource(directory)).toURI());
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
