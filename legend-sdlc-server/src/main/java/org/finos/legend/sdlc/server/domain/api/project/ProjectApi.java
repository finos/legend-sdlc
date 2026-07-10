// Copyright 2026 Goldman Sachs
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

package org.finos.legend.sdlc.server.domain.api.project;

import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.project.workspace.WorkspaceSpecification;
import org.finos.legend.sdlc.server.gitlab.GitLabProjectId;

/**
 * @deprecated Retained temporarily for backward compatibility. Use
 * {@link org.finos.legend.sdlc.backend.api.project.ProjectApi} instead.
 */
@Deprecated
public interface ProjectApi extends org.finos.legend.sdlc.backend.api.project.ProjectApi
{
    /**
     * @deprecated This is a GitLab-specific operation that is not part of the backend-neutral API; it is retained
     * temporarily on this bridge only. GitLab-internal callers use the implementing class directly.
     */
    @Deprecated
    Revision configureProjectInWorkspace(GitLabProjectId projectId, ProjectType type, String groupId, String artifactId, WorkspaceSpecification workspaceSpec);
}
