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

package org.finos.legend.sdlc.server.gitlab.api;

import org.eclipse.collections.api.factory.Lists;
import org.finos.legend.sdlc.domain.model.TestTools;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.entity.change.EntityChange;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.backend.api.entity.EntityAccessContext;
import org.finos.legend.sdlc.backend.api.entity.EntityApi;
import org.finos.legend.sdlc.backend.api.entity.EntityModificationContext;
import org.finos.legend.sdlc.core.project.ProjectConfigurationUpdater;
import org.finos.legend.sdlc.project.source.SourceSpecification;
import org.finos.legend.sdlc.project.source.WorkspaceSourceSpecification;
import org.finos.legend.sdlc.project.workspace.WorkspaceSpecification;
import org.finos.legend.sdlc.error.LegendSDLCException;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider;
import org.finos.legend.sdlc.project.files.ProjectFileOperation;
import org.finos.legend.sdlc.project.structure.EntitySourceDirectory;
import org.finos.legend.sdlc.project.structure.ProjectStructure;
import org.finos.legend.sdlc.project.files.InMemoryProjectFileAccessProvider;
import org.finos.legend.sdlc.core.project.ProjectStructureUpdater;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * Characterization tests for the entity access/modification logic of {@link GitLabEntityApi} (re-architecture
 * Phase 3). These tests pin the behavior of the duplicated entity read/write logic before it is factored out into
 * the SDLC core module, by driving the real api class over an in-memory {@link ProjectFileAccessProvider} (only the
 * GitLab-native paths - review contexts, GitLab error translation - are out of reach and out of scope). Behavior
 * pinned here is intentionally preserved, bugs included; see the re-architecture worklog for the list of quirks
 * observed. Do not "fix" an assertion here without a deliberate behavior-change decision.
 */
public class TestGitLabEntityApiCharacterization
{
    protected static final String PROJECT_ID = "TestProject";
    protected static final String WORKSPACE_ID = "entitytestworkspace";
    protected static final String GROUP_ID = "org.finos.legend.sdlc.test";
    protected static final String ARTIFACT_ID = "entity-api-test";

    protected InMemoryProjectFileAccessProvider fileAccessProvider;
    protected WorkspaceSourceSpecification workspaceSourceSpec;
    protected EntityApi entityApi;

    private static class InMemoryBackedGitLabEntityApi extends GitLabEntityApi
    {
        private final ProjectFileAccessProvider fileAccessProvider;

        private InMemoryBackedGitLabEntityApi(ProjectFileAccessProvider fileAccessProvider)
        {
            super(null, null, null);
            this.fileAccessProvider = fileAccessProvider;
        }

        @Override
        protected ProjectFileAccessProvider getProjectFileAccessProvider()
        {
            return this.fileAccessProvider;
        }
    }

    @Before
    public void setUp()
    {
        this.fileAccessProvider = new InMemoryProjectFileAccessProvider("author", "committer");
        ProjectStructureUpdater.newUpdateBuilder(this.fileAccessProvider, PROJECT_ID,
                        ProjectConfigurationUpdater.newUpdater()
                                .withProjectId(PROJECT_ID)
                                .withProjectType(ProjectType.MANAGED)
                                .withGroupId(GROUP_ID)
                                .withArtifactId(ARTIFACT_ID))
                .withMessage("Build project structure")
                .build();
        this.fileAccessProvider.createWorkspace(PROJECT_ID, WORKSPACE_ID);
        this.workspaceSourceSpec = SourceSpecification.workspaceSourceSpecification(WorkspaceSpecification.newWorkspaceSpecification(WORKSPACE_ID, WorkspaceType.USER));
        this.entityApi = new InMemoryBackedGitLabEntityApi(this.fileAccessProvider);
    }

    // Access context creation

    @Test
    public void testGetEntityAccessContextNullValidation()
    {
        LegendSDLCException e1 = Assert.assertThrows(LegendSDLCException.class, () -> this.entityApi.getEntityAccessContext(null, this.workspaceSourceSpec, null));
        Assert.assertEquals("projectId may not be null", e1.getMessage());
        Assert.assertEquals(400, e1.getStatusCode());

        LegendSDLCException e2 = Assert.assertThrows(LegendSDLCException.class, () -> this.entityApi.getEntityAccessContext(PROJECT_ID, null, null));
        Assert.assertEquals("sourceSpecification may not be null", e2.getMessage());
        Assert.assertEquals(400, e2.getStatusCode());
    }

