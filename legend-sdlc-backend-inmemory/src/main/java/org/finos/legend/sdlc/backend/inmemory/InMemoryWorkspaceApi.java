// Copyright 2026 Goldman Sachs
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

package org.finos.legend.sdlc.backend.inmemory;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.sdlc.backend.api.spi.BackendCapability;
import org.finos.legend.sdlc.backend.api.workspace.WorkspaceApi;
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.error.LegendSDLCException;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider.WorkspaceAccessType;
import org.finos.legend.sdlc.project.source.SourceSpecification;
import org.finos.legend.sdlc.project.workspace.WorkspaceSource;
import org.finos.legend.sdlc.project.workspace.WorkspaceSpecification;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Workspace lifecycle over the in-memory registry and storage provider: a workspace is a provider branch plus
 * an ownership entry. Only project-sourced user workspaces with the standard access type exist (the backend
 * declares {@code USER_WORKSPACES} only). A workspace that has fallen behind its source cannot be updated —
 * the in-memory VCS has no source-into-branch merge — so {@code updateWorkspace} reports {@code NO_OP} when the
 * workspace is current and fails with 501 otherwise.
 */
public class InMemoryWorkspaceApi implements WorkspaceApi
{
    private final InMemoryBackend backend;
    private final String userId;

    InMemoryWorkspaceApi(InMemoryBackend backend, String userId)
    {
        this.backend = backend;
        this.userId = userId;
    }

    @Override
    public Workspace getWorkspace(String projectId, WorkspaceSpecification workspaceSpecification)
    {
        InMemoryBackend.WorkspaceState state = resolveWorkspace(projectId, workspaceSpecification);
        if (state == null)
        {
            throw new LegendSDLCException("Unknown workspace in project " + projectId + ": " + workspaceSpecification, 404);
        }
        return toWorkspace(projectId, state);
    }

    @Override
    public List<Workspace> getWorkspaces(String projectId, Set<WorkspaceType> types, Set<WorkspaceAccessType> accessTypes, Set<WorkspaceSource> sources)
    {
        return getWorkspaces(projectId, types, accessTypes, sources, this.userId);
    }

    @Override
    public List<Workspace> getAllWorkspaces(String projectId, Set<WorkspaceType> types, Set<WorkspaceAccessType> accessTypes, Set<WorkspaceSource> sources)
    {
        return getWorkspaces(projectId, types, accessTypes, sources, null);
    }

    @Override
    public Workspace newWorkspace(String projectId, String workspaceId, WorkspaceType type, WorkspaceSource source)
    {
        LegendSDLCException.validateNonNull(projectId, "projectId may not be null", 400);
        LegendSDLCException.validateNonNull(workspaceId, "workspaceId may not be null", 400);
        LegendSDLCException.validateNonNull(type, "type may not be null", 400);

        WorkspaceSpecification workspaceSpec = WorkspaceSpecification.newWorkspaceSpecification(workspaceId, type, null, source, this.userId);
        BackendCapability.checkSourceScope(this.backend, workspaceSpec.getSourceSpecification());

        InMemoryBackend.ProjectState project = this.backend.getProject(projectId);
        synchronized (this.backend)
        {
            InMemoryBackend.WorkspaceState state = new InMemoryBackend.WorkspaceState(workspaceId, this.userId);
            project.addWorkspace(state);
            this.backend.getFileAccessProvider().createWorkspace(projectId, workspaceId);
            return toWorkspace(projectId, state);
        }
    }

    @Override
    public void deleteWorkspace(String projectId, WorkspaceSpecification workspaceSpecification)
    {
        InMemoryBackend.WorkspaceState state = resolveWorkspace(projectId, workspaceSpecification);
        if (state == null)
        {
            throw new LegendSDLCException("Unknown workspace in project " + projectId + ": " + workspaceSpecification, 404);
        }
        synchronized (this.backend)
        {
            this.backend.getFileAccessProvider().deleteWorkspace(projectId, state.getWorkspaceId());
            this.backend.getProject(projectId).removeWorkspace(state.getWorkspaceId());
        }
    }

    @Override
    public boolean isWorkspaceOutdated(String projectId, WorkspaceSpecification workspaceSpecification)
    {
        InMemoryBackend.WorkspaceState state = resolveWorkspace(projectId, workspaceSpecification);
        if (state == null)
        {
            throw new LegendSDLCException("Unknown workspace in project " + projectId + ": " + workspaceSpecification, 404);
        }
        Revision base = getBaseRevision(projectId, state);
        Revision sourceCurrent = this.backend.getFileAccessProvider()
                .getRevisionAccessContext(projectId, SourceSpecification.projectSourceSpecification())
                .getCurrentRevision();
        return (sourceCurrent != null) && ((base == null) || !sourceCurrent.getId().equals(base.getId()));
    }

