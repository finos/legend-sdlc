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

package org.finos.legend.sdlc.server.gitlab.api;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.set.MutableSet;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.revision.RevisionAlias;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.domain.api.project.SourceSpecification;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.GitLabConfiguration;
import org.finos.legend.sdlc.server.gitlab.GitLabProjectId;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.gitlab.tools.GitLabApiTools;
import org.finos.legend.sdlc.server.gitlab.tools.PagerTools;
import org.finos.legend.sdlc.server.project.AbstractFileAccessContext;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider.FileModificationContext;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider.RevisionAccessContext;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider.WorkspaceAccessType;
import org.finos.legend.sdlc.server.project.ProjectFileOperation;
import org.finos.legend.sdlc.server.project.ProjectFiles;
import org.finos.legend.sdlc.server.project.ProjectPaths;
import org.finos.legend.sdlc.server.project.ProjectStructure;
import org.finos.legend.sdlc.server.tools.BackgroundTaskProcessor;
import org.finos.legend.sdlc.server.tools.IOTools;
import org.finos.legend.sdlc.server.tools.StringTools;
import org.gitlab4j.api.CommitsApi;
import org.gitlab4j.api.Constants;
import org.gitlab4j.api.Constants.Encoding;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.Pager;
import org.gitlab4j.api.RepositoryApi;
import org.gitlab4j.api.RepositoryFileApi;
import org.gitlab4j.api.models.Branch;
import org.gitlab4j.api.models.Commit;
import org.gitlab4j.api.models.CommitAction;
import org.gitlab4j.api.models.CommitAction.Action;
import org.gitlab4j.api.models.CommitRef;
import org.gitlab4j.api.models.CommitRef.RefType;
import org.gitlab4j.api.models.Pipeline;
import org.gitlab4j.api.models.PipelineStatus;
import org.gitlab4j.api.models.RepositoryFile;
import org.gitlab4j.api.models.Tag;
import org.gitlab4j.api.models.TreeItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.Response.Status.Family;

abstract class GitLabApiWithFileAccess extends BaseGitLabApi
{
    private static final Logger LOGGER = LoggerFactory.getLogger(GitLabApiWithFileAccess.class);

    private static final int MAX_COMMIT_SIZE = 512;
    private static final int MAX_COMMIT_RETRIES = 10;

    private final BackgroundTaskProcessor backgroundTaskProcessor;

    protected GitLabApiWithFileAccess(GitLabConfiguration gitLabConfiguration, GitLabUserContext userContext, BackgroundTaskProcessor backgroundTaskProcessor)
    {
        super(gitLabConfiguration, userContext);
        this.backgroundTaskProcessor = backgroundTaskProcessor;
    }

    protected ProjectConfiguration getProjectConfiguration(String projectId, WorkspaceInfo workspaceInfo, String revisionId)
    {
        return getProjectConfiguration(projectId, SourceSpecification.newSourceSpecification(workspaceInfo.getWorkspaceId(), workspaceInfo.getWorkspaceType(), workspaceInfo.getWorkspaceAccessType()), revisionId);
    }

    protected ProjectConfiguration getProjectConfiguration(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
       ProjectConfiguration config = ProjectStructure.getProjectConfiguration(projectId, sourceSpecification, revisionId, getProjectFileAccessProvider());
        if (config == null)
        {
            config = ProjectStructure.getDefaultProjectConfiguration(projectId);
        }
        return config;
    }

    protected ProjectConfiguration getProjectConfiguration(String projectId, VersionId patchReleaseVersionId)
    {
        return ProjectStructure.getProjectConfiguration(projectId, SourceSpecification.newSourceSpecification(patchReleaseVersionId), null, getProjectFileAccessProvider());
    }

    protected ProjectConfiguration getVersionProjectConfiguration(String projectId, VersionId versionId)
    {
        ProjectConfiguration config = ProjectStructure.getProjectConfiguration(projectId, versionId, getProjectFileAccessProvider());
        if (config == null)
        {
            config = ProjectStructure.getDefaultProjectConfiguration(projectId);
        }
        return config;
    }

    protected ProjectStructure getProjectStructure(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
        return ProjectStructure.getProjectStructure(projectId, sourceSpecification, revisionId, getProjectFileAccessProvider());
    }

    protected ProjectFileAccessProvider getProjectFileAccessProvider()
    {
        return new GitLabProjectFileAccessProvider();
    }

    private String getCurrentRevisionId(GitLabProjectId projectId, SourceSpecification sourceSpecification)
    {
        Revision revision = new GitLabRevisionAccessContext(projectId, sourceSpecification, null).getCurrentRevision();
        return (revision == null) ? null : revision.getId();
    }

    private class GitLabProjectFileAccessProvider implements ProjectFileAccessProvider
    {
        // File Access Access Context

        @Override
        public FileAccessContext getFileAccessContext(String projectId, String workspaceId, WorkspaceType workspaceType, WorkspaceAccessType workspaceAccessType, String revisionId)
        {
            return new GitLabProjectFileAccessContext(parseProjectId(projectId), SourceSpecification.newSourceSpecification(workspaceId, workspaceType, workspaceAccessType), revisionId);
        }

        @Override
        public FileAccessContext getFileAccessContext(String projectId, SourceSpecification sourceSpecification, String revisionId)
        {
            return new GitLabProjectFileAccessContext(parseProjectId(projectId), sourceSpecification, revisionId);
        }

        @Override
        public FileAccessContext getFileAccessContext(String projectId, VersionId versionId)
        {
            return new GitLabProjectVersionFileAccessContext(parseProjectId(projectId), versionId);
        }

        // Revision Access Context

        @Override
        public RevisionAccessContext getRevisionAccessContext(String projectId, SourceSpecification sourceSpecification, Iterable<? extends String> paths)
        {
            return new GitLabRevisionAccessContext(parseProjectId(projectId), sourceSpecification, paths);
        }

        @Override
        public RevisionAccessContext getRevisionAccessContext(String projectId, VersionId versionId, Iterable<? extends String> paths)
        {
            return new GitLabProjectVersionRevisionAccessContext(parseProjectId(projectId), versionId, paths);
        }

        // File Modification Context

        @Override
        public FileModificationContext getFileModificationContext(String projectId, SourceSpecification sourceSpecification, String revisionId)
        {
            return new GitLabProjectFileFileModificationContext(parseProjectId(projectId), sourceSpecification, revisionId);
        }
    }

    private abstract class AbstractGitLabFileAccessContext extends AbstractFileAccessContext
    {
        protected final GitLabProjectId projectId;

        AbstractGitLabFileAccessContext(GitLabProjectId projectId)
        {
            this.projectId = projectId;
        }

        @Override
        protected Stream<ProjectFileAccessProvider.ProjectFile> getFilesInCanonicalDirectories(MutableList<String> directories)
        {
            Exception exception;
            try
            {
                return getFilesFromRepoArchive(directories);
            }
            catch (Exception e)
            {
                exception = e;
            }

            if (exception instanceof GitLabApiException)
            {
                int statusCode = ((GitLabApiException) exception).getHttpStatus();
                if ((statusCode == Status.NOT_FOUND.getStatusCode()) && "404 File Not Found".equals(exception.getMessage()))
                {
                    // This means the repository is empty
                    return Stream.empty();
                }
                if ((statusCode == Status.TOO_MANY_REQUESTS.getStatusCode()) || (statusCode == Status.NOT_ACCEPTABLE.getStatusCode()))
                {
                    LOGGER.warn("Failed to get files for {} from repository archive (http status: {}), will try to get them from tree(s)", getReference(), statusCode, exception);
                    try
                    {
                        return getFilesFromTrees(directories);
                    }
                    catch (Exception e)
                    {
                        try
                        {
                            e.addSuppressed(exception);
                        }
                        catch (Exception ignore)
                        {
                            // ignore failure to add suppressed exception
                        }
                        exception = e;
                    }
                }
            }
            throw buildException(exception,
                    () -> "User " + getCurrentUser() + " is not allowed to access files for " + getDescriptionForExceptionMessage(),
                    () -> "Unknown " + getDescriptionForExceptionMessage(),
                    () -> "Failed to access files for " + getDescriptionForExceptionMessage());
        }

