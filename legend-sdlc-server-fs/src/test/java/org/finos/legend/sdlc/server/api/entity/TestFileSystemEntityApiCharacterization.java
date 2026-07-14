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

package org.finos.legend.sdlc.server.api.entity;

import org.eclipse.collections.api.factory.Lists;
import org.finos.legend.sdlc.domain.model.TestTools;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.entity.change.EntityChange;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.server.api.project.FileSystemProjectApi;
import org.finos.legend.sdlc.server.api.workspace.FileSystemWorkspaceApi;
import org.finos.legend.sdlc.backend.api.entity.EntityAccessContext;
import org.finos.legend.sdlc.backend.api.entity.EntityModificationContext;
import org.finos.legend.sdlc.project.source.SourceSpecification;
import org.finos.legend.sdlc.project.source.WorkspaceSourceSpecification;
import org.finos.legend.sdlc.project.workspace.WorkspaceSource;
import org.finos.legend.sdlc.project.workspace.WorkspaceSpecification;
import org.finos.legend.sdlc.error.LegendSDLCException;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.startup.FSConfiguration;
import org.finos.legend.sdlc.project.structure.EntitySourceDirectory;
import org.finos.legend.sdlc.project.structure.ProjectStructure;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;

/**
 * Characterization tests for the entity access/modification logic of {@link FileSystemEntityApi}, driven end-to-end
 * over a real git repository in a temporary directory. Originally written (re-architecture Phase 3) to pin the
 * behavior of the duplicated entity read/write logic before its extraction into the SDLC core module — including
 * several file-system provider defects, preserved deliberately at the time. The Phase 5 FS refit fixed those defects
 * (see the worklog: standard-context enumeration works and is platform-independent; entity reads and writes go
 * through one code path over the core operations; a stale reference revision is a 409; access contexts honor their
 * revision id), and the assertions here now pin the fixed behavior.
 */
public class TestFileSystemEntityApiCharacterization
{
    protected static final String PROJECT_ID = "TestProject";
    protected static final String WORKSPACE_ID = "entitytestworkspace";
    protected static final String GROUP_ID = "org.finos.legend.sdlc.test";
    protected static final String ARTIFACT_ID = "entity-api-test";

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    protected FSConfiguration fsConfiguration;
    protected FileSystemEntityApi entityApi;
    protected WorkspaceSourceSpecification workspaceSourceSpec;

    @Before
    public void setUp() throws IOException
    {
        this.fsConfiguration = FSConfiguration.newConfiguration(this.tempFolder.getRoot().getCanonicalFile().getAbsolutePath());
        new FileSystemProjectApi(this.fsConfiguration, null, null, null).createProject(PROJECT_ID, "characterization test project", ProjectType.MANAGED, GROUP_ID, ARTIFACT_ID, Collections.emptyList());
        new FileSystemWorkspaceApi(this.fsConfiguration).newWorkspace(PROJECT_ID, WORKSPACE_ID, WorkspaceType.USER, WorkspaceSource.projectWorkspaceSource());
        this.workspaceSourceSpec = SourceSpecification.workspaceSourceSpecification(WorkspaceSpecification.newWorkspaceSpecification(WORKSPACE_ID, WorkspaceType.USER));
        this.entityApi = new FileSystemEntityApi(this.fsConfiguration);
    }

    @Test
    public void testCreateAndGetEntity()
    {
        Entity entity = TestTools.newClassEntity("TestClass", "model::domain");
        Revision revision = modificationContext().performChanges(
                Collections.singletonList(EntityChange.newCreateEntity(entity.getPath(), entity.getClassifierPath(), entity.getContent())), null, "create entity");
        Assert.assertNotNull(revision);

        Entity fetched = accessContext().getEntity("model::domain::TestClass");
        Assert.assertEquals("model::domain::TestClass", fetched.getPath());
        Assert.assertEquals(entity.getClassifierPath(), fetched.getClassifierPath());
        // entity content is not round-trip identical: what is read back is the serializer-normalized form
        Assert.assertEquals(normalize(entity).getContent(), fetched.getContent());

        // the entity file exists in the working tree at the location the project structure prescribes
        String filePath = expectedFilePath(entity);
        File onDisk = new File(this.tempFolder.getRoot(), PROJECT_ID + filePath);
        Assert.assertTrue(onDisk.getPath(), onDisk.exists());
    }

