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

package org.finos.legend.sdlc.server.backend;

import org.finos.legend.sdlc.backend.api.review.ReviewApi;
import org.finos.legend.sdlc.backend.api.spi.UnsupportedCapabilityException;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.review.Approval;
import org.finos.legend.sdlc.domain.model.review.Review;
import org.finos.legend.sdlc.domain.model.review.ReviewState;
import org.finos.legend.sdlc.project.workspace.WorkspaceSource;
import org.finos.legend.sdlc.project.workspace.WorkspaceSpecification;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Compatibility affordance for backends without the {@code REVIEWS} capability (re-architecture Phase 5,
 * recorded amendment to the Phase 4 review's decision 2): review <em>enumeration</em> reports that no reviews
 * exist (an empty list) instead of failing with 501, because Legend Studio's editor initialization fetches the
 * open reviews of the current workspace and hard-blocks on any error. Every other review operation rethrows the
 * capability error, and the capability/discovery surface is untouched — the backend still does not declare
 * {@code REVIEWS}. Retained temporarily: once Studio adapts its review UI to
 * {@code GET /configuration/capabilities}, this class is removed and absent {@code REVIEWS} maps uniformly
 * to 501.
 */
public class NoReviewsReviewApi implements ReviewApi
{
    private final UnsupportedCapabilityException cause;

    public NoReviewsReviewApi(UnsupportedCapabilityException cause)
    {
        this.cause = Objects.requireNonNull(cause, "cause may not be null");
    }

    @Override
    public List<Review> getReviews(String projectId, ReviewState state, Iterable<String> revisionIds, BiPredicate<String, WorkspaceType> workspaceIdAndTypePredicate, Set<WorkspaceSource> sources, Instant since, Instant until, Integer limit)
    {
        return Collections.emptyList();
    }

    @Override
    public List<Review> getReviews(boolean assignedToMe, boolean authoredByMe, List<String> labels, BiPredicate<String, WorkspaceType> workspaceIdAndTypePredicate, ReviewState state, Instant since, Instant until, Integer limit)
    {
        return Collections.emptyList();
    }

    @Override
    public Review getReview(String projectId, String reviewId)
    {
        throw this.cause;
    }

    @Override
    public Review createReview(String projectId, WorkspaceSpecification workspaceSpecification, String title, String description, List<String> labels)
    {
        throw this.cause;
    }

    @Override
    public Review editReview(String projectId, String reviewId, String title, String description, List<String> labels)
    {
        throw this.cause;
    }

    @Override
    public Review closeReview(String projectId, String reviewId)
    {
        throw this.cause;
    }

    @Override
    public Review reopenReview(String projectId, String reviewId)
    {
        throw this.cause;
    }

    @Override
    public Review approveReview(String projectId, String reviewId)
    {
        throw this.cause;
    }

    @Override
    public Review revokeReviewApproval(String projectId, String reviewId)
    {
        throw this.cause;
    }

    @Override
    public Review rejectReview(String projectId, String reviewId)
    {
        throw this.cause;
    }

    @Override
    public Approval getReviewApproval(String projectId, String reviewId)
    {
        throw this.cause;
    }

    @Override
    public Review commitReview(String projectId, String reviewId, String message)
    {
        throw this.cause;
    }

    @Override
    public ReviewUpdateStatus getReviewUpdateStatus(String projectId, String reviewId)
    {
        throw this.cause;
    }

    @Override
    public ReviewUpdateStatus updateReview(String projectId, String reviewId)
    {
        throw this.cause;
    }
}
