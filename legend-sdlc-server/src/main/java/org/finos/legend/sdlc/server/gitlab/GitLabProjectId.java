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

import org.finos.legend.sdlc.server.gitlab.mode.GitLabMode;
import org.gitlab4j.api.models.Project;

public final class GitLabProjectId
{
    private static final char DELIMITER = '-';

    private final GitLabMode mode;
    private final int gitLabId;

    private GitLabProjectId(GitLabMode mode, int gitLabId)
    {
        this.mode = mode;
        this.gitLabId = gitLabId;
    }

    public GitLabMode getGitLabMode()
    {
        return this.mode;
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
        GitLabProjectId that = (GitLabProjectId)other;
        return (this.mode == that.mode) && (this.gitLabId == that.gitLabId);
    }

    @Override
    public int hashCode()
    {
        return this.mode.hashCode() ^ this.gitLabId;
    }

    @Override
    public String toString()
    {
        return getProjectIdString(this.mode, this.gitLabId);
    }

    public static GitLabProjectId newProjectId(GitLabMode mode, int gitLabId)
    {
        return new GitLabProjectId(mode, gitLabId);
    }

    public static String getProjectIdString(GitLabMode mode, Project gitLabProject)
    {
        return getProjectIdString(mode, gitLabProject.getId());
    }

    public static GitLabProjectId parseProjectId(String projectId)
    {
        if (projectId == null)
        {
            return null;
        }

        int separatorIndex = getSeparatorIndex(projectId);
        return newProjectId(parseGitLabMode(projectId, separatorIndex), parseGitLabId(projectId, separatorIndex));
    }

    public static GitLabMode getGitLabMode(String projectId)
    {
        return parseGitLabMode(projectId, getSeparatorIndex(projectId));
    }

    private static int getSeparatorIndex(String projectId)
    {
        int separatorIndex = projectId.indexOf(DELIMITER);
        if (separatorIndex == -1)
        {
            throw new IllegalArgumentException("Invalid project id: " + projectId);
        }
        return separatorIndex;
    }

    private static GitLabMode parseGitLabMode(String projectId, int separatorIndex)
    {
        try
        {
            return GitLabMode.getMode(projectId, true, 0, separatorIndex);
        }
        catch (IllegalArgumentException e)
        {
            throw new IllegalArgumentException("Invalid project id: " + projectId);
        }
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

    private static String getProjectIdString(GitLabMode mode, int gitLabId)
    {
        return mode.name() + DELIMITER + gitLabId;
    }
}
