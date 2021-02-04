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

package org.finos.legend.sdlc.versions;

import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.testing.MojoRule;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.serialization.EntityLoader;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TestVersionQualifiedPackageMojo
{
    private static final String GOAL = "version-qualify-packages";

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Rule
    public MojoRule mojoRule = new MojoRule();

    @Test
    public void testEmptyEntityDirectories() throws Exception
    {
        File[] emptyEntityDirs = {this.tempFolder.newFolder("empty1"), this.tempFolder.newFolder("empty2")};
        File projectDir = buildSingleModuleProject("project", "org.finos.legend.sdlc", "test-project", "1.0.0", emptyEntityDirs, null, null, null);

        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);

        File outputDir = new File(mavenProject.getBuild().getOutputDirectory());
        assertDirectoryEmpty(outputDir);
        this.mojoRule.executeMojo(projectDir, GOAL);
        assertDirectoryEmpty(outputDir);
    }

    @Test
    public void testNonExistentEntityDirectories() throws Exception
    {
        File tempPath = this.tempFolder.getRoot();
        File[] nonExistentEntityDirectories = {new File(tempPath, "nonexistent1"), new File(tempPath, "nonexistent2")};
        File projectDir = buildSingleModuleProject("project", "org.finos.legend.sdlc", "test-project", "1.0.0", nonExistentEntityDirectories, null, null, null);

        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);

        File outputDir = new File(mavenProject.getBuild().getOutputDirectory());
        assertDirectoryEmpty(outputDir);
        this.mojoRule.executeMojo(projectDir, GOAL);
        assertDirectoryEmpty(outputDir);
    }

    @Test
    public void testNumericVersion() throws Exception
    {
        File[] entityDirectories = getTestEntityDirectories();
        File projectDir = buildSingleModuleProject("project", "org.finos.legend.sdlc", "test-project", "4.07.219", entityDirectories, null, null, null);

        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);

        File outputDir = new File(mavenProject.getBuild().getOutputDirectory());
        assertDirectoryEmpty(outputDir);
        this.mojoRule.executeMojo(projectDir, GOAL);

        List<Entity> expected = EntityTransformationTestTools.transformEntities(EntityLoader.newEntityLoader(entityDirectories).getAllEntities().collect(Collectors.toList()), "org::finos::legend::sdlc::test_project::v4_7_219::"::concat);
        List<Entity> actual = EntityLoader.newEntityLoader(outputDir).getAllEntities().collect(Collectors.toList());
        EntityTransformationTestTools.assertEntitiesEquivalent(expected, actual);
    }

    @Test
    public void testSnapshotVersion() throws Exception
    {
        File[] entityDirectories = getTestEntityDirectories();
        File projectDir = buildSingleModuleProject("project", "org.finos.legend.sdlc.snapshot", "test-project-for-snapshot", "trunk-SNAPSHOT", entityDirectories, null, null, false);

        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);

        File outputDir = new File(mavenProject.getBuild().getOutputDirectory());
        assertDirectoryEmpty(outputDir);
        this.mojoRule.executeMojo(projectDir, GOAL);

        List<Entity> expected = EntityTransformationTestTools.transformEntities(EntityLoader.newEntityLoader(entityDirectories).getAllEntities().collect(Collectors.toList()), "org::finos::legend::sdlc::snapshot::test_project_for_snapshot::vX_X_X::"::concat);
        List<Entity> actual = EntityLoader.newEntityLoader(outputDir).getAllEntities().collect(Collectors.toList());
        EntityTransformationTestTools.assertEntitiesEquivalent(expected, actual);
    }

    @Test
    public void testVersionAlias() throws Exception
    {
        File[] entityDirectories = getTestEntityDirectories();
        File projectDir = buildSingleModuleProject("project", "org.finos.legend.sdlc.alias", "test-project-for-alias", "1.2.3", entityDirectories, null, "latest", true);

        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);

        File outputDir = new File(mavenProject.getBuild().getOutputDirectory());
        assertDirectoryEmpty(outputDir);
        this.mojoRule.executeMojo(projectDir, GOAL);

        List<Entity> expected = EntityTransformationTestTools.transformEntities(EntityLoader.newEntityLoader(entityDirectories).getAllEntities().collect(Collectors.toList()), "org::finos::legend::sdlc::alias::test_project_for_alias::latest::"::concat);
        List<Entity> actual = EntityLoader.newEntityLoader(outputDir).getAllEntities().collect(Collectors.toList());
        EntityTransformationTestTools.assertEntitiesEquivalent(expected, actual);
    }

    @Test
    public void testInvalidVersionAlias() throws Exception
    {
        File[] entityDirs = {this.tempFolder.newFolder("empty1"), this.tempFolder.newFolder("empty2")};

        try
        {
            File projectDir = buildSingleModuleProject("project1", "org.finos.legend.sdlc", "test-project", "1.0.0", entityDirs, null, "invalid version alias", null);
            this.mojoRule.executeMojo(projectDir, GOAL);
            Assert.fail("Expected exception");
        }
        catch (MojoFailureException e)
        {
            Assert.assertEquals("Invalid version alias: \"invalid version alias\"", e.getMessage());
        }

        try
        {
            File projectDir = buildSingleModuleProject("project2", "org.finos.legend.sdlc", "test-project", "1.0.0", entityDirs, null, "invalid/version\\alias", null);
            this.mojoRule.executeMojo(projectDir, GOAL);
            Assert.fail("Expected exception");
        }
        catch (MojoFailureException e)
        {
            Assert.assertEquals("Invalid version alias: \"invalid/version\\alias\"", e.getMessage());
        }
    }

    @Test
    public void testOutputDirectory() throws Exception
    {
        File[] entityDirectories = getTestEntityDirectories();
        File outputDir = this.tempFolder.newFolder("output");
        File projectDir = buildSingleModuleProject("project", "org.finos.legend.sdlc", "test-project", "1.1.2", entityDirectories, outputDir, null, null);

        assertDirectoryEmpty(outputDir);
        this.mojoRule.executeMojo(projectDir, GOAL);

        List<Entity> expected = EntityTransformationTestTools.transformEntities(EntityLoader.newEntityLoader(entityDirectories).getAllEntities().collect(Collectors.toList()), "org::finos::legend::sdlc::test_project::v1_1_2::"::concat);
        List<Entity> actual = EntityLoader.newEntityLoader(outputDir).getAllEntities().collect(Collectors.toList());
        EntityTransformationTestTools.assertEntitiesEquivalent(expected, actual);
    }

    @Test
    public void testMultiModuleProjectInfoFromDefault() throws Exception
    {
        File[] entityDirectories = getTestEntityDirectories();

        Model mainModel = buildMavenModel("org.finos.legend.sdlc", "test-project", "2.7.1", "pom");
        Model childModel = buildMavenModelWithPlugin(null, "test-project-versions", "8.8.8", entityDirectories, null, null, null);
        addModule(mainModel, childModel);
        File projectDir = buildProject("project", mainModel, childModel);
        File childProjectDir = new File(projectDir, childModel.getArtifactId());

        MavenProject childProject = this.mojoRule.readMavenProject(childProjectDir);

        File outputDir = new File(childProject.getBuild().getOutputDirectory());
        assertDirectoryEmpty(outputDir);
        this.mojoRule.executeMojo(childProjectDir, GOAL);

        List<Entity> expected = EntityTransformationTestTools.transformEntities(EntityLoader.newEntityLoader(entityDirectories).getAllEntities().collect(Collectors.toList()), "org::finos::legend::sdlc::test_project::v2_7_1::"::concat);
        List<Entity> actual = EntityLoader.newEntityLoader(outputDir).getAllEntities().collect(Collectors.toList());
        EntityTransformationTestTools.assertEntitiesEquivalent(expected, actual);
    }

    @Test
    public void testMultiModuleProjectInfoFromParent() throws Exception
    {
        File[] entityDirectories = getTestEntityDirectories();

        Model mainModel = buildMavenModel("org.finos.legend.sdlc", "test-project", "5.0.0", "pom");
        Model childModel = buildMavenModelWithPlugin(null, "test-project-versions", "1.0.0", entityDirectories, null, null, true);
        addModule(mainModel, childModel);
        File projectDir = buildProject("project", mainModel, childModel);
        File childProjectDir = new File(projectDir, childModel.getArtifactId());

        MavenProject childProject = this.mojoRule.readMavenProject(childProjectDir);

        File outputDir = new File(childProject.getBuild().getOutputDirectory());
        assertDirectoryEmpty(outputDir);
        this.mojoRule.executeMojo(childProjectDir, GOAL);

        List<Entity> expected = EntityTransformationTestTools.transformEntities(EntityLoader.newEntityLoader(entityDirectories).getAllEntities().collect(Collectors.toList()), "org::finos::legend::sdlc::test_project::v5_0_0::"::concat);
        List<Entity> actual = EntityLoader.newEntityLoader(outputDir).getAllEntities().collect(Collectors.toList());
        EntityTransformationTestTools.assertEntitiesEquivalent(expected, actual);
    }

    @Test
    public void testMultiModuleProjectInfoFromChild() throws Exception
    {
        File[] entityDirectories = getTestEntityDirectories();

        Model mainModel = buildMavenModel("org.finos.legend.sdlc", "test-project", "9.9.9", "pom");
        Model childModel = buildMavenModelWithPlugin(null, "test-project-versions", "4.3.2", entityDirectories, null, null, false);
        addModule(mainModel, childModel);
        File projectDir = buildProject("project", mainModel, childModel);
        File childProjectDir = new File(projectDir, childModel.getArtifactId());

        MavenProject childProject = this.mojoRule.readMavenProject(childProjectDir);

        File outputDir = new File(childProject.getBuild().getOutputDirectory());
        assertDirectoryEmpty(outputDir);
        this.mojoRule.executeMojo(childProjectDir, GOAL);

        List<Entity> expected = EntityTransformationTestTools.transformEntities(EntityLoader.newEntityLoader(entityDirectories).getAllEntities().collect(Collectors.toList()), "org::finos::legend::sdlc::test_project_versions::v4_3_2::"::concat);
        List<Entity> actual = EntityLoader.newEntityLoader(outputDir).getAllEntities().collect(Collectors.toList());
        EntityTransformationTestTools.assertEntitiesEquivalent(expected, actual);
    }

    private File buildSingleModuleProject(String projectDirName, String groupId, String artifactId, String version, File[] entitySourceDirectories, File outputDirectory, String versionAlias, Boolean useParentInfoIfPresent) throws IOException
    {
        Model mavenModel = buildMavenModelWithPlugin(groupId, artifactId, version, entitySourceDirectories, outputDirectory, versionAlias, useParentInfoIfPresent);
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

    private Model buildMavenModelWithPlugin(String groupId, String artifactId, String version, File[] entitySourceDirectories, File outputDirectory, String versionAlias, Boolean useParentInfoIfPresent)
    {
        Model mavenModel = buildMavenModel(groupId, artifactId, version, null);
        Build build = new Build();
        build.addPlugin(buildPlugin(entitySourceDirectories, outputDirectory, versionAlias, useParentInfoIfPresent));
        mavenModel.setBuild(build);
        return mavenModel;
    }

    private void addModule(Model parent, Model child)
    {
        parent.addModule(child.getArtifactId());

        Parent p = new Parent();
        p.setGroupId(parent.getGroupId());
        p.setArtifactId(parent.getArtifactId());
        p.setVersion(parent.getVersion());
        child.setParent(p);
    }

    private Plugin buildPlugin(File[] entitySourceDirectories, File outputDirectory, String versionAlias, Boolean useParentInfoIfPresent)
    {
        Plugin plugin = new Plugin();
        plugin.setGroupId("org.finos.legend.sdlc");
        plugin.setArtifactId("legend-sdlc-version-package-maven-plugin");

        // config
        Xpp3Dom configuration = newXpp3Dom("configuration", null, null);
        plugin.setConfiguration(configuration);

        Xpp3Dom entitySourceDirectoriesElement = newXpp3Dom("entitySourceDirectories", null, configuration);
        Arrays.stream(entitySourceDirectories).forEach(dir -> newXpp3Dom("entitySourceDirectory", dir.getAbsolutePath(), entitySourceDirectoriesElement));
        if (outputDirectory != null)
        {
            newXpp3Dom("outputDirectory", outputDirectory.getAbsolutePath(), configuration);
        }
        if (versionAlias != null)
        {
            newXpp3Dom("versionAlias", versionAlias, configuration);
        }
        if (useParentInfoIfPresent != null)
        {
            newXpp3Dom("useParentInfoIfPresent", useParentInfoIfPresent.toString(), configuration);
        }

        // execution
        PluginExecution execution = new PluginExecution();
        plugin.addExecution(execution);
        execution.setPhase("compile");
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

    private void assertDirectoryEmpty(File directory) throws IOException
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

    private File[] getTestEntityDirectories()
    {
        return new File[] {EntityTransformationTestTools.getResource(getClass().getClassLoader(), "entity-path-transformer-test-resources").toFile()};
    }
}
