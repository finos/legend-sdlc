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

package org.finos.legend.sdlc.entities;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.testing.MojoRule;
import org.apache.maven.project.MavenProject;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;

public class TestEntityMojo
{
    private static final String GOAL = "process-entities";

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Rule
    public MojoRule mojoRule = new MojoRule();

    @Test
    public void testNoSourceDirectoriesConfigured() throws Exception
    {
        File projectDir = this.tempFolder.newFolder();
        copyPomFromResource("poms/no-source-directories.xml", projectDir);
        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);
        Path outputDir = new File(mavenProject.getBuild().getOutputDirectory()).toPath();
        Path srcMain = projectDir.toPath().resolve("src").resolve("main");

        // No source directories exist
        TestHelper.assertDirectoryEmptyOrNonExistent(outputDir);
        this.mojoRule.executeMojo(projectDir, GOAL);
        TestHelper.assertDirectoryEmptyOrNonExistent(outputDir);

        // Java (irrelevant) source directory exists
        Files.createDirectories(srcMain.resolve("java"));
        TestHelper.assertDirectoryEmptyOrNonExistent(outputDir);
        this.mojoRule.executeMojo(projectDir, GOAL);
        TestHelper.assertDirectoryEmptyOrNonExistent(outputDir);

        // Legend source directory exists (found as a default source directory)
        Path simpleJsonModelDir = TestHelper.getPathFromResource("simple-json-model");
        TestHelper.copyDirectoryTree(simpleJsonModelDir, Files.createDirectories(srcMain.resolve("legend")));
        TestHelper.assertDirectoryEmptyOrNonExistent(outputDir);
        this.mojoRule.executeMojo(projectDir, GOAL);

