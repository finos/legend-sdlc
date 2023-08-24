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


package org.finos.legend.sdlc.domain.model.project;

import org.finos.legend.sdlc.domain.model.patch.Patch;
import org.finos.legend.sdlc.domain.model.version.VersionId;

import java.util.Objects;

public abstract class DevelopmentStream
{
    private static String type;

    public DevelopmentStream(String type)
    {
        this.type = Objects.requireNonNull(type, "developemnt stream type amy not be null");
    }

    public abstract <T> T visit(DevelopmentStreamVisitor<T> visitor);

    @Override
    public String toString()
    {
        return appendString(new StringBuilder()).toString();
    }

    public StringBuilder appendString(StringBuilder builder)
    {
        return appendAdditionalInfo(builder.append("<").append(getClass().getSimpleName())).append('>');
    }

    protected abstract StringBuilder appendAdditionalInfo(StringBuilder builder);

    public String getType()
    {
        return this.type;
    }

    public static ProjectDevelopmentStream projectDevelopmentStream()
    {
        return ProjectDevelopmentStream.INSTANCE;
    }

    public static Patch patch(String projectId, VersionId patchReleaseVersionId)
    {
        return new Patch(projectId, patchReleaseVersionId);
    }

    public static Patch patch(String projectId, String patchReleaseVersionId)
    {
        return new Patch(projectId, VersionId.parseVersionId(patchReleaseVersionId));
    }
}
