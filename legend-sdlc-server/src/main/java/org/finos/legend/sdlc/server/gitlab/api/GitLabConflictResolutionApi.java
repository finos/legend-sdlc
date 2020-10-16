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

import org.finos.legend.sdlc.server.application.entity.PerformChangesCommand;
import org.finos.legend.sdlc.server.domain.api.conflictResolution.ConflictResolutionApi;
import org.finos.legend.sdlc.server.domain.api.entity.EntityApi;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.GitLabProjectId;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.gitlab.tools.GitLabApiTools;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.tools.BackgroundTaskProcessor;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.RepositoryApi;
import org.gitlab4j.api.models.Branch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

public class GitLabConflictResolutionApi extends GitLabApiWithFileAccess implements ConflictResolutionApi
{
    private static final Logger LOGGER = LoggerFactory.getLogger(GitLabConflictResolutionApi.class);

    private final EntityApi entityApi;

    @Inject
    public GitLabConflictResolutionApi(GitLabUserContext userContext, EntityApi entityApi, BackgroundTaskProcessor backgroundTaskProcessor)
    {
        super(userContext, backgroundTaskProcessor);
        this.entityApi = entityApi;
    }

    @Override
    public void discardConflictResolution(String projectId, String workspaceId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceId, "workspaceId may not be null");
        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        RepositoryApi repositoryApi = getGitLabApi(gitLabProjectId.getGitLabMode()).getRepositoryApi();
        boolean conflictResolutionBranchDeleted;
        try
        {
            conflictResolutionBranchDeleted = GitLabApiTools.deleteBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(), getUserWorkspaceBranchName(workspaceId, ProjectFileAccessProvider.WorkspaceAccessType.CONFLICT_RESOLUTION), 20, 1_000);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to delete workspace with conflict resolution " + workspaceId + " in project " + projectId,
                    () -> "Unknown workspace with conflict resolution (" + workspaceId + ") or project (" + projectId + ")",
                    () -> "Error deleting workspace with conflict resolution " + workspaceId + " in project " + projectId);
        }
        if (!conflictResolutionBranchDeleted)
        {
            throw new LegendSDLCServerException("Failed to delete " + ProjectFileAccessProvider.WorkspaceAccessType.CONFLICT_RESOLUTION.getLabel() + " " + workspaceId + " in project " + projectId);
        }
    }

    /**
     * This method will discard all changes in conflict resolution. Assume we have workspace branch `w1`, this method will:
     * 1. Remove conflict resolution branch of `w1`
     * 2. Create backup branch for `w1`
     * 3. Remove `w1`
     * 4. Create `w1` from project head
     * 5. Remove backup branch for `w1`
     */
    @Override
    public void discardChangesConflictResolution(String projectId, String workspaceId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceId, "workspaceId may not be null");
        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        RepositoryApi repositoryApi = getGitLabApi(gitLabProjectId.getGitLabMode()).getRepositoryApi();
        // Verify conflict resolution is happening
        try
        {
            withRetries(() -> repositoryApi.getBranch(gitLabProjectId.getGitLabId(), getUserWorkspaceBranchName(workspaceId, ProjectFileAccessProvider.WorkspaceAccessType.CONFLICT_RESOLUTION)));
        }
        catch (Exception e)
        {
            if (GitLabApiTools.isNotFoundGitLabApiException(e))
            {
                LOGGER.error("Conflict resolution is not happening on workspace {} in project {}, so discard changes is not actionable", workspaceId, projectId);
            }
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to get workspace with conflict resolution " + workspaceId + " in project " + projectId,
                    () -> "Unknown workspace with conflict resolution (" + workspaceId + ") or project (" + projectId + "). " +
                            "This implies that conflict resolution is not taking place, hence discard changes is not actionable",
                    () -> "Error getting workspace with conflict resolution " + workspaceId + " in project " + projectId);
        }
        // Delete backup branch if already exists
        boolean backupWorkspaceDeleted;
        try
        {
            backupWorkspaceDeleted = GitLabApiTools.deleteBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(),
                    getUserWorkspaceBranchName(workspaceId, ProjectFileAccessProvider.WorkspaceAccessType.BACKUP), 20, 1_000);
        }
        catch (Exception e)
        {
            // If we fail to delete the residual backup workspace, we cannot proceed anyway, so we will throw the error here
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to delete backup workspace " + workspaceId + " in project " + projectId,
                    () -> "Unknown project: " + projectId,
                    () -> "Error deleting backup workspace " + workspaceId + " in project " + projectId);
        }
        if (!backupWorkspaceDeleted)
        {
            throw new LegendSDLCServerException("Failed to delete " + ProjectFileAccessProvider.WorkspaceAccessType.BACKUP.getLabel() + " " + workspaceId + " in project " + projectId);
        }
        // Create backup branch
        Branch workspaceBranch;
        try
        {
            workspaceBranch = GitLabApiTools.createBranchFromSourceBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(),
                    getUserWorkspaceBranchName(workspaceId, ProjectFileAccessProvider.WorkspaceAccessType.BACKUP),
                    getUserWorkspaceBranchName(workspaceId, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE),
                    30, 1_000
            );
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to create backup workspace " + workspaceId + " in project " + projectId,
                    () -> "Unknown project: " + projectId,
                    () -> "Error creating backup workspace " + workspaceId + " in project " + projectId);
        }
        if (workspaceBranch == null)
        {
            throw new LegendSDLCServerException("Failed to create " + ProjectFileAccessProvider.WorkspaceAccessType.BACKUP.getLabel() + " " + workspaceId + " in project " + projectId);
        }
        // Delete original branch
        boolean originalBranchDeleted;
        try
        {
            originalBranchDeleted = GitLabApiTools.deleteBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(), getUserWorkspaceBranchName(workspaceId, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), 20, 1_000);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to delete workspace " + workspaceId + " in project " + projectId,
                    () -> "Unknown workspace (" + workspaceId + ") or project (" + projectId + ")",
                    () -> "Error deleting workspace " + workspaceId + " in project " + projectId);
        }
        if (!originalBranchDeleted)
        {
            throw new LegendSDLCServerException("Failed to delete " + ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE.getLabel() + " " + workspaceId + " in project " + projectId);
        }
        // Create new workspace branch off the project HEAD
        Branch newWorkspaceBranch;
        try
        {
            newWorkspaceBranch = GitLabApiTools.createBranchFromSourceBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(),
                    getUserWorkspaceBranchName(workspaceId, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE),
                    MASTER_BRANCH, 30, 1_000);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to create workspace " + workspaceId + " in project " + projectId,
                    () -> "Unknown project: " + projectId,
                    () -> "Error creating workspace " + workspaceId + " in project " + projectId);
        }
        if (newWorkspaceBranch == null)
        {
            throw new LegendSDLCServerException("Failed to create " + ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE.getLabel() + " " + workspaceId + " in project " + projectId);
        }
        // Delete conflict resolution branch
        boolean conflictResolutionWorkspaceDeleted;
        try
        {
            conflictResolutionWorkspaceDeleted = GitLabApiTools.deleteBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(),
                    getUserWorkspaceBranchName(workspaceId, ProjectFileAccessProvider.WorkspaceAccessType.CONFLICT_RESOLUTION), 20, 1_000);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to delete workspace with conflict resolution " + workspaceId + " in project " + projectId,
                    () -> "Unknown workspace with conflict resolution (" + workspaceId + ") or project (" + projectId + ")",
                    () -> "Error deleting workspace with conflict resolution " + workspaceId + " in project " + projectId);
        }
        if (!conflictResolutionWorkspaceDeleted)
        {
            throw new LegendSDLCServerException("Failed to delete workspace with conflict resolution " + workspaceId + " in project " + projectId);
        }
        // Delete backup branch
        try
        {
            boolean deleted = GitLabApiTools.deleteBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(),
                    getUserWorkspaceBranchName(workspaceId, ProjectFileAccessProvider.WorkspaceAccessType.BACKUP), 20, 1_000);
            if (!deleted)
            {
                LOGGER.error("Failed to delete backup workspace {} in project {}", workspaceId, projectId);
            }
        }
        catch (Exception e)
        {
            // unfortunate, but this should not throw error
            LOGGER.error("Error deleting backup workspace " + workspaceId + " in project " + projectId, e);
        }
    }

    /**
     * This method will apply conflict resolution changes and mark a conflict resolution as done.
     * Assume we have workspace branch `w1`, this method will:
     * 1. Perform entity changes to resolve conflicts
     * 2. Remove backup branch for `w1` if exists
     * 3. Create backup branch for `w1`
     * 4. Remove workspace branch `w1`
     * 5. Create new workspace branch `w1` from conflict resolution branch `w1`
     * 6. Remove conflict resolution branch `w1`
     * 7. Remove backup branch `w1`
     */
    @Override
    public void acceptConflictResolution(String projectId, String workspaceId, PerformChangesCommand command)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceId, "workspaceId may not be null");
        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        RepositoryApi repositoryApi = getGitLabApi(gitLabProjectId.getGitLabMode()).getRepositoryApi();
        // Verify conflict resolution is happening
        try
        {
            withRetries(() -> repositoryApi.getBranch(gitLabProjectId.getGitLabId(), getUserWorkspaceBranchName(workspaceId, ProjectFileAccessProvider.WorkspaceAccessType.CONFLICT_RESOLUTION)));
        }
        catch (Exception e)
        {
            if (e instanceof GitLabApiException && GitLabApiTools.isNotFoundGitLabApiException((GitLabApiException) e))
            {
                LOGGER.error("Conflict resolution is not happening on workspace {} in project {}, so accepting conflict resolution is not actionable", workspaceId, projectId);
            }
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to get workspace with conflict resolution " + workspaceId + " in project " + projectId,
                    () -> "Unknown workspace with conflict resolution (" + workspaceId + ") or project (" + projectId + "). " +
                            "This implies that conflict resolution is not taking place, hence accepting conflict resolution is not actionable",
                    () -> "Error getting workspace with conflict resolution " + workspaceId + " in project " + projectId);
        }
        // Perform entity changes to resolve conflicts
        try
        {
            this.entityApi.getWorkspaceWithConflictResolutionEntityModificationContext(projectId, workspaceId).performChanges(command.getEntityChanges(), command.getRevisionId(), command.getMessage());
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to apply conflict resolution changes in workspace with conflict resolution " + workspaceId + " in project " + projectId,
                    () -> "Unknown workspace with conflict resolution (" + workspaceId + ") or project (" + projectId + "). " +
                            "This implies that conflict resolution is not taking place, hence accept is not actionable",
                    () -> "Error applying conflict resolution changes in workspace with conflict resolution " + workspaceId + " in project " + projectId);
        }
        // Delete backup branch if already exists
        boolean backupWorkspaceDeleted;
        try
        {
            backupWorkspaceDeleted = GitLabApiTools.deleteBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(),
                    getUserWorkspaceBranchName(workspaceId, ProjectFileAccessProvider.WorkspaceAccessType.BACKUP), 20, 1_000);
        }
        catch (Exception e)
        {
            // If we fail to delete the residual backup workspace, we cannot proceed anyway, so we will throw the error here
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to delete backup workspace " + workspaceId + " in project " + projectId,
                    () -> "Unknown backup workspace (" + workspaceId + ") or project (" + projectId + ")",
                    () -> "Error deleting backup workspace " + workspaceId + " in project " + projectId);
        }
        if (!backupWorkspaceDeleted)
        {
            throw new LegendSDLCServerException("Failed to delete " + ProjectFileAccessProvider.WorkspaceAccessType.BACKUP.getLabel() + " " + workspaceId + " in project " + projectId);
        }
        // Create backup branch from original branch
        Branch newBackupBranch;
        try
        {
            newBackupBranch = GitLabApiTools.createBranchFromSourceBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(),
                    getUserWorkspaceBranchName(workspaceId, ProjectFileAccessProvider.WorkspaceAccessType.BACKUP),
                    getUserWorkspaceBranchName(workspaceId, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), 30, 1_000);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to create backup workspace " + workspaceId + " in project " + projectId,
                    () -> "Unknown project: " + projectId,
                    () -> "Error creating backup workspace " + workspaceId + " in project " + projectId);
        }
        if (newBackupBranch == null)
        {
            throw new LegendSDLCServerException("Failed to create " + ProjectFileAccessProvider.WorkspaceAccessType.BACKUP.getLabel() + " " + workspaceId +
                    " from " + ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE.getLabel() + " " + workspaceId + " in project " + projectId);
        }
        // Delete original branch
        boolean originalBranchDeleted;
        try
        {
            originalBranchDeleted = GitLabApiTools.deleteBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(), getUserWorkspaceBranchName(workspaceId, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), 20, 1_000);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to delete workspace " + workspaceId + " in project " + projectId,
                    () -> "Unknown workspace (" + workspaceId + ") or project (" + projectId + ")",
                    () -> "Error deleting workspace " + workspaceId + " in project " + projectId);
        }
        if (!originalBranchDeleted)
        {
            throw new LegendSDLCServerException("Failed to delete " + ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE.getLabel() + " " + workspaceId + " in project " + projectId);
        }
        // Create new workspace branch off the conflict workspace head
        Branch newWorkspaceBranch;
        try
        {
            newWorkspaceBranch = GitLabApiTools.createBranchFromSourceBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(),
                    getUserWorkspaceBranchName(workspaceId, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE),
                    getUserWorkspaceBranchName(workspaceId, ProjectFileAccessProvider.WorkspaceAccessType.CONFLICT_RESOLUTION), 30, 1_000);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to create workspace " + workspaceId + " in project " + projectId,
                    () -> "Unknown workspace with conflict resolution (" + workspaceId + ") or project (" + projectId + ")",
                    () -> "Error creating workspace " + workspaceId + " in project " + projectId);
        }
        if (newWorkspaceBranch == null)
        {
            throw new LegendSDLCServerException("Failed to create " + ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE.getLabel() + " " + workspaceId +
                    " from " + ProjectFileAccessProvider.WorkspaceAccessType.CONFLICT_RESOLUTION.getLabel() + " " + workspaceId + " in project " + projectId);
        }
        // Delete conflict resolution branch
        boolean conflictResolutionWorkspaceDeleted;
        try
        {
            conflictResolutionWorkspaceDeleted = GitLabApiTools.deleteBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(),
                    getUserWorkspaceBranchName(workspaceId, ProjectFileAccessProvider.WorkspaceAccessType.CONFLICT_RESOLUTION), 20, 1_000);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to delete workspace with conflict resolution " + workspaceId + " in project " + projectId,
                    () -> "Unknown workspace with conflict resolution (" + workspaceId + ") or project (" + projectId + ")",
                    () -> "Error deleting workspace with conflict resolution " + workspaceId + " in project " + projectId);
        }
        if (!conflictResolutionWorkspaceDeleted)
        {
            throw new LegendSDLCServerException("Failed to delete workspace with conflict resolution " + workspaceId + " in project " + projectId);
        }
        // Delete backup branch
        try
        {
            boolean deleted = GitLabApiTools.deleteBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(),
                    getUserWorkspaceBranchName(workspaceId, ProjectFileAccessProvider.WorkspaceAccessType.BACKUP), 20, 1_000);
            if (!deleted)
            {
                LOGGER.error("Failed to delete backup workspace {} in project {}", workspaceId, projectId);
            }
        }
        catch (Exception e)
        {
            // unfortunate, but this should not throw error
            LOGGER.error("Error deleting backup workspace " + workspaceId + " in project " + projectId, e);
        }
    }
}
