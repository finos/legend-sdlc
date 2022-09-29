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

import java.util.Comparator;
import java.util.Objects;

public abstract class ProjectDependency extends Dependency
{
    public abstract String getProjectId();

    public abstract String getVersionId();

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

        ProjectDependency that = (ProjectDependency) other;
        return Objects.equals(this.getProjectId(), that.getProjectId()) && Objects.equals(this.getVersionId(), that.getVersionId());
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode(getProjectId()) + 53 * Objects.hashCode(getVersionId());
    }

    @Override
    public StringBuilder appendDependencyIdString(StringBuilder builder)
    {
        return builder.append(getProjectId());
    }

    @Override
    public StringBuilder appendVersionIdString(StringBuilder builder)
    {
        return builder.append(getVersionId());
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
            throw new IllegalArgumentException("Invalid project dependency string: \"" + string.substring(start, end) + '"');
        }

        String projectId = string.substring(start, delimiterIndex);
        String versionId = string.substring(delimiterIndex + 1);
        return newProjectDependency(projectId, versionId);
    }

    public static ProjectDependency newProjectDependency(String projectId, String versionId)
    {
        return new ProjectDependency()
        {
            @Override
            public String getProjectId()
            {
                return projectId;
            }

            @Override
            public String getVersionId()
            {
                return versionId;
            }
        };
    }

    public static Comparator<ProjectDependency> getDefaultComparator()
    {
        Comparator<String> nullsLastStringCmp = Comparator.nullsLast(Comparator.naturalOrder());
        return Comparator.comparing(ProjectDependency::getProjectId, nullsLastStringCmp)
                .thenComparing(ProjectDependency::getVersionId, nullsLastStringCmp);
    }
}
