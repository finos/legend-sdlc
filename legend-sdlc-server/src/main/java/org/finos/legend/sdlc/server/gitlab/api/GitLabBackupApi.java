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

import org.finos.legend.sdlc.server.domain.api.backup.BackupApi;
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
import javax.ws.rs.core.Response;

public class GitLabBackupApi extends GitLabApiWithFileAccess implements BackupApi
{
    private static final Logger LOGGER = LoggerFactory.getLogger(GitLabBackupApi.class);

    @Inject
    public GitLabBackupApi(GitLabConfiguration gitLabConfiguration, GitLabUserContext userContext, BackgroundTaskProcessor backgroundTaskProcessor)
    {
        super(gitLabConfiguration, userContext, backgroundTaskProcessor);
    }

    @Override
    public void discardBackupWorkspace(String projectId, WorkspaceSpecification workspaceSpecification)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceSpecification, "workspace specification may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        WorkspaceSpecification backupWorkspaceSpec = (workspaceSpecification.getAccessType() == WorkspaceAccessType.BACKUP) ?
                workspaceSpecification :
                WorkspaceSpecification.newWorkspaceSpecification(workspaceSpecification.getId(), workspaceSpecification.getType(), WorkspaceAccessType.BACKUP, workspaceSpecification.getSource(), workspaceSpecification.getUserId());
        RepositoryApi repositoryApi = getGitLabApi().getRepositoryApi();
        try
        {
            boolean success = GitLabApiTools.deleteBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(), getWorkspaceBranchName(backupWorkspaceSpec), 20, 1_000);
            if (!success)
            {
                throw new LegendSDLCServerException("Failed to delete " + getReferenceInfo(projectId, backupWorkspaceSpec));
            }
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to delete " + getReferenceInfo(projectId, backupWorkspaceSpec),
                    () -> "Unknown " + getReferenceInfo(projectId, backupWorkspaceSpec),
                    () -> "Error deleting " + getReferenceInfo(projectId, backupWorkspaceSpec));
        }
    }

    /**
     * This method will recover a backup workspace by doing the following step:
     * 1. Verify that the backup workspace exists
     * 2. Delete the existing workspace
     * 3. Create
     */
    @Override
    public void recoverBackupWorkspace(String projectId, WorkspaceSpecification workspaceSpecification, boolean forceRecovery)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceSpecification, "workspace specification may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);

        WorkspaceSpecification mainWorkspaceSpec = (workspaceSpecification.getAccessType() == WorkspaceAccessType.WORKSPACE) ?
                workspaceSpecification :
                WorkspaceSpecification.newWorkspaceSpecification(workspaceSpecification.getId(), workspaceSpecification.getType(), WorkspaceAccessType.WORKSPACE, workspaceSpecification.getSource(), workspaceSpecification.getUserId());
        WorkspaceSpecification backupWorkspaceSpec = (workspaceSpecification.getAccessType() == WorkspaceAccessType.BACKUP) ?
                workspaceSpecification :
                WorkspaceSpecification.newWorkspaceSpecification(workspaceSpecification.getId(), workspaceSpecification.getType(), WorkspaceAccessType.BACKUP, workspaceSpecification.getSource(), workspaceSpecification.getUserId());

        String mainWorkspaceBranchName = getWorkspaceBranchName(mainWorkspaceSpec);
        String backupWorkspaceBranchName = getWorkspaceBranchName(backupWorkspaceSpec);

        RepositoryApi repositoryApi = getGitLabApi().getRepositoryApi();

        // Verify the backup exists
        try
        {
            withRetries(() -> repositoryApi.getBranch(gitLabProjectId.getGitLabId(), backupWorkspaceBranchName));
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to access " + getReferenceInfo(projectId, backupWorkspaceSpec),
                    () -> "A backup cannot be found for " + getReferenceInfo(projectId, mainWorkspaceSpec) + ", so recovery is not possible",
                    () -> "Error getting " + getReferenceInfo(projectId, backupWorkspaceSpec));
        }

        // Check if branch exists
        Branch existingBranch = null;
        try
        {
            existingBranch = withRetries(() -> repositoryApi.getBranch(gitLabProjectId.getGitLabId(), mainWorkspaceBranchName));
        }
        catch (Exception e)
        {
            if (!GitLabApiTools.isNotFoundGitLabApiException(e))
            {
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to access " + getReferenceInfo(projectId, mainWorkspaceSpec),
                        null,
                        () -> "Error getting " + getReferenceInfo(projectId, mainWorkspaceSpec));
            }
        }
        if (existingBranch != null)
        {
            if (!forceRecovery)
            {
                throw new LegendSDLCServerException(getReferenceInfo(projectId, mainWorkspaceSpec) + " already exists and the recovery is not forced, so recovery from backup is not possible", Response.Status.CONFLICT);
            }
            // Delete the existing branch
            boolean workspaceDeleted;
            try
            {
                workspaceDeleted = GitLabApiTools.deleteBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(), mainWorkspaceBranchName, 20, 1_000);
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "Error while attempting to recover backup for " + getReferenceInfo(projectId, mainWorkspaceSpec) + ": user " + getCurrentUser() + " is not allowed to delete workspace",
                        () -> "Error while attempting to recover backup for " + getReferenceInfo(projectId, mainWorkspaceSpec) + ": Unknown project: " + projectId,
                        () -> "Error while attempting to recover backup for " + getReferenceInfo(projectId, mainWorkspaceSpec) + ": Error deleting workspace");
            }
            if (!workspaceDeleted)
            {
                throw new LegendSDLCServerException("Failed to delete " + getReferenceInfo(projectId, mainWorkspaceSpec));
            }
        }
        // Create new workspace branch off the backup branch head
        Branch workspaceBranch;
        try
        {
            workspaceBranch = GitLabApiTools.createBranchFromSourceBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(), mainWorkspaceBranchName, backupWorkspaceBranchName, 30, 1_000);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "Error while attempting to recover backup for " + getReferenceInfo(projectId, mainWorkspaceSpec) + ": user " + getCurrentUser() + " is not allowed to create workspace",
                    () -> "Error while attempting to recover backup for " + getReferenceInfo(projectId, mainWorkspaceSpec) + ": unknown project: " + projectId,
                    () -> "Error while attempting to recover backup for " + getReferenceInfo(projectId, mainWorkspaceSpec) + ": error creating workspace");
        }
        if (workspaceBranch == null)
        {
            throw new LegendSDLCServerException("Error while attempting to recover backup for " + getReferenceInfo(projectId, mainWorkspaceSpec) + ": failed to create workspace");
        }

        // Delete backup branch
        boolean deleted;
        try
        {
            deleted = GitLabApiTools.deleteBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(), backupWorkspaceBranchName, 20, 1_000);
        }
        catch (Exception e)
        {
            // unfortunate, but this should not throw error
            LOGGER.error("Error deleting {} in project {} after recovery is completed", backupWorkspaceBranchName, projectId, e);
            deleted = false;
        }
        if (!deleted)
        {
            LOGGER.warn("Failed to delete {} in project {}, submitting background task", backupWorkspaceBranchName, projectId);
            submitBackgroundRetryableTask(() -> GitLabApiTools.deleteBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(), backupWorkspaceBranchName, 5, 1_000), 5000L, "delete " + backupWorkspaceBranchName);
        }
    }
}
