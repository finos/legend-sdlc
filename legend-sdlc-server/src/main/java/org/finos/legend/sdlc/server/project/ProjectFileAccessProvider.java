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

package org.finos.legend.sdlc.server.project;

import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.server.domain.api.project.SourceSpecification;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.tools.IOTools;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

public interface ProjectFileAccessProvider
{
    // File Access Context

    default FileAccessContext getProjectFileAccessContext(String projectId)
    {
        return getFileAccessContext(projectId, null, WorkspaceType.USER, WorkspaceAccessType.WORKSPACE, null);
    }

    default FileAccessContext getProjectRevisionFileAccessContext(String projectId, String revisionId)
    {
        LegendSDLCServerException.validateNonNull(revisionId, "revisionId may not be null");
        return getFileAccessContext(projectId, null, WorkspaceType.USER, WorkspaceAccessType.WORKSPACE, revisionId);
    }

    default FileAccessContext getWorkspaceFileAccessContext(String projectId, String workspaceId, WorkspaceType workspaceType, WorkspaceAccessType workspaceAccessType)
    {
        LegendSDLCServerException.validateNonNull(workspaceId, "workspaceId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceType, "workspaceType may not be null");
        LegendSDLCServerException.validateNonNull(workspaceAccessType, "workspaceAccessType may not be null");
        return getFileAccessContext(projectId, workspaceId, workspaceType, workspaceAccessType, null);
    }

    default FileAccessContext getWorkspaceRevisionFileAccessContext(String projectId, String workspaceId, WorkspaceType workspaceType, WorkspaceAccessType workspaceAccessType, String revisionId)
    {
        LegendSDLCServerException.validateNonNull(workspaceId, "workspaceId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceAccessType, "workspaceAccessType may not be null");
        LegendSDLCServerException.validateNonNull(revisionId, "revisionId may not be null");
        return getFileAccessContext(projectId, workspaceId, workspaceType, workspaceAccessType, revisionId);
    }

    // for backward compatibility
    @Deprecated
    default FileAccessContext getFileAccessContext(String projectId, String workspaceId, WorkspaceAccessType workspaceAccessType, String revisionId)
    {
        return getFileAccessContext(projectId, workspaceId, WorkspaceType.USER, workspaceAccessType, revisionId);
    }

    /**
     * Get a file access context. The project id must always be supplied, but workspace
     * and revision ids are optional. If workspace is specified, workspace access type is also required
     * <p>
     * If a workspace id is supplied, then the access context is for that workspace.
     * Otherwise, it is for the trunk or master branch of the project.
     * <p>
     * If a revision id is supplied, then the access context is for that particular
     * revision of the project or workspace. Otherwise, the access context is for
     * the current state of the project or workspace. Note that as the current
     * state may change over time, calls to the access context may yield different
     * results over time.
     *
     * @param projectId           project id
     * @param workspaceId         workspace id (optional)
     * @param workspaceType       type for the workspace (e.g. user, group) (optional)
     * @param workspaceAccessType access type for the workspace (e.g. conflict resolution, backup) (optional)
     * @param revisionId          revision id (optional)
     * @return access context
     */
     default FileAccessContext getFileAccessContext(String projectId, String workspaceId, WorkspaceType workspaceType, WorkspaceAccessType workspaceAccessType, String revisionId)
     {
         return getFileAccessContext(projectId, SourceSpecification.newSourceSpecification(workspaceId, workspaceType, workspaceAccessType), revisionId);
     }

    /**
     * Get a file access context. The project id must always be supplied, but workspace
     * and revision ids are optional. If workspace is specified, workspace access type is also required
     * <p>
     * If a workspace id is supplied, then the access context is for that workspace.
     * Otherwise, it is for the trunk or master branch of the project.
     * <p>
     * If a revision id is supplied, then the access context is for that particular
     * revision of the project or workspace. Otherwise, the access context is for
     * the current state of the project or workspace. Note that as the current
     * state may change over time, calls to the access context may yield different
     * results over time.
     *
     * @param projectId           project id
     * @param sourceSpecification source specification
     * @param revisionId          revision id (optional)
     * @return access context
     */
    FileAccessContext getFileAccessContext(String projectId, SourceSpecification sourceSpecification, String revisionId);

    /**
     * Get a file access context for a version of a project. Both the project and
     * version ids must be supplied.
     *
     * @param projectId project id
     * @param versionId version id
     * @return access context
     */
    FileAccessContext getFileAccessContext(String projectId, VersionId versionId);

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
                throw new RuntimeException(e);
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
                throw new RuntimeException(e);
            }
        }
    }

    // Revision Access Context

    default RevisionAccessContext getProjectRevisionAccessContext(String projectId)
    {
        return getRevisionAccessContext(projectId, null, WorkspaceType.USER, WorkspaceAccessType.WORKSPACE, (String) null);
    }

    default RevisionAccessContext getProjectPathRevisionAccessContext(String projectId, String path)
    {
        LegendSDLCServerException.validateNonNull(path, "path may not be null");
        return getRevisionAccessContext(projectId, null, WorkspaceType.USER, WorkspaceAccessType.WORKSPACE, path);
    }

    default RevisionAccessContext getWorkspaceRevisionAccessContext(String projectId, String workspaceId, WorkspaceType workspaceType, WorkspaceAccessType workspaceAccessType)
    {
        LegendSDLCServerException.validateNonNull(workspaceId, "workspaceId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceType, "workspaceType may not be null");
        LegendSDLCServerException.validateNonNull(workspaceAccessType, "workspaceAccessType may not be null");
        return getRevisionAccessContext(projectId, workspaceId, workspaceType, workspaceAccessType, (String) null);
    }

    default RevisionAccessContext getWorkspaceRevisionAccessContext(String projectId, SourceSpecification sourceSpecification)
    {
        LegendSDLCServerException.validateNonNull(sourceSpecification.getWorkspaceId(), "workspaceId may not be null");
        LegendSDLCServerException.validateNonNull(sourceSpecification.getWorkspaceType(), "workspaceType may not be null");
        LegendSDLCServerException.validateNonNull(sourceSpecification.getWorkspaceAccessType(), "workspaceAccessType may not be null");
        return getRevisionAccessContext(projectId, sourceSpecification, null);
    }

    default RevisionAccessContext getWorkspacePathRevisionAccessContext(String projectId, String workspaceId, WorkspaceType workspaceType, WorkspaceAccessType workspaceAccessType, String path)
    {
        LegendSDLCServerException.validateNonNull(workspaceId, "workspaceId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceType, "workspaceType may not be null");
        LegendSDLCServerException.validateNonNull(workspaceAccessType, "workspaceAccessType may not be null");
        LegendSDLCServerException.validateNonNull(path, "path may not be null");
        return getRevisionAccessContext(projectId, workspaceId, workspaceType, workspaceAccessType, path);
    }

    default RevisionAccessContext getWorkspacePathsRevisionAccessContext(String projectId, String workspaceId, WorkspaceType workspaceType, WorkspaceAccessType workspaceAccessType, Iterable<? extends String> paths)
    {
        LegendSDLCServerException.validateNonNull(workspaceId, "workspaceId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceAccessType, "workspaceAccessType may not be null");
        LegendSDLCServerException.validateNonNull(paths, "paths may not be null");
        return getRevisionAccessContext(projectId, SourceSpecification.newSourceSpecification(workspaceId, workspaceType, workspaceAccessType), paths);
    }

    default RevisionAccessContext getVersionRevisionAccessContext(String projectId, VersionId versionId)
    {
        return getRevisionAccessContext(projectId, versionId);
    }

    default RevisionAccessContext getVersionPathRevisionAccessContext(String projectId, VersionId versionId, String path)
    {
        LegendSDLCServerException.validateNonNull(path, "path may not be null");
        return getRevisionAccessContext(projectId, versionId, path);
    }

    default RevisionAccessContext getVersionPathRevisionAccessContext(String projectId, VersionId versionId, Iterable<? extends String> paths)
    {
        LegendSDLCServerException.validateNonNull(paths, "paths may not be null");
        return getRevisionAccessContext(projectId, versionId, paths);
    }

    /**
     * Get a revision access context for a version of a project. Both the project id and
     * version id must be supplied.
     *
     * @param projectId project id
     * @param versionId version id
     * @return revision access context
     */
    default RevisionAccessContext getRevisionAccessContext(String projectId, VersionId versionId)
    {
        return getRevisionAccessContext(projectId, versionId, (Iterable<String>) null);
    }

    /**
     * Get a revision access context for a version of a project. Both the project id and version id must be supplied,
     * but path is optional.
     * <p>
     * If path is supplied, then the revision access context is for that path. The path can be either a file or
     * directory path. It should use the slash character ('/') to separate directories, and it should start with a
     * slash.
     *
     * @param projectId project id
     * @param versionId version id
     * @param path      file or directory path (optional)
     * @return revision access context
     */
    default RevisionAccessContext getRevisionAccessContext(String projectId, VersionId versionId, String path)
    {
        return getRevisionAccessContext(projectId, versionId, (path == null) ? null : Collections.singleton(path));
    }

    /**
     * Get a revision access context for a version of a project. Both the project id and version id must be supplied,
     * but paths is optional.
     * <p>
     * If paths is supplied, then the revision access context is for that set of paths. The paths can be either file or
     * directory paths. They should use the slash character ('/') to separate directories, and should all start with a
     * slash.
     *
     * @param projectId project id
     * @param versionId version id
     * @param paths     file or directory paths (optional)
     * @return revision access context
     */
    RevisionAccessContext getRevisionAccessContext(String projectId, VersionId versionId, Iterable<? extends String> paths);

    /**
     * Get a revision access context. The project id must always be supplied, but workspace id is optional. If
     * workspace id is specified, workspace access type must also be specified.
     * <p>
     * If a workspace id is supplied, then the revision access context is for that workspace. Otherwise, it is for the
     * trunk or master branch of the project.
     *
     * @param projectId           project id
     * @param workspaceId         workspace id (optional)
     * @param workspaceType       type for the workspace (e.g. user, group)
     * @param workspaceAccessType access type for the workspace (e.g. conflict resolution, backup)
     * @return revision access context
     */
    default RevisionAccessContext getRevisionAccessContext(String projectId, String workspaceId, WorkspaceType workspaceType, WorkspaceAccessType workspaceAccessType)
    {
        return getRevisionAccessContext(projectId, SourceSpecification.newSourceSpecification(workspaceId, workspaceType, workspaceAccessType), (Iterable<String>) null);
    }

    /**
     * Get a revision access context. The project id must always be supplied, but workspace id and path are optional.
     * If workspace id is specified, workspace access type must also be specified.
     * <p>
     * If a workspace id is supplied, then the revision access context is for that workspace. Otherwise, it is for the
     * trunk or master branch of the project.
     * <p>
     * If path is supplied, then the revision access context is for that path. The path can be either a file or
     * directory path. It should use the slash character ('/') to separate directories, and it should start with a
     * slash.
     *
     * @param projectId           project id
     * @param workspaceId         workspace id (optional)
     * @param workspaceType       type for the workspace (e.g. user, group)
     * @param workspaceAccessType access type for the workspace (e.g. conflict resolution, backup)
     * @param path                file or directory path (optional)
     * @return revision access context
     */
    default RevisionAccessContext getRevisionAccessContext(String projectId, String workspaceId, WorkspaceType workspaceType, WorkspaceAccessType workspaceAccessType, String path)
    {
        return getRevisionAccessContext(projectId, SourceSpecification.newSourceSpecification(workspaceId, workspaceType, workspaceAccessType), (path == null) ? null : Collections.singleton(path));
    }

    /**
     * Get a revision access context. The project id must always be supplied, but workspace id and paths are optional.
     * If workspace id is specified, workspace access type must also be specified.
     * <p>
     * If a workspace id is supplied, then the revision access context is for that workspace. Otherwise, it is for the
     * trunk or master branch of the project.
     * <p>
     * If paths is supplied, then the revision access context is for those paths. The paths can be either file or
     * directory paths. They should use the slash character ('/') to separate directories, and should all start with a
     * slash.
     *
     * @param projectId              project id
     * @param sourceSpecification source specification
     * @param paths                  file or directory paths (optional)
     * @return revision access context
     */
    RevisionAccessContext getRevisionAccessContext(String projectId, SourceSpecification sourceSpecification, Iterable<? extends String> paths);

    interface RevisionAccessContext
    {
        Revision getBaseRevision();

        Revision getCurrentRevision();

        Revision getRevision(String revisionId);

        Stream<Revision> getAllRevisions(Predicate<? super Revision> predicate, Instant since, Instant until, Integer limit);
    }

    // File Modification Context

    default FileModificationContext getProjectFileModificationContext(String projectId)
    {
        return getFileModificationContext(projectId, null, WorkspaceType.USER, WorkspaceAccessType.WORKSPACE, null);
    }

    default FileModificationContext getProjectFileModificationContext(String projectId, String revisionId)
    {
        LegendSDLCServerException.validateNonNull(revisionId, "revisionId may not be null");
        return getFileModificationContext(projectId, null, WorkspaceType.USER, WorkspaceAccessType.WORKSPACE, revisionId);
    }

    default FileModificationContext getWorkspaceFileModificationContext(String projectId, String workspaceId, WorkspaceType workspaceType, WorkspaceAccessType workspaceAccessType)
    {
        LegendSDLCServerException.validateNonNull(workspaceId, "workspaceId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceType, "workspaceType may not be null");
        LegendSDLCServerException.validateNonNull(workspaceAccessType, "workspaceAccessType may not be null");
        return getFileModificationContext(projectId, workspaceId, workspaceType, workspaceAccessType, null);
    }

    default FileModificationContext getWorkspaceFileModificationContext(String projectId, String workspaceId, WorkspaceType workspaceType, WorkspaceAccessType workspaceAccessType, String revisionId)
    {
        LegendSDLCServerException.validateNonNull(workspaceId, "workspaceId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceType, "workspaceType may not be null");
        LegendSDLCServerException.validateNonNull(workspaceAccessType, "workspaceAccessType may not be null");
        LegendSDLCServerException.validateNonNull(revisionId, "revisionId may not be null");
        return getFileModificationContext(projectId, workspaceId, workspaceType, workspaceAccessType, revisionId);
    }

    // for backward compatibility
    @Deprecated
    default FileModificationContext getFileModificationContext(String projectId, String workspaceId, WorkspaceAccessType workspaceAccessType, String revisionId)
    {
        return getFileModificationContext(projectId, workspaceId, WorkspaceType.USER, workspaceAccessType, revisionId);
    }

    /**
     * Get a modification context. The project id must always be supplied, but workspace and revision ids are optional.
     * If workspace id is specified, workspace access type must also be specified.
     * <p>
     * If a workspace id is supplied, then the modification context is for that workspace. Otherwise, it is for the
     * trunk or master branch of the project. Note that it is generally advisable to perform modifications within a
     * workspace.
     * <p>
     * If a revision id is supplied, it is used to validate the current revision of the project or workspace before
     * making any modifications.
     *
     * @param projectId           project id
     * @param sourceSpecification source specification
     * @param revisionId          revision id (optional, for validation)
     * @return modification context
     */
    FileModificationContext getFileModificationContext(String projectId, SourceSpecification sourceSpecification, String revisionId);

    default FileModificationContext getFileModificationContext(String projectId, String workspaceId, WorkspaceType workspaceType, WorkspaceAccessType workspaceAccessType, String revisionId)
    {
        return this.getFileModificationContext(projectId, SourceSpecification.newSourceSpecification(workspaceId, workspaceType, workspaceAccessType), revisionId);
    }

    interface FileModificationContext
    {
        Revision submit(String message, List<? extends ProjectFileOperation> operations);
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
