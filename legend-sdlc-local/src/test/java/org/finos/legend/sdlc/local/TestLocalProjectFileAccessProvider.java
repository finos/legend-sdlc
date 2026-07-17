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

import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.error.LegendSDLCException;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider.FileAccessContext;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider.FileModificationContext;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider.ProjectFile;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider.RevisionAccessContext;
import org.finos.legend.sdlc.project.files.ProjectFileOperation;
import org.finos.legend.sdlc.project.source.SourceSpecification;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.project.workspace.WorkspaceSpecification;
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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestLocalProjectFileAccessProvider
{
    private static final String PROJECT_ID = "proj";

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private Path root;
    private LocalProjectFileAccessProvider provider;

    @Before
    public void setUp() throws IOException
    {
        this.root = this.tempFolder.getRoot().toPath();
        write("project.json", "{}");
        write("entities/model/Person.json", "person");
        write("other/notes.txt", "notes");
        this.provider = new LocalProjectFileAccessProvider(this.root, PROJECT_ID);
    }

    @Test
    public void testFileAccess()
    {
        FileAccessContext context = this.provider.getFileAccessContext(PROJECT_ID, SourceSpecification.projectSourceSpecification(), null);

        ProjectFile file = context.getFile("/project.json");
        Assert.assertNotNull(file);
        Assert.assertEquals("/project.json", file.getPath());
        Assert.assertEquals("{}", file.getContentAsString());
        Assert.assertNull(context.getFile("/no/such/file.txt"));
        Assert.assertTrue(context.fileExists("/entities/model/Person.json"));

        try (Stream<ProjectFile> files = context.getFiles())
        {
            Assert.assertEquals(
                    Arrays.asList("/entities/model/Person.json", "/other/notes.txt", "/project.json"),
                    files.map(ProjectFile::getPath).sorted().collect(Collectors.toList()));
        }
        try (Stream<ProjectFile> files = context.getFilesInDirectory("/entities"))
        {
            Assert.assertEquals(
                    Collections.singletonList("/entities/model/Person.json"),
                    files.map(ProjectFile::getPath).collect(Collectors.toList()));
        }
        try (Stream<ProjectFile> files = context.getFilesInDirectory("/nonexistent"))
        {
            Assert.assertEquals(0L, files.count());
        }
    }

    @Test
    public void testReadsAreLive() throws IOException
    {
        FileAccessContext context = this.provider.getFileAccessContext(PROJECT_ID, SourceSpecification.projectSourceSpecification(), null);
        Assert.assertEquals("{}", context.getFile("/project.json").getContentAsString());
        write("project.json", "{\"changed\":true}");
        Assert.assertEquals("{\"changed\":true}", context.getFile("/project.json").getContentAsString());
    }

    @Test
    public void testPathEscapeRejected()
    {
        FileAccessContext context = this.provider.getFileAccessContext(PROJECT_ID, SourceSpecification.projectSourceSpecification(), null);
        Assert.assertThrows(IllegalArgumentException.class, () -> context.getFile("/../outside.txt"));
        Assert.assertThrows(IllegalArgumentException.class, () -> context.getFile("/a/./b.txt"));
        Assert.assertThrows(IllegalArgumentException.class, () -> context.getFile("//double.txt"));
    }

    @Test
    public void testModification() throws IOException
    {
        FileModificationContext context = this.provider.getFileModificationContext(PROJECT_ID, SourceSpecification.projectSourceSpecification(), null);
        Revision revision = context.submit("changes", Arrays.asList(
                ProjectFileOperation.addFile("/entities/model/Firm.json", "firm"),
                ProjectFileOperation.modifyFile("/entities/model/Person.json", "person v2"),
                ProjectFileOperation.moveFile("/other/notes.txt", "/docs/notes.txt")));

        Assert.assertEquals(LocalProjectFileAccessProvider.WORKING_COPY_REVISION_ID, revision.getId());
        Assert.assertEquals("firm", read("entities/model/Firm.json"));
        Assert.assertEquals("person v2", read("entities/model/Person.json"));
        Assert.assertEquals("notes", read("docs/notes.txt"));
        // the directory emptied by the move is pruned
        Assert.assertFalse(Files.exists(this.root.resolve("other")));

        context.submit("delete", Collections.singletonList(ProjectFileOperation.deleteFile("/entities/model/Firm.json")));
        Assert.assertFalse(Files.exists(this.root.resolve("entities/model/Firm.json")));
        // Person.json still holds the directory open
        Assert.assertTrue(Files.exists(this.root.resolve("entities/model")));
    }

    @Test
    public void testModificationValidation()
    {
        FileModificationContext context = this.provider.getFileModificationContext(PROJECT_ID, SourceSpecification.projectSourceSpecification(), null);

        LegendSDLCException e1 = Assert.assertThrows(LegendSDLCException.class,
                () -> context.submit("m", Collections.singletonList(ProjectFileOperation.addFile("/project.json", "x"))));
        Assert.assertTrue(e1.getMessage(), e1.getMessage().contains("already exists"));

        LegendSDLCException e2 = Assert.assertThrows(LegendSDLCException.class,
                () -> context.submit("m", Collections.singletonList(ProjectFileOperation.modifyFile("/no.txt", "x"))));
        Assert.assertTrue(e2.getMessage(), e2.getMessage().contains("does not exist"));

        // validation happens before application: the valid first operation must not be applied
        Assert.assertThrows(LegendSDLCException.class, () -> context.submit("m", Arrays.asList(
                ProjectFileOperation.addFile("/new.txt", "x"),
                ProjectFileOperation.deleteFile("/no.txt"))));
        Assert.assertFalse(Files.exists(this.root.resolve("new.txt")));
    }

    @Test
    public void testWorkingCopyRevisionContract()
    {
        RevisionAccessContext context = this.provider.getRevisionAccessContext(PROJECT_ID, SourceSpecification.projectSourceSpecification(), null);
        Revision current = context.getCurrentRevision();
        Assert.assertEquals(LocalProjectFileAccessProvider.WORKING_COPY_REVISION_ID, current.getId());
        Assert.assertEquals(current.getId(), context.getBaseRevision().getId());
        Assert.assertEquals(current.getId(), context.getRevision(LocalProjectFileAccessProvider.WORKING_COPY_REVISION_ID).getId());
        Assert.assertNull(context.getRevision("abcd1234"));

        try (Stream<Revision> revisions = context.getAllRevisions(null, null, null, null))
        {
            Assert.assertEquals(Collections.singletonList(current.getId()), revisions.map(Revision::getId).collect(Collectors.toList()));
        }
        try (Stream<Revision> revisions = context.getAllRevisions(r -> false, null, null, null))
        {
            Assert.assertEquals(0L, revisions.count());
        }
        try (Stream<Revision> revisions = context.getAllRevisions(null, null, null, 0))
        {
            Assert.assertEquals(0L, revisions.count());
        }

        // the working-copy revision id is a valid reference for file access and modification
        Assert.assertNotNull(this.provider.getFileAccessContext(PROJECT_ID, SourceSpecification.projectSourceSpecification(), LocalProjectFileAccessProvider.WORKING_COPY_REVISION_ID));
        Assert.assertThrows(IllegalArgumentException.class,
                () -> this.provider.getFileAccessContext(PROJECT_ID, SourceSpecification.projectSourceSpecification(), "abcd1234"));
    }

    @Test
    public void testOnlyProjectSourceSupported()
    {
        SourceSpecification workspaceSource = SourceSpecification.workspaceSourceSpecification(WorkspaceSpecification.newWorkspaceSpecification("ws1", WorkspaceType.USER));
        Assert.assertThrows(UnsupportedOperationException.class, () -> this.provider.getFileAccessContext(PROJECT_ID, workspaceSource, null));
        Assert.assertThrows(UnsupportedOperationException.class, () -> this.provider.getFileModificationContext(PROJECT_ID, workspaceSource, null));
        Assert.assertThrows(UnsupportedOperationException.class, () -> this.provider.getRevisionAccessContext(PROJECT_ID, workspaceSource, null));
        Assert.assertThrows(IllegalArgumentException.class, () -> this.provider.getFileAccessContext("other-project", SourceSpecification.projectSourceSpecification(), null));
    }

    private void write(String relativePath, String content) throws IOException
    {
        Path file = this.root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }

    private String read(String relativePath) throws IOException
    {
        return new String(Files.readAllBytes(this.root.resolve(relativePath)), StandardCharsets.UTF_8);
    }
}
