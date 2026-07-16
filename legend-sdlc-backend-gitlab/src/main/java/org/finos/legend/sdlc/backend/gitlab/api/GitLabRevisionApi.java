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

package org.finos.legend.sdlc.backend.gitlab.api;

import org.finos.legend.sdlc.error.LegendSDLCException;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.utility.Iterate;
import org.eclipse.collections.impl.utility.LazyIterate;
import org.finos.legend.sdlc.domain.model.patch.Patch;
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.revision.RevisionStatus;
import org.finos.legend.sdlc.domain.model.version.Version;
import org.finos.legend.sdlc.project.source.SourceSpecification;
import org.finos.legend.sdlc.backend.api.revision.RevisionAccessContext;
import org.finos.legend.sdlc.backend.api.revision.RevisionApi;
import org.finos.legend.sdlc.project.workspace.WorkspaceSpecification;
import org.finos.legend.sdlc.backend.gitlab.GitLabConfiguration;
import org.finos.legend.sdlc.backend.gitlab.GitLabProjectId;
import org.finos.legend.sdlc.backend.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.backend.gitlab.tools.PagerTools;
import org.finos.legend.sdlc.project.structure.EntitySourceDirectory;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider;
import org.finos.legend.sdlc.project.files.ProjectPaths;
import org.finos.legend.sdlc.project.structure.ProjectStructure;
import org.finos.legend.sdlc.backend.api.tools.BackgroundTaskProcessor;
import org.finos.legend.sdlc.tools.entity.EntityPaths;
import org.gitlab4j.api.CommitsApi;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.TagsApi;
import org.gitlab4j.api.models.CommitRef;
import org.gitlab4j.api.models.CommitRef.RefType;
import org.gitlab4j.api.models.Tag;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class GitLabRevisionApi extends GitLabApiWithFileAccess implements RevisionApi
{
    public GitLabRevisionApi(GitLabConfiguration gitLabConfiguration, GitLabUserContext userContext, BackgroundTaskProcessor backgroundTaskProcessor)
    {
        super(gitLabConfiguration, userContext, backgroundTaskProcessor);
    }

    @Override
    public RevisionAccessContext getRevisionContext(String projectId, SourceSpecification sourceSpec)
    {
        LegendSDLCException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCException.validateNonNull(sourceSpec, "sourceSpec may not be null");
        return new ProjectFileRevisionAccessContextWrapper(getProjectFileAccessProvider().getRevisionAccessContext(projectId, sourceSpec));
    }

    @Override
    public RevisionAccessContext getPackageRevisionContext(String projectId, SourceSpecification sourceSpec, String packagePath)
    {
        LegendSDLCException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCException.validateNonNull(sourceSpec, "sourceSpec may not be null");
        LegendSDLCException.validateNonNull(packagePath, "packagePath may not be null");
        if (!EntityPaths.isValidPackagePath(packagePath))
        {
            throw new LegendSDLCException("Invalid package path: " + packagePath, 400);
        }
        ProjectStructure projectStructure = getProjectStructure(projectId, sourceSpec, null);
        MutableList<String> directories = Iterate.collectWith(projectStructure.getEntitySourceDirectories(), EntitySourceDirectory::packagePathToFilePath, packagePath, Lists.mutable.empty());
        MutableList<String> canonicalizedAndReducedDirectories = ProjectPaths.canonicalizeAndReduceDirectories(directories);
        return new ProjectFileRevisionAccessContextWrapper(getProjectFileAccessProvider().getRevisionAccessContext(projectId, sourceSpec, canonicalizedAndReducedDirectories), new PackageablePathExceptionProcessor(packagePath, canonicalizedAndReducedDirectories));
    }

    @Override
    public RevisionAccessContext getEntityRevisionContext(String projectId, SourceSpecification sourceSpec, String entityPath)
    {
        LegendSDLCException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCException.validateNonNull(sourceSpec, "sourceSpec may not be null");
        LegendSDLCException.validateNonNull(entityPath, "entityPath may not be null");
        if (!EntityPaths.isValidEntityPath(entityPath))
        {
            throw new LegendSDLCException("Invalid entity path: " + entityPath, 400);
        }
        ProjectFileAccessProvider fileAccessProvider = getProjectFileAccessProvider();
        ProjectFileAccessProvider.FileAccessContext fileAccessContext = fileAccessProvider.getFileAccessContext(projectId, sourceSpec, null);
        ProjectStructure projectStructure = ProjectStructure.getProjectStructure(fileAccessContext);
        String filePath = projectStructure.findEntityFile(entityPath, fileAccessContext);
        if (filePath == null)
        {
            throw new LegendSDLCException("Cannot find entity \"" + entityPath + "\" in " + getReferenceInfo(projectId, sourceSpec), 404);
        }
        String canonicalFilePath = ProjectPaths.canonicalizeFile(filePath);
        return new ProjectFileRevisionAccessContextWrapper(fileAccessProvider.getRevisionAccessContext(projectId, sourceSpec, Collections.singleton(canonicalFilePath)), new PackageablePathExceptionProcessor(entityPath, canonicalFilePath));
    }

    @Override
    public RevisionStatus getRevisionStatus(String projectId, String revisionId)
    {
        LegendSDLCException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCException.validateNonNull(revisionId, "revisionId may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        try
        {
            GitLabApi gitLabApi = getGitLabApi();
            CommitsApi commitsApi = gitLabApi.getCommitsApi();
            Revision revision = getRevisionContext(projectId, SourceSpecification.projectSourceSpecification()).getRevision(revisionId);

            MutableList<CommitRef> commitRefs = PagerTools.stream(withRetries(() -> commitsApi.getCommitRefs(gitLabProjectId.getGitLabId(), revision.getId(), RefType.ALL, ITEMS_PER_PAGE)))
                    .collect(Collectors.toCollection(Lists.mutable::empty));

            String defaultBranch = getDefaultBranch(gitLabProjectId);
            boolean isCommitted = commitRefs.anySatisfy(cr -> defaultBranch.equals(cr.getName()));

            List<Version> versions;
            MutableList<String> versionTagNames = commitRefs.collectIf(cr -> (RefType.TAG == cr.getType()) && isVersionTagName(cr.getName()), CommitRef::getName);
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

            List<Workspace> workspaces = Lists.mutable.empty();
            List<Patch> patches = Lists.mutable.empty();
            if (!isCommitted)
            {
                // Note that here we will not account for conflict resolution or backup branch because in the model those are not real workspaces.
                String patchReleaseBranchPrefix = getPatchReleaseBranchPrefix();
                commitRefs.forEach(cr ->
                {
                    if (cr.getType() != RefType.BRANCH)
                    {
                        return;
                    }

                    // patch branch
                    if (branchNameStartsWith(cr.getName(), patchReleaseBranchPrefix))
                    {
                        patches.add(fromPatchBranchName(projectId, cr.getName()));
                        return;
                    }

                    // workspace branch
                    WorkspaceSpecification workspaceSpec = parseWorkspaceBranchName(cr.getName());
                    if ((workspaceSpec != null) && (workspaceSpec.getAccessType() == ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE))
                    {
                        workspaces.add(fromWorkspaceSpecification(projectId, workspaceSpec));
                    }
                });
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

                @Override
                public List<Patch> getPatches()
                {
                    return patches;
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

    private static class PackageablePathExceptionProcessor implements Function<LegendSDLCException, LegendSDLCException>
    {
        private final String packageablePath;
        private final ListIterable<String> filePaths;

        private PackageablePathExceptionProcessor(String packageablePath, ListIterable<String> filePaths)
        {
            this.packageablePath = packageablePath;
            this.filePaths = filePaths;
        }

        private PackageablePathExceptionProcessor(String packageablePath, String filePath)
        {
            this(packageablePath, Lists.immutable.with(filePath));
        }

        @Override
        public LegendSDLCException apply(LegendSDLCException e)
        {
            String message = e.getMessage();
            ListIterable<String> found = this.filePaths.select(message::contains);
            if (found.size() == 1)
            {
                String newMessage = message.replace(found.get(0), this.packageablePath);
                if (!newMessage.equals(message))
                {
                    return new LegendSDLCException(newMessage, e.getStatusCode(), e);
                }
            }
            else if (found.notEmpty())
            {
                String anyFoundPattern = LazyIterate.collect(found, Pattern::quote).makeString("((", ")|(", "))");
                String patternString = "\\{?+(" + anyFoundPattern + ",\\s*+)*+" + anyFoundPattern + "}?+";
                String newMessage = message.replaceAll(patternString, this.packageablePath);
                if (!newMessage.equals(message))
                {
                    return new LegendSDLCException(newMessage, e.getStatusCode(), e);
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
        private final Function<? super LegendSDLCException, ? extends LegendSDLCException> exceptionProcessor;

        private ProjectFileRevisionAccessContextWrapper(ProjectFileAccessProvider.RevisionAccessContext revisionAccessContext, Function<? super LegendSDLCException, ? extends LegendSDLCException> exceptionProcessor)
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
            catch (LegendSDLCException e)
            {
                if (this.exceptionProcessor != null)
                {
                    LegendSDLCException processedException = this.exceptionProcessor.apply(e);
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
            catch (LegendSDLCException e)
            {
                if (this.exceptionProcessor != null)
                {
                    LegendSDLCException processedException = this.exceptionProcessor.apply(e);
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
            catch (LegendSDLCException e)
            {
                if (this.exceptionProcessor != null)
                {
                    LegendSDLCException processedException = this.exceptionProcessor.apply(e);
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
            catch (LegendSDLCException e)
            {
                if (this.exceptionProcessor != null)
                {
                    LegendSDLCException processedException = this.exceptionProcessor.apply(e);
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
