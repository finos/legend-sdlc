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

import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactGeneration;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactTypeGenerationConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.MetamodelDependency;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectStructureVersion;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.domain.api.project.ProjectConfigurationApi;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.GitLabProjectId;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.project.ProjectConfigurationUpdateBuilder;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.project.ProjectStructure;
import org.finos.legend.sdlc.server.project.config.ProjectStructureConfiguration;
import org.finos.legend.sdlc.server.project.extension.ProjectStructureExtensionProvider;
import org.finos.legend.sdlc.server.tools.BackgroundTaskProcessor;
import org.gitlab4j.api.models.DiffRef;
import org.gitlab4j.api.models.MergeRequest;

import javax.inject.Inject;
import javax.ws.rs.core.Response.Status;
import java.util.Collections;
import java.util.List;

public class GitLabProjectConfigurationApi extends GitLabApiWithFileAccess implements ProjectConfigurationApi
{
    private final ProjectStructureConfiguration projectStructureConfig;
    private final ProjectStructureExtensionProvider projectStructureExtensionProvider;

    @Inject
    public GitLabProjectConfigurationApi(GitLabUserContext userContext, ProjectStructureConfiguration projectStructureConfig, ProjectStructureExtensionProvider projectStructureExtensionProvider, BackgroundTaskProcessor backgroundTaskProcessor)
    {
        super(userContext, backgroundTaskProcessor);
        this.projectStructureConfig = projectStructureConfig;
        this.projectStructureExtensionProvider = projectStructureExtensionProvider;
    }

    @Override
    public ProjectConfiguration getProjectProjectConfiguration(String projectId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        try
        {
            return getProjectConfiguration(projectId, null, null, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE);
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
    public ProjectConfiguration getProjectRevisionProjectConfiguration(String projectId, String revisionId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(revisionId, "revisionId may not be null");
        String resolvedRevisionId;
        try
        {
            resolvedRevisionId = resolveRevisionId(revisionId, getProjectFileAccessProvider().getRevisionAccessContext(projectId, null, null, null));
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to get revision " + revisionId + " of project " + projectId,
                    () -> "Unknown revision " + revisionId + " of project " + projectId,
                    () -> "Failed to get revision " + revisionId + " of project " + projectId
            );
        }
        if (resolvedRevisionId == null)
        {
            throw new LegendSDLCServerException("Failed to resolve revision " + revisionId + " of project " + projectId, Status.NOT_FOUND);
        }
        try
        {
            return getProjectConfiguration(projectId, null, resolvedRevisionId, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE);
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
    public ProjectConfiguration getWorkspaceProjectConfiguration(String projectId, String workspaceId)
    {
        return this.getWorkspaceProjectConfigurationByAccessType(projectId, workspaceId, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE);
    }

    @Override
    public ProjectConfiguration getBackupWorkspaceProjectConfiguration(String projectId, String workspaceId)
    {
        return this.getWorkspaceProjectConfigurationByAccessType(projectId, workspaceId, ProjectFileAccessProvider.WorkspaceAccessType.BACKUP);
    }

    @Override
    public ProjectConfiguration getWorkspaceWithConflictResolutionProjectConfiguration(String projectId, String workspaceId)
    {
        return this.getWorkspaceProjectConfigurationByAccessType(projectId, workspaceId, ProjectFileAccessProvider.WorkspaceAccessType.CONFLICT_RESOLUTION);
    }

    private ProjectConfiguration getWorkspaceProjectConfigurationByAccessType(String projectId, String workspaceId, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceId, "workspaceId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceAccessType, "workspaceAccessType may not be null");
        try
        {
            return getProjectConfiguration(projectId, workspaceId, null, workspaceAccessType);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to access project configuration for project " + projectId + " in " + workspaceAccessType.getLabel() + " " + workspaceId,
                    () -> "Unknown " + workspaceAccessType.getLabel() + " (" + workspaceId + ") or project (" + projectId + ")",
                    () -> "Failed to access project configuration for project " + projectId + " in " + workspaceAccessType.getLabel() + " " + workspaceId);
        }
    }

    @Override
    public ProjectConfiguration getWorkspaceRevisionProjectConfiguration(String projectId, String workspaceId, String revisionId)
    {
        return this.getWorkspaceRevisionProjectConfigurationByWorkspaceAccessType(projectId, workspaceId, revisionId, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE);
    }

    @Override
    public ProjectConfiguration getBackupWorkspaceRevisionProjectConfiguration(String projectId, String workspaceId, String revisionId)
    {
        return this.getWorkspaceRevisionProjectConfigurationByWorkspaceAccessType(projectId, workspaceId, revisionId, ProjectFileAccessProvider.WorkspaceAccessType.BACKUP);
    }

    @Override
    public ProjectConfiguration getWorkspaceWithConflictResolutionRevisionProjectConfiguration(String projectId, String workspaceId, String revisionId)
    {
        return this.getWorkspaceRevisionProjectConfigurationByWorkspaceAccessType(projectId, workspaceId, revisionId, ProjectFileAccessProvider.WorkspaceAccessType.CONFLICT_RESOLUTION);
    }

    private ProjectConfiguration getWorkspaceRevisionProjectConfigurationByWorkspaceAccessType(String projectId, String workspaceId, String revisionId, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceId, "workspaceId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceAccessType, "workspaceAccessType may not be null");
        LegendSDLCServerException.validateNonNull(revisionId, "revisionId may not be null");
        String resolvedRevisionId;
        try
        {
            resolvedRevisionId = resolveRevisionId(revisionId, getProjectFileAccessProvider().getRevisionAccessContext(projectId, workspaceId, null, workspaceAccessType));
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to get revision " + revisionId + " in " + workspaceAccessType.getLabel() + " " + workspaceId + " of project " + projectId,
                    () -> "Unknown revision " + revisionId + " in " + workspaceAccessType.getLabel() + " " + workspaceId + " of project " + projectId,
                    () -> "Failed to get revision " + revisionId + " in " + workspaceAccessType.getLabel() + " " + workspaceId + " of project " + projectId
            );
        }
        if (resolvedRevisionId == null)
        {
            throw new LegendSDLCServerException("Failed to resolve revision " + revisionId + " in " + workspaceAccessType.getLabel() + " " + workspaceId + " of project " + projectId, Status.NOT_FOUND);
        }
        try
        {
            return getProjectConfiguration(projectId, workspaceId, resolvedRevisionId, workspaceAccessType);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to access project configuration for project " + projectId + " in " + workspaceAccessType.getLabel() + " " + workspaceId + " at revision " + revisionId,
                    () -> "Unknown project (" + projectId + "), " + workspaceAccessType.getLabel() + " (" + workspaceId + "), or revision (" + revisionId + ")",
                    () -> "Failed to access project configuration for project " + projectId + " in " + workspaceAccessType.getLabel() + " " + workspaceId + " at revision " + revisionId);
        }
    }

