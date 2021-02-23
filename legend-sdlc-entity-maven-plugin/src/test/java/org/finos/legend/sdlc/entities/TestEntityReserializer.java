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

import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.serialization.EntitySerializers;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class TestEntityReserializer
{
    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testNonExistentSourceDirectory() throws IOException
    {
        EntityReserializer reserializer = EntityReserializer.newReserializer(new PureDomainDeserializer(), EntitySerializers.getDefaultJsonSerializer());
        Path tempRoot = this.tempFolder.getRoot().toPath();
        Path sourceDir = tempRoot.resolve("source");
        Path targetDir = tempRoot.resolve("target");
        Assert.assertTrue(Files.notExists(sourceDir));
        Assert.assertTrue(Files.notExists(targetDir));

        List<String> paths = reserializer.reserializeDirectoryTree(sourceDir, targetDir);
        Assert.assertEquals(Collections.emptyList(), paths);
        Assert.assertTrue(Files.notExists(sourceDir));
        Assert.assertTrue(Files.notExists(targetDir));
    }

    @Test
    public void testEmptySourceDirectory() throws IOException
    {
        EntityReserializer reserializer = EntityReserializer.newReserializer(new PureDomainDeserializer(), EntitySerializers.getDefaultJsonSerializer());
        Path sourceDir = this.tempFolder.newFolder("source").toPath();
        Path targetDir = this.tempFolder.getRoot().toPath().resolve("target");
        TestHelper.assertDirectoryEmpty(sourceDir);
        Assert.assertTrue(Files.notExists(targetDir));

        List<String> paths = reserializer.reserializeDirectoryTree(sourceDir, targetDir);
        Assert.assertEquals(Collections.emptyList(), paths);
        TestHelper.assertDirectoryEmpty(sourceDir);
        Assert.assertTrue(Files.notExists(targetDir));
    }

    @Test
    public void testPureDomainDirectory() throws IOException
    {
        EntityReserializer reserializer = EntityReserializer.newReserializer(new PureDomainDeserializer(), EntitySerializers.getDefaultJsonSerializer());
        Path sourceDir = TestHelper.getPathFromResource("simple-pure-model");
        Path targetDir = this.tempFolder.getRoot().toPath().resolve("target");

        Assert.assertTrue(Files.isDirectory(sourceDir));
        Assert.assertTrue(Files.notExists(targetDir));

        Map<String, Entity> expectedEntities = TestHelper.loadEntitiesFromResource("simple-json-model");

        List<String> paths = reserializer.reserializeDirectoryTree(sourceDir, targetDir);
        Assert.assertEquals(expectedEntities.keySet(), Sets.mutable.withAll(paths));
        Assert.assertTrue(Files.isDirectory(sourceDir));
        Assert.assertTrue(Files.isDirectory(targetDir));
        TestHelper.assertDirectoryTreeFilePaths(
                Iterate.collect(expectedEntities.keySet(), p -> Paths.get("entities" + targetDir.getFileSystem().getSeparator() + p.replace("::", targetDir.getFileSystem().getSeparator()) + ".json"), Sets.mutable.empty()),
                targetDir);

        Map<String, Entity> actualEntities = TestHelper.loadEntities(targetDir);
        TestHelper.assertEntitiesByPathEqual(expectedEntities, actualEntities);
    }

    @Test
    public void testTargetFileAlreadyExists() throws IOException
    {
        EntityReserializer reserializer = EntityReserializer.newReserializer(new PureDomainDeserializer(), EntitySerializers.getDefaultJsonSerializer());
        Path sourceDir = TestHelper.getPathFromResource("simple-pure-model/model/domain/enums");
        Path targetDir = this.tempFolder.getRoot().toPath().resolve("target");

        Assert.assertTrue(Files.isDirectory(sourceDir));
        Assert.assertTrue(Files.notExists(targetDir));

        List<String> paths = reserializer.reserializeDirectoryTree(sourceDir, targetDir);
        Assert.assertEquals(Collections.singletonList("model::domain::enums::AddressType"), paths);

        IOException e = Assert.assertThrows(IOException.class, () -> reserializer.reserializeDirectoryTree(sourceDir, targetDir));
        Assert.assertEquals(
                "Error serializing entity 'model::domain::enums::AddressType' to " + targetDir.resolve(Paths.get("entities", "model", "domain", "enums", "AddressType.json")) + ": target file already exists",
                e.getMessage());
    }

    @Test
    public void testMixedSourceDirectoryWithFiltering() throws IOException
    {
        EntityReserializer pureReserializer = EntityReserializer.newReserializer(new PureDomainDeserializer(), EntitySerializers.getDefaultJsonSerializer());
        EntityReserializer jsonReserializer = EntityReserializer.newReserializer(EntitySerializers.getDefaultJsonSerializer(), EntitySerializers.getDefaultJsonSerializer());

        Path sourceDir = TestHelper.getPathFromResource("simple-mixed-model");
        Path targetDir = this.tempFolder.getRoot().toPath().resolve("target");

        Assert.assertTrue(Files.isDirectory(sourceDir));
        Assert.assertTrue(Files.notExists(targetDir));

        Map<String, Entity> expectedEntities = TestHelper.loadEntitiesFromResource("simple-json-model");

        List<String> pureEntityPaths = pureReserializer.reserializeDirectoryTree(sourceDir, EntityReserializer.getExtensionFilter("pure"), targetDir);
        List<String> jsonEntityPaths = jsonReserializer.reserializeDirectoryTree(sourceDir, EntityReserializer.getExtensionFilter("json"), targetDir);
        Assert.assertEquals(
                Sets.mutable.with("model::domain::associations::Employment", "model::domain::classes::Address", "model::domain::classes::Person"),
                Sets.mutable.withAll(pureEntityPaths));
        Assert.assertEquals(
                Sets.mutable.with("model::domain::classes::EntityWithAddresses", "model::domain::classes::Firm", "model::domain::enums::AddressType"),
                Sets.mutable.withAll(jsonEntityPaths));
        Assert.assertTrue(Files.isDirectory(sourceDir));
        Assert.assertTrue(Files.isDirectory(targetDir));
        TestHelper.assertDirectoryTreeFilePaths(
                Iterate.collect(expectedEntities.keySet(), p -> Paths.get("entities" + targetDir.getFileSystem().getSeparator() + p.replace("::", targetDir.getFileSystem().getSeparator()) + ".json"), Sets.mutable.empty()),
                targetDir);

        Map<String, Entity> actualEntities = TestHelper.loadEntities(targetDir);
        TestHelper.assertEntitiesByPathEqual(expectedEntities, actualEntities);
    }

    @Test
    public void testGetSingleExtensionFilter()
    {
        Path pureFile = Paths.get("dir1", "dir2", "file.pure");
        Path fakePureFile = Paths.get("dir1", "dir2", "filepure");
        Path textFile = Paths.get("dir1", "dir2", "file.txt");
        Path jsonFile = Paths.get("dir1", "dir2", "file.json");
        Path fakeJsonFile = Paths.get("dir1", "dir2", "filejson");

        Predicate<Path> pureFilter = EntityReserializer.getExtensionFilter("pure");
        Assert.assertTrue(pureFilter.test(pureFile));
        Assert.assertFalse(pureFilter.test(fakePureFile));
        Assert.assertFalse(pureFilter.test(textFile));
        Assert.assertFalse(pureFilter.test(jsonFile));
        Assert.assertFalse(pureFilter.test(fakeJsonFile));

        Predicate<Path> pureFilter2 = EntityReserializer.getExtensionFilter(".pure");
        Assert.assertTrue(pureFilter2.test(pureFile));
        Assert.assertFalse(pureFilter2.test(fakePureFile));
        Assert.assertFalse(pureFilter2.test(textFile));
        Assert.assertFalse(pureFilter2.test(jsonFile));
        Assert.assertFalse(pureFilter2.test(fakeJsonFile));

        Predicate<Path> jsonFilter = EntityReserializer.getExtensionFilter("json");
        Assert.assertFalse(jsonFilter.test(pureFile));
        Assert.assertFalse(jsonFilter.test(fakePureFile));
        Assert.assertFalse(jsonFilter.test(textFile));
        Assert.assertTrue(jsonFilter.test(jsonFile));
        Assert.assertFalse(jsonFilter.test(fakeJsonFile));

        Predicate<Path> jsonFilter2 = EntityReserializer.getExtensionFilter(".json");
        Assert.assertFalse(jsonFilter2.test(pureFile));
        Assert.assertFalse(jsonFilter2.test(fakePureFile));
        Assert.assertFalse(jsonFilter2.test(textFile));
        Assert.assertTrue(jsonFilter2.test(jsonFile));
        Assert.assertFalse(jsonFilter2.test(fakeJsonFile));

        Predicate<Path> noExtensionFilter = EntityReserializer.getExtensionFilter(null);
        Assert.assertFalse(noExtensionFilter.test(pureFile));
        Assert.assertTrue(noExtensionFilter.test(fakePureFile));
        Assert.assertFalse(noExtensionFilter.test(textFile));
        Assert.assertFalse(noExtensionFilter.test(jsonFile));
        Assert.assertTrue(noExtensionFilter.test(fakeJsonFile));

        Predicate<Path> noExtensionFilter2 = EntityReserializer.getExtensionFilter("");
        Assert.assertFalse(noExtensionFilter2.test(pureFile));
        Assert.assertTrue(noExtensionFilter2.test(fakePureFile));
        Assert.assertFalse(noExtensionFilter2.test(textFile));
        Assert.assertFalse(noExtensionFilter2.test(jsonFile));
        Assert.assertTrue(noExtensionFilter2.test(fakeJsonFile));

        Predicate<Path> defaultFilter = EntityReserializer.newReserializer(new PureDomainDeserializer(), EntitySerializers.getDefaultJsonSerializer()).getDefaultExtensionFilter();
        Assert.assertTrue(defaultFilter.test(pureFile));
        Assert.assertFalse(defaultFilter.test(fakePureFile));
        Assert.assertFalse(defaultFilter.test(textFile));
        Assert.assertFalse(defaultFilter.test(jsonFile));
        Assert.assertFalse(defaultFilter.test(fakeJsonFile));
    }

    @Test
    public void testGetMultiExtensionFilter()
    {
        Path pureFile = Paths.get("dir1", "dir2", "file.pure");
        Path fakePureFile = Paths.get("dir1", "dir2", "filepure");
        Path textFile = Paths.get("dir1", "dir2", "file.txt");
        Path jsonFile = Paths.get("dir1", "dir2", "file.json");
        Path fakeJsonFile = Paths.get("dir1", "dir2", "filejson");

        Predicate<Path> pureOrTextFilter = EntityReserializer.getExtensionsFilter("pure", "txt");
        Assert.assertTrue(pureOrTextFilter.test(pureFile));
        Assert.assertFalse(pureOrTextFilter.test(fakePureFile));
        Assert.assertTrue(pureOrTextFilter.test(textFile));
        Assert.assertFalse(pureOrTextFilter.test(jsonFile));
        Assert.assertFalse(pureOrTextFilter.test(fakeJsonFile));

        Predicate<Path> emptyTextFilter = EntityReserializer.getExtensionsFilter();
        Assert.assertFalse(emptyTextFilter.test(pureFile));
        Assert.assertFalse(emptyTextFilter.test(fakePureFile));
        Assert.assertFalse(emptyTextFilter.test(textFile));
        Assert.assertFalse(emptyTextFilter.test(jsonFile));
        Assert.assertFalse(emptyTextFilter.test(fakeJsonFile));

        Predicate<Path> textOrNoExtensionFilter = EntityReserializer.getExtensionsFilter("", "txt");
        Assert.assertFalse(textOrNoExtensionFilter.test(pureFile));
        Assert.assertTrue(textOrNoExtensionFilter.test(fakePureFile));
        Assert.assertTrue(textOrNoExtensionFilter.test(textFile));
        Assert.assertFalse(textOrNoExtensionFilter.test(jsonFile));
        Assert.assertTrue(textOrNoExtensionFilter.test(fakeJsonFile));
    }
}
