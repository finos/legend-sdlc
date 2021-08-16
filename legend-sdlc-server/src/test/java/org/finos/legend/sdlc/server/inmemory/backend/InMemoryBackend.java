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

package org.finos.legend.sdlc.server.inmemory.backend;

import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;
import org.finos.legend.sdlc.domain.model.TestTools;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.domain.api.entity.EntityApi;
import org.finos.legend.sdlc.server.domain.api.project.ProjectApi;
import org.finos.legend.sdlc.server.domain.api.project.ProjectConfigurationApi;
import org.finos.legend.sdlc.server.domain.api.revision.RevisionApi;
import org.finos.legend.sdlc.server.domain.api.version.VersionApi;
import org.finos.legend.sdlc.server.inmemory.backend.api.InMemoryEntityApi;
import org.finos.legend.sdlc.server.inmemory.backend.api.InMemoryProjectApi;
import org.finos.legend.sdlc.server.inmemory.backend.api.InMemoryProjectConfigurationApi;
import org.finos.legend.sdlc.server.inmemory.backend.api.InMemoryRevisionApi;
import org.finos.legend.sdlc.server.inmemory.backend.api.InMemoryVersionApi;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryProject;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryRevision;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryVersion;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryWorkspace;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class InMemoryBackend
{
    private final EntityApi entityApi = new InMemoryEntityApi(this);
    private final RevisionApi revisionApi = new InMemoryRevisionApi(this);
    private final ProjectConfigurationApi projectConfigurationApi = new InMemoryProjectConfigurationApi(this);
    private final VersionApi versionApi = new InMemoryVersionApi(this);
    private final ProjectApi projectApi = new InMemoryProjectApi(this);

    private final MutableMap<String, InMemoryProject> projects = Maps.mutable.empty();

    @Inject
    public InMemoryBackend()
    {
        RevisionIdGenerator.initialize();
    }

    public void reinitialize()
    {
        this.projects.clear();
    }

    public InMemoryProject getProject(String projectId)
    {
        return this.projects.get(projectId);
    }

    public ProjectBuilder project(String projectId)
    {
        return new ProjectBuilder(this.projects.getIfAbsentPutWithKey(projectId, InMemoryProject::new), this);
    }

    public Iterable<InMemoryProject> getAllProjects()
    {
        return this.projects.valuesView();
    }

    public static class ProjectBuilder
    {
        private final InMemoryProject project;
        private final InMemoryBackend backend;

        public ProjectBuilder(InMemoryProject project, InMemoryBackend backend)
        {
            this.project = project;
            this.backend = backend;
        }

        public void addWorkspace(String workspaceId, ProjectFileAccessProvider.WorkspaceType workspaceType)
        {
            if (workspaceType == ProjectFileAccessProvider.WorkspaceType.GROUP)
            {
                this.project.addNewGroupWorkspace(workspaceId, this.project.getCurrentRevision());
            }
            else
            {
                this.project.addNewUserWorkspace(workspaceId, this.project.getCurrentRevision());
            }
        }

        public void addVersionedEntities(String versionId, List<Entity> entities)
        {
            InMemoryRevision newRevision = new InMemoryRevision(this.project.getProjectId(), this.project.getCurrentRevision());
            newRevision.addEntities(entities);
            this.project.addNewRevision(newRevision);
            this.project.addNewVersion(versionId, newRevision);
        }

        public void addVersionedEntities(String versionId, Entity... entities)
        {
            this.addVersionedEntities(versionId, Arrays.asList(entities));
        }

        public void addVersionedClasses(String versionId, String... classNames)
        {
            List<Entity> entities = Arrays.stream(classNames).map(name -> TestTools.newClassEntity(name, this.project.getProjectId())).collect(Collectors.toList());
            this.addVersionedEntities(versionId, entities);
        }

        private void addEntities(String workspaceId, List<Entity> entities)
        {
            this.addEntities(workspaceId, ProjectFileAccessProvider.WorkspaceType.USER, entities);
        }

        private void addEntities(String workspaceId, ProjectFileAccessProvider.WorkspaceType workspaceType, List<Entity> entities)
        {
            InMemoryWorkspace workspace = workspaceType == ProjectFileAccessProvider.WorkspaceType.GROUP ? this.project.addNewGroupWorkspace(workspaceId, this.project.getCurrentRevision()) : this.project.addNewUserWorkspace(workspaceId, this.project.getCurrentRevision());
            workspace.getCurrentRevision().addEntities(entities);
        }

        public void addEntities(String workspaceId, Entity... entityList)
        {
            this.addEntities(workspaceId, Arrays.asList(entityList));
        }

        public void addEntities(String workspaceId, ProjectFileAccessProvider.WorkspaceType workspaceType, Entity... entityList)
        {
            this.addEntities(workspaceId, workspaceType, Arrays.asList(entityList));
        }

        public void removeEntities(String workspaceId, Entity... entityList)
        {
            this.removeEntities(workspaceId, Arrays.asList(entityList));
        }

        private void removeEntities(String workspaceId, List<Entity> entities)
        {
            this.removeEntities(workspaceId, ProjectFileAccessProvider.WorkspaceType.USER, entities);
        }

        private void removeEntities(String workspaceId, ProjectFileAccessProvider.WorkspaceType workspaceType, List<Entity> entities)
        {
            InMemoryWorkspace workspace = workspaceType == ProjectFileAccessProvider.WorkspaceType.GROUP ? this.project.addNewGroupWorkspace(workspaceId, this.project.getCurrentRevision()) : this.project.addNewUserWorkspace(workspaceId, this.project.getCurrentRevision());
            workspace.getCurrentRevision().removeEntities(entities);
        }

        public void addProjectDependency(String workspaceId, String... projectDependencies)
        {
            this.addProjectDependency(workspaceId, ProjectFileAccessProvider.WorkspaceType.USER, projectDependencies);
        }

        public void addProjectDependency(String workspaceId, ProjectFileAccessProvider.WorkspaceType workspaceType, String... projectDependencies)
        {
            InMemoryWorkspace workspace = workspaceType == ProjectFileAccessProvider.WorkspaceType.GROUP ? this.project.addNewGroupWorkspace(workspaceId, this.project.getCurrentRevision()) : this.project.addNewUserWorkspace(workspaceId, this.project.getCurrentRevision());

            InMemoryRevision newRevision = new InMemoryRevision(workspaceId, workspace.getCurrentRevision());
            for (String dependencyString : projectDependencies)
            {
                ProjectDependency projectDependency = this.validateProjectDependency(dependencyString);
                newRevision.getConfiguration().getProjectDependencies().add(projectDependency);
            }
            workspace.addNewRevision(newRevision);
        }

        public void removeProjectDependency(String workspaceId, String... projectDependencies)
        {
            this.removeProjectDependency(workspaceId, ProjectFileAccessProvider.WorkspaceType.USER, projectDependencies);
        }

        public void removeProjectDependency(String workspaceId, ProjectFileAccessProvider.WorkspaceType workspaceType, String... projectDependencies)
        {
            InMemoryWorkspace workspace = workspaceType == ProjectFileAccessProvider.WorkspaceType.GROUP ? this.project.addNewGroupWorkspace(workspaceId, this.project.getCurrentRevision()) : this.project.addNewUserWorkspace(workspaceId, this.project.getCurrentRevision());

            InMemoryRevision newRevision = new InMemoryRevision(workspaceId, workspace.getCurrentRevision());
            for (String dependencyString : projectDependencies)
            {
                ProjectDependency projectDependency = this.validateProjectDependency(dependencyString);
                newRevision.getConfiguration().removeDependency(projectDependency);
            }
            workspace.addNewRevision(newRevision);
        }

        public void updateProjectDependency(String workspaceId, String oldDependency, String newDependency)
        {
            this.updateProjectDependency(workspaceId, ProjectFileAccessProvider.WorkspaceType.USER, oldDependency, newDependency);
        }

        public void updateProjectDependency(String workspaceId, ProjectFileAccessProvider.WorkspaceType workspaceType, String oldDependency, String newDependency)
        {
            InMemoryWorkspace workspace = workspaceType == ProjectFileAccessProvider.WorkspaceType.GROUP ? this.project.addNewGroupWorkspace(workspaceId, this.project.getCurrentRevision()) : this.project.addNewUserWorkspace(workspaceId, this.project.getCurrentRevision());

            InMemoryRevision newRevision = new InMemoryRevision(workspaceId, workspace.getCurrentRevision());

            newRevision.getConfiguration().removeDependency(this.validateProjectDependency(oldDependency));
            newRevision.getConfiguration().getProjectDependencies().add(this.validateProjectDependency(newDependency));
            workspace.addNewRevision(newRevision);
        }

        public void addClasses(String workspaceId, String... classNames)
        {
            List<Entity> entities = Arrays.stream(classNames).map(name -> TestTools.newClassEntity(name, this.project.getProjectId())).collect(Collectors.toList());
            this.addEntities(workspaceId, entities);
        }

        public void addDependency(String... projectDependencies)
        {
            InMemoryRevision newRevision = new InMemoryRevision(this.project.getProjectId(), this.project.getCurrentRevision());
            this.project.addNewRevision(newRevision);
            for (String dependencyString : projectDependencies)
            {
                ProjectDependency projectDependency = this.validateProjectDependency(dependencyString);
                newRevision.getConfiguration().getProjectDependencies().add(projectDependency);
            }
        }

        private ProjectDependency validateProjectDependency(String dependencyString)
        {
            ProjectDependency parsedProjectDependency = ProjectDependency.parseProjectDependency(dependencyString);
            String projectDependencyId = parsedProjectDependency.getProjectId();
            VersionId projectDependencyVersionId = parsedProjectDependency.getVersionId();

            if (!this.backend.projects.containsKey(projectDependencyId))
            {
                throw new IllegalStateException(String.format("Unknown project dependency %s", parsedProjectDependency));
            }
            InMemoryProject projectDependency = this.backend.projects.get(projectDependencyId);
            InMemoryVersion projectDependencyVersion = projectDependency.getVersion(projectDependencyVersionId.toVersionIdString());
            if (projectDependencyVersion == null)
            {
                throw new IllegalStateException(String.format("Unknown project dependency version %s", dependencyString));
            }
            return parsedProjectDependency;
        }
    }

    public EntityApi getEntityApi()
    {
        return entityApi;
    }

    public RevisionApi getRevisionApi()
    {
        return revisionApi;
    }

    public ProjectConfigurationApi getProjectConfigurationApi()
    {
        return projectConfigurationApi;
    }

    public VersionApi getVersionApi()
    {
        return versionApi;
    }

    public ProjectApi getProjectApi()
    {
        return projectApi;
    }
}
