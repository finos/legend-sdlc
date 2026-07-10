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

package org.finos.legend.sdlc.backend.api.comparison;

import org.finos.legend.sdlc.backend.api.review.ReviewApi;
import org.finos.legend.sdlc.core.comparison.ComparisonOperations;
import org.finos.legend.sdlc.domain.model.comparison.Comparison;
import org.finos.legend.sdlc.domain.model.review.Review;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.error.LegendSDLCException;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider;
import org.finos.legend.sdlc.project.source.SourceSpecification;
import org.finos.legend.sdlc.project.workspace.WorkspaceSpecification;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Generic {@link ComparisonApi} over a {@link ProjectFileAccessProvider}: revisions come from the provider's
 * revision access contexts (workspace HEAD = current revision of the workspace source; workspace BASE = its base
 * revision; source HEAD = current revision of the workspace's source), and the comparison itself is
 * {@link ComparisonOperations#compare}'s byte-level file walk. Backends with native diffing override this with
 * their own implementation.
 * <p>
 * The review comparisons resolve the review through the supplied {@link ReviewApi} and assume a project-source
 * workspace (the {@code Review} model does not carry a workspace source); backends whose reviews can target
 * other sources (e.g. patch release branches) should override them.
 */
public class DefaultComparisonApi implements ComparisonApi
{
    private final ProjectFileAccessProvider fileAccessProvider;
    private final Supplier<? extends ReviewApi> reviewApiSupplier;

    /**
     * @param fileAccessProvider the backend's file access provider
     * @param reviewApiSupplier  supplies the review api for the review comparisons; expected to throw for
     *                           backends without the REVIEWS capability (the session accessor does exactly this)
     */
    public DefaultComparisonApi(ProjectFileAccessProvider fileAccessProvider, Supplier<? extends ReviewApi> reviewApiSupplier)
    {
        this.fileAccessProvider = Objects.requireNonNull(fileAccessProvider, "fileAccessProvider may not be null");
        this.reviewApiSupplier = Objects.requireNonNull(reviewApiSupplier, "reviewApiSupplier may not be null");
    }

    @Override
    public Comparison getWorkspaceCreationComparison(String projectId, WorkspaceSpecification workspaceSpecification)
    {
        LegendSDLCException.validateNonNull(projectId, "projectId may not be null", 400);
        LegendSDLCException.validateNonNull(workspaceSpecification, "workspace specification may not be null", 400);

        SourceSpecification workspaceSourceSpec = workspaceSpecification.getSourceSpecification();
        ProjectFileAccessProvider.RevisionAccessContext revisionContext = this.fileAccessProvider.getRevisionAccessContext(projectId, workspaceSourceSpec);
        Revision currentRevision = revisionContext.getCurrentRevision();
        if (currentRevision == null)
        {
            throw new LegendSDLCException("Could not access current revision for " + workspaceSpecification + " in project " + projectId);
        }
        Revision baseRevision = revisionContext.getBaseRevision();
        if (baseRevision == null)
        {
            throw new LegendSDLCException("Could not access creation revision for " + workspaceSpecification + " in project " + projectId);
        }
        return compare(projectId, workspaceSourceSpec, baseRevision.getId(), workspaceSourceSpec, currentRevision.getId());
    }

    @Override
    public Comparison getWorkspaceSourceComparison(String projectId, WorkspaceSpecification workspaceSpecification)
    {
        LegendSDLCException.validateNonNull(projectId, "projectId may not be null", 400);
        LegendSDLCException.validateNonNull(workspaceSpecification, "workspace specification may not be null", 400);

        SourceSpecification workspaceSourceSpec = workspaceSpecification.getSourceSpecification();
        SourceSpecification sourceSourceSpec = workspaceSpecification.getSource().getSourceSpecification();
        Revision workspaceRevision = this.fileAccessProvider.getRevisionAccessContext(projectId, workspaceSourceSpec).getCurrentRevision();
        if (workspaceRevision == null)
        {
            throw new LegendSDLCException("Could not access current revision for " + workspaceSpecification + " in project " + projectId);
        }
        Revision sourceRevision = this.fileAccessProvider.getRevisionAccessContext(projectId, sourceSourceSpec).getCurrentRevision();
        if (sourceRevision == null)
        {
            throw new LegendSDLCException("Could not access current revision for " + workspaceSpecification.getSource() + " in project " + projectId);
        }
        return compare(projectId, sourceSourceSpec, sourceRevision.getId(), workspaceSourceSpec, workspaceRevision.getId());
    }

    @Override
    public Comparison getReviewComparison(String projectId, String reviewId)
    {
        return getWorkspaceSourceComparison(projectId, getReviewWorkspaceSpecification(projectId, reviewId));
    }

    @Override
    public Comparison getReviewWorkspaceCreationComparison(String projectId, String reviewId)
    {
        return getWorkspaceCreationComparison(projectId, getReviewWorkspaceSpecification(projectId, reviewId));
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

    private Comparison compare(String projectId, SourceSpecification fromSpec, String fromRevisionId, SourceSpecification toSpec, String toRevisionId)
    {
        ProjectFileAccessProvider.FileAccessContext fromContext = this.fileAccessProvider.getFileAccessContext(projectId, fromSpec, fromRevisionId);
        ProjectFileAccessProvider.FileAccessContext toContext = this.fileAccessProvider.getFileAccessContext(projectId, toSpec, toRevisionId);
        return ComparisonOperations.compare(fromContext, toContext, fromRevisionId, toRevisionId);
    }
}
