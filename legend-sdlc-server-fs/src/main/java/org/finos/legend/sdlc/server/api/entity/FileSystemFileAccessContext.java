// Copyright 2023 Goldman Sachs
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

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.finos.legend.sdlc.server.api.BaseFSApi;
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification;
import org.finos.legend.sdlc.server.exception.FSException;
import org.finos.legend.sdlc.server.project.AbstractFileAccessContext;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.project.ProjectFiles;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class FileSystemFileAccessContext extends AbstractFileAccessContext
{
    protected final String projectId;
    private final SourceSpecification sourceSpecification;
    private final String revisionId;

    public FileSystemFileAccessContext(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
        this.projectId = projectId;
        this.sourceSpecification = sourceSpecification;
        this.revisionId = revisionId;
    }

    @Override
    protected Stream<ProjectFileAccessProvider.ProjectFile> getFilesInCanonicalDirectories(MutableList<String> directories)
    {
        List<ProjectFileAccessProvider.ProjectFile> files = new ArrayList<>();
        Repository repo = BaseFSApi.retrieveRepo(this.projectId);
        try
        {
            ObjectId commitId = ObjectId.fromString(revisionId);
            RevCommit commit = repo.parseCommit(commitId);
            RevTree tree = commit.getTree();
            TreeWalk treeWalk = new TreeWalk(repo);
            treeWalk.addTree(tree);
            treeWalk.setRecursive(true);

            while (treeWalk.next())
            {
                String path = treeWalk.getPathString();
                for (String dir : directories)
                {
                    if (path.startsWith(dir))
                    {
                        files.add(getFile(path));
                        break; // No need to check other directories for this file
                    }
                }
            }
        }
        catch (Exception e)
        {
            throw FSException.getLegendSDLCServerException("Error getting files in directories for " + projectId, e);
        }
        return files.stream();
    }

    @Override
    public ProjectFileAccessProvider.ProjectFile getFile(String path)
    {
        String branchName = BaseFSApi.getRefBranchName(sourceSpecification);
        try
        {
            Repository repo = BaseFSApi.retrieveRepo(this.projectId);
            RevWalk revWalk = new RevWalk(repo);
            RevCommit branchCommit = revWalk.parseCommit(repo.resolve(branchName));
            RevTree branchTree = branchCommit.getTree();
            path = path.startsWith("/") ? path.substring(1) : path;
            try (TreeWalk treeWalk = TreeWalk.forPath(repo, path, branchTree))
            {
                if (treeWalk != null)
                {
                    ObjectId objectId = treeWalk.getObjectId(0);
                    ObjectReader objectReader = repo.newObjectReader();
                    byte[] fileBytes = objectReader.open(objectId).getBytes();
                    return ProjectFiles.newByteArrayProjectFile(path, fileBytes);
                }
            }
        }
        catch (Exception e)
        {
            throw FSException.getLegendSDLCServerException("Error getting file " + path, e);
        }
        return null;
    }

    @Override
    public boolean fileExists(String path)
    {
        String workspaceId = BaseFSApi.getRefBranchName(sourceSpecification);
        try
        {
            Repository repo = BaseFSApi.retrieveRepo(this.projectId);
            RevWalk revWalk = new RevWalk(repo);
            RevCommit branchCommit = revWalk.parseCommit(repo.resolve(workspaceId));
            RevTree branchTree = branchCommit.getTree();
            path = path.startsWith("/") ? path.substring(1) : path;
            try (TreeWalk treeWalk = TreeWalk.forPath(repo, path, branchTree))
            {
                return treeWalk != null;
            }
        }
        catch (Exception e)
        {
            throw FSException.getLegendSDLCServerException("Error occurred while parsing Git commit for workspace " + workspaceId, e);
        }
    }
}
