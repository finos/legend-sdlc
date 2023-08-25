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

package org.finos.legend.sdlc.server.inmemory.backend.api;

import org.eclipse.collections.api.factory.Lists;
import org.finos.legend.sdlc.domain.model.project.Project;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.project.accessRole.AccessRole;
import org.finos.legend.sdlc.domain.model.project.accessRole.AuthorizableProjectAction;
import org.finos.legend.sdlc.server.domain.api.project.ProjectApi;
import org.finos.legend.sdlc.server.inmemory.backend.InMemoryBackend;

import javax.inject.Inject;
import java.util.List;
import java.util.Set;

public class InMemoryProjectApi implements ProjectApi
{
    private final InMemoryBackend backend;

    @Inject
    public InMemoryProjectApi(InMemoryBackend backend)
    {
        this.backend = backend;
    }

    @Override
    public Project getProject(String id)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public List<Project> getProjects(boolean user, String search, Iterable<String> tags, Integer limit)
    {
        List<Project> projects = Lists.mutable.withAll(this.backend.getAllProjects());
        if (limit != null && projects.size() > limit)
        {
            projects = projects.subList(0, limit);
        }
        return projects;
    }

    @Override
    public Project createProject(String name, String description, ProjectType type, String groupId, String artifactId, Iterable<String> tags)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void deleteProject(String id)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void changeProjectName(String id, String newName)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void changeProjectDescription(String id, String newDescription)
    {

    }

    @Override
    public void updateProjectTags(String id, Iterable<String> tagsToRemove, Iterable<String> tagsToAdd)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void setProjectTags(String id, Iterable<String> tags)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public AccessRole getCurrentUserAccessRole(String id)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Set<AuthorizableProjectAction> checkUserAuthorizedActions(String id, Set<AuthorizableProjectAction> actions)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public boolean checkUserAuthorizedAction(String id, AuthorizableProjectAction action)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public ImportReport importProject(String id, ProjectType type, String groupId, String artifactId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }
}
