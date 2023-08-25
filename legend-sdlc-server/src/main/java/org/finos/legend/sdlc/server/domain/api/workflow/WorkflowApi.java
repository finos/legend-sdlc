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

public interface WorkflowApi
{
    WorkflowAccessContext getWorkflowAccessContext(String projectId, SourceSpecification sourceSpecification);

    WorkflowAccessContext getReviewWorkflowAccessContext(String projectId, String reviewId);

    // Deprecated APIs

    @Deprecated
    default WorkflowAccessContext getProjectWorkflowAccessContext(String projectId, VersionId patchReleaseVersionId)
    {
        return getWorkflowAccessContext(projectId, SourceSpecification.newSourceSpecification(patchReleaseVersionId));
    }

    @Deprecated
    default WorkflowAccessContext getProjectWorkflowAccessContext(String projectId)
    {
        return getWorkflowAccessContext(projectId, SourceSpecification.projectSourceSpecification());
    }

    @Deprecated
    default WorkflowAccessContext getWorkspaceWorkflowAccessContext(String projectId, SourceSpecification sourceSpecification)
    {
        if (!(sourceSpecification instanceof WorkspaceSourceSpecification))
        {
            throw new IllegalArgumentException("Not a workspace source specification: " + sourceSpecification);
        }
        return getWorkflowAccessContext(projectId, sourceSpecification);
    }

    @Deprecated
    default WorkflowAccessContext getWorkspaceWorkflowAccessContext(String projectId, String workspaceId, WorkspaceType workspaceType, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType)
    {
        return getWorkflowAccessContext(projectId, WorkspaceSpecification.newWorkspaceSpecification(workspaceId, workspaceType, workspaceAccessType).getSourceSpecification());
    }

    @Deprecated
    default WorkflowAccessContext getVersionWorkflowAccessContext(String projectId, String versionIdString)
    {
        return getWorkflowAccessContext(projectId, SourceSpecification.versionSourceSpecification(versionIdString));
    }

    @Deprecated
    default WorkflowAccessContext getVersionWorkflowAccessContext(String projectId, VersionId versionId)
    {
        return getWorkflowAccessContext(projectId, SourceSpecification.versionSourceSpecification(versionId));
    }

    @Deprecated
    default WorkflowAccessContext getReviewWorkflowAccessContext(String projectId, VersionId patchReleaseVersionId, String reviewId)
    {
        return getReviewWorkflowAccessContext(projectId, reviewId);
    }
}
