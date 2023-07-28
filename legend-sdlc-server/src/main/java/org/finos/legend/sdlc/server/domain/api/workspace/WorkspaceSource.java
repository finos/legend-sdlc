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

import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification;

public abstract class WorkspaceSource
{
    WorkspaceSource()
    {
    }

    public abstract SourceSpecification getSourceSpecification();

    public abstract <T> T visit(WorkspaceSourceVisitor<T> visitor);

    @Override
    public String toString()
    {
        return appendString(new StringBuilder()).toString();
    }

    StringBuilder appendString(StringBuilder builder)
    {
        return appendAdditionalInfo(builder.append("<").append(getClass().getSimpleName())).append('>');
    }

    protected abstract StringBuilder appendAdditionalInfo(StringBuilder builder);

    public static ProjectWorkspaceSource projectWorkspaceSource()
    {
        return ProjectWorkspaceSource.INSTANCE;
    }

    public static PatchWorkspaceSource patchWorkspaceSource(String patch)
    {
        return patchWorkspaceSource(VersionId.parseVersionId(patch));
    }

    public static PatchWorkspaceSource patchWorkspaceSource(VersionId patch)
    {
        return new PatchWorkspaceSource(patch);
    }
}