    // Entity read

    @Test
    public void testGetEntity()
    {
        Entity entity = TestTools.newClassEntity("TestClass", "model::domain");
        createEntities(entity);

        Entity fetched = accessContext().getEntity("model::domain::TestClass");
        Assert.assertEquals("model::domain::TestClass", fetched.getPath());
        Assert.assertEquals(entity.getClassifierPath(), fetched.getClassifierPath());
        // entity content is NOT round-trip identical: serialization normalizes it (e.g. adds superTypes,
        // stereotypes, constraints for a class); what is read back is the normalized form
        Assert.assertNotEquals(entity.getContent(), fetched.getContent());
        Assert.assertEquals(normalize(entity).getContent(), fetched.getContent());

        // the entity file is written to the structure's serialization location for the entity
        String filePath = expectedFilePath(entity);
        Assert.assertNotNull(filePath, fileAccessContext().getFile(filePath));
    }

    @Test
    public void testGetUnknownEntity()
    {
        LegendSDLCException e = Assert.assertThrows(LegendSDLCException.class, () -> accessContext().getEntity("model::Missing"));
        Assert.assertEquals("Unknown entity model::Missing for user workspace " + WORKSPACE_ID + " of project " + PROJECT_ID, e.getMessage());
        Assert.assertEquals(404, e.getStatusCode());
    }

    @Test
    public void testGetEntitiesWithPredicates()
    {
        Entity classA = TestTools.newClassEntity("ClassA", "model::domain");
        Entity classB = TestTools.newClassEntity("ClassB", "model::other");
        Entity enumC = TestTools.newEnumerationEntity("EnumC", "model::domain", "X", "Y");
        createEntities(classA, classB, enumC);

        List<Entity> all = accessContext().getEntities(null, null, null);
        Assert.assertEquals(3, all.size());

        List<Entity> domainOnly = accessContext().getEntities(p -> p.startsWith("model::domain::"), null, null);
        Assert.assertEquals(2, domainOnly.size());

        List<Entity> enumsOnly = accessContext().getEntities(null, c -> c.equals(enumC.getClassifierPath()), null);
        Assert.assertEquals(1, enumsOnly.size());
        Assert.assertEquals("model::domain::EnumC", enumsOnly.get(0).getPath());

        List<Entity> byContent = accessContext().getEntities(null, null, content -> "ClassB".equals(content.get("name")));
        Assert.assertEquals(1, byContent.size());
        Assert.assertEquals("model::other::ClassB", byContent.get(0).getPath());

        List<String> paths = accessContext().getEntityPaths(null, null, null);
        Assert.assertEquals(Lists.mutable.with("model::domain::ClassA", "model::domain::EnumC", "model::other::ClassB"), Lists.mutable.withAll(paths).sortThis());
    }

    @Test
    public void testInvalidEntityFile()
    {
        Entity valid = TestTools.newClassEntity("Valid", "model");
        createEntities(valid);
        String invalidFilePath = plantFile("model::Bad", "this is not json".getBytes(StandardCharsets.UTF_8));

        // getEntity on the invalid entity reports a deserialization error
        LegendSDLCException e1 = Assert.assertThrows(LegendSDLCException.class, () -> accessContext().getEntity("model::Bad"));
        Assert.assertTrue(e1.getMessage(), e1.getMessage().startsWith("Error deserializing entity \"model::Bad\" from file \"" + invalidFilePath + "\""));
        Assert.assertEquals(500, e1.getStatusCode());

        // getEntities without excludeInvalid fails wholesale. (Deliberate Phase 3 drift: before the factoring,
        // the underlying deserialization failure was re-wrapped as "Failed to get entities for <context>: ...";
        // the widened base-exception pass-through now surfaces it directly - same status, more precise message.
        // See the worklog.)
        LegendSDLCException e2 = Assert.assertThrows(LegendSDLCException.class, () -> accessContext().getEntities(null, null, null, false));
        Assert.assertTrue(e2.getMessage(), e2.getMessage().startsWith("Error deserializing entity from file "));
        Assert.assertEquals(500, e2.getStatusCode());

        // getEntities with excludeInvalid silently drops the invalid entity
        List<Entity> entities = accessContext().getEntities(null, null, null, true);
        Assert.assertEquals(1, entities.size());
        Assert.assertEquals("model::Valid", entities.get(0).getPath());

        // getEntityPaths does not deserialize, so the invalid entity is listed
        List<String> paths = accessContext().getEntityPaths(null, null, null);
        Assert.assertEquals(Lists.mutable.with("model::Bad", "model::Valid"), Lists.mutable.withAll(paths).sortThis());
    }

