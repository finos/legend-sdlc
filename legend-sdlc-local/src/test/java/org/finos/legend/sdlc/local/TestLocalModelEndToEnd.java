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
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectStructureVersion;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider;
import org.finos.legend.sdlc.project.files.ProjectFileOperation;
import org.finos.legend.sdlc.project.files.RootedFileAccessContext;
import org.finos.legend.sdlc.project.source.SourceSpecification;
import org.finos.legend.sdlc.project.structure.ProjectStructure;
import org.finos.legend.sdlc.project.structure.SimpleProjectConfiguration;
import org.finos.legend.sdlc.project.structure.extension.ProjectStructureExtension;
import org.finos.legend.sdlc.project.structure.extension.ProjectStructureExtensionProvider;
import org.finos.legend.sdlc.serialization.EntitySerializers;
import org.finos.legend.sdlc.serialization.EntityTextSerializer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The re-architecture section 6 Phase 6 end-to-end scenario: a repository containing non-Legend content plus two
 * models at subpaths; open, edit entities, update configuration, verify files; then mutate files on disk under an
 * open handle and confirm refresh reconciles. Also confirms (section 4.6) the degraded mode of the structure-aware
 * tier: with no extension provider, configuration updates proceed but leave extension-managed files untouched and
 * preserve the recorded extension version.
 */
public class TestLocalModelEndToEnd
{
    private static final String EMBEDDED_MODEL_PATH = "analytics/model";
    private static final String MANAGED_MODEL_PATH = "services/svc-model";
    private static final String EXTENSION_MANAGED_FILE = "/deployment-ci.yml";

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private Path repoRoot;
    private Path embeddedModelRoot;
    private Path managedModelRoot;
    private EntityTextSerializer serializer;

    @Before
    public void setUpRepository() throws IOException
    {
        this.repoRoot = this.tempFolder.getRoot().toPath().toAbsolutePath().normalize();
        this.serializer = EntitySerializers.getDefaultJsonSerializer();

        // non-Legend content
        write("README.md", "# Not a Legend repository root\n");
        write("src/app/Main.java", "public class Main {}\n");

        // model 1: embedded (Form 2) at a subpath, structure version 0, one seed entity
        this.embeddedModelRoot = this.repoRoot.resolve(EMBEDDED_MODEL_PATH);
        writeBytes(EMBEDDED_MODEL_PATH + "/project.json", ProjectStructure.serializeProjectConfiguration(
                SimpleProjectConfiguration.newConfiguration("emb-proj", ProjectType.EMBEDDED, ProjectStructureVersion.newProjectStructureVersion(0), null, null, null, null, null, null, null, null)));
        writeBytes(EMBEDDED_MODEL_PATH + "/entities/model/Person.json", this.serializer.serializeToBytes(newEntity("model::Person")));

        // model 2: managed (Form 1) at a subpath, structure version 11 with extension version 1, one seed entity
        this.managedModelRoot = this.repoRoot.resolve(MANAGED_MODEL_PATH);
        writeBytes(MANAGED_MODEL_PATH + "/project.json", ProjectStructure.serializeProjectConfiguration(
                SimpleProjectConfiguration.newConfiguration("svc-proj", ProjectType.MANAGED, ProjectStructureVersion.newProjectStructureVersion(11, 1), null, "org.test.svc", "svc-model", null, null, null, null, null)));
        writeBytes(MANAGED_MODEL_PATH + "/svc-model-entities/src/main/legend/model/Service.json", this.serializer.serializeToBytes(newEntity("model::Service")));
        // a stale extension-managed file, as the deployment's extension last wrote it
        write(MANAGED_MODEL_PATH + EXTENSION_MANAGED_FILE, "ci: stale\n");
    }