    @Override
    public boolean isWorkspaceInConflictResolutionMode(String projectId, WorkspaceSpecification workspaceSpecification)
    {
        return false;
    }

    @Override
    public WorkspaceUpdateReport updateWorkspace(String projectId, WorkspaceSpecification workspaceSpecification)
    {
        InMemoryBackend.WorkspaceState state = resolveWorkspace(projectId, workspaceSpecification);
        if (state == null)
        {
            throw new LegendSDLCException("Unknown workspace in project " + projectId + ": " + workspaceSpecification, 404);
        }
        if (isWorkspaceOutdated(projectId, workspaceSpecification))
        {
            throw new LegendSDLCException("Updating an outdated workspace is not supported by the in-memory backend", 501);
        }
        Revision base = getBaseRevision(projectId, state);
        Revision current = this.backend.getFileAccessProvider()
                .getRevisionAccessContext(projectId, workspaceSourceSpecification(state))
                .getCurrentRevision();
        String baseId = (base == null) ? null : base.getId();
        String currentId = (current == null) ? null : current.getId();
        return new WorkspaceUpdateReport()
        {
            @Override
            public WorkspaceUpdateReportStatus getStatus()
            {
                return WorkspaceUpdateReportStatus.NO_OP;
            }

            @Override
            public String getWorkspaceMergeBaseRevisionId()
            {
                return baseId;
            }

            @Override
            public String getWorkspaceRevisionId()
            {
                return currentId;
            }
        };
    }

    private List<Workspace> getWorkspaces(String projectId, Set<WorkspaceType> types, Set<WorkspaceAccessType> accessTypes, Set<WorkspaceSource> sources, String ownerId)
    {
        LegendSDLCException.validateNonNull(projectId, "projectId may not be null", 400);
        if (((types != null) && !types.contains(WorkspaceType.USER))
                || ((accessTypes != null) && !accessTypes.contains(WorkspaceAccessType.WORKSPACE))
                || ((sources != null) && !sources.contains(WorkspaceSource.projectWorkspaceSource())))
        {
            return Lists.mutable.empty();
        }
        List<InMemoryBackend.WorkspaceState> states = this.backend.getProject(projectId)
                .getWorkspaces((ownerId == null) ? null : (state -> ownerId.equals(state.getUserId())));
        return Iterate.collect(states, state -> toWorkspace(projectId, state), Lists.mutable.<Workspace>empty());
    }

    private InMemoryBackend.WorkspaceState resolveWorkspace(String projectId, WorkspaceSpecification workspaceSpecification)
    {
        LegendSDLCException.validateNonNull(projectId, "projectId may not be null", 400);
        LegendSDLCException.validateNonNull(workspaceSpecification, "workspace specification may not be null", 400);
        BackendCapability.checkSourceScope(this.backend, workspaceSpecification.getSourceSpecification());

        InMemoryBackend.WorkspaceState state = this.backend.getProject(projectId).findWorkspace(workspaceSpecification.getId());
        String specUserId = (workspaceSpecification.getUserId() == null) ? this.userId : workspaceSpecification.getUserId();
        return ((state == null) || !Objects.equals(state.getUserId(), specUserId)) ? null : state;
    }

    private Revision getBaseRevision(String projectId, InMemoryBackend.WorkspaceState state)
    {
        return this.backend.getFileAccessProvider()
                .getRevisionAccessContext(projectId, workspaceSourceSpecification(state))
                .getBaseRevision();
    }

    private SourceSpecification workspaceSourceSpecification(InMemoryBackend.WorkspaceState state)
    {
        return WorkspaceSpecification.newWorkspaceSpecification(state.getWorkspaceId(), WorkspaceType.USER, null, null, state.getUserId()).getSourceSpecification();
    }

    private Workspace toWorkspace(String projectId, InMemoryBackend.WorkspaceState state)
    {
        String workspaceId = state.getWorkspaceId();
        String workspaceUserId = state.getUserId();
        return new Workspace()
        {
            @Override
            public String getProjectId()
            {
                return projectId;
            }

            @Override
            public String getUserId()
            {
                return workspaceUserId;
            }

            @Override
            public String getWorkspaceId()
            {
                return workspaceId;
            }
        };
    }
}
