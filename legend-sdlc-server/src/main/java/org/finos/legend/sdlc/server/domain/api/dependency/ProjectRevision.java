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

package org.finos.legend.sdlc.server.domain.api.dependency;

import java.util.Objects;

public class ProjectRevision
{
    private final String projectId;
    private final String revisionId;

    public ProjectRevision(String projectId, String revisionId)
    {
        this.projectId = projectId;
        this.revisionId = revisionId;
    }

    public String getProjectId()
    {
        return this.projectId;
    }

    public String getRevisionId()
    {
        return this.revisionId;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }

        if (!(other instanceof ProjectRevision))
        {
            return false;
        }

        ProjectRevision that = (ProjectRevision) other;
        return Objects.equals(this.getProjectId(), that.getProjectId()) && Objects.equals(this.getRevisionId(), that.getRevisionId());
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode(getProjectId()) + (53 * Objects.hashCode(getRevisionId()));
    }

    public String toProjectRevisionString()
    {
        return this.projectId + ":" + this.revisionId;
    }
}