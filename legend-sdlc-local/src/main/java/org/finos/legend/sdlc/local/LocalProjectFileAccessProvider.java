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

import org.eclipse.collections.api.list.MutableList;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.error.LegendSDLCException;
import org.finos.legend.sdlc.project.files.AbstractFileAccessContext;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider;
import org.finos.legend.sdlc.project.files.ProjectFileOperation;
import org.finos.legend.sdlc.project.files.ProjectFiles;
import org.finos.legend.sdlc.project.files.ProjectPaths;
import org.finos.legend.sdlc.project.source.ProjectSourceSpecification;
import org.finos.legend.sdlc.project.source.SourceSpecification;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * The local working-copy storage provider (re-architecture section 4.2): file access and modification over a
 * directory tree, plugged in at the storage SPI — which is exactly why everything the SDLC core offers over that SPI
 * works on a local checkout. This is not a backend in the backend-SPI sense: there are no projects to enumerate, no
 * workspaces, and no history. It presents a single project — the directory it is constructed with — through the
 * project source only; source specifications for workspaces, versions, or patches are rejected with
 * {@link UnsupportedOperationException}.
 *
 * <p><b>Revisions.</b> A working copy has no revision history, and this provider deliberately does not pretend
 * otherwise (no JGit; see the re-architecture worklog, Phase 6). What it has is exactly one addressable state — the
 * working tree, now — exposed as the synthetic revision {@link #WORKING_COPY_REVISION_ID}: the revision access
 * context reports it as the current (and only) revision, and file access/modification contexts accept it (or null)
 * as a revision id. This keeps the generic write-side working unmodified — the SDLC core uses the revision context
 * as a reference probe, not as history. Unlike a true revision, the working copy is mutable: two reads of the same
 * "revision" may differ if the working tree changed in between. Time-window arguments to revision enumeration are
 * ignored (the single state is unstamped).
 *
 * <p><b>Modification.</b> Operations are validated against the working tree (add: must not exist; modify/delete:
 * must exist; move: source must exist, target must not), then applied in order, writing directly to disk.
 * Directories left empty by a delete or move are removed. There is no atomicity guarantee: an I/O failure
 * mid-application leaves the already-applied operations on disk — the working copy is the caller's tree, and undo
 * is the caller's (typically the IDE's or version control's) concern.
 *
 * <p><b>Reads are live.</b> A file access context reflects the working tree at the moment of each call, and file
 * content is read when accessed, not when listed.
 *
 * <p>This class is safe to instantiate many times, including over the same directory. Individual contexts are not
 * thread-safe; callers serialize.
 */
public class LocalProjectFileAccessProvider implements ProjectFileAccessProvider
{
    /**
     * The id of the synthetic revision representing the current state of the working tree.
     */
    public static final String WORKING_COPY_REVISION_ID = "working-copy";

    private static final Revision WORKING_COPY_REVISION = new WorkingCopyRevision();

    private final Path root;
    private final String projectId;

    /**
     * Create a provider over a directory tree.
     *
     * @param root      directory holding the project (the model root)
     * @param projectId id the single local project is addressed by
     */
    public LocalProjectFileAccessProvider(Path root, String projectId)
    {
        this.root = Objects.requireNonNull(root, "root may not be null");
        this.projectId = Objects.requireNonNull(projectId, "projectId may not be null");
        if (!Files.isDirectory(root))
        {
            throw new IllegalArgumentException("Not a directory: " + root);
        }
    }

    public Path getRoot()
    {
        return this.root;
    }

    public String getProjectId()
    {
        return this.projectId;
    }

    @Override
    public FileAccessContext getFileAccessContext(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
        validateProjectId(projectId);
        validateSourceSpecification(sourceSpecification);
        validateRevisionId(revisionId);
        return new LocalFileAccessContext(this.root);
    }

    @Override
    public RevisionAccessContext getRevisionAccessContext(String projectId, SourceSpecification sourceSpecification, Iterable<? extends String> paths)
    {
        validateProjectId(projectId);
        validateSourceSpecification(sourceSpecification);
        // paths are ignored: the single working-copy state is the history of every path
        return new WorkingCopyRevisionAccessContext();
    }

    @Override
    public FileModificationContext getFileModificationContext(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
        validateProjectId(projectId);
        validateSourceSpecification(sourceSpecification);
        validateRevisionId(revisionId);
        return new LocalFileModificationContext(this.root);
    }

    private void validateProjectId(String projectId)
    {
        if (!this.projectId.equals(projectId))
        {
            throw new IllegalArgumentException("Unknown project id: \"" + projectId + "\" (this provider holds project \"" + this.projectId + "\")");
        }
    }

    private void validateSourceSpecification(SourceSpecification sourceSpecification)
    {
        Objects.requireNonNull(sourceSpecification, "source specification may not be null");
        if (!(sourceSpecification instanceof ProjectSourceSpecification))
        {
            throw new UnsupportedOperationException("Local working-copy storage has only the project source; workspaces, versions, and patches do not exist locally (got: " + sourceSpecification + ")");
        }
    }

    private void validateRevisionId(String revisionId)
    {
        if ((revisionId != null) && !WORKING_COPY_REVISION_ID.equals(revisionId))
        {
            throw new IllegalArgumentException("Unknown revision id: \"" + revisionId + "\" (local working-copy storage has only the \"" + WORKING_COPY_REVISION_ID + "\" revision)");
        }
    }

    // Path mapping

    static Path resolveProjectPath(Path root, String projectPath)
    {
        String canonical = ProjectPaths.canonicalizeFile(projectPath);
        Path result = root;
        int start = 1;
        int length = canonical.length();
        while (start < length)
        {
            int end = canonical.indexOf('/', start);
            if (end == -1)
            {
                end = length;
            }
            String element = canonical.substring(start, end);
            if (element.isEmpty() || ".".equals(element) || "..".equals(element))
            {
                throw new IllegalArgumentException("Invalid project path: \"" + projectPath + "\"");
            }
            result = result.resolve(element);
            start = end + 1;
        }
        if (root.equals(result))
        {
            throw new IllegalArgumentException("Invalid project path: \"" + projectPath + "\"");
        }
        return result;
    }

    private static String toProjectPath(Path root, Path file)
    {
        StringBuilder builder = new StringBuilder();
        for (Path element : root.relativize(file))
        {
            builder.append(ProjectPaths.PATH_SEPARATOR).append(element.getFileName());
        }
        return builder.toString();
    }

    // File access

    private static class LocalFileAccessContext extends AbstractFileAccessContext
    {
        private final Path root;

        private LocalFileAccessContext(Path root)
        {
            this.root = root;
        }

        @Override
        protected Stream<ProjectFile> getFilesInCanonicalDirectories(MutableList<String> directories)
        {
            List<ProjectFile> files = new ArrayList<>();
            for (String directory : directories)
            {
                Path directoryPath = ProjectPaths.ROOT_DIRECTORY.equals(directory) ? this.root : resolveProjectPath(this.root, directory.substring(0, directory.length() - 1));
                if (Files.isDirectory(directoryPath))
                {
                    try
                    {
                        Files.walkFileTree(directoryPath, new SimpleFileVisitor<Path>()
                        {
                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            {
                                if (attrs.isRegularFile())
                                {
                                    files.add(newProjectFile(toProjectPath(LocalFileAccessContext.this.root, file), file));
                                }
                                return FileVisitResult.CONTINUE;
                            }
                        });
                    }
                    catch (IOException e)
                    {
                        throw new UncheckedIOException("Error listing files in " + directoryPath, e);
                    }
                }
            }
            return files.stream();
        }

        @Override
        public ProjectFile getFile(String path)
        {
            Path filePath = resolveProjectPath(this.root, path);
            return Files.isRegularFile(filePath) ? newProjectFile(ProjectPaths.canonicalizeFile(path), filePath) : null;
        }

        @Override
        public boolean fileExists(String path)
        {
            return Files.isRegularFile(resolveProjectPath(this.root, path));
        }

        private static ProjectFile newProjectFile(String projectPath, Path filePath)
        {
            return ProjectFiles.newByteArrayProjectFile(projectPath, p ->
            {
                try
                {
                    return Files.readAllBytes(filePath);
                }
                catch (IOException e)
                {
                    throw new UncheckedIOException("Error reading " + filePath, e);
                }
            });
        }
    }

    // File modification

    private static class LocalFileModificationContext implements FileModificationContext
    {
        private final Path root;

        private LocalFileModificationContext(Path root)
        {
            this.root = root;
        }

        @Override
        public Revision submit(String message, List<? extends ProjectFileOperation> operations)
        {
            operations.forEach(this::validateOperation);
            for (ProjectFileOperation operation : operations)
            {
                try
                {
                    applyOperation(operation);
                }
                catch (IOException e)
                {
                    throw new UncheckedIOException("Error applying " + operation, e);
                }
            }
            return WORKING_COPY_REVISION;
        }

        private void validateOperation(ProjectFileOperation operation)
        {
            if (operation instanceof ProjectFileOperation.AddFile)
            {
                validateAbsent(operation.getPath());
            }
            else if (operation instanceof ProjectFileOperation.ModifyFile)
            {
                validatePresent(operation.getPath());
            }
            else if (operation instanceof ProjectFileOperation.DeleteFile)
            {
                validatePresent(operation.getPath());
            }
            else if (operation instanceof ProjectFileOperation.MoveFile)
            {
                validatePresent(operation.getPath());
                validateAbsent(((ProjectFileOperation.MoveFile) operation).getNewPath());
            }
            else
            {
                throw new IllegalArgumentException("Unknown file operation type: " + operation);
            }
        }

        private void validatePresent(String path)
        {
            if (!Files.isRegularFile(resolveProjectPath(this.root, path)))
            {
                throw new LegendSDLCException("File does not exist: " + path, 404);
            }
        }

        private void validateAbsent(String path)
        {
            if (Files.exists(resolveProjectPath(this.root, path)))
            {
                throw new LegendSDLCException("File already exists: " + path, 409);
            }
        }

        private void applyOperation(ProjectFileOperation operation) throws IOException
        {
            if (operation instanceof ProjectFileOperation.AddFile)
            {
                Path target = resolveProjectPath(this.root, operation.getPath());
                Files.createDirectories(target.getParent());
                Files.write(target, ((ProjectFileOperation.AddFile) operation).getContent());
            }
            else if (operation instanceof ProjectFileOperation.ModifyFile)
            {
                Files.write(resolveProjectPath(this.root, operation.getPath()), ((ProjectFileOperation.ModifyFile) operation).getNewContent());
            }
            else if (operation instanceof ProjectFileOperation.DeleteFile)
            {
                Path target = resolveProjectPath(this.root, operation.getPath());
                Files.delete(target);
                pruneEmptyDirectories(target.getParent());
            }
            else if (operation instanceof ProjectFileOperation.MoveFile)
            {
                ProjectFileOperation.MoveFile moveFile = (ProjectFileOperation.MoveFile) operation;
                Path source = resolveProjectPath(this.root, moveFile.getPath());
                Path target = resolveProjectPath(this.root, moveFile.getNewPath());
                Files.createDirectories(target.getParent());
                if (moveFile.getNewContent() == null)
                {
                    Files.move(source, target);
                }
                else
                {
                    Files.write(target, moveFile.getNewContent());
                    Files.delete(source);
                }
                pruneEmptyDirectories(source.getParent());
            }
        }

        private void pruneEmptyDirectories(Path directory) throws IOException
        {
            Path current = directory;
            while (!this.root.equals(current) && isEmptyDirectory(current))
            {
                Files.delete(current);
                current = current.getParent();
            }
        }

        private static boolean isEmptyDirectory(Path directory) throws IOException
        {
            if (!Files.isDirectory(directory))
            {
                return false;
            }
            try (Stream<Path> entries = Files.list(directory))
            {
                return !entries.findAny().isPresent();
            }
        }
    }

    // Revisions

    private static class WorkingCopyRevisionAccessContext implements RevisionAccessContext
    {
        @Override
        public Revision getBaseRevision()
        {
            return WORKING_COPY_REVISION;
        }

        @Override
        public Revision getCurrentRevision()
        {
            return WORKING_COPY_REVISION;
        }

        @Override
        public Revision getRevision(String revisionId)
        {
            return WORKING_COPY_REVISION_ID.equals(revisionId) ? WORKING_COPY_REVISION : null;
        }

        @Override
        public Stream<Revision> getAllRevisions(Predicate<? super Revision> predicate, Instant since, Instant until, Integer limit)
        {
            if (((limit != null) && (limit < 1)) || ((predicate != null) && !predicate.test(WORKING_COPY_REVISION)))
            {
                return Stream.empty();
            }
            // since/until are ignored: the single working-copy state is unstamped
            return Stream.of(WORKING_COPY_REVISION);
        }
    }

    private static class WorkingCopyRevision implements Revision
    {
        @Override
        public String getId()
        {
            return WORKING_COPY_REVISION_ID;
        }

        @Override
        public String getAuthorName()
        {
            return null;
        }

        @Override
        public Instant getAuthoredTimestamp()
        {
            return null;
        }

        @Override
        public String getCommitterName()
        {
            return null;
        }

        @Override
        public Instant getCommittedTimestamp()
        {
            return null;
        }

        @Override
        public String getMessage()
        {
            return "Local working copy";
        }

        @Override
        public String toString()
        {
            return "<Revision " + WORKING_COPY_REVISION_ID + ">";
        }
    }
}
