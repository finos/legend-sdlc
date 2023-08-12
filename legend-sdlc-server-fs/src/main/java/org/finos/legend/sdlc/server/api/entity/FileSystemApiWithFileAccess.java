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
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.server.api.BaseFSApi;
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification;
import org.finos.legend.sdlc.server.domain.model.revision.FileSystemRevision;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.exception.FSException;
import org.finos.legend.sdlc.server.project.AbstractFileAccessContext;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.project.ProjectFileOperation;
import org.finos.legend.sdlc.server.project.ProjectFiles;
import org.finos.legend.sdlc.server.startup.FSConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

public abstract class FileSystemApiWithFileAccess extends BaseFSApi
{
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemApiWithFileAccess.class);

    public FileSystemApiWithFileAccess(FSConfiguration fsConfiguration)
    {
        super(fsConfiguration);
    }

    private class FileSystemFileAccessContext extends AbstractFileAccessContext
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
            Repository repo = retrieveRepo(this.projectId);
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
                    if (directories.anySatisfy(d -> path.startsWith(d)))
                    {
                        files.add(getFile(path));
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
            String branchName = getRefBranchName(sourceSpecification);
            try
            {
                Repository repo = retrieveRepo(this.projectId);
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
            String workspaceId = getRefBranchName(sourceSpecification);
            try
            {
                Repository repo = retrieveRepo(this.projectId);
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

    private class FileSystemRevisionAccessContext implements ProjectFileAccessProvider.RevisionAccessContext
    {
        private final String projectId;
        private final SourceSpecification sourceSpecification;

        public FileSystemRevisionAccessContext(String projectId, SourceSpecification sourceSpecification)
        {
            this.projectId = projectId;
            this.sourceSpecification = Objects.requireNonNull(sourceSpecification, "source specification may not be null");
        }

        @Override
        public Revision getCurrentRevision()
        {
            String branchName = getRefBranchName(sourceSpecification);
            Repository repo = retrieveRepo(this.projectId);
            try
            {
                ObjectId commitId = repo.resolve(branchName);
                RevWalk revWalk = new RevWalk(repo);
                RevCommit commit = revWalk.parseCommit(commitId);
                revWalk.dispose();
                return getRevisionInfo(commit);
            }
            catch (Exception e)
            {
                throw FSException.getLegendSDLCServerException("Failed to get current revision for branch " + branchName + " in project " + this.projectId, e);
            }
        }

        @Override
        public Revision getBaseRevision()
        {
            String branchName = getRefBranchName(sourceSpecification);
            Repository repo = retrieveRepo(this.projectId);
            try
            {
                Ref branchRef = repo.exactRef(Constants.R_HEADS + branchName);
                ObjectId branchCommitId = branchRef.getObjectId();
                ObjectId masterCommitId = repo.resolve(Constants.R_HEADS + "master");
                RevWalk revWalk = new RevWalk(repo);
                RevCommit branchCommit = revWalk.parseCommit(branchCommitId);
                if (masterCommitId.equals(branchCommitId)) // If branch is master, return the first commit
                {
                    RevCommit baseCommit = revWalk.parseCommit(masterCommitId);
                    revWalk.dispose();
                    return getRevisionInfo(baseCommit);
                }
                else
                {
                    revWalk.markStart(branchCommit);
                    revWalk.markStart(revWalk.parseCommit(masterCommitId));
                    RevCommit baseCommit = revWalk.next(); // Finds the common commit which is merge base
                    revWalk.dispose();
                    return getRevisionInfo(baseCommit);
                }
            }
            catch (Exception e)
            {
                throw FSException.getLegendSDLCServerException("Failed to get current revision for branch " + branchName + " in project " + this.projectId, e);
            }
        }

        @Override
        public Revision getRevision(String revisionId)
        {
            LegendSDLCServerException.validateNonNull(revisionId, "revisionId may not be null");
            String resolvedRevisionId = resolveRevisionId(revisionId, this);
            if (resolvedRevisionId == null)
            {
                throw new LegendSDLCServerException("Failed to resolve revision " + revisionId + " of project " + this.projectId, Response.Status.NOT_FOUND);
            }
            String branchName = getRefBranchName(sourceSpecification);
            Repository repo = retrieveRepo(this.projectId);
            try
            {
                ObjectId commitId = ObjectId.fromString(resolvedRevisionId);
                RevWalk revWalk = new RevWalk(repo);
                RevCommit commit = revWalk.parseCommit(commitId);
                revWalk.dispose();
                return getRevisionInfo(commit);
            }
            catch (Exception e)
            {
                throw FSException.getLegendSDLCServerException("Failed to get " + resolvedRevisionId + " revision for branch " + branchName + " in project " + this.projectId, e);
            }
        }

        @Override
        public Stream<Revision> getAllRevisions(Predicate<? super Revision> predicate, Instant since, Instant until, Integer limit)
        {
            throw new UnsupportedOperationException("Not implemented");
        }

        private Revision getRevisionInfo(RevCommit commit)
        {
            String revisionID = commit.getId().getName();
            String authorName = commit.getAuthorIdent().getName();
            Instant authoredTimeStamp = commit.getAuthorIdent().getWhenAsInstant();
            String committerName = commit.getCommitterIdent().getName();
            Instant committedTimeStamp = commit.getCommitterIdent().getWhenAsInstant();
            String message = commit.getFullMessage();
            return new FileSystemRevision(revisionID, authorName, authoredTimeStamp, committerName, committedTimeStamp, message);
        }
    }

    public class FileSystemFileFileModificationContext implements ProjectFileAccessProvider.FileModificationContext
    {
        private final String projectId;
        private final String revisionId;
        private final SourceSpecification sourceSpecification;

        public FileSystemFileFileModificationContext(String projectId, SourceSpecification sourceSpecification, String revisionId)
        {
            this.projectId = projectId;
            this.revisionId = revisionId;
            this.sourceSpecification = Objects.requireNonNull(sourceSpecification, "source specification may not be null");
        }

        @Override
        public Revision submit(String message, List<? extends ProjectFileOperation> operations)
        {
            String branchName = getRefBranchName(sourceSpecification);
            try
            {
                Repository repo = retrieveRepo(this.projectId);
                String referenceRevisionId = this.revisionId;
                Ref branch = getGitBranch(projectId, branchName);
                if (referenceRevisionId != null)
                {
                    String targetBranchRevision = FileSystemRevision.getFileSystemRevision(this.projectId, branchName, repo, branch).getId();
                    if (!referenceRevisionId.equals(targetBranchRevision))
                    {
                        String msg = "Expected " + sourceSpecification + " to be at revision " + referenceRevisionId + "; instead it was at revision " + targetBranchRevision;
                        LOGGER.info(msg);
                        throw new LegendSDLCServerException(msg, Response.Status.CONFLICT);
                    }
                }
                Git git = new Git(repo);
                git.checkout().setName(branchName).call();
                for (ProjectFileOperation fileOperation : operations)
                {
                    if (fileOperation instanceof ProjectFileOperation.AddFile)
                    {
                        File newFile = new File(repo.getDirectory().getParent(), fileOperation.getPath());
                        Files.createDirectories(newFile.toPath().getParent());
                        Files.write(newFile.toPath(), ((ProjectFileOperation.AddFile) fileOperation).getContent(), StandardOpenOption.CREATE_NEW);
                        git.add().addFilepattern(".").call();
                    }
                    else if (fileOperation instanceof ProjectFileOperation.ModifyFile)
                    {
                        File file = new File(repo.getDirectory().getParent(), fileOperation.getPath());
                        if (file.exists())
                        {
                            Files.write(file.toPath(), ((ProjectFileOperation.ModifyFile) fileOperation).getNewContent());
                        }
                        else
                        {
                            throw new LegendSDLCServerException("File " + file + " does not exist");
                        }
                        git.add().addFilepattern(".").call();
                    }
                    else if (fileOperation instanceof ProjectFileOperation.DeleteFile)
                    {
                        File fileToRemove = new File(repo.getWorkTree(), fileOperation.getPath().substring(1));
                        if (!fileToRemove.exists())
                        {
                            throw new LegendSDLCServerException("File " + fileToRemove + " does not exist");
                        }
                        fileToRemove.delete();
                        git.rm().addFilepattern(fileOperation.getPath().substring(1)).call();
                    }
                    else
                    {
                        throw new LegendSDLCServerException(fileOperation + "operation is not yet supported");
                    }
                }
                git.commit().setMessage(message).call();
                repo.close();
                return FileSystemRevision.getFileSystemRevision(projectId, branchName, repo, branch);
            }
            catch (Exception e)
            {
                throw FSException.getLegendSDLCServerException("Error occurred while committing changes to " + branchName + " of project " + projectId, e);
            }
        }
    }

    protected ProjectFileAccessProvider getProjectFileAccessProvider()
    {
        return new ProjectFileAccessProvider()
        {
            @Override
            public FileAccessContext getFileAccessContext(String projectId, SourceSpecification sourceSpecification, String revisionId)
            {
                return new FileSystemFileAccessContext(projectId, sourceSpecification, revisionId);
            }

            @Override
            public RevisionAccessContext getRevisionAccessContext(String projectId, SourceSpecification sourceSpecification, Iterable<? extends String> paths)
            {
                return new FileSystemRevisionAccessContext(projectId, sourceSpecification);
            }

            @Override
            public FileModificationContext getFileModificationContext(String projectId, SourceSpecification sourceSpecification, String revisionId)
            {
                return new FileSystemFileFileModificationContext(projectId, sourceSpecification, revisionId);
            }
        };
    }

}
