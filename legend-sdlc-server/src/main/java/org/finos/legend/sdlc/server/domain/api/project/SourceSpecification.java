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

package org.finos.legend.sdlc.server.domain.api.project;

import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;

import java.util.Objects;

public class SourceSpecification
{
    private final String workspaceId;

    private final WorkspaceType workspaceType;

    private final ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType;

    private final VersionId patchReleaseVersionId;

    private SourceSpecification(String workspaceId, WorkspaceType workspaceType, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType, VersionId patchReleaseVersionId)
    {
        this.workspaceId = workspaceId;
        this.workspaceType = workspaceType;
        this.workspaceAccessType = workspaceAccessType == null ? ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE : workspaceAccessType;
        this.patchReleaseVersionId = patchReleaseVersionId;
        if ((this.workspaceId != null) && ((this.workspaceType == null) || (this.workspaceAccessType == null)))
        {
            throw new RuntimeException("workspace type and access type are required when workspace id is specified");
        }
    }

    public static SourceSpecification newGroupWorkspaceSourceSpecification(String workspaceId)
    {
        return new SourceSpecification(workspaceId, WorkspaceType.GROUP, null, null);
    }

    public static SourceSpecification newUserWorkspaceSourceSpecification(String workspaceId)
    {
        return new SourceSpecification(workspaceId, WorkspaceType.USER, null, null);
    }

    public static SourceSpecification newGroupWorkspaceSourceSpecification(String workspaceId, VersionId patchReleaseVersionId)
    {
        return new SourceSpecification(workspaceId, WorkspaceType.GROUP, null, patchReleaseVersionId);
    }

    public static SourceSpecification newUserWorkspaceSourceSpecification(String workspaceId, VersionId patchReleaseVersionId)
    {
        return new SourceSpecification(workspaceId, WorkspaceType.USER, null, patchReleaseVersionId);
    }

    public static SourceSpecification newGroupWorkspaceSourceSpecification(String workspaceId, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType, VersionId patchReleaseVersionId)
    {
        return new SourceSpecification(workspaceId, WorkspaceType.GROUP, workspaceAccessType, patchReleaseVersionId);
    }

    public static SourceSpecification newUserWorkspaceSourceSpecification(String workspaceId, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType, VersionId patchReleaseVersionId)
    {
        return new SourceSpecification(workspaceId, WorkspaceType.USER, workspaceAccessType, patchReleaseVersionId);
    }

    public static SourceSpecification newSourceSpecification(String workspaceId, WorkspaceType workspaceType)
    {
        return new SourceSpecification(workspaceId, workspaceType, null, null);
    }

    public static SourceSpecification newSourceSpecification(VersionId patchReleaseVersionId)
    {
        return new SourceSpecification(null, null, null, patchReleaseVersionId);
    }

    public static SourceSpecification newSourceSpecification(String workspaceId, WorkspaceType workspaceType, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType, VersionId patchReleaseVersionId)
    {
        return new SourceSpecification(workspaceId, workspaceType, workspaceAccessType, patchReleaseVersionId);
    }

    public static SourceSpecification newSourceSpecification(String workspaceId, WorkspaceType workspaceType, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType)
    {
        return new SourceSpecification(workspaceId, workspaceType, workspaceAccessType, null);
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
        if (!(other instanceof SourceSpecification))
        {
            return false;
        }

        SourceSpecification that = (SourceSpecification) other;
        return  Objects.equals(this.getWorkspaceId(), that.getWorkspaceId()) &&
                this.getWorkspaceType() == that.getWorkspaceType() &&
                this.getWorkspaceAccessType() == that.getWorkspaceAccessType() &&
                Objects.equals(this.getPatchReleaseVersionId(), that.getPatchReleaseVersionId());
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder("<SourceSpecification");
        if (getWorkspaceId() != null)
        {
            builder.append(" workspaceId=\"").append(getWorkspaceId()).append("\"");
        }
        if (getWorkspaceType() != null)
        {
            builder.append(" workspaceType=").append(getWorkspaceType());
        }
        if (getWorkspaceAccessType() != null)
        {
            builder.append(" workspaceAccessType=").append(getWorkspaceAccessType());
        }
        if (getPatchReleaseVersionId() != null)
        {
            builder.append(" patchReleaseVersionId=").append(getPatchReleaseVersionId());
        }
        return builder.append('>').toString();
    }
}
