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
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.server.domain.api.revision.RevisionApi;
import org.finos.legend.sdlc.server.domain.api.workspace.PatchWorkspaceSource;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceApi;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSource;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSourceConsumer;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSpecification;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.GitLabConfiguration;
import org.finos.legend.sdlc.server.gitlab.GitLabProjectId;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.gitlab.tools.GitLabApiTools;
import org.finos.legend.sdlc.server.gitlab.tools.PagerTools;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider.WorkspaceAccessType;
import org.finos.legend.sdlc.server.tools.BackgroundTaskProcessor;
import org.finos.legend.sdlc.server.tools.CallUntil;
import org.gitlab4j.api.CommitsApi;
import org.gitlab4j.api.Constants;
import org.gitlab4j.api.Constants.StateEvent;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.MergeRequestApi;
import org.gitlab4j.api.Pager;
import org.gitlab4j.api.RepositoryApi;
import org.gitlab4j.api.models.Branch;
import org.gitlab4j.api.models.Commit;
import org.gitlab4j.api.models.CommitAction;
import org.gitlab4j.api.models.CommitRef;
import org.gitlab4j.api.models.CommitRef.RefType;
import org.gitlab4j.api.models.CompareResults;
import org.gitlab4j.api.models.Diff;
import org.gitlab4j.api.models.MergeRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

public class GitLabWorkspaceApi extends GitLabApiWithFileAccess implements WorkspaceApi
{
    private static final Logger LOGGER = LoggerFactory.getLogger(GitLabWorkspaceApi.class);

    private final RevisionApi revisionApi;

    @Inject
    public GitLabWorkspaceApi(GitLabConfiguration gitLabConfiguration, GitLabUserContext userContext, RevisionApi revisionApi, BackgroundTaskProcessor backgroundTaskProcessor)
    {
        super(gitLabConfiguration, userContext, backgroundTaskProcessor);
        this.revisionApi = revisionApi;
    }

