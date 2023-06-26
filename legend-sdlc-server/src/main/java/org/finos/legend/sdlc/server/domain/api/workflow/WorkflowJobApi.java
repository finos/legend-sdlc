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

package org.finos.legend.sdlc.server.domain.api.workflow;

import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.server.domain.api.project.SourceSpecification;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;

import javax.ws.rs.core.Response;

public interface WorkflowJobApi
{
    WorkflowJobAccessContext getProjectWorkflowJobAccessContext(String projectId, VersionId patchReleaseVersionId);

    default WorkflowJobAccessContext getProjectWorkflowJobAccessContext(String projectId)
    {
        return this.getProjectWorkflowJobAccessContext(projectId, null);
    }

    WorkflowJobAccessContext getWorkspaceWorkflowJobAccessContext(String projectId, SourceSpecification sourceSpecification);

    default WorkflowJobAccessContext getWorkspaceWorkflowJobAccessContext(String projectId, String workspaceId, WorkspaceType workspaceType, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType)
    {
        return this.getWorkspaceWorkflowJobAccessContext(projectId, SourceSpecification.newSourceSpecification(workspaceId, workspaceType, workspaceAccessType));
    }

    default WorkflowJobAccessContext getVersionWorkflowJobAccessContext(String projectId, String versionIdString)
    {
        VersionId versionId;
        try
        {
            versionId = VersionId.parseVersionId(versionIdString);
        }
        catch (IllegalArgumentException e)
        {
            throw new LegendSDLCServerException(e.getMessage(), Response.Status.BAD_REQUEST, e);
        }
        return getVersionWorkflowJobAccessContext(projectId, versionId);
    }

    WorkflowJobAccessContext getVersionWorkflowJobAccessContext(String projectId, VersionId versionId);

    WorkflowJobAccessContext getReviewWorkflowJobAccessContext(String projectId, VersionId patchReleaseVersionId, String reviewId);

    default WorkflowJobAccessContext getReviewWorkflowJobAccessContext(String projectId, String reviewId)
    {
        return this.getReviewWorkflowJobAccessContext(projectId, null, reviewId);
    }
}