    @Test
    public void testGetUnknownEntity()
    {
        LegendSDLCException e = Assert.assertThrows(LegendSDLCException.class, () -> accessContext().getEntity("model::Missing"));
        Assert.assertEquals("Unknown entity model::Missing", e.getMessage());
        Assert.assertEquals(404, e.getStatusCode());
    }

    @Test
    public void testGetEntities()
    {
        Entity entity = TestTools.newClassEntity("TestClass", "model");
        modificationContext().performChanges(
                Collections.singletonList(EntityChange.newCreateEntity(entity.getPath(), entity.getClassifierPath(), entity.getContent())), null, "create entity");

        // enumeration goes through the standard file access context and is platform-independent
        List<Entity> entities = accessContext().getEntities(null, null, null);
        Assert.assertEquals(1, entities.size());
        Assert.assertEquals("model::TestClass", entities.get(0).getPath());
    }

    @Test
    public void testGetEntityPaths()
    {
        Entity classA = TestTools.newClassEntity("ClassA", "model");
        Entity classB = TestTools.newClassEntity("ClassB", "model::other");
        modificationContext().performChanges(Lists.mutable.with(
                EntityChange.newCreateEntity(classA.getPath(), classA.getClassifierPath(), classA.getContent()),
                EntityChange.newCreateEntity(classB.getPath(), classB.getClassifierPath(), classB.getContent())), null, "create entities");

        Assert.assertEquals(Lists.mutable.with("model::ClassA", "model::other::ClassB"), sortedEntityPaths());
    }

    @Test
    public void testUpdateEntities()
    {
        Entity entity = TestTools.newClassEntity("TestClass", "model");
        modificationContext().performChanges(
                Collections.singletonList(EntityChange.newCreateEntity(entity.getPath(), entity.getClassifierPath(), entity.getContent())), null, "create entity");

        // no-op update: same entity, no revision is created (byte-level no-op suppression)
        Assert.assertNull(modificationContext().updateEntities(Collections.singletonList(entity), false, "no-op"));

        // modifying an existing entity through updateEntities works
        Entity modified = TestTools.newClassEntity("TestClass", "model", TestTools.newProperty("prop", "String", 0, 1));
        Revision modifyRevision = modificationContext().updateEntities(Collections.singletonList(modified), false, "modify entity");
        Assert.assertNotNull(modifyRevision);
        Assert.assertEquals(normalize(modified).getContent(), accessContext().getEntity("model::TestClass").getContent());

        // creating a new entity alongside, without replace: existing entities untouched
        Entity other = TestTools.newClassEntity("Other", "model");
        Revision addRevision = modificationContext().updateEntities(Collections.singletonList(other), false, "add entity");
        Assert.assertNotNull(addRevision);
        Assert.assertEquals(Lists.mutable.with("model::Other", "model::TestClass"), sortedEntityPaths());

        // replace=true with an empty set deletes all entities ...
        Revision deleteAllRevision = modificationContext().updateEntities(Collections.emptyList(), true, "delete all");
        Assert.assertNotNull(deleteAllRevision);
        Assert.assertEquals(Collections.emptyList(), sortedEntityPaths());

        // ... but the project configuration file survives
        Assert.assertNotNull(fileAccessContext().getFile(ProjectStructure.PROJECT_CONFIG_PATH));
    }

    @Test
    public void testPerformChangesModifyAndDelete()
    {
        Entity entity = TestTools.newClassEntity("TestClass", "model");
        modificationContext().performChanges(
                Collections.singletonList(EntityChange.newCreateEntity(entity.getPath(), entity.getClassifierPath(), entity.getContent())), null, "create entity");

        Entity modified = TestTools.newClassEntity("TestClass", "model", TestTools.newProperty("prop", "String", 0, 1));
        Revision modifyRevision = modificationContext().performChanges(
                Collections.singletonList(EntityChange.newModifyEntity(modified.getPath(), modified.getClassifierPath(), modified.getContent())), null, "modify entity");
        Assert.assertNotNull(modifyRevision);

        Revision deleteRevision = modificationContext().performChanges(
                Collections.singletonList(EntityChange.newDeleteEntity("model::TestClass")), null, "delete entity");
        Assert.assertNotNull(deleteRevision);
        LegendSDLCException e = Assert.assertThrows(LegendSDLCException.class, () -> accessContext().getEntity("model::TestClass"));
        Assert.assertEquals(404, e.getStatusCode());
    }