    @Test
    public void testEntityFilePathMismatch()
    {
        // a well-formed entity file whose declared package/name disagree with its location
        Entity elsewhere = TestTools.newClassEntity("Thing", "other");
        String plantedPath = plantFile("model::Mismatch", serialize(elsewhere));

        LegendSDLCException e = Assert.assertThrows(LegendSDLCException.class, () -> accessContext().getEntity("model::Mismatch"));
        Assert.assertEquals("Error deserializing entity \"model::Mismatch\" from file \"" + plantedPath + "\": Expected entity path model::Mismatch, found other::Thing", e.getMessage());
        Assert.assertEquals(500, e.getStatusCode());

        // excluded when excludeInvalid, though the path is still enumerated by getEntityPaths
        Assert.assertEquals(Collections.emptyList(), accessContext().getEntities(null, null, null, true));
        Assert.assertEquals(Collections.singletonList("model::Mismatch"), accessContext().getEntityPaths(null, null, null));
    }

    @Test
    public void testWorkspaceIsolation()
    {
        createEntities(TestTools.newClassEntity("WorkspaceOnly", "model"));
        EntityAccessContext projectContext = this.entityApi.getEntityAccessContext(PROJECT_ID, SourceSpecification.projectSourceSpecification(), null);
        Assert.assertEquals(Collections.emptyList(), projectContext.getEntities(null, null, null));
    }

    // updateEntities

    @Test
    public void testUpdateEntitiesNullValidation()
    {
        LegendSDLCException e1 = Assert.assertThrows(LegendSDLCException.class, () -> modificationContext().updateEntities(null, false, "message"));
        Assert.assertEquals("entities may not be null", e1.getMessage());

        LegendSDLCException e2 = Assert.assertThrows(LegendSDLCException.class, () -> modificationContext().updateEntities(Collections.emptyList(), false, null));
        Assert.assertEquals("message may not be null", e2.getMessage());
    }

    @Test
    public void testUpdateEntitiesCreatesModifiesAndDeletes()
    {
        Entity classA = TestTools.newClassEntity("ClassA", "model");
        Entity classB = TestTools.newClassEntity("ClassB", "model");
        createEntities(classA, classB);

        // no-op update: same entities, no revision is created
        Assert.assertNull(modificationContext().updateEntities(Lists.mutable.with(classA, classB), false, "no-op"));
        Assert.assertNull(modificationContext().updateEntities(Lists.mutable.with(classA, classB), true, "no-op replace"));

        // modify one, add one, without replace: other entities untouched
        Entity classAModified = TestTools.newClassEntity("ClassA", "model", TestTools.newProperty("prop", "String", 0, 1));
        Entity classC = TestTools.newClassEntity("ClassC", "model");
        Revision revision = modificationContext().updateEntities(Lists.mutable.with(classAModified, classC), false, "modify and add");
        Assert.assertNotNull(revision);
        Assert.assertEquals(Lists.mutable.with("model::ClassA", "model::ClassB", "model::ClassC"), sortedEntityPaths());
        Assert.assertEquals(normalize(classAModified).getContent(), accessContext().getEntity("model::ClassA").getContent());

        // replace: entities not in the new set are deleted
        Revision replaceRevision = modificationContext().updateEntities(Lists.mutable.with(classAModified), true, "replace");
        Assert.assertNotNull(replaceRevision);
        Assert.assertEquals(Lists.mutable.with("model::ClassA"), sortedEntityPaths());

        // empty set with replace deletes everything
        Revision deleteAllRevision = modificationContext().updateEntities(Collections.emptyList(), true, "delete all");
        Assert.assertNotNull(deleteAllRevision);
        Assert.assertEquals(Collections.emptyList(), sortedEntityPaths());

        // ... but the project configuration file survives
        Assert.assertNotNull(fileAccessContext().getFile(ProjectStructure.PROJECT_CONFIG_PATH));

        // empty set without replace is a no-op
        Assert.assertNull(modificationContext().updateEntities(Collections.emptyList(), false, "no-op empty"));
    }

