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

    public static WorkspaceSpecification newGroupWorkspaceSpecification(String workspaceId)
    {
        return new WorkspaceSpecification(workspaceId, WorkspaceType.GROUP, null, null);
    }

    public static WorkspaceSpecification newUserWorkspaceSpecification(String workspaceId)
    {
        return new WorkspaceSpecification(workspaceId, WorkspaceType.USER, null, null);
    }

    public static WorkspaceSpecification newGroupWorkspaceSpecification(String workspaceId, VersionId patchReleaseVersionId)
    {
        return new WorkspaceSpecification(workspaceId, WorkspaceType.GROUP, null, patchReleaseVersionId);
    }

    public static WorkspaceSpecification newUserWorkspaceSpecification(String workspaceId, VersionId patchReleaseVersionId)
    {
        return new WorkspaceSpecification(workspaceId, WorkspaceType.USER, null, patchReleaseVersionId);
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
        return new WorkspaceSpecification(workspaceId, workspaceType, null, null);
    }

    public static WorkspaceSpecification newWorkspaceSpecification(String workspaceId, WorkspaceType workspaceType, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType, VersionId patchReleaseVersionId)
    {
        return new WorkspaceSpecification(workspaceId, workspaceType, workspaceAccessType, patchReleaseVersionId);
    }

    public static WorkspaceSpecification newWorkspaceSpecification(String workspaceId, WorkspaceType workspaceType, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType)
    {
        return new WorkspaceSpecification(workspaceId, workspaceType, workspaceAccessType, null);
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
        return (this.getWorkspaceId().equals(that.getWorkspaceId())) &&
                this.getWorkspaceType() == that.getWorkspaceType() &&
                this.getWorkspaceAccessType() == that.getWorkspaceAccessType() &&
                Objects.equals(this.getPatchReleaseVersionId(), that.getPatchReleaseVersionId());
    }

    @Override
    public String toString()
    {
        return "<WorkspaceSpecification workspaceId=\"" + ((getWorkspaceId() == null) ? null : getWorkspaceId()) +
                "\" workspaceType=" + ((getWorkspaceType() == null) ? null : getWorkspaceType().toString()) +
                " workspaceAccessType=" + ((getWorkspaceAccessType() == null) ? null : getWorkspaceAccessType().toString()) +
                " patchReleaseVersionId=" + ((getPatchReleaseVersionId() == null) ? null : getPatchReleaseVersionId().toVersionIdString()) +
                ">";
    }
}
