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

package org.finos.legend.sdlc.server.domain.api.project.source;

import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSource;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSpecification;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;

public abstract class SourceSpecification
{
    SourceSpecification()
    {
    }

    public abstract <T> T visit(SourceSpecificationVisitor<T> visitor);

    @Deprecated
    public String getWorkspaceId()
    {
        if (this instanceof WorkspaceSourceSpecification)
        {
            return ((WorkspaceSourceSpecification) this).getWorkspaceSpecification().getId();
        }
        return null;
    }

    @Deprecated
    public final WorkspaceType getWorkspaceType()
    {
        if (this instanceof WorkspaceSourceSpecification)
        {
            return ((WorkspaceSourceSpecification) this).getWorkspaceSpecification().getType();
        }
        return null;
    }

    @Deprecated
    public final ProjectFileAccessProvider.WorkspaceAccessType getWorkspaceAccessType()
    {
        if (this instanceof WorkspaceSourceSpecification)
        {
            return ((WorkspaceSourceSpecification) this).getWorkspaceSpecification().getAccessType();
        }
        return null;
    }

    @Deprecated
    public final VersionId getPatchReleaseVersionId()
    {
        if (this instanceof PatchSourceSpecification)
        {
            return ((PatchSourceSpecification) this).getVersionId();
        }
        return null;
    }

    @Override
    public final String toString()
    {
        return appendAdditionalInfo(new StringBuilder("<").append(getClass().getSimpleName())).append('>').toString();
    }

    protected abstract StringBuilder appendAdditionalInfo(StringBuilder builder);

    public static ProjectSourceSpecification projectSourceSpecification()
    {
        return ProjectSourceSpecification.INSTANCE;
    }

    public static VersionSourceSpecification versionSourceSpecification(String versionId)
    {
        return versionSourceSpecification(VersionId.parseVersionId(versionId));
    }

    public static VersionSourceSpecification versionSourceSpecification(VersionId versionId)
    {
        return new VersionSourceSpecification(versionId);
    }

    public static PatchSourceSpecification patchSourceSpecification(VersionId versionId)
    {
        return new PatchSourceSpecification(versionId);
    }

    public static WorkspaceSourceSpecification workspaceSourceSpecification(WorkspaceSpecification workspaceSpec)
    {
        return new WorkspaceSourceSpecification(workspaceSpec);
    }

    @Deprecated
    public static SourceSpecification newSourceSpecification(String workspaceId, WorkspaceType workspaceType, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType, VersionId patchReleaseVersionId)
    {
        if (workspaceId != null)
        {
            WorkspaceSource workspaceSource = (patchReleaseVersionId == null) ? WorkspaceSource.projectWorkspaceSource() : WorkspaceSource.patchWorkspaceSource(patchReleaseVersionId);
            WorkspaceSpecification workspaceSpec = WorkspaceSpecification.newWorkspaceSpecification(workspaceId, workspaceType, workspaceAccessType, workspaceSource);
            return workspaceSourceSpecification(workspaceSpec);
        }
        if (patchReleaseVersionId != null)
        {
            return patchSourceSpecification(patchReleaseVersionId);
        }
        return projectSourceSpecification();
    }

    @Deprecated
    public static SourceSpecification newSourceSpecification(String workspaceId, WorkspaceType workspaceType, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType)
    {
        return newSourceSpecification(workspaceId, workspaceType, workspaceAccessType, null);
    }

    @Deprecated
    public static SourceSpecification newSourceSpecification(String workspaceId, WorkspaceType workspaceType)
    {
        return newSourceSpecification(workspaceId, workspaceType, null, null);
    }

    @Deprecated
    public static SourceSpecification newSourceSpecification(VersionId patchReleaseVersionId)
    {
        return newSourceSpecification(null, null, null, patchReleaseVersionId);
    }

    @Deprecated
    public static SourceSpecification newGroupWorkspaceSourceSpecification(String workspaceId, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType, VersionId patchReleaseVersionId)
    {
        return newSourceSpecification(workspaceId, WorkspaceType.GROUP, workspaceAccessType, patchReleaseVersionId);
    }

    @Deprecated
    public static SourceSpecification newGroupWorkspaceSourceSpecification(String workspaceId, VersionId patchReleaseVersionId)
    {
        return newGroupWorkspaceSourceSpecification(workspaceId, null, patchReleaseVersionId);
    }

    @Deprecated
    public static SourceSpecification newGroupWorkspaceSourceSpecification(String workspaceId)
    {
        return newGroupWorkspaceSourceSpecification(workspaceId, null);
    }

    @Deprecated
    public static SourceSpecification newUserWorkspaceSourceSpecification(String workspaceId, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType, VersionId patchReleaseVersionId)
    {
        return newSourceSpecification(workspaceId, WorkspaceType.USER, workspaceAccessType, patchReleaseVersionId);
    }

    @Deprecated
    public static SourceSpecification newUserWorkspaceSourceSpecification(String workspaceId, VersionId patchReleaseVersionId)
    {
        return newUserWorkspaceSourceSpecification(workspaceId, null, patchReleaseVersionId);
    }

    @Deprecated
    public static SourceSpecification newUserWorkspaceSourceSpecification(String workspaceId)
    {
        return newUserWorkspaceSourceSpecification(workspaceId, null);
    }
}