    @Test
    public void testUpdateEntitiesValidation()
    {
        // single error: the message is the bare error
        LegendSDLCException e1 = Assert.assertThrows(LegendSDLCException.class,
                () -> modificationContext().updateEntities(Collections.singletonList(TestTools.newClassEntity("Bad Name", "model")), false, "message"));
        Assert.assertEquals("Invalid entity path: \"model::Bad Name\"", e1.getMessage());
        Assert.assertEquals(400, e1.getStatusCode());

        // null entity
        LegendSDLCException e2 = Assert.assertThrows(LegendSDLCException.class,
                () -> modificationContext().updateEntities(Collections.singletonList(null), false, "message"));
        Assert.assertEquals("Invalid entity: null", e2.getMessage());
        Assert.assertEquals(400, e2.getStatusCode());

        // path/package+name mismatch
        Entity mismatch = Entity.newEntity("model::Mismatch", TestTools.newClassEntity("X", "model").getClassifierPath(),
                TestTools.newClassEntity("Other", "pkg").getContent());
        LegendSDLCException e3 = Assert.assertThrows(LegendSDLCException.class,
                () -> modificationContext().updateEntities(Collections.singletonList(mismatch), false, "message"));
        Assert.assertEquals("Entity: model::Mismatch; mismatch between entity path and package (\"pkg\") and name (\"Other\") properties", e3.getMessage());

        // duplicate definitions
        Entity dup = TestTools.newClassEntity("Dup", "model");
        LegendSDLCException e4 = Assert.assertThrows(LegendSDLCException.class,
                () -> modificationContext().updateEntities(Lists.mutable.with(dup, dup), false, "message"));
        Assert.assertEquals("Entity: model::Dup; error: multiple definitions", e4.getMessage());

        // multiple errors are aggregated
        LegendSDLCException e5 = Assert.assertThrows(LegendSDLCException.class,
                () -> modificationContext().updateEntities(Lists.mutable.with(null, TestTools.newClassEntity("Bad Name", "model")), false, "message"));
        Assert.assertEquals("There are errors with entity definitions:\n\tInvalid entity: null\n\tInvalid entity path: \"model::Bad Name\"", e5.getMessage());
    }

    // performChanges

    @Test
    public void testPerformChangesNullValidation()
    {
        LegendSDLCException e1 = Assert.assertThrows(LegendSDLCException.class, () -> modificationContext().performChanges(null, null, "message"));
        Assert.assertEquals("changes may not be null", e1.getMessage());

        LegendSDLCException e2 = Assert.assertThrows(LegendSDLCException.class, () -> modificationContext().performChanges(Collections.emptyList(), null, null));
        Assert.assertEquals("message may not be null", e2.getMessage());
    }

    @Test
    public void testPerformChangesEmptyIsNoOp()
    {
        Assert.assertNull(modificationContext().performChanges(Collections.emptyList(), null, "no changes"));
    }

    @Test
    public void testPerformChangesCreate()
    {
        Entity entity = TestTools.newClassEntity("Created", "model");
        Revision revision = modificationContext().performChanges(
                Collections.singletonList(EntityChange.newCreateEntity(entity.getPath(), entity.getClassifierPath(), entity.getContent())), null, "create");
        Assert.assertNotNull(revision);
        TestTools.assertEntitiesEquivalent(Collections.singletonList(normalize(entity)), accessContext().getEntities(null, null, null));

        // creating an entity that already exists fails
        LegendSDLCException e = Assert.assertThrows(LegendSDLCException.class,
                () -> modificationContext().performChanges(
                        Collections.singletonList(EntityChange.newCreateEntity(entity.getPath(), entity.getClassifierPath(), entity.getContent())), null, "create again"));
        Assert.assertTrue(e.getMessage(), e.getMessage().endsWith(": entity \"model::Created\" already exists"));
        Assert.assertEquals(500, e.getStatusCode());
    }

