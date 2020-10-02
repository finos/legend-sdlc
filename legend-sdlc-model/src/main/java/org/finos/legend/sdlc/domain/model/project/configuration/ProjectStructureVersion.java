// Copyright 2020 Goldman Sachs
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

package org.finos.legend.sdlc.domain.model.project.configuration;

import java.util.Objects;

public abstract class ProjectStructureVersion implements Comparable<ProjectStructureVersion>
{
    protected static final char DELIMITER = '.';

    public abstract int getVersion();

    public abstract Integer getExtensionVersion();

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }

        if (!(other instanceof ProjectStructureVersion))
        {
            return false;
        }

        ProjectStructureVersion that = (ProjectStructureVersion)other;
        return (this.getVersion() == that.getVersion()) && Objects.equals(this.getExtensionVersion(), that.getExtensionVersion());
    }

    @Override
    public int hashCode()
    {
        return getVersion() + 17 * Objects.hashCode(getExtensionVersion());
    }

    @Override
    public int compareTo(ProjectStructureVersion other)
    {
        if (this == other)
        {
            return 0;
        }

        if (other == null)
        {
            return -1;
        }

        int cmp = Integer.compare(this.getVersion(), other.getVersion());
        if (cmp == 0)
        {
            Integer thisExtensionVersion = this.getExtensionVersion();
            Integer otherExtensionVersion = other.getExtensionVersion();
            cmp = (thisExtensionVersion == null) ? ((otherExtensionVersion == null) ? 0 : -1) : ((otherExtensionVersion == null) ? 1 : thisExtensionVersion.compareTo(otherExtensionVersion));
        }
        return cmp;
    }

    @Override
    public String toString()
    {
        return "<ProjectStructureVersion version=" + getVersion() + " extensionVersion=" + getExtensionVersion() + ">";
    }

    public String toVersionString()
    {
        return toVersionString(DELIMITER);
    }

    public String toVersionString(char delimiter)
    {
        return appendVersionString(new StringBuilder(), delimiter).toString();
    }

    public StringBuilder appendVersionString(StringBuilder builder)
    {
        return appendVersionString(builder, DELIMITER);
    }

    public StringBuilder appendVersionString(StringBuilder builder, char delimiter)
    {
        builder.append(getVersion());
        Integer extensionVersion = getExtensionVersion();
        if (extensionVersion != null)
        {
            builder.append(delimiter).append(extensionVersion.intValue());
        }
        return builder;
    }

    public static ProjectStructureVersion newProjectStructureVersion(int version, Integer extensionVersion)
    {
        return new ProjectStructureVersion()
        {
            @Override
            public int getVersion()
            {
                return version;
            }

            @Override
            public Integer getExtensionVersion()
            {
                return extensionVersion;
            }
        };
    }

    public static ProjectStructureVersion newProjectStructureVersion(int version)
    {
        return newProjectStructureVersion(version, null);
    }
}