    @Override
    public Workspace getWorkspace(String projectId, WorkspaceSpecification workspaceSpecification)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceSpecification, "workspace specification may not be null");

        try
        {
            GitLabProjectId gitLabProjectId = parseProjectId(projectId);
            String branchName = getWorkspaceBranchName(workspaceSpecification);
            RepositoryApi repositoryApi = getGitLabApi().getRepositoryApi();
            Branch branch = withRetries(() -> repositoryApi.getBranch(gitLabProjectId.getGitLabId(), branchName));
            return fromWorkspaceBranchName(projectId, branch.getName());
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to get " + getReferenceInfo(projectId, workspaceSpecification),
                    () -> "Unknown: " + getReferenceInfo(projectId, workspaceSpecification),
                    () -> "Error getting " + getReferenceInfo(projectId, workspaceSpecification));
        }
    }

    @Override
    public List<Workspace> getWorkspaces(String projectId, Set<WorkspaceType> types, Set<WorkspaceAccessType> accessTypes, Set<WorkspaceSource> sources)
    {
        return getWorkspaces(projectId, types, accessTypes, sources, getCurrentUser());
    }

    @Override
    public List<Workspace> getAllWorkspaces(String projectId, Set<WorkspaceType> types, Set<WorkspaceAccessType> accessTypes, Set<WorkspaceSource> sources)
    {
        return getWorkspaces(projectId, types, accessTypes, sources, null);
    }

    private List<Workspace> getWorkspaces(String projectId, Set<WorkspaceType> types, Set<WorkspaceAccessType> accessTypes, Set<WorkspaceSource> sources, String userId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        GitLabProjectId gitLabProjectId = parseProjectId(projectId);

        Set<WorkspaceType> resolvedTypes = (types == null) ? EnumSet.allOf(WorkspaceType.class) : types;
        Set<WorkspaceAccessType> resolvedAccessTypes = (accessTypes == null) ? EnumSet.allOf(WorkspaceAccessType.class) : accessTypes;

        try
        {
            Stream<WorkspaceSpecification> stream = null;
            if (sources == null)
            {
                stream = getAllPatchWorkspaces(gitLabProjectId, types, accessTypes, userId);
            }
            else
            {
                for (WorkspaceType type : resolvedTypes)
                {
                    for (WorkspaceAccessType accessType : resolvedAccessTypes)
                    {
                        for (WorkspaceSource source : sources)
                        {
                            Stream<WorkspaceSpecification> localStream = getWorkspaces(gitLabProjectId, type, accessType, source, userId);
                            stream = (stream == null) ? localStream : Stream.concat(stream, localStream);
                        }
                    }
                }
            }
            if (stream == null)
            {
                // If stream is null, then types, accessTypes, or sources is empty, which means the result must be empty.
                // However, we still should verify that the project exists and that the user is allowed to access branches.
                // The following request should result in an error if either the project does not exist or the user is not
                // allowed to access branches.
                withRetries(() -> getGitLabApi().getRepositoryApi().getBranches(gitLabProjectId.getGitLabId(), 1));
                return Collections.emptyList();
            }
            String prIdStr = gitLabProjectId.toString();
            return stream.map(ws -> fromWorkspaceSpecification(prIdStr, ws)).collect(Collectors.toList());
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to get workspaces for project " + projectId,
                    () -> "Unknown project: " + projectId,
                    () -> "Error getting workspaces for project " + projectId);
        }
    }

    private Stream<WorkspaceSpecification> getAllPatchWorkspaces(GitLabProjectId projectId, Set<WorkspaceType> types, Set<WorkspaceAccessType> accessTypes, String userId) throws GitLabApiException
    {
        String branchPrefix = getPatchWorkspaceBranchPrefix();
        RepositoryApi repositoryApi = getGitLabApi().getRepositoryApi();
        Pager<Branch> pager = withRetries(() -> repositoryApi.getBranches(projectId.getGitLabId(), "^" + branchPrefix, ITEMS_PER_PAGE));
        Stream<WorkspaceSpecification> stream = PagerTools.stream(pager)
                .map(Branch::getName)
                .filter(n -> (n != null) && n.startsWith(branchPrefix))
                .map(GitLabWorkspaceApi::parseWorkspaceBranchName)
                .filter(Objects::nonNull);
        if (types != null)
        {
            stream = stream.filter(ws -> types.contains(ws.getType()));
        }
        if (accessTypes != null)
        {
            stream = stream.filter(ws -> accessTypes.contains(ws.getAccessType()));
        }
        if (userId != null)
        {
            stream = stream.filter(ws -> userId.equals(ws.getUserId()));
        }
        return stream;
    }

    private Stream<WorkspaceSpecification> getWorkspaces(GitLabProjectId projectId, WorkspaceType type, WorkspaceAccessType accessType, WorkspaceSource source, String userId) throws GitLabApiException
    {
        String branchPrefix = getBranchSearchPrefix(type, accessType, source, userId);

        RepositoryApi repositoryApi = getGitLabApi().getRepositoryApi();
        Pager<Branch> pager = withRetries(() -> repositoryApi.getBranches(projectId.getGitLabId(), "^" + branchPrefix, ITEMS_PER_PAGE));
        return PagerTools.stream(pager)
                .map(Branch::getName)
                .filter(n -> (n != null) && n.startsWith(branchPrefix))
                .map(GitLabWorkspaceApi::parseWorkspaceBranchName)
                .filter(Objects::nonNull)
                .filter(spec -> (spec.getType() == type) && (spec.getAccessType() == accessType) && source.equals(spec.getSource()));
    }

    private String getBranchSearchPrefix(WorkspaceType type, WorkspaceAccessType accessType, WorkspaceSource source, String userId)
    {
        if (type == WorkspaceType.USER)
        {
            return (userId == null) ?
                    getWorkspaceBranchNamePrefix(WorkspaceSpecification.newWorkspaceSpecification("", type, accessType, source)) :
                    getWorkspaceBranchName(WorkspaceSpecification.newWorkspaceSpecification("", type, accessType, source, userId));
        }
        return getWorkspaceBranchName(WorkspaceSpecification.newWorkspaceSpecification("", type, accessType, source));
    }

    @Override
    public boolean isWorkspaceOutdated(String projectId, WorkspaceSpecification workspaceSpecification)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceSpecification, "workspace specification may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        String workspaceBranchName = getWorkspaceBranchName(workspaceSpecification);
        GitLabApi gitLabApi = getGitLabApi();
        RepositoryApi repositoryApi = gitLabApi.getRepositoryApi();

        // Get the workspace branch
        Branch workspaceBranch;
        try
        {
            workspaceBranch = withRetries(() -> repositoryApi.getBranch(gitLabProjectId.getGitLabId(), workspaceBranchName));
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to access " + getReferenceInfo(projectId, workspaceSpecification),
                    () -> "Unknown: " + getReferenceInfo(projectId, workspaceSpecification),
                    () -> "Error accessing " + getReferenceInfo(projectId, workspaceSpecification));
        }
        String workspaceRevisionId = workspaceBranch.getCommit().getId();

        // Get source branch
        String sourceBranchName = getSourceBranch(gitLabProjectId, workspaceSpecification);
        Branch sourceBranch;
        try
        {
            sourceBranch = withRetries(() -> repositoryApi.getBranch(gitLabProjectId.getGitLabId(), sourceBranchName));
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to access the latest revision in " + getReferenceInfo(projectId, workspaceSpecification.getSource()),
                    () -> "Unknown: " + getReferenceInfo(projectId, workspaceSpecification.getSource()),
                    () -> "Error accessing latest revision for " + getReferenceInfo(projectId, workspaceSpecification.getSource()));
        }
        String sourceBranchRevisionId = sourceBranch.getCommit().getId();

        // Check if the workspace does not have the latest revision of the project, i.e. it is outdated
        if (sourceBranchRevisionId.equals(workspaceRevisionId))
        {
            return false;
        }

        CommitsApi commitsApi = gitLabApi.getCommitsApi();
        try
        {
            Pager<CommitRef> sourceCommitRefsPager = withRetries(() -> commitsApi.getCommitRefs(gitLabProjectId.getGitLabId(), sourceBranchRevisionId, RefType.BRANCH, ITEMS_PER_PAGE));
            Stream<CommitRef> sourceCommitRefs = PagerTools.stream(sourceCommitRefsPager);
            // This will check if the branch contains the master HEAD commit by looking up the list of references the commit is pushed to
            return sourceCommitRefs.noneMatch(cr -> workspaceBranchName.equals(cr.getName()));
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to check if " + getReferenceInfo(projectId, workspaceSpecification) + " is outdated",
                    () -> "Unknown revision (" + sourceBranchRevisionId + "), or " + getReferenceInfo(projectId, workspaceSpecification),
                    () -> "Error checking if " + getReferenceInfo(projectId, workspaceSpecification) + " is outdated");
        }
    }

    @Override
    public boolean isWorkspaceInConflictResolutionMode(String projectId, WorkspaceSpecification workspaceSpecification)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceSpecification, "workspace specification may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        RepositoryApi repositoryApi = getGitLabApi().getRepositoryApi();

        String workspaceBranchName = getWorkspaceBranchName(workspaceSpecification);
        try
        {
            withRetries(() -> repositoryApi.getBranch(gitLabProjectId.getGitLabId(), workspaceBranchName));
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to access " + getReferenceInfo(projectId, workspaceSpecification),
                    () -> "Unknown: " + getReferenceInfo(projectId, workspaceSpecification),
                    () -> "Error accessing " + getReferenceInfo(projectId, workspaceSpecification));
        }

        if (workspaceSpecification.getAccessType() != WorkspaceAccessType.WORKSPACE)
        {
            return false;
        }

        WorkspaceSpecification conflictWorkspaceSpec = WorkspaceSpecification.newWorkspaceSpecification(workspaceSpecification.getId(), workspaceSpecification.getType(), WorkspaceAccessType.CONFLICT_RESOLUTION, workspaceSpecification.getSource(), workspaceSpecification.getUserId());
        String conflictBranchName = getWorkspaceBranchName(conflictWorkspaceSpec);
        try
        {
            return GitLabApiTools.branchExists(repositoryApi, gitLabProjectId.getGitLabId(), conflictBranchName);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to access " + getReferenceInfo(projectId, conflictWorkspaceSpec),
                    () -> "Unknown: " + getReferenceInfo(projectId, conflictWorkspaceSpec),
                    () -> "Error accessing " + getReferenceInfo(projectId, conflictWorkspaceSpec));
        }
    }

    @Override
    public Workspace newWorkspace(String projectId, String workspaceId, WorkspaceType type, WorkspaceSource source)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceId, "workspace id may not be null");
        LegendSDLCServerException.validateNonNull(type, "workspace type may not be null");
        LegendSDLCServerException.validateNonNull(source, "workspace source may not be null");

        validateWorkspaceId(workspaceId);
        WorkspaceSpecification workspaceSpecification = WorkspaceSpecification.newWorkspaceSpecification(workspaceId, type, WorkspaceAccessType.WORKSPACE, source);
        if (getProjectConfiguration(projectId, workspaceSpecification.getSourceSpecification()) == null)
        {
            throw new LegendSDLCServerException("Project structure has not been set up", Status.CONFLICT);
        }

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        RepositoryApi repositoryApi = getGitLabApi().getRepositoryApi();

        // check if the source branch exists or not
        workspaceSpecification.getSource().visit(new WorkspaceSourceConsumer()
        {
            @Override
            protected void accept(PatchWorkspaceSource source)
            {
                if (!isPatchReleaseBranchPresent(gitLabProjectId, source.getPatchVersionId()))
                {
                    throw new LegendSDLCServerException("Patch release branch for " + source.getPatchVersionId() + " doesn't exist", Response.Status.BAD_REQUEST);
                }
            }
        });

        // Delete backup workspace with the same name if exists
        String backupBranchName = getWorkspaceBranchName(WorkspaceSpecification.newWorkspaceSpecification(workspaceId, type, WorkspaceAccessType.BACKUP, source));
        try
        {
            if (GitLabApiTools.branchExists(repositoryApi, gitLabProjectId.getGitLabId(), backupBranchName))
            {
                LOGGER.debug("Cleaning up left-over backup branch {} in project {}", backupBranchName, projectId);
                boolean deleted = GitLabApiTools.deleteBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(), backupBranchName, 20, 1_000);
                if (!deleted)
                {
                    LOGGER.error("Failed to delete backup branch {} in project {}", backupBranchName, projectId);
                }
            }
        }
        catch (Exception e)
        {
            LOGGER.error("Error cleaning up backup branch {} in project {}", backupBranchName, projectId, e);
        }

        // Delete workspace with conflict resolution with the same name if exists
        String conflictResolutionBranchName = getWorkspaceBranchName(WorkspaceSpecification.newWorkspaceSpecification(workspaceId, type, WorkspaceAccessType.CONFLICT_RESOLUTION, source));
        try
        {
            if (GitLabApiTools.branchExists(repositoryApi, gitLabProjectId.getGitLabId(), conflictResolutionBranchName))
            {
                LOGGER.debug("Cleaning up left-over conflict resolution branch {} in project {}", conflictResolutionBranchName, projectId);
                boolean deleted = GitLabApiTools.deleteBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(), conflictResolutionBranchName, 20, 1_000);
                if (!deleted)
                {
                    LOGGER.error("Failed to delete conflict resolution branch {} in project {}", conflictResolutionBranchName, projectId);
                }
            }
        }
        catch (Exception e)
        {
            LOGGER.error("Error cleaning up conflict resolution branch {} in project {}", conflictResolutionBranchName, projectId, e);
        }

        // Create new workspace
        String workspaceBranchName = getWorkspaceBranchName(workspaceSpecification);
        String sourceBranchName = getSourceBranch(gitLabProjectId, workspaceSpecification);
        Branch branch;
        try
        {
            branch = GitLabApiTools.createBranchFromSourceBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(), workspaceBranchName, sourceBranchName, 30, 1_000);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to create " + getReferenceInfo(projectId, workspaceSpecification),
                    () -> "Unknown project: " + projectId,
                    () -> "Error creating " + getReferenceInfo(projectId, workspaceSpecification));
        }
        if (branch == null)
        {
            throw new LegendSDLCServerException("Failed to create " + getReferenceInfo(projectId, workspaceSpecification));
        }
        return fromWorkspaceBranchName(projectId, branch.getName());
    }

    /**
     * When we delete a workspace, we also need to remember to delete the conflict resolution and backup workspaces
     */
    @Override
    public void deleteWorkspace(String projectId, WorkspaceSpecification workspaceSpecification)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceSpecification, "workspace specification may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        RepositoryApi repositoryApi = getGitLabApi().getRepositoryApi();

        // Delete workspace
        String workspaceBranchName = getWorkspaceBranchName(workspaceSpecification);
        boolean workspaceDeleted;
        try
        {
            workspaceDeleted = GitLabApiTools.deleteBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(), workspaceBranchName, 20, 1_000);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to delete " + getReferenceInfo(projectId, workspaceSpecification),
                    () -> "Unknown: " + getReferenceInfo(projectId, workspaceSpecification),
                    () -> "Error deleting " + getReferenceInfo(projectId, workspaceSpecification));
        }
        if (!workspaceDeleted)
        {
            throw new LegendSDLCServerException("Failed to delete " + getReferenceInfo(projectId, workspaceSpecification));
        }

        // Delete conflict resolution workspace
        String conflictResolutionBranchName = getWorkspaceBranchName(WorkspaceSpecification.newWorkspaceSpecification(workspaceSpecification.getId(), workspaceSpecification.getType(), WorkspaceAccessType.CONFLICT_RESOLUTION, workspaceSpecification.getSource()));
        try
        {
            boolean deleted = GitLabApiTools.deleteBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(), conflictResolutionBranchName, 20, 1_000);
            if (!deleted)
            {
                LOGGER.error("Failed to delete {} in project {}", conflictResolutionBranchName, projectId);
            }
        }
        catch (Exception e)
        {
            // unfortunate, but this should not throw error
            LOGGER.error("Error deleting {} in project {}", conflictResolutionBranchName, projectId, e);
        }

        // Delete backup workspace
        String backupBranchName = getWorkspaceBranchName(WorkspaceSpecification.newWorkspaceSpecification(workspaceSpecification.getId(), workspaceSpecification.getType(), WorkspaceAccessType.BACKUP, workspaceSpecification.getSource()));
        try
        {
            boolean deleted = GitLabApiTools.deleteBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(), backupBranchName, 20, 1_000);
            if (!deleted)
            {
                LOGGER.error("Failed to delete {} in project {}", backupBranchName, projectId);
            }
        }
        catch (Exception e)
        {
            // unfortunate, but this should not throw error
            LOGGER.error("Error deleting {} in project {}", backupBranchName, projectId, e);
        }
    }

    /**
     * There are 4 possible outcome for this method:
     * 1. NO_OP: If the workspace is already branching from the HEAD of master, nothing is needed.
     * 2. UPDATED: If the workspace is not branching from the HEAD of master, and we successfully rebase the branch to master HEAD.
     * 3. CONFLICT: If the workspace is not branching from the HEAD of master, and we failed to rebase the branch to master HEAD due to merge conflicts.
     * 4. ERROR
     * <p>
     * The procedure goes like the followings:
     * 1. Check if the current workspace is already up to date:
     * - If yes, return NO_OP
     * - If no, proceed
     * 2. Create a temp branch to attempt to rebase:
     * - If rebase succeeded, return UPDATED
     * - If rebase failed, further check if we need to enter conflict resolution mode.
     * This check makes sure the conflict that causes rebase to fail does not come from intermediate
     * commits by squashing these commits and attempt to do another rebase there. If this still fails
     * it means the workspace in overall truly has merge conflicts while updating, so entering conflict resolution mode
     */
    @Override
    public WorkspaceUpdateReport updateWorkspace(String projectId, WorkspaceSpecification workspaceSpecification)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceSpecification, "workspace specification may not be null");

        LOGGER.debug("Updating workspace {} in project {} to latest revision", workspaceSpecification, projectId);
        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        GitLabApi gitLabApi = getGitLabApi();
        RepositoryApi repositoryApi = gitLabApi.getRepositoryApi();

        // Get the workspace branch
        String workspaceBranchName = getWorkspaceBranchName(workspaceSpecification);
        Branch workspaceBranch;
        try
        {
            workspaceBranch = withRetries(() -> repositoryApi.getBranch(gitLabProjectId.getGitLabId(), workspaceBranchName));
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to access " + getReferenceInfo(projectId, workspaceSpecification),
                    () -> "Unknown: " + getReferenceInfo(projectId, workspaceSpecification),
                    () -> "Error accessing " + getReferenceInfo(projectId, workspaceSpecification));
        }
        String currentWorkspaceRevisionId = workspaceBranch.getCommit().getId();
        LOGGER.debug("Found latest revision of {} in project {}: {}", workspaceBranchName, projectId, currentWorkspaceRevisionId);

        // Determine the revision to update to
        String sourceBranchName = getSourceBranch(gitLabProjectId, workspaceSpecification);
        Branch sourceBranch;
        try
        {
            sourceBranch = withRetries(() -> repositoryApi.getBranch(gitLabProjectId.getGitLabId(), sourceBranchName));
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to access the latest revision in " + getReferenceInfo(projectId, workspaceSpecification.getSource()),
                    () -> "Unknown : " + getReferenceInfo(projectId, workspaceSpecification.getSource()),
                    () -> "Error accessing latest revision for " + getReferenceInfo(projectId, workspaceSpecification.getSource()));
        }

        String sourceRevisionId = sourceBranch.getCommit().getId();
        LOGGER.debug("Found latest revision of project {}: {}", projectId, sourceRevisionId);
        CommitsApi commitsApi = gitLabApi.getCommitsApi();
        // Check if the workspace already has the latest revision
        try
        {
            boolean isAlreadyLatest;
            // This will check if the branch contains the master HEAD commit by looking up the list of references the commit is pushed to
            if (sourceRevisionId.equals(currentWorkspaceRevisionId))
            {
                isAlreadyLatest = true;
            }
            else
            {
                Pager<CommitRef> sourceRevisionRefPager = withRetries(() -> commitsApi.getCommitRefs(gitLabProjectId.getGitLabId(), sourceRevisionId, RefType.BRANCH, ITEMS_PER_PAGE));
                isAlreadyLatest = PagerTools.stream(sourceRevisionRefPager).anyMatch(cr -> workspaceBranchName.equals(cr.getName()));
            }
            if (isAlreadyLatest)
            {
                // revision is already in the workspace, no update necessary, hence NO_OP
                LOGGER.debug("Workspace {} in project {} already has revision {}, no update necessary", workspaceSpecification, projectId, sourceRevisionId);
                return createWorkspaceUpdateReport(WorkspaceUpdateReportStatus.NO_OP, sourceRevisionId, currentWorkspaceRevisionId);
            }
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to access revision " + sourceRevisionId + " in " + getReferenceInfo(projectId, workspaceSpecification.getSource()),
                    () -> "Unknown revision in " + getReferenceInfo(projectId, workspaceSpecification.getSource()) + ": " + sourceRevisionId,
                    () -> "Error accessing revision " + sourceRevisionId + " of " + getReferenceInfo(projectId, workspaceSpecification.getSource()));
        }

        // Temp branch for checking for merge conflicts
        String tempBranchName = newUserTemporaryBranchName();
        Branch tempBranch;
        try
        {
            tempBranch = GitLabApiTools.createBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(), tempBranchName, currentWorkspaceRevisionId, 30, 1_000);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to create temporary workspace " + tempBranchName + " in project " + projectId,
                    () -> "Unknown project: " + projectId,
                    () -> "Error creating temporary workspace " + tempBranchName + " in project " + projectId);
        }
        if (tempBranch == null)
        {
            throw new LegendSDLCServerException("Failed to create temporary workspace " + tempBranchName + " in project " + projectId + " from revision " + currentWorkspaceRevisionId);
        }

        // Attempt to rebase the temporary branch on top of master
        boolean rebaseSucceeded = attemptToRebaseWorkspaceUsingTemporaryBranch(projectId, workspaceSpecification, tempBranchName, sourceRevisionId);
        // If fail to rebase, there could be 2 possible reasons:
        // 1. At least one of the intermediate commits on the workspace branch causes rebase to fail
        //      -> we need to squash the workspace branch and try rebase again
        //      to do this, we first check if we even need to squash by checking the number of commits on the workspace branch
        // 2. There are merge conflicts, so we enter conflict resolution route
        if (!rebaseSucceeded)
        {

            String workspaceCreationRevisionId;
            try
            {
                workspaceCreationRevisionId = withRetries(() -> repositoryApi.getMergeBase(gitLabProjectId.getGitLabId(), Arrays.asList(sourceBranchName, currentWorkspaceRevisionId)).getId());
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to get merged base revision for " + getReferenceInfo(projectId, workspaceSpecification),
                        () -> "Could not find revision " + currentWorkspaceRevisionId + " from " + getReferenceInfo(projectId, workspaceSpecification),
                        () -> "Failed to fetch merged base revision for " + getReferenceInfo(projectId, workspaceSpecification));
            }
            // Small optimization step to make sure we need squashing.
            // If there are less than 2 commits (not including the base commit), there is no point in squashing
            List<Revision> latestTwoRevisionsOnWorkspaceBranch = this.revisionApi.getRevisionContext(projectId, workspaceSpecification.getSourceSpecification()).getRevisions(null, null, null, 2);
            Set<String> latestTwoRevisionOnWorkspaceBranchIds = latestTwoRevisionsOnWorkspaceBranch.stream().map(Revision::getId).collect(Collectors.toSet());
            if (latestTwoRevisionOnWorkspaceBranchIds.contains(workspaceCreationRevisionId))
            {
                LOGGER.debug("Failed to rebase branch {}, but the branch does not have enough commits to perform squashing. Proceeding to conflict resolution...", workspaceBranchName);
                return createConflictResolution(projectId, workspaceSpecification, sourceRevisionId);
            }
            else
            {
                LOGGER.debug("Failed to rebase branch {}. Performing squashing commits and re-attempting rebase...", workspaceBranchName);
            }

            WorkspaceUpdateReport rebaseUpdateAttemptReport = attemptToSquashAndRebaseWorkspace(projectId, workspaceSpecification, sourceRevisionId, currentWorkspaceRevisionId, workspaceCreationRevisionId);
            return WorkspaceUpdateReportStatus.UPDATED.equals(rebaseUpdateAttemptReport.getStatus()) ? rebaseUpdateAttemptReport : this.createConflictResolution(projectId, workspaceSpecification, sourceRevisionId);
        }
        String updatedCurrentWorkspaceRevisionId = this.revisionApi.getRevisionContext(projectId, workspaceSpecification.getSourceSpecification()).getCurrentRevision().getId();
        return createWorkspaceUpdateReport(WorkspaceUpdateReportStatus.UPDATED, sourceRevisionId, updatedCurrentWorkspaceRevisionId);
    }

    /**
     * This method is called when we failed to rebase the workspace branch on top of master branch. This implies that either
     * the whole change is causing a merge conflict or one of the intermediate commits have conflicts with the change in master branch
     * <p>
     * To handle the latter case, this method will squash all commits on the workspace branch and attempt rebase again.
     * If this fails, it implies that rebase fails because of merge conflicts, which means we have to enter conflict resolution route.
     * <p>
     * NOTE: based on the nature of this method, we have an optimization here where we check for the number of commits on the current
     * branch, if it is less than 2 (not counting the base), it means no squashing is needed and we can just immediately tell that there
     * is merge conflict and the workspace update should enter conflict resolution route
     * <p>
     * So following is the summary of this method:
     * 1. Create a temp branch to do the squashing on that branch
     * 3. Attempt to rebase the temp branch on master:
     * - If succeeded: re-create current workspace branch on top of the temp branch
     * - If failed -> implies conflict resolution is needed
     *
     * @return a workspace update report that might have status as UPDATED or CONFLICT.
     */
    private WorkspaceUpdateReport attemptToSquashAndRebaseWorkspace(String projectId, WorkspaceSpecification workspaceSpecification, String masterRevisionId, String currentWorkspaceRevisionId, String workspaceCreationRevisionId)
    {
        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        RepositoryApi repositoryApi = getGitLabApi().getRepositoryApi();
        // Create temp branch for rebasing
        String tempBranchName = newUserTemporaryBranchName();
        Branch tempBranch;
        try
        {
            tempBranch = GitLabApiTools.createBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(), tempBranchName, workspaceCreationRevisionId, 30, 1_000);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to create temporary workspace " + tempBranchName + " in project " + projectId,
                    () -> "Unknown project: " + projectId,
                    () -> "Error creating temporary workspace " + tempBranchName + " in project " + projectId);
        }
        if (tempBranch == null)
        {
            throw new LegendSDLCServerException("Failed to create temporary workspace " + tempBranchName + " in project " + projectId + " from revision " + workspaceCreationRevisionId);
        }
        CompareResults comparisonResult;
        try
        {
            comparisonResult = withRetries(() -> repositoryApi.compare(gitLabProjectId.getGitLabId(), workspaceCreationRevisionId, currentWorkspaceRevisionId, true));
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to get comparison information from revision " + workspaceCreationRevisionId + "  to revision " + currentWorkspaceRevisionId + " on project" + projectId,
                    () -> "Could not find revisions " + workspaceCreationRevisionId + " ," + currentWorkspaceRevisionId + " on project" + projectId,
                    () -> "Failed to fetch comparison information from revision " + workspaceCreationRevisionId + "  to revision " + currentWorkspaceRevisionId + " on project" + projectId);
        }
        // Create a new commit on temp branch that squashes all changes on the concerned workspace branch
        CommitsApi commitsApi = getGitLabApi().getCommitsApi();
        ProjectFileAccessProvider.FileAccessContext workspaceFileAccessContext = getProjectFileAccessProvider().getFileAccessContext(projectId, workspaceSpecification.getSourceSpecification());
        Commit squashedCommit;
        try
        {
            List<CommitAction> commitActions = Lists.mutable.empty();
            comparisonResult.getDiffs().forEach(diff ->
            {
                if (diff.getDeletedFile())
                {
                    // DELETE
                    commitActions.add(new CommitAction().withAction(CommitAction.Action.DELETE).withFilePath(diff.getOldPath()));
                }
                else if (diff.getRenamedFile())
                {
                    // MOVE
                    //
                    // tl;dr;
                    // split this into a delete and a create to make sure the moved entity has the content of the entity
                    // in workspace HEAD revision
                    //
                    // Since we use comparison API to compute the diff, Git has a smart algorithm to calculate file move
                    // as it is a heuristics such that if file content is only slightly different, Git can conclude that the
                    // diff was of type RENAME.
                    //
                    // The problem with this is when we compare the workspace BASE revision and workspace HEAD revision
                    // Assume that we actually renamed an entity, we also want to update the entity path, not just its location
                    // the location part is correctly identified by Git, and the change in content is captured as a diff string
                    // in comparison result (which shows the change in the path)
                    // but when we create CommitAction, there is no way for us to apply this patch on top of the entity content
                    // so if we just create action of type MOVE for CommitAction, we will have the content of the old entity
                    // which has the wrong path, and thus this entity if continue to exist in the workspace will throw off
                    // our path and entity location validation check
                    commitActions.add(new CommitAction()
                            .withAction(CommitAction.Action.DELETE)
                            .withFilePath(diff.getOldPath())
                    );
                    commitActions.add(new CommitAction()
                            .withAction(CommitAction.Action.CREATE)
                            .withFilePath(diff.getNewPath())
                            .withEncoding(Constants.Encoding.BASE64)
                            .withContent(encodeBase64(workspaceFileAccessContext.getFile(diff.getNewPath()).getContentAsBytes()))
                    );
                }
                else if (diff.getNewFile())
                {
                    // CREATE
                    commitActions.add(new CommitAction()
                            .withAction(CommitAction.Action.CREATE)
                            .withFilePath(diff.getNewPath())
                            .withEncoding(Constants.Encoding.BASE64)
                            .withContent(encodeBase64(workspaceFileAccessContext.getFile(diff.getNewPath()).getContentAsBytes()))
                    );
                }
                else
                {
                    // UPDATE
                    commitActions.add(new CommitAction()
                            .withAction(CommitAction.Action.UPDATE)
                            .withFilePath(diff.getOldPath())
                            .withEncoding(Constants.Encoding.BASE64)
                            .withContent(encodeBase64(workspaceFileAccessContext.getFile(diff.getOldPath()).getContentAsBytes()))
                    );
                }
            });
            squashedCommit = commitsApi.createCommit(gitLabProjectId.getGitLabId(), tempBranchName, "aggregated changes for workspace " + workspaceSpecification.getId(), null, null, getCurrentUser(), commitActions);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to create commit on temporary workspace " + tempBranchName + " of project " + projectId,
                    () -> "Unknown project: " + projectId + " or temporary workspace " + tempBranchName,
                    () -> "Failed to create commit in temporary workspace " + tempBranchName + " of project " + projectId);
        }
        // Attempt to rebase the temporary branch on top of master
        boolean attemptRebaseResult = attemptToRebaseWorkspaceUsingTemporaryBranch(projectId, workspaceSpecification, tempBranchName, masterRevisionId);
        // If rebasing failed, this implies there are conflicts, otherwise, the workspace should be updated
        if (!attemptRebaseResult)
        {
            return createWorkspaceUpdateReport(WorkspaceUpdateReportStatus.CONFLICT, null, null);
        }
        return createWorkspaceUpdateReport(WorkspaceUpdateReportStatus.UPDATED, masterRevisionId, squashedCommit.getId());
    }

    /**
     * This method attempts to rebase the workspace branch on top of master by using a temp branch. Detailed procedure outlined below:
     * 1. Create a new merge request (MR) that merges temp branch into master branch so that we can use gitlab rebase functionality
     * 2. Call rebase.
     * 3. Continuously check the rebase status of the merge request:
     * - If failed -> return `false`
     * - If succeeded, proceed
     * 4. Re-create workspace branch on top of the rebased temp branch.
     * 5. Cleanup: remove the temp branch and the MR
     * 6. Return `true`
     *
     * @return a boolean flag indicating if the attempted rebase succeeded.
     */
    private boolean attemptToRebaseWorkspaceUsingTemporaryBranch(String projectId, WorkspaceSpecification workspaceSpecification, String tempBranchName, String masterRevisionId)
    {
        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        GitLabApi gitLabApi = getGitLabApi();
        RepositoryApi repositoryApi = gitLabApi.getRepositoryApi();
        // Create merge request to rebase
        MergeRequestApi mergeRequestApi = getGitLabApi().getMergeRequestApi();
        String title = "Update workspace " + workspaceSpecification.getId();
        String message = "Update workspace " + workspaceSpecification.getId() + " up to revision " + masterRevisionId;
        MergeRequest mergeRequest;
        try
        {
            mergeRequest = mergeRequestApi.createMergeRequest(gitLabProjectId.getGitLabId(), tempBranchName, getSourceBranch(gitLabProjectId, workspaceSpecification), title, message, null, null, null, null, false, false);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to create merge request in project " + projectId,
                    () -> "Unknown branch in project " + projectId + ": " + tempBranchName,
                    () -> "Error creating merge request in project " + projectId);
        }
        // Attempt to rebase the merge request
        try
        {
            mergeRequestApi.rebaseMergeRequest(gitLabProjectId.getGitLabId(), mergeRequest.getIid());
            // Check rebase status
            // This only throws when we have 403, so we need to keep polling till we know the result
            // See https://docs.gitlab.com/ee/api/merge_requests.html#rebase-a-merge-request
            CallUntil<MergeRequest, GitLabApiException> rebaseStatusCallUntil = CallUntil.callUntil(
                    () -> withRetries(() -> mergeRequestApi.getRebaseStatus(gitLabProjectId.getGitLabId(), mergeRequest.getIid())),
                    mr -> !mr.getRebaseInProgress(),
                    600,
                    1000L);
            if (!rebaseStatusCallUntil.succeeded())
            {
                LOGGER.warn("Timeout waiting for merge request " + mergeRequest.getIid() + " in project " + projectId + " to finish rebasing");
                return false;
            }
            // Check if there is merge conflict
            if (rebaseStatusCallUntil.getResult().getMergeError() != null)
            {
                return false;
            }
            // if there are no merge conflicts, proceed with the update
            else
            {
                String workspaceBranchName = getWorkspaceBranchName(workspaceSpecification);
                // Create backup branch
                Branch backupBranch;
                WorkspaceSpecification backupWorkspaceSpec = WorkspaceSpecification.newWorkspaceSpecification(workspaceSpecification.getId(), workspaceSpecification.getType(), WorkspaceAccessType.BACKUP, workspaceSpecification.getSource());
                String backupBranchName = getWorkspaceBranchName(backupWorkspaceSpec);
                try
                {
                    backupBranch = GitLabApiTools.createBranchFromSourceBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(), backupBranchName, workspaceBranchName, 30, 1_000);
                }
                catch (Exception e)
                {
                    throw buildException(e,
                            () -> "User " + getCurrentUser() + " is not allowed to create " + getReferenceInfo(projectId, backupWorkspaceSpec),
                            () -> "Unknown project: " + projectId,
                            () -> "Error creating " + getReferenceInfo(projectId, backupWorkspaceSpec));
                }
                if (backupBranch == null)
                {
                    throw new LegendSDLCServerException("Failed to create " + getReferenceInfo(projectId, backupWorkspaceSpec) + " from " + getReferenceInfo(projectId, workspaceSpecification));
                }
                // Delete original branch
                boolean originalBranchDeleted;
                try
                {
                    originalBranchDeleted = GitLabApiTools.deleteBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(), workspaceBranchName, 20, 1_000);
                }
                catch (Exception e)
                {
                    throw buildException(e,
                            () -> "Error while attempting to update " + getReferenceInfo(projectId, workspaceSpecification) + ": user " + getCurrentUser() + " is not allowed to delete workspace",
                            () -> "Error while attempting to update " + getReferenceInfo(projectId, workspaceSpecification) + ": unknown workspace or project",
                            () -> "Error while attempting to update " + getReferenceInfo(projectId, workspaceSpecification) + ": error deleting workspace");
                }
                if (!originalBranchDeleted)
                {
                    throw new LegendSDLCServerException("Failed to delete " + getReferenceInfo(projectId, workspaceSpecification));
                }
                // Create new workspace branch off the temp branch head
                Branch newWorkspaceBranch;
                try
                {
                    newWorkspaceBranch = GitLabApiTools.createBranchFromSourceBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(),
                            workspaceBranchName,
                            tempBranchName,
                            30, 1_000);
                }
                catch (Exception e)
                {
                    throw buildException(e,
                            () -> "Error while attempting to update " + getReferenceInfo(projectId, workspaceSpecification) + ": user " + getCurrentUser() + " is not allowed to create workspace",
                            () -> "Error while attempting to update " + getReferenceInfo(projectId, workspaceSpecification) + ": unknown project: " + projectId,
                            () -> "Error while attempting to update " + getReferenceInfo(projectId, workspaceSpecification) + ": error creating workspace");
                }
                if (newWorkspaceBranch == null)
                {
                    throw new LegendSDLCServerException("Failed to create " + getReferenceInfo(projectId, workspaceSpecification) + " from temporary workspace " + tempBranchName);
                }
                // Delete backup branch
                try
                {
                    boolean deleted = GitLabApiTools.deleteBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(), backupBranchName, 20, 1_000);
                    if (!deleted)
                    {
                        LOGGER.error("Failed to delete {} in project {}", backupBranchName, projectId);
                    }
                }
                catch (Exception e)
                {
                    // unfortunate, but this should not throw error
                    LOGGER.error("Error deleting {} in project {}", backupBranchName, projectId);
                }
            }
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to rebase merge request " + mergeRequest.getIid() + " in project " + projectId,
                    () -> "Unknown merge request ( " + mergeRequest.getIid() + " ) or project ( " + projectId + " )",
                    () -> "Error rebasing merge request " + mergeRequest.getIid() + " in project " + projectId);
        }
        finally
        {
            // Try to close merge request
            try
            {
                mergeRequestApi.updateMergeRequest(gitLabProjectId.getGitLabId(), mergeRequest.getIid(), null, title, null, null, StateEvent.CLOSE, null, null, null, null, null, null);
            }
            catch (Exception closeEx)
            {
                // if we fail, log the error but we don't throw it
                LOGGER.error("Could not close merge request {} for project {}: {}", mergeRequest.getIid(), projectId, mergeRequest.getWebUrl(), closeEx);
            }
            // Delete temporary branch in the background
            submitBackgroundRetryableTask(() -> waitForPipelinesDeleteBranchAndVerify(gitLabApi, gitLabProjectId, tempBranchName), 5000L, "delete " + tempBranchName);
        }
        return true;
    }

    /**
     * This method will mark create a conflict resolution branch from a given workspace branch. Assume we have workspace branch `w1`, this method will:
     * 1. Create resolution branch from `master` branch (check if that's the latest)
     * 2. Get all the changes of workspace branch `w1`
     * 3. Copy and replace those changes to resolution branch `w1` and create a new commit out of that.
     */
    private WorkspaceUpdateReport createConflictResolution(String projectId, WorkspaceSpecification workspaceSpec, String masterRevisionId)
    {
        // Check if conflict resolution is happening, if it is, it means conflict resolution branch already existed, so we will
        // scrap that branch and create a new one.
        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        RepositoryApi repositoryApi = getGitLabApi().getRepositoryApi();
        WorkspaceSpecification conflictResolutionWorkspaceSpec = WorkspaceSpecification.newWorkspaceSpecification(workspaceSpec.getId(), workspaceSpec.getType(), WorkspaceAccessType.CONFLICT_RESOLUTION, workspaceSpec.getSource());
        String conflictResolutionWorkspaceBranchName = getWorkspaceBranchName(conflictResolutionWorkspaceSpec);
        try
        {
            if (GitLabApiTools.branchExists(repositoryApi, gitLabProjectId.getGitLabId(), conflictResolutionWorkspaceBranchName))
            {
                LOGGER.debug("Conflict resolution already happened in {} in project {}, but we will recreate this conflict resolution workspace to make sure it's up to date", workspaceSpec, projectId);
                boolean conflictResolutionBranchDeleted;
                try
                {
                    conflictResolutionBranchDeleted = GitLabApiTools.deleteBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(), conflictResolutionWorkspaceBranchName, 20, 1_000);
                }
                catch (Exception e)
                {
                    throw buildException(e,
                            () -> "User " + getCurrentUser() + " is not allowed to delete " + getReferenceInfo(projectId, conflictResolutionWorkspaceSpec),
                            () -> "Unknown: " + getReferenceInfo(projectId, conflictResolutionWorkspaceSpec),
                            () -> "Error deleting " + getReferenceInfo(projectId, conflictResolutionWorkspaceSpec));
                }
                if (!conflictResolutionBranchDeleted)
                {
                    throw new LegendSDLCServerException("Failed to delete " + getReferenceInfo(projectId, conflictResolutionWorkspaceSpec));
                }
            }
        }
        catch (Exception e)
        {
            LOGGER.error("Error accessing {} in project {}", conflictResolutionWorkspaceBranchName, projectId, e);
        }

        // Create conflict resolution workspace
        Branch conflictResolutionBranch;
        String sourceBranch = getSourceBranch(gitLabProjectId, workspaceSpec);
        try
        {
            conflictResolutionBranch = GitLabApiTools.createBranchFromSourceBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(), conflictResolutionWorkspaceBranchName, sourceBranch, 30, 1_000);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to create " + getReferenceInfo(projectId, conflictResolutionWorkspaceSpec),
                    () -> "Unknown project: " + projectId,
                    () -> "Error creating " + getReferenceInfo(projectId, conflictResolutionWorkspaceSpec));
        }
        if (conflictResolutionBranch == null)
        {
            throw new LegendSDLCServerException("Failed to create " + getReferenceInfo(projectId, conflictResolutionWorkspaceSpec));
        }
        // Get the changes of the current workspace
        String currentWorkspaceRevisionId = this.revisionApi.getRevisionContext(projectId, workspaceSpec.getSourceSpecification()).getCurrentRevision().getId();
        String workspaceCreationRevisionId;

        try
        {
            workspaceCreationRevisionId = withRetries(() -> repositoryApi.getMergeBase(gitLabProjectId.getGitLabId(), Arrays.asList(sourceBranch, currentWorkspaceRevisionId)).getId());
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to get merged base revision for revisions " + sourceBranch + ", " + currentWorkspaceRevisionId + " from project " + projectId,
                    () -> "Could not find revisions " + sourceBranch + ", " + currentWorkspaceRevisionId + " from project " + projectId,
                    () -> "Failed to fetch merged base information for revisions " + sourceBranch + ", " + currentWorkspaceRevisionId + " from project " + projectId);
        }
        CompareResults comparisonResult;
        try
        {
            comparisonResult = withRetries(() -> repositoryApi.compare(gitLabProjectId.getGitLabId(), workspaceCreationRevisionId, currentWorkspaceRevisionId, true));
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to get comparison information from revision " + workspaceCreationRevisionId + "  to revision " + currentWorkspaceRevisionId + " on project" + projectId,
                    () -> "Could not find revisions " + workspaceCreationRevisionId + " ," + currentWorkspaceRevisionId + " on project" + projectId,
                    () -> "Failed to fetch comparison information from revision " + workspaceCreationRevisionId + "  to revision " + currentWorkspaceRevisionId + " on project" + projectId);
        }
        List<Diff> diffs = comparisonResult.getDiffs();

        // Create a new commit on conflict resolution branch
        CommitsApi commitsApi = getGitLabApi().getCommitsApi();
        ProjectFileAccessProvider.FileAccessContext projectFileAccessContext = getProjectFileAccessProvider().getFileAccessContext(projectId, workspaceSpec.getSource().getSourceSpecification());
        ProjectFileAccessProvider.FileAccessContext workspaceFileAccessContext = getProjectFileAccessProvider().getFileAccessContext(projectId, workspaceSpec.getSourceSpecification());
        try
        {
            List<CommitAction> commitActions = Lists.mutable.empty();
            // NOTE: we are bringing the diffs from the workspace and applying those diffs to the project HEAD. Now, the project
            // HEAD could potentially differ greatly from the workspace base revision. This means that when we create the commit
            // action from the diff, we must be careful about the action type.
            // Take for example DELETE: if according to the diff, we delete file A, but at project HEAD, A is already deleted
            // in one of the commits between workspace base and project HEAD, such DELETE commit action will fail, this should then be
            // a NO_OP. Then again, we will have to be careful when CREATE, MOVE, and UPDATE.
            diffs.forEach(diff ->
            {
                if (diff.getDeletedFile())
                {
                    // Ensure the file to delete exists at project HEAD
                    ProjectFileAccessProvider.ProjectFile fileToDelete = projectFileAccessContext.getFile(diff.getOldPath());
                    if (fileToDelete != null)
                    {
                        commitActions.add(new CommitAction()
                                .withAction(CommitAction.Action.DELETE)
                                .withFilePath(diff.getOldPath())
                        );
                    }
                }
                else if (diff.getRenamedFile())
                {
                    // split a MOVE into a DELETE followed by an CREATE/UPDATE to handle cases when
                    // file to be moved is already deleted at project HEAD
                    // and file to be moved to is already created at project HEAD
                    ProjectFileAccessProvider.ProjectFile fileToDelete = projectFileAccessContext.getFile(diff.getOldPath());
                    ProjectFileAccessProvider.ProjectFile fileToReplace = projectFileAccessContext.getFile(diff.getNewPath());
                    if (fileToDelete != null)
                    {
                        commitActions.add(new CommitAction()
                                .withAction(CommitAction.Action.DELETE)
                                .withFilePath(diff.getOldPath())
                        );
                    }
                    commitActions.add(new CommitAction()
                            .withAction(fileToReplace == null ? CommitAction.Action.CREATE : CommitAction.Action.UPDATE)
                            .withFilePath(diff.getNewPath())
                            .withEncoding(Constants.Encoding.BASE64)
                            .withContent(encodeBase64(workspaceFileAccessContext.getFile(diff.getNewPath()).getContentAsBytes()))
                    );
                }
                else if (diff.getNewFile())
                {
                    // If the file to be created already exists at project HEAD, change this to an UPDATE
                    ProjectFileAccessProvider.ProjectFile fileToCreate = projectFileAccessContext.getFile(diff.getOldPath());
                    commitActions.add(new CommitAction()
                            .withAction(fileToCreate == null ? CommitAction.Action.CREATE : CommitAction.Action.UPDATE)
                            .withFilePath(diff.getNewPath())
                            .withEncoding(Constants.Encoding.BASE64)
                            .withContent(encodeBase64(workspaceFileAccessContext.getFile(diff.getNewPath()).getContentAsBytes()))
                    );
                }
                else
                {
                    // File was updated
                    // If the file to be updated is deleted at project HEAD, change this to a CREATE
                    ProjectFileAccessProvider.ProjectFile fileToUpdate = projectFileAccessContext.getFile(diff.getOldPath());
                    commitActions.add(new CommitAction()
                            .withAction(fileToUpdate == null ? CommitAction.Action.CREATE : CommitAction.Action.UPDATE)
                            .withFilePath(diff.getOldPath())
                            .withEncoding(Constants.Encoding.BASE64)
                            .withContent(encodeBase64(workspaceFileAccessContext.getFile(diff.getOldPath()).getContentAsBytes()))
                    );
                }
            });
            commitsApi.createCommit(gitLabProjectId.getGitLabId(), conflictResolutionWorkspaceBranchName, "aggregated changes for conflict resolution", null, null, getCurrentUser(), commitActions);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to create commit on " + getReferenceInfo(projectId, conflictResolutionWorkspaceSpec),
                    () -> "Unknown : " + getReferenceInfo(projectId, conflictResolutionWorkspaceSpec),
                    () -> "Failed to create commit in " + getReferenceInfo(projectId, conflictResolutionWorkspaceSpec));
        }

        return createWorkspaceUpdateReport(WorkspaceUpdateReportStatus.CONFLICT, masterRevisionId, conflictResolutionBranch.getCommit().getId());
    }

    private WorkspaceUpdateReport createWorkspaceUpdateReport(WorkspaceUpdateReportStatus status, String workspaceMergeBaseRevisionId, String workspaceRevisionId)
    {
        return new WorkspaceUpdateReport()
        {
            @Override
            public WorkspaceUpdateReportStatus getStatus()
            {
                return status;
            }

            @Override
            public String getWorkspaceMergeBaseRevisionId()
            {
                return workspaceMergeBaseRevisionId;
            }

            @Override
            public String getWorkspaceRevisionId()
            {
                return workspaceRevisionId;
            }
        };
    }

    private static void validateWorkspaceId(String idString)
    {
        validateWorkspaceId(idString, null);
    }

    private static void validateWorkspaceId(String idString, Status errorStatus)
    {
        if (!isValidWorkspaceId(idString))
        {
            throw new LegendSDLCServerException("Invalid workspace id: \"" + idString + "\". A workspace id must be a non-empty string consisting of characters from the following set: {a-z, A-Z, 0-9, _, ., -}. The id may not contain \"..\" and may not start or end with '.' or '-'.", (errorStatus == null) ? Status.BAD_REQUEST : errorStatus);
        }
    }

    private static boolean isValidWorkspaceId(String string)
    {
        if ((string == null) || string.isEmpty())
        {
            return false;
        }

        if (!isValidWorkspaceStartEndChar(string.charAt(0)))
        {
            return false;
        }
        int lastIndex = string.length() - 1;
        for (int i = 1; i < lastIndex; i++)
        {
            char c = string.charAt(i);
            boolean isValid = isValidWorkspaceStartEndChar(c) || (c == '-') || ((c == '.') && (string.charAt(i - 1) != '.'));
            if (!isValid)
            {
                return false;
            }
        }
        return isValidWorkspaceStartEndChar(string.charAt(lastIndex));
    }

    private static boolean isValidWorkspaceStartEndChar(char c)
    {
        return (c == '_') || (('a' <= c) && (c <= 'z')) || (('A' <= c) && (c <= 'Z')) || (('0' <= c) && (c <= '9'));
    }
}
