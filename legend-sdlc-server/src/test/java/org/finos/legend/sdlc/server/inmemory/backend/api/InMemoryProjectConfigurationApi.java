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

package org.finos.legend.sdlc.server.inmemory.backend.api;

import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactGeneration;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactTypeGenerationConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectStructureVersion;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.server.domain.api.project.ProjectConfigurationApi;
import org.finos.legend.sdlc.server.inmemory.backend.InMemoryBackend;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryProject;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryRevision;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryVersion;

import javax.inject.Inject;
import java.util.List;

public class InMemoryProjectConfigurationApi implements ProjectConfigurationApi
{
    private final InMemoryBackend backend;

    @Inject
    public InMemoryProjectConfigurationApi(InMemoryBackend backend)
    {
        this.backend = backend;
    }

    @Override
    public ProjectConfiguration getProjectProjectConfiguration(String projectId)
    {
        InMemoryProject project = this.backend.getProject(projectId);
        return project.getCurrentRevision().getConfiguration();
    }

    @Override
    public ProjectConfiguration getProjectRevisionProjectConfiguration(String projectId, String revisionId)
    {
        InMemoryProject project = this.backend.getProject(projectId);
        InMemoryRevision revision = project.getRevision(revisionId);
        return revision.getConfiguration();
    }

    @Override
    public ProjectConfiguration getWorkspaceProjectConfiguration(String projectId, String workspaceId, WorkspaceType workspaceType)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public ProjectConfiguration getBackupWorkspaceProjectConfiguration(String projectId, String workspaceId, WorkspaceType workspaceType)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public ProjectConfiguration getWorkspaceWithConflictResolutionProjectConfiguration(String projectId, String workspaceId, WorkspaceType workspaceType)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public ProjectConfiguration getWorkspaceRevisionProjectConfiguration(String projectId, String workspaceId, WorkspaceType workspaceType, String revisionId)
    {
        InMemoryRevision revision = workspaceType == WorkspaceType.GROUP ? backend.getProject(projectId).getGroupWorkspace(workspaceId).getRevision(revisionId) : backend.getProject(projectId).getUserWorkspace(workspaceId).getRevision(revisionId);
        return revision.getConfiguration();
    }

    @Override
    public ProjectConfiguration getBackupUserWorkspaceRevisionProjectConfiguration(String projectId, String workspaceId, String revisionId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public ProjectConfiguration getBackupGroupWorkspaceRevisionProjectConfiguration(String projectId, String workspaceId, String revisionId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public ProjectConfiguration getBackupWorkspaceRevisionProjectConfiguration(String projectId, String workspaceId, WorkspaceType workspaceType, String revisionId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public ProjectConfiguration getWorkspaceWithConflictResolutionRevisionProjectConfiguration(String projectId, String workspaceId, WorkspaceType workspaceType, String revisionId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public ProjectConfiguration getVersionProjectConfiguration(String projectId, VersionId versionId)
    {
        InMemoryVersion version = backend.getProject(projectId).getVersion(versionId.toVersionIdString());
        return version.getConfiguration();
    }

    @Override
    public ProjectConfiguration getReviewFromProjectConfiguration(String projectId, String reviewId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public ProjectConfiguration getReviewToProjectConfiguration(String projectId, String reviewId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Revision updateProjectConfiguration(String projectId, String workspaceId, WorkspaceType workspaceType, String message, Integer projectStructureVersion, Integer projectStructureExtensionVersion, String groupId, String artifactId, Iterable<? extends ProjectDependency> projectDependenciesToAdd, Iterable<? extends ProjectDependency> projectDependenciesToRemove, List<? extends ArtifactGeneration> artifactGenerationsToAdd, List<String> artifactGenerationsToRemove)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Revision updateProjectConfigurationForWorkspaceWithConflictResolution(String projectId, String workspaceId, String message, Integer projectStructureVersion, Integer projectStructureExtensionVersion, String groupId, String artifactId, Iterable<? extends ProjectDependency> projectDependenciesToAdd, Iterable<? extends ProjectDependency> projectDependenciesToRemove, List<? extends ArtifactGeneration> artifactGenerationsToAdd, List<String> artifactGenerationsToRemove)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public List<ArtifactTypeGenerationConfiguration> getProjectAvailableArtifactGenerations(String projectId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public List<ArtifactTypeGenerationConfiguration> getRevisionAvailableArtifactGenerations(String projectId, String revisionId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public List<ArtifactTypeGenerationConfiguration> getWorkspaceRevisionAvailableArtifactGenerations(String projectId, String workspaceId, WorkspaceType workspaceType, String revisionId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public List<ArtifactTypeGenerationConfiguration> getWorkspaceAvailableArtifactGenerations(String projectId, String workspaceId, WorkspaceType workspaceType)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public List<ArtifactTypeGenerationConfiguration> getVersionAvailableArtifactGenerations(String projectId, String versionId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public ProjectStructureVersion getLatestProjectStructureVersion()
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public List<ArtifactTypeGenerationConfiguration> getLatestAvailableArtifactGenerations()
    {
        throw new UnsupportedOperationException("Not implemented");
    }
}
