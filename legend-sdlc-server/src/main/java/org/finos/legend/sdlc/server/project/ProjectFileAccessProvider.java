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
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.tools.IOTools;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

public interface ProjectFileAccessProvider
{

    // File Access Context

    default FileAccessContext getProjectFileAccessContext(String projectId)
    {
        return getFileAccessContext(projectId, null, null, WorkspaceAccessType.WORKSPACE);
    }

    default FileAccessContext getProjectRevisionFileAccessContext(String projectId, String revisionId)
    {
        LegendSDLCServerException.validateNonNull(revisionId, "revisionId may not be null");
        return getFileAccessContext(projectId, null, revisionId, WorkspaceAccessType.WORKSPACE);
    }

    default FileAccessContext getWorkspaceFileAccessContext(String projectId, String workspaceId, WorkspaceAccessType workspaceAccessType)
    {
        LegendSDLCServerException.validateNonNull(workspaceId, "workspaceId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceAccessType, "workspaceAccessType may not be null");
        return getFileAccessContext(projectId, workspaceId, null, workspaceAccessType);
    }

    default FileAccessContext getWorkspaceRevisionFileAccessContext(String projectId, String workspaceId, String revisionId, WorkspaceAccessType workspaceAccessType)
    {
        LegendSDLCServerException.validateNonNull(workspaceId, "workspaceId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceAccessType, "workspaceAccessType may not be null");
        LegendSDLCServerException.validateNonNull(revisionId, "revisionId may not be null");
        return getFileAccessContext(projectId, workspaceId, revisionId, workspaceAccessType);
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
     * @param revisionId          revision id (optional)
     * @param workspaceAccessType access type for the workspace (e.g. conflict resolution, backup) (optional)
     * @return access context
     */
    FileAccessContext getFileAccessContext(String projectId, String workspaceId, String revisionId, WorkspaceAccessType workspaceAccessType);

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
            return getFilesInDirectory("/");
        }

        /**
         * Get all files in a directory as a stream. Note that this stream should be
         * closed when no longer needed. It is strongly recommended to use a
         * try-with-resources statement.
         *
         * @param directory directory path
         * @return stream of all files in the given directory
         */
        Stream<ProjectFile> getFilesInDirectory(String directory);

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
        return getRevisionAccessContext(projectId, (String) null, null, WorkspaceAccessType.WORKSPACE);
    }

    default RevisionAccessContext getProjectPathRevisionAccessContext(String projectId, String path)
    {
        LegendSDLCServerException.validateNonNull(path, "path may not be null");
        return getRevisionAccessContext(projectId, (String) null, path, WorkspaceAccessType.WORKSPACE);
    }

    default RevisionAccessContext getWorkspaceRevisionAccessContext(String projectId, String workspaceId, WorkspaceAccessType workspaceAccessType)
    {
        LegendSDLCServerException.validateNonNull(workspaceId, "workspaceId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceAccessType, "workspaceAccessType may not be null");
        return getRevisionAccessContext(projectId, workspaceId, null, workspaceAccessType);
    }

    default RevisionAccessContext getWorkspacePathRevisionAccessContext(String projectId, String workspaceId, String path, WorkspaceAccessType workspaceAccessType)
    {
        LegendSDLCServerException.validateNonNull(workspaceId, "workspaceId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceAccessType, "workspaceAccessType may not be null");
        LegendSDLCServerException.validateNonNull(path, "path may not be null");
        return getRevisionAccessContext(projectId, workspaceId, path, workspaceAccessType);
    }

    default RevisionAccessContext getVersionRevisionAccessContext(String projectId, VersionId versionId)
    {
        return getRevisionAccessContext(projectId, versionId, null);
    }

    default RevisionAccessContext getVersionPathRevisionAccessContext(String projectId, VersionId versionId, String path)
    {
        LegendSDLCServerException.validateNonNull(path, "path may not be null");
        return getRevisionAccessContext(projectId, versionId, path);
    }

    /**
     * Get a revision access context for a version of a project. Both the project id and
     * version id must be supplied, but path is optional.
     * <p>
     * If path is supplied, then the revision access context is for that path. The path can
     * be either a file or directory path. It should use the slash character ('/') to separate
     * directories, and it should start with a slash.
     *
     * @param projectId project id
     * @param versionId version id
     * @param path      file or directory path (optional)
     * @return revision access context
     */
    RevisionAccessContext getRevisionAccessContext(String projectId, VersionId versionId, String path);

    /**
     * Get a revision access context. The project id must always be supplied, but workspace id
     * and path are optional. If workspace Id is specified, we must also specify a workspace
     * access type.
     * <p>
     * If a workspace id is supplied, then the revision access context is for that workspace.
     * Otherwise, it is for the trunk or master branch of the project.
     * <p>
     * If path is supplied, then the revision access context is for that path. The path can
     * be either a file or directory path. It should use the slash character ('/') to separate
     * directories, and it should start with a slash.
     *
     * @param projectId           project id
     * @param workspaceId         workspace id (optional)
     * @param path                file or directory path (optional)
     * @param workspaceAccessType access type for the workspace (e.g. conflict resolution, backup)
     * @return revision access context
     */
    RevisionAccessContext getRevisionAccessContext(String projectId, String workspaceId, String path, WorkspaceAccessType workspaceAccessType);

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
        return getFileModificationContext(projectId, null, null, WorkspaceAccessType.WORKSPACE);
    }

    default FileModificationContext getProjectFileModificationContext(String projectId, String revisionId)
    {
        LegendSDLCServerException.validateNonNull(revisionId, "revisionId may not be null");
        return getFileModificationContext(projectId, null, revisionId, WorkspaceAccessType.WORKSPACE);
    }

    default FileModificationContext getWorkspaceFileModificationContext(String projectId, String workspaceId, WorkspaceAccessType workspaceAccessType)
    {
        LegendSDLCServerException.validateNonNull(workspaceId, "workspaceId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceAccessType, "workspaceAccessType may not be null");
        return getFileModificationContext(projectId, workspaceId, null, workspaceAccessType);
    }

    default FileModificationContext getWorkspaceFileModificationContext(String projectId, String workspaceId, String revisionId, WorkspaceAccessType workspaceAccessType)
    {
        LegendSDLCServerException.validateNonNull(workspaceId, "workspaceId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceAccessType, "workspaceAccessType may not be null");
        LegendSDLCServerException.validateNonNull(revisionId, "revisionId may not be null");
        return getFileModificationContext(projectId, workspaceId, revisionId, workspaceAccessType);
    }

    /**
     * Get a modification context. The project id must always be supplied, but workspace
     * and revision ids are optional.
     * <p>
     * If a workspace id is supplied, then the modification context is for that workspace.
     * Otherwise, it is for the trunk or master branch of the project. Note that it is
     * generally advisable to perform modifications within a workspace.
     * <p>
     * If a revision id is supplied, it is used to validate the current revision of the
     * project or workspace before making any modifications.
     *
     * @param projectId           project id
     * @param workspaceId         workspace id (optional)
     * @param revisionId          revision id (optional, for validation)
     * @param workspaceAccessType access type for the workspace (e.g. conflict resolution, backup)
     * @return modification context
     */
    FileModificationContext getFileModificationContext(String projectId, String workspaceId, String revisionId, WorkspaceAccessType workspaceAccessType);

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
