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

package org.finos.legend.sdlc.server.inmemory.backend.api;

import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceApi;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;

import javax.inject.Inject;
import java.util.List;
import java.util.Set;

public class InMemoryWorkspaceApi implements WorkspaceApi
{
    @Inject
    public InMemoryWorkspaceApi()
    {
    }

    @Override
    public List<Workspace> getUserWorkspaces(String projectId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public List<Workspace> getGroupWorkspaces(String projectId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public List<Workspace> getWorkspaces(String projectId, Set<ProjectFileAccessProvider.WorkspaceAccessType> workspaceAccessTypes)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public List<Workspace> getWorkspacesWithConflictResolution(String projectId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public List<Workspace> getBackupWorkspaces(String projectId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public List<Workspace> getAllWorkspaces(String projectId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public List<Workspace> getAllUserWorkspaces(String projectId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public List<Workspace> getAllWorkspaces(String projectId, Set<ProjectFileAccessProvider.WorkspaceAccessType> workspaceAccessTypes)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Workspace getUserWorkspace(String projectId, String workspaceId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Workspace getGroupWorkspace(String projectId, String workspaceId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Workspace getWorkspace(String projectId, String workspaceId, boolean isGroup)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Workspace getWorkspaceWithConflictResolution(String projectId, String workspaceId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Workspace getBackupWorkspace(String projectId, String workspaceId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public boolean isUserWorkspaceOutdated(String projectId, String workspaceId)
    {
        return false;
    }

    @Override
    public boolean isGroupWorkspaceOutdated(String projectId, String workspaceId)
    {
        return false;
    }

    @Override
    public boolean isWorkspaceOutdated(String projectId, String workspaceId, boolean isGroup)
    {
        return false;
    }

    @Override
    public boolean isWorkspaceWithConflictResolutionOutdated(String projectId, String workspaceId)
    {
        return false;
    }

    @Override
    public boolean isBackupWorkspaceOutdated(String projectId, String workspaceId)
    {
        return false;
    }

    @Override
    public boolean isUserWorkspaceInConflictResolutionMode(String projectId, String workspaceId)
    {
        return false;
    }

    @Override
    public boolean isGroupWorkspaceInConflictResolutionMode(String projectId, String workspaceId)
    {
        return false;
    }

    @Override
    public boolean isWorkspaceInConflictResolutionMode(String projectId, String workspaceId, boolean isGroup)
    {
        return false;
    }

    @Override
    public Workspace newUserWorkspace(String projectId, String workspaceId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Workspace newGroupWorkspace(String projectId, String workspaceId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Workspace newWorkspace(String projectId, String workspaceId, boolean isGroup)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void deleteUserWorkspace(String projectId, String workspaceId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void deleteGroupWorkspace(String projectId, String workspaceId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void deleteWorkspace(String projectId, String workspaceId, boolean isGroup)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public WorkspaceUpdateReport updateUserWorkspace(String projectId, String workspaceId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public WorkspaceUpdateReport updateGroupWorkspace(String projectId, String workspaceId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public WorkspaceUpdateReport updateWorkspace(String projectId, String workspaceId, boolean isGroup)
    {
        throw new UnsupportedOperationException("Not implemented");
    }
}
