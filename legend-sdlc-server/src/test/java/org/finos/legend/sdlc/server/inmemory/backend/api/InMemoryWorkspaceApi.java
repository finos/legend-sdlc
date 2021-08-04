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

import org.eclipse.collections.api.factory.Lists;
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceApi;
import org.finos.legend.sdlc.server.inmemory.backend.InMemoryBackend;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryProject;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;

import javax.inject.Inject;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class InMemoryWorkspaceApi implements WorkspaceApi
{
    private final InMemoryBackend backend;

    @Inject
    public InMemoryWorkspaceApi(InMemoryBackend inMemoryBackend)
    {
        this.backend = inMemoryBackend;
    }

    @Override
    public List<Workspace> getUserWorkspaces(String projectId)
    {
        return this.getWorkspaces(projectId, Collections.unmodifiableSet(EnumSet.of(ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE)));
    }

    @Override
    public List<Workspace> getGroupWorkspaces(String projectId)
    {
        return this.getWorkspaces(projectId, Collections.unmodifiableSet(EnumSet.of(ProjectFileAccessProvider.WorkspaceAccessType.GROUP)));
    }

    @Override
    public List<Workspace> getWorkspaces(String projectId, Set<ProjectFileAccessProvider.WorkspaceAccessType> workspaceAccessTypes)
    {
        InMemoryProject inMemoryProject = this.backend.getProject(projectId);
        List<Workspace> result = Lists.mutable.empty();
        if (workspaceAccessTypes.contains(ProjectFileAccessProvider.WorkspaceAccessType.GROUP))
        {
            result.addAll(Lists.mutable.withAll(inMemoryProject.getGroupWorkspaces()));
        }
        if (workspaceAccessTypes.contains(ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE))
        {
            result.addAll(Lists.mutable.withAll(inMemoryProject.getUserWorkspaces()));
        }
        return result;
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
        return this.getAllWorkspaces(projectId, Collections.unmodifiableSet(EnumSet.of(ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE, ProjectFileAccessProvider.WorkspaceAccessType.GROUP)));
    }

    @Override
    public List<Workspace> getAllUserWorkspaces(String projectId)
    {
        return this.getAllWorkspaces(projectId, Collections.unmodifiableSet(EnumSet.of(ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE)));
    }

    @Override
    public List<Workspace> getAllWorkspaces(String projectId, Set<ProjectFileAccessProvider.WorkspaceAccessType> workspaceAccessTypes)
    {
        return this.getWorkspaces(projectId, workspaceAccessTypes);
    }

    @Override
    public Workspace getUserWorkspace(String projectId, String workspaceId)
    {
        return this.getWorkspace(projectId, workspaceId, false);
    }

    @Override
    public Workspace getGroupWorkspace(String projectId, String workspaceId)
    {
        return this.getWorkspace(projectId, workspaceId, true);
    }

    @Override
    public Workspace getWorkspace(String projectId, String workspaceId, boolean isGroupWorkspace)
    {
        InMemoryProject inMemoryProject = this.backend.getProject(projectId);
        return isGroupWorkspace ? inMemoryProject.getGroupWorkspace(workspaceId) : inMemoryProject.getUserWorkspace(workspaceId);
    }

    @Override
    public Workspace getUserWorkspaceWithConflictResolution(String projectId, String workspaceId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Workspace getGroupWorkspaceWithConflictResolution(String projectId, String workspaceId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Workspace getWorkspaceWithConflictResolution(String projectId, String workspaceId, boolean isGroupWorkspace)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Workspace getBackupUserWorkspace(String projectId, String workspaceId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Workspace getBackupGroupWorkspace(String projectId, String workspaceId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Workspace getBackupWorkspace(String projectId, String workspaceId, boolean isGroupWorkspace)
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
    public boolean isWorkspaceOutdated(String projectId, String workspaceId, boolean isGroupWorkspace)
    {
        return false;
    }

    @Override
    public boolean isUserWorkspaceWithConflictResolutionOutdated(String projectId, String workspaceId)
    {
        return false;
    }

    @Override
    public boolean isGroupWorkspaceWithConflictResolutionOutdated(String projectId, String workspaceId)
    {
        return false;
    }

    @Override
    public boolean isWorkspaceWithConflictResolutionOutdated(String projectId, String workspaceId, boolean isGroupWorkspace)
    {
        return false;
    }

    @Override
    public boolean isBackupUserWorkspaceOutdated(String projectId, String workspaceId)
    {
        return false;
    }

    @Override
    public boolean isBackupGroupWorkspaceOutdated(String projectId, String workspaceId)
    {
        return false;
    }

    @Override
    public boolean isBackupWorkspaceOutdated(String projectId, String workspaceId, boolean isGroupWorkspace)
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
    public boolean isWorkspaceInConflictResolutionMode(String projectId, String workspaceId, boolean isGroupWorkspace)
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
    public Workspace newWorkspace(String projectId, String workspaceId, boolean isGroupWorkspace)
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
    public void deleteWorkspace(String projectId, String workspaceId, boolean isGroupWorkspace)
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
    public WorkspaceUpdateReport updateWorkspace(String projectId, String workspaceId, boolean isGroupWorkspace)
    {
        throw new UnsupportedOperationException("Not implemented");
    }
}
