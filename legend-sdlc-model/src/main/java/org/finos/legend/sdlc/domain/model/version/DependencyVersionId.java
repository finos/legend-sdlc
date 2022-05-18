// Copyright 2022 Goldman Sachs
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

package org.finos.legend.sdlc.domain.model.version;

public abstract class DependencyVersionId implements Comparable<DependencyVersionId>
{
    public abstract String getVersion();

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }

        if (!(other instanceof DependencyVersionId))
        {
            return false;
        }

        DependencyVersionId that = (DependencyVersionId)other;
        return (this.getVersion().equals(that.getVersion()));
    }

    @Override
    public int compareTo(DependencyVersionId other)
    {
        if (this == other)
        {
            return 0;
        }

        return this.getVersion().compareTo(other.getVersion());
    }

    @Override
    public String toString()
    {
        return getVersion();
    }

    @Override
    public int hashCode()
    {
        return this.getVersion().hashCode();
    }

    public static DependencyVersionId fromVersionString(String versionId)
    {
        return new DependencyVersionId()
        {
            @Override
            public String getVersion()
            {
                return versionId;
            }
        };
    }

    public static DependencyVersionId fromVersionId(VersionId versionId)
    {
        return fromVersionId(versionId, '.');
    }

    public static DependencyVersionId fromVersionId(VersionId versionId, char delimiter)
    {
        return new DependencyVersionId()
        {
            @Override
            public String getVersion()
            {
                return versionId.toVersionIdString(delimiter);
            }
        };
    }
}
