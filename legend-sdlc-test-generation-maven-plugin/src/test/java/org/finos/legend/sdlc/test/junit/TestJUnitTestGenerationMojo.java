// Copyright 2023 Goldman Sachs
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

package org.finos.legend.sdlc.test.junit;

import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.testing.MojoRule;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.factory.SortedMaps;
import org.finos.legend.engine.testable.extension.TestableRunnerExtensionLoader;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.serialization.EntityLoader;
import org.finos.legend.sdlc.serialization.EntitySerializers;
import org.finos.legend.sdlc.tools.entity.EntityPaths;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class TestJUnitTestGenerationMojo
{
    private static final String GOAL = "generate-junit-tests";

    @ClassRule
    public static TemporaryFolder TMP_FOLDER = new TemporaryFolder();

    @Rule
    public MojoRule mojoRule = new MojoRule();

    @Test
    public void testEmptyEntitiesDirectory() throws Exception
    {
        File projectDir = buildSingleModuleProject("project", "org.finos.test", "test-project", "1.0.0", null, null, null, null, "org.finos.test.test_project", null, null);

        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);

        File outputDir = getExpectedOutputDir(mavenProject);
        assertDirectoryEmpty(outputDir);
        executeMojo(projectDir);
        assertDirectoryEmpty(outputDir);
    }

    @Test
    public void testNoTestables() throws Exception
    {
        File projectDir = buildSingleModuleProject("project", "org.finos.test", "test-project", "1.0.0", null, null, null, null, "org.finos.test.test_project", null, null);
        Set<String> testableClassifiers = TestableRunnerExtensionLoader.getClassifierPathToTestableRunnerMap().keySet();
        writeTestEntitiesToBuildOutputDir(projectDir, e -> !testableClassifiers.contains(e.getClassifierPath()), false);

        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);

        File outputDir = getExpectedOutputDir(mavenProject);
        assertDirectoryEmpty(outputDir);
        executeMojo(projectDir);
        assertDirectoryEmpty(outputDir);
    }

    @Test
    public void testFullModel() throws Exception
    {
        File projectDir = buildSingleModuleProject("project", "org.finos.test", "test-project", "1.0.0", null, null, null, null, "org.finos.legend.sdlc.test.junit.junit4", null, null);
        writeTestEntitiesToBuildOutputDir(projectDir);

        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);

        File outputDir = getExpectedOutputDir(mavenProject);
        assertDirectoryEmpty(outputDir);
        executeMojo(projectDir);

        SortedMap<String, String> expected = loadExpectedJavaSources("org/finos/legend/sdlc/test/junit/junit4/execution/TestRelationalMapping.java", "org/finos/legend/sdlc/test/junit/junit4/legend/demo/TestSingleQuoteInResultM2M.java", "org/finos/legend/sdlc/test/junit/junit4/model/mapping/TestSourceToTargetM2M.java", "org/finos/legend/sdlc/test/junit/junit4/testTestSuites/TestTestService.java", "org/finos/legend/sdlc/test/junit/junit4/testTestSuites/TestTestService2.java", "org/finos/legend/sdlc/test/junit/junit4/testTestSuites/TestServiceStoreMapping.java");

        SortedMap<String, String> actual = loadJavaSourcesFromDirectory(outputDir.toPath());
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testFullModelWithExcludedEntities() throws Exception
    {
        File projectDir = buildSingleModuleProject("project", "org.finos.test", "test-project", "1.0.0", null, null, Sets.mutable.with("execution::RelationalMapping", "testTestSuites::TestService2"), null, "org.finos.legend.sdlc.test.junit.junit4", null, null);
        writeTestEntitiesToBuildOutputDir(projectDir);

        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);

        File outputDir = getExpectedOutputDir(mavenProject);
        assertDirectoryEmpty(outputDir);
        executeMojo(projectDir);

        SortedMap<String, String> expected = loadExpectedJavaSources("org/finos/legend/sdlc/test/junit/junit4/legend/demo/TestSingleQuoteInResultM2M.java", "org/finos/legend/sdlc/test/junit/junit4/model/mapping/TestSourceToTargetM2M.java", "org/finos/legend/sdlc/test/junit/junit4/testTestSuites/TestTestService.java", "org/finos/legend/sdlc/test/junit/junit4/testTestSuites/TestServiceStoreMapping.java");

        SortedMap<String, String> actual = loadJavaSourcesFromDirectory(outputDir.toPath());
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testFullModelWithExcludedPackages() throws Exception
    {
        File projectDir = buildSingleModuleProject("project", "org.finos.test", "test-project", "1.0.0", null, null, null, Sets.mutable.with("testTestSuites", "model::mapping"), "org.finos.legend.sdlc.test.junit.junit4", null, null);
        writeTestEntitiesToBuildOutputDir(projectDir);

        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);

        File outputDir = getExpectedOutputDir(mavenProject);
        assertDirectoryEmpty(outputDir);
        executeMojo(projectDir);

        SortedMap<String, String> expected = loadExpectedJavaSources("org/finos/legend/sdlc/test/junit/junit4/execution/TestRelationalMapping.java", "org/finos/legend/sdlc/test/junit/junit4/legend/demo/TestSingleQuoteInResultM2M.java");

        SortedMap<String, String> actual = loadJavaSourcesFromDirectory(outputDir.toPath());
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testFullModelWithExcludedEntitiesAndPackages() throws Exception
    {
        File projectDir = buildSingleModuleProject("project", "org.finos.test", "test-project", "1.0.0", null, null, Sets.mutable.with("execution::RelationalMapping"), Sets.mutable.with("testTestSuites"), "org.finos.legend.sdlc.test.junit.junit4", null, null);
        writeTestEntitiesToBuildOutputDir(projectDir);

        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);

        File outputDir = getExpectedOutputDir(mavenProject);
        assertDirectoryEmpty(outputDir);
        executeMojo(projectDir);

        SortedMap<String, String> expected = loadExpectedJavaSources("org/finos/legend/sdlc/test/junit/junit4/legend/demo/TestSingleQuoteInResultM2M.java", "org/finos/legend/sdlc/test/junit/junit4/model/mapping/TestSourceToTargetM2M.java");

        SortedMap<String, String> actual = loadJavaSourcesFromDirectory(outputDir.toPath());
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testFullModelWithDependencies() throws Exception
    {
        File projectDir = buildSingleModuleProject("project", "org.finos.test", "test-project", "1.0.0", null, null, null, null, "org.finos.legend.sdlc.test.junit.junit4", null, true);
        writeTestEntitiesToBuildOutputDir(projectDir, null, true);

        MavenProject mavenProject = this.mojoRule.readMavenProject(projectDir);

        File outputDir = getExpectedOutputDir(mavenProject);
        assertDirectoryEmpty(outputDir);
        executeMojo(projectDir);

        SortedMap<String, String> expected = loadExpectedJavaSources("org/finos/legend/sdlc/test/junit/junit4/execution/TestRelationalMapping.java", "org/finos/legend/sdlc/test/junit/junit4/legend/demo/TestSingleQuoteInResultM2M.java", "org/finos/legend/sdlc/test/junit/junit4/model/mapping/TestSourceToTargetM2M.java", "org/finos/legend/sdlc/test/junit/junit4/testTestSuites/TestTestService.java", "org/finos/legend/sdlc/test/junit/junit4/testTestSuites/TestTestService2.java", "org/finos/legend/sdlc/test/junit/junit4/testTestSuites/TestServiceStoreMapping.java", "org/finos/legend/sdlc/test/junit/junit4/model/TestMyMapping.java");

        SortedMap<String, String> actual = loadJavaSourcesFromDirectory(outputDir.toPath());
        Assert.assertEquals(expected, actual);
    }

    private SortedMap<String, String> loadExpectedJavaSources(String... javaSourceNames)
    {
        return loadExpectedJavaSources(Arrays.asList(javaSourceNames));
    }

    private SortedMap<String, String> loadExpectedJavaSources(Iterable<String> javaSourceNames)
    {
        SortedMap<String, String> map = SortedMaps.mutable.empty();
        javaSourceNames.forEach(javaSourceName ->
        {
            String text = loadTextResource("generated/java/" + javaSourceName);
            map.put(javaSourceName, text);
        });
        return map;
    }

    private SortedMap<String, String> loadJavaSourcesFromDirectory(Path directory)
    {
        SortedMap<String, String> map = SortedMaps.mutable.empty();
        try (Stream<Path> stream = Files.walk(directory))
        {
            stream.forEach(path ->
            {
                try
                {
                    if (Files.isRegularFile(path))
                    {
                        String relativePath = directory.relativize(path).toString().replace(path.getFileSystem().getSeparator(), "/");
                        String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                        map.put(relativePath, text);
                    }
                }
                catch (IOException e)
                {
                    throw new UncheckedIOException(e);
                }
            });
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
        return map;
    }

    private void executeMojo(File projectDir) throws Exception
    {
        this.mojoRule.executeMojo(projectDir, GOAL);
    }

    private EntityLoader getTestEntities()
    {
        return getTestEntities(false);
    }

    private EntityLoader getTestEntities(boolean includeDependencies)
    {
        try
        {
            Path entities = Paths.get(Objects.requireNonNull(Thread.currentThread().getContextClassLoader().getResource("test-entities")).toURI());
            return includeDependencies ? EntityLoader.newEntityLoader(entities, Paths.get(Objects.requireNonNull(Thread.currentThread().getContextClassLoader().getResource("dependencies-entities")).toURI())) : EntityLoader.newEntityLoader(entities);
        }
        catch (URISyntaxException e)
        {
            throw new RuntimeException(e);
        }
    }

    private void writeTestEntitiesToBuildOutputDir(File projectDirectory, boolean includeDependencies)
    {
        writeTestEntitiesToBuildOutputDir(projectDirectory, null, includeDependencies);
    }

    private void writeTestEntitiesToBuildOutputDir(File projectDirectory)
    {
        writeTestEntitiesToBuildOutputDir(projectDirectory, null, false);
    }

    private void writeTestEntitiesToBuildOutputDir(File projectDirectory, Predicate<Entity> predicate, boolean includeDependencies)
    {
        writeTestEntitiesToDirectory(projectDirectory.toPath().resolve("target").resolve("classes"), predicate, includeDependencies);
    }

    private void writeTestEntitiesToDirectory(Path directory, Predicate<Entity> predicate)
    {
        writeTestEntitiesToDirectory(directory, predicate, false);
    }

    private void writeTestEntitiesToDirectory(Path directory, Predicate<Entity> predicate, boolean includeDependencies)
    {
        try (EntityLoader entityLoader = getTestEntities(includeDependencies))
        {
            Stream<Entity> stream = entityLoader.getAllEntities();
            if (predicate != null)
            {
                stream = stream.filter(predicate);
            }
            stream.forEach(e -> writeEntityToDirectory(directory, e));
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private void writeEntityToDirectory(Path directory, Entity entity)
    {
        Path entityFilePath = directory.resolve("entities").resolve(entity.getPath().replace(EntityPaths.PACKAGE_SEPARATOR, directory.getFileSystem().getSeparator()) + ".json");
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

    private File getExpectedOutputDir(MavenProject mavenProject)
    {
        return new File(mavenProject.getBuild().getDirectory(), "generated-test-sources");
    }

    private File buildSingleModuleProject(String projectDirName, String groupId, String artifactId, String version, Set<String> includePaths, Set<String> includePackages, Set<String> excludePaths, Set<String> excludePackages, String packagePrefix, File outputDirectory, Boolean runDependencyTests) throws IOException
    {
        Model mavenModel = buildMavenModelWithPlugin(groupId, artifactId, version, includePaths, includePackages, excludePaths, excludePackages, packagePrefix, outputDirectory, runDependencyTests);
        return buildProject(projectDirName, mavenModel);
    }

    private File buildProject(String projectDirName, Model mainModel, Model... childModels) throws IOException
    {
        File projectParentDir = TMP_FOLDER.newFolder();
        File projectDir = new File(projectParentDir, projectDirName);
        projectDir.mkdirs();
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

    private Model buildMavenModelWithPlugin(String groupId, String artifactId, String version, Set<String> includePaths, Set<String> includePackages, Set<String> excludePaths, Set<String> excludePackages, String packagePrefix, File outputDirectory, Boolean runDependencyTests)
    {
        Model mavenModel = buildMavenModel(groupId, artifactId, version, null);
        Build build = new Build();
        build.addPlugin(buildPlugin(buildEntityFilterSpecification(includePaths, includePackages), buildEntityFilterSpecification(excludePaths, excludePackages), packagePrefix, outputDirectory, runDependencyTests));
        mavenModel.setBuild(build);
        return mavenModel;
    }

    private JUnitTestGenerationMojo.EntityFilterSpecification buildEntityFilterSpecification(Set<String> paths, Set<String> packages)
    {
        if ((paths == null) && (packages == null))
        {
            return null;
        }

        JUnitTestGenerationMojo.EntityFilterSpecification spec = new JUnitTestGenerationMojo.EntityFilterSpecification();
        if (paths != null)
        {
            spec.paths = paths;
        }
        if (packages != null)
        {
            spec.packages = packages;
        }
        return spec;
    }

    private Plugin buildPlugin(JUnitTestGenerationMojo.EntityFilterSpecification inclusions, JUnitTestGenerationMojo.EntityFilterSpecification exclusions, String packagePrefix, File outputDirectory, Boolean runDependencyTests)
    {
        Plugin plugin = new Plugin();
        plugin.setGroupId("org.finos.legend.sdlc");
        plugin.setArtifactId("legend-sdlc-test-generation-maven-plugin");

        // config
        Xpp3Dom configuration = newXpp3Dom("configuration", null, null);
        plugin.setConfiguration(configuration);

        if (inclusions != null)
        {
            Xpp3Dom includesElement = buildEntityFilterSpecification("inclusions", inclusions);
            configuration.addChild(includesElement);
        }
        if (exclusions != null)
        {
            Xpp3Dom excludesElement = buildEntityFilterSpecification("exclusions", exclusions);
            configuration.addChild(excludesElement);
        }
        if (packagePrefix != null)
        {
            newXpp3Dom("packagePrefix", packagePrefix, configuration);
        }
        if (outputDirectory != null)
        {
            newXpp3Dom("outputDirectory", outputDirectory.getAbsolutePath(), configuration);
        }
        if (runDependencyTests != null)
        {
            newXpp3Dom("runDependencyTests", "true", configuration);
        }

        // execution
        PluginExecution execution = new PluginExecution();
        plugin.addExecution(execution);
        execution.setPhase("generate-test-sources");
        execution.getGoals().add(GOAL);

        return plugin;
    }

    private Xpp3Dom buildEntityFilterSpecification(String name, JUnitTestGenerationMojo.EntityFilterSpecification servicesSpec)
    {
        Xpp3Dom element = newXpp3Dom(name, null, null);
        if (servicesSpec.paths != null)
        {
            Xpp3Dom servicePaths = newXpp3Dom("paths", null, element);
            servicesSpec.paths.forEach(p -> newXpp3Dom("path", p, servicePaths));
        }
        if (servicesSpec.packages != null)
        {
            Xpp3Dom packages = newXpp3Dom("packages", null, element);
            servicesSpec.packages.forEach(p -> newXpp3Dom("package", p, packages));
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
            Assert.assertFalse("Expected " + directory + " to be empty", fileStream.findAny().isPresent());
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

    private static URL toURL(File file)
    {
        try
        {
            return file.toURI().toURL();
        }
        catch (MalformedURLException e)
        {
            throw new RuntimeException(e);
        }
    }

    private static String loadTextResource(String resourceName)
    {
        URL url = Thread.currentThread().getContextClassLoader().getResource(resourceName);
        if (url == null)
        {
            throw new RuntimeException("Unable to find resource '" + resourceName + "' on context classpath");
        }
        try (Reader reader = new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))
        {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[1024];
            int read;
            while ((read = reader.read(buffer)) != -1)
            {
                builder.append(buffer, 0, read);
            }
            return Pattern.compile("\\R").matcher(builder).replaceAll("\n");
        }
        catch (IOException e)
        {
            throw new UncheckedIOException("Error reading " + resourceName + " (" + url + ")", e);
        }
    }
}
