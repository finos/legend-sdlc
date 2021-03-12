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

import org.eclipse.collections.api.factory.Lists;
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.revision.RevisionStatus;
import org.finos.legend.sdlc.domain.model.version.Version;
import org.finos.legend.sdlc.server.domain.api.revision.RevisionAccessContext;
import org.finos.legend.sdlc.server.domain.api.revision.RevisionApi;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.GitLabProjectId;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.gitlab.tools.PagerTools;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.tools.BackgroundTaskProcessor;
import org.gitlab4j.api.CommitsApi;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.Pager;
import org.gitlab4j.api.TagsApi;
import org.gitlab4j.api.models.CommitRef;
import org.gitlab4j.api.models.CommitRef.RefType;
import org.gitlab4j.api.models.Tag;

import javax.inject.Inject;
import javax.ws.rs.core.Response.Status;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class GitLabRevisionApi extends GitLabApiWithFileAccess implements RevisionApi
{
    @Inject
    public GitLabRevisionApi(GitLabUserContext userContext, BackgroundTaskProcessor backgroundTaskProcessor)
    {
        super(userContext, backgroundTaskProcessor);
    }

    @Override
    public RevisionAccessContext getProjectRevisionContext(String projectId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        return new ProjectFileRevisionAccessContextWrapper(getProjectFileAccessProvider().getRevisionAccessContext(projectId, null, null, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE));
    }

    @Override
    public RevisionAccessContext getProjectEntityRevisionContext(String projectId, String entityPath)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(entityPath, "entityPath may not be null");
        if (!isValidEntityPath(entityPath))
        {
            throw new LegendSDLCServerException("Invalid entity path: " + entityPath, Status.BAD_REQUEST);
        }
        String filePath = getProjectStructure(projectId, null, null, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE).entityPathToFilePath(entityPath);
        return new ProjectFileRevisionAccessContextWrapper(getProjectFileAccessProvider().getRevisionAccessContext(projectId, null, filePath, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), new PackageablePathExceptionProcessor(entityPath, filePath));
    }

    @Override
    public RevisionAccessContext getProjectPackageRevisionContext(String projectId, String packagePath)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(packagePath, "packagePath may not be null");
        if (!isValidPackagePath(packagePath))
        {
            throw new LegendSDLCServerException("Invalid package path: " + packagePath, Status.BAD_REQUEST);
        }
        String filePath = getProjectStructure(projectId, null, null, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE).packagePathToFilePath(packagePath);
        return new ProjectFileRevisionAccessContextWrapper(getProjectFileAccessProvider().getRevisionAccessContext(projectId, null, filePath, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), new PackageablePathExceptionProcessor(packagePath, filePath));
    }

    @Override
    public RevisionAccessContext getWorkspaceRevisionContext(String projectId, String workspaceId)
    {
        return this.getWorkspaceRevisionContextByWorkspaceAccessType(projectId, workspaceId, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE);
    }

    @Override
    public RevisionAccessContext getBackupWorkspaceRevisionContext(String projectId, String workspaceId)
    {
        return this.getWorkspaceRevisionContextByWorkspaceAccessType(projectId, workspaceId, ProjectFileAccessProvider.WorkspaceAccessType.BACKUP);
    }

    @Override
    public RevisionAccessContext getWorkspaceWithConflictResolutionRevisionContext(String projectId, String workspaceId)
    {
        return this.getWorkspaceRevisionContextByWorkspaceAccessType(projectId, workspaceId, ProjectFileAccessProvider.WorkspaceAccessType.CONFLICT_RESOLUTION);
    }

    private RevisionAccessContext getWorkspaceRevisionContextByWorkspaceAccessType(String projectId, String workspaceId, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceId, "workspaceId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceAccessType, "workspaceAccessType may not be null");
        return new ProjectFileRevisionAccessContextWrapper(getProjectFileAccessProvider().getRevisionAccessContext(projectId, workspaceId, null, workspaceAccessType));
    }

    @Override
    public RevisionAccessContext getWorkspaceEntityRevisionContext(String projectId, String workspaceId, String entityPath)
    {
        return this.getWorkspaceEntityRevisionContextByWorkspaceAccessType(projectId, workspaceId, entityPath, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE);
    }

    private RevisionAccessContext getWorkspaceEntityRevisionContextByWorkspaceAccessType(String projectId, String workspaceId, String entityPath, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceId, "workspaceId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceAccessType, "workspaceAccessType may not be null");
        LegendSDLCServerException.validateNonNull(entityPath, "entityPath may not be null");
        if (!isValidEntityPath(entityPath))
        {
            throw new LegendSDLCServerException("Invalid entity path: " + entityPath, Status.BAD_REQUEST);
        }
        String filePath = getProjectStructure(projectId, workspaceId, null, workspaceAccessType).entityPathToFilePath(entityPath);
        return new ProjectFileRevisionAccessContextWrapper(getProjectFileAccessProvider().getRevisionAccessContext(projectId, workspaceId, filePath, workspaceAccessType), new PackageablePathExceptionProcessor(entityPath, filePath));
    }

    @Override
    public RevisionAccessContext getWorkspacePackageRevisionContext(String projectId, String workspaceId, String packagePath)
    {
        return this.getWorkspacePackageRevisionContextByWorkspaceAccessType(projectId, workspaceId, packagePath, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE);
    }

    private RevisionAccessContext getWorkspacePackageRevisionContextByWorkspaceAccessType(String projectId, String workspaceId, String packagePath, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceId, "workspaceId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceAccessType, "workspaceAccessType may not be null");
        LegendSDLCServerException.validateNonNull(packagePath, "packagePath may not be null");
        if (!isValidPackagePath(packagePath))
        {
            throw new LegendSDLCServerException("Invalid package path: " + packagePath, Status.BAD_REQUEST);
        }
        String filePath = getProjectStructure(projectId, workspaceId, null, workspaceAccessType).packagePathToFilePath(packagePath);
        return new ProjectFileRevisionAccessContextWrapper(getProjectFileAccessProvider().getRevisionAccessContext(projectId, workspaceId, filePath, workspaceAccessType), new PackageablePathExceptionProcessor(packagePath, filePath));
    }

    @Override
    public RevisionStatus getRevisionStatus(String projectId, String revisionId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(revisionId, "revisionId may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        try
        {
            GitLabApi gitLabApi = getGitLabApi(gitLabProjectId.getGitLabMode());
            CommitsApi commitsApi = gitLabApi.getCommitsApi();
            Revision revision = getProjectRevisionContext(projectId).getRevision(revisionId);

            Pager<CommitRef> commitRefPager = withRetries(() -> commitsApi.getCommitRefs(gitLabProjectId.getGitLabId(), revision.getId(), RefType.ALL, ITEMS_PER_PAGE));
            List<CommitRef> commitRefs = PagerTools.stream(commitRefPager).collect(Collectors.toList());
            boolean isCommitted = commitRefs.stream().anyMatch(cr -> MASTER_BRANCH.equals(cr.getName()));

            List<Version> versions;
            List<String> versionTagNames = commitRefs.stream()
                    .filter(cr -> (RefType.TAG == cr.getType()) && isVersionTagName(cr.getName()))
                    .map(CommitRef::getName)
                    .collect(Collectors.toList());
            if (versionTagNames.isEmpty())
            {
                versions = Collections.emptyList();
            }
            else
            {
                TagsApi tagsApi = gitLabApi.getTagsApi();
                versions = Lists.mutable.ofInitialCapacity(versionTagNames.size());
                for (String tagName : versionTagNames)
                {
                    Tag tag = withRetries(() -> tagsApi.getTag(gitLabProjectId.getGitLabId(), tagName));
                    versions.add(fromGitLabTag(projectId, tag));
                }
                versions.sort(Comparator.comparing(Version::getId));
            }

            List<Workspace> workspaces;
            if (isCommitted)
            {
                workspaces = Collections.emptyList();
            }
            else
            {
                // Note that here we will not account for conflict resolution or backup branch because in the model those are not real workspaces.
                workspaces = commitRefs.stream()
                        .filter(cr -> (RefType.BRANCH == cr.getType()) && isWorkspaceBranchName(cr.getName(), ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE))
                        .map(cr -> fromWorkspaceBranchName(projectId, cr.getName(), ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE))
                        .collect(Collectors.toList());
            }

            return new RevisionStatus()
            {
                @Override
                public Revision getRevision()
                {
                    return revision;
                }

                @Override
                public boolean isCommitted()
                {
                    return isCommitted;
                }

                @Override
                public List<Workspace> getWorkspaces()
                {
                    return workspaces;
                }

                @Override
                public List<Version> getVersions()
                {
                    return versions;
                }
            };
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to access the status for revision " + revisionId + " of project " + projectId,
                    () -> "Unknown: revision " + revisionId + " of project " + projectId,
                    () -> "Error getting the status for revision " + revisionId + " of project " + projectId);
        }
    }

    private static class PackageablePathExceptionProcessor implements Function<LegendSDLCServerException, LegendSDLCServerException>
    {
        private final String packageablePath;
        private final String filePath;

        private PackageablePathExceptionProcessor(String packageablePath, String filePath)
        {
            this.packageablePath = packageablePath;
            this.filePath = filePath;
        }

        @Override
        public LegendSDLCServerException apply(LegendSDLCServerException e)
        {
            String message = e.getMessage();
            if (message.contains(this.filePath))
            {
                String newMessage = message.replace(this.filePath, this.packageablePath);
                if (!newMessage.equals(message))
                {
                    throw new LegendSDLCServerException(newMessage, e.getStatus(), e);
                }
            }
            return e;
        }
    }

    /**
     * This is a wrapper around ProjectFileAccessProvider.RevisionAccessContext. It basically directly calls all
     * methods of ProjectFileAccessProvider.RevisionAccessContext. But since that deals with files in Gitlab
     * and in error messages, we want to report entity path, this wrapper will further process the exception thrown
     * by methods in ProjectFileAccessProvider.RevisionAccessContext to show entity path instead.
     */
    private static class ProjectFileRevisionAccessContextWrapper implements RevisionAccessContext
    {
        private final ProjectFileAccessProvider.RevisionAccessContext revisionAccessContext;
        private final Function<? super LegendSDLCServerException, ? extends LegendSDLCServerException> exceptionProcessor;

        private ProjectFileRevisionAccessContextWrapper(ProjectFileAccessProvider.RevisionAccessContext revisionAccessContext, Function<? super LegendSDLCServerException, ? extends LegendSDLCServerException> exceptionProcessor)
        {
            this.revisionAccessContext = revisionAccessContext;
            this.exceptionProcessor = exceptionProcessor;
        }

        private ProjectFileRevisionAccessContextWrapper(ProjectFileAccessProvider.RevisionAccessContext revisionAccessContext)
        {
            this(revisionAccessContext, null);
        }

        @Override
        public Revision getRevision(String revisionId)
        {
            try
            {
                return this.revisionAccessContext.getRevision(revisionId);
            }
            catch (LegendSDLCServerException e)
            {
                if (this.exceptionProcessor != null)
                {
                    LegendSDLCServerException processedException = this.exceptionProcessor.apply(e);
                    if (processedException != null)
                    {
                        throw processedException;
                    }
                }
                throw e;
            }
        }

        @Override
        public Revision getBaseRevision()
        {
            try
            {
                return this.revisionAccessContext.getBaseRevision();
            }
            catch (LegendSDLCServerException e)
            {
                if (this.exceptionProcessor != null)
                {
                    LegendSDLCServerException processedException = this.exceptionProcessor.apply(e);
                    if (processedException != null)
                    {
                        throw processedException;
                    }
                }
                throw e;
            }
        }

        @Override
        public Revision getCurrentRevision()
        {
            try
            {
                return this.revisionAccessContext.getCurrentRevision();
            }
            catch (LegendSDLCServerException e)
            {
                if (this.exceptionProcessor != null)
                {
                    LegendSDLCServerException processedException = this.exceptionProcessor.apply(e);
                    if (processedException != null)
                    {
                        throw processedException;
                    }
                }
                throw e;
            }
        }

        @Override
        public List<Revision> getRevisions(Predicate<? super Revision> predicate, Instant since, Instant until, Integer limit)
        {
            try
            {
                return this.revisionAccessContext.getAllRevisions(predicate, since, until, limit).collect(Collectors.toList());
            }
            catch (LegendSDLCServerException e)
            {
                if (this.exceptionProcessor != null)
                {
                    LegendSDLCServerException processedException = this.exceptionProcessor.apply(e);
                    if (processedException != null)
                    {
                        throw processedException;
                    }
                }
                throw e;
            }
        }
    }
}
