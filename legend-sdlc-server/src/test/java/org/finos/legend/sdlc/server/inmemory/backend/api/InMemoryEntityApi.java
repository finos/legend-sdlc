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

import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.entity.change.EntityChange;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.server.domain.api.entity.EntityAccessContext;
import org.finos.legend.sdlc.server.domain.api.entity.EntityApi;
import org.finos.legend.sdlc.server.domain.api.entity.EntityModificationContext;
import org.finos.legend.sdlc.server.domain.api.project.SourceSpecification;
import org.finos.legend.sdlc.server.inmemory.backend.InMemoryBackend;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryProject;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryRevision;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryVersion;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryWorkspace;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class InMemoryEntityApi implements EntityApi
{
    private final InMemoryBackend backend;

    @Inject
    public InMemoryEntityApi(InMemoryBackend backend)
    {
        this.backend = backend;
    }

    @Override
    public EntityAccessContext getProjectEntityAccessContext(String projectId, VersionId patchReleaseVersionId)
    {
        InMemoryProject project = this.backend.getProject(projectId);
        return new InMemoryEntityAccessContext(project.getCurrentRevision().getEntities());
    }

    @Override
    public EntityAccessContext getProjectRevisionEntityAccessContext(String projectId, VersionId patchReleaseVersionId, String revisionId)
    {
        InMemoryProject project = this.backend.getProject(projectId);
        return new InMemoryEntityAccessContext(project.getCurrentRevision().getEntities());
    }

    @Override
    public EntityAccessContext getWorkspaceEntityAccessContext(String projectId, SourceSpecification sourceSpecification)
    {
        InMemoryProject project = this.backend.getProject(projectId);
        InMemoryWorkspace workspace = sourceSpecification.getWorkspaceType() == WorkspaceType.GROUP ? project.getGroupWorkspace(sourceSpecification.getWorkspaceId(), sourceSpecification.getPatchReleaseVersionId()) : project.getUserWorkspace(sourceSpecification.getWorkspaceId(), sourceSpecification.getPatchReleaseVersionId());
        return new InMemoryEntityAccessContext(workspace.getCurrentRevision().getEntities());
    }

    @Override
    public EntityAccessContext getBackupWorkspaceEntityAccessContext(String projectId, SourceSpecification sourceSpecification)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public EntityAccessContext getWorkspaceWithConflictResolutionEntityAccessContext(String projectId, SourceSpecification sourceSpecification)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public EntityAccessContext getWorkspaceRevisionEntityAccessContext(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
        InMemoryProject project = this.backend.getProject(projectId);
        InMemoryWorkspace workspace = sourceSpecification.getWorkspaceType() == WorkspaceType.GROUP ? project.getGroupWorkspace(sourceSpecification.getWorkspaceId(), sourceSpecification.getPatchReleaseVersionId()) : project.getUserWorkspace(sourceSpecification.getWorkspaceId(), sourceSpecification.getPatchReleaseVersionId());
        InMemoryRevision revision = workspace.getRevision(revisionId);
        return new InMemoryEntityAccessContext(revision.getEntities());
    }

    @Override
    public EntityAccessContext getBackupWorkspaceRevisionEntityAccessContext(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public EntityAccessContext getWorkspaceWithConflictResolutionRevisionEntityAccessContext(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public EntityAccessContext getReviewFromEntityAccessContext(String projectId, VersionId patchReleaseVersionId, String reviewId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public EntityAccessContext getReviewToEntityAccessContext(String projectId, VersionId patchReleaseVersionId, String reviewId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public EntityAccessContext getVersionEntityAccessContext(String projectId, VersionId versionId)
    {
        InMemoryProject project = this.backend.getProject(projectId);
        InMemoryVersion version = project.getVersion(versionId.toVersionIdString());
        return new InMemoryEntityAccessContext(version.getRevision().getEntities());
    }

    @Override
    public EntityModificationContext getWorkspaceEntityModificationContext(String projectId, SourceSpecification sourceSpecification)
    {
        InMemoryProject project = this.backend.getProject(projectId);
        InMemoryWorkspace workspace = sourceSpecification.getWorkspaceType() == WorkspaceType.GROUP ? project.getGroupWorkspace(sourceSpecification.getWorkspaceId(), sourceSpecification.getPatchReleaseVersionId()) : project.getUserWorkspace(sourceSpecification.getWorkspaceId(), sourceSpecification.getPatchReleaseVersionId());
        return new InMemoryEntityModificationContext(workspace.getCurrentRevision().getEntities());
    }

    @Override
    public EntityModificationContext getWorkspaceWithConflictResolutionEntityModificationContext(String projectId, SourceSpecification sourceSpecification)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    static class InMemoryEntityAccessContext implements EntityAccessContext
    {
        private final Iterable<Entity> entities;

        public InMemoryEntityAccessContext(Iterable<Entity> entities)
        {
            this.entities = entities;
        }

        @Override
        public Entity getEntity(String path)
        {
            List<Entity> matches = this.getEntities((p) -> p.equals(path), null, null);
            if (matches.size() > 1)
            {
                throw new IllegalStateException(String.format("Found %d instead of 1 matches for entity with path %s", matches.size(), path));
            }
            if (matches.size() == 0)
            {
                throw new IllegalStateException(String.format("Entity with path %s not found", path));
            }
            return matches.get(0);
        }

        @Override
        public List<Entity> getEntities(Predicate<String> entityPathPredicate, Predicate<String> classifierPathPredicate, Predicate<? super Map<String, ?>> entityContentPredicate, boolean excludeInvalid)
        {
            Stream<Entity> stream = StreamSupport.stream(this.entities.spliterator(), false);
            if (entityPathPredicate != null)
            {
                stream = stream.filter(entity -> entityPathPredicate.test(entity.getPath()));
            }

            if (classifierPathPredicate != null)
            {
                stream = stream.filter(entity -> classifierPathPredicate.test(entity.getClassifierPath()));
            }

            if (entityContentPredicate != null)
            {
                stream = stream.filter(entity -> entityContentPredicate.test(entity.getContent()));
            }

            return stream.collect(Collectors.toList());
        }

        @Override
        public List<String> getEntityPaths(Predicate<String> entityPathPredicate, Predicate<String> classifierPathPredicate, Predicate<? super Map<String, ?>> entityContentPredicate)
        {
            List<Entity> entities = this.getEntities(entityPathPredicate, classifierPathPredicate, entityContentPredicate);
            return entities.stream().map(Entity::getPath).collect(Collectors.toList());
        }
    }

    static class InMemoryEntityModificationContext implements EntityModificationContext
    {
        private final Iterable<Entity> entities;

        public InMemoryEntityModificationContext(Iterable<Entity> entities)
        {
            this.entities = entities;
        }

        @Override
        public Revision updateEntities(Iterable<? extends Entity> entities, boolean replace, String message)
        {
            if (!replace)
            {
                throw new UnsupportedOperationException("Non-replace entity update is not supported in inMemory backend.");
            }

            InMemoryRevision revision = new InMemoryRevision(message + "-" + Math.random());
            revision.removeEntities(this.entities);
            revision.addEntities(entities);

            return revision;
        }

        @Override
        public Revision performChanges(List<? extends EntityChange> changes, String revisionId, String message)
        {
            throw new UnsupportedOperationException("Not implemented");
        }
    }
}
