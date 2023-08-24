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

package org.finos.legend.sdlc.server.api.review;

import org.finos.legend.sdlc.domain.model.project.DevelopmentStream;
import org.finos.legend.sdlc.domain.model.review.Approval;
import org.finos.legend.sdlc.domain.model.review.Review;
import org.finos.legend.sdlc.domain.model.review.ReviewState;
import org.finos.legend.sdlc.server.domain.api.review.ReviewApi;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSpecification;
import org.finos.legend.sdlc.server.exception.FSException;

import javax.inject.Inject;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class FileSystemReviewApi implements ReviewApi
{
    @Inject
    public FileSystemReviewApi()
    {
    }

    @Override
    public Review getReview(String projectId, String reviewId)
    {
        throw FSException.unavailableFeature();
    }

    @Override
    public List<Review> getReviews(String projectId, Set<DevelopmentStream> workspaceSources, ReviewState state, Iterable<String> revisionIds, Instant since, Instant until, Integer limit)
    {
        return Collections.emptyList();
    }

    @Override
    public List<Review> getReviews(boolean assignedToMe, boolean authoredByMe, Set<DevelopmentStream> workspaceSources, List<String> labels, ReviewState state, Instant since, Instant until, Integer limit)
    {
        return Collections.emptyList();
    }

    @Override
    public Review createReview(String projectId, WorkspaceSpecification workspaceSpecification, String title, String description, List<String> labels)
    {
        throw FSException.unavailableFeature();
    }

    @Override
    public Review editReview(String projectId, String reviewId, String title, String description, List<String> labels)
    {
        throw FSException.unavailableFeature();
    }

    @Override
    public Review closeReview(String projectId, String reviewId)
    {
        throw FSException.unavailableFeature();
    }

    @Override
    public Review reopenReview(String projectId, String reviewId)
    {
        throw FSException.unavailableFeature();
    }

    @Override
    public Review approveReview(String projectId, String reviewId)
    {
        throw FSException.unavailableFeature();
    }

    @Override
    public Review revokeReviewApproval(String projectId, String reviewId)
    {
        throw FSException.unavailableFeature();
    }

    @Override
    public Review rejectReview(String projectId, String reviewId)
    {
        throw FSException.unavailableFeature();
    }

    @Override
    public Approval getReviewApproval(String projectId, String reviewId)
    {
        throw FSException.unavailableFeature();
    }

    @Override
    public Review commitReview(String projectId, String reviewId, String message)
    {
        throw FSException.unavailableFeature();
    }

    @Override
    public ReviewUpdateStatus getReviewUpdateStatus(String projectId, String reviewId)
    {
        throw FSException.unavailableFeature();
    }

    @Override
    public ReviewUpdateStatus updateReview(String projectId, String reviewId)
    {
        throw FSException.unavailableFeature();
    }
}
