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

package org.finos.legend.sdlc.server.backend.simple.api.project;

import org.finos.legend.sdlc.domain.model.project.Project;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.project.accessRole.AccessRole;
import org.finos.legend.sdlc.domain.model.project.accessRole.AuthorizableProjectAction;
import org.finos.legend.sdlc.server.backend.simple.exception.UnavailableFeature;
import org.finos.legend.sdlc.server.backend.simple.state.SimpleBackendState;
import org.finos.legend.sdlc.server.domain.api.project.ProjectApi;

import javax.inject.Inject;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class SimpleBackendProjectApi implements ProjectApi
{
    private SimpleBackendState backendState;

    @Inject
    public SimpleBackendProjectApi(SimpleBackendState backendState)
    {
        this.backendState = backendState;
    }

    @Override
    public Project getProject(String id)
    {
        return this.backendState.getProject(id);
    }

    @Override
    public List<Project> getProjects(boolean user, String search, Iterable<String> tags, Integer limit)
    {
        return this.backendState.getProjects();
    }

    @Override
    public Project createProject(String name, String description, ProjectType type, String groupId, String artifactId, Iterable<String> tags)
    {
        return this.backendState.createProject(name, description, type, groupId, artifactId, tags);
    }

    @Override
    public void deleteProject(String id)
    {
        this.backendState.deleteProject(id);
    }

    @Override
    public void changeProjectName(String id, String newName)
    {
        this.backendState.getProject(id).setName(newName);
    }

    @Override
    public void changeProjectDescription(String id, String newDescription)
    {
        this.backendState.getProject(id).setDescription(newDescription);
    }

    @Override
    public void updateProjectTags(String id, Iterable<String> tagsToRemove, Iterable<String> tagsToAdd)
    {
    }

    @Override
    public void setProjectTags(String id, Iterable<String> tags)
    {

    }

    @Override
    public AccessRole getCurrentUserAccessRole(String id)
    {
        return new AccessRole()
        {
            @Override
            public String getAccessRole()
            {
                return "ALL";
            }
        };
    }

    @Override
    public Set<AuthorizableProjectAction> checkUserAuthorizedActions(String id, Set<AuthorizableProjectAction> actions)
    {
        return EnumSet.of(AuthorizableProjectAction.CREATE_WORKSPACE);
    }

    @Override
    public boolean checkUserAuthorizedAction(String id, AuthorizableProjectAction action)
    {
        return action == AuthorizableProjectAction.CREATE_WORKSPACE;
    }

    @Override
    public ImportReport importProject(String id, ProjectType type, String groupId, String artifactId)
    {
        throw UnavailableFeature.exception();
    }
}