    @Test
    public void testPerformChangesModify()
    {
        Entity entity = TestTools.newClassEntity("ToModify", "model");
        createEntities(entity);

        // modification with identical content produces no revision
        Assert.assertNull(modificationContext().performChanges(
                Collections.singletonList(EntityChange.newModifyEntity(entity.getPath(), entity.getClassifierPath(), entity.getContent())), null, "no-op modify"));

        // real modification
        Entity modified = TestTools.newClassEntity("ToModify", "model", TestTools.newProperty("prop", "String", 0, 1));
        Revision revision = modificationContext().performChanges(
                Collections.singletonList(EntityChange.newModifyEntity(modified.getPath(), modified.getClassifierPath(), modified.getContent())), null, "modify");
        Assert.assertNotNull(revision);
        Assert.assertEquals(normalize(modified).getContent(), accessContext().getEntity("model::ToModify").getContent());

        // modifying a nonexistent entity fails
        Entity missing = TestTools.newClassEntity("Missing", "model");
        LegendSDLCException e = Assert.assertThrows(LegendSDLCException.class,
                () -> modificationContext().performChanges(
                        Collections.singletonList(EntityChange.newModifyEntity(missing.getPath(), missing.getClassifierPath(), missing.getContent())), null, "modify missing"));
        Assert.assertTrue(e.getMessage(), e.getMessage().endsWith(": could not find entity \"model::Missing\""));
        Assert.assertEquals(500, e.getStatusCode());
    }

    @Test
    public void testPerformChangesDelete()
    {
        Entity entity = TestTools.newClassEntity("ToDelete", "model");
        createEntities(entity);

        Revision revision = modificationContext().performChanges(Collections.singletonList(EntityChange.newDeleteEntity("model::ToDelete")), null, "delete");
        Assert.assertNotNull(revision);
        Assert.assertEquals(Collections.emptyList(), sortedEntityPaths());

        LegendSDLCException e = Assert.assertThrows(LegendSDLCException.class,
                () -> modificationContext().performChanges(Collections.singletonList(EntityChange.newDeleteEntity("model::ToDelete")), null, "delete again"));
        Assert.assertTrue(e.getMessage(), e.getMessage().endsWith(": could not find entity \"model::ToDelete\""));
        Assert.assertEquals(500, e.getStatusCode());
    }

    @Test
    public void testPerformChangesRenameMovesFileWithoutRewritingContent()
    {
        Entity entity = TestTools.newClassEntity("Original", "model");
        createEntities(entity);
        String originalFilePath = expectedFilePath(entity);

        Revision revision = modificationContext().performChanges(
                Collections.singletonList(EntityChange.newRenameEntity("model::Original", "model::renamed::Original")), null, "rename");
        Assert.assertNotNull(revision);

        // the file has moved ...
        Assert.assertNull(fileAccessContext().getFile(originalFilePath));
        String newFilePath = expectedFilePath(Entity.newEntity("model::renamed::Original", entity.getClassifierPath(), entity.getContent()));
        Assert.assertNotNull(fileAccessContext().getFile(newFilePath));

        // ... but its content still declares the old package, so the entity file is now internally inconsistent:
        // reading the renamed entity fails with a path mismatch (RENAME does not rewrite file content)
        LegendSDLCException e = Assert.assertThrows(LegendSDLCException.class, () -> accessContext().getEntity("model::renamed::Original"));
        Assert.assertEquals("Error deserializing entity \"model::renamed::Original\" from file \"" + newFilePath + "\": Expected entity path model::renamed::Original, found model::Original", e.getMessage());

        // renaming a nonexistent entity fails
        LegendSDLCException e2 = Assert.assertThrows(LegendSDLCException.class,
                () -> modificationContext().performChanges(
                        Collections.singletonList(EntityChange.newRenameEntity("model::Missing", "model::StillMissing")), null, "rename missing"));
        Assert.assertTrue(e2.getMessage(), e2.getMessage().endsWith(": could not find entity \"model::Missing\""));
    }

