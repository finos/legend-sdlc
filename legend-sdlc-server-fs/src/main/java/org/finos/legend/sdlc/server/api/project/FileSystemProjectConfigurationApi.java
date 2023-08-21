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

package org.finos.legend.sdlc.server.api.project;

import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactTypeGenerationConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectStructureVersion;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.server.api.entity.FileSystemApiWithFileAccess;
import org.finos.legend.sdlc.server.domain.api.project.ProjectConfigurationApi;
import org.finos.legend.sdlc.server.domain.api.project.ProjectConfigurationUpdater;
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification;
import org.finos.legend.sdlc.server.domain.api.project.source.WorkspaceSourceSpecification;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.project.ProjectConfigurationStatusReport;
import org.finos.legend.sdlc.server.project.ProjectStructure;
import org.finos.legend.sdlc.server.project.extension.ProjectStructureExtensionProvider;
import org.finos.legend.sdlc.server.startup.FSConfiguration;

import java.util.Collections;
import java.util.List;
import javax.inject.Inject;

public class FileSystemProjectConfigurationApi extends FileSystemApiWithFileAccess implements ProjectConfigurationApi
{
    private final ProjectStructureExtensionProvider projectStructureExtensionProvider;

    @Inject
    public FileSystemProjectConfigurationApi(FSConfiguration fsConfiguration, ProjectStructureExtensionProvider projectStructureExtensionProvider)
    {
        super(fsConfiguration);
        this.projectStructureExtensionProvider = projectStructureExtensionProvider;
    }

    @Override
    public ProjectConfiguration getProjectConfiguration(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(sourceSpecification, "sourceSpecification may not be null");
        ProjectConfiguration config = ProjectStructure.getProjectConfiguration(projectId, sourceSpecification, revisionId, getProjectFileAccessProvider());
        return (config == null) ? ProjectStructure.getDefaultProjectConfiguration(projectId) : config;
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
         return Collections.emptyList();
    }

    @Override
    public ProjectConfigurationStatusReport getProjectConfigurationStatus(String projectId)
    {
        boolean isProjectConfigured = (ProjectStructure.getProjectConfiguration(projectId, SourceSpecification.projectSourceSpecification(), null, getProjectFileAccessProvider()) != null);
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
        return ProjectStructureVersion.newProjectStructureVersion(latestProjectStructureVersion, this.projectStructureExtensionProvider.getLatestVersionForProjectStructureVersion(latestProjectStructureVersion));
    }
}
