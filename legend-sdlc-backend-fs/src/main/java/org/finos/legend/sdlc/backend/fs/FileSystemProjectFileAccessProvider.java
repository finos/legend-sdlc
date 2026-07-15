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

package org.finos.legend.sdlc.backend.fs;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.utility.Iterate;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.revision.RevisionAlias;
import org.finos.legend.sdlc.error.LegendSDLCException;
import org.finos.legend.sdlc.project.files.AbstractFileAccessContext;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider;
import org.finos.legend.sdlc.project.files.ProjectFileOperation;
import org.finos.legend.sdlc.project.files.ProjectFiles;
import org.finos.legend.sdlc.project.files.ProjectPaths;
import org.finos.legend.sdlc.project.source.PatchSourceSpecification;
import org.finos.legend.sdlc.project.source.ProjectSourceSpecification;
import org.finos.legend.sdlc.project.source.SourceSpecification;
import org.finos.legend.sdlc.project.source.SourceSpecificationVisitor;
import org.finos.legend.sdlc.project.source.VersionSourceSpecification;
import org.finos.legend.sdlc.project.source.WorkspaceSourceSpecification;
import org.finos.legend.sdlc.project.workspace.WorkspaceSpecification;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * The file-system storage provider: every project is a git repository in a directory named by the project id
 * under a common root; sources are branches ({@code master} for the project source, per-user workspace
 * branches); revisions are commits. The provider is session-scoped — workspace specifications with a null user
 * id resolve against the session user it is created with.
 */
