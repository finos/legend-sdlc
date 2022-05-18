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

import org.finos.legend.sdlc.domain.model.version.DependencyVersionId;
import org.finos.legend.sdlc.domain.model.version.VersionId;

import java.util.Objects;

public abstract class ProjectDependency extends Dependency implements Comparable<ProjectDependency>
{
    public abstract String getProjectId();

    public abstract DependencyVersionId getVersionId();

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
        DependencyVersionId versionId = getVersionId();
        return (versionId == null) ? builder.append("null") : builder.append(versionId.getVersion());
    }

    public static boolean isLegacyProjectDependency(ProjectDependency projectDependency)
    {
        if (projectDependency.getProjectId() == null)
        {
            throw new IllegalArgumentException("Invalid project id string (null) for project dependency");
        }
        return !projectDependency.getProjectId().contains(":");
    }

    public static boolean isSnapshotProjectDependency(ProjectDependency projectDependency)
    {
        if (projectDependency.getVersionId() == null)
        {
            throw new IllegalArgumentException("Invalid version id string (null) for project dependency");
        }
        return projectDependency.getVersionId().getVersion().toLowerCase().contains("-snapshot");
    }

    public static boolean isParsableToVersionId(ProjectDependency projectDependency)
    {
        if (projectDependency.getVersionId() == null)
        {
            throw new IllegalArgumentException("Invalid version id string (null) for project dependency");
        }
        try
        {
            VersionId.parseVersionId(projectDependency.getVersionId().getVersion());
            return true;
        }
        catch (IllegalArgumentException e)
        {
            return false;
        }
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

        int delimiterIndex = string.lastIndexOf(delimiter, end - 1);
        if ((delimiterIndex == -1) || (delimiterIndex < start))
        {
            throw new IllegalArgumentException(new StringBuilder("Invalid project dependency string: \"").append(string, start, end).append('"').toString());
        }

        String projectId = string.substring(start, delimiterIndex);
        DependencyVersionId dependencyVersionId;
        try
        {
            VersionId versionId = VersionId.parseVersionId(string, delimiterIndex + 1, end);
            dependencyVersionId = DependencyVersionId.fromVersionString(versionId.toVersionIdString());
        }
        catch (IllegalArgumentException e)
        {
            throw new IllegalArgumentException(new StringBuilder("Invalid project dependency string: \"").append(string, start, end).append('"').toString(), e);
        }
        return newProjectDependency(projectId, dependencyVersionId);
    }

    public static ProjectDependency newProjectDependency(String projectId, DependencyVersionId versionId)
    {
        return new ProjectDependency()
        {
            @Override
            public String getProjectId()
            {
                return projectId;
            }

            @Override
            public DependencyVersionId getVersionId()
            {
                return versionId;
            }
        };
    }
}
