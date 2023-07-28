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
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification;
import org.finos.legend.sdlc.server.domain.api.project.source.WorkspaceSourceSpecification;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSource;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSpecification;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;

public interface ComparisonApi
{
    /**
     * Get the comparison between workspace HEAD (current revision) and workspace BASE (creation revision).
     *
     * @param projectId              project id
     * @param workspaceSpecification workspace specification
     * @return comparison between workspace HEAD and BASE
     */
    Comparison getWorkspaceCreationComparison(String projectId, WorkspaceSpecification workspaceSpecification);

    /**
     * Get the comparison between the HEAD (current revision) of a workspace and its source.
     *
     * @param projectId              project id
     * @param workspaceSpecification workspace specification
     * @return comparison between user workspace HEAD and workspace source HEAD
     */
    Comparison getWorkspaceSourceComparison(String projectId, WorkspaceSpecification workspaceSpecification);

    /**
     * Get the comparison for a given review between the HEAD (current revision) of its source (workspace) and target.
     * This is equivalent to calling {@link #getWorkspaceSourceComparison} for the source workspace of the review.
     *
     * @param projectId project id
     * @param reviewId  review id
     * @return comparison between review source HEAD and target HEAD
     */
    Comparison getReviewComparison(String projectId, String reviewId);

    /**
     * Get the comparison for a given review between the HEAD (current revision) and BASE (creation revision) of its
     * source workspace. This is equivalent to calling {@link #getWorkspaceCreationComparison} for the source workspace
     * of the reivew.
     *
     * @param projectId project id
     * @param reviewId  review id
     * @return comparison between review workspace HEAD and BASE
     */
    Comparison getReviewWorkspaceCreationComparison(String projectId, String reviewId);


    // Deprecated APIs

    @Deprecated
    default Comparison getUserWorkspaceCreationComparison(String projectId, String workspaceId)
    {
        return getWorkspaceCreationComparison(projectId, workspaceId, WorkspaceType.USER);
    }

    @Deprecated
    default Comparison getGroupWorkspaceCreationComparison(String projectId, String workspaceId)
    {
        return this.getWorkspaceCreationComparison(projectId, workspaceId, WorkspaceType.GROUP);
    }

    @Deprecated
    default Comparison getWorkspaceCreationComparison(String projectId, SourceSpecification sourceSpecification)
    {
        if (!(sourceSpecification instanceof WorkspaceSourceSpecification))
        {
            throw new IllegalArgumentException("Not a workspace source specification: " + sourceSpecification);
        }
        return getWorkspaceCreationComparison(projectId, ((WorkspaceSourceSpecification) sourceSpecification).getWorkspaceSpecification());
    }

    @Deprecated
    default Comparison getWorkspaceCreationComparison(String projectId, String workspaceId, WorkspaceType workspaceType)
    {
        return getWorkspaceCreationComparison(projectId, WorkspaceSpecification.newWorkspaceSpecification(workspaceId, workspaceType));
    }

    @Deprecated
    default Comparison getUserWorkspaceProjectComparison(String projectId, String workspaceId)
    {
        return this.getWorkspaceProjectComparison(projectId, workspaceId, WorkspaceType.USER);
    }

    @Deprecated
    default Comparison getGroupWorkspaceProjectComparison(String projectId, String workspaceId)
    {
        return this.getWorkspaceProjectComparison(projectId, workspaceId, WorkspaceType.GROUP);
    }

    @Deprecated
    default Comparison getWorkspaceProjectComparison(String projectId, SourceSpecification sourceSpecification)
    {
        if (!(sourceSpecification instanceof WorkspaceSourceSpecification))
        {
            throw new IllegalArgumentException("Not a workspace source specification: " + sourceSpecification);
        }
        return getWorkspaceSourceComparison(projectId, ((WorkspaceSourceSpecification) sourceSpecification).getWorkspaceSpecification());
    }

    @Deprecated
    default Comparison getWorkspaceProjectComparison(String projectId, String workspaceId, WorkspaceType workspaceType)
    {
        return getWorkspaceSourceComparison(projectId, WorkspaceSpecification.newWorkspaceSpecification(workspaceId, workspaceType, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE, WorkspaceSource.projectWorkspaceSource()));
    }

    @Deprecated
    default Comparison getReviewComparison(String projectId, VersionId patchReleaseVersionId, String reviewId)
    {
        return getReviewComparison(projectId, reviewId);
    }

    @Deprecated
    default Comparison getReviewWorkspaceCreationComparison(String projectId, VersionId patchReleaseVersionId, String reviewId)
    {
        return getReviewWorkspaceCreationComparison(projectId, reviewId);
    }
}

