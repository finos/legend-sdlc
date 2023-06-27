// Copyright 2023 Goldman Sachs
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

package org.finos.legend.sdlc.server.backend.simple.api.workspace;

import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.server.backend.simple.state.SimpleBackendState;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceApi;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class SimpleBackendWorkspaceApi implements WorkspaceApi
{
    private SimpleBackendState simpleBackendState;

    @Inject
    public SimpleBackendWorkspaceApi(SimpleBackendState simpleBackendState)
    {
        this.simpleBackendState = simpleBackendState;
    }

    @Override
    public List<Workspace> getWorkspaces(String projectId, Set<WorkspaceType> workspaceTypes)
    {
        return this.simpleBackendState.getProject(projectId).getWorkspaces(workspaceTypes);
    }

    @Override
    public List<Workspace> getWorkspacesWithConflictResolution(String projectId)
    {
        return Collections.emptyList();
    }

    @Override
    public List<Workspace> getBackupWorkspaces(String projectId)
    {
        return Collections.emptyList();
    }

    @Override
    public List<Workspace> getAllWorkspaces(String projectId, Set<WorkspaceType> workspaceTypes)
    {
        return this.getWorkspaces(projectId, workspaceTypes);
    }

    @Override
    public Workspace getWorkspace(String projectId, String workspaceId, WorkspaceType workspaceType)
    {
        return this.simpleBackendState.getProject(projectId).getWorkspace(workspaceId, workspaceType);
    }

    @Override
    public Workspace getWorkspaceWithConflictResolution(String projectId, String workspaceId, WorkspaceType workspaceType)
    {
        return null;
    }

    @Override
    public Workspace getBackupWorkspace(String projectId, String workspaceId, WorkspaceType workspaceType)
    {
        return null;
    }

    @Override
    public boolean isWorkspaceOutdated(String projectId, String workspaceId, WorkspaceType workspaceType)
    {
        return false;
    }

    @Override
    public boolean isWorkspaceWithConflictResolutionOutdated(String projectId, String workspaceId, WorkspaceType workspaceType)
    {
        return false;
    }

    @Override
    public boolean isBackupWorkspaceOutdated(String projectId, String workspaceId, WorkspaceType workspaceType)
    {
        return false;
    }

    @Override
    public boolean isWorkspaceInConflictResolutionMode(String projectId, String workspaceId, WorkspaceType workspaceType)
    {
        return false;
    }

    @Override
    public Workspace newWorkspace(String projectId, String workspaceId, WorkspaceType workspaceType)
    {
        return this.simpleBackendState.getProject(projectId).newWorkspace(workspaceId, workspaceType);
    }

    @Override
    public void deleteWorkspace(String projectId, String workspaceId, WorkspaceType workspaceType)
    {
        this.simpleBackendState.getProject(projectId).deleteWorkspace(workspaceId, workspaceType);
    }

    @Override
    public WorkspaceUpdateReport updateWorkspace(String projectId, String workspaceId, WorkspaceType workspaceType)
    {
        return null;
    }
}
