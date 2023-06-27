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

package org.finos.legend.sdlc.server.backend.simple.api.project;

import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactTypeGenerationConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectStructureVersion;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.backend.simple.exception.UnavailableFeature;
import org.finos.legend.sdlc.server.backend.simple.state.SimpleBackendState;
import org.finos.legend.sdlc.server.domain.api.project.ProjectConfigurationApi;
import org.finos.legend.sdlc.server.domain.api.project.ProjectConfigurationUpdater;
import org.finos.legend.sdlc.server.project.ProjectConfigurationStatusReport;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;

public class SimpleBackendProjectConfigurationApi implements ProjectConfigurationApi
{
    private SimpleBackendState simpleBackendState;

    @Inject
    public SimpleBackendProjectConfigurationApi(SimpleBackendState simpleBackendState)
    {
        this.simpleBackendState = simpleBackendState;
    }

    @Override
    public ProjectConfiguration getProjectProjectConfiguration(String projectId)
    {
        return this.simpleBackendState.getProject(projectId).getProjectConfiguration();
    }

    @Override
    public ProjectConfiguration getProjectRevisionProjectConfiguration(String projectId, String revisionId)
    {
        return this.getProjectProjectConfiguration(projectId);
    }

    @Override
    public ProjectConfiguration getWorkspaceProjectConfiguration(String projectId, String workspaceId, WorkspaceType workspaceType)
    {
        return this.getProjectProjectConfiguration(projectId);
    }

    @Override
    public ProjectConfiguration getBackupWorkspaceProjectConfiguration(String projectId, String workspaceId, WorkspaceType workspaceType)
    {
        return this.getProjectProjectConfiguration(projectId);
    }

    @Override
    public ProjectConfiguration getWorkspaceWithConflictResolutionProjectConfiguration(String projectId, String workspaceId, WorkspaceType workspaceType)
    {
        return this.getProjectProjectConfiguration(projectId);
    }

    @Override
    public ProjectConfiguration getWorkspaceRevisionProjectConfiguration(String projectId, String workspaceId, WorkspaceType workspaceType, String revisionId)
    {
        return this.getProjectProjectConfiguration(projectId);
    }

    @Override
    public ProjectConfiguration getBackupWorkspaceRevisionProjectConfiguration(String projectId, String workspaceId, WorkspaceType workspaceType, String revisionId)
    {
        return this.getProjectProjectConfiguration(projectId);
    }

    @Override
    public ProjectConfiguration getWorkspaceWithConflictResolutionRevisionProjectConfiguration(String projectId, String workspaceId, WorkspaceType workspaceType, String revisionId)
    {
        return this.getProjectProjectConfiguration(projectId);
    }

    @Override
    public ProjectConfiguration getVersionProjectConfiguration(String projectId, VersionId versionId)
    {
        return this.getProjectProjectConfiguration(projectId);
    }

    @Override
    public ProjectConfiguration getReviewFromProjectConfiguration(String projectId, String reviewId)
    {
        return this.getProjectProjectConfiguration(projectId);
    }

    @Override
    public ProjectConfiguration getReviewToProjectConfiguration(String projectId, String reviewId)
    {
        return this.getProjectProjectConfiguration(projectId);
    }

    @Override
    public Revision updateProjectConfiguration(String projectId, String workspaceId, WorkspaceType workspaceType, String message, ProjectConfigurationUpdater updater)
    {
        throw UnavailableFeature.exception();
    }

    @Override
    public Revision updateProjectConfigurationForWorkspaceWithConflictResolution(String projectId, String workspaceId, String message, ProjectConfigurationUpdater updater)
    {
        throw UnavailableFeature.exception();
    }

    @Override
    public List<ArtifactTypeGenerationConfiguration> getProjectAvailableArtifactGenerations(String projectId)
    {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<ArtifactTypeGenerationConfiguration> getRevisionAvailableArtifactGenerations(String projectId, String revisionId)
    {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<ArtifactTypeGenerationConfiguration> getWorkspaceRevisionAvailableArtifactGenerations(String projectId, String workspaceId, WorkspaceType workspaceType, String revisionId)
    {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<ArtifactTypeGenerationConfiguration> getWorkspaceAvailableArtifactGenerations(String projectId, String workspaceId, WorkspaceType workspaceType)
    {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<ArtifactTypeGenerationConfiguration> getVersionAvailableArtifactGenerations(String projectId, String versionId)
    {
        return Collections.EMPTY_LIST;
    }

    @Override
    public ProjectStructureVersion getLatestProjectStructureVersion()
    {
        return ProjectStructureVersion.newProjectStructureVersion(13);
    }

    @Override
    public ProjectConfigurationStatusReport getProjectConfigurationStatus(String projectId)
    {
        return new ProjectConfigurationStatusReport()
        {
            @Override
            public boolean isProjectConfigured()
            {
                return true;
            }

            @Override
            public List<String> getReviewIds()
            {
                return Collections.emptyList();
            }
        };
    }
}
