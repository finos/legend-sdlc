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
import org.eclipse.collections.impl.list.mutable.FastList;
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

import static java.nio.charset.StandardCharsets.UTF_8;

public class TestFileGenerationMojo
{
    static final String BLANK = "";
    public static final Pattern IGNORE_WHITE_SPACES = Pattern.compile("\\s+");
    private static final String GENERATION_SPECIFICATION_CLASSIFIER_PATH = "meta::pure::generation::metamodel::GenerationSpecification";
    private static final String FILE_GENERATION_CLASSIFIER_PATH = "meta::pure::generation::metamodel::GenerationConfiguration";

    private static final String GOAL = "generate-file-generations";

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Rule
    public MojoRule mojoRule = new MojoRule();

    @Test
    public void testEmptyEntitiesDirectory() throws Exception
    {
        File[] emptyEntityDirs = {this.tempFolder.newFolder("empty1"), this.tempFolder.newFolder("empty2")};
        File projectDir = buildSingleModuleProject("project", "org.finos.test", "test-project", "1.0.0", null, emptyEntityDirs, null);

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
        File projectDir = buildSingleModuleProject("project", "org.finos.test", "test-project", "1.0.0", null, nonExistentEntityDirectories, null);
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
        Assert.assertEquals(11, entities.size());
        entities.forEach(e -> writeEntityToDirectory(entitiesDir.toPath(), e));
        File projectDir = buildSingleModuleProject("project", "org.finos.test", "test-project", "1.0.0", null, new File[]{entitiesDir}, null);
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
        Assert.assertEquals(12, entities.size());
        entities.forEach(e -> writeEntityToDirectory(entitiesDir.toPath(), e));
        File projectDir = buildSingleModuleProject("project", "org.finos.test", "test-project", "1.0.0", null, new File[]{entitiesDir}, null);
        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);
        Path outputDir = Paths.get(mavenProject.getBuild().getOutputDirectory());
        Path generatedSourceDir = Paths.get(mavenProject.getBuild().getDirectory()).resolve("classes");
        assertDirectoryEmpty(outputDir);
        executeMojo(projectDir, entitiesDir);
        Set<String> actualGeneratedSourceFiles = getFileStream(generatedSourceDir, true).map(Path::toString).collect(Collectors.toSet());
        Assert.assertEquals(actualGeneratedSourceFiles.size(), 9);
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
            return "\nExpected:\n" + this.getExpected() + "\n Actual:\n" + this.getActual() + " for file " + this.getFileName();
        }
    }

    private static void verifyDirsAreEqual(Path actualPath, Path expectedPath) throws IOException
    {
        List<FileDiff> fileDiffs = new FastList<>();
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
                if (!file.getFileName().toString().equals("rosettaTypes.txt") && !sameContent(actualContent, expectedContent)
                )
                {
                    fileDiffs.add(new FileDiff(file.getFileName().toString(), actualContent, expectedContent));
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
        return IGNORE_WHITE_SPACES.matcher(expectedContent).replaceAll(BLANK).equals(IGNORE_WHITE_SPACES.matcher(actual).replaceAll(BLANK));
    }

    @Test
    public void testDifferentIncludeDirectories() throws Exception
    {
        File includedDirectories = this.tempFolder.newFolder("includedDirectories");
        File entitySourceDirectories = this.tempFolder.newFolder("entitySourceDirectories");
        List<Entity> entities;
        try (EntityLoader testEntities = getEntities("org/finos/legend/sdlc/generation/file/allFormats"))
        {
            entities = testEntities.getAllEntities()
                    .collect(Collectors.toList());
        }
        Assert.assertEquals(12, entities.size());
        entities.forEach(e -> writeEntityToDirectory(entitySourceDirectories.toPath(), e));
        entities.forEach(e -> writeEntityToDirectory(includedDirectories.toPath(), e));
        File projectDir = buildSingleModuleProject("project", "org.finos.test", "test-project", "1.0.0", null, new File[]{includedDirectories}, null);
        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);
        Path outputDir = Paths.get(mavenProject.getBuild().getOutputDirectory());
        Path generatedSourceDir = Paths.get(mavenProject.getBuild().getDirectory()).resolve("classes");
        assertDirectoryEmpty(outputDir);
        executeMojo(projectDir, entitySourceDirectories);
        Set<String> actualGeneratedSourceFiles = getFileStream(generatedSourceDir, true).map(Path::toString).collect(Collectors.toSet());
        Assert.assertEquals(actualGeneratedSourceFiles.size(), 9);
    }

    @Test
    public void testFileGenerationNotInProject() throws Exception
    {
        File includedDirectories = this.tempFolder.newFolder("includedDirectories");
        File entitySourceDirectories = this.tempFolder.newFolder("entitySourceDirectories");
        List<Entity> entities;
        try (EntityLoader testEntities = getEntities("org/finos/legend/sdlc/generation/file/allFormats"))
        {
            entities = testEntities.getAllEntities()
                    .collect(Collectors.toList());
        }
        Assert.assertEquals(12, entities.size());
        entities.forEach(e -> writeEntityToDirectory(entitySourceDirectories.toPath(), e));
        entities.stream().filter(p -> !FILE_GENERATION_CLASSIFIER_PATH.equals(p.getClassifierPath())).forEach(e -> writeEntityToDirectory(includedDirectories.toPath(), e));
        File projectDir = buildSingleModuleProject("project", "org.finos.test", "test-project", "1.0.0", null, new File[]{includedDirectories}, null);
        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);
        Path outputDir = Paths.get(mavenProject.getBuild().getOutputDirectory());
        assertDirectoryEmpty(outputDir);
        try
        {
            executeMojo(projectDir, entitySourceDirectories);
            Assert.fail("Execution should of failed");
        }
        catch (Exception e)
        {
            Assert.assertEquals("Error generating files: File Generation 'model::myAvro' not in current project", e.getMessage());
        }
    }

    private Model buildMavenModel(String groupId, String artifactId, String version, String packaging)
    {
        Model mavenModel = new Model();
        mavenModel.setModelVersion("4.0.0");
        mavenModel.setModelEncoding(UTF_8.name());
        mavenModel.setGroupId(groupId);
        mavenModel.setArtifactId(artifactId);
        mavenModel.setVersion(version);
        mavenModel.setPackaging(packaging);
        return mavenModel;
    }

    private File buildSingleModuleProject(String projectDirName, String groupId, String artifactId, String version, List<Entity> entities, File[] directories, File outputDirectory) throws IOException
    {
        Model mavenModel = buildMavenModelWithPlugin(groupId, artifactId, version, entities, directories, outputDirectory);
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

    private Model buildMavenModelWithPlugin(String groupId, String artifactId, String version, List<Entity> entities, File[] directories, File outputDirectory)
    {
        Model mavenModel = buildMavenModel(groupId, artifactId, version, null);
        Build build = new Build();
        build.addPlugin(buildPlugin(buildIncludedElementsSpecification(entities, directories), outputDirectory));
        mavenModel.setBuild(build);
        return mavenModel;
    }

    private Plugin buildPlugin(FileGenerationMojo.IncludedElementsSpecification inclusions, File outputDirectory)
    {
        Plugin plugin = new Plugin();
        plugin.setGroupId("org.finos.legend.sdlc");
        plugin.setArtifactId("legend-sdlc-generation-file-maven-plugin");
        // config
        Xpp3Dom configuration = newXpp3Dom("configuration", null, null);
        plugin.setConfiguration(configuration);
        // output
        if (outputDirectory != null)
        {
            newXpp3Dom("outputDirectory", outputDirectory.getAbsolutePath(), configuration);
        }
        if (inclusions != null)
        {
            Xpp3Dom includesElement = buildIncludedElementsSpecification("inclusions", inclusions);
            configuration.addChild(includesElement);
        }
        // execution
        PluginExecution execution = new PluginExecution();
        plugin.addExecution(execution);
        execution.setPhase("generate-sources");
        execution.getGoals().add(GOAL);
        return plugin;
    }

    private Xpp3Dom buildIncludedElementsSpecification(String name, FileGenerationMojo.IncludedElementsSpecification inclusions)
    {
        Xpp3Dom element = newXpp3Dom(name, null, null);
        if (inclusions.directories != null)
        {
            Xpp3Dom directories = newXpp3Dom("directories", null, element);
            Arrays.stream(inclusions.directories).forEach(d -> newXpp3Dom("directory", d.getAbsolutePath(), directories));
        }
        if (inclusions.includedGenerationEntities != null)
        {
            Xpp3Dom includedGenerationEntities = newXpp3Dom("includedGenerationEntities", null, element);
            inclusions.includedGenerationEntities.forEach(p -> newXpp3Dom("entity", p.getPath(), includedGenerationEntities));
        }
        return element;
    }

    private FileGenerationMojo.IncludedElementsSpecification buildIncludedElementsSpecification(List<Entity> entities, File[] directories)
    {
        FileGenerationMojo.IncludedElementsSpecification inclusions = new FileGenerationMojo.IncludedElementsSpecification();
        if (entities != null)
        {
            inclusions.includedGenerationEntities = entities;
        }
        if (directories != null)
        {
            inclusions.directories = directories;
        }
        return inclusions;
    }

    private void serializeMavenModel(Path projectDir, Model mavenModel) throws IOException
    {
        Files.createDirectories(projectDir);
        try (Writer writer = Files.newBufferedWriter(projectDir.resolve("pom.xml"), UTF_8))
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
            try (BufferedWriter writer = Files.newBufferedWriter(entityFilePath, UTF_8))
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
