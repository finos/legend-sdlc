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
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceApi;
import org.finos.legend.sdlc.server.inmemory.backend.InMemoryBackend;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryProject;

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
        return this.getWorkspaces(projectId, Collections.unmodifiableSet(EnumSet.of(WorkspaceType.USER)));
    }

    @Override
    public List<Workspace> getGroupWorkspaces(String projectId)
    {
        return this.getWorkspaces(projectId, Collections.unmodifiableSet(EnumSet.of(WorkspaceType.GROUP)));
    }

    @Override
    public List<Workspace> getWorkspaces(String projectId, Set<WorkspaceType> workspaceTypes)
    {
        InMemoryProject inMemoryProject = this.backend.getProject(projectId);
        List<Workspace> result = Lists.mutable.empty();
        if (workspaceTypes.contains(WorkspaceType.GROUP))
        {
            result.addAll(Lists.mutable.withAll(inMemoryProject.getGroupWorkspaces()));
        }
        if (workspaceTypes.contains(WorkspaceType.USER))
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
    public List<Workspace> getAllWorkspaces(String projectId, Set<WorkspaceType> workspaceTypes)
    {
        return this.getWorkspaces(projectId, workspaceTypes);
    }

    @Override
    public Workspace getWorkspace(String projectId, String workspaceId, WorkspaceType workspaceType)
    {
        InMemoryProject inMemoryProject = this.backend.getProject(projectId);
        return workspaceType == WorkspaceType.GROUP ? inMemoryProject.getGroupWorkspace(workspaceId) : inMemoryProject.getUserWorkspace(workspaceId);
    }

    @Override
    public Workspace getWorkspaceWithConflictResolution(String projectId, String workspaceId, WorkspaceType workspaceType)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Workspace getBackupWorkspace(String projectId, String workspaceId, WorkspaceType workspaceType)
    {
        throw new UnsupportedOperationException("Not implemented");
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
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void deleteWorkspace(String projectId, String workspaceId, WorkspaceType workspaceType)
    {
        InMemoryProject inMemoryProject = this.backend.getProject(projectId);
        if (workspaceType == WorkspaceType.GROUP)
        {
            inMemoryProject.deleteGroupWorkspace(workspaceId);
        }
        else
        {
            inMemoryProject.deleteUserWorkspace(workspaceId);
        }
    }

    @Override
    public WorkspaceUpdateReport updateWorkspace(String projectId, String workspaceId, WorkspaceType workspaceType)
    {
        throw new UnsupportedOperationException("Not implemented");
    }
}