    @Test
    public void testPerformChangesValidation()
    {
        Entity entity = TestTools.newClassEntity("Subject", "model");

        // CREATE without content
        LegendSDLCException e1 = Assert.assertThrows(LegendSDLCException.class,
                () -> modificationContext().performChanges(Collections.singletonList(EntityChange.newCreateEntity("model::Subject", entity.getClassifierPath(), null)), null, "message"));
        Assert.assertTrue(e1.getMessage(), e1.getMessage().startsWith("There are entity change errors:\n\tEntity change #1 (")
                && e1.getMessage().endsWith("):\n\t\tMissing content"));
        Assert.assertEquals(400, e1.getStatusCode());

        // DELETE with unexpected classifier and content
        LegendSDLCException e2 = Assert.assertThrows(LegendSDLCException.class,
                () -> modificationContext().performChanges(Collections.singletonList(EntityChange.newModifyEntity("model::Subject", null, null)), null, "message"));
        Assert.assertTrue(e2.getMessage(), e2.getMessage().contains("Missing classifier path") && e2.getMessage().contains("Missing content"));

        // RENAME with invalid new path
        LegendSDLCException e3 = Assert.assertThrows(LegendSDLCException.class,
                () -> modificationContext().performChanges(Collections.singletonList(EntityChange.newRenameEntity("model::Subject", "not a path")), null, "message"));
        Assert.assertTrue(e3.getMessage(), e3.getMessage().contains("Invalid new entity path: not a path"));

        // path/package+name mismatch on CREATE
        LegendSDLCException e4 = Assert.assertThrows(LegendSDLCException.class,
                () -> modificationContext().performChanges(
                        Collections.singletonList(EntityChange.newCreateEntity("model::Subject", entity.getClassifierPath(), TestTools.newClassEntity("Other", "pkg").getContent())), null, "message"));
        Assert.assertTrue(e4.getMessage(), e4.getMessage().contains("Mismatch between entity path (\"model::Subject\") and package (\"pkg\") and name (\"Other\") properties"));

        // errors across multiple changes are numbered
        LegendSDLCException e5 = Assert.assertThrows(LegendSDLCException.class,
                () -> modificationContext().performChanges(Lists.mutable.with(
                        EntityChange.newCreateEntity("model::Subject", entity.getClassifierPath(), null),
                        EntityChange.newDeleteEntity(null)), null, "message"));
        Assert.assertTrue(e5.getMessage(), e5.getMessage().contains("Entity change #1 (") && e5.getMessage().contains("Entity change #2 (")
                && e5.getMessage().contains("Missing content") && e5.getMessage().contains("Missing entity path"));
    }

    // Helpers

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
        return this.fileAccessProvider.getFileAccessContext(PROJECT_ID, this.workspaceSourceSpec, null);
    }

    protected void createEntities(Entity... entities)
    {
        List<EntityChange> changes = Lists.mutable.empty();
        for (Entity entity : entities)
        {
            changes.add(EntityChange.newCreateEntity(entity.getPath(), entity.getClassifierPath(), entity.getContent()));
        }
        Revision revision = modificationContext().performChanges(changes, null, "create test entities");
        Assert.assertNotNull(revision);
    }

    protected List<String> sortedEntityPaths()
    {
        return Lists.mutable.withAll(accessContext().getEntityPaths(null, null, null)).sortThis();
    }

    protected ProjectStructure projectStructure()
    {
        return ProjectStructure.getProjectStructure(fileAccessContext());
    }

    protected String expectedFilePath(Entity entity)
    {
        EntitySourceDirectory sourceDirectory = projectStructure().findSourceDirectoryForEntity(entity);
        Assert.assertNotNull("no source directory for " + entity.getPath(), sourceDirectory);
        return sourceDirectory.entityPathToFilePath(entity.getPath());
    }

    protected byte[] serialize(Entity entity)
    {
        EntitySourceDirectory sourceDirectory = projectStructure().findSourceDirectoryForEntity(entity);
        Assert.assertNotNull("no source directory for " + entity.getPath(), sourceDirectory);
        return sourceDirectory.serializeToBytes(entity);
    }

    protected Entity normalize(Entity entity)
    {
        EntitySourceDirectory sourceDirectory = projectStructure().findSourceDirectoryForEntity(entity);
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

    protected String plantFile(String entityPath, byte[] content)
    {
        ProjectStructure projectStructure = projectStructure();
        EntitySourceDirectory sourceDirectory = projectStructure.getEntitySourceDirectories().get(0);
        String filePath = sourceDirectory.entityPathToFilePath(entityPath);
        this.fileAccessProvider.getFileModificationContext(PROJECT_ID, this.workspaceSourceSpec, null)
                .submit("plant file " + filePath, Collections.singletonList(ProjectFileOperation.addFile(filePath, content)));
        return filePath;
    }
}
