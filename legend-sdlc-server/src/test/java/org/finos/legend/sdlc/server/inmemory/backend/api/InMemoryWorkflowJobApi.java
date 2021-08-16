// Copyright 2021 Goldman Sachs
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

import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.server.domain.api.workflow.WorkflowJobAccessContext;
import org.finos.legend.sdlc.server.domain.api.workflow.WorkflowJobApi;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;

import javax.inject.Inject;

public class InMemoryWorkflowJobApi implements WorkflowJobApi
{
    @Inject
    public InMemoryWorkflowJobApi()
    {
    }

    @Override
    public WorkflowJobAccessContext getProjectWorkflowJobAccessContext(String projectId)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public WorkflowJobAccessContext getWorkspaceWorkflowJobAccessContext(String projectId, String workspaceId, WorkspaceType workspaceType, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public WorkflowJobAccessContext getVersionWorkflowJobAccessContext(String projectId, VersionId versionId)
    {
        throw new UnsupportedOperationException();
    }
}
