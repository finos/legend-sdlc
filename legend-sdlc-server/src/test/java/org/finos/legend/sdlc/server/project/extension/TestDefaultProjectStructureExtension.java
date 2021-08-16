// Copyright 2020 Goldman Sachs
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

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.server.project.InMemoryProjectFileAccessProvider;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.project.ProjectFileOperation;
import org.finos.legend.sdlc.server.project.ProjectStructure;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class TestDefaultProjectStructureExtension
{
    protected static final String PROJECT_ID = "project_id";
    protected static final String WORKSPACE_ID = "workspace_id";
    protected static final String AUTHOR = "author";
    protected static final String COMMITTER = "committer";

    @Test
    public void testVersions()
    {
        for (int i = 0; i <= ProjectStructure.getLatestProjectStructureVersion(); i++)
        {
            for (int j = 0; j < 5; j++)
            {
                DefaultProjectStructureExtension extension = newProjectStructureExtension(i, j);
                Assert.assertEquals(i, extension.getProjectStructureVersion());
                Assert.assertEquals(j, extension.getVersion());
            }
        }
    }

    @Test
    public void testCollectUpdateProjectConfigurationOperations_Vacuous()
    {
        DefaultProjectStructureExtension extension = newProjectStructureExtension(0, 0);

        InMemoryProjectFileAccessProvider fileAccessProvider = new InMemoryProjectFileAccessProvider(AUTHOR, COMMITTER);
        fileAccessProvider.createWorkspace(PROJECT_ID, WORKSPACE_ID);
        ProjectFileAccessProvider.FileAccessContext fileAccessContext = fileAccessProvider.getFileAccessContext(PROJECT_ID, WORKSPACE_ID, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE, null);
        Assert.assertEquals(0L, fileAccessContext.getFiles().count());

        List<ProjectFileOperation> operations = Lists.mutable.empty();
        extension.collectUpdateProjectConfigurationOperations(null, null, fileAccessContext, operations::add);
        Assert.assertEquals(Collections.emptyList(), operations);
        Assert.assertEquals(0L, fileAccessContext.getFiles().count());
    }

    @Test
    public void testCollectUpdateProjectConfigurationOperations_EmptyStart()
    {
        String file1Path = "/file1.txt";
        String file1Content = "the quick brown fox";
        String file2Path = "/dir/file2.txt";
        String file2Content = "jumped over the lazy dog";
        DefaultProjectStructureExtension extension = newProjectStructureExtension(0, 0, file1Path, file1Content, file2Path, file2Content);

        InMemoryProjectFileAccessProvider fileAccessProvider = new InMemoryProjectFileAccessProvider(AUTHOR, COMMITTER);
        fileAccessProvider.createWorkspace(PROJECT_ID, WORKSPACE_ID);
        ProjectFileAccessProvider.FileAccessContext fileAccessContext = fileAccessProvider.getFileAccessContext(PROJECT_ID, WORKSPACE_ID, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE, null);
        Assert.assertEquals(0L, fileAccessContext.getFiles().count());

        List<ProjectFileOperation> operations = Lists.mutable.empty();
        extension.collectUpdateProjectConfigurationOperations(null, null, fileAccessContext, operations::add);
        Assert.assertEquals(2, operations.size());

        ProjectFileAccessProvider.FileModificationContext modificationContext = fileAccessProvider.getFileModificationContext(PROJECT_ID, WORKSPACE_ID, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE, null);
        modificationContext.submit("message", operations);

        Assert.assertEquals(2L, fileAccessContext.getFiles().count());

        ProjectFileAccessProvider.ProjectFile file1 = fileAccessContext.getFile(file1Path);
        Assert.assertNotNull(file1Path, file1);
        Assert.assertEquals(file1Content, file1.getContentAsString());

        ProjectFileAccessProvider.ProjectFile file2 = fileAccessContext.getFile(file2Path);
        Assert.assertNotNull(file2Path, file2);
        Assert.assertEquals(file2Content, file2.getContentAsString());
    }

    @Test
    public void testCollectUpdateProjectConfigurationOperations_NonEmptyStart()
    {
        String file1Path = "/file1.txt";
        String file1ContentBefore = "the quick brown fox jumped over the lazy dog";
        String file1ContentAfter = "THE QUICK BROWN FOX";
        String file2Path = "/dir/file2.txt";
        String file2ContentAfter = "JUMPED OVER THE LAZY DOG";

        DefaultProjectStructureExtension extension = newProjectStructureExtension(0, 0, file1Path, file1ContentAfter, file2Path, file2ContentAfter);

        InMemoryProjectFileAccessProvider fileAccessProvider = new InMemoryProjectFileAccessProvider(AUTHOR, COMMITTER);
        fileAccessProvider.createWorkspace(PROJECT_ID, WORKSPACE_ID);
        ProjectFileAccessProvider.FileAccessContext fileAccessContext = fileAccessProvider.getFileAccessContext(PROJECT_ID, WORKSPACE_ID, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE, null);
        Assert.assertEquals(0L, fileAccessContext.getFiles().count());

        ProjectFileAccessProvider.FileModificationContext modificationContext = fileAccessProvider.getFileModificationContext(PROJECT_ID, WORKSPACE_ID, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE, null);
        modificationContext.submit("initial state", Collections.singletonList(ProjectFileOperation.addFile(file1Path, file1ContentBefore)));
        Assert.assertEquals(1L, fileAccessContext.getFiles().count());

        Assert.assertNotNull(file1Path, fileAccessContext.getFile(file1Path));
        Assert.assertEquals(file1ContentBefore, fileAccessContext.getFile(file1Path).getContentAsString());

        Assert.assertNull(file2Path, fileAccessContext.getFile(file2Path));

        List<ProjectFileOperation> operations = Lists.mutable.empty();
        extension.collectUpdateProjectConfigurationOperations(null, null, fileAccessContext, operations::add);
        Assert.assertEquals(2, operations.size());

        modificationContext.submit("update", operations);

        Assert.assertEquals(2L, fileAccessContext.getFiles().count());

        Assert.assertNotNull(file1Path, fileAccessContext.getFile(file1Path));
        Assert.assertEquals(file1ContentAfter, fileAccessContext.getFile(file1Path).getContentAsString());

        Assert.assertNotNull(file2Path, fileAccessContext.getFile(file2Path));
        Assert.assertEquals(file2ContentAfter, fileAccessContext.getFile(file2Path).getContentAsString());
    }

    @Test
    public void testFilePathCanonicalization()
    {
        String filePath = "file1.txt";
        String canonicalFilePath = "/file1.txt";
        String fileContent = "the quick brown fox jumped over the lazy dog";

        InMemoryProjectFileAccessProvider fileAccessProvider = new InMemoryProjectFileAccessProvider(AUTHOR, COMMITTER);
        fileAccessProvider.createWorkspace(PROJECT_ID, WORKSPACE_ID);
        ProjectFileAccessProvider.FileAccessContext fileAccessContext = fileAccessProvider.getFileAccessContext(PROJECT_ID, WORKSPACE_ID, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE, null);
        Assert.assertEquals(0L, fileAccessContext.getFiles().count());

        DefaultProjectStructureExtension extension = newProjectStructureExtension(0, 0, filePath, fileContent);
        List<ProjectFileOperation> operations = Lists.mutable.empty();
        extension.collectUpdateProjectConfigurationOperations(null, null, fileAccessContext, operations::add);
        Assert.assertEquals(1, operations.size());

        ProjectFileAccessProvider.FileModificationContext modificationContext = fileAccessProvider.getFileModificationContext(PROJECT_ID, WORKSPACE_ID, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE, null);
        modificationContext.submit("update", operations);

        Assert.assertNull(filePath, fileAccessContext.getFile(filePath));
        Assert.assertNotNull(canonicalFilePath, fileAccessContext.getFile(canonicalFilePath));
        Assert.assertEquals(fileContent, fileAccessContext.getFile(canonicalFilePath).getContentAsString());
    }

    @Test
    public void testFilePathCanonicalizationConflict()
    {
        String filePath = "file1.txt";
        String canonicalFilePath = "/file1.txt";
        String fileContent = "the quick brown fox jumped over the lazy dog";

        InMemoryProjectFileAccessProvider fileAccessProvider = new InMemoryProjectFileAccessProvider(AUTHOR, COMMITTER);
        fileAccessProvider.createWorkspace(PROJECT_ID, WORKSPACE_ID);
        ProjectFileAccessProvider.FileAccessContext fileAccessContext = fileAccessProvider.getFileAccessContext(PROJECT_ID, WORKSPACE_ID, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE, null);
        Assert.assertEquals(0L, fileAccessContext.getFiles().count());

        RuntimeException e = Assert.assertThrows(RuntimeException.class, () -> newProjectStructureExtension(0, 0, filePath, fileContent, canonicalFilePath, "other content"));
        Assert.assertEquals("Multiple definitions for \"/file1.txt\" for project structure version 0, extension 0: one for \"/file1.txt\" and another for \"file1.txt\"", e.getMessage());
    }

    protected DefaultProjectStructureExtension newProjectStructureExtension(int projectStructureVersion, int extensionVersion, String... projectFiles)
    {
        if ((projectFiles == null) || (projectFiles.length == 0))
        {
            return newProjectStructureExtension(projectStructureVersion, extensionVersion, Collections.emptyMap());
        }
        if ((projectFiles.length % 2) != 0)
        {
            throw new IllegalArgumentException("projectFiles must have an even number of values, found: " + projectFiles.length);
        }

        Map<String, String> projectFilesMap = Maps.mutable.ofInitialCapacity(projectFiles.length / 2);
        for (int i = 0; i < projectFiles.length; i += 2)
        {
            projectFilesMap.put(projectFiles[i], projectFiles[i + 1]);
        }
        return newProjectStructureExtension(projectStructureVersion, extensionVersion, projectFilesMap);
    }

    protected DefaultProjectStructureExtension newProjectStructureExtension(int projectStructureVersion, int extensionVersion, Map<String, String> projectFiles)
    {
        return DefaultProjectStructureExtension.newProjectStructureExtension(projectStructureVersion, extensionVersion, projectFiles);
    }
}
