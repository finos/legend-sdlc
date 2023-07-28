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

import java.util.Objects;

public class PatchSourceSpecification extends SourceSpecification
{
    private final VersionId versionId;

    PatchSourceSpecification(VersionId versionId)
    {
        this.versionId = Objects.requireNonNull(versionId, "patch version id is required");
    }

    @Override
    public <T> T visit(SourceSpecificationVisitor<T> visitor)
    {
        return visitor.visit(this);
    }

    public VersionId getVersionId()
    {
        return this.versionId;
    }

    @Override
    public boolean equals(Object other)
    {
        return (this == other) ||
                ((other instanceof PatchSourceSpecification) && this.versionId.equals(((PatchSourceSpecification) other).versionId));
    }

    @Override
    public int hashCode()
    {
        return this.versionId.hashCode();
    }

    @Override
    protected StringBuilder appendAdditionalInfo(StringBuilder builder)
    {
        return this.versionId.appendVersionIdString(builder.append(" patch="));
    }
}
