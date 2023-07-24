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

package org.finos.legend.sdlc.server.backend.simple.api.revision;

import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.revision.RevisionStatus;
import org.finos.legend.sdlc.server.backend.simple.exception.UnavailableFeature;
import org.finos.legend.sdlc.server.backend.simple.state.SimpleBackendState;
import org.finos.legend.sdlc.server.domain.api.revision.RevisionAccessContext;
import org.finos.legend.sdlc.server.domain.api.revision.RevisionApi;

import javax.inject.Inject;

public class SimpleBackendRevisionApi implements RevisionApi
{
    private SimpleBackendState simpleBackendState;

    @Inject
    public SimpleBackendRevisionApi(SimpleBackendState simpleBackendState)
    {
        this.simpleBackendState = simpleBackendState;
    }

    @Override
    public RevisionAccessContext getProjectRevisionContext(String projectId)
    {
        return simpleBackendState.getProject(projectId).getRevisionAccessContext();
    }

    @Override
    public RevisionAccessContext getProjectEntityRevisionContext(String projectId, String entityPath)
    {
        return simpleBackendState.getProject(projectId).getRevisionAccessContext();
    }

    @Override
    public RevisionAccessContext getProjectPackageRevisionContext(String projectId, String packagePath)
    {
        return simpleBackendState.getProject(projectId).getRevisionAccessContext();
    }

    @Override
    public RevisionAccessContext getWorkspaceRevisionContext(String projectId, String workspaceId, WorkspaceType workspaceType)
    {
        return simpleBackendState.getProject(projectId).getWorkspace(workspaceId, workspaceType).getRevisionAccessContext();
    }

    @Override
    public RevisionAccessContext getBackupWorkspaceRevisionContext(String projectId, String workspaceId, WorkspaceType workspaceType)
    {
        throw UnavailableFeature.exception();
    }

    @Override
    public RevisionAccessContext getWorkspaceWithConflictResolutionRevisionContext(String projectId, String workspaceId, WorkspaceType workspaceType)
    {
        throw UnavailableFeature.exception();
    }

    @Override
    public RevisionAccessContext getWorkspaceEntityRevisionContext(String projectId, String workspaceId, WorkspaceType workspaceType, String entityPath)
    {
        return simpleBackendState.getProject(projectId).getWorkspace(workspaceId, workspaceType).getRevisionAccessContext();
    }

    @Override
    public RevisionAccessContext getWorkspacePackageRevisionContext(String projectId, String workspaceId, WorkspaceType workspaceType, String packagePath)
    {
        return simpleBackendState.getProject(projectId).getWorkspace(workspaceId, workspaceType).getRevisionAccessContext();
    }

    @Override
    public RevisionStatus getRevisionStatus(String projectId, String revisionId)
    {
        throw UnavailableFeature.exception();
    }
}
