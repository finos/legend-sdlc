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

import org.finos.legend.sdlc.domain.model.TestTools;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.entity.change.EntityChange;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.server.api.project.FileSystemProjectApi;
import org.finos.legend.sdlc.server.api.workspace.FileSystemWorkspaceApi;
import org.finos.legend.sdlc.server.domain.api.entity.EntityAccessContext;
import org.finos.legend.sdlc.server.domain.api.entity.EntityModificationContext;
import org.finos.legend.sdlc.project.source.SourceSpecification;
import org.finos.legend.sdlc.project.source.WorkspaceSourceSpecification;
import org.finos.legend.sdlc.project.workspace.WorkspaceSource;
import org.finos.legend.sdlc.project.workspace.WorkspaceSpecification;
import org.finos.legend.sdlc.error.LegendSDLCException;
import org.finos.legend.sdlc.server.startup.FSConfiguration;
import org.finos.legend.sdlc.project.structure.ProjectStructure;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Characterization tests for the entity access/modification logic of {@link FileSystemEntityApi} (re-architecture
 * Phase 3), driven end-to-end over a real git repository in a temporary directory. These tests pin current behavior
 * before the duplicated entity read/write logic is factored out into the SDLC core module - including behavior that
 * is plainly buggy (see the re-architecture worklog):
 *
 * <ul>
 * <li>File enumeration through the standard {@code FileAccessContext} returns no files, because
 * {@code FileSystemFileAccessContext.getFilesInCanonicalDirectories} compares canonical directory names (leading
 * {@code /}) against git tree paths (no leading {@code /}). Consequently {@code updateEntities} never sees existing
 * entities: modifying an existing entity fails with "already exists", and {@code replace=true} deletes nothing.</li>
 * <li>Entity enumeration through {@code getEntities} (a git tree walk) relativizes file paths with
 * {@code java.nio.Path} + string concatenation, which produces {@code \}-separated paths on Windows that never match
 * an entity source directory: enumeration is empty on Windows and works on POSIX. Assertions on enumeration are
 * therefore conditional on {@code File.separatorChar}.</li>
 * <li>{@code getEntityPaths} does not take the git tree walk route: it enumerates through the standard
 * {@code FileAccessContext}, whose {@code getFilesInCanonicalDirectories} calls {@code ObjectId.fromString(null)}
 * (the access context is always created with a null revision id) - so it always throws.</li>
 * </ul>
 *
 * Do not "fix" an assertion here without a deliberate behavior-change decision.
 */
public class TestFileSystemEntityApiCharacterization
{
    protected static final String PROJECT_ID = "TestProject";
    protected static final String WORKSPACE_ID = "entitytestworkspace";
    protected static final String GROUP_ID = "org.finos.legend.sdlc.test";
    protected static final String ARTIFACT_ID = "entity-api-test";

    private static final boolean POSIX_SEPARATOR = File.separatorChar == '/';

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

        // getEntity resolves through direct file lookup and works on all platforms
        Entity fetched = accessContext().getEntity("model::domain::TestClass");
        Assert.assertEquals("model::domain::TestClass", fetched.getPath());
        Assert.assertEquals(entity.getClassifierPath(), fetched.getClassifierPath());

        // the entity file exists in the working tree at the location the project structure prescribes
        ProjectStructure projectStructure = ProjectStructure.getProjectStructure(
                this.entityApi.getProjectFileAccessProvider().getFileAccessContext(PROJECT_ID, this.workspaceSourceSpec, null));
        String filePath = projectStructure.findSourceDirectoryForEntity(entity).entityPathToFilePath(entity.getPath());
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
    public void testGetEntitiesIsPlatformDependent()
    {
        Entity entity = TestTools.newClassEntity("TestClass", "model");
        modificationContext().performChanges(
                Collections.singletonList(EntityChange.newCreateEntity(entity.getPath(), entity.getClassifierPath(), entity.getContent())), null, "create entity");

        List<Entity> entities = accessContext().getEntities(null, null, null);
        if (POSIX_SEPARATOR)
        {
            Assert.assertEquals(1, entities.size());
            Assert.assertEquals("model::TestClass", entities.get(0).getPath());
        }
        else
        {
            // path relativization produces \-separated paths that match no entity source directory
            Assert.assertEquals(Collections.emptyList(), entities);
        }
    }

