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
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.GitLabProjectId;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.gitlab.tools.GitLabApiTools;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
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
    public GitLabBackupApi(GitLabUserContext userContext, BackgroundTaskProcessor backgroundTaskProcessor)
    {
        super(userContext, backgroundTaskProcessor);
    }

    @Override
    public void discardBackupUserWorkspace(String projectId, String workspaceId)
    {
        this.discardBackupWorkspace(projectId, workspaceId, false);
    }

    @Override
    public void discardBackupGroupWorkspace(String projectId, String workspaceId)
    {
        this.discardBackupWorkspace(projectId, workspaceId, true);
    }

    @Override
    public void discardBackupWorkspace(String projectId, String workspaceId, boolean isGroupWorkspace)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceId, "workspaceId may not be null");
        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        RepositoryApi repositoryApi = getGitLabApi(gitLabProjectId.getGitLabMode()).getRepositoryApi();
        boolean backupWorkspaceDeleted;
        ProjectFileAccessProvider.WorkspaceAccessType backupWorkspaceType = getAdjustedWorkspaceAccessType(ProjectFileAccessProvider.WorkspaceAccessType.BACKUP, isGroupWorkspace);
        try
        {
            backupWorkspaceDeleted = GitLabApiTools.deleteBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(),
                    getUserWorkspaceBranchName(workspaceId, backupWorkspaceType), 20, 1_000);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to delete " + backupWorkspaceType.getLabel() + " " + workspaceId + " in project " + projectId,
                    () -> "Unknown " + backupWorkspaceType.getLabel() + " (" + workspaceId + ") or project (" + projectId + ")",
                    () -> "Error deleting " + backupWorkspaceType.getLabel() + " " + workspaceId + " in project " + projectId);
        }
        if (!backupWorkspaceDeleted)
        {
            throw new LegendSDLCServerException("Failed to delete " + backupWorkspaceType.getLabel() + " " + workspaceId + " in project " + projectId);
        }
    }

    @Override
    public void recoverBackupUserWorkspace(String projectId, String workspaceId, boolean forceRecovery)
    {
        this.recoverBackupWorkspace(projectId, workspaceId, false, forceRecovery);
    }

    @Override
    public void recoverBackupGroupWorkspace(String projectId, String workspaceId, boolean forceRecovery)
    {
        this.recoverBackupWorkspace(projectId, workspaceId, true, forceRecovery);
    }

    /**
     * This method will recover a backup workspace by doing the following step:
     * 1. Verify that the backup workspace exists
     * 2. Delete the existing workspace
     * 3. Create
     */
    @Override
    public void recoverBackupWorkspace(String projectId, String workspaceId, boolean isGroupWorkspace, boolean forceRecovery)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceId, "workspaceId may not be null");
        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        RepositoryApi repositoryApi = getGitLabApi(gitLabProjectId.getGitLabMode()).getRepositoryApi();
        ProjectFileAccessProvider.WorkspaceAccessType backupWorkspaceType = getAdjustedWorkspaceAccessType(ProjectFileAccessProvider.WorkspaceAccessType.BACKUP, isGroupWorkspace);
        // Verify the backup exists
        try
        {
            withRetries(() -> repositoryApi.getBranch(gitLabProjectId.getGitLabId(), getUserWorkspaceBranchName(workspaceId, backupWorkspaceType)));
        }
        catch (Exception e)
        {
            if (GitLabApiTools.isNotFoundGitLabApiException(e))
            {
                LOGGER.error("No backup for workspace {} in project {}, so recovery is not possible", workspaceId, projectId);
            }
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to get " + backupWorkspaceType.getLabel() + " " + workspaceId + " in project " + projectId,
                    () -> "Unknown " + backupWorkspaceType.getLabel() + " with (" + workspaceId + ") or project (" + projectId + "). " +
                            "This implies that a backup does not exist for the specified workspace, hence recovery is not possible",
                    () -> "Error getting " + backupWorkspaceType.getLabel() + " " + workspaceId + " in project " + projectId);
        }
        Branch existingBranch = null;
        ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType = getAdjustedWorkspaceAccessType(ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE, isGroupWorkspace);
        // Check if branch exists
        try
        {
            existingBranch = withRetries(() -> repositoryApi.getBranch(gitLabProjectId.getGitLabId(), getUserWorkspaceBranchName(workspaceId, workspaceAccessType)));
        }
        catch (Exception e)
        {
            if (!GitLabApiTools.isNotFoundGitLabApiException(e))
            {
                LOGGER.error("Error getting {} {} in project {}", workspaceAccessType.getLabel(), workspaceId, projectId, e);
            }
        }
        if (existingBranch != null)
        {
            if (!forceRecovery)
            {
                throw new LegendSDLCServerException("Workspace " + workspaceId + " of project " + projectId + " already existed and the recovery is not forced, so recovery from backup is not possible", Response.Status.METHOD_NOT_ALLOWED);
            }
            // Delete the existing branch
            boolean workspaceDeleted;
            try
            {
                workspaceDeleted = GitLabApiTools.deleteBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(),
                        getUserWorkspaceBranchName(workspaceId, workspaceAccessType), 20, 1_000);
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "Error while attempting to recover backup for " + workspaceAccessType.getLabel() + " " + workspaceId + " in project " + projectId + ": User " + getCurrentUser() + " is not allowed to delete workspace",
                        () -> "Error while attempting to recover backup for " + workspaceAccessType.getLabel() + " " + workspaceId + " in project " + projectId + ": Unknown project: " + projectId,
                        () -> "Error while attempting to recover backup for " + workspaceAccessType.getLabel() + " " + workspaceId + " in project " + projectId + ": Error deleting workspace");
            }
            if (!workspaceDeleted)
            {
                throw new LegendSDLCServerException("Failed to delete " + workspaceAccessType.getLabel() + " " + workspaceId + " in project " + projectId);
            }
        }
        // Create new workspace branch off the backup branch head
        Branch workspaceBranch;
        try
        {
            workspaceBranch = GitLabApiTools.createBranchFromSourceBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(),
                    getUserWorkspaceBranchName(workspaceId, workspaceAccessType),
                    getUserWorkspaceBranchName(workspaceId, backupWorkspaceType),
                    30, 1_000
            );
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to create " + workspaceAccessType.getLabel() + " " + workspaceId + " in project " + projectId,
                    () -> "Unknown project: " + projectId,
                    () -> "Error creating " + workspaceAccessType.getLabel() + " " + workspaceId + " in project " + projectId);
        }
        if (workspaceBranch == null)
        {
            throw new LegendSDLCServerException("Failed to create " + workspaceAccessType.getLabel() + " " + workspaceId + " in project " + projectId + " from " + backupWorkspaceType.getLabel() + " " + workspaceId);
        }
        // Delete backup branch
        try
        {
            boolean deleted = GitLabApiTools.deleteBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(),
                    getUserWorkspaceBranchName(workspaceId, backupWorkspaceType), 20, 1_000);
            if (!deleted)
            {
                LOGGER.error("Failed to delete {} {} in project {}", backupWorkspaceType.getLabel(), workspaceId, projectId);
            }
        }
        catch (Exception e)
        {
            // unfortunate, but this should not throw error
            LOGGER.error("Error deleting {} {} in project {} after recovery is completed", backupWorkspaceType.getLabel(), workspaceId, projectId, e);
        }
    }
}