public class FileSystemProjectFileAccessProvider implements ProjectFileAccessProvider
{
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemProjectFileAccessProvider.class);

    /**
     * Branch-name user segment when the session has no user id — the fixed user of the pre-SPI file-system
     * server, so anonymous deployments keep addressing workspaces created before the refit.
     */
    static final String DEFAULT_USER_ID = "local_user";

    static final String MASTER_BRANCH = "master";
    private static final String WORKSPACE_BRANCH_PREFIX = "workspace";
    private static final String CONFLICT_RESOLUTION_BRANCH_PREFIX = "resolution";
    private static final String BACKUP_BRANCH_PREFIX = "backup";
    private static final char BRANCH_DELIMITER = '/';

    private final String rootDirectory;
    private final String userId;

    FileSystemProjectFileAccessProvider(String rootDirectory, String userId)
    {
        this.rootDirectory = Objects.requireNonNull(rootDirectory, "rootDirectory may not be null");
        this.userId = (userId == null) ? DEFAULT_USER_ID : userId;
    }

    @Override
    public FileAccessContext getFileAccessContext(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
        return new FileSystemFileAccessContext(projectId, sourceSpecification, revisionId);
    }

    @Override
    public RevisionAccessContext getRevisionAccessContext(String projectId, SourceSpecification sourceSpecification, Iterable<? extends String> paths)
    {
        return new FileSystemRevisionAccessContext(projectId, sourceSpecification, paths);
    }

    @Override
    public FileModificationContext getFileModificationContext(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
        return new FileSystemFileModificationContext(projectId, sourceSpecification, revisionId);
    }

    String getRootDirectory()
    {
        return this.rootDirectory;
    }

    String getUserId()
    {
        return this.userId;
    }

    // Repository access

    /**
     * Open the project's repository, or return null if no repository exists for the id.
     */
    Repository findRepository(String projectId)
    {
        File repoDir = new File(this.rootDirectory + File.separator + projectId + File.separator + Constants.DOT_GIT);
        if (!repoDir.exists() || !repoDir.isDirectory())
        {
            return null;
        }
        try
        {
            return FileRepositoryBuilder.create(repoDir);
        }
        catch (Exception e)
        {
            throw wrapException("Error accessing project " + projectId, e);
        }
    }

    Repository getRepository(String projectId)
    {
        Repository repository = findRepository(projectId);
        if (repository == null)
        {
            throw new LegendSDLCException("Unknown project: " + projectId, 404);
        }
        return repository;
    }

    /**
     * Initialize a project repository: an empty repository with an initial commit, plus the project metadata in
     * its configuration. This is the storage half of project creation; the backend's project api calls it and
     * then builds the project structure through the generic updater.
     */
    Repository initRepository(String projectId, String name, String description)
    {
        String projectPath = this.rootDirectory + File.separator + projectId;
        try
        {
            Repository repository = FileRepositoryBuilder.create(new File(projectPath, Constants.DOT_GIT));
            repository.create();
            try (Git git = new Git(repository))
            {
                git.commit().setMessage("Initial Commit").call();
            }
            repository.getConfig().setString("project", null, "id", projectId);
            repository.getConfig().setString("project", null, "name", name);
            if (description != null)
            {
                repository.getConfig().setString("project", null, "description", description);
            }
            repository.getConfig().save();
            return repository;
        }
        catch (Exception e)
        {
            throw wrapException("Failed to create project: " + projectId, e);
        }
    }

    // Branch naming

    String getBranchName(SourceSpecification sourceSpecification)
    {
        return sourceSpecification.visit(new SourceSpecificationVisitor<String>()
        {
            @Override
            public String visit(ProjectSourceSpecification sourceSpec)
            {
                return MASTER_BRANCH;
            }

            @Override
            public String visit(WorkspaceSourceSpecification sourceSpec)
            {
                return getWorkspaceBranchName(sourceSpec.getWorkspaceSpecification());
            }

            @Override
            public String visit(VersionSourceSpecification sourceSpec)
            {
                throw new LegendSDLCException("Version sources are not supported by the file-system backend", 501);
            }

            @Override
            public String visit(PatchSourceSpecification sourceSpec)
            {
                throw new LegendSDLCException("Patch sources are not supported by the file-system backend", 501);
            }
        });
    }

    String getWorkspaceBranchName(WorkspaceSpecification workspaceSpecification)
    {
        if (workspaceSpecification.getType() != WorkspaceType.USER)
        {
            throw new LegendSDLCException("Group workspaces are not supported by the file-system backend", 501);
        }
        StringBuilder builder = new StringBuilder(getWorkspaceBranchNamePrefix(workspaceSpecification.getAccessType())).append(BRANCH_DELIMITER);
        String workspaceUserId = workspaceSpecification.getUserId();
        builder.append((workspaceUserId == null) ? this.userId : workspaceUserId).append(BRANCH_DELIMITER);
        return builder.append(workspaceSpecification.getId()).toString();
    }

    String getWorkspaceBranchPrefix(String workspaceUserId)
    {
        return WORKSPACE_BRANCH_PREFIX + BRANCH_DELIMITER + ((workspaceUserId == null) ? this.userId : workspaceUserId) + BRANCH_DELIMITER;
    }

    private static String getWorkspaceBranchNamePrefix(WorkspaceAccessType accessType)
    {
        switch ((accessType == null) ? WorkspaceAccessType.WORKSPACE : accessType)
        {
            case WORKSPACE:
            {
                return WORKSPACE_BRANCH_PREFIX;
            }
            case CONFLICT_RESOLUTION:
            {
                return CONFLICT_RESOLUTION_BRANCH_PREFIX;
            }
            case BACKUP:
            {
                return BACKUP_BRANCH_PREFIX;
            }
            default:
            {
                throw new LegendSDLCException("Unknown workspace access type: " + accessType, 400);
            }
        }
    }

    // Revision helpers

    private static String toTreePath(String path)
    {
        String canonical = path.endsWith(ProjectPaths.PATH_SEPARATOR) ? ProjectPaths.canonicalizeDirectory(path) : ProjectPaths.canonicalizeFile(path);
        int endIndex = canonical.endsWith(ProjectPaths.PATH_SEPARATOR) ? (canonical.length() - 1) : canonical.length();
        return canonical.substring(1, endIndex);
    }

    static Revision toRevision(RevCommit commit)
    {
        return new FileSystemRevision(commit.getId().getName(),
                commit.getAuthorIdent().getName(), commit.getAuthorIdent().getWhenAsInstant(),
                commit.getCommitterIdent().getName(), commit.getCommitterIdent().getWhenAsInstant(),
                commit.getFullMessage());
    }

    static LegendSDLCException wrapException(String errorMessage, Exception e)
    {
        if (e instanceof LegendSDLCException)
        {
            // a deliberately-statused exception (e.g. a 404 or a 409) passes through unchanged
            return (LegendSDLCException) e;
        }
        String exceptionMessage = e.getMessage();
        return new LegendSDLCException((exceptionMessage == null) ? errorMessage : (errorMessage + " : " + exceptionMessage), e);
    }

    private RevCommit resolveBranchTip(Repository repo, String projectId, String branchName)
    {
        try
        {
            ObjectId branchTip = repo.resolve(branchName);
            if (branchTip == null)
            {
                throw new LegendSDLCException("Unknown branch " + branchName + " in project " + projectId, 404);
            }
            return repo.parseCommit(branchTip);
        }
        catch (IOException e)
        {
            throw wrapException("Error resolving branch " + branchName + " in project " + projectId, e);
        }
    }

    private static RevCommit getMergeBase(Repository repo, RevCommit left, RevCommit right) throws IOException
    {
        try (RevWalk walk = new RevWalk(repo))
        {
            walk.setRevFilter(RevFilter.MERGE_BASE);
            walk.markStart(walk.parseCommit(left));
            walk.markStart(walk.parseCommit(right));
            return walk.next();
        }
    }

    private static RevCommit getFirstCommit(Repository repo, RevCommit tip) throws IOException
    {
        try (RevWalk walk = new RevWalk(repo))
        {
            walk.markStart(walk.parseCommit(tip));
            walk.sort(RevSort.REVERSE);
            return walk.next();
        }
    }

    // File access

    private class FileSystemFileAccessContext extends AbstractFileAccessContext
    {
        private final String projectId;
        private final SourceSpecification sourceSpecification;
        private final String revisionId;

        private FileSystemFileAccessContext(String projectId, SourceSpecification sourceSpecification, String revisionId)
        {
            this.projectId = projectId;
            this.sourceSpecification = sourceSpecification;
            this.revisionId = revisionId;
        }

        @Override
        protected Stream<ProjectFile> getFilesInCanonicalDirectories(MutableList<String> directories)
        {
            MutableList<ProjectFile> files = Lists.mutable.empty();
            try
            {
                Repository repo = getRepository(this.projectId);
                RevCommit commit = resolveContextCommit(repo);
                boolean allFiles = directories.contains(ProjectPaths.ROOT_DIRECTORY);
                try (TreeWalk treeWalk = new TreeWalk(repo))
                {
                    treeWalk.addTree(commit.getTree());
                    treeWalk.setRecursive(true);
                    while (treeWalk.next())
                    {
                        // git tree paths have no leading separator; canonical paths do
                        String canonicalPath = ProjectPaths.PATH_SEPARATOR + treeWalk.getPathString();
                        if (allFiles || directories.anySatisfy(canonicalPath::startsWith))
                        {
                            byte[] fileBytes = repo.open(treeWalk.getObjectId(0)).getBytes();
                            files.add(ProjectFiles.newByteArrayProjectFile(canonicalPath, fileBytes));
                        }
                    }
                }
            }
            catch (Exception e)
            {
                throw wrapException("Error getting files in directories for " + this.projectId, e);
            }
            return files.stream();
        }

        @Override
        public ProjectFile getFile(String path)
        {
            try
            {
                Repository repo = getRepository(this.projectId);
                RevCommit commit = resolveContextCommit(repo);
                String treePath = path.startsWith(ProjectPaths.PATH_SEPARATOR) ? path.substring(1) : path;
                try (TreeWalk treeWalk = TreeWalk.forPath(repo, treePath, commit.getTree()))
                {
                    if (treeWalk != null)
                    {
                        byte[] fileBytes = repo.open(treeWalk.getObjectId(0)).getBytes();
                        return ProjectFiles.newByteArrayProjectFile(path, fileBytes);
                    }
                }
            }
            catch (Exception e)
            {
                throw wrapException("Error getting file " + path, e);
            }
            return null;
        }

        @Override
        public boolean fileExists(String path)
        {
            try
            {
                Repository repo = getRepository(this.projectId);
                RevCommit commit = resolveContextCommit(repo);
                String treePath = path.startsWith(ProjectPaths.PATH_SEPARATOR) ? path.substring(1) : path;
                try (TreeWalk treeWalk = TreeWalk.forPath(repo, treePath, commit.getTree()))
                {
                    return treeWalk != null;
                }
            }
            catch (Exception e)
            {
                throw wrapException("Error checking existence of file " + path, e);
            }
        }

        /**
         * The commit this context reads from: the context's revision id if it has one, otherwise the current
         * tip of the branch its source specification designates.
         */
        private RevCommit resolveContextCommit(Repository repo) throws IOException
        {
            if (this.revisionId == null)
            {
                return resolveBranchTip(repo, this.projectId, getBranchName(this.sourceSpecification));
            }
            return repo.parseCommit(ObjectId.fromString(this.revisionId));
        }
    }

    // Revision access

    private class FileSystemRevisionAccessContext implements RevisionAccessContext
    {
        private final String projectId;
        private final SourceSpecification sourceSpecification;
        private final MutableList<String> treePaths;

        private FileSystemRevisionAccessContext(String projectId, SourceSpecification sourceSpecification, Iterable<? extends String> paths)
        {
            this.projectId = projectId;
            this.sourceSpecification = Objects.requireNonNull(sourceSpecification, "source specification may not be null");
            // a path is a directory scope if it ends with the separator, otherwise a file scope (the convention
            // of ProjectPaths canonical forms); either way, git addresses it without the surrounding separators
            this.treePaths = (paths == null) ? null : Iterate.collect(paths, FileSystemProjectFileAccessProvider::toTreePath, Lists.mutable.empty());
        }

        @Override
        public Revision getCurrentRevision()
        {
            Repository repo = getRepository(this.projectId);
            String branchName = getBranchName(this.sourceSpecification);
            try
            {
                RevCommit tip = resolveBranchTip(repo, this.projectId, branchName);
                if (this.treePaths == null)
                {
                    return toRevision(tip);
                }
                return scopedLog(repo, tip).map(FileSystemProjectFileAccessProvider::toRevision).findFirst().orElse(null);
            }
            catch (Exception e)
            {
                throw wrapException("Failed to get current revision for branch " + branchName + " in project " + this.projectId, e);
            }
        }

        @Override
        public Revision getBaseRevision()
        {
            Repository repo = getRepository(this.projectId);
            String branchName = getBranchName(this.sourceSpecification);
            try
            {
                RevCommit tip = resolveBranchTip(repo, this.projectId, branchName);
                if (MASTER_BRANCH.equals(branchName))
                {
                    return toRevision(getFirstCommit(repo, tip));
                }
                RevCommit masterTip = resolveBranchTip(repo, this.projectId, MASTER_BRANCH);
                RevCommit base = getMergeBase(repo, tip, masterTip);
                return (base == null) ? null : toRevision(base);
            }
            catch (Exception e)
            {
                throw wrapException("Failed to get base revision for branch " + branchName + " in project " + this.projectId, e);
            }
        }

        @Override
        public Revision getRevision(String revisionId)
        {
            LegendSDLCException.validateNonNull(revisionId, "revisionId may not be null", 400);
            String resolvedRevisionId = resolveRevisionAlias(revisionId);
            if (resolvedRevisionId == null)
            {
                throw new LegendSDLCException("Failed to resolve revision " + revisionId + " of project " + this.projectId, 404);
            }
            Repository repo = getRepository(this.projectId);
            try
            {
                ObjectId commitId;
                try
                {
                    commitId = ObjectId.fromString(resolvedRevisionId);
                }
                catch (IllegalArgumentException e)
                {
                    throw new LegendSDLCException("Unknown revision " + resolvedRevisionId + " of project " + this.projectId, 404);
                }
                RevCommit commit;
                try (RevWalk walk = new RevWalk(repo))
                {
                    commit = walk.parseCommit(commitId);
                }
                if ((this.treePaths != null) && scopedLog(repo, resolveBranchTip(repo, this.projectId, getBranchName(this.sourceSpecification))).noneMatch(c -> c.getId().equals(commit.getId())))
                {
                    return null;
                }
                return toRevision(commit);
            }
            catch (MissingObjectException e)
            {
                throw new LegendSDLCException("Unknown revision " + resolvedRevisionId + " of project " + this.projectId, 404);
            }
            catch (Exception e)
            {
                throw wrapException("Failed to get revision " + resolvedRevisionId + " of project " + this.projectId, e);
            }
        }

        @Override
        public Stream<Revision> getAllRevisions(Predicate<? super Revision> predicate, Instant since, Instant until, Integer limit)
        {
            Repository repo = getRepository(this.projectId);
            String branchName = getBranchName(this.sourceSpecification);
            try
            {
                RevCommit tip = resolveBranchTip(repo, this.projectId, branchName);
                Stream<Revision> stream = scopedLog(repo, tip).map(FileSystemProjectFileAccessProvider::toRevision);
                if (since != null)
                {
                    stream = stream.filter(r -> !r.getCommittedTimestamp().isBefore(since));
                }
                if (until != null)
                {
                    stream = stream.filter(r -> !r.getCommittedTimestamp().isAfter(until));
                }
                if (predicate != null)
                {
                    stream = stream.filter(predicate);
                }
                if (limit != null)
                {
                    stream = stream.limit(limit);
                }
                return stream;
            }
            catch (Exception e)
            {
                throw wrapException("Failed to get revisions for branch " + branchName + " in project " + this.projectId, e);
            }
        }

        /**
         * The commits reachable from the given tip that touch this context's scope paths (all commits when the
         * context is unscoped), newest first.
         */
        private Stream<RevCommit> scopedLog(Repository repo, RevCommit tip) throws Exception
        {
            LogCommand log = Git.wrap(repo).log().add(tip);
            if (this.treePaths != null)
            {
                this.treePaths.forEach(log::addPath);
            }
            return StreamSupport.stream(log.call().spliterator(), false);
        }

        private String resolveRevisionAlias(String revisionId)
        {
            RevisionAlias alias = getRevisionAlias(revisionId);
            switch (alias)
            {
                case BASE:
                {
                    Revision revision = getBaseRevision();
                    return (revision == null) ? null : revision.getId();
                }
                case HEAD:
                {
                    Revision revision = getCurrentRevision();
                    return (revision == null) ? null : revision.getId();
                }
                case REVISION_ID:
                {
                    return revisionId;
                }
                default:
                {
                    throw new LegendSDLCException("Unknown revision alias type: " + alias, 400);
                }
            }
        }

        private RevisionAlias getRevisionAlias(String revisionId)
        {
            if (RevisionAlias.BASE.getValue().equalsIgnoreCase(revisionId))
            {
                return RevisionAlias.BASE;
            }
            if (RevisionAlias.HEAD.getValue().equalsIgnoreCase(revisionId) || RevisionAlias.CURRENT.getValue().equalsIgnoreCase(revisionId) || RevisionAlias.LATEST.getValue().equalsIgnoreCase(revisionId))
            {
                return RevisionAlias.HEAD;
            }
            return RevisionAlias.REVISION_ID;
        }
    }

    // File modification

    private class FileSystemFileModificationContext implements FileModificationContext
    {
        private final String projectId;
        private final SourceSpecification sourceSpecification;
        private final String revisionId;

        private FileSystemFileModificationContext(String projectId, SourceSpecification sourceSpecification, String revisionId)
        {
            this.projectId = projectId;
            this.sourceSpecification = Objects.requireNonNull(sourceSpecification, "source specification may not be null");
            this.revisionId = revisionId;
        }

        @Override
        public Revision submit(String message, List<? extends ProjectFileOperation> operations)
        {
            String branchName = getBranchName(this.sourceSpecification);
            try
            {
                Repository repo = getRepository(this.projectId);
                if (this.revisionId != null)
                {
                    String targetBranchRevision = resolveBranchTip(repo, this.projectId, branchName).getId().getName();
                    if (!this.revisionId.equals(targetBranchRevision))
                    {
                        String msg = "Expected " + this.sourceSpecification + " to be at revision " + this.revisionId + "; instead it was at revision " + targetBranchRevision;
                        LOGGER.info(msg);
                        throw new LegendSDLCException(msg, 409);
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
                        if (!file.exists())
                        {
                            throw new LegendSDLCException("File " + file + " does not exist");
                        }
                        Files.write(file.toPath(), ((ProjectFileOperation.ModifyFile) fileOperation).getNewContent());
                        git.add().addFilepattern(".").call();
                    }
                    else if (fileOperation instanceof ProjectFileOperation.DeleteFile)
                    {
                        File fileToRemove = new File(repo.getWorkTree(), fileOperation.getPath().substring(1));
                        if (!fileToRemove.exists())
                        {
                            throw new LegendSDLCException("File " + fileToRemove + " does not exist");
                        }
                        fileToRemove.delete();
                        git.rm().addFilepattern(fileOperation.getPath().substring(1)).call();
                    }
                    else
                    {
                        throw new LegendSDLCException(fileOperation + " operation is not supported");
                    }
                }
                if (git.status().call().isClean())
                {
                    // nothing actually changed (e.g. modifications with identical content): no revision —
                    // jgit would otherwise happily create an empty commit
                    repo.close();
                    return null;
                }
                // return the commit just made, not a stale branch snapshot
                RevCommit newCommit = git.commit().setMessage(message).call();
                repo.close();
                return toRevision(newCommit);
            }
            catch (Exception e)
            {
                throw wrapException("Error occurred while committing changes to " + branchName + " of project " + this.projectId, e);
            }
        }
    }
}
