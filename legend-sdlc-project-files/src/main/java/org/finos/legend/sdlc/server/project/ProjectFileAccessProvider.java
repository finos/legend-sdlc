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

package org.finos.legend.sdlc.server.project;

import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification;
import org.finos.legend.sdlc.server.tools.IOTools;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

public interface ProjectFileAccessProvider
{
    // File Access Context

    interface FileAccessContext
    {
        /**
         * Get all files as a stream. Note that this stream should be closed when
         * no longer needed. It is strongly recommended to use a try-with-resources
         * statement.
         *
         * @return stream of all files
         */
        default Stream<ProjectFile> getFiles()
        {
            return getFilesInDirectory(ProjectPaths.ROOT_DIRECTORY);
        }

        /**
         * Get all files in a directory as a stream. Note that this stream should be
         * closed when no longer needed. It is strongly recommended to use a
         * try-with-resources statement.
         *
         * @param directory directory path
         * @return stream of all files in the given directory
         */
        default Stream<ProjectFile> getFilesInDirectory(String directory)
        {
            return getFilesInDirectories(Collections.singletonList(directory));
        }

        /**
         * Get all files in the directories as a stream. No file should appear multiple
         * times in the stream, even if a directory appears more than once in the input.
         * Note that this stream should be closed when no longer needed. It is strongly
         * recommended to use a try-with-resources statement.
         *
         * @param directories directory paths
         * @return stream of all files in the given directories
         */
        Stream<ProjectFile> getFilesInDirectories(Stream<? extends String> directories);

        /**
         * Get all files in the directories as a stream. No file should appear multiple
         * times in the stream, even if a directory appears more than once in the input.
         * Note that this stream should be closed when no longer needed. It is strongly
         * recommended to use a try-with-resources statement.
         *
         * @param directories directory paths
         * @return stream of all files in the given directories
         */
        Stream<ProjectFile> getFilesInDirectories(Iterable<? extends String> directories);

        /**
         * Get a single file. Returns null if the file does not exist.
         *
         * @param path file path
         * @return file or null, if there is no such file
         */
        ProjectFile getFile(String path);

        /**
         * Return whether a file exists.
         *
         * @param path file path
         * @return whether the file exists
         */
        default boolean fileExists(String path)
        {
            return getFile(path) != null;
        }
    }

    /**
     * A project file, comprising a path and content. The content can be accessed as
     * an InputStream, a Reader, a byte array, or a String. Accessing as a Reader or
     * String is only appropriate in the case of text files. By default, UTF-8 is
     * used when converting between bytes and characters. Note that different
     * implementations may differ in which methods of accessing the content are more
     * or less efficient.
     */
    interface ProjectFile
    {
        /**
         * Path of the file with the project. The slash character ('/') is used to
         * separate directories within the path. Paths will always begin with a
         * slash, and will never be empty.
         *
         * @return file path relative to project root
         */
        String getPath();

        /**
         * Get the content of the file as an InputStream.
         *
         * @return content as InputStream
         */
        InputStream getContentAsInputStream();

        /**
         * Get the content of the file as a Reader. This is only appropriate for
         * text files.
         *
         * @return content as Reader
         */
        default Reader getContentAsReader()
        {
            return new InputStreamReader(getContentAsInputStream(), StandardCharsets.UTF_8);
        }

        /**
         * Get the content of the file as a byte array.
         *
         * @return content as byte array
         */
        default byte[] getContentAsBytes()
        {
            try
            {
                return IOTools.readAllBytes(getContentAsInputStream());
            }
            catch (IOException e)
            {
                throw new UncheckedIOException(e);
            }
        }

        /**
         * Get the content of the file as a String. This is only appropriate for
         * text files.
         *
         * @return content as String
         */
        default String getContentAsString()
        {
            try
            {
                return IOTools.readAllToString(getContentAsInputStream(), StandardCharsets.UTF_8);
            }
            catch (IOException e)
            {
                throw new UncheckedIOException(e);
            }
        }
    }

