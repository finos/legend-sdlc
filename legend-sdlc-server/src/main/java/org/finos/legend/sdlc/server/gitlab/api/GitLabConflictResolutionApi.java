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
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSpecification;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.GitLabConfiguration;
import org.finos.legend.sdlc.server.gitlab.GitLabProjectId;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.gitlab.tools.GitLabApiTools;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider.WorkspaceAccessType;
import org.finos.legend.sdlc.server.tools.BackgroundTaskProcessor;
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
    public GitLabConflictResolutionApi(GitLabConfiguration gitLabConfiguration, GitLabUserContext userContext, EntityApi entityApi, BackgroundTaskProcessor backgroundTaskProcessor)
    {
        super(gitLabConfiguration, userContext, backgroundTaskProcessor);
        this.entityApi = entityApi;
    }

    @Override
    public void discardConflictResolution(String projectId, WorkspaceSpecification workspaceSpecification)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceSpecification, "workspace specification may not be null");
        
        GitLabProjectId gitLabProjectId = parseProjectId(projectId);

        WorkspaceSpecification conflictResWorkspaceSpec = getWorkspaceSpecWithAccessType(workspaceSpecification, WorkspaceAccessType.CONFLICT_RESOLUTION);
        RepositoryApi repositoryApi = getGitLabApi().getRepositoryApi();
        try
        {
            boolean success = GitLabApiTools.deleteBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(), getWorkspaceBranchName(conflictResWorkspaceSpec), 20, 1_000);
            if (!success)
            {
                throw new LegendSDLCServerException("Failed to delete " + getReferenceInfo(projectId, conflictResWorkspaceSpec));
            }
        }
        catch (Exception e)
        {
            throw buildException(e,
                () -> "User " + getCurrentUser() + " is not allowed to delete " + getReferenceInfo(projectId, conflictResWorkspaceSpec),
                () -> "Unknown: " + getReferenceInfo(projectId, conflictResWorkspaceSpec),
                () -> "Error deleting " + getReferenceInfo(projectId, conflictResWorkspaceSpec));
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
    public void discardChangesConflictResolution(String projectId, WorkspaceSpecification workspaceSpecification)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceSpecification, "workspace specification may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        RepositoryApi repositoryApi = getGitLabApi().getRepositoryApi();

        WorkspaceSpecification workspaceWorkspaceSpec = getWorkspaceSpecWithAccessType(workspaceSpecification, WorkspaceAccessType.WORKSPACE);
        WorkspaceSpecification conflictResWorkspaceSpec = getWorkspaceSpecWithAccessType(workspaceSpecification, WorkspaceAccessType.CONFLICT_RESOLUTION);
        WorkspaceSpecification backupWorkspaceSpec = getWorkspaceSpecWithAccessType(workspaceSpecification, WorkspaceAccessType.BACKUP);

        String workspaceBranchName = getWorkspaceBranchName(workspaceWorkspaceSpec);
        String conflictResBranchName = getWorkspaceBranchName(conflictResWorkspaceSpec);
        String backupBranchName = getWorkspaceBranchName(backupWorkspaceSpec);

        // Verify conflict resolution is happening
        try
        {
            withRetries(() -> repositoryApi.getBranch(gitLabProjectId.getGitLabId(), conflictResBranchName));
        }
        catch (Exception e)
        {
            if (GitLabApiTools.isNotFoundGitLabApiException(e))
            {
                LOGGER.debug("Could not find branch {} in project {}: conflict resolution is not happening, so discard changes is not actionable", conflictResBranchName, projectId);
            }
            throw buildException(e,
                () -> "User " + getCurrentUser() + " is not allowed to access " + getReferenceInfo(projectId, conflictResWorkspaceSpec),
                () -> "Unknown " + getReferenceInfo(projectId, conflictResWorkspaceSpec) + ": conflict resolution is not taking place, hence discard changes is not actionable",
                () -> "Error getting " + getReferenceInfo(projectId, conflictResWorkspaceSpec));
        }

        // Delete backup branch if already exists
        boolean backupWorkspaceDeleted;
        try
        {
            backupWorkspaceDeleted = GitLabApiTools.deleteBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(), backupBranchName, 20, 1_000);
        }
        catch (Exception e)
        {
            // If we fail to delete the residual backup workspace, we cannot proceed anyway, so we will throw the error here
            throw buildException(e,
                () -> "User " + getCurrentUser() + " is not allowed to delete " + getReferenceInfo(projectId, backupWorkspaceSpec),
                () -> "Unknown " + getReferenceInfo(projectId, backupWorkspaceSpec),
                () -> "Error deleting " + getReferenceInfo(projectId, backupWorkspaceSpec));
        }
        if (!backupWorkspaceDeleted)
        {
            throw new LegendSDLCServerException("Failed to delete " + getReferenceInfo(projectId, backupWorkspaceSpec));
        }

        // Create backup branch
        Branch workspaceBranch;
        try
        {
            workspaceBranch = GitLabApiTools.createBranchFromSourceBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(), backupBranchName, workspaceBranchName, 30, 1_000);
        }
        catch (Exception e)
        {
            throw buildException(e,
                () -> "User " + getCurrentUser() + " is not allowed to create " + getReferenceInfo(projectId, backupWorkspaceSpec),
                () -> "Unknown " + getReferenceInfo(projectId, workspaceWorkspaceSpec),
                () -> "Error creating " + getReferenceInfo(projectId, backupWorkspaceSpec));
        }
        if (workspaceBranch == null)
        {
            throw new LegendSDLCServerException("Failed to create " + getReferenceInfo(projectId, backupWorkspaceSpec));
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
                () -> "User " + getCurrentUser() + " is not allowed to delete " + getReferenceInfo(projectId, workspaceWorkspaceSpec),
                () -> "Unknown " + getReferenceInfo(projectId, workspaceWorkspaceSpec),
                () -> "Error deleting " + getReferenceInfo(projectId, workspaceWorkspaceSpec));
        }
        if (!originalBranchDeleted)
        {
            throw new LegendSDLCServerException("Failed to delete " + getReferenceInfo(projectId, workspaceWorkspaceSpec));
        }

        // Create new workspace branch off the project HEAD
        Branch newWorkspaceBranch;
        try
        {
            newWorkspaceBranch = GitLabApiTools.createBranchFromSourceBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(), workspaceBranchName, getSourceBranch(gitLabProjectId, workspaceWorkspaceSpec), 30, 1_000);
        }
        catch (Exception e)
        {
            throw buildException(e,
                () -> "User " + getCurrentUser() + " is not allowed to create " + getReferenceInfo(projectId, workspaceWorkspaceSpec),
                () -> "Unknown " + getReferenceInfo(projectId, workspaceWorkspaceSpec.getSource()),
                () -> "Error creating " + getReferenceInfo(projectId, workspaceWorkspaceSpec));
        }
        if (newWorkspaceBranch == null)
        {
            throw new LegendSDLCServerException("Failed to create " + getReferenceInfo(projectId, workspaceWorkspaceSpec));
        }

        // Delete conflict resolution branch
        boolean conflictResolutionWorkspaceDeleted;
        try
        {
            conflictResolutionWorkspaceDeleted = GitLabApiTools.deleteBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(), conflictResBranchName, 20, 1_000);
        }
        catch (Exception e)
        {
            throw buildException(e,
                () -> "User " + getCurrentUser() + " is not allowed to delete " + getReferenceInfo(projectId, conflictResWorkspaceSpec),
                () -> "Unknown " + getReferenceInfo(projectId, conflictResWorkspaceSpec),
                () -> "Error deleting " + getReferenceInfo(projectId, conflictResWorkspaceSpec));
        }
        if (!conflictResolutionWorkspaceDeleted)
        {
            throw new LegendSDLCServerException("Failed to delete " + getReferenceInfo(projectId, conflictResWorkspaceSpec));
        }

        // Delete backup branch
        boolean backupBranchDeleted;
        try
        {
            backupBranchDeleted = GitLabApiTools.deleteBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(), backupBranchName, 20, 1_000);
        }
        catch (Exception e)
        {
            // unfortunate, but this should not throw error
            LOGGER.error("Error deleting backup branch {} in project {}", backupBranchName, projectId, e);
            backupBranchDeleted = false;
        }
        if (!backupBranchDeleted)
        {
            LOGGER.error("Failed to delete backup branch {} in project {}, submitting background task", backupBranchName, projectId);
            submitBackgroundRetryableTask(() -> GitLabApiTools.deleteBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(), backupBranchName, 5, 1_000), 5000L, "delete " + backupBranchName);
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
    public void acceptConflictResolution(String projectId, WorkspaceSpecification workspaceSpecification, PerformChangesCommand command)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceSpecification, "workspace specification may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        RepositoryApi repositoryApi = getGitLabApi().getRepositoryApi();

        WorkspaceSpecification workspaceWorkspaceSpec = getWorkspaceSpecWithAccessType(workspaceSpecification, WorkspaceAccessType.WORKSPACE);
        WorkspaceSpecification conflictResWorkspaceSpec = getWorkspaceSpecWithAccessType(workspaceSpecification, WorkspaceAccessType.CONFLICT_RESOLUTION);
        WorkspaceSpecification backupWorkspaceSpec = getWorkspaceSpecWithAccessType(workspaceSpecification, WorkspaceAccessType.BACKUP);

        String workspaceBranchName = getWorkspaceBranchName(workspaceWorkspaceSpec);
        String conflictResBranchName = getWorkspaceBranchName(conflictResWorkspaceSpec);
        String backupBranchName = getWorkspaceBranchName(backupWorkspaceSpec);

        // Verify conflict resolution is happening
        try
        {
            withRetries(() -> repositoryApi.getBranch(gitLabProjectId.getGitLabId(), conflictResBranchName));
        }
        catch (Exception e)
        {
            if (GitLabApiTools.isNotFoundGitLabApiException(e))
            {
                LOGGER.debug("Could not find branch {} in project {}: conflict resolution is not happening, so accepting conflict resolution is not actionable", conflictResBranchName, projectId);
            }
            throw buildException(e,
                () -> "User " + getCurrentUser() + " is not allowed to get " + getReferenceInfo(projectId, conflictResWorkspaceSpec),
                () -> "Unknown " + getReferenceInfo(projectId, conflictResWorkspaceSpec) + ": conflict resolution is not taking place, hence accepting conflict resolution is not actionable",
                () -> "Error getting " + getReferenceInfo(projectId, conflictResWorkspaceSpec));
        }

        // Perform entity changes to resolve conflicts
        try
        {
            this.entityApi.getEntityModificationContext(projectId, conflictResWorkspaceSpec.getSourceSpecification()).performChanges(command.getEntityChanges(), command.getRevisionId(), command.getMessage());
        }
        catch (Exception e)
        {
            throw buildException(e,
                () -> "User " + getCurrentUser() + " is not allowed to apply conflict resolution changes in " + getReferenceInfo(projectId, conflictResWorkspaceSpec),
                () -> "Unknown " + getReferenceInfo(projectId, conflictResWorkspaceSpec) + ": conflict resolution is not taking place, hence accepting conflict resolution is not actionable",
                () -> "Error applying conflict resolution changes in " + getReferenceInfo(projectId, conflictResWorkspaceSpec));
        }

        // Delete backup branch if already exists
        boolean backupWorkspaceDeleted;
        try
        {
            backupWorkspaceDeleted = GitLabApiTools.deleteBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(), backupBranchName, 20, 1_000);
        }
        catch (Exception e)
        {
            // If we fail to delete the residual backup workspace, we cannot proceed anyway, so we will throw the error here
            throw buildException(e,
                () -> "User " + getCurrentUser() + " is not allowed to delete " + getReferenceInfo(projectId, backupWorkspaceSpec),
                () -> "Unknown " + getReferenceInfo(projectId, backupWorkspaceSpec),
                () -> "Error deleting " + getReferenceInfo(projectId, backupWorkspaceSpec));
        }
        if (!backupWorkspaceDeleted)
        {
            throw new LegendSDLCServerException("Failed to delete " + getReferenceInfo(projectId, backupWorkspaceSpec));
        }

        // Create backup branch from original branch
        try
        {
            Thread.sleep(1000); // Wait to allow nodes to synchronize that backup branch is already deleted
        }
        catch (InterruptedException e)
        {
            LOGGER.warn("Interrupted while waiting for nodes to synchronize that backup branch was deleted", e);
            Thread.currentThread().interrupt();
        }
        Branch newBackupBranch;
        try
        {
            newBackupBranch = GitLabApiTools.createBranchFromSourceBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(), backupBranchName, workspaceBranchName, 30, 1_000);
        }
        catch (Exception e)
        {
            throw buildException(e,
                () -> "User " + getCurrentUser() + " is not allowed to create " + getReferenceInfo(projectId, backupWorkspaceSpec),
                () -> "Unknown " + getReferenceInfo(projectId, workspaceWorkspaceSpec),
                () -> "Error creating " + getReferenceInfo(projectId, backupWorkspaceSpec));
        }
        if (newBackupBranch == null)
        {
            throw new LegendSDLCServerException("Failed to create " + getReferenceInfo(projectId, backupWorkspaceSpec));
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
                () -> "User " + getCurrentUser() + " is not allowed to delete " + getReferenceInfo(projectId, workspaceWorkspaceSpec),
                () -> "Unknown " + getReferenceInfo(projectId, workspaceWorkspaceSpec),
                () -> "Error deleting " + getReferenceInfo(projectId, workspaceWorkspaceSpec));
        }
        if (!originalBranchDeleted)
        {
            throw new LegendSDLCServerException("Failed to delete " + getReferenceInfo(projectId, workspaceWorkspaceSpec));
        }

        // Create new workspace branch off the conflict workspace head
        try
        {
            Thread.sleep(1000); // Wait to allow nodes to synchronize that original branch is already deleted.
        }
        catch (InterruptedException e)
        {
            LOGGER.warn("Interrupted while waiting for nodes to synchronize that original branch was deleted.", e);
            Thread.currentThread().interrupt();
        }
        Branch newWorkspaceBranch;
        try
        {
            newWorkspaceBranch = GitLabApiTools.createBranchFromSourceBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(), workspaceBranchName, conflictResBranchName, 30, 1_000);
        }
        catch (Exception e)
        {
            throw buildException(e,
                () -> "User " + getCurrentUser() + " is not allowed to create " + getReferenceInfo(projectId, workspaceWorkspaceSpec),
                () -> "Unknown " + getReferenceInfo(projectId, conflictResWorkspaceSpec),
                () -> "Error creating " + getReferenceInfo(projectId, workspaceWorkspaceSpec));
        }
        if (newWorkspaceBranch == null)
        {
            throw new LegendSDLCServerException("Failed to create " + getReferenceInfo(projectId, workspaceWorkspaceSpec));
        }

        // Delete conflict resolution branch
        boolean conflictResolutionWorkspaceDeleted;
        try
        {
            // No need to waste wait time here since conflict resolution branch was long created during update
            conflictResolutionWorkspaceDeleted = GitLabApiTools.deleteBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(), conflictResBranchName, 20, 1_000);
        }
        catch (Exception e)
        {
            throw buildException(e,
                () -> "User " + getCurrentUser() + " is not allowed to delete " + getReferenceInfo(projectId, conflictResWorkspaceSpec),
                () -> "Unknown " + getReferenceInfo(projectId, conflictResWorkspaceSpec),
                () -> "Error deleting " + getReferenceInfo(projectId, conflictResWorkspaceSpec));
        }
        if (!conflictResolutionWorkspaceDeleted)
        {
            throw new LegendSDLCServerException("Failed to delete " + getReferenceInfo(projectId, conflictResWorkspaceSpec));
        }

        // Delete backup branch
        try
        {
            Thread.sleep(500); // Wait extra 500 ms to allow nodes to synchronize that backup branch was recreated
        }
        catch (InterruptedException e)
        {
            LOGGER.warn("Interrupted while waiting for nodes to synchronize that backup branch was recreated.", e);
            Thread.currentThread().interrupt();
        }
        boolean backupBranchDeleted;
        try
        {
            backupBranchDeleted = GitLabApiTools.deleteBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(), backupBranchName, 20, 1_000);
        }
        catch (Exception e)
        {
            // unfortunate, but this should not throw error
            LOGGER.error("Error deleting backup branch {} in project {}", backupBranchName, projectId, e);
            backupBranchDeleted = false;
        }
        if (!backupBranchDeleted)
        {
            LOGGER.error("Failed to delete backup branch {} in project {}, submitting background task", backupBranchName, projectId);
            submitBackgroundRetryableTask(() -> GitLabApiTools.deleteBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(), backupBranchName, 5, 1_000), 5000L, "delete " + backupBranchName);
        }
    }

    private static WorkspaceSpecification getWorkspaceSpecWithAccessType(WorkspaceSpecification workspaceSpec, WorkspaceAccessType accessType)
    {
        return (workspaceSpec.getAccessType() == accessType) ?
                workspaceSpec :
                WorkspaceSpecification.newWorkspaceSpecification(workspaceSpec.getId(), workspaceSpec.getType(), accessType, workspaceSpec.getSource(), workspaceSpec.getUserId());
    }
}
