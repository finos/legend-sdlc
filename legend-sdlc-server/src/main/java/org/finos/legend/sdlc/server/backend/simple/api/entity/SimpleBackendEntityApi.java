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

package org.finos.legend.sdlc.server.backend.simple.api.entity;

import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.backend.simple.exception.UnavailableFeature;
import org.finos.legend.sdlc.server.backend.simple.state.SimpleBackendState;
import org.finos.legend.sdlc.server.domain.api.entity.EntityAccessContext;
import org.finos.legend.sdlc.server.domain.api.entity.EntityApi;
import org.finos.legend.sdlc.server.domain.api.entity.EntityModificationContext;

import javax.inject.Inject;

public class SimpleBackendEntityApi implements EntityApi
{
    private SimpleBackendState simpleBackendState;

    @Inject
    public SimpleBackendEntityApi(SimpleBackendState simpleBackendState)
    {
        this.simpleBackendState = simpleBackendState;
    }

    @Override
    public EntityAccessContext getProjectEntityAccessContext(String projectId)
    {
        return this.simpleBackendState.getProject(projectId).getEntityAccessContext();
    }

    @Override
    public EntityAccessContext getProjectRevisionEntityAccessContext(String projectId, String revisionId)
    {
        return this.simpleBackendState.getProject(projectId).getEntityAccessContext();
    }

    @Override
    public EntityAccessContext getWorkspaceEntityAccessContext(String projectId, String workspaceId, WorkspaceType workspaceType)
    {
        return this.simpleBackendState.getProject(projectId).getWorkspace(workspaceId, workspaceType).getEntityAccessContext();
    }

    @Override
    public EntityAccessContext getBackupWorkspaceEntityAccessContext(String projectId, String workspaceId, WorkspaceType workspaceType)
    {
        throw UnavailableFeature.exception();
    }

    @Override
    public EntityAccessContext getWorkspaceWithConflictResolutionEntityAccessContext(String projectId, String workspaceId, WorkspaceType workspaceType)
    {
        throw UnavailableFeature.exception();
    }

    @Override
    public EntityAccessContext getWorkspaceRevisionEntityAccessContext(String projectId, String workspaceId, WorkspaceType workspaceType, String revisionId)
    {
        return this.simpleBackendState.getProject(projectId).getWorkspace(workspaceId, workspaceType).getEntityAccessContext();
    }

    @Override
    public EntityAccessContext getBackupWorkspaceRevisionEntityAccessContext(String projectId, String workspaceId, WorkspaceType workspaceType, String revisionId)
    {
        throw UnavailableFeature.exception();
    }

    @Override
    public EntityAccessContext getWorkspaceWithConflictResolutionRevisionEntityAccessContext(String projectId, String workspaceId, WorkspaceType workspaceType, String revisionId)
    {
        throw UnavailableFeature.exception();
    }

    @Override
    public EntityAccessContext getReviewFromEntityAccessContext(String projectId, String reviewId)
    {
        throw UnavailableFeature.exception();
    }

    @Override
    public EntityAccessContext getReviewToEntityAccessContext(String projectId, String reviewId)
    {
        throw UnavailableFeature.exception();
    }

    @Override
    public EntityAccessContext getVersionEntityAccessContext(String projectId, VersionId versionId)
    {
        throw UnavailableFeature.exception();
    }

    @Override
    public EntityModificationContext getWorkspaceEntityModificationContext(String projectId, String workspaceId, WorkspaceType workspaceType)
    {
        return this.simpleBackendState.getProject(projectId).getWorkspace(workspaceId, workspaceType).getEntityModificationContext();
    }

    @Override
    public EntityModificationContext getWorkspaceWithConflictResolutionEntityModificationContext(String projectId, String workspaceId, WorkspaceType workspaceType)
    {
        throw UnavailableFeature.exception();
    }
}
