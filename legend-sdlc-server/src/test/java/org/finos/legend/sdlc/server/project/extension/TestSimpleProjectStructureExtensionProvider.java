// Copyright 2022 Goldman Sachs
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

package org.finos.legend.sdlc.server.project.extension;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MapIterable;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.impl.utility.MapIterate;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.project.ProjectFileOperation;
import org.finos.legend.sdlc.server.project.ProjectStructure;
import org.finos.legend.sdlc.server.project.config.ProjectFileConfiguration;
import org.finos.legend.sdlc.server.project.extension.SimpleProjectStructureExtensionProvider.ExtensionConfiguration;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class TestSimpleProjectStructureExtensionProvider
{
    private static final String RESOURCE1 = "file1.txt";
    private static final String RESOURCE2 = "org/finos/legend/file2.txt";
    private static final String RESOURCE3 = "org/finos/legend/more/file3.txt";

    private static final String EMPTY_CONFIG_FILENAME = "empty_config.yaml";
    private static final String EMPTY_CONFIG = "[]";

    private static final String SIMPLE_CONFIG_FILENAME = "simple_config.yaml";
    private static final String SIMPLE_CONFIG = "- projectStructureVersion: 11\n" +
            "  extensions:\n" +
            "    - files:\n" +
            "        /a/b/c.txt:\n" +
            "          resourceName: " + RESOURCE1 + "\n" +
            "    - files:\n" +
            "        /a/b/c.txt:\n" +
            "          resourceName: " + RESOURCE1 + "\n" +
            "        /a/b/d.txt:\n" +
            "          resourceName: " + RESOURCE2 + "\n" +
            "- projectStructureVersion: 12\n" +
            "  extensions:\n" +
            "    - files:\n" +
            "        /d/e/f.txt:\n" +
            "          resourceName: " + RESOURCE3 + "\n";

    private static final String CUSTOM_CONFIG_FILENAME = "custom_config.yaml";
    private static final String CUSTOM_CONFIG = "- projectStructureVersion: 10\n" +
            "  extensions:\n" +
            "    - fileType: RESOURCE\n" +
            "      files:\n" +
            "        /d/e/f.txt: " + RESOURCE1 + "\n" +
            "        /a/b/c.txt: " + RESOURCE2 + "\n" +
            "    - fileType: CONTENT\n" +
            "      files:\n" +
            "        /a/b/d.txt: \"the quick brown fox\"\n" +
            "      other: jumps over the lazy dog\n";


    @ClassRule
    public static TemporaryFolder TEMP_FOLDER = new TemporaryFolder();

    private static int latestProjectStructureVersion;

    private static MapIterable<String, String> resources;
    private static Path resourcesDir;

    private static Path configsDir;

    @BeforeClass
    public static void setUp() throws IOException
    {
        latestProjectStructureVersion = ProjectStructure.getLatestProjectStructureVersion();

        // Prepare resources
        resourcesDir = TEMP_FOLDER.newFolder().toPath();

        resources = generateResourceFiles();

        writeResourceFile(RESOURCE1, resources.get(RESOURCE1));
        writeResourceFile(RESOURCE2, resources.get(RESOURCE2));
        writeResourceFile(RESOURCE3, resources.get(RESOURCE3));

        // Prepare configs
        configsDir = TEMP_FOLDER.newFolder().toPath();

        Files.write(configsDir.resolve(EMPTY_CONFIG_FILENAME), EMPTY_CONFIG.getBytes(StandardCharsets.UTF_8));
        Files.write(configsDir.resolve(SIMPLE_CONFIG_FILENAME), SIMPLE_CONFIG.getBytes(StandardCharsets.UTF_8));
        Files.write(configsDir.resolve(CUSTOM_CONFIG_FILENAME), CUSTOM_CONFIG.getBytes(StandardCharsets.UTF_8));
    }

    @AfterClass
    public static void tearDown()
    {
        resources = null;
    }

    private static MapIterable<String, String> generateResourceFiles()
    {
        MutableMap<String, String> resourceFiles = Maps.mutable.empty();
        Random random = new Random();
        resourceFiles.put(RESOURCE1, generateRandomString(random));
        resourceFiles.put(RESOURCE2, generateRandomString(random));
        resourceFiles.put(RESOURCE3, generateRandomString(random));
        resourceFiles.forEachKeyValue((file, content) ->
        {
            String newContent = new String(content.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
            Assert.assertEquals(file, content, newContent);
        });
        return resourceFiles.asUnmodifiable();
    }

    private static String generateRandomString(Random random)
    {
        int len = random.nextInt(32_767);
        int[] codePoints = new int[len];
        for (int i = 0; i < len; i++)
        {
            codePoints[i] = random.nextInt(32_767);
        }
        return new String(codePoints, 0, len);
    }

    private static Path getResourcePath(String resourceName)
    {
        return resourcesDir.resolve(resourceName);
    }

    private static void writeResourceFile(String resourceName, String content) throws IOException
    {
        Path path = getResourcePath(resourceName);
        Files.createDirectories(path.getParent());
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        Files.write(path, bytes);
    }

    @Test
    public void testEmpty()
    {
        testConfig(EMPTY_CONFIG_FILENAME, EMPTY_CONFIG, null, this::testEmpty);
    }

    @Test
    public void testEmptyWithCustomExtensionConfig()
    {
        testConfig(EMPTY_CONFIG_FILENAME, EMPTY_CONFIG, CustomExtensionConfig.class, this::testEmpty);
    }

    private void testEmpty(ProjectStructureExtensionProvider provider)
    {
        for (int i = 0; i <= latestProjectStructureVersion; i++)
        {
            Assert.assertNull(provider.getLatestVersionForProjectStructureVersion(i));
        }
    }

    @Test
    public void testSimpleConfig()
    {
        testConfig(SIMPLE_CONFIG_FILENAME, SIMPLE_CONFIG, null, this::testSimple);
    }

    private void testSimple(ProjectStructureExtensionProvider provider)
    {
        for (int i = 0; i < 11; i++)
        {
            Assert.assertNull(provider.getLatestVersionForProjectStructureVersion(i));
        }
        Assert.assertEquals(Integer.valueOf(2), provider.getLatestVersionForProjectStructureVersion(11));
        Assert.assertEquals(Integer.valueOf(1), provider.getLatestVersionForProjectStructureVersion(12));

        ProjectStructureExtension ext_11_1 = provider.getProjectStructureExtension(11, 1);
        assertFiles(Maps.mutable.with("/a/b/c.txt", resources.get(RESOURCE1)), ext_11_1);

        ProjectStructureExtension ext_11_2 = provider.getProjectStructureExtension(11, 2);
        assertFiles(Maps.mutable.with("/a/b/c.txt", resources.get(RESOURCE1), "/a/b/d.txt", resources.get(RESOURCE2)), ext_11_2);

        ProjectStructureExtension ext_12_1 = provider.getProjectStructureExtension(12, 1);
        assertFiles(Maps.mutable.with("/d/e/f.txt", resources.get(RESOURCE3)), ext_12_1);
    }

    @Test
    public void testCustomConfig()
    {
        testConfig(CUSTOM_CONFIG_FILENAME, CUSTOM_CONFIG, CustomExtensionConfig.class, this::testCustom);
    }

    private void testCustom(ProjectStructureExtensionProvider provider)
    {
        for (int i = 0; i < 10; i++)
        {
            Assert.assertNull(provider.getLatestVersionForProjectStructureVersion(i));
        }
        Assert.assertEquals(Integer.valueOf(2), provider.getLatestVersionForProjectStructureVersion(10));
        Assert.assertNull(provider.getLatestVersionForProjectStructureVersion(11));
        Assert.assertNull(provider.getLatestVersionForProjectStructureVersion(12));

        ProjectStructureExtension ext_10_1 = provider.getProjectStructureExtension(10, 1);
        Assert.assertNull(((CustomProjectStructureExtension) ext_10_1).getOther());
        assertFiles(Maps.mutable.with("/a/b/c.txt", resources.get(RESOURCE2), "/d/e/f.txt", resources.get(RESOURCE1)), ext_10_1);

        ProjectStructureExtension ext_10_2 = provider.getProjectStructureExtension(10, 2);
        Assert.assertEquals("jumps over the lazy dog", ((CustomProjectStructureExtension) ext_10_2).getOther());
        assertFiles(Maps.mutable.with("/a/b/d.txt", "the quick brown fox"), ext_10_2);
    }

    @Test
    public void testCustomProvider()
    {
        Thread currentThread = Thread.currentThread();
        ClassLoader parent = currentThread.getContextClassLoader();
        try
        {
            URLClassLoader classLoader = new URLClassLoader(new URL[]{pathToURL(configsDir), pathToURL(resourcesDir)}, parent);
            currentThread.setContextClassLoader(classLoader);
            CustomProjectStructureExtensionProvider provider = new CustomProjectStructureExtensionProvider(CUSTOM_CONFIG_FILENAME);
            testCustom(provider);
        }
        finally
        {
            currentThread.setContextClassLoader(parent);
        }
    }

    private void testConfig(String configFileName, String config, Class<? extends ExtensionConfiguration> extensionConfigClass, Consumer<? super SimpleProjectStructureExtensionProvider> test)
    {
        Path configFilePath = configsDir.resolve(configFileName);

        Thread currentThread = Thread.currentThread();
        ClassLoader parent = currentThread.getContextClassLoader();
        try
        {
            URLClassLoader classLoader = new URLClassLoader(new URL[]{pathToURL(configsDir), pathToURL(resourcesDir)}, parent);
            currentThread.setContextClassLoader(classLoader);
            testFromString(config, extensionConfigClass, test);
            testFromResource(classLoader, configFileName, extensionConfigClass, test);
            testFromURL(configFilePath, extensionConfigClass, test);
            testFromInputStream(configFilePath, extensionConfigClass, test);
            testFromReader(configFilePath, extensionConfigClass, test);
        }
        finally
        {
            currentThread.setContextClassLoader(parent);
        }
    }

    private void testFromString(String config, Class<? extends ExtensionConfiguration> extensionConfigClass, Consumer<? super SimpleProjectStructureExtensionProvider> test)
    {
        SimpleProjectStructureExtensionProvider provider = newBuilder(extensionConfigClass).withConfig(config).build();
        test.accept(provider);
    }

    private void testFromResource(ClassLoader classLoader, String resourceName, Class<? extends ExtensionConfiguration> extensionConfigClass, Consumer<? super SimpleProjectStructureExtensionProvider> test)
    {
        SimpleProjectStructureExtensionProvider provider = newBuilder(extensionConfigClass).withConfig(classLoader, resourceName).build();
        test.accept(provider);
    }

    private void testFromURL(Path path, Class<? extends ExtensionConfiguration> extensionConfigClass, Consumer<? super SimpleProjectStructureExtensionProvider> test)
    {
        URL url;
        try
        {
            url = path.toUri().toURL();
        }
        catch (MalformedURLException e)
        {
            throw new RuntimeException(e);
        }
        SimpleProjectStructureExtensionProvider provider = newBuilder(extensionConfigClass).withConfig(url).build();
        test.accept(provider);
    }

    private void testFromInputStream(Path path, Class<? extends ExtensionConfiguration> extensionConfigClass, Consumer<? super SimpleProjectStructureExtensionProvider> test)
    {
        SimpleProjectStructureExtensionProvider provider;
        try (InputStream stream = Files.newInputStream(path))
        {
            provider = newBuilder(extensionConfigClass).withConfig(stream).build();
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
        test.accept(provider);
    }

    private void testFromReader(Path path, Class<? extends ExtensionConfiguration> extensionConfigClass, Consumer<? super SimpleProjectStructureExtensionProvider> test)
    {
        SimpleProjectStructureExtensionProvider provider;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8))
        {
            provider = newBuilder(extensionConfigClass).withConfig(reader).build();
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
        test.accept(provider);
    }

    private void assertFiles(MutableMap<String, String> expectedFiles, ProjectStructureExtension extension)
    {
        MutableMap<String, String> actualFiles = getNewFiles(extension);
        Assert.assertEquals(expectedFiles.keySet(), actualFiles.keySet());
        expectedFiles.forEachKeyValue((fileName, expectedContent) ->
        {
            String actualContent = actualFiles.get(fileName);
            Assert.assertEquals(fileName, expectedContent, actualContent);
        });
    }

    private MutableMap<String, String> getNewFiles(ProjectStructureExtension extension)
    {
        MutableMap<String, String> newFiles = Maps.mutable.empty();
        extension.collectUpdateProjectConfigurationOperations(null, null, new EmptyFileAccessContext(), op ->
        {
            if (!(op instanceof ProjectFileOperation.AddFile))
            {
                throw new RuntimeException("Expected AddFile operation, got: " + op);
            }
            String content = new String(((ProjectFileOperation.AddFile) op).getContent(), StandardCharsets.UTF_8);
            newFiles.put(op.getPath(), content);
        });
        return newFiles;
    }

    private static SimpleProjectStructureExtensionProvider.Builder newBuilder(Class<? extends ExtensionConfiguration> extensionConfigClass)
    {
        return SimpleProjectStructureExtensionProvider.newBuilder(extensionConfigClass);
    }

    private static URL pathToURL(Path path)
    {
        try
        {
            return path.toUri().toURL();
        }
        catch (MalformedURLException e)
        {
            throw new RuntimeException(e);
        }
    }

    private static class CustomExtensionConfig implements ExtensionConfiguration
    {
        private final Map<String, String> files;
        private final FileType fileType;
        private final String other;

        private CustomExtensionConfig(Map<String, String> files, FileType fileType, String other)
        {
            this.files = files;
            this.fileType = fileType;
            this.other = other;
        }

        @Override
        public ProjectStructureExtension build(int projectStructureVersion, int extensionVersion)
        {
            return CustomProjectStructureExtension.newExtension(projectStructureVersion, extensionVersion, MapIterate.collect(this.files, f -> f, this::toFileConfiguration, Maps.mutable.empty()), this.other);
        }

        private ProjectFileConfiguration toFileConfiguration(String value)
        {
            switch (this.fileType)
            {
                case CONTENT:
                {
                    return ProjectFileConfiguration.newContent(value);
                }
                case PATH:
                {
                    return ProjectFileConfiguration.newPath(value);
                }
                case RESOURCE:
                {
                    return ProjectFileConfiguration.newResourceName(value);
                }
                case URL:
                {
                    return ProjectFileConfiguration.newUrl(value);
                }
                default:
                {
                    throw new UnsupportedOperationException();
                }
            }
        }

        @JsonCreator
        static CustomExtensionConfig newConfig(@JsonProperty("files") Map<String, String> files, @JsonProperty("fileType") FileType fileType, @JsonProperty("other") String other)
        {
            return new CustomExtensionConfig(files, Optional.ofNullable(fileType).orElse(FileType.RESOURCE), other);
        }
    }

    private enum FileType
    {
        CONTENT, PATH, RESOURCE, URL
    }

    private static class CustomProjectStructureExtension extends DefaultProjectStructureExtension
    {
        private final String other;

        private CustomProjectStructureExtension(int projectStructureVersion, int extensionVersion, Map<String, String> projectFiles, String other)
        {
            super(projectStructureVersion, extensionVersion, projectFiles);
            this.other = other;
        }

        public String getOther()
        {
            return this.other;
        }

        static CustomProjectStructureExtension newExtension(int projectStructureVersion, int extensionVersion, Map<String, ProjectFileConfiguration> files, String other)
        {
            return new CustomProjectStructureExtension(projectStructureVersion, extensionVersion, computeProjectFiles(files, projectStructureVersion, extensionVersion), other);
        }
    }

    private static class CustomProjectStructureExtensionProvider extends SimpleProjectStructureExtensionProvider
    {
        public CustomProjectStructureExtensionProvider(String config)
        {
            super(newBuilder(CustomExtensionConfig.class).withConfigFromResource(config));
        }
    }

    private static class EmptyFileAccessContext implements ProjectFileAccessProvider.FileAccessContext
    {
        @Override
        public Stream<ProjectFileAccessProvider.ProjectFile> getFilesInDirectories(Stream<? extends String> directories)
        {
            return Stream.empty();
        }

        @Override
        public Stream<ProjectFileAccessProvider.ProjectFile> getFilesInDirectories(Iterable<? extends String> directories)
        {
            return Stream.empty();
        }

        @Override
        public ProjectFileAccessProvider.ProjectFile getFile(String path)
        {
            return null;
        }
    }
}