        private Stream<ProjectFileAccessProvider.ProjectFile> getFilesFromRepoArchive(MutableList<String> directories) throws GitLabApiException, IOException
        {
            InputStream inStream = null;
            ArchiveInputStream archiveInputStream = null;
            try
            {
                String referenceId = getReference();
                RepositoryApi repositoryApi = getGitLabApi().getRepositoryApi();
                inStream = withRetries(() -> repositoryApi.getRepositoryArchive(this.projectId.getGitLabId(), referenceId));
                archiveInputStream = new TarArchiveInputStream(new GzipCompressorInputStream(inStream));
                Stream<ProjectFileAccessProvider.ProjectFile> stream = IOTools.streamCloseableSpliterator(new ArchiveStreamProjectFileSpliterator(archiveInputStream), false);
                if (directories.size() == 1)
                {
                    String directory = directories.get(0);
                    if (!ProjectPaths.ROOT_DIRECTORY.equals(directory))
                    {
                        stream = stream.filter(f -> f.getPath().startsWith(directory));
                    }
                }
                else
                {
                    stream = stream.filter(f ->
                    {
                        String path = f.getPath();
                        return directories.anySatisfy(path::startsWith);
                    });
                }
                return stream;
            }
            catch (Exception e)
            {
                if (archiveInputStream != null)
                {
                    try
                    {
                        archiveInputStream.close();
                    }
                    catch (Exception ignore)
                    {
                        // ignore this
                    }
                }
                if (inStream != null)
                {
                    try
                    {
                        inStream.close();
                    }
                    catch (Exception ignore)
                    {
                        // ignore this
                    }
                }
                throw e;
            }
        }

        private Stream<ProjectFileAccessProvider.ProjectFile> getFilesFromTrees(List<String> directories) throws GitLabApiException
        {
            String referenceId = getReference();
            RepositoryApi repositoryApi = getGitLabApi().getRepositoryApi();
            MutableList<Pager<TreeItem>> pagers = Lists.mutable.ofInitialCapacity(directories.size());
            for (String directory : directories)
            {
                String filePath = ProjectPaths.ROOT_DIRECTORY.equals(directory) ? null : directory.substring(1);
                Pager<TreeItem> pager;
                try
                {
                    pager = withRetries(() -> repositoryApi.getTree(this.projectId.getGitLabId(), filePath, referenceId, true, ITEMS_PER_PAGE));
                }
                catch (GitLabApiException e)
                {
                    if (GitLabApiTools.isNotFoundGitLabApiException(e) && "404 Tree Not Found".equals(e.getMessage()))
                    {
                        // The directory doesn't exist, no need to add a pager
                        pager = null;
                    }
                    else
                    {
                        throw e;
                    }
                }
                if (pager != null)
                {
                    pagers.add(pager);
                }
            }
            if (pagers.isEmpty())
            {
                return Stream.empty();
            }
            return pagers.stream()
                    .flatMap(PagerTools::stream)
                    .filter(ti -> ti.getType() == TreeItem.Type.BLOB)
                    .map(TreeItem::getPath)
                    .map(p -> p.startsWith("/") ? p : ("/" + p))
                    .map(path -> ProjectFiles.newDelegatingProjectFile(path, this::getFile));
        }

        @Override
        public ProjectFileAccessProvider.ProjectFile getFile(String path)
        {
            String referenceId = getReference();
            try
            {
                RepositoryFileApi repositoryFileApi = getGitLabApi().getRepositoryFileApi();
                String gitLabFilePath = toGitLabFilePath(path);
                RepositoryFile file = withRetries(() -> repositoryFileApi.getFile(this.projectId.getGitLabId(), gitLabFilePath, referenceId, true));
                Encoding encoding = file.getEncoding();
                if (encoding == null)
                {
                    throw new RuntimeException("Unknown encoding: null");
                }
                switch (encoding)
                {
                    case TEXT:
                    {
                        return ProjectFiles.newStringProjectFile(path, file.getContent());
                    }
                    case BASE64:
                    {
                        byte[] content = Base64.getDecoder().decode(file.getContent().getBytes(StandardCharsets.ISO_8859_1));
                        return ProjectFiles.newByteArrayProjectFile(path, content);
                    }
                    default:
                    {
                        throw new RuntimeException("Unknown encoding: " + encoding);
                    }
                }
            }
            catch (Exception e)
            {
                if (GitLabApiTools.isNotFoundGitLabApiException(e))
                {
                    return null;
                }
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to access file " + path + " for " + getDescriptionForExceptionMessage(),
                        () -> "Unknown file " + path + " for " + getDescriptionForExceptionMessage(),
                        () -> "Error getting file " + path + " for " + getDescriptionForExceptionMessage());
            }
        }

