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

import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.review.Review;
import org.finos.legend.sdlc.domain.model.review.ReviewState;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.server.domain.api.review.ReviewApi;

import javax.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public class InMemoryReviewApi implements ReviewApi
{
    @Inject
    public InMemoryReviewApi()
    {
    }

    @Override
    public Review getReview(String projectId, String reviewId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public List<Review> getReviews(String projectId, ReviewState state, Iterable<String> revisionIds, Instant since, Instant until, Integer limit)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public List<Review> getReviews(Set<ProjectType> projectTypes, boolean assignedToMe, boolean authoredByMe, List<String> labels, ReviewState state, Instant since, Instant until, Integer limit)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Review createReview(String projectId, String workspaceId, WorkspaceType workspaceType, String title, String description, List<String> labels)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Review closeReview(String projectId, String reviewId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Review reopenReview(String projectId, String reviewId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Review approveReview(String projectId, String reviewId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Review revokeReviewApproval(String projectId, String reviewId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Review rejectReview(String projectId, String reviewId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Review commitReview(String projectId, String reviewId, String message)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public ReviewUpdateStatus getReviewUpdateStatus(String projectId, String reviewId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public ReviewUpdateStatus updateReview(String projectId, String reviewId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Review editReview(String projectId, String reviewId, String title, String description, List<String> labels)
    {
        throw new UnsupportedOperationException("Not implemented");
    }
}