    @Override
    public ProjectConfiguration getVersionProjectConfiguration(String projectId, VersionId versionId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(versionId, "versionId may not be null");
        try
        {
            return getProjectConfiguration(projectId, versionId);
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
    public ProjectConfiguration getReviewFromProjectConfiguration(String projectId, String reviewId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        MergeRequest mergeRequest = getReviewMergeRequest(getGitLabApi(gitLabProjectId.getGitLabMode()).getMergeRequestApi(), gitLabProjectId, reviewId);
        validateMergeRequestForComparison(mergeRequest);
        DiffRef diffRef = mergeRequest.getDiffRefs();

        if (diffRef != null && diffRef.getStartSha() != null && diffRef.getHeadSha() != null)
        {
            String revisionId = diffRef.getStartSha();
            return getProjectRevisionProjectConfiguration(projectId, revisionId);
        }
        else
        {
            throw new LegendSDLCServerException("Unable to get [from] revision info in project " + projectId + " for review " + reviewId);
        }
    }

    @Override
    public ProjectConfiguration getReviewToProjectConfiguration(String projectId, String reviewId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        MergeRequest mergeRequest = getReviewMergeRequest(getGitLabApi(gitLabProjectId.getGitLabMode()).getMergeRequestApi(), gitLabProjectId, reviewId);
        validateMergeRequestForComparison(mergeRequest);
        DiffRef diffRef = mergeRequest.getDiffRefs();

        if (diffRef != null && diffRef.getStartSha() != null && diffRef.getHeadSha() != null)
        {
            String revisionId = diffRef.getHeadSha();
            return getProjectRevisionProjectConfiguration(projectId, revisionId);
        }
        else
        {
            throw new LegendSDLCServerException("Unable to get [to] revision info in project " + projectId + " for review " + reviewId);
        }
    }


    @Override
    public Revision updateProjectConfiguration(String projectId, String workspaceId, String message, Integer projectStructureVersion, Integer projectStructureExtensionVersion, String groupId, String artifactId, Iterable<? extends ProjectDependency> projectDependenciesToAdd, Iterable<? extends ProjectDependency> projectDependenciesToRemove, List<? extends ArtifactGeneration> artifactGenerationsToAdd, List<String> artifactGenerationsToRemove)
    {
        return this.updateProjectConfigurationByWorkspaceAccessType(projectId, workspaceId, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE, message, projectStructureVersion, projectStructureExtensionVersion, groupId, artifactId, projectDependenciesToAdd, projectDependenciesToRemove, artifactGenerationsToAdd, artifactGenerationsToRemove);
    }

    @Override
    public Revision updateProjectConfigurationForWorkspaceWithConflictResolution(String projectId, String workspaceId, String message, Integer projectStructureVersion, Integer projectStructureExtensionVersion, String groupId, String artifactId, Iterable<? extends ProjectDependency> projectDependenciesToAdd, Iterable<? extends ProjectDependency> projectDependenciesToRemove, List<? extends ArtifactGeneration> artifactGenerationsToAdd, List<String> artifactGenerationsToRemove)
    {
        return this.updateProjectConfigurationByWorkspaceAccessType(projectId, workspaceId, ProjectFileAccessProvider.WorkspaceAccessType.CONFLICT_RESOLUTION, message, projectStructureVersion, projectStructureExtensionVersion, groupId, artifactId, projectDependenciesToAdd, projectDependenciesToRemove, artifactGenerationsToAdd, artifactGenerationsToRemove);
    }

    private Revision updateProjectConfigurationByWorkspaceAccessType(String projectId, String workspaceId, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType, String message, Integer projectStructureVersion, Integer projectStructureExtensionVersion, String groupId, String artifactId, Iterable<? extends ProjectDependency> projectDependenciesToAdd, Iterable<? extends ProjectDependency> projectDependenciesToRemove, List<? extends ArtifactGeneration> artifactGenerationsToAdd, List<String> artifactGenerationsToRemove)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceId, "workspaceId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceAccessType, "workspaceAccessType may not be null");
        LegendSDLCServerException.validateNonNull(message, "message may not be null");

        if ((groupId != null) && !ProjectStructure.isValidGroupId(groupId))
        {
            throw new LegendSDLCServerException("Invalid groupId: " + groupId, Status.BAD_REQUEST);
        }
        if ((artifactId != null) && !ProjectStructure.isValidArtifactId(artifactId))
        {
            throw new LegendSDLCServerException("Invalid artifactId: " + artifactId, Status.BAD_REQUEST);
        }
        if ((projectStructureVersion != null) && (this.projectStructureConfig != null) && this.projectStructureConfig.getDemisedVersions().contains(projectStructureVersion))
        {
            throw new LegendSDLCServerException("Project structure version " + projectStructureVersion + " is demised", Status.BAD_REQUEST);
        }

        try
        {
            ProjectFileAccessProvider fileAccessProvider = getProjectFileAccessProvider();
            Revision currentRevision = fileAccessProvider.getRevisionAccessContext(projectId, workspaceId, null, workspaceAccessType).getCurrentRevision();

            if (currentRevision == null)
            {
                throw new LegendSDLCServerException("Could not find current revision for " + workspaceAccessType.getLabel() + " " + workspaceId + " in project " + projectId + ": " + workspaceAccessType.getLabel() + " may be corrupt");
            }
            return ProjectConfigurationUpdateBuilder.newBuilder(fileAccessProvider, getProjectTypeFromMode(GitLabProjectId.getGitLabMode(projectId)), projectId)
                    .withWorkspace(workspaceId, workspaceAccessType)
                    .withRevisionId(currentRevision.getId())
                    .withMessage(message)
                    .withProjectStructureVersion(projectStructureVersion)
                    .withProjectStructureExtensionVersion(projectStructureExtensionVersion)
                    .withGroupId(groupId)
                    .withArtifactId(artifactId)
                    .withProjectDependenciesToAdd(projectDependenciesToAdd)
                    .withProjectDependenciesToRemove(projectDependenciesToRemove)
                    .withArtifactGenerationsToAdd(artifactGenerationsToAdd)
                    .withArtifactGenerationsToRemove(artifactGenerationsToRemove)
                    .withProjectStructureExtensionProvider(this.projectStructureExtensionProvider)
                    .updateProjectConfiguration();
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to update project configuration for project " + projectId + " in " + workspaceAccessType.getLabel() + " " + workspaceId,
                    () -> "Unknown project (" + projectId + ") or " + workspaceAccessType.getLabel() + " (" + workspaceId + ")",
                    () -> "Failed to update project configuration for project " + projectId + " in " + workspaceAccessType.getLabel() + " " + workspaceId);
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
    public List<ArtifactTypeGenerationConfiguration> getProjectAvailableArtifactGenerations(String projectId)
    {
        return ProjectStructure.getProjectStructure(getProjectProjectConfiguration(projectId)).getAvailableGenerationConfigurations();
    }

    @Override
    public List<ArtifactTypeGenerationConfiguration> getRevisionAvailableArtifactGenerations(String projectId, String revisionId)
    {
        return ProjectStructure.getProjectStructure(getProjectRevisionProjectConfiguration(projectId, revisionId)).getAvailableGenerationConfigurations();
    }

    @Override
    public List<ArtifactTypeGenerationConfiguration> getWorkspaceRevisionAvailableArtifactGenerations(String projectId, String workspaceId, String revisionId)
    {
        return ProjectStructure.getProjectStructure(getWorkspaceRevisionProjectConfiguration(projectId, workspaceId, revisionId)).getAvailableGenerationConfigurations();
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
            public ProjectType getProjectType()
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

            @Override
            public List<ArtifactGeneration> getArtifactGenerations()
            {
                return Collections.emptyList();
            }
        };
        return ProjectStructure.getProjectStructure(projectConfiguration).getAvailableGenerationConfigurations();
    }

    @Override
    public List<ArtifactTypeGenerationConfiguration> getWorkspaceAvailableArtifactGenerations(String projectId, String workspaceId)
    {
        return ProjectStructure.getProjectStructure(getWorkspaceProjectConfiguration(projectId, workspaceId)).getAvailableGenerationConfigurations();
    }
}