// Copyright 2021 Goldman Sachs
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

package org.finos.legend.sdlc.server.gitlab.finos;

import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.project.ProjectFileOperation;
import org.finos.legend.sdlc.server.project.extension.BaseProjectStructureExtensionProvider;
import org.finos.legend.sdlc.server.project.extension.DefaultProjectStructureExtension;
import org.finos.legend.sdlc.server.project.extension.ProjectStructureExtension;

import java.util.Collections;
import java.util.function.Consumer;

public class FinosGitlabProjectStructureExtensionProvider extends BaseProjectStructureExtensionProvider
{
    private static final String CI_CONFIG_PATH = "/.gitlab-ci.yml";

    private final FinosFileLoader ciFileLoader;

    protected FinosGitlabProjectStructureExtensionProvider()
    {
        this.ciFileLoader = new FinosFileLoader(getClass().getClassLoader(), "gitlab-ci");
    }

    @Override
    public Integer getLatestVersionForProjectStructureVersion(int projectStructureVersion)
    {
        if (projectStructureVersion == 11)
        {
            return 1;
        }
        return null;
    }

    @Override
    public ProjectStructureExtension getProjectStructureExtension(int projectStructureVersion, int projectStructureExtensionVersion)
    {
        Integer latestExtension = getLatestVersionForProjectStructureVersion(projectStructureVersion);
        if ((latestExtension == null) || (projectStructureExtensionVersion > latestExtension))
        {
            return null;
        }

        return new FinosGitlabProjectStructureExtension(projectStructureVersion, projectStructureExtensionVersion);
    }

    private String getGitLabCIFile(int projectStructureVersion, int projectStructureExtensionVersion)
    {
        return (this.ciFileLoader == null) ? null : this.ciFileLoader.getCIResource(getGitLabCIFileVersion(projectStructureVersion, projectStructureExtensionVersion));
    }

    protected int getGitLabCIFileVersion(int projectStructureVersion, int projectStructureExtensionVersion)
    {
        if (projectStructureVersion == 11)
        {
            if (projectStructureExtensionVersion == 1)
            {
                return 1;
            }
        }
        throw new IllegalArgumentException("Unknown project structure extension version for project structure version " + projectStructureVersion + ": " + projectStructureExtensionVersion);
    }

    private class FinosGitlabProjectStructureExtension extends DefaultProjectStructureExtension
    {
        private FinosGitlabProjectStructureExtension(int projectStructureVersion, int extensionVersion)
        {
            super(projectStructureVersion, extensionVersion, Collections.emptyMap());
        }

        @Override
        public void collectUpdateProjectConfigurationOperations(ProjectConfiguration oldConfig, ProjectConfiguration newConfig, ProjectFileAccessProvider.FileAccessContext fileAccessContext, Consumer<ProjectFileOperation> operationConsumer)
        {
            super.collectUpdateProjectConfigurationOperations(oldConfig, newConfig, fileAccessContext, operationConsumer);
            updateFile(CI_CONFIG_PATH, getCiConfigContent(), fileAccessContext, operationConsumer);
        }

        private String getCiConfigContent()
        {
            return getGitLabCIFile(getProjectStructureVersion(), getVersion());
        }

    }
}
