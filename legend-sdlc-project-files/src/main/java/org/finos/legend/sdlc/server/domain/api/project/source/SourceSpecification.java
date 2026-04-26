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

import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSpecification;

public abstract class SourceSpecification
{
    SourceSpecification()
    {
    }

    public abstract <T> T visit(SourceSpecificationVisitor<T> visitor);

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
}
