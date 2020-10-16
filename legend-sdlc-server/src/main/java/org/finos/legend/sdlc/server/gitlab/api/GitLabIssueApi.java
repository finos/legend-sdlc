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

package org.finos.legend.sdlc.server.gitlab.api;

import org.finos.legend.sdlc.domain.model.issue.Issue;
import org.finos.legend.sdlc.server.domain.api.issue.IssueApi;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.GitLabProjectId;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.gitlab.tools.PagerTools;
import org.gitlab4j.api.Pager;

import javax.inject.Inject;
import java.time.Instant;
import java.util.List;

public class GitLabIssueApi extends BaseGitLabApi implements IssueApi
{
    @Inject
    public GitLabIssueApi(GitLabUserContext userContext)
    {
        super(userContext);
    }

    @Override
    public Issue getIssue(String projectId, String issueId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(issueId, "issueId may not be null");
        try
        {
            GitLabProjectId gitLabProjectId = parseProjectId(projectId);
            org.gitlab4j.api.models.Issue issue = withRetries(() -> getGitLabApi(gitLabProjectId.getGitLabMode()).getIssuesApi().getIssue(gitLabProjectId.getGitLabId(), parseIntegerIdIfNotNull(issueId)));
            return fromGitLabIssue(issue);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to get issue " + issueId + " for project " + projectId,
                    () -> "Unknown issue (" + issueId + ") or project (" + projectId + ")",
                    () -> "Failed to get issue " + issueId + " for project " + projectId);
        }
    }

    @Override
    public List<Issue> getIssues(String projectId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        try
        {
            GitLabProjectId gitLabProjectId = parseProjectId(projectId);
            Pager<org.gitlab4j.api.models.Issue> pager = withRetries(() -> getGitLabApi(gitLabProjectId.getGitLabMode()).getIssuesApi().getIssues((Integer)gitLabProjectId.getGitLabId(), ITEMS_PER_PAGE));
            return PagerTools.stream(pager).map(GitLabIssueApi::fromGitLabIssue).collect(PagerTools.listCollector(pager));
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to get issues for project " + projectId,
                    () -> "Unknown project: " + projectId,
                    () -> "Failed to get issues for project " + projectId);
        }
    }

    @Override
    public Issue createIssue(String projectId, String title, String description)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(title, "title may not be null");
        LegendSDLCServerException.validateNonNull(description, "description may not be null");
        try
        {
            GitLabProjectId gitLabProjectId = parseProjectId(projectId);
            org.gitlab4j.api.models.Issue issue = withRetries(() -> getGitLabApi(gitLabProjectId.getGitLabMode()).getIssuesApi().createIssue(gitLabProjectId.getGitLabId(), title, description));
            return fromGitLabIssue(issue);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to create issues for project " + projectId,
                    () -> "Unknown project: " + projectId,
                    () -> "Failed to create issue for project " + projectId);
        }
    }

    @Override
    public void deleteIssue(String projectId, String issueId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(issueId, "issueId may not be null");
        try
        {
            GitLabProjectId gitLabProjectId = parseProjectId(projectId);
            withRetries(() -> getGitLabApi(gitLabProjectId.getGitLabMode()).getIssuesApi().deleteIssue(gitLabProjectId.getGitLabId(), parseIntegerIdIfNotNull(issueId)));
        }
        catch (LegendSDLCServerException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to delete issue " + issueId + " for project " + projectId,
                    () -> "Unknown issue (" + issueId + ") or project (" + projectId + ")",
                    () -> "Failed to delete issue " + issueId + " for project " + projectId);
        }
    }

    private static Issue fromGitLabIssue(org.gitlab4j.api.models.Issue issue)
    {
        return applyIfNotNull(IssueWrapper::new, issue);
    }

    private static class IssueWrapper implements Issue
    {
        private final org.gitlab4j.api.models.Issue gitLabIssue;

        private IssueWrapper(org.gitlab4j.api.models.Issue gitLabIssue)
        {
            this.gitLabIssue = gitLabIssue;
        }

        @Override
        public String getId()
        {
            return toStringIfNotNull(this.gitLabIssue.getId());
        }

        @Override
        public String getProjectId()
        {
            return toStringIfNotNull(this.gitLabIssue.getProjectId());
        }

        @Override
        public String getTitle()
        {
            return this.gitLabIssue.getTitle();
        }

        @Override
        public String getDescription()
        {
            return this.gitLabIssue.getDescription();
        }

        @Override
        public Instant getCreationTime()
        {
            return toInstantIfNotNull(this.gitLabIssue.getCreatedAt());
        }

        @Override
        public Instant getLastUpdateTime()
        {
            return toInstantIfNotNull(this.gitLabIssue.getUpdatedAt());
        }

        @Override
        public String getWebURL()
        {
            return this.gitLabIssue.getWebUrl();
        }
    }
}
