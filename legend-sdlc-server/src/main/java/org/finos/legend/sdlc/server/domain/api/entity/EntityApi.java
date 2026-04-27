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

package org.finos.legend.sdlc.server.domain.api.entity;

import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification;
import org.finos.legend.sdlc.server.domain.api.project.source.WorkspaceSourceSpecification;

public interface EntityApi
{
    // Entity access

    EntityAccessContext getEntityAccessContext(String projectId, SourceSpecification sourceSpecification, String revisionId);

    default EntityAccessContext getEntityAccessContext(String projectId, SourceSpecification sourceSpecification)
    {
        return getEntityAccessContext(projectId, sourceSpecification, null);
    }

    EntityAccessContext getReviewFromEntityAccessContext(String projectId, String reviewId);

    EntityAccessContext getReviewToEntityAccessContext(String projectId, String reviewId);

    // Entity modification

    EntityModificationContext getEntityModificationContext(String projectId, WorkspaceSourceSpecification sourceSpecification);

    // Deprecated APIs

    @Deprecated
    default EntityAccessContext getReviewFromEntityAccessContext(String projectId, VersionId patchReleaseVersionId, String reviewId)
    {
        return getReviewFromEntityAccessContext(projectId, reviewId);
    }

    @Deprecated
    default EntityAccessContext getReviewToEntityAccessContext(String projectId, VersionId patchReleaseVersionId, String reviewId)
    {
        return getReviewToEntityAccessContext(projectId, reviewId);
    }
}
