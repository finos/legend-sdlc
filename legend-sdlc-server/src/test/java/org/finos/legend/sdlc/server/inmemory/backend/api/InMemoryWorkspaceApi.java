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
import org.finos.legend.sdlc.server.domain.api.workspace.PatchWorkspaceSource;
import org.finos.legend.sdlc.server.domain.api.workspace.ProjectWorkspaceSource;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceApi;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSource;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSourceVisitor;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSpecification;
import org.finos.legend.sdlc.server.inmemory.backend.InMemoryBackend;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryProject;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;

import java.util.EnumSet;
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
    public Workspace getWorkspace(String projectId, WorkspaceSpecification workspaceSpec)
    {
        if (workspaceSpec.getAccessType() != ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE)
        {
            throw new UnsupportedOperationException("Not implemented");
        }
        InMemoryProject inMemoryProject = this.backend.getProject(projectId);
        VersionId patchVersion = workspaceSpec.getSource().visit(new WorkspaceSourceVisitor<VersionId>()
        {
            @Override
            public VersionId visit(ProjectWorkspaceSource source)
            {
                return null;
            }

            @Override
            public VersionId visit(PatchWorkspaceSource source)
            {
                return source.getPatchVersionId();
            }
        });
        return (workspaceSpec.getType() == WorkspaceType.GROUP) ?
                inMemoryProject.getGroupWorkspace(workspaceSpec.getId(), patchVersion) :
                inMemoryProject.getUserWorkspace(workspaceSpec.getId(), patchVersion);
    }

    @Override
    public List<Workspace> getWorkspaces(String projectId, Set<WorkspaceType> types, Set<ProjectFileAccessProvider.WorkspaceAccessType> accessTypes, Set<WorkspaceSource> sources)
    {
        if (sources == null)
        {
            throw new UnsupportedOperationException("Not implemented");
        }

        Set<WorkspaceType> resolvedTypes = (types == null) ? EnumSet.allOf(WorkspaceType.class) : types;
        Set<ProjectFileAccessProvider.WorkspaceAccessType> resolvedAccessTypes = (accessTypes == null) ? EnumSet.allOf(ProjectFileAccessProvider.WorkspaceAccessType.class) : accessTypes;

        InMemoryProject inMemoryProject = this.backend.getProject(projectId);
        MutableList<Workspace> result = Lists.mutable.empty();
        // currently only WORKSPACE access type is supported
        if (resolvedAccessTypes.contains(ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE))
        {
            for (WorkspaceSource source : sources)
            {
                VersionId patchVersionId = source.visit(new WorkspaceSourceVisitor<VersionId>()
                {
                    @Override
                    public VersionId visit(ProjectWorkspaceSource source)
                    {
                        return null;
                    }

                    @Override
                    public VersionId visit(PatchWorkspaceSource source)
                    {
                        return source.getPatchVersionId();
                    }
                });
                if (resolvedTypes.contains(WorkspaceType.GROUP))
                {
                    result.addAllIterable(inMemoryProject.getGroupWorkspaces(patchVersionId));
                }
                if (resolvedTypes.contains(WorkspaceType.USER))
                {
                    result.addAllIterable(inMemoryProject.getUserWorkspaces(patchVersionId));
                }
            }
        }
        return result;
    }

    @Override
    public List<Workspace> getAllWorkspaces(String projectId, Set<WorkspaceType> types, Set<ProjectFileAccessProvider.WorkspaceAccessType> accessTypes, Set<WorkspaceSource> sources)
    {
        return getWorkspaces(projectId, types, accessTypes, sources);
    }

    @Override
    public Workspace newWorkspace(String projectId, String workspaceId, WorkspaceType type, WorkspaceSource source)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void deleteWorkspace(String projectId, WorkspaceSpecification workspaceSpec)
    {
        InMemoryProject project = this.backend.getProject(projectId);
        VersionId patchVersion = workspaceSpec.getSource().visit(new WorkspaceSourceVisitor<VersionId>()
        {
            @Override
            public VersionId visit(ProjectWorkspaceSource source)
            {
                return null;
            }

            @Override
            public VersionId visit(PatchWorkspaceSource source)
            {
                return source.getPatchVersionId();
            }
        });
        if (workspaceSpec.getType() == WorkspaceType.GROUP)
        {
            project.deleteGroupWorkspace(workspaceSpec.getId(), patchVersion);
        }
        else
        {
            project.deleteUserWorkspace(workspaceSpec.getId(), patchVersion);
        }
    }

    @Override
    public boolean isWorkspaceOutdated(String projectId, WorkspaceSpecification workspaceSpecification)
    {
        // TODO implement
        return false;
    }

    @Override
    public boolean isWorkspaceInConflictResolutionMode(String projectId, WorkspaceSpecification workspaceSpecification)
    {
        // TODO implement
        return false;
    }

    @Override
    public WorkspaceUpdateReport updateWorkspace(String projectId, WorkspaceSpecification workspaceSpecification)
    {
        throw new UnsupportedOperationException("Not implemented");
    }
}
