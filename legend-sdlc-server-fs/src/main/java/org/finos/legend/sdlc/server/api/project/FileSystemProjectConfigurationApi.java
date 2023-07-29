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
import org.finos.legend.sdlc.server.domain.api.project.ProjectConfigurationApi;
import org.finos.legend.sdlc.server.domain.api.project.ProjectConfigurationUpdater;
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification;
import org.finos.legend.sdlc.server.domain.api.project.source.WorkspaceSourceSpecification;
import org.finos.legend.sdlc.server.domain.model.project.configuration.FileSystemProjectConfiguration;
import org.finos.legend.sdlc.server.project.ProjectConfigurationStatusReport;

import java.util.Collections;
import java.util.List;
import javax.inject.Inject;

public class FileSystemProjectConfigurationApi implements ProjectConfigurationApi
{
    @Inject
    public FileSystemProjectConfigurationApi()
    {
    }

    @Override
    public ProjectConfiguration getProjectConfiguration(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
        return new FileSystemProjectConfiguration(projectId, "", "");
    }

    @Override
    public ProjectConfiguration getReviewFromProjectConfiguration(String projectId, String reviewId)
    {
        return new FileSystemProjectConfiguration(projectId, "", "");
    }

    @Override
    public ProjectConfiguration getReviewToProjectConfiguration(String projectId, String reviewId)
    {
        return new FileSystemProjectConfiguration(projectId, "", "");
    }

    @Override
    public Revision updateProjectConfiguration(String projectId, WorkspaceSourceSpecification sourceSpecification, String message, ProjectConfigurationUpdater updater)
    {
        return null;
    }

    @Override
    public List<ArtifactTypeGenerationConfiguration> getAvailableArtifactGenerations(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
         return Collections.emptyList();
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

    @Override
    public ProjectStructureVersion getLatestProjectStructureVersion()
    {
        return ProjectStructureVersion.newProjectStructureVersion(13);
    }
}
