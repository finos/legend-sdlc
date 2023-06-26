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

package org.finos.legend.sdlc.server.backend.simple.state;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.project.Project;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.server.backend.simple.api.revision.SimpleBackendRevisionApi;
import org.finos.legend.sdlc.server.backend.simple.canned.StaticEntities;
import org.finos.legend.sdlc.server.backend.simple.domain.model.project.SimpleBackendProject;
import org.finos.legend.sdlc.server.backend.simple.domain.model.project.configuration.SimpleBackendProjectConfiguration;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class SimpleBackendState
{
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private MutableMap<String, SimpleBackendProject> projects = Maps.mutable.empty();

    private SimpleBackendRevisionApi revisionApi = new SimpleBackendRevisionApi(this);

    @Inject
    public SimpleBackendState()
    {
        this.initializeWithStaticProjects();
    }

    public SimpleBackendProject getProject(String projectId)
    {
        return this.projects.get(projectId);
    }

    public SimpleBackendRevisionApi revisionApi()
    {
        return this.revisionApi;
    }

    public List<Project> getProjects()
    {
        MutableList<Project> projects = Lists.mutable.empty();
        projects.addAll(this.projects.values());
        return projects.toImmutable().castToList();
    }

    public String nextProjectId()
    {
        return String.valueOf(COUNTER.incrementAndGet());
    }

    public Project createProject(String name, String description, ProjectType type, String groupId, String artifactId, Iterable<String> tags)
    {
        String projectId = this.nextProjectId();
        SimpleBackendProject simpleBackendProject =  new SimpleBackendProject(projectId, name, description, type, null);
        SimpleBackendProjectConfiguration simpleBackendProjectConfiguration = new SimpleBackendProjectConfiguration(simpleBackendProject.getProjectId(), groupId, artifactId);
        simpleBackendProject.setProjectConfiguration(simpleBackendProjectConfiguration);

        this.projects.put(projectId, simpleBackendProject);

        return this.projects.get(projectId);
    }

    public void deleteProject(String id)
    {
        this.projects.remove(id);
    }


    public void initializeWithStaticProjects()
    {
        try
        {
            Project guidedTourProject = this.buildGuidedTourProject();
            this.projects.put(guidedTourProject.getProjectId(), (SimpleBackendProject) guidedTourProject);
        }
        catch (Exception e)
        {
            // TODO : add log
        }
    }

    private SimpleBackendProject buildGuidedTourProject() throws Exception
    {
        SimpleBackendProject project = (SimpleBackendProject) this.createProject("Guided Tour", "A guided tour of Legend", ProjectType.PRODUCTION, "demo", "demo", Collections.emptyList());
        MutableMap<String, Entity> simpleBackendEntitiesMap = StaticEntities.loadEntities("tour.json");
        project.addEntities(simpleBackendEntitiesMap);
        return project;
    }
}
