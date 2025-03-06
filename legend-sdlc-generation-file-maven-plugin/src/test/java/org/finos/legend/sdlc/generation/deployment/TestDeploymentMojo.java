// Copyright 2025 Goldman Sachs
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.testing.MojoRule;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
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
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class TestDeploymentMojo
{

    private static final String GOAL = "run-deployment";

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
    public void testWithEntities() throws Exception
    {
        Path outputFilePath = getFilePathOnTempFolder("deploymentWithEntities.json");
        File entitiesDir = this.tempFolder.newFolder("testEntities");
        File[] entitiesDirectories = {entitiesDir};
        List<Entity> entities;
        try (EntityLoader testEntities = getEntities("org/finos/legend/sdlc/generation/file/allFormats"))
        {
            entities = testEntities.getAllEntities().collect(Collectors.toList());
        }
        Assert.assertEquals(13, entities.size());
        entities.forEach(e -> writeEntityToDirectory(entitiesDir.toPath(), e));

        File projectDir = buildSingleModuleProject("project", "org.finos.test", "test-project", "1.0.0", entitiesDirectories, outputFilePath);
        serializeProjectConfiguration(projectDir);
        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);

        File outputDir = new File(mavenProject.getBuild().getOutputDirectory());
        assertDirectoryEmpty(outputDir);
        executeMojo(projectDir, entitiesDirectories);
        Path expectedPath = getPath("org/finos/legend/sdlc/generation/deploy/deploymentWithEntities.json");
        compareDeploymentResponse(outputFilePath, expectedPath);
    }

    @Test
    public void testWithEntitiesValidate() throws Exception
    {
        Path outputFilePath = getFilePathOnTempFolder("validateWithEntities.json");
        File entitiesDir = this.tempFolder.newFolder("testEntities");
        File[] entitiesDirectories = {entitiesDir};
        List<Entity> entities;
        try (EntityLoader testEntities = getEntities("org/finos/legend/sdlc/generation/file/allFormats"))
        {
            entities = testEntities.getAllEntities().collect(Collectors.toList());
        }
        Assert.assertEquals(13, entities.size());
        entities.forEach(e -> writeEntityToDirectory(entitiesDir.toPath(), e));

        File projectDir = buildSingleModuleProject("project", "org.finos.test", "test-project", "1.0.0", entitiesDirectories, outputFilePath, "Validate", null, null);
        serializeProjectConfiguration(projectDir);
        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);

        File outputDir = new File(mavenProject.getBuild().getOutputDirectory());
        assertDirectoryEmpty(outputDir);
        executeMojo(projectDir, entitiesDirectories);
        Path expectedPath = getPath("org/finos/legend/sdlc/generation/deploy/validateWithEntities.json");
        compareDeploymentResponse(outputFilePath, expectedPath);
    }

    @Test
    public void testWithEntitiesValidateSingularElement() throws Exception
    {
        Path outputFilePath = getFilePathOnTempFolder("cityDeployment.json");
        File entitiesDir = this.tempFolder.newFolder("testEntities");
        File[] entitiesDirectories = {entitiesDir};
        List<Entity> entities;
        try (EntityLoader testEntities = getEntities("org/finos/legend/sdlc/generation/file/allFormats"))
        {
            entities = testEntities.getAllEntities().collect(Collectors.toList());
        }
        Assert.assertEquals(13, entities.size());
        entities.forEach(e -> writeEntityToDirectory(entitiesDir.toPath(), e));

        File projectDir = buildSingleModuleProject("project", "org.finos.test", "test-project", "1.0.0", entitiesDirectories, outputFilePath, "Deploy", "model::City", null);
        serializeProjectConfiguration(projectDir);
        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);

        File outputDir = new File(mavenProject.getBuild().getOutputDirectory());
        assertDirectoryEmpty(outputDir);
        executeMojo(projectDir, entitiesDirectories);
        Path expectedPath = getPath("org/finos/legend/sdlc/generation/deploy/cityDeployment.json");
        compareDeploymentResponse(outputFilePath, expectedPath);
    }

    @Test
    public void testFail() throws Exception
    {
        Path outputFilePath = getFilePathOnTempFolder("cityDeployment.json");
        File entitiesDir = this.tempFolder.newFolder("testEntities");
        File[] entitiesDirectories = {entitiesDir};
        List<Entity> entities;
        try (EntityLoader testEntities = getEntities("org/finos/legend/sdlc/generation/file/allFormats"))
        {
            entities = testEntities.getAllEntities().collect(Collectors.toList());
        }
        Assert.assertEquals(13, entities.size());
        entities.forEach(e -> writeEntityToDirectory(entitiesDir.toPath(), e));

        File projectDir = buildSingleModuleProject("project", "org.finos.test", "test-project", "1.0.0", entitiesDirectories, outputFilePath, "Deploy", "model::City", "classDeployTest");
        serializeProjectConfiguration(projectDir);
        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);

        File outputDir = new File(mavenProject.getBuild().getOutputDirectory());
        assertDirectoryEmpty(outputDir);
        try
        {
            executeMojo(projectDir, entitiesDirectories);
            Assert.fail();
        }
        catch (Exception error)
        {
        //ignore
        }
    }

    @Test
    public void testFailFilterResponse() throws Exception
    {
        Path outputFilePath = getFilePathOnTempFolder("cityDeployment.json");
        File entitiesDir = this.tempFolder.newFolder("testEntities");
        File[] entitiesDirectories = {entitiesDir};
        List<Entity> entities;
        try (EntityLoader testEntities = getEntities("org/finos/legend/sdlc/generation/file/allFormats"))
        {
            entities = testEntities.getAllEntities().collect(Collectors.toList());
        }
        Assert.assertEquals(13, entities.size());
        entities.forEach(e -> writeEntityToDirectory(entitiesDir.toPath(), e));

        File projectDir = buildSingleModuleProject("project", "org.finos.test", "test-project", "1.0.0", entitiesDirectories, outputFilePath, "Deploy", "model::IncType", null);
        serializeProjectConfiguration(projectDir);
        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);

        File outputDir = new File(mavenProject.getBuild().getOutputDirectory());
        assertDirectoryEmpty(outputDir);
        Exception exception = assertThrows(
                MojoExecutionException.class,
                () -> executeMojo(projectDir, entitiesDirectories)

        );
        assertEquals(exception.getMessage(), "Error deploying model::IncType");

    }

    @Test
    public void testWithEntitiesDeployExtension() throws Exception
    {
        Path outputFilePath = getFilePathOnTempFolder("classDeployTestDeployment.json");
        File entitiesDir = this.tempFolder.newFolder("testEntities");
        File[] entitiesDirectories = {entitiesDir};
        List<Entity> entities;
        try (EntityLoader testEntities = getEntities("org/finos/legend/sdlc/generation/file/allFormats"))
        {
            entities = testEntities.getAllEntities().collect(Collectors.toList());
        }
        Assert.assertEquals(13, entities.size());
        entities.forEach(e -> writeEntityToDirectory(entitiesDir.toPath(), e));

        File projectDir = buildSingleModuleProject("project", "org.finos.test", "test-project", "1.0.0", entitiesDirectories, outputFilePath, "Deploy", null, "classDeployTest");
        serializeProjectConfiguration(projectDir);
        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);

        File outputDir = new File(mavenProject.getBuild().getOutputDirectory());
        assertDirectoryEmpty(outputDir);
        executeMojo(projectDir, entitiesDirectories);
        Path expectedPath = getPath("org/finos/legend/sdlc/generation/deploy/classDeployTestDeployment.json");
        compareDeploymentResponse(outputFilePath, expectedPath);
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

    private static void compareDeploymentResponse(Path actualPath, Path expectedPath) throws IOException
    {
        FileDiff fileDiff = null;
        String expectedContent = new String(Files.readAllBytes(actualPath), StandardCharsets.UTF_8);
        String actualContent = new String(Files.readAllBytes(expectedPath), StandardCharsets.UTF_8);
        if (!sameResponseContent(expectedContent, actualContent))
        {
            fileDiff =  new FileDiff(actualPath.toString(), expectedContent, actualContent);
        }
        if (fileDiff != null)
        {
            Assert.fail("Found differences in file: " + fileDiff.getErrorMessage());
        }
    }

    private static boolean sameResponseContent(String expectedContent, String actual) throws JsonProcessingException
    {
        ObjectMapper objectMapper = new ObjectMapper();
        Comparator responseComparator = new Comparator<Map<String, String>>()
        {
            @Override
            public int compare(Map<String, String> map1, Map<String, String> map2)
            {
                return (map1.get("key") + map1.getOrDefault("element", "")).compareTo(map2.get("key") + map2.getOrDefault("element", ""));
            }
        };
        List<Map<String, String>> response = objectMapper.readValue(
                expectedContent,
                objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class)
        );
        List<Map<String, String>> other = objectMapper.readValue(
                actual,
                objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class)
        );
        response.sort(responseComparator);
        other.sort(responseComparator);
        return sameJSONContent(objectMapper.writeValueAsString(response), objectMapper.writeValueAsString(other));
    }

    private static boolean sameJSONContent(String expectedContent, String actual) throws JsonProcessingException
    {
        ObjectMapper mapper = new ObjectMapper();
        List<?> map1 = mapper.readValue(expectedContent, List.class);
        List<?> map2 = mapper.readValue(actual, List.class);
        return map1.equals(map2);
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

    private File buildSingleModuleProject(String projectDirName, String groupId, String artifactId, String version, File[] includedDirectories, Path outputFile) throws IOException
    {
        return buildSingleModuleProject(projectDirName, groupId, artifactId, version, includedDirectories, outputFile, null, null, null);
    }

    private File buildSingleModuleProject(String projectDirName, String groupId, String artifactId, String version, File[] includedDirectories) throws IOException
    {
        return buildSingleModuleProject(projectDirName, groupId, artifactId, version, includedDirectories, null, null, null, null);
    }

    private File buildSingleModuleProject(String projectDirName, String groupId, String artifactId, String version, File[] includedDirectories, Path outputDirectory,String deploymentPhase, String elementFilter, String deploymentKeyFilter) throws IOException
    {
        Model mavenModel = buildMavenModelWithPlugin(groupId, artifactId, version, includedDirectories, outputDirectory, deploymentPhase, elementFilter, deploymentKeyFilter);
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

    private Model buildMavenModelWithPlugin(String groupId, String artifactId, String version, File[] includedDirectories, Path outputDirectory, String deploymentPhase, String elementFilter, String deploymentKeyFilter)
    {
        Model mavenModel = buildMavenModel(groupId, artifactId, version, null);
        Build build = new Build();
        build.addPlugin(buildPlugin(includedDirectories, outputDirectory, deploymentPhase, elementFilter, deploymentKeyFilter));
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

    private Plugin buildPlugin(File[] includedDirectories, Path outputFile, String deploymentPhase, String elementFilter, String deploymentKeyFilter)
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
        if (deploymentPhase != null)
        {
            Xpp3Dom deploymentPhaseDom = newXpp3Dom("deploymentPhase", deploymentPhase, null);
            configuration.addChild(deploymentPhaseDom);
        }

        if (elementFilter != null)
        {
            Xpp3Dom elementFilterDom = newXpp3Dom("elementFilter", elementFilter, null);
            configuration.addChild(elementFilterDom);
        }

        if (deploymentKeyFilter != null)
        {
            Xpp3Dom deploymentKeyFilterDom = newXpp3Dom("deploymentKeyFilter", deploymentKeyFilter, null);
            configuration.addChild(deploymentKeyFilterDom);
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