    @Test
    public void testStaleReferenceRevisionIsConflict()
    {
        Entity entity = TestTools.newClassEntity("TestClass", "model");
        Revision r1 = modificationContext().performChanges(
                Collections.singletonList(EntityChange.newCreateEntity(entity.getPath(), entity.getClassifierPath(), entity.getContent())), null, "create entity");
        Entity second = TestTools.newClassEntity("Second", "model");
        Revision r2 = modificationContext().performChanges(
                Collections.singletonList(EntityChange.newCreateEntity(second.getPath(), second.getClassifierPath(), second.getContent())), null, "second entity");

        // submitting against a stale reference revision is a conflict, reported as such
        Entity third = TestTools.newClassEntity("Third", "model");
        LegendSDLCException e = Assert.assertThrows(LegendSDLCException.class,
                () -> modificationContext().performChanges(
                        Collections.singletonList(EntityChange.newCreateEntity(third.getPath(), third.getClassifierPath(), third.getContent())), r1.getId(), "stale revision"));
        Assert.assertEquals(409, e.getStatusCode());
        Assert.assertEquals("Expected " + this.workspaceSourceSpec + " to be at revision " + r1.getId() + "; instead it was at revision " + r2.getId(), e.getMessage());
    }

    @Test
    public void testRevisionPinnedAccessContext()
    {
        Entity first = TestTools.newClassEntity("First", "model");
        Revision r1 = modificationContext().performChanges(
                Collections.singletonList(EntityChange.newCreateEntity(first.getPath(), first.getClassifierPath(), first.getContent())), null, "first entity");
        Entity second = TestTools.newClassEntity("Second", "model");
        modificationContext().performChanges(
                Collections.singletonList(EntityChange.newCreateEntity(second.getPath(), second.getClassifierPath(), second.getContent())), null, "second entity");

        // an access context created with a revision id reads that revision, not the branch tip
        EntityAccessContext pinned = this.entityApi.getEntityAccessContext(PROJECT_ID, this.workspaceSourceSpec, r1.getId());
        Assert.assertEquals(Collections.singletonList("model::First"), pinned.getEntityPaths(null, null, null));
        Assert.assertEquals(Lists.mutable.with("model::First", "model::Second"), sortedEntityPaths());
    }

    @Test
    public void testWorkspaceIsolation()
    {
        Entity entity = TestTools.newClassEntity("WorkspaceOnly", "model");
        modificationContext().performChanges(
                Collections.singletonList(EntityChange.newCreateEntity(entity.getPath(), entity.getClassifierPath(), entity.getContent())), null, "create entity");

        EntityAccessContext projectContext = this.entityApi.getEntityAccessContext(PROJECT_ID, SourceSpecification.projectSourceSpecification(), null);
        LegendSDLCException e = Assert.assertThrows(LegendSDLCException.class, () -> projectContext.getEntity("model::WorkspaceOnly"));
        Assert.assertEquals("Unknown entity model::WorkspaceOnly", e.getMessage());
        Assert.assertEquals(404, e.getStatusCode());
        Assert.assertEquals(Collections.emptyList(), projectContext.getEntities(null, null, null));
    }

    protected EntityAccessContext accessContext()
    {
        return this.entityApi.getEntityAccessContext(PROJECT_ID, this.workspaceSourceSpec, null);
    }

    protected EntityModificationContext modificationContext()
    {
        return this.entityApi.getEntityModificationContext(PROJECT_ID, this.workspaceSourceSpec);
    }

    protected ProjectFileAccessProvider.FileAccessContext fileAccessContext()
    {
        return this.entityApi.getProjectFileAccessProvider().getFileAccessContext(PROJECT_ID, this.workspaceSourceSpec, null);
    }

    protected List<String> sortedEntityPaths()
    {
        return Lists.mutable.withAll(accessContext().getEntityPaths(null, null, null)).sortThis();
    }

    protected String expectedFilePath(Entity entity)
    {
        EntitySourceDirectory sourceDirectory = ProjectStructure.getProjectStructure(fileAccessContext()).findSourceDirectoryForEntity(entity);
        Assert.assertNotNull("no source directory for " + entity.getPath(), sourceDirectory);
        return sourceDirectory.entityPathToFilePath(entity.getPath());
    }

    protected Entity normalize(Entity entity)
    {
        EntitySourceDirectory sourceDirectory = ProjectStructure.getProjectStructure(fileAccessContext()).findSourceDirectoryForEntity(entity);
        Assert.assertNotNull("no source directory for " + entity.getPath(), sourceDirectory);
        try
        {
            return sourceDirectory.deserialize(sourceDirectory.serializeToBytes(entity));
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }
}
