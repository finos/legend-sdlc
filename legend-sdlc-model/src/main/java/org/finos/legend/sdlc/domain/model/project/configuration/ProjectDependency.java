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

import org.finos.legend.sdlc.domain.model.version.VersionId;

import java.util.Objects;

public abstract class ProjectDependency extends Dependency implements Comparable<ProjectDependency>
{
    public abstract String getProjectId();

    public abstract VersionId getVersionId();

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }

        if (!(other instanceof ProjectDependency))
        {
            return false;
        }

        ProjectDependency that = (ProjectDependency)other;
        return Objects.equals(this.getProjectId(), that.getProjectId()) && Objects.equals(this.getVersionId(), that.getVersionId());
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode(getProjectId()) + 53 * Objects.hashCode(getVersionId());
    }

    @Override
    public int compareTo(ProjectDependency other)
    {
        if (this == other)
        {
            return 0;
        }

        int cmp = comparePossiblyNull(this.getProjectId(), other.getProjectId());
        return (cmp != 0) ? cmp : comparePossiblyNull(this.getVersionId(), other.getVersionId());
    }

    @Override
    public StringBuilder appendDependencyIdString(StringBuilder builder)
    {
        return builder.append(getProjectId());
    }

    @Override
    public StringBuilder appendVersionIdString(StringBuilder builder)
    {
        VersionId versionId = getVersionId();
        return (versionId == null) ? builder.append("null") : versionId.appendVersionIdString(builder);
    }

    public static ProjectDependency parseProjectDependency(String string)
    {
        return parseProjectDependency(string, DEFAULT_DELIMITER);
    }

    public static ProjectDependency parseProjectDependency(String string, char delimiter)
    {
        if (string == null)
        {
            throw new IllegalArgumentException("Invalid project dependency string: null");
        }
        return parseProjectDependency(string, 0, string.length(), delimiter);
    }

    public static ProjectDependency parseProjectDependency(String string, int start, int end)
    {
        return parseProjectDependency(string, start, end, DEFAULT_DELIMITER);
    }

    public static ProjectDependency parseProjectDependency(String string, int start, int end, char delimiter)
    {
        if (string == null)
        {
            throw new IllegalArgumentException("Invalid project dependency string: null");
        }

        int delimiterIndex = string.indexOf(delimiter, start);
        if ((delimiterIndex == -1) || (delimiterIndex >= end))
        {
            throw new IllegalArgumentException(new StringBuilder("Invalid project dependency string: \"").append(string, start, end).append('"').toString());
        }

        String projectId = string.substring(start, delimiterIndex);
        VersionId versionId;
        try
        {
            versionId = VersionId.parseVersionId(string, delimiterIndex + 1, end);
        }
        catch (IllegalArgumentException e)
        {
            throw new IllegalArgumentException(new StringBuilder("Invalid project dependency string: \"").append(string, start, end).append('"').toString(), e);
        }
        return newProjectDependency(projectId, versionId);
    }

    public static ProjectDependency newProjectDependency(String projectId, VersionId versionId)
    {
        return new ProjectDependency()
        {
            @Override
            public String getProjectId()
            {
                return projectId;
            }

            @Override
            public VersionId getVersionId()
            {
                return versionId;
            }
        };
    }
}