        Map<String, Entity> expectedEntities = TestHelper.loadEntities(simpleJsonModelDir);
        TestHelper.assertDirectoryTreeFilePaths(
                Iterate.collect(expectedEntities.keySet(), p -> Paths.get("entities" + outputDir.getFileSystem().getSeparator() + p.replace("::", outputDir.getFileSystem().getSeparator()) + ".json"), Sets.mutable.empty()),
                outputDir);
        Map<String, Entity> actualEntities = TestHelper.loadEntities(outputDir);
        TestHelper.assertEntitiesByPathEqual(expectedEntities, actualEntities);
    }

    @Test
    public void testEmptySourceDirectoriesConfigured() throws Exception
    {
        File projectDir = this.tempFolder.newFolder();
        copyPomFromResource("poms/empty-source-directories.xml", projectDir);
        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);
        Path outputDir = new File(mavenProject.getBuild().getOutputDirectory()).toPath();
        Path srcMain = projectDir.toPath().resolve("src").resolve("main");

        // No source directories exist
        TestHelper.assertDirectoryEmptyOrNonExistent(outputDir);
        this.mojoRule.executeMojo(projectDir, GOAL);
        TestHelper.assertDirectoryEmptyOrNonExistent(outputDir);

        // Java (irrelevant) source directory exists
        Files.createDirectories(srcMain.resolve("java"));
        TestHelper.assertDirectoryEmptyOrNonExistent(outputDir);
        this.mojoRule.executeMojo(projectDir, GOAL);
        TestHelper.assertDirectoryEmptyOrNonExistent(outputDir);

        // Legend source directory exists (ignored since an empty set of source directories is explicitly specified)
        Path simpleJsonModelDir = TestHelper.getPathFromResource("simple-json-model");
        TestHelper.copyDirectoryTree(simpleJsonModelDir, Files.createDirectories(srcMain.resolve("legend")));
        TestHelper.assertDirectoryEmptyOrNonExistent(outputDir);
        this.mojoRule.executeMojo(projectDir, GOAL);
        TestHelper.assertDirectoryEmptyOrNonExistent(outputDir);
        Assert.assertEquals(Collections.emptyMap(), TestHelper.loadEntities(outputDir));
    }

    @Test
    public void testUnknownSerializer() throws Exception
    {
        File projectDir = this.tempFolder.newFolder();
        copyPomFromResource("poms/unknown-serializer.xml", projectDir);
        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);
        Path outputDir = new File(mavenProject.getBuild().getOutputDirectory()).toPath();

        TestHelper.assertDirectoryEmptyOrNonExistent(outputDir);
        MojoExecutionException e = Assert.assertThrows(MojoExecutionException.class, () -> this.mojoRule.executeMojo(projectDir, GOAL));
        Assert.assertEquals("Unknown entity serializer: nonExistent", e.getMessage());
        TestHelper.assertDirectoryEmptyOrNonExistent(outputDir);
    }

    @Test
    public void testLegendSource() throws Exception
    {
        File projectDir = this.tempFolder.newFolder();
        copyPomFromResource("poms/legend-source-directory.xml", projectDir);
        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);
        Path outputDir = new File(mavenProject.getBuild().getOutputDirectory()).toPath();
        Path srcMain = projectDir.toPath().resolve("src").resolve("main");

        // No source directories exist
        TestHelper.assertDirectoryEmptyOrNonExistent(outputDir);
        this.mojoRule.executeMojo(projectDir, GOAL);
        TestHelper.assertDirectoryEmptyOrNonExistent(outputDir);

        // Java (irrelevant) source directory exists
        Files.createDirectories(srcMain.resolve("java"));
        TestHelper.assertDirectoryEmptyOrNonExistent(outputDir);
        this.mojoRule.executeMojo(projectDir, GOAL);
        TestHelper.assertDirectoryEmptyOrNonExistent(outputDir);

        // Legend source directory exists (found as a default source directory)
        Path simpleJsonModelDir = TestHelper.getPathFromResource("simple-json-model");
        TestHelper.copyDirectoryTree(simpleJsonModelDir.resolve("entities"), Files.createDirectories(srcMain.resolve("legend")));
        TestHelper.assertDirectoryEmptyOrNonExistent(outputDir);
        this.mojoRule.executeMojo(projectDir, GOAL);

        Map<String, Entity> expectedEntities = TestHelper.loadEntities(simpleJsonModelDir);
        TestHelper.assertDirectoryTreeFilePaths(
                Iterate.collect(expectedEntities.keySet(), p -> Paths.get("entities" + outputDir.getFileSystem().getSeparator() + p.replace("::", outputDir.getFileSystem().getSeparator()) + ".json"), Sets.mutable.empty()),
                outputDir);
        Map<String, Entity> actualEntities = TestHelper.loadEntities(outputDir);
        TestHelper.assertEntitiesByPathEqual(expectedEntities, actualEntities);
    }

    @Test
    public void testPureSource() throws Exception
    {
        File projectDir = this.tempFolder.newFolder();
        copyPomFromResource("poms/pure-source-directory.xml", projectDir);
        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);
        Path outputDir = new File(mavenProject.getBuild().getOutputDirectory()).toPath();
        Path srcMain = projectDir.toPath().resolve("src").resolve("main");

        // No source directories exist
        TestHelper.assertDirectoryEmptyOrNonExistent(outputDir);
        this.mojoRule.executeMojo(projectDir, GOAL);
        TestHelper.assertDirectoryEmptyOrNonExistent(outputDir);

        // Java (irrelevant) source directory exists
        Files.createDirectories(srcMain.resolve("java"));
        TestHelper.assertDirectoryEmptyOrNonExistent(outputDir);
        this.mojoRule.executeMojo(projectDir, GOAL);
        TestHelper.assertDirectoryEmptyOrNonExistent(outputDir);

        // Legend source directory exists (found as a default source directory)
        TestHelper.copyResourceDirectoryTree("simple-pure-model", Files.createDirectories(srcMain.resolve("pure")));
        TestHelper.assertDirectoryEmptyOrNonExistent(outputDir);
        this.mojoRule.executeMojo(projectDir, GOAL);

        Map<String, Entity> expectedEntities = TestHelper.loadEntities(TestHelper.getPathFromResource("simple-json-model"));
        TestHelper.assertDirectoryTreeFilePaths(
                Iterate.collect(expectedEntities.keySet(), p -> Paths.get("entities" + outputDir.getFileSystem().getSeparator() + p.replace("::", outputDir.getFileSystem().getSeparator()) + ".json"), Sets.mutable.empty()),
                outputDir);
        Map<String, Entity> actualEntities = TestHelper.loadEntities(outputDir);
        TestHelper.assertEntitiesByPathEqual(expectedEntities, actualEntities);
    }

    @Test
    public void testMixedSourceDirectoryWithFilters() throws Exception
    {
        File projectDir = this.tempFolder.newFolder();
        copyPomFromResource("poms/extension-filters.xml", projectDir);
        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);
        Path outputDir = new File(mavenProject.getBuild().getOutputDirectory()).toPath();

        Path srcMain = projectDir.toPath().resolve("src").resolve("main");
        TestHelper.copyResourceDirectoryTree("simple-mixed-model", Files.createDirectories(srcMain.resolve("mixed")));
        TestHelper.assertDirectoryEmptyOrNonExistent(outputDir);
        this.mojoRule.executeMojo(projectDir, GOAL);

        Map<String, Entity> expectedEntities = TestHelper.loadEntities(TestHelper.getPathFromResource("simple-json-model"));
        TestHelper.assertDirectoryTreeFilePaths(
                Iterate.collect(expectedEntities.keySet(), p -> Paths.get("entities" + outputDir.getFileSystem().getSeparator() + p.replace("::", outputDir.getFileSystem().getSeparator()) + ".json"), Sets.mutable.empty()),
                outputDir);
        Map<String, Entity> actualEntities = TestHelper.loadEntities(outputDir);
        TestHelper.assertEntitiesByPathEqual(expectedEntities, actualEntities);
    }

    @Test
    public void testMultipleSourceDirectories() throws Exception
    {
        File projectDir = this.tempFolder.newFolder();
        copyPomFromResource("poms/multiple-source-directories.xml", projectDir);
        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);
        Path outputDir = new File(mavenProject.getBuild().getOutputDirectory()).toPath();

        Path srcMain = projectDir.toPath().resolve("src").resolve("main");
        TestHelper.copyResourceDirectoryTree("simple-mixed-model", Files.createDirectories(srcMain.resolve("legend")), p -> p.toString().endsWith(".json"));
        TestHelper.copyResourceDirectoryTree("simple-mixed-model", Files.createDirectories(srcMain.resolve("pure")), p -> p.toString().endsWith(".pure"));
        TestHelper.assertDirectoryEmptyOrNonExistent(outputDir);
        this.mojoRule.executeMojo(projectDir, GOAL);

        Map<String, Entity> expectedEntities = TestHelper.loadEntities(TestHelper.getPathFromResource("simple-json-model"));
        TestHelper.assertDirectoryTreeFilePaths(
                Iterate.collect(expectedEntities.keySet(), p -> Paths.get("entities" + outputDir.getFileSystem().getSeparator() + p.replace("::", outputDir.getFileSystem().getSeparator()) + ".json"), Sets.mutable.empty()),
                outputDir);
        Map<String, Entity> actualEntities = TestHelper.loadEntities(outputDir);
        TestHelper.assertEntitiesByPathEqual(expectedEntities, actualEntities);
    }

    @Test
    public void testMultipleSourceDirectoriesWithConflict() throws Exception
    {
        File projectDir = this.tempFolder.newFolder();
        copyPomFromResource("poms/multiple-source-directories.xml", projectDir);
        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);
        Path outputDir = new File(mavenProject.getBuild().getOutputDirectory()).toPath();

        Path srcMain = projectDir.toPath().resolve("src").resolve("main");
        TestHelper.copyResourceDirectoryTree("simple-json-model", Files.createDirectories(srcMain.resolve("legend")));
        TestHelper.copyResourceDirectoryTree("simple-pure-model/model/domain/enums", Files.createDirectories(srcMain.resolve("pure")));
        TestHelper.assertDirectoryEmptyOrNonExistent(outputDir);

        String expectedMessage = "Error reserializing entities from " + srcMain.resolve("pure") + " using serializer \"pure\" to " + outputDir + ": Error serializing entity 'model::domain::enums::AddressType' to " + outputDir.resolve(Paths.get("entities", "model", "domain", "enums", "AddressType.json")) + ": target file already exists";
        MojoExecutionException e = Assert.assertThrows(MojoExecutionException.class, () -> this.mojoRule.executeMojo(projectDir, GOAL));
        Assert.assertEquals(expectedMessage, e.getMessage());
    }

    private void copyPomFromResource(String resourceName, File targetDir) throws IOException
    {
        copyPomFromResource(resourceName, targetDir.toPath());
    }

    private void copyPomFromResource(String resourceName, Path targetDir) throws IOException
    {
        Path resourcePath = TestHelper.getPathFromResource(resourceName);
        Files.copy(resourcePath, targetDir.resolve("pom.xml"));
    }
}