    @Test
    public void testGetEntityPathsAlwaysFails()
    {
        Entity entity = TestTools.newClassEntity("TestClass", "model");
        modificationContext().performChanges(
                Collections.singletonList(EntityChange.newCreateEntity(entity.getPath(), entity.getClassifierPath(), entity.getContent())), null, "create entity");

        // getEntityPaths enumerates through the standard file access context, which cannot handle the null revision
        // id that the entity access context is created with
        LegendSDLCException e = Assert.assertThrows(LegendSDLCException.class, () -> accessContext().getEntityPaths(null, null, null));
        Assert.assertTrue(e.getMessage(), e.getMessage().startsWith("Error getting files in directories for " + PROJECT_ID));
        Assert.assertEquals(500, e.getStatusCode());
    }

    @Test
    public void testUpdateEntitiesCannotSeeExistingEntities()
    {
        Entity entity = TestTools.newClassEntity("TestClass", "model");
        modificationContext().performChanges(
                Collections.singletonList(EntityChange.newCreateEntity(entity.getPath(), entity.getClassifierPath(), entity.getContent())), null, "create entity");

        // modifying an existing entity through updateEntities fails: enumeration through the standard file access
        // context returns nothing, so the update is treated as a create, which then finds the existing file
        Entity modified = TestTools.newClassEntity("TestClass", "model", TestTools.newProperty("prop", "String", 0, 1));
        LegendSDLCException e = Assert.assertThrows(LegendSDLCException.class,
                () -> modificationContext().updateEntities(Collections.singletonList(modified), false, "modify entity"));
        Assert.assertTrue(e.getMessage(), e.getMessage().endsWith(": entity \"model::TestClass\" already exists"));
        Assert.assertEquals(500, e.getStatusCode());

        // replace=true with an empty set deletes nothing (existing entities are invisible) and creates no revision
        Assert.assertNull(modificationContext().updateEntities(Collections.emptyList(), true, "delete all"));
        Assert.assertEquals("model::TestClass", accessContext().getEntity("model::TestClass").getPath());

        // creating a genuinely new entity through updateEntities works
        Entity other = TestTools.newClassEntity("Other", "model");
        Revision revision = modificationContext().updateEntities(Collections.singletonList(other), false, "add entity");
        Assert.assertNotNull(revision);
        Assert.assertEquals("model::Other", accessContext().getEntity("model::Other").getPath());
    }

    @Test
    public void testPerformChangesModifyAndDelete()
    {
        Entity entity = TestTools.newClassEntity("TestClass", "model");
        modificationContext().performChanges(
                Collections.singletonList(EntityChange.newCreateEntity(entity.getPath(), entity.getClassifierPath(), entity.getContent())), null, "create entity");

        // performChanges MODIFY/DELETE resolve entities through direct file lookup, so they work
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
    public void testStaleReferenceRevisionIsWrappedConflict()
    {
        Entity entity = TestTools.newClassEntity("TestClass", "model");
        Revision r1 = modificationContext().performChanges(
                Collections.singletonList(EntityChange.newCreateEntity(entity.getPath(), entity.getClassifierPath(), entity.getContent())), null, "create entity");
        Entity second = TestTools.newClassEntity("Second", "model");
        modificationContext().performChanges(
                Collections.singletonList(EntityChange.newCreateEntity(second.getPath(), second.getClassifierPath(), second.getContent())), null, "second entity");

        // submitting against a stale reference revision is a conflict, but FSException re-wraps it as a 500
        Entity third = TestTools.newClassEntity("Third", "model");
        LegendSDLCException e = Assert.assertThrows(LegendSDLCException.class,
                () -> modificationContext().performChanges(
                        Collections.singletonList(EntityChange.newCreateEntity(third.getPath(), third.getClassifierPath(), third.getContent())), r1.getId(), "stale revision"));
        Assert.assertEquals(500, e.getStatusCode());
        Assert.assertTrue(e.getMessage(), e.getMessage().startsWith("Error occurred while committing changes to ")
                && e.getMessage().contains("Expected ") && e.getMessage().contains(" to be at revision " + r1.getId()));
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
    }

    protected EntityAccessContext accessContext()
    {
        return this.entityApi.getEntityAccessContext(PROJECT_ID, this.workspaceSourceSpec, null);
    }

    protected EntityModificationContext modificationContext()
    {
        return this.entityApi.getEntityModificationContext(PROJECT_ID, this.workspaceSourceSpec);
    }
}
