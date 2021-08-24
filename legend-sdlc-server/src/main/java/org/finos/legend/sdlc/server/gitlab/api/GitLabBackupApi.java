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

import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
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
    public void discardBackupWorkspace(String projectId, String workspaceId, WorkspaceType workspaceType)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceId, "workspaceId may not be null");
        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        RepositoryApi repositoryApi = getGitLabApi(gitLabProjectId.getGitLabMode()).getRepositoryApi();
        boolean backupWorkspaceDeleted;
        ProjectFileAccessProvider.WorkspaceAccessType backupWorkspaceType = ProjectFileAccessProvider.WorkspaceAccessType.BACKUP;
        try
        {
            backupWorkspaceDeleted = GitLabApiTools.deleteBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(),
                    getWorkspaceBranchName(workspaceId, workspaceType, backupWorkspaceType), 20, 1_000);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to delete " + workspaceType.getLabel() + " " + backupWorkspaceType.getLabel() + " " + workspaceId + " in project " + projectId,
                    () -> "Unknown " + workspaceType.getLabel() + " " + backupWorkspaceType.getLabel() + " (" + workspaceId + ") or project (" + projectId + ")",
                    () -> "Error deleting " + workspaceType.getLabel() + " " + backupWorkspaceType.getLabel() + " " + workspaceId + " in project " + projectId);
        }
        if (!backupWorkspaceDeleted)
        {
            throw new LegendSDLCServerException("Failed to delete " + workspaceType.getLabel() + " " + backupWorkspaceType.getLabel() + " " + workspaceId + " in project " + projectId);
        }
    }

    /**
     * This method will recover a backup workspace by doing the following step:
     * 1. Verify that the backup workspace exists
     * 2. Delete the existing workspace
     * 3. Create
     */
    @Override
    public void recoverBackupWorkspace(String projectId, String workspaceId, WorkspaceType workspaceType, boolean forceRecovery)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceId, "workspaceId may not be null");
        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        RepositoryApi repositoryApi = getGitLabApi(gitLabProjectId.getGitLabMode()).getRepositoryApi();
        ProjectFileAccessProvider.WorkspaceAccessType backupWorkspaceType = ProjectFileAccessProvider.WorkspaceAccessType.BACKUP;
        // Verify the backup exists
        try
        {
            withRetries(() -> repositoryApi.getBranch(gitLabProjectId.getGitLabId(), getWorkspaceBranchName(workspaceId, workspaceType, backupWorkspaceType)));
        }
        catch (Exception e)
        {
            if (GitLabApiTools.isNotFoundGitLabApiException(e))
            {
                LOGGER.error("No backup for workspace {} in project {}, so recovery is not possible", workspaceId, projectId);
            }
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to get " + workspaceType.getLabel() + " " + backupWorkspaceType.getLabel() + " " + workspaceId + " in project " + projectId,
                    () -> "Unknown " + workspaceType.getLabel() + " " + backupWorkspaceType.getLabel() + " with (" + workspaceId + ") or project (" + projectId + "). " +
                            "This implies that a backup does not exist for the specified workspace, hence recovery is not possible",
                    () -> "Error getting " + workspaceType.getLabel() + " " + backupWorkspaceType.getLabel() + " " + workspaceId + " in project " + projectId);
        }
        Branch existingBranch = null;
        ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType = ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE;
        // Check if branch exists
        try
        {
            existingBranch = withRetries(() -> repositoryApi.getBranch(gitLabProjectId.getGitLabId(), getWorkspaceBranchName(workspaceId, workspaceType, workspaceAccessType)));
        }
        catch (Exception e)
        {
            if (!GitLabApiTools.isNotFoundGitLabApiException(e))
            {
                LOGGER.error("Error getting {} {} in project {}", workspaceType.getLabel() + " " + workspaceAccessType.getLabel(), workspaceId, projectId, e);
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
                        getWorkspaceBranchName(workspaceId, workspaceType, workspaceAccessType), 20, 1_000);
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "Error while attempting to recover backup for " + workspaceType.getLabel() + " " + workspaceAccessType.getLabel() + " " + workspaceId + " in project " + projectId + ": User " + getCurrentUser() + " is not allowed to delete workspace",
                        () -> "Error while attempting to recover backup for " + workspaceType.getLabel() + " " + workspaceAccessType.getLabel() + " " + workspaceId + " in project " + projectId + ": Unknown project: " + projectId,
                        () -> "Error while attempting to recover backup for " + workspaceType.getLabel() + " " + workspaceAccessType.getLabel() + " " + workspaceId + " in project " + projectId + ": Error deleting workspace");
            }
            if (!workspaceDeleted)
            {
                throw new LegendSDLCServerException("Failed to delete " + workspaceType.getLabel() + " " + workspaceAccessType.getLabel() + " " + workspaceId + " in project " + projectId);
            }
        }
        // Create new workspace branch off the backup branch head
        Branch workspaceBranch;
        try
        {
            workspaceBranch = GitLabApiTools.createBranchFromSourceBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(),
                    getWorkspaceBranchName(workspaceId, workspaceType, workspaceAccessType),
                    getWorkspaceBranchName(workspaceId, workspaceType, backupWorkspaceType),
                    30, 1_000
            );
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to create " + workspaceType.getLabel() + " " + workspaceAccessType.getLabel() + " " + workspaceId + " in project " + projectId,
                    () -> "Unknown project: " + projectId,
                    () -> "Error creating " + workspaceType.getLabel() + " " + workspaceAccessType.getLabel() + " " + workspaceId + " in project " + projectId);
        }
        if (workspaceBranch == null)
        {
            throw new LegendSDLCServerException("Failed to create " + workspaceType.getLabel() + " " + workspaceAccessType.getLabel() + " " + workspaceId + " in project " + projectId + " from " + workspaceType.getLabel() + " " + backupWorkspaceType.getLabel() + " " + workspaceId);
        }
        // Delete backup branch
        try
        {
            boolean deleted = GitLabApiTools.deleteBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(),
                    getWorkspaceBranchName(workspaceId, workspaceType, backupWorkspaceType), 20, 1_000);
            if (!deleted)
            {
                LOGGER.error("Failed to delete {} {} in project {}", workspaceType.getLabel() + " " + backupWorkspaceType.getLabel(), workspaceId, projectId);
            }
        }
        catch (Exception e)
        {
            // unfortunate, but this should not throw error
            LOGGER.error("Error deleting {} {} in project {} after recovery is completed", workspaceType.getLabel() + " " + backupWorkspaceType.getLabel(), workspaceId, projectId, e);
        }
    }
}
