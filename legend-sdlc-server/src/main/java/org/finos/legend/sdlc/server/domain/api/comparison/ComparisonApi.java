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
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.domain.api.project.SourceSpecification;

public interface ComparisonApi
{
    /**
     * Get the comparison between user workspace HEAD and BASE
     * <p>
     * Given a user workspace, returns the comparison from the creation of the workspace
     * to the current revision of the workspace
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     * @return comparison between user workspace HEAD and BASE
     */
    default Comparison getUserWorkspaceCreationComparison(String projectId, String workspaceId)
    {
        return this.getWorkspaceCreationComparison(projectId, workspaceId, WorkspaceType.USER);
    }

    /**
     * Get the comparison between group workspace HEAD and BASE
     * <p>
     * Given a group workspace, returns the comparison from the creation of the workspace
     * to the current revision of the workspace
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     * @return comparison between group workspace HEAD and BASE
     */
    default Comparison getGroupWorkspaceCreationComparison(String projectId, String workspaceId)
    {
        return this.getWorkspaceCreationComparison(projectId, workspaceId, WorkspaceType.GROUP);
    }

    /**
     * Get the comparison between workspace HEAD and workspace BASE
     * <p>
     * Given a workspace, returns the comparison from the creation of the workspace
     * to the current revision of the workspace
     *
     * @param projectId              project id
     * @param sourceSpecification source specification
     * @return comparison between workspace HEAD and BASE
     */
    Comparison getWorkspaceCreationComparison(String projectId, SourceSpecification sourceSpecification);

    default Comparison getWorkspaceCreationComparison(String projectId, String workspaceId, WorkspaceType workspaceType)
    {
        return this.getWorkspaceCreationComparison(projectId, SourceSpecification.newSourceSpecification(workspaceId, workspaceType));
    }

    /**
     * Get the comparison between user workspace HEAD and project HEAD
     * <p>
     * Given a user workspace, returns the comparison from the current revision of the
     * project to the current revision of the workspace
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     * @return comparison between user workspace HEAD and project HEAD
     */
    default Comparison getUserWorkspaceProjectComparison(String projectId, String workspaceId)
    {
        return this.getWorkspaceProjectComparison(projectId, workspaceId, WorkspaceType.USER);
    }

    /**
     * Get the comparison between group workspace HEAD and project HEAD
     * <p>
     * Given a group workspace, returns the comparison from the current revision of the
     * project to the current revision of the workspace
     *
     * @param projectId   project id
     * @param workspaceId workspace id
     * @return comparison between group workspace HEAD and project HEAD
     */
    default Comparison getGroupWorkspaceProjectComparison(String projectId, String workspaceId)
    {
        return this.getWorkspaceProjectComparison(projectId, workspaceId, WorkspaceType.GROUP);
    }

    /**
     * Get the comparison between workspace HEAD and project HEAD
     * <p>
     * Given a workspace, returns the comparison from the current revision of the
     * project to the current revision of the workspace
     *
     * @param projectId              project id
     * @param sourceSpecification source specification
     * @return comparison between workspace HEAD and project HEAD
     */
    Comparison getWorkspaceProjectComparison(String projectId, SourceSpecification sourceSpecification);

    default Comparison getWorkspaceProjectComparison(String projectId, String workspaceId, WorkspaceType workspaceType)
    {
        return this.getWorkspaceProjectComparison(projectId, SourceSpecification.newSourceSpecification(workspaceId, workspaceType));
    }

    /**
     * Get the comparison for a given review (between review workspace HEAD and project HEAD)
     * <p>
     * Given a review, returns the comparison for that review
     * Uses the diff ref (start and end revision id for review) to get revisions ids for comparison
     *
     * @param projectId           project id
     * @param patchReleaseVersionId patch release version
     * @param reviewId            review id
     * @return comparison between review workspace HEAD and project HEAD
     */
    Comparison getReviewComparison(String projectId, VersionId patchReleaseVersionId, String reviewId);

    default Comparison getReviewComparison(String projectId, String reviewId)
    {
        return this.getReviewComparison(projectId, null, reviewId);
    }

    /**
     * Get the comparison for a given review (between review workspace HEAD and workspace BASE)
     *
     * @param projectId           project id
     * @param patchReleaseVersionId patch release version
     * @param reviewId            review id
     * @return comparison between review workspace HEAD and BASE
     */
    Comparison getReviewWorkspaceCreationComparison(String projectId, VersionId patchReleaseVersionId, String reviewId);

    default Comparison getReviewWorkspaceCreationComparison(String projectId, String reviewId)
    {
        return this.getReviewWorkspaceCreationComparison(projectId, null, reviewId);
    }
}

