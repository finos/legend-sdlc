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

package org.finos.legend.sdlc.server.inmemory.backend.metadata;

import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;
import org.finos.legend.sdlc.domain.model.TestTools;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.depot.api.MetadataApi;
import org.finos.legend.sdlc.server.depot.model.DepotProjectVersion;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class InMemoryMetadataBackend
{
    private final InMemoryMetadataApi metadataApi = new InMemoryMetadataApi(this);

    private final MutableMap<String, InMemoryProjectMetadata> projects = Maps.mutable.empty();

    @Inject
    public InMemoryMetadataBackend()
    {
        //bootstrap
    }

    public InMemoryProjectMetadata getProject(String projectId)
    {
        return this.projects.get(projectId);
    }

    public MetadataBuilder project(String projectId)
    {
        return new MetadataBuilder(this.projects.getIfAbsentPutWithKey(projectId, InMemoryProjectMetadata::new), this);
    }

    public MutableMap<String, InMemoryProjectMetadata> getProjects()
    {
        return this.projects;
    }

    public static class MetadataBuilder
    {
        private final InMemoryProjectMetadata project;
        private final InMemoryMetadataBackend backend;

        public MetadataBuilder(InMemoryProjectMetadata project, InMemoryMetadataBackend backend)
        {
            this.project = project;
            this.backend = backend;
        }

        public void addVersionedEntities(String versionId, List<Entity> entities)
        {
            String currentVersion = this.project.getCurrentVersionId();
            InMemoryVersionMetadata newVersion = this.project.getOrCreateVersion(versionId);

            if (currentVersion != null && !currentVersion.equals(versionId))
            {
                newVersion.addEntities(this.project.getVersion(currentVersion).getEntities());
            }
            newVersion.addEntities(entities);
            this.project.addNewVersion(versionId, newVersion);
        }

        public void addVersionedEntities(String versionId, Entity... entities)
        {
            this.addVersionedEntities(versionId, Arrays.asList(entities));
        }

        public void addVersionedClasses(String versionId, String... classNames)
        {
            List<Entity> entities = Arrays.stream(classNames).map(name -> TestTools.newClassEntity(name, this.project.getProjectId().toString())).collect(Collectors.toList());
            this.addVersionedEntities(versionId, entities);
        }

        public void addDependency(String versionId, String... projectDependencies)
        {
            InMemoryVersionMetadata newVersion = this.project.getOrCreateVersion(versionId);
            for (String dependencyString : projectDependencies)
            {
                DepotProjectVersion projectDependency = this.validateProjectDependency(dependencyString);
                newVersion.addDependency(projectDependency);
            }
        }

        private DepotProjectVersion validateProjectDependency(String dependencyString)
        {
            DepotProjectVersion parsedProjectDependency = DepotProjectVersion.parseDepotProjectVersion(dependencyString);
            String projectDependencyId = parsedProjectDependency.getDepotProjectId().toString();
            VersionId projectDependencyVersionId = VersionId.parseVersionId(parsedProjectDependency.getVersionId());

            if (!this.backend.projects.containsKey(projectDependencyId))
            {
                throw new IllegalStateException(String.format("Unknown project dependency %s", parsedProjectDependency));
            }
            InMemoryProjectMetadata projectDependency = this.backend.projects.get(projectDependencyId);
            InMemoryVersionMetadata projectDependencyVersion = projectDependency.getVersion(projectDependencyVersionId.toVersionIdString());
            if (projectDependencyVersion == null)
            {
                throw new IllegalStateException(String.format("Unknown project dependency version %s", dependencyString));
            }
            return parsedProjectDependency;
        }
    }

    public MetadataApi getMetadataApi()
    {
        return this.metadataApi;
    }
}
