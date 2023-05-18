// Copyright 2021 Goldman Sachs
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
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.domain.api.entity.EntityApi;
import org.finos.legend.sdlc.server.domain.api.project.ProjectApi;
import org.finos.legend.sdlc.server.domain.api.project.ProjectConfigurationApi;
import org.finos.legend.sdlc.server.domain.api.revision.RevisionApi;
import org.finos.legend.sdlc.server.domain.api.review.ReviewApi;
import org.finos.legend.sdlc.server.domain.api.version.VersionApi;
import org.finos.legend.sdlc.server.inmemory.backend.api.InMemoryEntityApi;
import org.finos.legend.sdlc.server.inmemory.backend.api.InMemoryProjectApi;
import org.finos.legend.sdlc.server.inmemory.backend.api.InMemoryProjectConfigurationApi;
import org.finos.legend.sdlc.server.inmemory.backend.api.InMemoryRevisionApi;
import org.finos.legend.sdlc.server.inmemory.backend.api.InMemoryVersionApi;
import org.finos.legend.sdlc.server.inmemory.backend.metadata.InMemoryMetadataBackend;
import org.finos.legend.sdlc.server.inmemory.backend.metadata.InMemoryProjectMetadata;
import org.finos.legend.sdlc.server.inmemory.backend.metadata.InMemoryVersionMetadata;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryProject;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryRevision;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryVersion;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryWorkspace;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryReview;
import org.finos.legend.sdlc.server.inmemory.backend.api.InMemoryReviewApi;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class InMemoryBackend
{
    private final EntityApi entityApi = new InMemoryEntityApi(this);
    private final RevisionApi revisionApi = new InMemoryRevisionApi(this);
    private final ProjectConfigurationApi projectConfigurationApi = new InMemoryProjectConfigurationApi(this);
    private final VersionApi versionApi = new InMemoryVersionApi(this);
    private final ProjectApi projectApi = new InMemoryProjectApi(this);
    private final ReviewApi reviewApi = new InMemoryReviewApi(this);

    private InMemoryMetadataBackend metadata = null;

    private final MutableMap<String, InMemoryProject> projects = Maps.mutable.empty();
    private final MutableMap<String, InMemoryReview> reviews = Maps.mutable.empty();

    @Inject
    public InMemoryBackend(InMemoryMetadataBackend metadata)
    {
        RevisionIdGenerator.initialize();
        this.metadata = metadata;
    }

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
        return new ProjectBuilder(this.projects.getIfAbsentPutWithKey(projectId, InMemoryProject::new), this, metadata);
    }

    public Iterable<InMemoryProject> getAllProjects()
    {
        return this.projects.valuesView();
    }

    public static class ProjectBuilder
    {
        private final InMemoryProject project;
        private final InMemoryBackend backend;

        private final InMemoryMetadataBackend metadata;

        public ProjectBuilder(InMemoryProject project, InMemoryBackend backend, InMemoryMetadataBackend metadata)
        {
            this.project = project;
            this.backend = backend;
            this.metadata = metadata;
        }

        public void addWorkspace(String workspaceId, WorkspaceType workspaceType)
        {
           this.addWorkspace(workspaceId, workspaceType, null);
        }

        public void addWorkspace(String workspaceId, WorkspaceType workspaceType, VersionId patchReleaseVersionId)
        {
            if (workspaceType == WorkspaceType.GROUP)
            {
                this.project.addNewGroupWorkspace(workspaceId, this.project.getCurrentRevision(), patchReleaseVersionId);
            }
            else
            {
                this.project.addNewUserWorkspace(workspaceId, this.project.getCurrentRevision(), patchReleaseVersionId);
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
            this.addEntities(workspaceId, WorkspaceType.USER, entities, null);
        }

        public void addEntities(String workspaceId, List<Entity> entities, VersionId patchReleaseVersionId)
        {
            this.addEntities(workspaceId, WorkspaceType.USER, entities, patchReleaseVersionId);
        }

        private void addEntities(String workspaceId, WorkspaceType workspaceType, List<Entity> entities)
        {
            this.addEntities(workspaceId, workspaceType, entities, null);
        }

        public void addEntities(String workspaceId, WorkspaceType workspaceType, List<Entity> entities, VersionId patchReleaseVersionId)
        {
            if (patchReleaseVersionId != null)
            {
                this.project.addPatch(patchReleaseVersionId, this.project.getCurrentRevision());
            }
            InMemoryWorkspace workspace = workspaceType == WorkspaceType.GROUP ? this.project.addNewGroupWorkspace(workspaceId, this.project.getCurrentRevision(), patchReleaseVersionId) : this.project.addNewUserWorkspace(workspaceId, this.project.getCurrentRevision(), patchReleaseVersionId);
            workspace.getCurrentRevision().addEntities(entities);
        }

        public void addEntities(String workspaceId, Entity... entityList)
        {
            this.addEntities(workspaceId, Arrays.asList(entityList));
        }

        public void addEntities(String workspaceId, WorkspaceType workspaceType, Entity... entityList)
        {
            this.addEntities(workspaceId, workspaceType, Arrays.asList(entityList));
        }

        public void removeEntities(String workspaceId, Entity... entityList)
        {
            this.removeEntities(workspaceId, Arrays.asList(entityList));
        }

        private void removeEntities(String workspaceId, List<Entity> entities)
        {
            this.removeEntities(workspaceId, WorkspaceType.USER, entities);
        }

        private void removeEntities(String workspaceId, WorkspaceType workspaceType, List<Entity> entities)
        {
            InMemoryWorkspace workspace = workspaceType == WorkspaceType.GROUP ? this.project.addNewGroupWorkspace(workspaceId, this.project.getCurrentRevision()) : this.project.addNewUserWorkspace(workspaceId, this.project.getCurrentRevision());
            workspace.getCurrentRevision().removeEntities(entities);
        }

        public void addProjectDependency(String workspaceId, String... projectDependencies)
        {
            this.addProjectDependency(workspaceId, WorkspaceType.USER, projectDependencies);
        }

        public void addProjectDependency(String workspaceId, WorkspaceType workspaceType, String... projectDependencies)
        {
            InMemoryWorkspace workspace = workspaceType == WorkspaceType.GROUP ? this.project.addNewGroupWorkspace(workspaceId, this.project.getCurrentRevision()) : this.project.addNewUserWorkspace(workspaceId, this.project.getCurrentRevision());

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
            this.removeProjectDependency(workspaceId, WorkspaceType.USER, projectDependencies);
        }

        public void removeProjectDependency(String workspaceId, WorkspaceType workspaceType, String... projectDependencies)
        {
            InMemoryWorkspace workspace = workspaceType == WorkspaceType.GROUP ? this.project.addNewGroupWorkspace(workspaceId, this.project.getCurrentRevision()) : this.project.addNewUserWorkspace(workspaceId, this.project.getCurrentRevision());

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
            this.updateProjectDependency(workspaceId, WorkspaceType.USER, oldDependency, newDependency);
        }

        public void updateProjectDependency(String workspaceId, WorkspaceType workspaceType, String oldDependency, String newDependency)
        {
            InMemoryWorkspace workspace = workspaceType == WorkspaceType.GROUP ? this.project.addNewGroupWorkspace(workspaceId, this.project.getCurrentRevision()) : this.project.addNewUserWorkspace(workspaceId, this.project.getCurrentRevision());

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

        public void addPatch(VersionId patchReleaseVersionId)
        {
            this.project.addPatch(patchReleaseVersionId, this.project.getCurrentRevision());
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

        public void setMavenCoordinates(String groupId, String artifactId)
        {
            InMemoryRevision newRevision = new InMemoryRevision(this.project.getProjectId(), this.project.getCurrentRevision());
            this.project.addNewRevision(newRevision);
            newRevision.getConfiguration().setMavenCoordinates(groupId, artifactId);
        }

        private ProjectDependency validateProjectDependency(String dependencyString)
        {
            ProjectDependency parsedProjectDependency = ProjectDependency.parseProjectDependency(dependencyString);
            String projectDependencyId = parsedProjectDependency.getProjectId();
            String projectDependencyVersionId = parsedProjectDependency.getVersionId();

            if (!this.backend.projects.containsKey(projectDependencyId))
            {
                if (this.metadata != null && this.metadata.getProjects().containsKey(projectDependencyId))
                {
                    InMemoryProjectMetadata projectDependency = this.metadata.getProjects().get(projectDependencyId);
                    InMemoryVersionMetadata projectDependencyVersion = projectDependency.getVersion(projectDependencyVersionId);
                    if (projectDependencyVersion == null)
                    {
                        throw new IllegalStateException(String.format("Unknown project dependency %s", parsedProjectDependency));
                    }
                    return new ProjectDependency()
                    {
                        @Override
                        public String getProjectId()
                        {
                            return projectDependency.getProjectId().toString();
                        }

                        @Override
                        public String getVersionId()
                        {
                            return projectDependency.getCurrentVersionId();
                        }
                    };
                }
                else
                {
                    throw new IllegalStateException(String.format("Unknown project dependency %s", parsedProjectDependency));
                }
            }
            InMemoryProject projectDependency = this.backend.projects.get(projectDependencyId);
            InMemoryVersion projectDependencyVersion = projectDependency.getVersion(projectDependencyVersionId);
            if (projectDependencyVersion == null)
            {
                throw new IllegalStateException(String.format("Unknown project dependency version %s", dependencyString));
            }
            return parsedProjectDependency;
        }

        public InMemoryReview getReview(String reviewId)
        {
            return this.project.getReview(reviewId);
        }
    
        public InMemoryReview addReview(String reviewId)
        {
            return this.addReview(reviewId, null);
        }

        public InMemoryReview addReview(String reviewId, VersionId patchReleaseVersionId)
        {
            if (patchReleaseVersionId != null)
            {
                this.project.addPatch(patchReleaseVersionId, this.project.getCurrentRevision());
            }
            return this.project.addReview(reviewId, patchReleaseVersionId);
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

    public ReviewApi getReviewApi()
    {
        return reviewApi;
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
