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

package org.finos.legend.sdlc.server.gitlab.api;

import org.eclipse.collections.api.factory.Lists;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactTypeGenerationConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.MetamodelDependency;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectStructureVersion;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.server.domain.api.project.ProjectConfigurationApi;
import org.finos.legend.sdlc.server.domain.api.project.ProjectConfigurationUpdater;
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification;
import org.finos.legend.sdlc.server.domain.api.project.source.WorkspaceSourceSpecification;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSpecification;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.GitLabConfiguration;
import org.finos.legend.sdlc.server.gitlab.GitLabProjectId;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.gitlab.tools.PagerTools;
import org.finos.legend.sdlc.server.project.ProjectConfigurationStatusReport;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.project.ProjectStructure;
import org.finos.legend.sdlc.server.project.ProjectStructurePlatformExtensions;
import org.finos.legend.sdlc.server.project.extension.ProjectStructureExtensionProvider;
import org.finos.legend.sdlc.server.tools.BackgroundTaskProcessor;
import org.gitlab4j.api.models.DiffRef;
import org.gitlab4j.api.models.MergeRequest;
import org.gitlab4j.api.models.MergeRequestFilter;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import javax.inject.Inject;

public class GitLabProjectConfigurationApi extends GitLabApiWithFileAccess implements ProjectConfigurationApi
{
    private final ProjectStructureExtensionProvider projectStructureExtensionProvider;
    private final ProjectStructurePlatformExtensions projectStructurePlatformExtensions;

    @Inject
    public GitLabProjectConfigurationApi(GitLabConfiguration gitLabConfiguration, GitLabUserContext userContext, ProjectStructureExtensionProvider projectStructureExtensionProvider, BackgroundTaskProcessor backgroundTaskProcessor, ProjectStructurePlatformExtensions projectStructurePlatformExtensions)
    {
        super(gitLabConfiguration, userContext, backgroundTaskProcessor);
        this.projectStructureExtensionProvider = projectStructureExtensionProvider;
        this.projectStructurePlatformExtensions = projectStructurePlatformExtensions;
    }

