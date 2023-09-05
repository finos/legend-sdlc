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

import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification;
import org.finos.legend.sdlc.server.domain.api.project.source.WorkspaceSourceSpecification;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSpecification;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;

public interface WorkflowJobApi
{
    WorkflowJobAccessContext getWorkflowJobAccessContext(String projectId, SourceSpecification sourceSpecification);

    WorkflowJobAccessContext getReviewWorkflowJobAccessContext(String projectId, String reviewId);

    // Deprecated APIs

    @Deprecated
    default WorkflowJobAccessContext getProjectWorkflowJobAccessContext(String projectId, VersionId patchReleaseVersionId)
    {
        return getWorkflowJobAccessContext(projectId, SourceSpecification.newSourceSpecification(patchReleaseVersionId));
    }

    @Deprecated
    default WorkflowJobAccessContext getProjectWorkflowJobAccessContext(String projectId)
    {
        return getWorkflowJobAccessContext(projectId, SourceSpecification.projectSourceSpecification());
    }

    @Deprecated
    default WorkflowJobAccessContext getWorkspaceWorkflowJobAccessContext(String projectId, SourceSpecification sourceSpecification)
    {
        if (!(sourceSpecification instanceof WorkspaceSourceSpecification))
        {
            throw new IllegalArgumentException("Not a workspace source specification: " + sourceSpecification);
        }
        return getWorkflowJobAccessContext(projectId, sourceSpecification);
    }

    @Deprecated
    default WorkflowJobAccessContext getWorkspaceWorkflowJobAccessContext(String projectId, String workspaceId, WorkspaceType workspaceType, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType)
    {
        return this.getWorkspaceWorkflowJobAccessContext(projectId, WorkspaceSpecification.newWorkspaceSpecification(workspaceId, workspaceType, workspaceAccessType).getSourceSpecification());
    }

    @Deprecated
    default WorkflowJobAccessContext getVersionWorkflowJobAccessContext(String projectId, String versionIdString)
    {
        return getWorkflowJobAccessContext(projectId, SourceSpecification.versionSourceSpecification(versionIdString));
    }

    @Deprecated
    default WorkflowJobAccessContext getVersionWorkflowJobAccessContext(String projectId, VersionId versionId)
    {
        return getWorkflowJobAccessContext(projectId, SourceSpecification.versionSourceSpecification(versionId));
    }

    @Deprecated
    default WorkflowJobAccessContext getReviewWorkflowJobAccessContext(String projectId, VersionId patchReleaseVersionId, String reviewId)
    {
        return getReviewWorkflowJobAccessContext(projectId, reviewId);
    }
}
