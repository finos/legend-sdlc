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

package org.finos.legend.sdlc.server.gitlab;

import org.gitlab4j.api.models.Project;

import java.util.Objects;

public final class GitLabProjectId
{
    private static final char DELIMITER = '-';

    private final String prefix;
    private final int gitLabId;

    private GitLabProjectId(String prefix, int gitLabId)
    {
        this.prefix = prefix;
        this.gitLabId = gitLabId;
    }

    public int getGitLabId()
    {
        return this.gitLabId;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }
        if ((other == null) || (other.getClass() != getClass()))
        {
            return false;
        }
        GitLabProjectId that = (GitLabProjectId) other;
        return Objects.equals(this.prefix, that.prefix) && (this.gitLabId == that.gitLabId);
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode(this.prefix) ^ this.gitLabId;
    }

    @Override
    public String toString()
    {
        return getProjectIdString(this.prefix, this.gitLabId);
    }

    public static GitLabProjectId newProjectId(String prefix, int gitLabId)
    {
        return new GitLabProjectId(prefix, gitLabId);
    }

    public static String getProjectIdString(String prefix, Project gitLabProject)
    {
        return getProjectIdString(prefix, gitLabProject.getId());
    }

    public static GitLabProjectId parseProjectId(String projectId)
    {
        if (projectId == null)
        {
            return null;
        }

        int separatorIndex = projectId.indexOf(DELIMITER);
        return newProjectId(separatorIndex == -1 ? null : projectId.substring(0, separatorIndex), parseGitLabId(projectId, separatorIndex));
    }

    private static int parseGitLabId(String projectId, int separatorIndex)
    {
        try
        {
            return Integer.parseInt(projectId.substring(separatorIndex + 1));
        }
        catch (NumberFormatException e)
        {
            throw new IllegalArgumentException("Invalid project id: " + projectId);
        }
    }

    private static String getProjectIdString(String prefix, int gitLabId)
    {
        return (prefix != null ? (prefix + DELIMITER) : "") + gitLabId;
    }
}