    @Override
    public ProjectConfiguration getProjectConfiguration(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(sourceSpecification, "sourceSpecification may not be null");

        String resolvedRevisionId = resolveRevisionId(projectId, sourceSpecification, revisionId);
        try
        {
            return super.getProjectConfiguration(projectId, sourceSpecification, resolvedRevisionId);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to access project configuration for " + getReferenceInfo(projectId, sourceSpecification, revisionId),
                    () -> "Unknown " + getReferenceInfo(projectId, sourceSpecification, revisionId),
                    () -> "Failed to access project configuration for " + getReferenceInfo(projectId, sourceSpecification, revisionId));
        }
    }

    @Override
    public ProjectConfiguration getProjectConfiguration(String projectId, SourceSpecification sourceSpecification)
    {
        return getProjectConfiguration(projectId, sourceSpecification, null);
    }

    @Override
    public ProjectConfiguration getReviewFromProjectConfiguration(String projectId, String reviewId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        MergeRequest mergeRequest = getReviewMergeRequest(getGitLabApi().getMergeRequestApi(), gitLabProjectId, reviewId);
        validateMergeRequestForComparison(mergeRequest);
        DiffRef diffRef = mergeRequest.getDiffRefs();
        if ((diffRef == null) || (diffRef.getStartSha() == null))
        {
            throw new LegendSDLCServerException("Unable to get [from] revision info in project " + projectId + " for review " + reviewId);
        }

        WorkspaceSpecification workspaceSpec = parseWorkspaceBranchName(mergeRequest.getSourceBranch());
        return super.getProjectConfiguration(projectId, workspaceSpec.getSourceSpecification(), diffRef.getStartSha());
    }

    @Override
    public ProjectConfiguration getReviewToProjectConfiguration(String projectId, String reviewId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        MergeRequest mergeRequest = getReviewMergeRequest(getGitLabApi().getMergeRequestApi(), gitLabProjectId, reviewId);
        validateMergeRequestForComparison(mergeRequest);
        DiffRef diffRef = mergeRequest.getDiffRefs();
        if ((diffRef == null) || (diffRef.getHeadSha() == null))
        {
            throw new LegendSDLCServerException("Unable to get [to] revision info in project " + projectId + " for review " + reviewId);
        }

        WorkspaceSpecification workspaceSpec = parseWorkspaceBranchName(mergeRequest.getSourceBranch());
        return super.getProjectConfiguration(projectId, workspaceSpec.getSourceSpecification(), diffRef.getHeadSha());
    }

    private void validateMergeRequestForComparison(MergeRequest mergeRequest)
    {
        // We only allow review in OPEN and COMMITTED state. Note that this is the only control point for this restriction
        if (!isOpen(mergeRequest) && !isCommitted(mergeRequest))
        {
            throw new LegendSDLCServerException("Current operation not supported for review state " + getReviewState(mergeRequest) + " on review " + mergeRequest.getIid());
        }
    }

    @Override
    public Revision updateProjectConfiguration(String projectId, WorkspaceSourceSpecification sourceSpec, String message, ProjectConfigurationUpdater updater)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(sourceSpec, "sourceSpec may not be null");
        LegendSDLCServerException.validateNonNull(message, "message may not be null");

        try
        {
            ProjectFileAccessProvider fileAccessProvider = getProjectFileAccessProvider();
            Revision currentRevision = fileAccessProvider.getRevisionAccessContext(projectId, sourceSpec, null).getCurrentRevision();
            if (currentRevision == null)
            {
                throw new LegendSDLCServerException("Could not find current revision for " + getReferenceInfo(projectId, sourceSpec) + ": it may be corrupt");
            }
            return ProjectStructure.newUpdateBuilder(fileAccessProvider, projectId)
                    .withProjectConfigurationUpdater(updater)
                    .withSourceSpecification(sourceSpec)
                    .withRevisionId(currentRevision.getId())
                    .withMessage(message)
                    .withProjectStructureExtensionProvider(this.projectStructureExtensionProvider)
                    .withProjectStructurePlatformExtensions(this.projectStructurePlatformExtensions)
                    .update();
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to update project configuration for " + getReferenceInfo(projectId, sourceSpec),
                    () -> "Unknown: " + getReferenceInfo(projectId, sourceSpec),
                    () -> "Failed to update project configuration for " + getReferenceInfo(projectId, sourceSpec));
        }
    }

    @Override
    public List<ArtifactTypeGenerationConfiguration> getAvailableArtifactGenerations(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
        ProjectConfiguration config = getProjectConfiguration(projectId, sourceSpecification, revisionId);
        ProjectStructure structure = ProjectStructure.getProjectStructure(config);
        return structure.getAvailableGenerationConfigurations();
    }

    @Override
    public ProjectStructureVersion getLatestProjectStructureVersion()
    {
        int latestProjectStructureVersion = ProjectStructure.getLatestProjectStructureVersion();
        return ProjectStructureVersion.newProjectStructureVersion(latestProjectStructureVersion, this.projectStructureExtensionProvider.getLatestVersionForProjectStructureVersion(latestProjectStructureVersion));
    }

    @Override
    public List<ArtifactTypeGenerationConfiguration> getLatestAvailableArtifactGenerations()
    {
        ProjectConfiguration projectConfiguration = new ProjectConfiguration()
        {
            @Override
            public String getProjectId()
            {
                return null;
            }

            @Override
            public ProjectStructureVersion getProjectStructureVersion()
            {
                return getLatestProjectStructureVersion();
            }

            @Override
            public String getGroupId()
            {
                return null;
            }

            @Override
            public String getArtifactId()
            {
                return null;
            }

            @Override
            public List<ProjectDependency> getProjectDependencies()
            {
                return Collections.emptyList();
            }

            @Override
            public List<MetamodelDependency> getMetamodelDependencies()
            {
                return Collections.emptyList();
            }
        };
        return ProjectStructure.getProjectStructure(projectConfiguration).getAvailableGenerationConfigurations();
    }

    @Override
    public ProjectConfigurationStatusReport getProjectConfigurationStatus(String projectId)
    {
        boolean isProjectConfigured = hasProjectConfiguration(projectId, SourceSpecification.projectSourceSpecification());
        List<String> reviewIds = Lists.mutable.empty();
        if (!isProjectConfigured)
        {
            try
            {
                GitLabProjectId gitLabProjectId = parseProjectId(projectId);
                MergeRequestFilter mergeRequestFilter =  new MergeRequestFilter();
                mergeRequestFilter.setProjectId(gitLabProjectId.getGitLabId());
                mergeRequestFilter.setTargetBranch(getDefaultBranch(gitLabProjectId));
                Stream<MergeRequest> mergeRequests = PagerTools.stream(withRetries(() -> getGitLabApi().getMergeRequestApi().getMergeRequests(mergeRequestFilter, ITEMS_PER_PAGE)));
                mergeRequests.filter(mr -> mr.getSourceBranch() != null && mr.getSourceBranch().contains(GitLabProjectApi.PROJECT_CONFIGURATION_WORKSPACE_ID_PREFIX)).map(mr -> toStringIfNotNull(mr.getIid())).forEach(reviewIds::add);
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to access the project configuration status of " + projectId,
                        () -> "Unknown project (" + projectId + ")",
                        () -> "Failed to access project configuration status for project " + projectId);
            }
        }
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
                return reviewIds;
            }
        };
    }
}