    /**
     * Get a file access context. The project id and source specification must always be supplied, but revision id is
     * optional.
     * <p>
     * If a revision id is supplied, then the access context is for that particular revision. Otherwise, the access
     * context is for the current state. Note that as the current state may change over time, calls to the access
     * context may yield different results over time.
     *
     * @param projectId           project id
     * @param sourceSpecification source specification
     * @param revisionId          revision id (optional)
     * @return access context
     */
    FileAccessContext getFileAccessContext(String projectId, SourceSpecification sourceSpecification, String revisionId);

    /**
     * Get a file access context for the current revision. Note that as the current state may change over time, calls to
     * the access context may yield different results over time.
     *
     * @param projectId           project id
     * @param sourceSpecification source specification
     * @return access context
     */
    default FileAccessContext getFileAccessContext(String projectId, SourceSpecification sourceSpecification)
    {
        return getFileAccessContext(projectId, sourceSpecification, null);
    }

    // Revision Access Context

    interface RevisionAccessContext
    {
        Revision getBaseRevision();

        Revision getCurrentRevision();

        Revision getRevision(String revisionId);

        Stream<Revision> getAllRevisions(Predicate<? super Revision> predicate, Instant since, Instant until, Integer limit);
    }

    /**
     * Get a revision access context. The project id and source specification must always be supplied, but the paths are
     * optional. If paths are supplied, then the revision access context is for those paths. The paths can be either
     * file or directory paths. They should use the slash character ('/') to separate directories, and should all start
     * with a slash.
     *
     * @param projectId           project id
     * @param sourceSpecification source specification
     * @param paths               file or directory paths (optional)
     * @return revision access context
     */
    RevisionAccessContext getRevisionAccessContext(String projectId, SourceSpecification sourceSpecification, Iterable<? extends String> paths);

    /**
     * Get a revision access context.
     *
     * @param projectId           project id
     * @param sourceSpecification source specification
     * @return revision access context
     */
    default RevisionAccessContext getRevisionAccessContext(String projectId, SourceSpecification sourceSpecification)
    {
        return getRevisionAccessContext(projectId, sourceSpecification, null);
    }

    // File Modification Context

    interface FileModificationContext
    {
        Revision submit(String message, List<? extends ProjectFileOperation> operations);
    }

    /**
     * Get a modification context. The project id and source specification must always be supplied, but revision id is
     * optional. If a revision id is supplied, it is used to validate the current revision before making any
     * modifications. Note that not all source specifications need be supported for modification.
     *
     * @param projectId           project id
     * @param sourceSpecification source specification
     * @param revisionId          revision id (optional, for validation)
     * @return modification context
     * @throws UnsupportedOperationException if the source specification is not supported for modification
     */
    FileModificationContext getFileModificationContext(String projectId, SourceSpecification sourceSpecification, String revisionId);

    /**
     * Get a modification context.
     *
     * @param projectId           project id
     * @param sourceSpecification source specification
     * @return modification context
     * @throws UnsupportedOperationException if the source specification is not supported for modification
     */
    default FileModificationContext getFileModificationContext(String projectId, SourceSpecification sourceSpecification)
    {
        return getFileModificationContext(projectId, sourceSpecification, null);
    }

    enum WorkspaceAccessType
    {
        WORKSPACE("workspace", "workspaces"),
        CONFLICT_RESOLUTION("workspace with conflict resolution", "workspaces with conflict resolution"),
        BACKUP("backup workspace", "backup workspaces");

        private final String label;
        private final String labelPlural;

        WorkspaceAccessType(final String label, final String labelPlural)
        {
            this.label = label;
            this.labelPlural = labelPlural;
        }

        public String getLabel()
        {
            return this.label;
        }

        public String getLabelPlural()
        {
            return this.labelPlural;
        }
    }
}
