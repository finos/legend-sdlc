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


package org.finos.legend.sdlc.domain.model.patch;

import org.finos.legend.sdlc.domain.model.project.DevelopmentStream;
import org.finos.legend.sdlc.domain.model.project.DevelopmentStreamType;
import org.finos.legend.sdlc.domain.model.project.DevelopmentStreamVisitor;
import org.finos.legend.sdlc.domain.model.version.VersionId;

import java.util.Objects;

public class Patch extends DevelopmentStream
{
    private String projectId;
    private VersionId patchReleaseVersionId;

    public Patch(String projectId, VersionId patchReleaseVersionId)
    {
        super(DevelopmentStreamType.PATCH.toString());
        this.projectId = Objects.requireNonNull(projectId, "projectId may not be null");
        this.patchReleaseVersionId = Objects.requireNonNull(patchReleaseVersionId, "patchReleaseVersionId is not null");
    }

    public Patch()
    {
        super(DevelopmentStreamType.PATCH.toString());
    }

    public String getProjectId()
    {
        return this.projectId;
    }

    public VersionId getPatchReleaseVersionId()
    {
        return this.patchReleaseVersionId;
    }

    @Override
    public <T> T visit(DevelopmentStreamVisitor<T> visitor)
    {
        return visitor.visit(this);
    }

    @Override
    public boolean equals(Object other)
    {
        return (this == other) ||
                ((other instanceof Patch) && this.patchReleaseVersionId.equals(((Patch) other).patchReleaseVersionId));
    }

    @Override
    public int hashCode()
    {
        return this.patchReleaseVersionId.hashCode();
    }

    @Override
    protected StringBuilder appendAdditionalInfo(StringBuilder builder)
    {
        return this.patchReleaseVersionId.appendVersionIdString(builder.append(" patchVersion="));
    }

}