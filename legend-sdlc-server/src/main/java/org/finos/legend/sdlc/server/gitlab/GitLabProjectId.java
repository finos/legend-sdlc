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
    private final long gitLabId;

    private GitLabProjectId(String prefix, long gitLabId)
    {
        this.prefix = prefix;
        this.gitLabId = gitLabId;
    }

    public String getPrefix()
    {
        return this.prefix;
    }

    public long getGitLabId()
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
        return Objects.hashCode(this.prefix) ^ Long.hashCode(this.gitLabId);
    }

    @Override
    public String toString()
    {
        return getProjectIdString(this.prefix, this.gitLabId);
    }

    public static GitLabProjectId newProjectId(String prefix, long gitLabId)
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
            throw new IllegalArgumentException("Invalid project id: null");
        }
        if (projectId.isEmpty())
        {
            throw new IllegalArgumentException("Invalid project id: \"\"");
        }

        int separatorIndex = projectId.indexOf(DELIMITER);
        String prefix = (separatorIndex == -1) ? null : projectId.substring(0, separatorIndex);
        long gitLabId = parseGitLabId(projectId, separatorIndex + 1);
        return newProjectId(prefix, gitLabId);
    }

    private static long parseGitLabId(String projectId, int start)
    {
        try
        {
            return Long.parseLong(projectId.substring(start));
        }
        catch (NumberFormatException e)
        {
            throw new IllegalArgumentException("Invalid project id: \"" + projectId + "\"");
        }
    }

    private static String getProjectIdString(String prefix, long gitLabId)
    {
        return (prefix == null) ? Long.toString(gitLabId) : (prefix + DELIMITER + gitLabId);
    }
}