    @Test
    public void testDiscoverThenOpenThenEditEmbedded() throws IOException
    {
        Assert.assertEquals(Arrays.asList(this.embeddedModelRoot, this.managedModelRoot), LocalModelDiscovery.findModelRoots(this.repoRoot));

        byte[] managedEntityBefore = Files.readAllBytes(this.managedModelRoot.resolve("svc-model-entities/src/main/legend/model/Service.json"));
        try (LocalModel model = LocalModel.open(this.embeddedModelRoot))
        {
            Assert.assertEquals(this.embeddedModelRoot, model.getRoot());
            Assert.assertEquals(Collections.singletonList("model::Person"), model.getEntityPaths());
            Assert.assertTrue(model.validate().isEmpty());

            // create, read back, modify, rename, delete
            model.createEntity("model::Firm", "meta::pure::metamodel::type::Class", newEntityContent("model::Firm"));
            Assert.assertTrue(Files.isRegularFile(this.embeddedModelRoot.resolve("entities/model/Firm.json")));
            Assert.assertEquals("model::Firm", model.getEntity("model::Firm").getPath());

            Map<String, Object> updatedContent = newEntityContent("model::Firm");
            updatedContent.put("stereotypes", Collections.emptyList());
            model.updateEntity("model::Firm", "meta::pure::metamodel::type::Class", updatedContent);
            Assert.assertEquals(updatedContent, model.getEntity("model::Firm").getContent());

            // a consistent local rename is delete + create with rewritten content
            model.deleteEntity("model::Person");
            model.createEntity("model::people::Person", "meta::pure::metamodel::type::Class", newEntityContent("model::people::Person"));
            Assert.assertFalse(Files.exists(this.embeddedModelRoot.resolve("entities/model/Person.json")));
            Assert.assertTrue(Files.isRegularFile(this.embeddedModelRoot.resolve("entities/model/people/Person.json")));

            model.deleteEntity("model::Firm");
            Assert.assertEquals(Collections.singletonList("model::people::Person"), model.getEntityPaths());
        }

        // the other model and the non-Legend content are untouched
        Assert.assertArrayEquals(managedEntityBefore, Files.readAllBytes(this.managedModelRoot.resolve("svc-model-entities/src/main/legend/model/Service.json")));
        Assert.assertEquals("# Not a Legend repository root\n", read("README.md"));
    }

    @Test
    public void testRootedContextPresentsTheSameModel()
    {
        // the storage-generic form of rooting (section 4.2): a context over the repository, rooted at the model
        LocalProjectFileAccessProvider repoProvider = new LocalProjectFileAccessProvider(this.repoRoot, "repo");
        ProjectFileAccessProvider.FileAccessContext rooted = RootedFileAccessContext.root(
                repoProvider.getFileAccessContext("repo", SourceSpecification.projectSourceSpecification(), null),
                "/" + EMBEDDED_MODEL_PATH);
        ProjectConfiguration config = ProjectStructure.getProjectConfiguration(rooted);
        Assert.assertEquals("emb-proj", config.getProjectId());
        Assert.assertNotNull(rooted.getFile("/entities/model/Person.json"));
        Assert.assertNull(rooted.getFile("/README.md"));
    }

    @Test
    public void testManagedConfigurationUpdateWithProvider() throws IOException
    {
        try (ManagedLocalModel model = ManagedLocalModel.open(this.managedModelRoot, new TestExtensionProvider()))
        {
            model.newConfigurationUpdate()
                    .withProjectDependencyToAdd(ProjectDependency.newProjectDependency("org.test.dep:dep-model", "1.0.0"))
                    .withMessage("Add dep-model dependency")
                    .apply();

            // the handle reflects the update
            List<ProjectDependency> dependencies = model.getConfiguration().getProjectDependencies();
            Assert.assertEquals(1, dependencies.size());
            Assert.assertEquals("org.test.dep:dep-model", dependencies.get(0).getProjectId());
            Assert.assertEquals(Integer.valueOf(1), model.getConfiguration().getProjectStructureVersion().getExtensionVersion());
        }

        // the files: project.json carries the dependency; the structure wrote its build files; the extension
        // maintained its file
        String projectJson = read(MANAGED_MODEL_PATH + "/project.json");
        Assert.assertTrue(projectJson, projectJson.contains("org.test.dep:dep-model"));
        Assert.assertTrue(Files.isRegularFile(this.managedModelRoot.resolve("pom.xml")));
        Assert.assertEquals("ci: svc-model ext 1\n", read(MANAGED_MODEL_PATH + EXTENSION_MANAGED_FILE));
    }

    @Test
    public void testManagedConfigurationUpdateDegradedMode() throws IOException
    {
        try (ManagedLocalModel model = ManagedLocalModel.open(this.managedModelRoot, null))
        {
            Assert.assertNull(model.getExtensionProvider());
            model.newConfigurationUpdate()
                    .withProjectDependencyToAdd(ProjectDependency.newProjectDependency("org.test.dep:dep-model", "1.0.0"))
                    .apply();

            // the update proceeded: project.json carries the dependency and preserves the extension version, and
            // the structure-managed files were still written
            Assert.assertEquals("org.test.dep:dep-model", model.getConfiguration().getProjectDependencies().get(0).getProjectId());
            Assert.assertEquals(Integer.valueOf(1), model.getConfiguration().getProjectStructureVersion().getExtensionVersion());
            Assert.assertTrue(Files.isRegularFile(this.managedModelRoot.resolve("pom.xml")));

            // ... but the extension-managed file is untouched, to be reconciled server-side
            Assert.assertEquals("ci: stale\n", read(MANAGED_MODEL_PATH + EXTENSION_MANAGED_FILE));

            // setting a new extension version, however, needs the provider
            ManagedLocalModel.ConfigurationUpdate update = model.newConfigurationUpdate().withProjectStructureExtensionVersion(2);
            Assert.assertThrows(IllegalArgumentException.class, update::apply);
        }
    }

