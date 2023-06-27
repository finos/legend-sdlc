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

package org.finos.legend.sdlc.server.backend.simple.api.review;

import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.review.Approval;
import org.finos.legend.sdlc.domain.model.review.Review;
import org.finos.legend.sdlc.domain.model.review.ReviewState;
import org.finos.legend.sdlc.server.backend.simple.exception.UnavailableFeature;
import org.finos.legend.sdlc.server.domain.api.review.ReviewApi;

import javax.inject.Inject;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;

public class SimpleBackendReviewApi implements ReviewApi
{
    @Inject
    public SimpleBackendReviewApi()
    {
    }

    @Override
    public Review getReview(String projectId, String reviewId)
    {
        return null;
    }

    @Override
    public List<Review> getReviews(String projectId, ReviewState state, Iterable<String> revisionIds, BiPredicate<String, WorkspaceType> workspaceIdAndTypePredicate, Instant since, Instant until, Integer limit)
    {
        return Collections.emptyList();
    }

    @Override
    public List<Review> getReviews(boolean assignedToMe, boolean authoredByMe, List<String> labels, BiPredicate<String, WorkspaceType> workspaceIdAndTypePredicate, ReviewState state, Instant since, Instant until, Integer limit)
    {
        return Collections.emptyList();
    }

    @Override
    public Review createReview(String projectId, String workspaceId, WorkspaceType workspaceType, String title, String description, List<String> labels)
    {
        throw UnavailableFeature.exception();
    }

    @Override
    public Review closeReview(String projectId, String reviewId)
    {
        throw UnavailableFeature.exception();
    }

    @Override
    public Review reopenReview(String projectId, String reviewId)
    {
        throw UnavailableFeature.exception();
    }

    @Override
    public Review approveReview(String projectId, String reviewId)
    {
        throw UnavailableFeature.exception();
    }

    @Override
    public Review revokeReviewApproval(String projectId, String reviewId)
    {
        throw UnavailableFeature.exception();
    }

    @Override
    public Review rejectReview(String projectId, String reviewId)
    {
        throw UnavailableFeature.exception();
    }

    @Override
    public Approval getReviewApproval(String projectId, String reviewId)
    {
        throw UnavailableFeature.exception();
    }

    @Override
    public Review commitReview(String projectId, String reviewId, String message)
    {
        throw UnavailableFeature.exception();
    }

    @Override
    public ReviewUpdateStatus getReviewUpdateStatus(String projectId, String reviewId)
    {
        throw UnavailableFeature.exception();
    }

    @Override
    public ReviewUpdateStatus updateReview(String projectId, String reviewId)
    {
        throw UnavailableFeature.exception();
    }

    @Override
    public Review editReview(String projectId, String reviewId, String title, String description, List<String> labels)
    {
        throw UnavailableFeature.exception();
    }
}
