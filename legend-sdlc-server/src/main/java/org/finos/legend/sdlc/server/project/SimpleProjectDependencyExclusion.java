// Copyright 2025 Goldman Sachs
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

package org.finos.legend.sdlc.server.project;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependencyExclusion;

public class SimpleProjectDependencyExclusion extends ProjectDependencyExclusion
{
    private final String projectId;

    @JsonCreator
    public SimpleProjectDependencyExclusion(@JsonProperty("projectId") String projectId)
    {
        this.projectId = projectId;
    }

    @Override
    public String getProjectId()
    {
        return this.projectId;
    }
}
