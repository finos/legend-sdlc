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

package org.finos.legend.sdlc.server.application.review;

import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;

import java.util.List;

public class CreateReviewCommand
{
    private String workspaceId;
    private WorkspaceType workspaceType;
    private String title;
    private String description;
    private List<String> labels;

    public String getWorkspaceId()
    {
        return this.workspaceId;
    }

    public void setWorkspaceId(String workspaceId)
    {
        this.workspaceId = workspaceId;
    }

    public WorkspaceType getWorkspaceType()
    {
        return this.workspaceType;
    }

    public void setWorkspaceType(WorkspaceType workspaceType)
    {
        this.workspaceType = workspaceType;
    }

    public String getTitle()
    {
        return this.title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getDescription()
    {
        return this.description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public void setLabels(List<String> labels)
    {
        this.labels = labels;
    }

    public List<String> getLabels()
    {
        return this.labels;
    }
}
