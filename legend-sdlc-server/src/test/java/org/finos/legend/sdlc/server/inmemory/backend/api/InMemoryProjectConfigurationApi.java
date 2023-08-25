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

import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactTypeGenerationConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectStructureVersion;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.domain.api.project.ProjectConfigurationApi;
import org.finos.legend.sdlc.server.domain.api.project.ProjectConfigurationUpdater;
import org.finos.legend.sdlc.server.domain.api.project.source.PatchSourceSpecification;
import org.finos.legend.sdlc.server.domain.api.project.source.ProjectSourceSpecification;
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification;
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecificationVisitor;
import org.finos.legend.sdlc.server.domain.api.project.source.VersionSourceSpecification;
import org.finos.legend.sdlc.server.domain.api.project.source.WorkspaceSourceSpecification;
import org.finos.legend.sdlc.server.domain.api.workspace.PatchWorkspaceSource;
import org.finos.legend.sdlc.server.domain.api.workspace.ProjectWorkspaceSource;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSourceVisitor;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSpecification;
import org.finos.legend.sdlc.server.inmemory.backend.InMemoryBackend;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryPatch;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryProject;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryRevision;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryWorkspace;
import org.finos.legend.sdlc.server.project.ProjectConfigurationStatusReport;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;

import java.util.List;
import javax.inject.Inject;

public class InMemoryProjectConfigurationApi implements ProjectConfigurationApi
{
    private final InMemoryBackend backend;

    @Inject
    public InMemoryProjectConfigurationApi(InMemoryBackend backend)
    {
        this.backend = backend;
    }

    @Override
    public ProjectConfiguration getProjectConfiguration(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
        InMemoryProject project = this.backend.getProject(projectId);
        return sourceSpecification.visit(new SourceSpecificationVisitor<ProjectConfiguration>()
        {
            @Override
            public ProjectConfiguration visit(ProjectSourceSpecification sourceSpec)
            {
                return ((revisionId == null) ? project.getCurrentRevision() : project.getRevision(revisionId)).getConfiguration();
            }

            @Override
            public ProjectConfiguration visit(VersionSourceSpecification sourceSpec)
            {
                return project.getVersion(sourceSpec.getVersionId().toVersionIdString()).getConfiguration();
            }

            @Override
            public ProjectConfiguration visit(PatchSourceSpecification sourceSpec)
            {
                InMemoryPatch patch = project.getPatch(sourceSpec.getVersionId());
                return ((revisionId == null) ? patch.getCurrentRevision() : patch.getRevision(revisionId)).getConfiguration();
            }

            @Override
            public ProjectConfiguration visit(WorkspaceSourceSpecification sourceSpec)
            {
                WorkspaceSpecification workspaceSpec = sourceSpec.getWorkspaceSpecification();
                if (workspaceSpec.getAccessType() != ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE)
                {
                    throw new UnsupportedOperationException("Not implemented");
                }
                VersionId patchVersionId = workspaceSpec.getSource().visit(new WorkspaceSourceVisitor<VersionId>()
                {
                    @Override
                    public VersionId visit(ProjectWorkspaceSource source)
                    {
                        return null;
                    }

                    @Override
                    public VersionId visit(PatchWorkspaceSource source)
                    {
                        return source.getPatchVersionId();
                    }
                });
                InMemoryWorkspace workspace = (workspaceSpec.getType() == WorkspaceType.GROUP) ?
                        project.getGroupWorkspace(workspaceSpec.getId(), patchVersionId) :
                        project.getUserWorkspace(workspaceSpec.getId(), patchVersionId);
                InMemoryRevision revision = (revisionId == null) ? workspace.getCurrentRevision() : workspace.getRevision(revisionId);
                return revision.getConfiguration();
            }
        });
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
    public Revision updateProjectConfiguration(String projectId, WorkspaceSourceSpecification sourceSpecification, String message, ProjectConfigurationUpdater updater)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public List<ArtifactTypeGenerationConfiguration> getAvailableArtifactGenerations(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public ProjectStructureVersion getLatestProjectStructureVersion()
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public ProjectConfigurationStatusReport getProjectConfigurationStatus(String projectId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }
}
