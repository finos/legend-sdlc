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

package org.finos.legend.sdlc.project.files;

import org.eclipse.collections.api.list.MutableList;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider.FileAccessContext;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider.FileModificationContext;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider.ProjectFile;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestRootedContexts
{
    private Map<String, String> files;
    private FileAccessContext access;
    private FileModificationContext modification;

    @Before
    public void setUp()
    {
        this.files = new TreeMap<>();
        this.files.put("/README.md", "repo readme");
        this.files.put("/analytics/model/project.json", "{}");
        this.files.put("/analytics/model/entities/model/Person.json", "person");
        this.files.put("/analytics/model/entities/model/Firm.json", "firm");
        this.files.put("/analytics/other.txt", "other");
        this.access = new MapFileAccessContext();
        this.modification = new MapFileModificationContext();
    }

    @Test
    public void testRootedAccessStripsAndResolves()
    {
        FileAccessContext rooted = RootedFileAccessContext.root(this.access, "/analytics/model");

        ProjectFile config = rooted.getFile("/project.json");
        Assert.assertNotNull(config);
        Assert.assertEquals("/project.json", config.getPath());
        Assert.assertEquals("{}", config.getContentAsString());

        Assert.assertTrue(rooted.fileExists("/entities/model/Person.json"));
        Assert.assertFalse(rooted.fileExists("/README.md"));
        Assert.assertNull(rooted.getFile("/other.txt"));

        try (Stream<ProjectFile> stream = rooted.getFiles())
        {
            List<String> paths = stream.map(ProjectFile::getPath).sorted().collect(Collectors.toList());
            Assert.assertEquals(Arrays.asList("/entities/model/Firm.json", "/entities/model/Person.json", "/project.json"), paths);
        }

        try (Stream<ProjectFile> stream = rooted.getFilesInDirectory("/entities"))
        {
            List<String> paths = stream.map(ProjectFile::getPath).sorted().collect(Collectors.toList());
            Assert.assertEquals(Arrays.asList("/entities/model/Firm.json", "/entities/model/Person.json"), paths);
        }
    }

    @Test
    public void testRootAtRootDirectoryIsUnwrapped()
    {
        Assert.assertSame(this.access, RootedFileAccessContext.root(this.access, "/"));
        Assert.assertSame(this.modification, RootedFileModificationContext.root(this.modification, "/"));
    }

    @Test
    public void testRootedModificationPrefixesOperations()
    {
        FileModificationContext rooted = RootedFileModificationContext.root(this.modification, "/analytics/model");
        rooted.submit("changes", Arrays.asList(
                ProjectFileOperation.addFile("/entities/model/Address.json", "address"),
                ProjectFileOperation.modifyFile("/entities/model/Person.json", "person v2"),
                ProjectFileOperation.deleteFile("/entities/model/Firm.json"),
                ProjectFileOperation.moveFile("/project.json", "/project2.json")));

        Assert.assertEquals("address", this.files.get("/analytics/model/entities/model/Address.json"));
        Assert.assertEquals("person v2", this.files.get("/analytics/model/entities/model/Person.json"));
        Assert.assertFalse(this.files.containsKey("/analytics/model/entities/model/Firm.json"));
        Assert.assertFalse(this.files.containsKey("/analytics/model/project.json"));
        Assert.assertEquals("{}", this.files.get("/analytics/model/project2.json"));
        Assert.assertEquals("repo readme", this.files.get("/README.md"));
    }

    private class MapFileAccessContext extends AbstractFileAccessContext
    {
        @Override
        protected Stream<ProjectFile> getFilesInCanonicalDirectories(MutableList<String> directories)
        {
            return TestRootedContexts.this.files.keySet().stream()
                    .filter(path -> directories.anySatisfy(dir -> ProjectPaths.ROOT_DIRECTORY.equals(dir) || path.startsWith(dir)))
                    .map(this::getFile);
        }

        @Override
        public ProjectFile getFile(String path)
        {
            String canonicalPath = ProjectPaths.canonicalizeFile(path);
            String content = TestRootedContexts.this.files.get(canonicalPath);
            return (content == null) ? null : ProjectFiles.newStringProjectFile(canonicalPath, content);
        }
    }

    private class MapFileModificationContext implements FileModificationContext
    {
        @Override
        public Revision submit(String message, List<? extends ProjectFileOperation> operations)
        {
            operations.forEach(op ->
            {
                if (op instanceof ProjectFileOperation.AddFile)
                {
                    TestRootedContexts.this.files.put(op.getPath(), new String(((ProjectFileOperation.AddFile) op).getContent(), StandardCharsets.UTF_8));
                }
                else if (op instanceof ProjectFileOperation.ModifyFile)
                {
                    TestRootedContexts.this.files.put(op.getPath(), new String(((ProjectFileOperation.ModifyFile) op).getNewContent(), StandardCharsets.UTF_8));
                }
                else if (op instanceof ProjectFileOperation.DeleteFile)
                {
                    TestRootedContexts.this.files.remove(op.getPath());
                }
                else if (op instanceof ProjectFileOperation.MoveFile)
                {
                    ProjectFileOperation.MoveFile move = (ProjectFileOperation.MoveFile) op;
                    String oldContent = TestRootedContexts.this.files.remove(move.getPath());
                    TestRootedContexts.this.files.put(move.getNewPath(), (move.getNewContent() == null) ? oldContent : new String(move.getNewContent(), StandardCharsets.UTF_8));
                }
            });
            return null;
        }
    }
}
