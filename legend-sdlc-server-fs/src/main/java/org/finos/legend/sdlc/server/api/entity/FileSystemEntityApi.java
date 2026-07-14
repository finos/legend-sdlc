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

package org.finos.legend.sdlc.server.api.entity;

import org.finos.legend.sdlc.core.entity.EntityAccessOperations;
import org.finos.legend.sdlc.core.entity.EntityModificationOperations;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.entity.change.EntityChange;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.backend.api.entity.EntityAccessContext;
import org.finos.legend.sdlc.backend.api.entity.EntityApi;
import org.finos.legend.sdlc.backend.api.entity.EntityModificationContext;
import org.finos.legend.sdlc.project.source.SourceSpecification;
import org.finos.legend.sdlc.project.source.WorkspaceSourceSpecification;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.startup.FSConfiguration;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

public class FileSystemEntityApi extends FileSystemApiWithFileAccess implements EntityApi
{
    @Inject
    public FileSystemEntityApi(FSConfiguration fsConfiguration)
    {
        super(fsConfiguration);
    }

    @Override
    public EntityAccessContext getEntityAccessContext(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
        ProjectFileAccessProvider.FileAccessContext fileAccessContext = getProjectFileAccessProvider().getFileAccessContext(projectId, sourceSpecification, revisionId);
        return new EntityAccessContext()
        {
            @Override
            public Entity getEntity(String path)
            {
                return EntityAccessOperations.getEntity(fileAccessContext, path, null);
            }

            @Override
            public List<Entity> getEntities(Predicate<String> entityPathPredicate, Predicate<String> classifierPathPredicate, Predicate<? super Map<String, ?>> entityContentPredicate, boolean excludeInvalid)
            {
                return EntityAccessOperations.getEntities(fileAccessContext, entityPathPredicate, classifierPathPredicate, entityContentPredicate, excludeInvalid);
            }

            @Override
            public List<String> getEntityPaths(Predicate<String> entityPathPredicate, Predicate<String> classifierPathPredicate, Predicate<? super Map<String, ?>> entityContentPredicate)
            {
                return EntityAccessOperations.getEntityPaths(fileAccessContext, entityPathPredicate, classifierPathPredicate, entityContentPredicate);
            }
        };
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
        return new FileSystemEntityModificationContext(projectId, sourceSpecification);
    }

    public class FileSystemEntityModificationContext implements EntityModificationContext
    {
        private final String projectId;
        private final WorkspaceSourceSpecification sourceSpecification;

        private FileSystemEntityModificationContext(String projectId, WorkspaceSourceSpecification sourceSpecification)
        {
            this.projectId = projectId;
            this.sourceSpecification = Objects.requireNonNull(sourceSpecification, "source specification may not be null");
        }

        @Override
        public Revision updateEntities(Iterable<? extends Entity> entities, boolean replace, String message)
        {
            LegendSDLCServerException.validateNonNull(entities, "entities may not be null");
            LegendSDLCServerException.validateNonNull(message, "message may not be null");
            return FileSystemEntityApi.this.updateEntities(this.projectId, this.sourceSpecification, entities, replace, message);
        }

        @Override
        public Revision performChanges(List<? extends EntityChange> changes, String revisionId, String message)
        {
            LegendSDLCServerException.validateNonNull(changes, "changes may not be null");
            LegendSDLCServerException.validateNonNull(message, "message may not be null");
            EntityModificationOperations.validateEntityChanges(changes);
            return EntityModificationOperations.performChanges(getProjectFileAccessProvider(), this.projectId, this.sourceSpecification, revisionId, message, changes);
        }
    }

    public Revision updateEntities(String projectId, WorkspaceSourceSpecification sourceSpecification, Iterable<? extends Entity> newEntities, boolean replace, String message)
    {
        return EntityModificationOperations.updateEntities(getProjectFileAccessProvider(), projectId, sourceSpecification, newEntities, replace, message, String.valueOf(sourceSpecification));
    }
}
