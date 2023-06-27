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
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.domain.api.project.ProjectConfigurationApi;
import org.finos.legend.sdlc.server.domain.api.project.ProjectConfigurationUpdater;
import org.finos.legend.sdlc.server.domain.api.project.SourceSpecification;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.GitLabConfiguration;
import org.finos.legend.sdlc.server.gitlab.GitLabProjectId;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.gitlab.tools.PagerTools;
import org.finos.legend.sdlc.server.project.ProjectConfigurationStatusReport;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider.WorkspaceAccessType;
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
import javax.ws.rs.core.Response.Status;

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
    public ProjectConfiguration getProjectProjectConfiguration(String projectId, VersionId patchReleaseVersionId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        try
        {
            return getProjectConfiguration(projectId, SourceSpecification.newSourceSpecification(patchReleaseVersionId), null);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to access project configuration for project " + projectId,
                    () -> "Unknown project (" + projectId + ")",
                    () -> "Failed to access project configuration for project " + projectId);
        }
    }

    @Override
    public ProjectConfiguration getProjectRevisionProjectConfiguration(String projectId, VersionId patchReleaseVersionId, String revisionId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(revisionId, "revisionId may not be null");
        String resolvedRevisionId;
        try
        {
            resolvedRevisionId = resolveRevisionId(revisionId, getProjectFileAccessProvider().getRevisionAccessContext(projectId, SourceSpecification.newSourceSpecification(patchReleaseVersionId), null));
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to get revision " + revisionId + " of project " + projectId,
                    () -> "Unknown revision " + revisionId + " of project " + projectId,
                    () -> "Failed to get revision " + revisionId + " of project " + projectId);
        }
        if (resolvedRevisionId == null)
        {
            throw new LegendSDLCServerException("Failed to resolve revision " + revisionId + " of project " + projectId, Status.NOT_FOUND);
        }
        try
        {
            return getProjectConfiguration(projectId, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, WorkspaceAccessType.WORKSPACE, patchReleaseVersionId), resolvedRevisionId);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to access project configuration for project " + projectId + " at revision " + revisionId,
                    () -> "Unknown project (" + projectId + ") or revision (" + revisionId + ")",
                    () -> "Failed to access project configuration for project " + projectId + " at revision " + revisionId);
        }
    }

    @Override
    public ProjectConfiguration getWorkspaceProjectConfiguration(String projectId, SourceSpecification sourceSpecification)
    {
        return this.getWorkspaceProjectConfigurationByAccessType(projectId, SourceSpecification.newSourceSpecification(sourceSpecification.getWorkspaceId(), sourceSpecification.getWorkspaceType(), WorkspaceAccessType.WORKSPACE, sourceSpecification.getPatchReleaseVersionId()));
    }

    @Override
    public ProjectConfiguration getBackupWorkspaceProjectConfiguration(String projectId, SourceSpecification sourceSpecification)
    {
        return this.getWorkspaceProjectConfigurationByAccessType(projectId, SourceSpecification.newSourceSpecification(sourceSpecification.getWorkspaceId(), sourceSpecification.getWorkspaceType(), WorkspaceAccessType.BACKUP, sourceSpecification.getPatchReleaseVersionId()));
    }

    @Override
    public ProjectConfiguration getWorkspaceWithConflictResolutionProjectConfiguration(String projectId, SourceSpecification sourceSpecification)
    {
        return this.getWorkspaceProjectConfigurationByAccessType(projectId, SourceSpecification.newSourceSpecification(sourceSpecification.getWorkspaceId(), sourceSpecification.getWorkspaceType(), WorkspaceAccessType.CONFLICT_RESOLUTION, sourceSpecification.getPatchReleaseVersionId()));
    }

    private ProjectConfiguration getWorkspaceProjectConfigurationByAccessType(String projectId, SourceSpecification sourceSpecification)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(sourceSpecification.getWorkspaceId(), "workspaceId may not be null");
        LegendSDLCServerException.validateNonNull(sourceSpecification.getWorkspaceType(), "workspaceType may not be null");
        LegendSDLCServerException.validateNonNull(sourceSpecification.getWorkspaceAccessType(), "workspaceAccessType may not be null");
        try
        {
            return getProjectConfiguration(projectId, sourceSpecification, null);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to access project configuration for project " + projectId + " in " + sourceSpecification.getWorkspaceType().getLabel() + " " + sourceSpecification.getWorkspaceAccessType().getLabel() + " " + sourceSpecification.getWorkspaceId(),
                    () -> "Unknown " + sourceSpecification.getWorkspaceType().getLabel() + " " + sourceSpecification.getWorkspaceAccessType().getLabel() + " (" + sourceSpecification.getWorkspaceId() + ") or project (" + projectId + ")",
                    () -> "Failed to access project configuration for project " + projectId + " in " + sourceSpecification.getWorkspaceType().getLabel() + " " + sourceSpecification.getWorkspaceAccessType().getLabel() + " " + sourceSpecification.getWorkspaceId());
        }
    }

    @Override
    public ProjectConfiguration getWorkspaceRevisionProjectConfiguration(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
        return this.getWorkspaceRevisionProjectConfigurationByWorkspaceAccessType(projectId, SourceSpecification.newSourceSpecification(sourceSpecification.getWorkspaceId(), sourceSpecification.getWorkspaceType(), WorkspaceAccessType.WORKSPACE, sourceSpecification.getPatchReleaseVersionId()), revisionId);
    }

    @Override
    public ProjectConfiguration getBackupUserWorkspaceRevisionProjectConfiguration(String projectId, String workspaceId, String revisionId)
    {
        return this.getBackupWorkspaceRevisionProjectConfiguration(projectId, SourceSpecification.newUserWorkspaceSourceSpecification(workspaceId), revisionId);
    }

    @Override
    public ProjectConfiguration getBackupGroupWorkspaceRevisionProjectConfiguration(String projectId, String workspaceId, String revisionId)
    {
        return this.getBackupWorkspaceRevisionProjectConfiguration(projectId, SourceSpecification.newGroupWorkspaceSourceSpecification(workspaceId), revisionId);
    }

    @Override
    public ProjectConfiguration getBackupWorkspaceRevisionProjectConfiguration(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
        return this.getWorkspaceRevisionProjectConfigurationByWorkspaceAccessType(projectId, SourceSpecification.newSourceSpecification(sourceSpecification.getWorkspaceId(), sourceSpecification.getWorkspaceType(), WorkspaceAccessType.BACKUP, sourceSpecification.getPatchReleaseVersionId()), revisionId);
    }

    @Override
    public ProjectConfiguration getWorkspaceWithConflictResolutionRevisionProjectConfiguration(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
        return this.getWorkspaceRevisionProjectConfigurationByWorkspaceAccessType(projectId, SourceSpecification.newSourceSpecification(sourceSpecification.getWorkspaceId(), sourceSpecification.getWorkspaceType(), WorkspaceAccessType.CONFLICT_RESOLUTION, sourceSpecification.getPatchReleaseVersionId()), revisionId);
    }

    private ProjectConfiguration getWorkspaceRevisionProjectConfigurationByWorkspaceAccessType(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(sourceSpecification.getWorkspaceId(), "workspaceId may not be null");
        LegendSDLCServerException.validateNonNull(sourceSpecification.getWorkspaceType(), "workspaceType may not be null");
        LegendSDLCServerException.validateNonNull(sourceSpecification.getWorkspaceAccessType(), "workspaceAccessType may not be null");
        LegendSDLCServerException.validateNonNull(revisionId, "revisionId may not be null");
        String resolvedRevisionId;
        try
        {
            resolvedRevisionId = resolveRevisionId(revisionId, getProjectFileAccessProvider().getRevisionAccessContext(projectId, sourceSpecification, null));
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to get revision " + revisionId + " in " + sourceSpecification.getWorkspaceType().getLabel() + " " + sourceSpecification.getWorkspaceAccessType().getLabel() + " " + sourceSpecification.getWorkspaceId() + " of project " + projectId,
                    () -> "Unknown revision " + revisionId + " in " + sourceSpecification.getWorkspaceType().getLabel() + " " + sourceSpecification.getWorkspaceAccessType().getLabel() + " " + sourceSpecification.getWorkspaceId() + " of project " + projectId,
                    () -> "Failed to get revision " + revisionId + " in " + sourceSpecification.getWorkspaceType().getLabel() + " " + sourceSpecification.getWorkspaceAccessType().getLabel() + " " + sourceSpecification.getWorkspaceId() + " of project " + projectId);
        }
        if (resolvedRevisionId == null)
        {
            throw new LegendSDLCServerException("Failed to resolve revision " + revisionId + " in " + sourceSpecification.getWorkspaceType().getLabel() + " " + sourceSpecification.getWorkspaceAccessType().getLabel() + " " + sourceSpecification.getWorkspaceId() + " of project " + projectId, Status.NOT_FOUND);
        }
        try
        {
            return getProjectConfiguration(projectId, sourceSpecification, resolvedRevisionId);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to access project configuration for project " + projectId + " in " + sourceSpecification.getWorkspaceType().getLabel() + " " + sourceSpecification.getWorkspaceAccessType().getLabel() + " " + sourceSpecification.getWorkspaceId() + " at revision " + revisionId,
                    () -> "Unknown project (" + projectId + "), " + sourceSpecification.getWorkspaceType().getLabel() + " " + sourceSpecification.getWorkspaceAccessType().getLabel() + " (" + sourceSpecification.getWorkspaceId() + "), or revision (" + revisionId + ")",
                    () -> "Failed to access project configuration for project " + projectId + " in " + sourceSpecification.getWorkspaceType().getLabel() + " " + sourceSpecification.getWorkspaceAccessType().getLabel() + " " + sourceSpecification.getWorkspaceId() + " at revision " + revisionId);
        }
    }

    @Override
    public ProjectConfiguration getVersionProjectConfiguration(String projectId, VersionId versionId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(versionId, "versionId may not be null");
        try
        {
            return getVersionProjectConfiguration(projectId, versionId);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to access project configuration for version " + versionId.toVersionIdString() + " of project " + projectId,
                    () -> "Unknown project (" + projectId + ") or version (" + versionId.toVersionIdString() + ")",
                    () -> "Failed to access project configuration for version " + versionId.toVersionIdString() + " of project " + projectId);
        }
    }

    @Override
    public ProjectConfiguration getReviewFromProjectConfiguration(String projectId, VersionId patchReleaseVersionId, String reviewId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        MergeRequest mergeRequest = getReviewMergeRequest(getGitLabApi().getMergeRequestApi(), gitLabProjectId, patchReleaseVersionId, reviewId);
        validateMergeRequestForComparison(mergeRequest);
        DiffRef diffRef = mergeRequest.getDiffRefs();

        if (diffRef != null && diffRef.getStartSha() != null && diffRef.getHeadSha() != null)
        {
            String revisionId = diffRef.getStartSha();
            return getProjectRevisionProjectConfiguration(projectId, patchReleaseVersionId, revisionId);
        }
        else
        {
            throw new LegendSDLCServerException("Unable to get [from] revision info in project " + projectId + " for review " + reviewId);
        }
    }

    @Override
    public ProjectConfiguration getReviewToProjectConfiguration(String projectId, VersionId patchReleaseVersionId, String reviewId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        MergeRequest mergeRequest = getReviewMergeRequest(getGitLabApi().getMergeRequestApi(), gitLabProjectId, patchReleaseVersionId, reviewId);
        validateMergeRequestForComparison(mergeRequest);
        DiffRef diffRef = mergeRequest.getDiffRefs();

        if (diffRef != null && diffRef.getStartSha() != null && diffRef.getHeadSha() != null)
        {
            String revisionId = diffRef.getHeadSha();
            return getProjectRevisionProjectConfiguration(projectId, patchReleaseVersionId, revisionId);
        }
        else
        {
            throw new LegendSDLCServerException("Unable to get [to] revision info in project " + projectId + " for review " + reviewId);
        }
    }

    @Override
    public Revision updateProjectConfiguration(String projectId, SourceSpecification sourceSpecification, String message, ProjectConfigurationUpdater updater)
    {
        return updateProjectConfigurationByWorkspaceAccessType(projectId, SourceSpecification.newSourceSpecification(sourceSpecification.getWorkspaceId(), sourceSpecification.getWorkspaceType(), WorkspaceAccessType.WORKSPACE, sourceSpecification.getPatchReleaseVersionId()), message, updater);
    }

    @Override
    public Revision updateProjectConfigurationForWorkspaceWithConflictResolution(String projectId, String workspaceId, String message, ProjectConfigurationUpdater updater)
    {
        return updateProjectConfigurationByWorkspaceAccessType(projectId, SourceSpecification.newSourceSpecification(workspaceId, WorkspaceType.USER, WorkspaceAccessType.CONFLICT_RESOLUTION), message, updater);
    }

    private Revision updateProjectConfigurationByWorkspaceAccessType(String projectId, SourceSpecification sourceSpecification, String message, ProjectConfigurationUpdater updater)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(sourceSpecification.getWorkspaceId(), "workspaceId may not be null");
        LegendSDLCServerException.validateNonNull(sourceSpecification.getWorkspaceType(), "workspaceType may not be null");
        LegendSDLCServerException.validateNonNull(sourceSpecification.getWorkspaceAccessType(), "workspaceAccessType may not be null");
        LegendSDLCServerException.validateNonNull(message, "message may not be null");

        try
        {
            ProjectFileAccessProvider fileAccessProvider = getProjectFileAccessProvider();
            Revision currentRevision = fileAccessProvider.getRevisionAccessContext(projectId, sourceSpecification, null).getCurrentRevision();
            if (currentRevision == null)
            {
                throw new LegendSDLCServerException("Could not find current revision for " + sourceSpecification.getWorkspaceType().getLabel() + " " + sourceSpecification.getWorkspaceAccessType().getLabel() + " " + sourceSpecification.getWorkspaceId() + " in project " + projectId + ": " + sourceSpecification.getWorkspaceType().getLabel() + " " + sourceSpecification.getWorkspaceAccessType().getLabel() + " may be corrupt");
            }
            return ProjectStructure.newUpdateBuilder(fileAccessProvider, projectId)
                    .withProjectConfigurationUpdater(updater)
                    .withWorkspace(sourceSpecification)
                    .withRevisionId(currentRevision.getId())
                    .withMessage(message)
                    .withProjectStructureExtensionProvider(this.projectStructureExtensionProvider)
                    .withProjectStructurePlatformExtensions(this.projectStructurePlatformExtensions)
                    .update();
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to update project configuration for project " + projectId + " in " + sourceSpecification.getWorkspaceType().getLabel() + " " + sourceSpecification.getWorkspaceAccessType().getLabel() + " " + sourceSpecification.getWorkspaceId(),
                    () -> "Unknown project (" + projectId + ") or " + sourceSpecification.getWorkspaceType().getLabel() + " " + sourceSpecification.getWorkspaceAccessType().getLabel() + " (" + sourceSpecification.getWorkspaceId() + ")",
                    () -> "Failed to update project configuration for project " + projectId + " in " + sourceSpecification.getWorkspaceType().getLabel() + " " + sourceSpecification.getWorkspaceAccessType().getLabel() + " " + sourceSpecification.getWorkspaceId());
        }
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
    public List<ArtifactTypeGenerationConfiguration> getProjectAvailableArtifactGenerations(String projectId, VersionId patchReleaseVersionId)
    {
        return ProjectStructure.getProjectStructure(getProjectProjectConfiguration(projectId, patchReleaseVersionId)).getAvailableGenerationConfigurations();
    }

    @Override
    public List<ArtifactTypeGenerationConfiguration> getRevisionAvailableArtifactGenerations(String projectId, String revisionId)
    {
        return ProjectStructure.getProjectStructure(getProjectRevisionProjectConfiguration(projectId, revisionId)).getAvailableGenerationConfigurations();
    }

    @Override
    public List<ArtifactTypeGenerationConfiguration> getWorkspaceRevisionAvailableArtifactGenerations(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
        return ProjectStructure.getProjectStructure(getWorkspaceRevisionProjectConfiguration(projectId, sourceSpecification, revisionId)).getAvailableGenerationConfigurations();
    }

    @Override
    public List<ArtifactTypeGenerationConfiguration> getVersionAvailableArtifactGenerations(String projectId, String versionId)
    {
        return ProjectStructure.getProjectStructure(getVersionProjectConfiguration(projectId, versionId)).getAvailableGenerationConfigurations();
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
        Boolean isProjectConfigured = getProjectConfiguration(projectId, null) != null;
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

    @Override
    public List<ArtifactTypeGenerationConfiguration> getWorkspaceAvailableArtifactGenerations(String projectId, SourceSpecification sourceSpecification)
    {
        return ProjectStructure.getProjectStructure(getWorkspaceProjectConfiguration(projectId, sourceSpecification)).getAvailableGenerationConfigurations();
    }
}
