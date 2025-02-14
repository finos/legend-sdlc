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

package org.finos.legend.sdlc.generation.deployment;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.serialization.EntityLoader;
import org.finos.legend.sdlc.serialization.EntitySerializers;
import org.finos.legend.sdlc.tools.entity.EntityPaths;
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
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class TestDeploymentMetadataMojo
{
    private static final Pattern LINE_BREAK = Pattern.compile("\\R");


    private static final String GOAL = "deployment-metadata";



    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Rule
    public MojoRule mojoRule = new MojoRule();


    @Test
    public void testEmptyEntitiesDirectory() throws Exception
    {
        File[] emptyEntityDirs = {this.tempFolder.newFolder("empty1"), this.tempFolder.newFolder("empty2")};

        File projectDir = buildSingleModuleProject("project", "org.finos.test", "test-project", "1.0.0", emptyEntityDirs);
        serializeProjectConfiguration(projectDir);
        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);

        File outputDir = new File(mavenProject.getBuild().getOutputDirectory());
        assertDirectoryEmpty(outputDir);
        executeMojo(projectDir, emptyEntityDirs);
        assertDirectoryEmpty(outputDir);
    }

    @Test
    public void testEmptyEntitiesDirectoryWithOut() throws Exception
    {
        File[] emptyEntityDirs = {this.tempFolder.newFolder("empty1"), this.tempFolder.newFolder("empty2")};
        Path outputFilePath = getFilePathOnTempFolder("metadata.json");
        File projectDir = buildSingleModuleProject("project", "org.finos.test", "test-project", "1.0.0", emptyEntityDirs, outputFilePath, null);
        serializeProjectConfiguration(projectDir);
        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);

        File outputDir = new File(mavenProject.getBuild().getOutputDirectory());
        assertDirectoryEmpty(outputDir);
        executeMojo(projectDir, emptyEntityDirs);
        verifyJsonEqualsFiles(outputFilePath,
                new DeploymentMetadataTest("enumTest", Lists.mutable.with("meta::pure::metamodel::type::Enumeration"), Lists.mutable.empty()),
                new DeploymentMetadataTest("classDeployTest", Lists.mutable.with("meta::pure::metamodel::type::Class"), Lists.mutable.empty())
        );
    }

    @Test
    public void testWithEntities() throws Exception
    {
        Path outputFilePath = getFilePathOnTempFolder("metadataWithElements.json");
        File entitiesDir = this.tempFolder.newFolder("testEntities");
        File[] entitiesDirectories = {entitiesDir};
        List<Entity> entities;
        try (EntityLoader testEntities = getEntities("org/finos/legend/sdlc/generation/file/allFormats"))
        {
            entities = testEntities.getAllEntities().collect(Collectors.toList());
        }
        Assert.assertEquals(13, entities.size());
        entities.forEach(e -> writeEntityToDirectory(entitiesDir.toPath(), e));

        File projectDir = buildSingleModuleProject("project", "org.finos.test", "test-project", "1.0.0", entitiesDirectories, outputFilePath, true);
        serializeProjectConfiguration(projectDir);
        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);

        File outputDir = new File(mavenProject.getBuild().getOutputDirectory());
        assertDirectoryEmpty(outputDir);
        executeMojo(projectDir, entitiesDirectories);
        verifyJsonEqualsFiles(outputFilePath,
                new DeploymentMetadataTest("enumTest", Lists.mutable.with("meta::pure::metamodel::type::Enumeration"), Lists.mutable.with("model::City", "model::Country", "model::IncType")),
                new DeploymentMetadataTest("classDeployTest", Lists.mutable.with("meta::pure::metamodel::type::Class"), Lists.mutable.with(
                        "complex::AddressWithConstraint",
                        "model::Address",
                        "model::Firm",
                        "model::Person"))

        );
    }

    @Test
    public void testWithEntitiesNoElementsInMetadata() throws Exception
    {
        Path outputFilePath = getFilePathOnTempFolder("metadata.json");
        File entitiesDir = this.tempFolder.newFolder("testEntities");
        File[] entitiesDirectories = {entitiesDir};
        List<Entity> entities;
        try (EntityLoader testEntities = getEntities("org/finos/legend/sdlc/generation/file/allFormats"))
        {
            entities = testEntities.getAllEntities().collect(Collectors.toList());
        }
        Assert.assertEquals(13, entities.size());
        entities.forEach(e -> writeEntityToDirectory(entitiesDir.toPath(), e));

        File projectDir = buildSingleModuleProject("project", "org.finos.test", "test-project", "1.0.0", entitiesDirectories, outputFilePath, false);
        serializeProjectConfiguration(projectDir);
        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);

        File outputDir = new File(mavenProject.getBuild().getOutputDirectory());
        assertDirectoryEmpty(outputDir);
        executeMojo(projectDir, entitiesDirectories);
        verifyJsonEqualsFiles(outputFilePath,
                new DeploymentMetadataTest("enumTest", Lists.mutable.with("meta::pure::metamodel::type::Enumeration"), Lists.mutable.empty()),
                new DeploymentMetadataTest("classDeployTest", Lists.mutable.with("meta::pure::metamodel::type::Class"), Lists.mutable.empty())
        );
    }

    private void writeEntityToDirectory(Path directory, Entity entity)
    {
        Path entityFilePath = directory.resolve("entities").resolve(entity.getPath().replace(EntityPaths.PACKAGE_SEPARATOR, "/") + ".json");
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

    private EntityLoader getEntities(Path directory)
    {
        return EntityLoader.newEntityLoader(directory);
    }


    private Path getFilePathOnTempFolder(String filePath)
    {
        return Paths.get(this.tempFolder.getRoot().getAbsolutePath()).resolve(filePath);
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

    static class DeploymentMetadataTest
    {
        final String key;
        final List<String> classifierPaths;
        final List<String> elements;

        DeploymentMetadataTest(String key, List<String> classifierPaths, List<String> elements)
        {
            this.key = key;
            this.classifierPaths = classifierPaths;
            this.elements = elements;
        }
    }


    public static void testSameDeploymentMetadata(List<DeploymentExtensionInfo> response, DeploymentMetadataTest... checks)
    {
        Arrays.stream(checks).forEach(check ->
        {
            Optional<DeploymentExtensionInfo> infoOptional = response.stream().filter(e -> e.key.equals(check.key)).findFirst();
            Assert.assertTrue("metadata key expected" + check.key, infoOptional.isPresent());
            DeploymentExtensionInfo info = infoOptional.get();
            Collections.sort(info.classifierPaths);
            Collections.sort(info.elements);
            Collections.sort(check.classifierPaths);
            Collections.sort(check.elements);
            Assert.assertTrue("Classifier paths mismatch in key: " + check.key + ". " + printDiff(check.classifierPaths, info.classifierPaths), info.classifierPaths.containsAll(check.classifierPaths) && check.classifierPaths.containsAll(info.classifierPaths));
            Assert.assertTrue("Element paths mismatch in key: " + check.key + ". " + printDiff(check.elements, info.elements), check.elements.containsAll(info.elements) && info.elements.containsAll(check.elements));
            response.remove(infoOptional.get());
        });
        Assert.assertTrue("expected keys not found in response: " + response.stream().map(e -> e.key).collect(Collectors.joining(",")), response.isEmpty());
    }

    private static String printDiff(List<String> actual, List<String> expected)
    {
        return "Actual: " + String.join(", ", actual) + "; expected: " + String.join(", ", expected);
    }


    private static void verifyJsonEqualsFiles(Path metadataPath, DeploymentMetadataTest... checks) throws IOException
    {
        ObjectMapper objectMapper = new ObjectMapper();
        String metadataString = new String(Files.readAllBytes(metadataPath), StandardCharsets.UTF_8);
        DeploymentMetadata metadata = objectMapper.readValue(metadataString, DeploymentMetadata.class);
        testSameDeploymentMetadata(metadata.extensionMetadata, checks);
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

    private void serializeProjectConfiguration(File projectDir) throws IOException
    {
        try (Writer writer = Files.newBufferedWriter(projectDir.toPath().resolve("project.json"), StandardCharsets.UTF_8))
        {
            writer.write("{ \"groupId\": \"org.finos.test\", \"artifactId\": \"test-project\" }");
        }
    }

    private File buildSingleModuleProject(String projectDirName, String groupId, String artifactId, String version, File[] includedDirectories) throws IOException
    {
       return buildSingleModuleProject(projectDirName, groupId, artifactId, version, includedDirectories, null, null);
    }

    private File buildSingleModuleProject(String projectDirName, String groupId, String artifactId, String version, File[] includedDirectories, Path outputDirectory, Boolean includeElements) throws IOException
    {
        Model mavenModel = buildMavenModelWithPlugin(groupId, artifactId, version, includedDirectories, outputDirectory, includeElements);
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

    private Model buildMavenModelWithPlugin(String groupId, String artifactId, String version, File[] includedDirectories, Path outputDirectory, Boolean includeElements)
    {
        Model mavenModel = buildMavenModel(groupId, artifactId, version, null);
        Build build = new Build();
        build.addPlugin(buildPlugin(includedDirectories, outputDirectory, includeElements));
        mavenModel.setBuild(build);
        return mavenModel;
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

    private Plugin buildPlugin(File[] includedDirectories, Path outputFile, Boolean includeElements)
    {
        Plugin plugin = new Plugin();
        plugin.setGroupId("org.finos.legend.sdlc");
        plugin.setArtifactId("legend-sdlc-generation-file-maven-plugin");
        // config
        Xpp3Dom configuration = newXpp3Dom("configuration", null, null);
        plugin.setConfiguration(configuration);

        // inclusions
        if ((includedDirectories != null))
        {
            Xpp3Dom inclusions = newXpp3Dom("inclusions", null, null);
            if (includedDirectories != null)
            {
                ArrayIterate.forEach(includedDirectories, d -> newXpp3Dom("inclusion", d.getAbsolutePath(), inclusions));
            }

            configuration.addChild(inclusions);
        }
        if (includeElements != null)
        {
            Xpp3Dom elementDom = newXpp3Dom("includeElements", includeElements.toString(), null);
            configuration.addChild(elementDom);
        }

        // output
        if (outputFile != null)
        {
            newXpp3Dom("outputFile", outputFile.toString(), configuration);
        }

        // execution
        PluginExecution execution = new PluginExecution();
        plugin.addExecution(execution);
        execution.getGoals().add(GOAL);
        return plugin;
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

}



