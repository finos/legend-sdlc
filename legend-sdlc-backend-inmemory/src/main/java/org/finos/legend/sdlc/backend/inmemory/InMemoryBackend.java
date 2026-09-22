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

package org.finos.legend.sdlc.backend.inmemory;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.finos.legend.sdlc.backend.api.project.ProjectApi;
import org.finos.legend.sdlc.backend.api.spi.AbstractBackend;
import org.finos.legend.sdlc.backend.api.spi.BackendCapability;
import org.finos.legend.sdlc.backend.api.spi.BackendEnvironment;
import org.finos.legend.sdlc.backend.api.spi.BackendSession;
import org.finos.legend.sdlc.backend.api.spi.BackendSessionContext;
import org.finos.legend.sdlc.backend.api.user.UserApi;
import org.finos.legend.sdlc.backend.api.workspace.WorkspaceApi;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.error.LegendSDLCException;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;

/**
 * The in-memory backend: the &sect;3.2 minimal backend made literal. It supplies the in-memory storage provider
 * and native project/workspace/user lifecycle over an in-memory registry, declares only
 * {@link BackendCapability#USER_WORKSPACES}, and inherits every other behavior from the L4 defaults over the
 * storage SPI. It is the reference implementation the backend TCK runs against in this repository, and a
 * self-contained backend for tests and demos; all state is process-local and lost on close.
 */
public class InMemoryBackend extends AbstractBackend
{
    public static final String TYPE = "inMemory";

    private final InMemoryProjectFileAccessProvider fileAccessProvider = new InMemoryProjectFileAccessProvider("in-memory", "in-memory");
    private final MutableMap<String, ProjectState> projects = Maps.mutable.empty();

    public InMemoryBackend(BackendEnvironment environment)
    {
        super(TYPE, EnumSet.of(BackendCapability.USER_WORKSPACES), environment);
    }

    @Override
    public BackendSession newSession(BackendSessionContext context)
    {
        return new Session(context);
    }

    public class Session extends AbstractBackend.Session
    {
        protected Session(BackendSessionContext context)
        {
            super(context);
        }

        @Override
        protected ProjectFileAccessProvider getProjectFileAccessProvider()
        {
            return InMemoryBackend.this.fileAccessProvider;
        }

        @Override
        public ProjectApi getProjectApi()
        {
            return new InMemoryProjectApi(InMemoryBackend.this);
        }

        @Override
        public WorkspaceApi getWorkspaceApi()
        {
            return new InMemoryWorkspaceApi(InMemoryBackend.this, getUserId());
        }

        @Override
        public UserApi getUserApi()
        {
            return new InMemoryUserApi(getUserId());
        }
    }

    InMemoryProjectFileAccessProvider getFileAccessProvider()
    {
        return this.fileAccessProvider;
    }

    BackendEnvironment environment()
    {
        return getEnvironment();
    }

    synchronized ProjectState createProject(String id, String name, String description, ProjectType type, Iterable<String> tags)
    {
        if (this.projects.containsKey(id))
        {
            throw new LegendSDLCException("Project " + id + " already exists", 409);
        }
        ProjectState project = new ProjectState(id, name, description, type);
        if (tags != null)
        {
            tags.forEach(project::addTag);
        }
        this.projects.put(id, project);
        return project;
    }

    synchronized ProjectState findProject(String id)
    {
        return this.projects.get(id);
    }

    synchronized ProjectState getProject(String id)
    {
        ProjectState project = this.projects.get(id);
        if (project == null)
        {
            throw new LegendSDLCException("Unknown project: " + id, 404);
        }
        return project;
    }

    synchronized List<ProjectState> getProjects(Predicate<? super ProjectState> predicate)
    {
        return (predicate == null) ? Lists.mutable.withAll(this.projects.valuesView()) : this.projects.valuesView().select(predicate::test, Lists.mutable.empty());
    }

    synchronized void deleteProject(String id)
    {
        if (this.projects.removeKey(id) == null)
        {
            throw new LegendSDLCException("Unknown project: " + id, 404);
        }
        this.fileAccessProvider.deleteProject(id);
    }

    /**
     * Registry entry for a project: the domain metadata the storage provider does not hold. All access goes
     * through the backend's synchronized methods (or synchronizes on the backend for compound operations).
     */
    static class ProjectState
    {
        private final String id;
        private final ProjectType type;
        private final MutableList<String> tags = Lists.mutable.empty();
        private final MutableMap<String, WorkspaceState> workspaces = Maps.mutable.empty();
        private String name;
        private String description;

        private ProjectState(String id, String name, String description, ProjectType type)
        {
            this.id = id;
            this.name = name;
            this.description = description;
            this.type = type;
        }

        String getId()
        {
            return this.id;
        }

        ProjectType getType()
        {
            return this.type;
        }

        synchronized String getName()
        {
            return this.name;
        }

        synchronized void setName(String name)
        {
            this.name = name;
        }

        synchronized String getDescription()
        {
            return this.description;
        }

        synchronized void setDescription(String description)
        {
            this.description = description;
        }

        synchronized List<String> getTags()
        {
            return Lists.mutable.withAll(this.tags);
        }

        synchronized void addTag(String tag)
        {
            if (!this.tags.contains(tag))
            {
                this.tags.add(tag);
            }
        }

        synchronized void removeTag(String tag)
        {
            this.tags.remove(tag);
        }

        synchronized void setTags(Iterable<String> newTags)
        {
            this.tags.clear();
            newTags.forEach(this::addTag);
        }

        synchronized WorkspaceState findWorkspace(String workspaceId)
        {
            return this.workspaces.get(workspaceId);
        }

        synchronized List<WorkspaceState> getWorkspaces(Predicate<? super WorkspaceState> predicate)
        {
            return (predicate == null) ? Lists.mutable.withAll(this.workspaces.valuesView()) : this.workspaces.valuesView().select(predicate::test, Lists.mutable.empty());
        }

        synchronized void addWorkspace(WorkspaceState workspace)
        {
            if (this.workspaces.containsKey(workspace.getWorkspaceId()))
            {
                throw new LegendSDLCException("Workspace " + workspace.getWorkspaceId() + " already exists in project " + this.id, 409);
            }
            this.workspaces.put(workspace.getWorkspaceId(), workspace);
        }

        synchronized void removeWorkspace(String workspaceId)
        {
            this.workspaces.remove(workspaceId);
        }
    }

    /**
     * Registry entry for a (user) workspace: its owner. The storage provider knows the workspace only as a
     * branch; ownership is backend metadata.
     */
    static class WorkspaceState
    {
        private final String workspaceId;
        private final String userId;

        WorkspaceState(String workspaceId, String userId)
        {
            this.workspaceId = workspaceId;
            this.userId = userId;
        }

        String getWorkspaceId()
        {
            return this.workspaceId;
        }

        String getUserId()
        {
            return this.userId;
        }
    }
}