        @Override
        public boolean fileExists(String path)
        {
            String referenceId = getReference();
            try
            {
                RepositoryFileApi repositoryFileApi = getGitLabApi().getRepositoryFileApi();
                String gitLabFilePath = toGitLabFilePath(path);
                RepositoryFile file = withRetries(() -> repositoryFileApi.getFile(this.projectId.getGitLabId(), gitLabFilePath, referenceId, false));
                return file != null;
            }
            catch (Exception e)
            {
                if (GitLabApiTools.isNotFoundGitLabApiException(e))
                {
                    return false;
                }
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to access file " + path + " for " + getDescriptionForExceptionMessage(),
                        () -> "Unknown file " + path + " for " + getDescriptionForExceptionMessage(),
                        () -> "Error getting file " + path + " for " + getDescriptionForExceptionMessage());
            }
        }

        protected abstract String getReference();

        protected abstract String getDescriptionForExceptionMessage();
    }

    // NOTE: this file access context takes both workspaceId, and revisionId, which can make it look like when both
    // are provided, we will find revision in a particular workspace, but that's not the case.
    //
    // In case revisionId is provided, the final reference to be used will be the revisionId, and Gitlab API allows us to
    // access files of this commit regardless of the branch it belongs to. In particular, it can find commits whose branch
    // has been squashed, merged into master and deleted after a merge request. In this case the commit surely won;t appear
    // on master but Gitlab still got a hold of it.
    //
    // This is important because here we are leaving the semantic layer of the app and entering file access level in gitlab,
    // where we just need the reference, SHA to get files and underneath, we use a really powerful API to get the files via repository archive,
    // so we are allowed to access files of a commit that belongs to a deleted branch (in merge request for example)
    private class GitLabProjectFileAccessContext extends AbstractGitLabFileAccessContext
    {
        private final String revisionId;
        private final SourceSpecification sourceSpecification;

        private GitLabProjectFileAccessContext(GitLabProjectId projectId, SourceSpecification sourceSpecification, String revisionId)
        {
            super(projectId);
            this.sourceSpecification = sourceSpecification;
            this.revisionId = revisionId;
            if (sourceSpecification == null)
            {
                throw new RuntimeException("source specification may not be null");
            }
            if ((this.sourceSpecification.getWorkspaceId() != null) && ((this.sourceSpecification.getWorkspaceType() == null) || (this.sourceSpecification.getWorkspaceAccessType() == null)))
            {
                throw new RuntimeException("workspace type and access type are required when workspace id is specified");
            }
        }

        @Override
        protected String getReference()
        {
            return this.revisionId == null ? getBranchName(projectId, sourceSpecification) : this.revisionId;
        }

        @Override
        protected String getDescriptionForExceptionMessage()
        {
            return BaseGitLabApi.getReferenceInfo(this.projectId, this.sourceSpecification.getWorkspaceId(), this.sourceSpecification.getWorkspaceType(), this.sourceSpecification.getWorkspaceAccessType(), this.revisionId);
        }
    }

    private class GitLabProjectVersionFileAccessContext extends AbstractGitLabFileAccessContext
    {
        private final VersionId versionId;

        private GitLabProjectVersionFileAccessContext(GitLabProjectId projectId, VersionId versionId)
        {
            super(projectId);
            this.versionId = versionId;
        }

        @Override
        protected String getReference()
        {
            return buildVersionTagName(this.versionId);
        }

        @Override
        protected String getDescriptionForExceptionMessage()
        {
            return this.versionId.appendVersionIdString(new StringBuilder("version ")).append(" of project ").append(this.projectId).toString();
        }
    }

    private abstract class AbstractGitLabRevisionAccessContext implements RevisionAccessContext
    {
        protected final GitLabProjectId projectId;
        protected final MutableList<String> paths;

        // Either file or directory paths should already have been canonicalized at this point.
        AbstractGitLabRevisionAccessContext(GitLabProjectId projectId, Iterable<? extends String> canonicalPaths)
        {
            this.projectId = projectId;
            if (canonicalPaths == null)
            {
                this.paths = null;
            }
            else
            {
                MutableList<String> canonicalPathsList = Lists.mutable.withAll(canonicalPaths);
                if (canonicalPathsList.contains(ProjectPaths.ROOT_DIRECTORY))
                {
                    this.paths = null;
                }
                else
                {
                    this.paths = canonicalPathsList.collect(p -> p.substring(1));
                }
            }
        }

        @Override
        public Revision getCurrentRevision()
        {
            try
            {
                CommitsApi commitsApi = getGitLabApi().getCommitsApi();
                String referenceId = getReference();

                // Search for current commit
                Commit currentCommit;
                if (this.paths == null)
                {
                    currentCommit = getCurrentCommit(commitsApi, referenceId, null);
                }
                else
                {
                    Comparator<Commit> comparator = Comparator.nullsFirst(Comparator.comparing(Commit::getCommittedDate, Comparator.nullsFirst(Comparator.naturalOrder())));
                    currentCommit = null;
                    for (String path : this.paths)
                    {
                        Commit currentPathCommit = getCurrentCommit(commitsApi, referenceId, path);
                        if (comparator.compare(currentPathCommit, currentCommit) > 0)
                        {
                            currentCommit = currentPathCommit;
                        }
                    }
                }

                // Found a current commit
                if (currentCommit != null)
                {
                    return fromGitLabCommit(currentCommit);
                }

                // Check if reference exists
                if (!referenceExists())
                {
                    throw new LegendSDLCServerException("Unknown: " + getDescriptionForExceptionMessage(), Status.NOT_FOUND);
                }

                // Reference exists, but cannot get commits - check if we can get a base revision
                String baseRevisionId;
                try
                {
                    baseRevisionId = resolveRevisionId(RevisionAlias.BASE.getValue(), this);
                }
                catch (Exception ignore)
                {
                    baseRevisionId = null;
                }
                if (baseRevisionId != null)
                {
                    // We got a base revision but no commit - perhaps the reference is corrupt?
                    LOGGER.debug("Can't get current revision for {} even when the project has more than one revision (workspace or project may be corrupt)", getDescriptionForExceptionMessage());
                    throw new LegendSDLCServerException("Can't get current revision for " + getDescriptionForExceptionMessage() + " (workspace or project may be corrupt)", Status.INTERNAL_SERVER_ERROR);
                }
                // This happens when project is created but has no revision
                LOGGER.debug("Can't get current revision for {} because the project is created but has no revision", getDescriptionForExceptionMessage());
                return null;
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to get current revision for " + getDescriptionForExceptionMessage(),
                        () -> "Unknown: " + getDescriptionForExceptionMessage(),
                        () -> "Error getting current revision for " + getDescriptionForExceptionMessage());
            }
        }

        private Commit getCurrentCommit(CommitsApi commitsApi, String referenceId, String filePath) throws GitLabApiException
        {
            Pager<Commit> pager = withRetries(() -> commitsApi.getCommits(this.projectId.getGitLabId(), referenceId, null, null, filePath, 1));
            List<Commit> page = pager.next();
            return ((page == null) || page.isEmpty()) ? null : page.get(0);
        }

        @Override
        public Revision getBaseRevision()
        {
            try
            {
                CommitsApi commitsApi = getGitLabApi().getCommitsApi();
                String referenceId = getReference();

                // Search for base commit
                Commit baseCommit;
                if (this.paths == null)
                {
                    baseCommit = getBaseCommit(commitsApi, referenceId, null);
                }
                else
                {
                    Comparator<Commit> comparator = Comparator.nullsLast(Comparator.comparing(Commit::getCommittedDate, Comparator.nullsLast(Comparator.naturalOrder())));
                    baseCommit = null;
                    for (String path : this.paths)
                    {
                        Commit basePathCommit = getBaseCommit(commitsApi, referenceId, path);
                        if (comparator.compare(basePathCommit, baseCommit) < 0)
                        {
                            baseCommit = basePathCommit;
                        }
                    }
                }

                // Found base commit
                if (baseCommit != null)
                {
                    return fromGitLabCommit(baseCommit);
                }

                // Check if the reference exists
                if (!referenceExists())
                {
                    throw new LegendSDLCServerException("Unknown: " + getDescriptionForExceptionMessage(), Status.NOT_FOUND);
                }

                // This happens when project is created but has no revision
                LOGGER.debug("Can't get base revision for {} because the project is created but has no revision", getDescriptionForExceptionMessage());
                return null;
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to get base revision for " + getDescriptionForExceptionMessage(),
                        () -> "Unknown: " + getDescriptionForExceptionMessage(),
                        () -> "Error getting base revision for " + getDescriptionForExceptionMessage());
            }
        }

        private Commit getBaseCommit(CommitsApi commitsApi, String referenceId, String filePath) throws GitLabApiException
        {
            Pager<Commit> pager = withRetries(() -> commitsApi.getCommits(this.projectId.getGitLabId(), referenceId, null, null, filePath, 1));
            List<Commit> page = pager.last();
            return ((page == null) || page.isEmpty()) ? null : page.get(0);
        }

        @Override
        public Revision getRevision(String revisionId)
        {
            LegendSDLCServerException.validateNonNull(revisionId, "revisionId may not be null");
            CommitsApi commitsApi = getGitLabApi().getCommitsApi();
            String resolvedRevisionId;
            try
            {
                resolvedRevisionId = resolveRevisionId(revisionId, this);
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to access revision " + revisionId + " of project " + this.projectId,
                        () -> "Unknown revision " + revisionId + " of project " + this.projectId,
                        () -> "Failed to get revision " + revisionId + " of project " + this.projectId);
            }
            if (resolvedRevisionId == null)
            {
                throw new LegendSDLCServerException("Failed to resolve revision " + revisionId + " of project " + this.projectId, Status.NOT_FOUND);
            }
            // Get the commit
            Commit commit;
            try
            {
                commit = withRetries(() -> commitsApi.getCommit(this.projectId.getGitLabId(), resolvedRevisionId));
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to access revision " + resolvedRevisionId + " for " + getDescriptionForExceptionMessage(),
                        () -> "Revision " + resolvedRevisionId + " is unknown for " + getDescriptionForExceptionMessage(),
                        () -> "Error accessing revision " + resolvedRevisionId + " for " + getDescriptionForExceptionMessage());
            }

            // Validate the commit is for the appropriate branch
            String referenceId = getReference();
            try
            {
                Pager<CommitRef> commitRefPager = withRetries(() -> commitsApi.getCommitRefs(this.projectId.getGitLabId(), resolvedRevisionId, RefType.BRANCH, ITEMS_PER_PAGE));
                if (PagerTools.stream(commitRefPager).map(CommitRef::getName).noneMatch(referenceId::equals))
                {
                    throw new LegendSDLCServerException("Revision " + resolvedRevisionId + " is unknown for " + getDescriptionForExceptionMessage(), Status.NOT_FOUND);
                }
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to access revision " + resolvedRevisionId + " for " + getDescriptionForExceptionMessage(),
                        () -> "Revision " + resolvedRevisionId + " is unknown for " + getDescriptionForExceptionMessage(),
                        () -> "Error accessing revision " + resolvedRevisionId + "for " + getDescriptionForExceptionMessage());
            }

            // Validate the commit is for the appropriate files
            if (this.paths != null)
            {
                try
                {
                    Stream<Commit> commitStream = getAllCommits(commitsApi, referenceId, commit.getCommittedDate(), commit.getCommittedDate(), ITEMS_PER_PAGE);
                    if ((commitStream == null) || commitStream.map(Commit::getId).noneMatch(resolvedRevisionId::equals))
                    {
                        throw new LegendSDLCServerException("Revision " + resolvedRevisionId + " is unknown for " + getDescriptionForExceptionMessage(), Status.NOT_FOUND);
                    }
                }
                catch (Exception e)
                {
                    throw buildException(e,
                            () -> "User " + getCurrentUser() + " is not allowed to access revisions for " + getDescriptionForExceptionMessage(),
                            () -> "Unknown: " + getDescriptionForExceptionMessage(),
                            () -> "Error accessing revisions for " + getDescriptionForExceptionMessage());
                }
            }

            return fromGitLabCommit(commit);
        }

        @Override
        public Stream<Revision> getAllRevisions(Predicate<? super Revision> predicate, Instant since, Instant until, Integer limit)
        {
            boolean limited = false;
            if (limit != null)
            {
                if (limit == 0)
                {
                    return Stream.empty();
                }
                if (limit < 0)
                {
                    throw new LegendSDLCServerException("Invalid limit: " + limit, Status.BAD_REQUEST);
                }
                limited = true;
            }
            try
            {
                CommitsApi commitsApi = getGitLabApi().getCommitsApi();
                String branchName = getReference();
                Stream<Commit> commitStream = getAllCommits(commitsApi, branchName, toDateIfNotNull(since), toDateIfNotNull(until), ITEMS_PER_PAGE);
                if (commitStream == null)
                {
                    if (!referenceExists())
                    {
                        throw new LegendSDLCServerException("Unknown: " + getDescriptionForExceptionMessage(), Status.NOT_FOUND);
                    }
                    return Stream.empty();
                }

                Stream<Revision> revisionStream = commitStream.map(GitLabApiWithFileAccess::fromGitLabCommit);
                if (predicate != null)
                {
                    revisionStream = revisionStream.filter(predicate);
                }
                if (limited)
                {
                    revisionStream = revisionStream.limit(limit);
                }
                return revisionStream;
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to get revisions for " + getDescriptionForExceptionMessage(),
                        () -> "Unknown: " + getDescriptionForExceptionMessage(),
                        () -> "Error getting revisions for " + getDescriptionForExceptionMessage());
            }
        }

        private Stream<Commit> getAllCommits(CommitsApi commitsApi, String branchName, Date since, Date until, int itemsPerPage) throws GitLabApiException
        {
            if (this.paths == null)
            {
                Pager<Commit> pager = withRetries(() -> commitsApi.getCommits(this.projectId.getGitLabId(), branchName, since, until, null, itemsPerPage));
                return PagerTools.isEmpty(pager) ? null : PagerTools.stream(pager);
            }

            Stream<Commit> commitStream = null;
            int streamCount = 0;
            for (String path : this.paths)
            {
                Pager<Commit> pager = withRetries(() -> commitsApi.getCommits(this.projectId.getGitLabId(), branchName, since, until, path, itemsPerPage));
                if (!PagerTools.isEmpty(pager))
                {
                    streamCount++;
                    Stream<Commit> pathCommitStream = PagerTools.stream(pager);
                    commitStream = (commitStream == null) ? pathCommitStream : Stream.concat(commitStream, pathCommitStream);
                }
            }
            if ((commitStream != null) && (streamCount > 1))
            {
                MutableSet<String> ids = Sets.mutable.empty();
                commitStream = commitStream.filter(commit -> ids.add(commit.getId()));
            }
            return commitStream;
        }

        protected abstract String getReference();

        protected abstract boolean referenceExists() throws GitLabApiException;

        protected String getDescriptionForExceptionMessage()
        {
            StringBuilder builder = new StringBuilder();
            if (this.paths != null)
            {
                if (this.paths.size() == 1)
                {
                    builder.append(this.paths.get(0));
                }
                else
                {
                    this.paths.appendString(builder, "{", ", ", "}");
                }
                builder.append(" in ");
            }
            int lengthBefore = builder.length();
            appendReferenceDescription(builder);
            if (builder.length() != lengthBefore)
            {
                builder.append(" in ");
            }
            builder.append("project ").append(this.projectId);
            return builder.toString();

        }

        protected abstract void appendReferenceDescription(StringBuilder builder);
    }

    private class GitLabRevisionAccessContext extends AbstractGitLabRevisionAccessContext
    {
        private final SourceSpecification sourceSpecification;

        private GitLabRevisionAccessContext(GitLabProjectId projectId, SourceSpecification sourceSpecification, Iterable<? extends String> paths)
        {
            super(projectId, paths);
            this.sourceSpecification = sourceSpecification;
            if (sourceSpecification == null)
            {
                throw new RuntimeException("source specification may not be null");
            }
            if ((this.sourceSpecification.getWorkspaceId() != null) && ((this.sourceSpecification.getWorkspaceType() == null) || (this.sourceSpecification.getWorkspaceAccessType() == null)))
            {
                throw new RuntimeException("workspace type and access type are required when workspace id is specified");
            }
        }

        @Override
        protected String getReference()
        {
            return getBranchName(this.projectId, this.sourceSpecification);
        }

        @Override
        protected boolean referenceExists() throws GitLabApiException
        {
            if (this.sourceSpecification.getWorkspaceId() == null)
            {
                return true;
            }

            try
            {
                RepositoryApi repositoryApi = getGitLabApi().getRepositoryApi();
                Branch branch = withRetries(() -> repositoryApi.getBranch(this.projectId.getGitLabId(), getReference()));
                return branch != null;
            }
            catch (GitLabApiException e)
            {
                if (GitLabApiTools.isNotFoundGitLabApiException(e))
                {
                    return false;
                }
                throw e;
            }
        }

        @Override
        protected void appendReferenceDescription(StringBuilder builder)
        {
            if (this.sourceSpecification.getWorkspaceId() != null)
            {
                builder.append(this.sourceSpecification.getWorkspaceType().getLabel()).append(" ").append(this.sourceSpecification.getWorkspaceAccessType().getLabel()).append(" ").append(this.sourceSpecification.getWorkspaceId());
            }
        }

        @Override
        public Revision getBaseRevision()
        {
            if (this.sourceSpecification.getWorkspaceId() == null)
            {
                return super.getBaseRevision();
            }

            try
            {
                RepositoryApi repositoryApi = getGitLabApi().getRepositoryApi();
                Revision workspaceBaseRevision = fromGitLabCommit(withRetries(() -> repositoryApi.getMergeBase(this.projectId.getGitLabId(), Lists.fixedSize.with(getSourceBranch(projectId, sourceSpecification.getPatchReleaseVersionId()), getReference()))));
                if (this.paths == null)
                {
                    return workspaceBaseRevision;
                }

                Revision pathsBaseRevision = super.getBaseRevision();
                if (workspaceBaseRevision == null)
                {
                    return pathsBaseRevision;
                }
                if (pathsBaseRevision == null)
                {
                    return workspaceBaseRevision;
                }
                if (Objects.equals(workspaceBaseRevision.getId(), pathsBaseRevision.getId()))
                {
                    return workspaceBaseRevision;
                }

                Instant workspaceCommitted = workspaceBaseRevision.getCommittedTimestamp();
                Instant pathCommitted = pathsBaseRevision.getCommittedTimestamp();
                return ((pathCommitted != null) && (workspaceCommitted != null) && pathCommitted.isAfter(workspaceCommitted)) ? pathsBaseRevision : workspaceBaseRevision;
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to get base revision for " + getDescriptionForExceptionMessage(),
                        () -> "Unknown: " + getDescriptionForExceptionMessage(),
                        () -> "Error getting base revision for " + getDescriptionForExceptionMessage());
            }
        }
    }

    private class GitLabProjectVersionRevisionAccessContext extends AbstractGitLabRevisionAccessContext
    {
        private final VersionId versionId;

        private GitLabProjectVersionRevisionAccessContext(GitLabProjectId projectId, VersionId versionId, Iterable<? extends String> paths)
        {
            super(projectId, paths);
            this.versionId = versionId;
        }

        @Override
        protected String getReference()
        {
            return buildVersionTagName(this.versionId);
        }

        @Override
        protected boolean referenceExists() throws GitLabApiException
        {
            try
            {
                Tag tag = getGitLabApi().getTagsApi().getTag(this.projectId.getGitLabId(), getReference());
                return tag != null;
            }
            catch (GitLabApiException e)
            {
                if (GitLabApiTools.isNotFoundGitLabApiException(e))
                {
                    return false;
                }
                throw e;
            }
        }

        @Override
        protected void appendReferenceDescription(StringBuilder builder)
        {
            this.versionId.appendVersionIdString(builder.append("version "));
        }
    }

    private class GitLabProjectFileFileModificationContext implements FileModificationContext
    {
        private final GitLabProjectId projectId;
        private final String revisionId;
        private final SourceSpecification sourceSpecification;

        private GitLabProjectFileFileModificationContext(GitLabProjectId projectId, SourceSpecification sourceSpecification, String revisionId)
        {
            this.projectId = projectId;
            this.revisionId = revisionId;
            this.sourceSpecification = sourceSpecification;
            if (sourceSpecification == null)
            {
                throw new RuntimeException("source specification may not be null");
            }
            if ((this.sourceSpecification.getWorkspaceId() != null) && ((this.sourceSpecification.getWorkspaceType() == null) || (this.sourceSpecification.getWorkspaceAccessType() == null)))
            {
                throw new RuntimeException("workspace type and access type are required when workspace id is specified");
            }
        }

        @Override
        public Revision submit(String message, List<? extends ProjectFileOperation> operations)
        {
            try
            {
                int changeCount = operations.size();
                List<CommitAction> commitActions = operations.stream().map(this::fileOperationToCommitAction).collect(Collectors.toCollection(() -> Lists.mutable.ofInitialCapacity(changeCount)));
                String referenceRevisionId = this.revisionId;
                if (commitActions.stream().anyMatch(ca -> (ca.getAction() == Action.MOVE) && (ca.getContent() == null)))
                {
                    referenceRevisionId = fillInMissingMoveContent(commitActions);
                }
                Commit commit;
                if (changeCount > MAX_COMMIT_SIZE)
                {
                    commit = commitWithTemporaryBranch(message, commitActions, referenceRevisionId);
                }
                else
                {
                    // here we already assumed that we have the correct branch, if `referenceRevisionId` is not null, it means we have some
                    // information about the state of the branch and used it to create the operations. In this case, we're making sure
                    // that there have been NO subsequent comments since we got that information, otherwise, our operations are invalid
                    if (referenceRevisionId != null)
                    {
                        if (LOGGER.isDebugEnabled())
                        {
                            LOGGER.debug("Checking that {} is at revision {}", getDescription(), referenceRevisionId);
                        }
                        String targetBranchRevision = getCurrentRevisionId(this.projectId, this.sourceSpecification);
                        if (!referenceRevisionId.equals(targetBranchRevision))
                        {
                            String msg = "Expected " + getDescription() + " to be at revision " + referenceRevisionId + "; instead it was at revision " + targetBranchRevision;
                            LOGGER.info(msg);
                            throw new LegendSDLCServerException(msg, Status.CONFLICT);
                        }
                    }
                    String branchName = getBranchName(this.projectId, this.sourceSpecification);
                    commit = getGitLabApi().getCommitsApi().createCommit(this.projectId.getGitLabId(), branchName, message, null, null, null, commitActions);
                }
                if (LOGGER.isDebugEnabled())
                {
                    LOGGER.debug("Committed {} changes to {}: {}", changeCount, getDescription(), commit.getId());
                }
                return fromGitLabCommit(commit);
            }
            catch (Exception e)
            {
                // TODO improve exception handling
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to perform changes on " + this.sourceSpecification.getWorkspaceType().getLabel() + " " + this.sourceSpecification.getWorkspaceAccessType().getLabel() + " " + this.sourceSpecification.getWorkspaceId() + " in project " + this.projectId,
                        () -> "Unknown " + getDescription(),
                        () -> "Failed to perform changes on " + getDescription() + " (message: " + message + ")");
            }
        }

        private CommitAction fileOperationToCommitAction(ProjectFileOperation fileOperation)
        {
            if (fileOperation instanceof ProjectFileOperation.AddFile)
            {
                return new CommitAction()
                        .withAction(Action.CREATE)
                        .withFilePath(fileOperation.getPath())
                        .withEncoding(Encoding.BASE64)
                        .withContent(encodeBase64(((ProjectFileOperation.AddFile) fileOperation).getContent()));
            }
            if (fileOperation instanceof ProjectFileOperation.ModifyFile)
            {
                return new CommitAction()
                        .withAction(Action.UPDATE)
                        .withFilePath(toGitLabFilePath(fileOperation.getPath()))
                        .withEncoding(Encoding.BASE64)
                        .withContent(encodeBase64(((ProjectFileOperation.ModifyFile) fileOperation).getNewContent()));
            }
            if (fileOperation instanceof ProjectFileOperation.DeleteFile)
            {
                return new CommitAction()
                        .withAction(Action.DELETE)
                        .withFilePath(toGitLabFilePath(fileOperation.getPath()));
            }
            if (fileOperation instanceof ProjectFileOperation.MoveFile)
            {
                ProjectFileOperation.MoveFile moveFileOperation = (ProjectFileOperation.MoveFile) fileOperation;
                CommitAction commitAction = new CommitAction()
                        .withAction(Action.MOVE)
                        .withPreviousPath(toGitLabFilePath(moveFileOperation.getPath()))
                        .withFilePath(toGitLabFilePath(moveFileOperation.getNewPath()));

                byte[] newContent = moveFileOperation.getNewContent();
                if (newContent != null)
                {
                    commitAction.setEncoding(Encoding.BASE64);
                    commitAction.setContent(encodeBase64(newContent));
                }

                return commitAction;
            }
            throw new IllegalArgumentException("Unsupported project file operation: " + fileOperation);
        }

        private String fillInMissingMoveContent(List<? extends CommitAction> commitActions) throws GitLabApiException
        {
            String referenceRevisionId = this.revisionId;
            // Note: we index move actions by both previous and new path
            Map<String, List<CommitAction>> commitActionsByPath = Maps.mutable.empty();
            for (CommitAction commitAction : commitActions)
            {
                if (Action.MOVE == commitAction.getAction())
                {
                    String content = commitAction.getContent();
                    if (content == null)
                    {
                        List<CommitAction> previousCommitActionsForPath = commitActionsByPath.getOrDefault(commitAction.getPreviousPath(), Collections.emptyList());
                        if (previousCommitActionsForPath.isEmpty())
                        {
                            if (referenceRevisionId == null)
                            {
                                referenceRevisionId = getCurrentRevisionId(this.projectId, this.sourceSpecification);
                                LOGGER.debug("Using current revision ({}) as reference revision for filling in content for move operations", referenceRevisionId);
                            }
                            LOGGER.debug("Getting content for move from {} to {} from revision {}", commitAction.getPreviousPath(), commitAction.getFilePath(), referenceRevisionId);
                            // TODO handle not found case
                            RepositoryFile file = getGitLabApi().getRepositoryFileApi().getFile(this.projectId.getGitLabId(), commitAction.getPreviousPath(), referenceRevisionId, true);
                            commitAction.setEncoding(file.getEncoding());
                            commitAction.setContent(file.getContent());
                        }
                        else
                        {
                            LOGGER.debug("Getting content for move from {} to {} from previous commit action", commitAction.getPreviousPath(), commitAction.getFilePath());
                            // TODO throw if lastCommitActionForPath.getContent is null - or make sure this case can never happen
                            CommitAction lastCommitActionForPath = previousCommitActionsForPath.get(previousCommitActionsForPath.size() - 1);
                            commitAction.setEncoding(lastCommitActionForPath.getEncoding());
                            commitAction.setContent(lastCommitActionForPath.getContent());
                        }
                    }
                    commitActionsByPath.computeIfAbsent(commitAction.getPreviousPath(), k -> Lists.mutable.empty()).add(commitAction);
                }
                commitActionsByPath.computeIfAbsent(commitAction.getFilePath(), k -> Lists.mutable.empty()).add(commitAction);
            }
            return referenceRevisionId;
        }

        private Commit commitWithTemporaryBranch(String message, List<CommitAction> commitActions, String referenceRevisionId)
        {
            int commitActionCount = commitActions.size();
            int commitSize = MAX_COMMIT_SIZE;
            int totalCommitCount = ((commitActionCount - 1) / commitSize) + 1;

            LOGGER.debug("Committing {} changes in {} commit(s)", commitActionCount, totalCommitCount);
            try (TemporaryBranch tempBranch = newTemporaryBranch(this.projectId, this.sourceSpecification, referenceRevisionId))
            {
                if (LOGGER.isDebugEnabled())
                {
                    LOGGER.debug("Committing into temporary branch for {}", getDescription());
                }
                for (int i = 0, commitNumber = 1; i < commitActionCount; i += commitSize, commitNumber++)
                {
                    int end = Math.min(i + commitSize, commitActionCount);
                    LOGGER.debug("Committing part {} of {}: changes {}-{}", commitNumber, totalCommitCount, i + 1, end);
                    List<CommitAction> subList = commitActions.subList(i, end);
                    String subMessage = (totalCommitCount > 1) ? (message + " [" + commitNumber + " / " + totalCommitCount + "]") : message;
                    Commit commit = tempBranch.commit(subMessage, subList);
                    LOGGER.debug("Committed part {} of {}: {}", commitNumber, totalCommitCount, commit.getId());
                }
                Branch newBranch = tempBranch.replaceTargetAndDelete();
                Commit finalCommit = newBranch.getCommit();
                if (LOGGER.isDebugEnabled())
                {
                    LOGGER.debug("Changes from temporary branch {} merged into {} at revision {}", tempBranch.getBranchName(), getDescription(), finalCommit.getId());
                }
                return finalCommit;
            }
            catch (LegendSDLCServerException e)
            {
                throw new LegendSDLCServerException("Error committing to " + getDescription() + " with a temporary branch", e.getStatus(), e);
            }
            catch (Exception e)
            {
                // TODO improve exception handling
                throw new LegendSDLCServerException("Error committing to " + getDescription() + " with a temporary branch", e);
            }
        }

        private String getDescription()
        {
            return BaseGitLabApi.getReferenceInfo(this.projectId, this.sourceSpecification.getWorkspaceId(), this.sourceSpecification.getWorkspaceType(), this.sourceSpecification.getWorkspaceAccessType(), null);
        }
    }

    protected String encodeBase64(byte[] content)
    {
        return new String(Base64.getEncoder().encode(content), StandardCharsets.ISO_8859_1);
    }

    protected void submitBackgroundTask(BackgroundTaskProcessor.Task task)
    {
        this.backgroundTaskProcessor.submitTask(task);
    }

    protected void submitBackgroundTask(BackgroundTaskProcessor.Task task, String description)
    {
        this.backgroundTaskProcessor.submitTask(task, description);
    }

    protected void submitBackgroundRetryableTask(BackgroundTaskProcessor.RetryableTask task)
    {
        this.backgroundTaskProcessor.submitRetryableTask(task, GitLabApiWithFileAccess::shouldRetryOnException);
    }

    protected void submitBackgroundRetryableTask(BackgroundTaskProcessor.RetryableTask task, long minWaitBetweenRetriesMillis)
    {
        this.backgroundTaskProcessor.submitRetryableTask(task, GitLabApiWithFileAccess::shouldRetryOnException, minWaitBetweenRetriesMillis);
    }

    protected void submitBackgroundRetryableTask(BackgroundTaskProcessor.RetryableTask task, String description)
    {
        this.backgroundTaskProcessor.submitRetryableTask(task, GitLabApiWithFileAccess::shouldRetryOnException, description);
    }

    protected void submitBackgroundRetryableTask(BackgroundTaskProcessor.RetryableTask task, long minWaitBetweenRetriesMillis, String description)
    {
        this.backgroundTaskProcessor.submitRetryableTask(task, GitLabApiWithFileAccess::shouldRetryOnException, minWaitBetweenRetriesMillis, description);
    }

    private String toGitLabFilePath(String path)
    {
        return ((path != null) && path.startsWith("/")) ? path.substring(1) : path;
    }

    private TemporaryBranch newTemporaryBranch(GitLabProjectId projectId, SourceSpecification sourceSpecification, String referenceRevisionId)
    {
        String initialRevisionId = (referenceRevisionId == null) ? getCurrentRevisionId(projectId, sourceSpecification) : referenceRevisionId;
        return new TemporaryBranch(projectId, sourceSpecification.getWorkspaceId(), sourceSpecification.getWorkspaceType(), sourceSpecification.getWorkspaceAccessType(), initialRevisionId);
    }

    private static boolean shouldRetryOnException(Exception e)
    {
        if (!(e instanceof GitLabApiException))
        {
            return false;
        }

        GitLabApiException glae = (GitLabApiException) e;
        return GitLabApiTools.isRetryableGitLabApiException(glae) || (glae.getHttpStatus() == Status.INTERNAL_SERVER_ERROR.getStatusCode());
    }

    protected static boolean waitForPipelinesDeleteBranchAndVerify(GitLabApi gitLabApi, GitLabProjectId projectId, String branchName)
    {
        LOGGER.debug("Checking for pending pipelines for branch {} in project {}", branchName, projectId);
        try
        {
            Pager<Pipeline> pendingPipelines = GitLabApiTools.callWithRetries(() -> gitLabApi.getPipelineApi().getPipelines(projectId.getGitLabId(), Constants.PipelineScope.PENDING, PipelineStatus.PENDING, branchName, false, null, null, null, null, 1), 10, 1000L);
            if (!PagerTools.isEmpty(pendingPipelines))
            {
                LOGGER.debug("Found pending pipelines for branch {} in project {}", branchName, projectId);
                return false;
            }
        }
        catch (Exception e)
        {
            if (GitLabApiTools.isRetryableGitLabApiException(e))
            {
                return false;
            }
            // Don't let a non-retryable exception here block the delete
            LOGGER.warn("Error checking for pending pipelines for branch {} in project {}", branchName, projectId, e);
        }
        LOGGER.debug("Found no pending pipelines for branch {} in project {}", branchName, projectId);

        LOGGER.debug("Checking for running pipelines for branch {} in project {}", branchName, projectId);
        try
        {
            Pager<Pipeline> runningPipelines = GitLabApiTools.callWithRetries(() -> gitLabApi.getPipelineApi().getPipelines(projectId.getGitLabId(), Constants.PipelineScope.RUNNING, PipelineStatus.RUNNING, branchName, false, null, null, null, null, 1), 10, 1000L);
            if (!PagerTools.isEmpty(runningPipelines))
            {
                LOGGER.debug("Found running pipelines for branch {} in project {}", branchName, projectId);
                return false;
            }
        }
        catch (Exception e)
        {
            if (GitLabApiTools.isRetryableGitLabApiException(e))
            {
                return false;
            }
            // Don't let a non-retryable exception here block the delete
            LOGGER.warn("Error checking for running pipelines for branch {} in project {}", branchName, projectId, e);
        }
        LOGGER.debug("Found no running pipelines for branch {} in project {}", branchName, projectId);

        LOGGER.debug("Deleting branch {} in project {}", branchName, projectId);
        try
        {
            boolean success = GitLabApiTools.deleteBranchAndVerify(gitLabApi, projectId.getGitLabId(), branchName, 5, 1000L);
            if (success)
            {
                LOGGER.debug("Deleted branch {} in project {}", branchName, projectId);
            }
            else
            {
                LOGGER.debug("Did not delete branch {} in project {}", branchName, projectId);
            }
            return success;
        }
        catch (Exception e)
        {
            // a "not found" exception means the branch isn't there, and so doesn't need to be deleted
            if (GitLabApiTools.isNotFoundGitLabApiException(e))
            {
                LOGGER.debug("Branch {} in project {} cannot be found: no need to delete", branchName, projectId);
                return true;
            }

            StringBuilder builder = new StringBuilder("Error deleting branch ").append(branchName).append(" in project ").append(projectId);
            StringTools.appendThrowableMessageIfPresent(builder, e);
            String message = builder.toString();
            LOGGER.error(message, e);
            throw new LegendSDLCServerException(message, e);
        }
    }

    private class TemporaryBranch implements AutoCloseable
    {
        private final GitLabProjectId projectId;
        private final String workspaceId;
        private final WorkspaceType workspaceType;
        private final WorkspaceAccessType workspaceAccessType;
        private final String referenceCommitId;
        private String tempBranchName;
        private String lastSuccessfulCommitId;
        private boolean closed = false;

        private TemporaryBranch(GitLabProjectId projectId, String workspaceId, WorkspaceType workspaceType, WorkspaceAccessType workspaceAccessType, String referenceCommitId)
        {
            this.projectId = projectId;
            this.workspaceId = workspaceId;
            this.workspaceType = workspaceType;
            this.workspaceAccessType = workspaceAccessType;
            this.referenceCommitId = referenceCommitId;
        }

        String getBranchName()
        {
            return this.tempBranchName;
        }

        synchronized Commit commit(String message, List<CommitAction> commitActions)
        {
            checkOpen();

            CommitsApi commitsApi = getGitLabApi().getCommitsApi();
            Exception lastException = null;
            for (int i = 1; i <= MAX_COMMIT_RETRIES; i++)
            {
                try
                {
                    if ((i > 1) || (this.tempBranchName == null))
                    {
                        createNewTempBranch();
                    }
                    Commit commit = commitsApi.createCommit(this.projectId.getGitLabId(), this.tempBranchName, message, null, null, null, commitActions);
                    if (i > 1)
                    {
                        LOGGER.debug("Commit succeeded on try {}", i);
                    }
                    this.lastSuccessfulCommitId = commit.getId();
                    return commit;
                }
                catch (Exception e)
                {
                    GitLabApiException glae = GitLabApiTools.findGitLabApiException(e);
                    if (glae != null)
                    {
                        // a client error should be considered a fatal error that stops retries
                        int statusCode = glae.getHttpStatus();
                        if (Family.familyOf(statusCode) == Family.CLIENT_ERROR)
                        {
                            StringBuilder builder = new StringBuilder("Error committing to temporary branch ").append(this.tempBranchName)
                                    .append("for ").append(this.workspaceType.getLabel()).append(" ").append(this.workspaceAccessType.getLabel()).append(" ").append(this.workspaceId)
                                    .append(" in project ").append(this.projectId);
                            StringTools.appendThrowableMessageIfPresent(builder, e);
                            String msg = builder.toString();
                            LOGGER.error(msg, e);
                            if (LOGGER.isDebugEnabled())
                            {
                                StringBuilder debugMessage = new StringBuilder("Commit actions:");
                                for (CommitAction commitAction : commitActions)
                                {
                                    Action action = commitAction.getAction();
                                    debugMessage.append("\n\t").append(action).append(": ");
                                    if (action == Action.MOVE)
                                    {
                                        debugMessage.append(commitAction.getPreviousPath()).append(" -> ");
                                    }
                                    debugMessage.append(commitAction.getFilePath());
                                }
                                LOGGER.debug(debugMessage.toString());
                            }
                            throw new LegendSDLCServerException(msg, Status.fromStatusCode(statusCode), e);
                        }
                    }
                    LOGGER.error("Commit failed on try {}", i, e);
                    lastException = e;
                }
            }

            // Reached the max number of retries, give up
            StringBuilder builder = new StringBuilder("Failed to commit to temporary branch for ")
                    .append(this.workspaceType.getLabel()).append(" ").append(this.workspaceAccessType.getLabel()).append(" ").append(this.workspaceId)
                    .append(" in project ").append(this.projectId)
                    .append(" after ").append(MAX_COMMIT_RETRIES).append(" tries");
            StringTools.appendThrowableMessageIfPresent(builder, lastException);
            String msg = builder.toString();
            LOGGER.error(msg, lastException);
            throw new LegendSDLCServerException(msg, lastException);
        }

        synchronized Branch replaceTargetAndDelete()
        {
            checkOpen();

            if (this.tempBranchName == null)
            {
                throw new IllegalStateException("No temporary branch has been created yet");
            }
            if (this.lastSuccessfulCommitId == null)
            {
                throw new IllegalStateException("No commits on temporary branch " + this.tempBranchName + " in project " + this.projectId);
            }

            String targetBranchName = GitLabApiWithFileAccess.this.getBranchName(this.projectId, SourceSpecification.newSourceSpecification(this.workspaceId, this.workspaceType, this.workspaceAccessType));
            LOGGER.debug("Replacing target branch {} with temporary branch {} in project {}", targetBranchName, this.tempBranchName, this.projectId);

            RepositoryApi repositoryApi = getGitLabApi().getRepositoryApi();

            Branch targetBranch;
            try
            {
                targetBranch = withRetries(() -> GitLabApiTools.getBranch(repositoryApi, this.projectId.getGitLabId(), targetBranchName));
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to get " + this.workspaceType.getLabel() + " " + this.workspaceAccessType.getLabel() + " " + this.workspaceId + " in project " + this.projectId,
                        () -> "Unknown " + this.workspaceType.getLabel() + " " + this.workspaceAccessType.getLabel() + " (" + this.workspaceId + ") or project (" + this.projectId + ")",
                        () -> "Failed to get " + this.workspaceType.getLabel() + " " + this.workspaceAccessType.getLabel() + " " + this.workspaceId + " in project " + this.projectId);
            }

            if (targetBranch != null)
            {
                Commit targetBranchCommit = targetBranch.getCommit();
                String targetBranchCommitId = (targetBranchCommit == null) ? null : targetBranchCommit.getId();
                if (!this.referenceCommitId.equals(targetBranchCommitId))
                {
                    throw new LegendSDLCServerException("Expected " + this.workspaceType.getLabel() + " " + this.workspaceAccessType.getLabel() + " " + this.workspaceId + " to be at revision " + this.referenceCommitId + ", found " + targetBranchCommitId);
                }

                boolean oldDeleted;
                try
                {
                    oldDeleted = GitLabApiTools.deleteBranchAndVerify(repositoryApi, this.projectId.getGitLabId(), targetBranchName, 20, 1_000);
                }
                catch (Exception e)
                {
                    throw buildException(e,
                            () -> "User " + getCurrentUser() + " is not allowed to delete " + this.workspaceType.getLabel() + " " + this.workspaceAccessType.getLabel() + " " + this.workspaceId + " in project " + this.projectId,
                            () -> "Unknown " + this.workspaceType.getLabel() + " " + this.workspaceAccessType.getLabel() + " (" + this.workspaceId + ") or project (" + this.projectId + ")",
                            () -> "Failed to delete " + this.workspaceType.getLabel() + " " + this.workspaceAccessType.getLabel() + " " + this.workspaceId + " in project " + this.projectId);
                }
                if (!oldDeleted)
                {
                    throw new LegendSDLCServerException("Failed to delete " + this.workspaceType.getLabel() + " " + this.workspaceAccessType.getLabel() + " " + this.workspaceId + " in project " + this.projectId);
                }
            }

            Branch newBranch;
            try
            {
                newBranch = GitLabApiTools.createBranchAndVerify(repositoryApi, this.projectId.getGitLabId(), targetBranchName, this.lastSuccessfulCommitId, 30, 1_000);
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to create " + this.workspaceType.getLabel() + " " + this.workspaceAccessType.getLabel() + " " + this.workspaceId + " in project " + this.projectId + " from revision " + this.lastSuccessfulCommitId,
                        () -> "Unknown revision (" + this.lastSuccessfulCommitId + "), " + this.workspaceType.getLabel() + " " + this.workspaceAccessType.getLabel() + " (" + this.workspaceId + ") or project (" + this.projectId + ")",
                        () -> "Failed to create " + this.workspaceType.getLabel() + " " + this.workspaceAccessType.getLabel() + " " + this.workspaceId + " in project " + this.projectId + " from revision " + this.lastSuccessfulCommitId);
            }
            if (newBranch == null)
            {
                throw new LegendSDLCServerException("Failed to create " + this.workspaceType.getLabel() + " " + this.workspaceAccessType.getLabel() + " " + this.workspaceId + " in project " + this.projectId + " from revision " + this.lastSuccessfulCommitId);
            }

            deleteTempBranch(this.tempBranchName);
            this.closed = true;
            return newBranch;
        }

        public synchronized void delete()
        {
            checkOpen();
            if (this.tempBranchName == null)
            {
                LOGGER.debug("No temporary branch to delete for {} {} in project {}", this.workspaceType.getLabel(), this.workspaceAccessType.getLabel(), this.projectId);
            }
            else
            {
                deleteTempBranch(this.tempBranchName);
            }
            this.closed = true;
        }

        @Override
        public synchronized void close()
        {
            if (!this.closed)
            {
                delete();
            }
        }

        private synchronized void createNewTempBranch()
        {
            String newTempBranchName = newUserTemporaryBranchName(this.workspaceId);
            String branchCreationRef = (this.lastSuccessfulCommitId == null) ? this.referenceCommitId : this.lastSuccessfulCommitId;
            LOGGER.debug("Creating temporary branch for {} {} {} in project {} from {}: {}", this.workspaceType.getLabel(), this.workspaceAccessType.getLabel(), this.workspaceId, this.projectId, branchCreationRef, newTempBranchName);
            // Create new temp branch
            RepositoryApi repositoryApi = getGitLabApi().getRepositoryApi();
            Branch tempBranch;
            try
            {
                tempBranch = GitLabApiTools.createBranchAndVerify(repositoryApi, this.projectId.getGitLabId(), newTempBranchName, branchCreationRef, 30, 1_000);
                LOGGER.debug("Created temporary branch for {} {} {} in project {} from {}: {}", this.workspaceType.getLabel(), this.workspaceAccessType.getLabel(), this.workspaceId, this.projectId, branchCreationRef, newTempBranchName);
            }
            catch (Exception e)
            {
                LOGGER.debug("Failed to create temporary branch for {} {} {} in project {} from {}: {}", this.workspaceType.getLabel(), this.workspaceAccessType.getLabel(), this.workspaceId, this.projectId, branchCreationRef, newTempBranchName);
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to create temporary branch " + newTempBranchName + " in project " + this.projectId + " from revision " + branchCreationRef,
                        () -> "Unknown project " + this.projectId + " or revision " + branchCreationRef,
                        () -> "Error creating temporary branch " + newTempBranchName + " for " + this.workspaceType.getLabel() + " " + this.workspaceAccessType.getLabel() + " " + this.workspaceId + " in project " + this.projectId + " from revision " + branchCreationRef);
            }
            if (tempBranch == null)
            {
                throw new LegendSDLCServerException("Failed to create temporary branch " + newTempBranchName + " in project " + this.projectId + " from revision " + branchCreationRef);
            }
            // Delete old one, if it exists
            if (this.tempBranchName != null)
            {
                deleteTempBranch(this.tempBranchName);
            }
            // Record the new temp branch name
            this.tempBranchName = newTempBranchName;
        }

        private void deleteTempBranch(String branchName)
        {
            GitLabApi gitLabApi = getGitLabApi();
            GitLabProjectId projectId = this.projectId;
            submitBackgroundRetryableTask(() -> waitForPipelinesDeleteBranchAndVerify(gitLabApi, projectId, branchName), 5000L, "delete " + branchName);
        }

        private void checkOpen()
        {
            if (this.closed)
            {
                throw new IllegalStateException("Temporary branch " + this.tempBranchName + " in project " + this.projectId + " has already been closed");
            }
        }
    }

    private static class ArchiveStreamProjectFileSpliterator implements Spliterator<ProjectFileAccessProvider.ProjectFile>, Closeable
    {
        private final Object streamLock = new Object();
        private final ArchiveInputStream stream;
        private ArchiveEntry currentEntry = null;
        private boolean done = false;

        private ArchiveStreamProjectFileSpliterator(ArchiveInputStream stream)
        {
            this.stream = stream;
        }

        @Override
        public synchronized boolean tryAdvance(Consumer<? super ProjectFileAccessProvider.ProjectFile> action)
        {
            ProjectFileAccessProvider.ProjectFile file = getNextProjectFile();
            if (file == null)
            {
                return false;
            }
            action.accept(file);
            return true;
        }

        @Override
        public synchronized void forEachRemaining(Consumer<? super ProjectFileAccessProvider.ProjectFile> action)
        {
            for (ProjectFileAccessProvider.ProjectFile file = getNextProjectFile(); file != null; file = getNextProjectFile())
            {
                action.accept(file);
            }
        }

        @Override
        public Spliterator<ProjectFileAccessProvider.ProjectFile> trySplit()
        {
            return null;
        }

        @Override
        public long estimateSize()
        {
            return this.done ? 0L : Long.MAX_VALUE;
        }

        @Override
        public long getExactSizeIfKnown()
        {
            return this.done ? 0L : -1L;
        }

        @Override
        public int characteristics()
        {
            return 0;
        }

        @Override
        public synchronized void close() throws IOException
        {
            synchronized (this.streamLock)
            {
                this.done = true;
                this.currentEntry = null;
                this.stream.close();
            }
        }

        private ProjectFileAccessProvider.ProjectFile getNextProjectFile()
        {
            ArchiveEntry entry = advanceCurrentEntry();
            if (entry == null)
            {
                return null;
            }

            String name = entry.getName();
            int firstSlash = name.indexOf('/');
            String path = (firstSlash == -1) ? name : name.substring(firstSlash);
            return ProjectFiles.newByteArrayProjectFile(path, p -> getContent(p, entry));
        }

        private ArchiveEntry advanceCurrentEntry()
        {
            synchronized (this.streamLock)
            {
                if (this.done)
                {
                    return null;
                }
                this.currentEntry = null;
                try
                {
                    ArchiveEntry entry = this.stream.getNextEntry();
                    // skip directory entries
                    while ((entry != null) && entry.isDirectory())
                    {
                        entry = this.stream.getNextEntry();
                    }
                    this.currentEntry = entry;
                    if (entry == null)
                    {
                        // no more archive entries, close
                        close();
                    }
                    return entry;
                }
                catch (IOException e)
                {
                    throw new RuntimeException(e);
                }
            }
        }

        private byte[] getContent(String path, ArchiveEntry entry)
        {
            synchronized (this.streamLock)
            {
                if (entry != this.currentEntry)
                {
                    throw new IllegalStateException("Error reading file \"" + path + "\": no longer the current entry");
                }

                long entrySize = entry.getSize();
                if (entrySize > Integer.MAX_VALUE)
                {
                    throw new RuntimeException(String.format("Error reading file \"%s\": is too large (%,d bytes)", path, entrySize));
                }
                try
                {
                    return IOTools.readAllBytes(this.stream, (int) entrySize);
                }
                catch (IOException e)
                {
                    throw new RuntimeException(StringTools.appendThrowableMessageIfPresent(new StringBuilder("Error reading file \"").append(path).append('"'), e).toString(), e);
                }
            }
        }
    }

    protected static Revision fromGitLabCommit(Commit commit)
    {
        if (commit == null)
        {
            return null;
        }

        String id = commit.getId();
        String authorName = commit.getAuthorName();
        Instant authoredTimestamp = toInstantIfNotNull(commit.getAuthoredDate());
        String committerName = commit.getCommitterName();
        Instant committedTimestamp = toInstantIfNotNull(commit.getCommittedDate());
        String message = commit.getMessage();
        return new Revision()
        {
            @Override
            public String getId()
            {
                return id;
            }

            @Override
            public String getAuthorName()
            {
                return authorName;
            }

            @Override
            public Instant getAuthoredTimestamp()
            {
                return authoredTimestamp;
            }

            @Override
            public String getCommitterName()
            {
                return committerName;
            }

            @Override
            public Instant getCommittedTimestamp()
            {
                return committedTimestamp;
            }

            @Override
            public String getMessage()
            {
                return message;
            }
        };
    }
}
