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

package org.finos.legend.sdlc.server.domain.api.workspace;

import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;

import java.util.Objects;

public class WorkspaceSpecification
{
    private final String workspaceId;

    private final WorkspaceType workspaceType;

    private final ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType;

    private final VersionId patchReleaseVersionId;

    private WorkspaceSpecification(String workspaceId, WorkspaceType workspaceType, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType, VersionId patchReleaseVersionId)
    {
        this.workspaceId = workspaceId;
        this.workspaceType = workspaceType;
        this.workspaceAccessType = workspaceAccessType;
        this.patchReleaseVersionId = patchReleaseVersionId;
    }

    private WorkspaceSpecification(String workspaceId, WorkspaceType workspaceType, VersionId patchReleaseVersionId)
    {
        this.workspaceId = workspaceId;
        this.workspaceType = workspaceType;
        this.patchReleaseVersionId = patchReleaseVersionId;
        this.workspaceAccessType = null;
    }

    private WorkspaceSpecification(String workspaceId, WorkspaceType workspaceType, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType)
    {
        this.workspaceId = workspaceId;
        this.workspaceType = workspaceType;
        this.workspaceAccessType = workspaceAccessType;
        this.patchReleaseVersionId = null;
    }

    public WorkspaceSpecification(String workspaceId, WorkspaceType workspaceType)
    {
        this.workspaceId = workspaceId;
        this.workspaceType = workspaceType;
        this.patchReleaseVersionId = null;
        this.workspaceAccessType = null;
    }

    public static WorkspaceSpecification newGroupWorkspaceSpecification(String workspaceId)
    {
        return new WorkspaceSpecification(workspaceId, WorkspaceType.GROUP);
    }

    public static WorkspaceSpecification newUserWorkspaceSpecification(String workspaceId)
    {
        return new WorkspaceSpecification(workspaceId, WorkspaceType.USER);
    }

    public static WorkspaceSpecification newGroupWorkspaceSpecification(String workspaceId, VersionId patchReleaseVersionId)
    {
        return new WorkspaceSpecification(workspaceId, WorkspaceType.GROUP, patchReleaseVersionId);
    }

    public static WorkspaceSpecification newUserWorkspaceSpecification(String workspaceId, VersionId patchReleaseVersionId)
    {
        return new WorkspaceSpecification(workspaceId, WorkspaceType.USER, patchReleaseVersionId);
    }

    public static WorkspaceSpecification newGroupWorkspaceSpecification(String workspaceId, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType, VersionId patchReleaseVersionId)
    {
        return new WorkspaceSpecification(workspaceId, WorkspaceType.GROUP, workspaceAccessType, patchReleaseVersionId);
    }

    public static WorkspaceSpecification newUserWorkspaceSpecification(String workspaceId, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType, VersionId patchReleaseVersionId)
    {
        return new WorkspaceSpecification(workspaceId, WorkspaceType.USER, workspaceAccessType, patchReleaseVersionId);
    }

    public static WorkspaceSpecification newWorkspaceSpecification(String workspaceId, WorkspaceType workspaceType)
    {
        return new WorkspaceSpecification(workspaceId, workspaceType);
    }

    public static WorkspaceSpecification newWorkspaceSpecification(String workspaceId, WorkspaceType workspaceType, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType, VersionId patchReleaseVersionId)
    {
        return new WorkspaceSpecification(workspaceId, workspaceType, workspaceAccessType, patchReleaseVersionId);
    }

    public static WorkspaceSpecification newWorkspaceSpecification(String workspaceId, WorkspaceType workspaceType, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType)
    {
        return new WorkspaceSpecification(workspaceId, workspaceType, workspaceAccessType);
    }

    public String getWorkspaceId()
    {
        return this.workspaceId;
    }

    public WorkspaceType getWorkspaceType()
    {
        return this.workspaceType;
    }

    public ProjectFileAccessProvider.WorkspaceAccessType getWorkspaceAccessType()
    {
        return this.workspaceAccessType;
    }

    public VersionId getPatchReleaseVersionId()
    {
        return this.patchReleaseVersionId;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getWorkspaceId(), getWorkspaceType(), getWorkspaceAccessType(), getPatchReleaseVersionId());
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }
        if (!(other instanceof WorkspaceSpecification))
        {
            return false;
        }

        WorkspaceSpecification that = (WorkspaceSpecification) other;
        return (this.getWorkspaceId() == that.getWorkspaceId()) &&
                Objects.equals(this.getWorkspaceType(), that.getWorkspaceType()) &&
                Objects.equals(this.getWorkspaceAccessType(), that.getWorkspaceAccessType()) &&
                Objects.equals(this.getPatchReleaseVersionId(), that.getPatchReleaseVersionId());
    }

    @Override
    public String toString()
    {
        return "<WorkspaceSpecification workspaceId=" + ((getWorkspaceId() == null) ? null : getWorkspaceId()) +
                " workspaceType=" + getWorkspaceType().toString() +
                " workspaceAccessType=" + getWorkspaceAccessType().toString() +
                " patchReleaseVersionId=" + getPatchReleaseVersionId().toVersionIdString() +
                ">";
    }
}
