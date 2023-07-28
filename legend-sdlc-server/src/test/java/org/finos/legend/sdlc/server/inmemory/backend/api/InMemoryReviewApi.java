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
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.review.Approval;
import org.finos.legend.sdlc.domain.model.review.Review;
import org.finos.legend.sdlc.domain.model.review.ReviewState;
import org.finos.legend.sdlc.server.domain.api.review.ReviewApi;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSpecification;
import org.finos.legend.sdlc.server.inmemory.backend.InMemoryBackend;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryProject;

import java.time.Instant;
import java.util.List;
import java.util.function.BiPredicate;
import javax.inject.Inject;

public class InMemoryReviewApi implements ReviewApi
{
    private final InMemoryBackend backend;
    
    @Inject
    public InMemoryReviewApi(InMemoryBackend backend)
    {
        this.backend = backend;
    }

    @Override
    public Review getReview(String projectId, String reviewId)
    {
        InMemoryProject inMemoryProject = this.backend.getProject(projectId);
        Review result = inMemoryProject.getReview(reviewId);

        return result;
    }
    
    @Override
    public List<Review> getReviews(String projectId, ReviewState state, Iterable<String> revisionIds, BiPredicate<String, WorkspaceType> workspaceIdAndTypePredicate, Instant since, Instant until, Integer limit)
    {
        InMemoryProject inMemoryProject = this.backend.getProject(projectId);
        return Lists.mutable.withAll(inMemoryProject.getReviews(state, revisionIds, since, until, limit));
    }

    @Override
    public List<Review> getReviews(boolean assignedToMe, boolean authoredByMe, List<String> labels, BiPredicate<String, WorkspaceType> workspaceIdAndTypePredicate, ReviewState state, Instant since, Instant until, Integer limit)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Review createReview(String projectId, WorkspaceSpecification workspaceSpecification, String title, String description, List<String> labels)
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
    public Approval getReviewApproval(String projectId, String reviewId)
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
