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

package org.finos.legend.sdlc.server.domain.api.comparison;

import org.finos.legend.sdlc.domain.model.comparison.Comparison;

public interface ComparisonApi
{
    /**
     * Get the comparison between workspace HEAD and workspace BASE
     * <p>
     * Given a workspace, returns the comparison from the creation of the workspace
     * to the current revision of the workspace
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     * @return comparison between workspace HEAD and BASE
     */
    Comparison getWorkspaceCreationComparison(String projectId, String workspaceId);

    /**
     * Get the comparison between workspace HEAD and project HEAD
     * <p>
     * Given a workspace, returns the comparison from the current revision of the
     * project to the current revision of the workspace
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     * @return comparison between workspace HEAD and project HEAD
     */
    Comparison getWorkspaceProjectComparison(String projectId, String workspaceId);

    /**
     * Get the comparison for a given review (between review workspace HEAD and project HEAD)
     * <p>
     * Given a review, returns the comparison for that review
     * Uses the diff ref (start and end revision id for review) to get revisions ids for comparison
     *
     * @param projectId project id
     * @param reviewId  review id
     * @return comparison between review workspace HEAD and project HEAD
     */
    Comparison getReviewComparison(String projectId, String reviewId);


    /**
     * Get the comparison for a given review (between review workspace HEAD and workspace BASE)
     *
     * @param projectId project id
     * @param reviewId  review id
     * @return comparison between review workspace HEAD and BASE
     */
    Comparison getReviewWorkspaceCreationComparison(String projectId, String reviewId);
}

