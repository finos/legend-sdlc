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
import org.eclipse.collections.api.list.MutableList;
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceApi;
import org.finos.legend.sdlc.server.domain.api.project.SourceSpecification;
import org.finos.legend.sdlc.server.inmemory.backend.InMemoryBackend;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryProject;

import java.util.List;
import java.util.Set;
import javax.inject.Inject;

public class InMemoryWorkspaceApi implements WorkspaceApi
{
    private final InMemoryBackend backend;

    @Inject
    public InMemoryWorkspaceApi(InMemoryBackend inMemoryBackend)
    {
        this.backend = inMemoryBackend;
    }

    @Override
    public List<Workspace> getWorkspaces(String projectId, VersionId patchReleaseVersionId, Set<WorkspaceType> workspaceTypes)
    {
        InMemoryProject inMemoryProject = this.backend.getProject(projectId);
        MutableList<Workspace> result = Lists.mutable.empty();
        if (workspaceTypes.contains(WorkspaceType.GROUP))
        {
            result.addAllIterable(inMemoryProject.getGroupWorkspaces(patchReleaseVersionId));
        }
        if (workspaceTypes.contains(WorkspaceType.USER))
        {
            result.addAllIterable(inMemoryProject.getUserWorkspaces(patchReleaseVersionId));
        }
        return result;
    }

    @Override
    public List<Workspace> getWorkspacesWithConflictResolution(String projectId, VersionId patchReleaseVersionId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public List<Workspace> getBackupWorkspaces(String projectId, VersionId patchReleaseVersionId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public List<Workspace> getAllWorkspaces(String projectId, VersionId patchReleaseVersionId, Set<WorkspaceType> workspaceTypes)
    {
        return getWorkspaces(projectId, patchReleaseVersionId, workspaceTypes);
    }

    @Override
    public Workspace getWorkspace(String projectId, SourceSpecification sourceSpecification)
    {
        InMemoryProject inMemoryProject = this.backend.getProject(projectId);
        return sourceSpecification.getWorkspaceType() == WorkspaceType.GROUP ? inMemoryProject.getGroupWorkspace(sourceSpecification.getWorkspaceId(), sourceSpecification.getPatchReleaseVersionId()) : inMemoryProject.getUserWorkspace(sourceSpecification.getWorkspaceId(), sourceSpecification.getPatchReleaseVersionId());
    }

    @Override
    public Workspace getWorkspaceWithConflictResolution(String projectId, SourceSpecification sourceSpecification)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Workspace getBackupWorkspace(String projectId, SourceSpecification sourceSpecification)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public boolean isWorkspaceOutdated(String projectId, SourceSpecification sourceSpecification)
    {
        return false;
    }

    @Override
    public boolean isWorkspaceWithConflictResolutionOutdated(String projectId, SourceSpecification sourceSpecification)
    {
        return false;
    }

    @Override
    public boolean isBackupWorkspaceOutdated(String projectId, SourceSpecification sourceSpecification)
    {
        return false;
    }

    @Override
    public boolean isWorkspaceInConflictResolutionMode(String projectId, SourceSpecification sourceSpecification)
    {
        return false;
    }

    @Override
    public Workspace newWorkspace(String projectId, SourceSpecification sourceSpecification)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void deleteWorkspace(String projectId, SourceSpecification sourceSpecification)
    {
        InMemoryProject inMemoryProject = this.backend.getProject(projectId);
        if (sourceSpecification.getWorkspaceType() == WorkspaceType.GROUP)
        {
            inMemoryProject.deleteGroupWorkspace(sourceSpecification.getWorkspaceId(), sourceSpecification.getPatchReleaseVersionId());
        }
        else
        {
            inMemoryProject.deleteUserWorkspace(sourceSpecification.getWorkspaceId(), sourceSpecification.getPatchReleaseVersionId());
        }
    }

    @Override
    public WorkspaceUpdateReport updateWorkspace(String projectId, SourceSpecification sourceSpecification)
    {
        throw new UnsupportedOperationException("Not implemented");
    }
}
