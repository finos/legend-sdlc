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

package org.finos.legend.sdlc.backend.api.project;

import org.finos.legend.sdlc.backend.api.review.ReviewApi;
import org.finos.legend.sdlc.backend.api.spi.Backend;
import org.finos.legend.sdlc.backend.api.spi.BackendCapability;
import org.finos.legend.sdlc.core.project.ProjectConfigurationUpdater;
import org.finos.legend.sdlc.core.project.ProjectStructureUpdater;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactTypeGenerationConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectStructureVersion;
import org.finos.legend.sdlc.domain.model.review.Review;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.error.LegendSDLCException;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider;
import org.finos.legend.sdlc.project.source.SourceSpecification;
import org.finos.legend.sdlc.project.source.WorkspaceSourceSpecification;
import org.finos.legend.sdlc.project.structure.ProjectStructure;
import org.finos.legend.sdlc.project.structure.ProjectStructurePlatformExtensions;
import org.finos.legend.sdlc.project.structure.extension.ProjectStructureExtensionProvider;
import org.finos.legend.sdlc.project.workspace.WorkspaceSpecification;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Generic {@link ProjectConfigurationApi} over a {@link ProjectFileAccessProvider}: configuration read is the L2
 * project-structure read-side over the provider's file access contexts, and configuration update is the L3
 * {@link ProjectStructureUpdater}, applied with the deployment's project structure extensions. Backends with
 * native handling (or richer status reporting) override the relevant methods.
 * <p>
 * The review configurations resolve the review through the supplied {@link ReviewApi} and mirror the default
 * comparison semantics (from = the review workspace's source at its current revision, to = the workspace at its
 * current revision); the configuration status report carries no review ids (a backend whose project-setup flow
 * goes through reviews should override {@link #getProjectConfigurationStatus}).
 */
public class DefaultProjectConfigurationApi implements ProjectConfigurationApi
{
    private final Backend backend;
    private final ProjectFileAccessProvider fileAccessProvider;
    private final ProjectStructureExtensionProvider extensionProvider;
    private final ProjectStructurePlatformExtensions platformExtensions;
    private final Supplier<? extends ReviewApi> reviewApiSupplier;

    /**
     * @param backend            backend whose declared capabilities gate scoped access (version/patch sources)
     * @param fileAccessProvider the backend's file access provider
     * @param extensionProvider  the deployment's project structure extension provider
     * @param platformExtensions the deployment's platform extensions
     * @param reviewApiSupplier  supplies the review api for the review configurations; expected to throw for
     *                           backends without the REVIEWS capability (the session accessor does exactly this)
     */
    public DefaultProjectConfigurationApi(Backend backend, ProjectFileAccessProvider fileAccessProvider, ProjectStructureExtensionProvider extensionProvider, ProjectStructurePlatformExtensions platformExtensions, Supplier<? extends ReviewApi> reviewApiSupplier)
    {
        this.backend = Objects.requireNonNull(backend, "backend may not be null");
        this.fileAccessProvider = Objects.requireNonNull(fileAccessProvider, "fileAccessProvider may not be null");
        this.extensionProvider = Objects.requireNonNull(extensionProvider, "extensionProvider may not be null");
        this.platformExtensions = platformExtensions;
        this.reviewApiSupplier = Objects.requireNonNull(reviewApiSupplier, "reviewApiSupplier may not be null");
    }

    @Override
    public ProjectConfiguration getProjectConfiguration(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
        LegendSDLCException.validateNonNull(projectId, "projectId may not be null", 400);
        LegendSDLCException.validateNonNull(sourceSpecification, "sourceSpecification may not be null", 400);
        BackendCapability.checkSourceScope(this.backend, sourceSpecification);
        ProjectConfiguration config = ProjectStructure.getProjectConfiguration(projectId, sourceSpecification, revisionId, this.fileAccessProvider);
        return (config == null) ? ProjectStructure.getDefaultProjectConfiguration(projectId) : config;
    }

    @Override
    public ProjectConfiguration getReviewFromProjectConfiguration(String projectId, String reviewId)
    {
        WorkspaceSpecification workspaceSpec = getReviewWorkspaceSpecification(projectId, reviewId);
        return getProjectConfiguration(projectId, workspaceSpec.getSource().getSourceSpecification(), null);
    }

    @Override
    public ProjectConfiguration getReviewToProjectConfiguration(String projectId, String reviewId)
    {
        WorkspaceSpecification workspaceSpec = getReviewWorkspaceSpecification(projectId, reviewId);
        return getProjectConfiguration(projectId, workspaceSpec.getSourceSpecification(), null);
    }

    @Override
    public Revision updateProjectConfiguration(String projectId, WorkspaceSourceSpecification sourceSpecification, String message, ProjectConfigurationUpdater updater)
    {
        LegendSDLCException.validateNonNull(projectId, "projectId may not be null", 400);
        LegendSDLCException.validateNonNull(sourceSpecification, "sourceSpecification may not be null", 400);
        LegendSDLCException.validateNonNull(message, "message may not be null", 400);
        BackendCapability.checkSourceScope(this.backend, sourceSpecification);

        Revision currentRevision = this.fileAccessProvider.getRevisionAccessContext(projectId, sourceSpecification, null).getCurrentRevision();
        if (currentRevision == null)
        {
            throw new LegendSDLCException("Could not find current revision for " + sourceSpecification + " of project " + projectId + ": it may be corrupt");
        }
        return ProjectStructureUpdater.newUpdateBuilder(this.fileAccessProvider, projectId)
                .withProjectConfigurationUpdater(updater)
                .withSourceSpecification(sourceSpecification)
                .withRevisionId(currentRevision.getId())
                .withMessage(message)
                .withProjectStructureExtensionProvider(this.extensionProvider)
                .withProjectStructurePlatformExtensions(this.platformExtensions)
                .update();
    }

    @Override
    public List<ArtifactTypeGenerationConfiguration> getAvailableArtifactGenerations(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
        ProjectConfiguration config = getProjectConfiguration(projectId, sourceSpecification, revisionId);
        return ProjectStructure.getProjectStructure(config).getAvailableGenerationConfigurations();
    }

    @Override
    public ProjectConfigurationStatusReport getProjectConfigurationStatus(String projectId)
    {
        LegendSDLCException.validateNonNull(projectId, "projectId may not be null", 400);
        boolean isProjectConfigured = ProjectStructure.getProjectConfiguration(projectId, SourceSpecification.projectSourceSpecification(), null, this.fileAccessProvider) != null;
        return new ProjectConfigurationStatusReport()
        {
            @Override
            public boolean isProjectConfigured()
            {
                return isProjectConfigured;
            }

            @Override
            public List<String> getReviewIds()
            {
                return Collections.emptyList();
            }
        };
    }

    @Override
    public ProjectStructureVersion getLatestProjectStructureVersion()
    {
        int latestProjectStructureVersion = ProjectStructure.getLatestProjectStructureVersion();
        return ProjectStructureVersion.newProjectStructureVersion(latestProjectStructureVersion, this.extensionProvider.getLatestVersionForProjectStructureVersion(latestProjectStructureVersion));
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
}
