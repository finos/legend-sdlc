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

package org.finos.legend.sdlc.backend.api.entity;

import org.finos.legend.sdlc.backend.api.review.ReviewApi;
import org.finos.legend.sdlc.backend.api.spi.Backend;
import org.finos.legend.sdlc.backend.api.spi.BackendCapability;
import org.finos.legend.sdlc.core.entity.EntityAccessOperations;
import org.finos.legend.sdlc.core.entity.EntityModificationOperations;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.entity.change.EntityChange;
import org.finos.legend.sdlc.domain.model.review.Review;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.error.LegendSDLCException;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider;
import org.finos.legend.sdlc.project.source.SourceSpecification;
import org.finos.legend.sdlc.project.source.WorkspaceSourceSpecification;
import org.finos.legend.sdlc.project.workspace.WorkspaceSpecification;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Generic {@link EntityApi} over a {@link ProjectFileAccessProvider}: entity read and write are the core entity
 * operations ({@link EntityAccessOperations}/{@link EntityModificationOperations}) over the provider's file
 * access and modification contexts. Backends with native entity handling override this with their own
 * implementation.
 * <p>
 * The review access contexts resolve the review through the supplied {@link ReviewApi} and mirror the default
 * comparison semantics: the "from" side is the review workspace's source at its current revision, the "to" side
 * the workspace at its current revision. Backends whose reviews carry their own revision references (e.g. merge
 * request diff refs) should override them.
 */
public class DefaultEntityApi implements EntityApi
{
    private final Backend backend;
    private final ProjectFileAccessProvider fileAccessProvider;
    private final Supplier<? extends ReviewApi> reviewApiSupplier;

    /**
     * @param backend            backend whose declared capabilities gate scoped access (version/patch sources)
     * @param fileAccessProvider the backend's file access provider
     * @param reviewApiSupplier  supplies the review api for the review access contexts; expected to throw for
     *                           backends without the REVIEWS capability (the session accessor does exactly this)
     */
    public DefaultEntityApi(Backend backend, ProjectFileAccessProvider fileAccessProvider, Supplier<? extends ReviewApi> reviewApiSupplier)
    {
        this.backend = Objects.requireNonNull(backend, "backend may not be null");
        this.fileAccessProvider = Objects.requireNonNull(fileAccessProvider, "fileAccessProvider may not be null");
        this.reviewApiSupplier = Objects.requireNonNull(reviewApiSupplier, "reviewApiSupplier may not be null");
    }

    @Override
    public EntityAccessContext getEntityAccessContext(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
        LegendSDLCException.validateNonNull(projectId, "projectId may not be null", 400);
        LegendSDLCException.validateNonNull(sourceSpecification, "sourceSpecification may not be null", 400);
        BackendCapability.checkSourceScope(this.backend, sourceSpecification);
        return newAccessContext(projectId, sourceSpecification, revisionId);
    }

    @Override
    public EntityAccessContext getReviewFromEntityAccessContext(String projectId, String reviewId)
    {
        WorkspaceSpecification workspaceSpec = getReviewWorkspaceSpecification(projectId, reviewId);
        return newAccessContext(projectId, workspaceSpec.getSource().getSourceSpecification(), null);
    }

    @Override
    public EntityAccessContext getReviewToEntityAccessContext(String projectId, String reviewId)
    {
        WorkspaceSpecification workspaceSpec = getReviewWorkspaceSpecification(projectId, reviewId);
        return newAccessContext(projectId, workspaceSpec.getSourceSpecification(), null);
    }

    @Override
    public EntityModificationContext getEntityModificationContext(String projectId, WorkspaceSourceSpecification sourceSpecification)
    {
        LegendSDLCException.validateNonNull(projectId, "projectId may not be null", 400);
        LegendSDLCException.validateNonNull(sourceSpecification, "sourceSpecification may not be null", 400);
        BackendCapability.checkSourceScope(this.backend, sourceSpecification);
        return new DefaultEntityModificationContext(projectId, sourceSpecification);
    }

    private WorkspaceSpecification getReviewWorkspaceSpecification(String projectId, String reviewId)
    {
        LegendSDLCException.validateNonNull(projectId, "projectId may not be null", 400);
        LegendSDLCException.validateNonNull(reviewId, "reviewId may not be null", 400);

        Review review = this.reviewApiSupplier.get().getReview(projectId, reviewId);
        if (review == null)
        {
            throw new LegendSDLCException("Unknown review in project " + projectId + ": " + reviewId, 404);
        }
        return WorkspaceSpecification.newWorkspaceSpecification(review.getWorkspaceId(), review.getWorkspaceType());
    }

    private EntityAccessContext newAccessContext(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
        return new EntityAccessContext()
        {
            @Override
            public Entity getEntity(String path)
            {
                return EntityAccessOperations.getEntity(getFileAccessContext(), path, getReferenceInfo(projectId, sourceSpecification, revisionId));
            }

            @Override
            public List<Entity> getEntities(Predicate<String> entityPathPredicate, Predicate<String> classifierPathPredicate, Predicate<? super Map<String, ?>> entityContentPredicate, boolean excludeInvalid)
            {
                return EntityAccessOperations.getEntities(getFileAccessContext(), entityPathPredicate, classifierPathPredicate, entityContentPredicate, excludeInvalid);
            }

            @Override
            public List<String> getEntityPaths(Predicate<String> entityPathPredicate, Predicate<String> classifierPathPredicate, Predicate<? super Map<String, ?>> entityContentPredicate)
            {
                return EntityAccessOperations.getEntityPaths(getFileAccessContext(), entityPathPredicate, classifierPathPredicate, entityContentPredicate);
            }

            private ProjectFileAccessProvider.FileAccessContext getFileAccessContext()
            {
                return DefaultEntityApi.this.fileAccessProvider.getFileAccessContext(projectId, sourceSpecification, revisionId);
            }
        };
    }

    private class DefaultEntityModificationContext implements EntityModificationContext
    {
        private final String projectId;
        private final WorkspaceSourceSpecification sourceSpecification;

        private DefaultEntityModificationContext(String projectId, WorkspaceSourceSpecification sourceSpecification)
        {
            this.projectId = projectId;
            this.sourceSpecification = sourceSpecification;
        }

        @Override
        public Revision updateEntities(Iterable<? extends Entity> entities, boolean replace, String message)
        {
            LegendSDLCException.validateNonNull(entities, "entities may not be null", 400);
            LegendSDLCException.validateNonNull(message, "message may not be null", 400);
            return EntityModificationOperations.updateEntities(DefaultEntityApi.this.fileAccessProvider, this.projectId, this.sourceSpecification, entities, replace, message, getReferenceInfo(this.projectId, this.sourceSpecification, null));
        }

        @Override
        public Revision performChanges(List<? extends EntityChange> changes, String revisionId, String message)
        {
            LegendSDLCException.validateNonNull(changes, "changes may not be null", 400);
            LegendSDLCException.validateNonNull(message, "message may not be null", 400);
            EntityModificationOperations.validateEntityChanges(changes);
            return EntityModificationOperations.performChanges(DefaultEntityApi.this.fileAccessProvider, this.projectId, this.sourceSpecification, revisionId, message, changes);
        }
    }

    private static String getReferenceInfo(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
        StringBuilder builder = new StringBuilder();
        if (revisionId != null)
        {
            builder.append("revision ").append(revisionId).append(" of ");
        }
        return builder.append(sourceSpecification).append(" of project ").append(projectId).toString();
    }
}
