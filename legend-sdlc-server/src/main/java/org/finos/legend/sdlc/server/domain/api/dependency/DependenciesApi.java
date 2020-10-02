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

import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;

import java.util.Set;

public interface DependenciesApi
{
    // Upstream projects: projects that the project depends on
    Set<ProjectDependency> getWorkspaceRevisionUpstreamProjects(String projectId, String workspaceId, String revisionId, boolean transitive);

    Set<ProjectDependency> getProjectRevisionUpstreamProjects(String projectId, String revisionId, boolean transitive);

    Set<ProjectDependency> getProjectVersionUpstreamProjects(String projectId, String versionId, boolean transitive);

    // Downstream projects: projects that depend on the project
    Set<ProjectRevision> getDownstreamProjects(String projectId);
}
