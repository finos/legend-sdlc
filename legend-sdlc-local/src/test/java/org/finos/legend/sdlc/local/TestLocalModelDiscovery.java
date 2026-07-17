// Copyright 2026 Goldman Sachs
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

package org.finos.legend.sdlc.local;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TestLocalModelDiscovery
{
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testDiscoveryWithDefaultPruning() throws IOException
    {
        Path root = this.tempFolder.getRoot().toPath();
        write(root, "README.md", "not a model");
        write(root, "analytics/model/project.json", "{}");
        write(root, "analytics/model/entities/model/Person.json", "person");
        write(root, "services/svc-model/project.json", "{}");
        write(root, "services/other/config.json", "unrelated json");
        // noise that must be pruned
        write(root, ".git/project.json", "{}");
        write(root, "analytics/target/classes/project.json", "{}");
        write(root, "web/node_modules/dep/project.json", "{}");

        List<Path> modelRoots = LocalModelDiscovery.findModelRoots(root);
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Assert.assertEquals(
                Arrays.asList(normalizedRoot.resolve("analytics").resolve("model"), normalizedRoot.resolve("services").resolve("svc-model")),
                modelRoots);
    }

    @Test
    public void testDiscoveryWithCustomPruning() throws IOException
    {
        Path root = this.tempFolder.getRoot().toPath();
        write(root, "analytics/model/project.json", "{}");
        write(root, "services/svc-model/project.json", "{}");

        List<Path> modelRoots = LocalModelDiscovery.findModelRoots(root, directory -> !"services".equals(String.valueOf(directory.getFileName())));
        Assert.assertEquals(
                Collections.singletonList(root.toAbsolutePath().normalize().resolve("analytics").resolve("model")),
                modelRoots);
    }

    @Test
    public void testDiscoveryOfNonDirectory()
    {
        Assert.assertThrows(IllegalArgumentException.class, () -> LocalModelDiscovery.findModelRoots(this.tempFolder.getRoot().toPath().resolve("nope")));
    }

    private static void write(Path root, String relativePath, String content) throws IOException
    {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }
}
