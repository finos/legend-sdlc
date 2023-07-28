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
import org.eclipse.collections.impl.utility.Iterate;
import org.eclipse.collections.impl.utility.ListIterate;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.entity.change.EntityChange;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.domain.api.entity.EntityAccessContext;
import org.finos.legend.sdlc.server.domain.api.entity.EntityApi;
import org.finos.legend.sdlc.server.domain.api.entity.EntityModificationContext;
import org.finos.legend.sdlc.server.domain.api.project.source.PatchSourceSpecification;
import org.finos.legend.sdlc.server.domain.api.project.source.ProjectSourceSpecification;
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification;
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecificationVisitor;
import org.finos.legend.sdlc.server.domain.api.project.source.VersionSourceSpecification;
import org.finos.legend.sdlc.server.domain.api.project.source.WorkspaceSourceSpecification;
import org.finos.legend.sdlc.server.domain.api.workspace.PatchWorkspaceSource;
import org.finos.legend.sdlc.server.domain.api.workspace.ProjectWorkspaceSource;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSourceVisitor;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSpecification;
import org.finos.legend.sdlc.server.inmemory.backend.InMemoryBackend;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryPatch;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryProject;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryRevision;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryVersion;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryWorkspace;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.inject.Inject;

public class InMemoryEntityApi implements EntityApi
{
    private final InMemoryBackend backend;

    @Inject
    public InMemoryEntityApi(InMemoryBackend backend)
    {
        this.backend = backend;
    }

    @Override
    public EntityAccessContext getEntityAccessContext(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
        InMemoryProject project = this.backend.getProject(projectId);
        InMemoryRevision revision = sourceSpecification.visit(new SourceSpecificationVisitor<InMemoryRevision>()
        {
            @Override
            public InMemoryRevision visit(ProjectSourceSpecification sourceSpec)
            {
                return (revisionId == null) ? project.getCurrentRevision() : project.getRevision(revisionId);
            }

            @Override
            public InMemoryRevision visit(VersionSourceSpecification sourceSpec)
            {
                if (revisionId != null)
                {
                    throw new UnsupportedOperationException("Not implemented");
                }
                InMemoryVersion version = project.getVersion(sourceSpec.getVersionId().toVersionIdString());
                return version.getRevision();
            }

            @Override
            public InMemoryRevision visit(PatchSourceSpecification sourceSpec)
            {
                InMemoryPatch patch = project.getPatch(sourceSpec.getVersionId());
                return (revisionId == null) ? patch.getCurrentRevision() : patch.getRevision(revisionId);
            }

            @Override
            public InMemoryRevision visit(WorkspaceSourceSpecification sourceSpec)
            {
                WorkspaceSpecification workspaceSpec = sourceSpec.getWorkspaceSpecification();
                if (workspaceSpec.getAccessType() != ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE)
                {
                    throw new UnsupportedOperationException("Not implemented");
                }
                VersionId patchVersionId = workspaceSpec.getSource().visit(new WorkspaceSourceVisitor<VersionId>()
                {
                    @Override
                    public VersionId visit(ProjectWorkspaceSource source)
                    {
                        return null;
                    }

                    @Override
                    public VersionId visit(PatchWorkspaceSource source)
                    {
                        return source.getPatchVersionId();
                    }
                });
                InMemoryWorkspace workspace = (workspaceSpec.getType() == WorkspaceType.GROUP) ?
                        project.getGroupWorkspace(workspaceSpec.getId(), patchVersionId) :
                        project.getUserWorkspace(workspaceSpec.getId(), patchVersionId);
                return (revisionId == null) ? workspace.getCurrentRevision() : workspace.getRevision(revisionId);
            }
        });
        return new InMemoryEntityAccessContext(revision);
    }

    @Override
    public EntityAccessContext getReviewFromEntityAccessContext(String projectId, String reviewId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public EntityAccessContext getReviewToEntityAccessContext(String projectId, String reviewId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public EntityModificationContext getEntityModificationContext(String projectId, WorkspaceSourceSpecification sourceSpecification)
    {
        WorkspaceSpecification workspaceSpec = sourceSpecification.getWorkspaceSpecification();
        if (workspaceSpec.getAccessType() != ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE)
        {
            throw new UnsupportedOperationException("Not implemented");
        }
        InMemoryProject project = this.backend.getProject(projectId);
        VersionId patchVersionId = workspaceSpec.getSource().visit(new WorkspaceSourceVisitor<VersionId>()
        {
            @Override
            public VersionId visit(ProjectWorkspaceSource source)
            {
                return null;
            }

            @Override
            public VersionId visit(PatchWorkspaceSource source)
            {
                return source.getPatchVersionId();
            }
        });
        InMemoryWorkspace workspace = (workspaceSpec.getType() == WorkspaceType.GROUP) ?
                project.getGroupWorkspace(workspaceSpec.getId(), patchVersionId) :
                project.getUserWorkspace(workspaceSpec.getId(), patchVersionId);
        return new InMemoryEntityModificationContext(workspace.getCurrentRevision().getEntities());
    }

    static class InMemoryEntityAccessContext implements EntityAccessContext
    {
        private final InMemoryRevision revision;

        public InMemoryEntityAccessContext(InMemoryRevision revision)
        {
            this.revision = revision;
        }

        @Override
        public Entity getEntity(String path)
        {
            Entity entity = this.revision.getEntity(path);
            if (entity == null)
            {
                throw new IllegalStateException("Entity with path " + path + " not found");
            }
            return entity;
        }

        @Override
        public List<Entity> getEntities(Predicate<String> entityPathPredicate, Predicate<String> classifierPathPredicate, Predicate<? super Map<String, ?>> entityContentPredicate, boolean excludeInvalid)
        {
            return Iterate.select(this.revision.getEntities(),
                    e -> ((entityPathPredicate == null) || entityPathPredicate.test(e.getPath())) &&
                            ((classifierPathPredicate == null) || classifierPathPredicate.test(e.getClassifierPath())) &&
                            ((entityContentPredicate == null) || entityContentPredicate.test(e.getContent())),
                    Lists.mutable.empty());
        }

        @Override
        public List<String> getEntityPaths(Predicate<String> entityPathPredicate, Predicate<String> classifierPathPredicate, Predicate<? super Map<String, ?>> entityContentPredicate)
        {
            return ListIterate.collect(getEntities(entityPathPredicate, classifierPathPredicate, entityContentPredicate), Entity::getPath);
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
