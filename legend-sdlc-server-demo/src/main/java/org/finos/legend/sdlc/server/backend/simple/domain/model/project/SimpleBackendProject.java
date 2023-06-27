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

package org.finos.legend.sdlc.server.backend.simple.domain.model.project;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.ImmutableMap;
import org.eclipse.collections.api.map.MutableMap;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.project.Project;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.server.backend.simple.api.entity.SimpleBackendEntityAccessContext;
import org.finos.legend.sdlc.server.backend.simple.api.revision.SimpleBackendRevisionAccessContext;
import org.finos.legend.sdlc.server.backend.simple.domain.model.project.configuration.SimpleBackendProjectConfiguration;
import org.finos.legend.sdlc.server.backend.simple.domain.model.project.workspace.SimpleBackendProjectWorkspace;
import org.finos.legend.sdlc.server.backend.simple.domain.model.revision.SimpleBackendRevision;
import org.finos.legend.sdlc.server.backend.simple.domain.model.version.SimpleBackendVersion;
import org.finos.legend.sdlc.server.domain.api.revision.RevisionAccessContext;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SimpleBackendProject implements Project
{
    private SimpleBackendVersion version;
    private SimpleBackendRevision revision;
    private String projectId;
    private String name;
    private String description;
    private List<String> tags = Collections.emptyList();
    private ProjectType projectType;
    private String webUrl;

    @JsonIgnore
    private SimpleBackendProjectConfiguration projectConfiguration;

    @JsonIgnore
    private MutableMap<String, SimpleBackendProjectWorkspace> userWorkspaces = Maps.mutable.empty();

    @JsonIgnore
    private MutableMap<String, SimpleBackendProjectWorkspace> groupWorkspaces = Maps.mutable.empty();

    @JsonIgnore
    private MutableMap<String, Entity> entities = Maps.mutable.empty();

    @JsonIgnore
    private SimpleBackendRevisionAccessContext revisionAccessContext;

    @JsonIgnore
    private SimpleBackendEntityAccessContext entityAccessContext;

    @Inject
    public SimpleBackendProject()
    {
    }

    public SimpleBackendProject(String projectId, String name, String description, ProjectType projectType, String webUrl)
    {
        this.projectId = projectId;
        this.name = name;
        this.description = description;
        this.projectType = projectType;
        this.webUrl = webUrl;

        this.revision  = new SimpleBackendRevision(this.entities);
        this.version = new SimpleBackendVersion(projectId, revision);
        this.revisionAccessContext = new SimpleBackendRevisionAccessContext(revision);
        this.entityAccessContext = new SimpleBackendEntityAccessContext(revision);
    }

    public SimpleBackendProjectConfiguration getProjectConfiguration()
    {
        return projectConfiguration;
    }

    public void setProjectConfiguration(SimpleBackendProjectConfiguration projectConfiguration)
    {
        this.projectConfiguration = projectConfiguration;
    }

    @Override
    public String getProjectId()
    {
        return projectId;
    }

    @Override
    public String getName()
    {
        return name;
    }

    @Override
    public String getDescription()
    {
        return description;
    }

    @Override
    public List<String> getTags()
    {
        return tags;
    }

    @Override
    public ProjectType getProjectType()
    {
        return projectType;
    }

    @Override
    public String getWebUrl()
    {
        return webUrl;
    }

    public void setProjectId(String projectId)
    {
        this.projectId = projectId;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public void setTags(List<String> tags)
    {
        this.tags = tags;
    }

    public void setProjectType(ProjectType projectType)
    {
        this.projectType = projectType;
    }

    public void setWebUrl(String webUrl)
    {
        this.webUrl = webUrl;
    }

    public SimpleBackendEntityAccessContext getEntityAccessContext()
    {
        return entityAccessContext;
    }

    public void setEntityAccessContext(SimpleBackendEntityAccessContext entityAccessContext)
    {
        this.entityAccessContext = entityAccessContext;
    }

    public SimpleBackendProjectWorkspace getWorkspace(String id, WorkspaceType workspaceType)
    {
        if (WorkspaceType.USER == workspaceType)
        {
            return this.userWorkspaces.get(id);
        }
        return this.groupWorkspaces.get(id);
    }

    public List<Workspace> getWorkspaces(Set<WorkspaceType> workspaceTypes)
    {
        MutableList<Workspace> workspaces = Lists.mutable.empty();
        for (WorkspaceType workspaceType : workspaceTypes)
        {
            if (workspaceType == WorkspaceType.GROUP)
            {
                workspaces.addAll(this.groupWorkspaces.values());
            }
            if (workspaceType == WorkspaceType.USER)
            {
                workspaces.addAll(this.userWorkspaces.values());
            }
        }
        return workspaces;
    }

    public Workspace newWorkspace(String workspaceId, WorkspaceType workspaceType)
    {
        ImmutableMap<String, Entity> entities = this.entities.toImmutable();
        if (workspaceType == WorkspaceType.GROUP)
        {
            SimpleBackendProjectWorkspace workspace = new SimpleBackendProjectWorkspace(projectId, workspaceId, entities);
            this.groupWorkspaces.put(workspaceId, workspace);
        }
        else
        {
            SimpleBackendProjectWorkspace workspace = new SimpleBackendProjectWorkspace(projectId, workspaceId, "alice", entities);
            this.userWorkspaces.put(workspaceId, workspace);
        }

        return this.getWorkspace(workspaceId, workspaceType);
    }

    public void deleteWorkspace(String workspaceId, WorkspaceType workspaceType)
    {
        if (WorkspaceType.USER == workspaceType)
        {
            this.userWorkspaces.remove(workspaceId);
        }
        else if (WorkspaceType.GROUP == workspaceType)
        {
            this.groupWorkspaces.remove(workspaceId);
        }
    }

    public RevisionAccessContext getRevisionAccessContext()
    {
        return this.revisionAccessContext;
    }

    public SimpleBackendVersion getVersion()
    {
        return this.version;
    }

    public void addEntities(MutableMap<String, Entity> entities)
    {
        this.entities.putAll(entities);
    }
}
