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

package org.finos.legend.sdlc.server.api.issue;

import org.finos.legend.sdlc.domain.model.issue.Issue;
import org.finos.legend.sdlc.server.domain.api.issue.IssueApi;
import org.finos.legend.sdlc.server.exception.FSException;

import javax.inject.Inject;
import java.util.List;

public class FileSystemIssueApi implements IssueApi
{
    @Inject
    public FileSystemIssueApi()
    {
    }

    @Override
    public Issue getIssue(String projectId, String issueId)
    {
        throw FSException.unavailableFeature();
    }

    @Override
    public List<Issue> getIssues(String projectId)
    {
        throw FSException.unavailableFeature();
    }

    @Override
    public Issue createIssue(String projectId, String title, String description)
    {
        throw FSException.unavailableFeature();
    }

    @Override
    public void deleteIssue(String projectId, String issueId)
    {
        throw FSException.unavailableFeature();
    }
}
