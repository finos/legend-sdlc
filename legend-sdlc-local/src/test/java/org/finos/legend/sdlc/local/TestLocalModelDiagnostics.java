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

import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectStructureVersion;
import org.finos.legend.sdlc.project.structure.ProjectStructure;
import org.finos.legend.sdlc.project.structure.SimpleProjectConfiguration;
import org.finos.legend.sdlc.serialization.EntitySerializers;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestLocalModelDiagnostics
{
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testBareModelIsClean() throws IOException
    {
        // no project.json at all: legal (structure version 0), no findings
        Path root = this.tempFolder.newFolder("bare").toPath();
        try (LocalModel model = LocalModel.open(root))
        {
            Assert.assertNull(model.getConfiguration());
            Assert.assertTrue(model.validate().isEmpty());
            Assert.assertEquals(Collections.emptyList(), model.getEntityPaths());
        }
    }

    @Test
    public void testUnparseableConfiguration() throws IOException
    {
        Path root = this.tempFolder.newFolder("corrupt").toPath();
        Files.write(root.resolve("project.json"), "this is not json".getBytes(StandardCharsets.UTF_8));
        try (LocalModel model = LocalModel.open(root))
        {
            List<LocalModelDiagnostic> diagnostics = model.validate();
            Assert.assertEquals(1, diagnostics.size());
            Assert.assertEquals(LocalModelDiagnostic.Severity.ERROR, diagnostics.get(0).getSeverity());
            Assert.assertEquals(LocalModelDiagnostic.Category.CONFIGURATION, diagnostics.get(0).getCategory());
            Assert.assertEquals("/project.json", diagnostics.get(0).getFilePath());

            // operations refuse to run on an unresolved configuration; validation still works (above)
            Assert.assertThrows(IllegalStateException.class, model::getEntities);
        }
    }

    @Test
    public void testUnknownStructureVersion() throws IOException
    {
        Path root = this.tempFolder.newFolder("unknown-version").toPath();
        Files.write(root.resolve("project.json"), ProjectStructure.serializeProjectConfiguration(SimpleProjectConfiguration.newConfiguration(
                "p", ProjectType.MANAGED, ProjectStructureVersion.newProjectStructureVersion(99), null, "org.test", "p-model", null, null, null, null, null)));
        try (LocalModel model = LocalModel.open(root))
        {
            List<LocalModelDiagnostic> diagnostics = model.validate();
            Assert.assertEquals(1, diagnostics.size());
            Assert.assertEquals(LocalModelDiagnostic.Severity.ERROR, diagnostics.get(0).getSeverity());
            Assert.assertTrue(diagnostics.get(0).getMessage(), diagnostics.get(0).getMessage().contains("99"));
        }
    }

    @Test
    public void testConfigurationFindings() throws IOException
    {
        Path root = this.tempFolder.newFolder("findings").toPath();
        Files.write(root.resolve("project.json"), ProjectStructure.serializeProjectConfiguration(SimpleProjectConfiguration.newConfiguration(
                "p", ProjectType.MANAGED, ProjectStructureVersion.newProjectStructureVersion(0), null, "org.test", "Bad Artifact Id",
                Collections.singletonList(ProjectDependency.newProjectDependency("legacy-dep", "1.0.0")), null, null, null, null)));
        try (LocalModel model = LocalModel.open(root))
        {
            List<LocalModelDiagnostic> diagnostics = model.validate();
            Assert.assertEquals(diagnostics.toString(), 2, diagnostics.size());
            Assert.assertTrue(diagnostics.toString(), diagnostics.stream().anyMatch(d ->
                    (d.getSeverity() == LocalModelDiagnostic.Severity.ERROR) && d.getMessage().contains("artifactId")));
            Assert.assertTrue(diagnostics.toString(), diagnostics.stream().anyMatch(d ->
                    (d.getSeverity() == LocalModelDiagnostic.Severity.WARNING) && d.getMessage().contains("Legacy project dependency")));
        }
    }

    @Test
    public void testEntityFindings() throws IOException
    {
        Path root = this.tempFolder.newFolder("entities").toPath();
        Path entitiesDir = root.resolve("entities").resolve("model");
        Files.createDirectories(entitiesDir);
        // one good entity, one unreadable, one whose content disagrees with its location
        Map<String, Object> good = new HashMap<>();
        good.put("package", "model");
        good.put("name", "Good");
        Files.write(entitiesDir.resolve("Good.json"), EntitySerializers.getDefaultJsonSerializer().serializeToBytes(Entity.newEntity("model::Good", "meta::pure::metamodel::type::Class", good)));
        Files.write(entitiesDir.resolve("Broken.json"), "not an entity".getBytes(StandardCharsets.UTF_8));
        Map<String, Object> misplaced = new HashMap<>();
        misplaced.put("package", "model::other");
        misplaced.put("name", "Elsewhere");
        Files.write(entitiesDir.resolve("Misplaced.json"), EntitySerializers.getDefaultJsonSerializer().serializeToBytes(Entity.newEntity("model::other::Elsewhere", "meta::pure::metamodel::type::Class", misplaced)));

        try (LocalModel model = LocalModel.open(root))
        {
            List<LocalModelDiagnostic> diagnostics = model.validate();
            Assert.assertEquals(diagnostics.toString(), 2, diagnostics.size());
            diagnostics.forEach(d -> Assert.assertEquals(LocalModelDiagnostic.Category.ENTITY, d.getCategory()));
            Assert.assertTrue(diagnostics.toString(), diagnostics.stream().anyMatch(d -> "/entities/model/Broken.json".equals(d.getFilePath())));
            Assert.assertTrue(diagnostics.toString(), diagnostics.stream().anyMatch(d ->
                    "/entities/model/Misplaced.json".equals(d.getFilePath()) && d.getMessage().contains("model::Misplaced")));
        }
    }

    @Test
    public void testClosedHandleRefusesOperations() throws IOException
    {
        Path root = this.tempFolder.newFolder("closed").toPath();
        LocalModel model = LocalModel.open(root);
        model.close();
        Assert.assertThrows(IllegalStateException.class, model::getEntities);
        Assert.assertThrows(IllegalStateException.class, model::validate);
        Assert.assertThrows(IllegalStateException.class, model::refresh);
    }
}
