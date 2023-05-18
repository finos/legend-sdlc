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
    public GitLabBackupApi(GitLabConfiguration gitLabConfiguration, GitLabUserContext userContext, BackgroundTaskProcessor backgroundTaskProcessor)
    {
        super(gitLabConfiguration, userContext, backgroundTaskProcessor);
    }

    @Override
    public void discardBackupWorkspace(String projectId, WorkspaceSpecification workspaceSpecification)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceSpecification.getWorkspaceId(), "workspaceId may not be null");
        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        RepositoryApi repositoryApi = getGitLabApi().getRepositoryApi();
        boolean backupWorkspaceDeleted;
        ProjectFileAccessProvider.WorkspaceAccessType backupWorkspaceType = ProjectFileAccessProvider.WorkspaceAccessType.BACKUP;
        try
        {
            backupWorkspaceDeleted = GitLabApiTools.deleteBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(), getWorkspaceBranchName(WorkspaceSpecification.newWorkspaceSpecification(workspaceSpecification.getWorkspaceId(), workspaceSpecification.getWorkspaceType(), backupWorkspaceType, workspaceSpecification.getPatchReleaseVersionId())), 20, 1_000);
        }
        catch (Exception e)
        {
            throw buildException(e,
                () -> "User " + getCurrentUser() + " is not allowed to delete " + workspaceSpecification.getWorkspaceType().getLabel() + " " + backupWorkspaceType.getLabel() + " " + workspaceSpecification.getWorkspaceId() + " in project " + projectId,
                () -> "Unknown " + workspaceSpecification.getWorkspaceType().getLabel() + " " + backupWorkspaceType.getLabel() + " (" + workspaceSpecification.getWorkspaceId() + ") or project (" + projectId + ")",
                () -> "Error deleting " + workspaceSpecification.getWorkspaceType().getLabel() + " " + backupWorkspaceType.getLabel() + " " + workspaceSpecification.getWorkspaceId() + " in project " + projectId);
        }
        if (!backupWorkspaceDeleted)
        {
            throw new LegendSDLCServerException("Failed to delete " + workspaceSpecification.getWorkspaceType().getLabel() + " " + backupWorkspaceType.getLabel() + " " + workspaceSpecification.getWorkspaceId() + " in project " + projectId);
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
        LegendSDLCServerException.validateNonNull(workspaceSpecification.getWorkspaceId(), "workspaceId may not be null");
        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        RepositoryApi repositoryApi = getGitLabApi().getRepositoryApi();
        ProjectFileAccessProvider.WorkspaceAccessType backupWorkspaceType = ProjectFileAccessProvider.WorkspaceAccessType.BACKUP;
        // Verify the backup exists
        try
        {
            withRetries(() -> repositoryApi.getBranch(gitLabProjectId.getGitLabId(), getWorkspaceBranchName(WorkspaceSpecification.newWorkspaceSpecification(workspaceSpecification.getWorkspaceId(), workspaceSpecification.getWorkspaceType(), backupWorkspaceType, workspaceSpecification.getPatchReleaseVersionId()))));
        }
        catch (Exception e)
        {
            if (GitLabApiTools.isNotFoundGitLabApiException(e))
            {
                LOGGER.error("No backup for workspace {} in project {}, so recovery is not possible", workspaceSpecification.getWorkspaceId(), projectId);
            }
            throw buildException(e,
                () -> "User " + getCurrentUser() + " is not allowed to get " + workspaceSpecification.getWorkspaceType().getLabel() + " " + backupWorkspaceType.getLabel() + " " + workspaceSpecification.getWorkspaceId() + " in project " + projectId,
                () -> "Unknown " + workspaceSpecification.getWorkspaceType().getLabel() + " " + backupWorkspaceType.getLabel() + " with (" + workspaceSpecification.getWorkspaceId() + ") or project (" + projectId + "). " + "This implies that a backup does not exist for the specified workspace, hence recovery is not possible",
                () -> "Error getting " + workspaceSpecification.getWorkspaceType().getLabel() + " " + backupWorkspaceType.getLabel() + " " + workspaceSpecification.getWorkspaceId() + " in project " + projectId);
        }
        Branch existingBranch = null;
        ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType = ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE;
        // Check if branch exists
        try
        {
            existingBranch = withRetries(() -> repositoryApi.getBranch(gitLabProjectId.getGitLabId(), getWorkspaceBranchName(WorkspaceSpecification.newWorkspaceSpecification(workspaceSpecification.getWorkspaceId(), workspaceSpecification.getWorkspaceType(), workspaceAccessType, workspaceSpecification.getPatchReleaseVersionId()))));
        }
        catch (Exception e)
        {
            if (!GitLabApiTools.isNotFoundGitLabApiException(e))
            {
                LOGGER.error("Error getting {} {} in project {}", workspaceSpecification.getWorkspaceType().getLabel() + " " + workspaceAccessType.getLabel(), workspaceSpecification.getWorkspaceId(), projectId, e);
            }
        }
        if (existingBranch != null)
        {
            if (!forceRecovery)
            {
                throw new LegendSDLCServerException("Workspace " + workspaceSpecification.getWorkspaceId() + " of project " + projectId + " already existed and the recovery is not forced, so recovery from backup is not possible", Response.Status.METHOD_NOT_ALLOWED);
            }
            // Delete the existing branch
            boolean workspaceDeleted;
            try
            {
                workspaceDeleted = GitLabApiTools.deleteBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(), getWorkspaceBranchName(WorkspaceSpecification.newWorkspaceSpecification(workspaceSpecification.getWorkspaceId(), workspaceSpecification.getWorkspaceType(), workspaceAccessType, workspaceSpecification.getPatchReleaseVersionId())), 20, 1_000);
            }
            catch (Exception e)
            {
                throw buildException(e,
                    () -> "Error while attempting to recover backup for " + workspaceSpecification.getWorkspaceType().getLabel() + " " + workspaceAccessType.getLabel() + " " + workspaceSpecification.getWorkspaceId() + " in project " + projectId + ": User " + getCurrentUser() + " is not allowed to delete workspace",
                    () -> "Error while attempting to recover backup for " + workspaceSpecification.getWorkspaceType().getLabel() + " " + workspaceAccessType.getLabel() + " " + workspaceSpecification.getWorkspaceId() + " in project " + projectId + ": Unknown project: " + projectId,
                    () -> "Error while attempting to recover backup for " + workspaceSpecification.getWorkspaceType().getLabel() + " " + workspaceAccessType.getLabel() + " " + workspaceSpecification.getWorkspaceId() + " in project " + projectId + ": Error deleting workspace");
            }
            if (!workspaceDeleted)
            {
                throw new LegendSDLCServerException("Failed to delete " + workspaceSpecification.getWorkspaceType().getLabel() + " " + workspaceAccessType.getLabel() + " " + workspaceSpecification.getWorkspaceId() + " in project " + projectId);
            }
        }
        // Create new workspace branch off the backup branch head
        Branch workspaceBranch;
        try
        {
            workspaceBranch = GitLabApiTools.createBranchFromSourceBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(), getWorkspaceBranchName(WorkspaceSpecification.newWorkspaceSpecification(workspaceSpecification.getWorkspaceId(), workspaceSpecification.getWorkspaceType(), workspaceAccessType, workspaceSpecification.getPatchReleaseVersionId())), getWorkspaceBranchName(WorkspaceSpecification.newWorkspaceSpecification(workspaceSpecification.getWorkspaceId(), workspaceSpecification.getWorkspaceType(), backupWorkspaceType, workspaceSpecification.getPatchReleaseVersionId())), 30, 1_000);
        }
        catch (Exception e)
        {
            throw buildException(e,
                () -> "User " + getCurrentUser() + " is not allowed to create " + workspaceSpecification.getWorkspaceType().getLabel() + " " + workspaceAccessType.getLabel() + " " + workspaceSpecification.getWorkspaceId() + " in project " + projectId,
                () -> "Unknown project: " + projectId,
                () -> "Error creating " + workspaceSpecification.getWorkspaceType().getLabel() + " " + workspaceAccessType.getLabel() + " " + workspaceSpecification.getWorkspaceId() + " in project " + projectId);
        }
        if (workspaceBranch == null)
        {
            throw new LegendSDLCServerException("Failed to create " + workspaceSpecification.getWorkspaceType().getLabel() + " " + workspaceAccessType.getLabel() + " " + workspaceSpecification.getWorkspaceId() + " in project " + projectId + " from " + workspaceSpecification.getWorkspaceType().getLabel() + " " + backupWorkspaceType.getLabel() + " " + workspaceSpecification.getWorkspaceId());
        }
        // Delete backup branch
        try
        {
            boolean deleted = GitLabApiTools.deleteBranchAndVerify(repositoryApi, gitLabProjectId.getGitLabId(), getWorkspaceBranchName(WorkspaceSpecification.newWorkspaceSpecification(workspaceSpecification.getWorkspaceId(), workspaceSpecification.getWorkspaceType(), backupWorkspaceType, workspaceSpecification.getPatchReleaseVersionId())), 20, 1_000);
            if (!deleted)
            {
                LOGGER.error("Failed to delete {} {} in project {}", workspaceSpecification.getWorkspaceType().getLabel() + " " + backupWorkspaceType.getLabel(), workspaceSpecification.getWorkspaceId(), projectId);
            }
        }
        catch (Exception e)
        {
            // unfortunate, but this should not throw error
            LOGGER.error("Error deleting {} {} in project {} after recovery is completed", workspaceSpecification.getWorkspaceType().getLabel() + " " + backupWorkspaceType.getLabel(), workspaceSpecification.getWorkspaceId(), projectId, e);
        }
    }
}