    @Test
    public void testExternalMutationAndRefresh() throws IOException
    {
        try (LocalModel model = LocalModel.open(this.embeddedModelRoot))
        {
            Assert.assertEquals(Collections.singletonList("model::Person"), model.getEntityPaths());

            // entity reads are live: a file written directly to disk appears without refresh
            writeBytes(EMBEDDED_MODEL_PATH + "/entities/model/Firm.json", this.serializer.serializeToBytes(newEntity("model::Firm")));
            List<String> entityPaths = model.getEntityPaths();
            entityPaths.sort(String::compareTo);
            Assert.assertEquals(Arrays.asList("model::Firm", "model::Person"), entityPaths);

            // the configuration resolution is cached: an external project.json change appears only after refresh
            byte[] newConfig = ProjectStructure.serializeProjectConfiguration(SimpleProjectConfiguration.newConfiguration(
                    "emb-proj", ProjectType.EMBEDDED, ProjectStructureVersion.newProjectStructureVersion(0), null, null, null,
                    Collections.singletonList(ProjectDependency.newProjectDependency("org.test.dep:dep-model", "1.0.0")), null, null, null, null));
            writeBytes(EMBEDDED_MODEL_PATH + "/project.json", newConfig);
            Assert.assertTrue((model.getConfiguration().getProjectDependencies() == null) || model.getConfiguration().getProjectDependencies().isEmpty());

            model.refresh();
            Assert.assertEquals("org.test.dep:dep-model", model.getConfiguration().getProjectDependencies().get(0).getProjectId());
        }
    }

    private static Entity newEntity(String path)
    {
        return Entity.newEntity(path, "meta::pure::metamodel::type::Class", newEntityContent(path));
    }

    private static Map<String, Object> newEntityContent(String path)
    {
        int splitIndex = path.lastIndexOf("::");
        Map<String, Object> content = new HashMap<>();
        content.put("_type", "class");
        content.put("package", path.substring(0, splitIndex));
        content.put("name", path.substring(splitIndex + 2));
        return content;
    }

    private void write(String relativePath, String content) throws IOException
    {
        writeBytes(relativePath, content.getBytes(StandardCharsets.UTF_8));
    }

    private void writeBytes(String relativePath, byte[] content) throws IOException
    {
        Path file = this.repoRoot.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.write(file, content);
    }

    private String read(String relativePath) throws IOException
    {
        return new String(Files.readAllBytes(this.repoRoot.resolve(relativePath)), StandardCharsets.UTF_8);
    }

    /**
     * A deployment's extension fixture: extension version 1 for every structure version, managing one file whose
     * content is derived from the configuration.
     */
    private static class TestExtensionProvider implements ProjectStructureExtensionProvider
    {
        @Override
        public Integer getLatestVersionForProjectStructureVersion(int projectStructureVersion)
        {
            return 1;
        }

        @Override
        public ProjectStructureExtension getProjectStructureExtension(int projectStructureVersion, int projectStructureExtensionVersion)
        {
            return new TestExtension(projectStructureVersion, projectStructureExtensionVersion);
        }
    }

    private static class TestExtension implements ProjectStructureExtension
    {
        private final int projectStructureVersion;
        private final int version;

        private TestExtension(int projectStructureVersion, int version)
        {
            this.projectStructureVersion = projectStructureVersion;
            this.version = version;
        }

        @Override
        public int getVersion()
        {
            return this.version;
        }

        @Override
        public int getProjectStructureVersion()
        {
            return this.projectStructureVersion;
        }

        @Override
        public void collectUpdateProjectConfigurationOperations(ProjectConfiguration oldConfig, ProjectConfiguration newConfig, ProjectFileAccessProvider.FileAccessContext fileAccessContext, Consumer<ProjectFileOperation> operationConsumer)
        {
            byte[] content = ("ci: " + newConfig.getArtifactId() + " ext " + this.version + "\n").getBytes(StandardCharsets.UTF_8);
            if (fileAccessContext.fileExists(EXTENSION_MANAGED_FILE))
            {
                operationConsumer.accept(ProjectFileOperation.modifyFile(EXTENSION_MANAGED_FILE, content));
            }
            else
            {
                operationConsumer.accept(ProjectFileOperation.addFile(EXTENSION_MANAGED_FILE, content));
            }
        }
    }
}
