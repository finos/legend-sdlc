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

package org.finos.legend.sdlc.server.guice;

import com.google.inject.Binder;
import com.google.inject.Provides;
import com.hubspot.dropwizard.guicier.DropwizardAwareModule;
import org.finos.legend.sdlc.server.BaseLegendSDLCServer;
import org.finos.legend.sdlc.server.BaseServer.ServerInfo;
import org.finos.legend.sdlc.server.config.LegendSDLCServerConfiguration;
import org.finos.legend.sdlc.server.domain.api.dependency.DependenciesApi;
import org.finos.legend.sdlc.server.domain.api.dependency.DependenciesApiImpl;
import org.finos.legend.sdlc.server.project.config.ProjectStructureConfiguration;
import org.finos.legend.sdlc.server.project.extension.DefaultProjectStructureExtensionProvider;
import org.finos.legend.sdlc.server.project.extension.ProjectStructureExtension;
import org.finos.legend.sdlc.server.project.extension.ProjectStructureExtensionProvider;
import org.finos.legend.sdlc.server.project.extension.VoidProjectStructureExtensionProvider;
import org.finos.legend.sdlc.server.resources.BackupProjectResource;
import org.finos.legend.sdlc.server.resources.BackupWorkspaceEntitiesResource;
import org.finos.legend.sdlc.server.resources.BackupWorkspaceEntityPathsResource;
import org.finos.legend.sdlc.server.resources.BackupWorkspaceProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.BackupWorkspaceResource;
import org.finos.legend.sdlc.server.resources.BackupWorkspaceRevisionEntitiesResource;
import org.finos.legend.sdlc.server.resources.BackupWorkspaceRevisionEntityPathsResource;
import org.finos.legend.sdlc.server.resources.BackupWorkspaceRevisionProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.BackupWorkspaceRevisionsResource;
import org.finos.legend.sdlc.server.resources.ComparisonReviewEntitiesResource;
import org.finos.legend.sdlc.server.resources.ComparisonReviewProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.ComparisonReviewResource;
import org.finos.legend.sdlc.server.resources.ComparisonWorkspaceResource;
import org.finos.legend.sdlc.server.resources.ConfigurationResource;
import org.finos.legend.sdlc.server.resources.ConflictResolutionProjectResource;
import org.finos.legend.sdlc.server.resources.ConflictResolutionWorkspaceEntitiesResource;
import org.finos.legend.sdlc.server.resources.ConflictResolutionWorkspaceEntityPathsResource;
import org.finos.legend.sdlc.server.resources.ConflictResolutionWorkspaceProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.ConflictResolutionWorkspaceResource;
import org.finos.legend.sdlc.server.resources.ConflictResolutionWorkspaceRevisionEntitiesResource;
import org.finos.legend.sdlc.server.resources.ConflictResolutionWorkspaceRevisionEntityPathsResource;
import org.finos.legend.sdlc.server.resources.ConflictResolutionWorkspaceRevisionProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.ConflictResolutionWorkspaceRevisionsResource;
import org.finos.legend.sdlc.server.resources.CurrentUserResource;
import org.finos.legend.sdlc.server.resources.DownstreamDependenciesResource;
import org.finos.legend.sdlc.server.resources.InfoResource;
import org.finos.legend.sdlc.server.resources.IssuesResource;
import org.finos.legend.sdlc.server.resources.ProjectBuildsResource;
import org.finos.legend.sdlc.server.resources.ProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.ProjectEntitiesResource;
import org.finos.legend.sdlc.server.resources.ProjectEntityPathsResource;
import org.finos.legend.sdlc.server.resources.ProjectEntityRevisionsResource;
import org.finos.legend.sdlc.server.resources.ProjectPackageRevisionsResource;
import org.finos.legend.sdlc.server.resources.ProjectRevisionDependenciesResource;
import org.finos.legend.sdlc.server.resources.ProjectRevisionEntitiesResource;
import org.finos.legend.sdlc.server.resources.ProjectRevisionEntityPathsResource;
import org.finos.legend.sdlc.server.resources.ProjectRevisionProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.ProjectRevisionsResource;
import org.finos.legend.sdlc.server.resources.ProjectVersionDependenciesResource;
import org.finos.legend.sdlc.server.resources.ProjectsResource;
import org.finos.legend.sdlc.server.resources.ReviewsResource;
import org.finos.legend.sdlc.server.resources.UsersResource;
import org.finos.legend.sdlc.server.resources.VersionBuildsResource;
import org.finos.legend.sdlc.server.resources.VersionEntitiesResource;
import org.finos.legend.sdlc.server.resources.VersionEntityPathsResource;
import org.finos.legend.sdlc.server.resources.VersionProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.VersionsResource;
import org.finos.legend.sdlc.server.resources.WorkspaceBuildsResource;
import org.finos.legend.sdlc.server.resources.WorkspaceEntitiesResource;
import org.finos.legend.sdlc.server.resources.WorkspaceEntityChangesResource;
import org.finos.legend.sdlc.server.resources.WorkspaceEntityPathsResource;
import org.finos.legend.sdlc.server.resources.WorkspaceEntityRevisionsResource;
import org.finos.legend.sdlc.server.resources.WorkspacePackageRevisionsResource;
import org.finos.legend.sdlc.server.resources.WorkspaceProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.WorkspaceRevisionDependenciesResource;
import org.finos.legend.sdlc.server.resources.WorkspaceRevisionEntitiesResource;
import org.finos.legend.sdlc.server.resources.WorkspaceRevisionEntityPathsResource;
import org.finos.legend.sdlc.server.resources.WorkspaceRevisionProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.WorkspaceRevisionsResource;
import org.finos.legend.sdlc.server.resources.WorkspacesResource;
import org.finos.legend.sdlc.server.tools.BackgroundTaskProcessor;

import java.util.List;
import javax.inject.Named;

public abstract class AbstractBaseModule extends DropwizardAwareModule<LegendSDLCServerConfiguration>
{
    protected final BaseLegendSDLCServer<?> server;
    protected ProjectStructureExtensionProvider extensionProvider;

    public AbstractBaseModule(BaseLegendSDLCServer<?> server)
    {
        this.server = server;
    }

    @Override
    public void configure(Binder binder)
    {
        configureCommonApis(binder);
        configureApis(binder);

        binder.bind(UserContext.class);
        binder.bind(ProjectStructureConfiguration.class).toProvider(this::getProjectStructureConfiguration);
        binder.bind(ProjectStructureExtensionProvider.class).toProvider(this::getProjectStructureExtensionProvider);
        binder.bind(ServerInfo.class).toProvider(this.server::getServerInfo);
        binder.bind(BackgroundTaskProcessor.class).toProvider(this.server::getBackgroundTaskProcessor);

        bindResources(binder);
        bindFilters(binder);
        bindExceptionMappers(binder);
    }

    private void bindResources(Binder binder)
    {
        binder.bind(ProjectsResource.class);
        binder.bind(WorkspacesResource.class);
        binder.bind(ProjectConfigurationResource.class);
        binder.bind(ConfigurationResource.class);
        binder.bind(ProjectRevisionProjectConfigurationResource.class);
        binder.bind(WorkspaceProjectConfigurationResource.class);
        binder.bind(WorkspaceRevisionProjectConfigurationResource.class);
        binder.bind(ProjectEntitiesResource.class);
        binder.bind(ProjectEntityPathsResource.class);
        binder.bind(ProjectRevisionEntitiesResource.class);
        binder.bind(ProjectRevisionEntityPathsResource.class);
        binder.bind(WorkspaceEntitiesResource.class);
        binder.bind(WorkspaceEntityPathsResource.class);
        binder.bind(WorkspaceEntityChangesResource.class);
        binder.bind(WorkspaceRevisionEntitiesResource.class);
        binder.bind(WorkspaceRevisionEntityPathsResource.class);
        binder.bind(VersionEntitiesResource.class);
        binder.bind(VersionEntityPathsResource.class);
        binder.bind(VersionProjectConfigurationResource.class);
        binder.bind(ProjectRevisionsResource.class);
        binder.bind(ProjectEntityRevisionsResource.class);
        binder.bind(ProjectPackageRevisionsResource.class);
        binder.bind(WorkspaceRevisionsResource.class);
        binder.bind(WorkspaceEntityRevisionsResource.class);
        binder.bind(WorkspacePackageRevisionsResource.class);
        binder.bind(IssuesResource.class);
        binder.bind(UsersResource.class);
        binder.bind(CurrentUserResource.class);
        binder.bind(ReviewsResource.class);
        binder.bind(ProjectBuildsResource.class);
        binder.bind(WorkspaceBuildsResource.class);
        binder.bind(VersionBuildsResource.class);
        binder.bind(VersionsResource.class);
        binder.bind(InfoResource.class);
        binder.bind(ComparisonWorkspaceResource.class);
        binder.bind(ComparisonReviewResource.class);
        binder.bind(ComparisonReviewEntitiesResource.class);
        binder.bind(ComparisonReviewProjectConfigurationResource.class);
        binder.bind(ConflictResolutionProjectResource.class);
        binder.bind(ConflictResolutionWorkspaceResource.class);
        binder.bind(ConflictResolutionWorkspaceEntitiesResource.class);
        binder.bind(ConflictResolutionWorkspaceEntityPathsResource.class);
        binder.bind(ConflictResolutionWorkspaceRevisionsResource.class);
        binder.bind(ConflictResolutionWorkspaceRevisionEntitiesResource.class);
        binder.bind(ConflictResolutionWorkspaceRevisionEntityPathsResource.class);
        binder.bind(ConflictResolutionWorkspaceProjectConfigurationResource.class);
        binder.bind(ConflictResolutionWorkspaceRevisionProjectConfigurationResource.class);
        binder.bind(BackupProjectResource.class);
        binder.bind(BackupWorkspaceResource.class);
        binder.bind(BackupWorkspaceEntitiesResource.class);
        binder.bind(BackupWorkspaceEntityPathsResource.class);
        binder.bind(BackupWorkspaceRevisionsResource.class);
        binder.bind(BackupWorkspaceRevisionEntitiesResource.class);
        binder.bind(BackupWorkspaceRevisionEntityPathsResource.class);
        binder.bind(BackupWorkspaceProjectConfigurationResource.class);
        binder.bind(BackupWorkspaceRevisionProjectConfigurationResource.class);
        binder.bind(DownstreamDependenciesResource.class);
        binder.bind(ProjectRevisionDependenciesResource.class);
        binder.bind(ProjectVersionDependenciesResource.class);
        binder.bind(WorkspaceRevisionDependenciesResource.class);
    }

    private void configureCommonApis(Binder binder)
    {
        binder.bind(DependenciesApi.class).to(DependenciesApiImpl.class);
    }

    protected abstract void configureApis(Binder binder);

    private void bindFilters(Binder binder)
    {

    }

    private void bindExceptionMappers(Binder binder)
    {

    }

    private ProjectStructureConfiguration getProjectStructureConfiguration()
    {
        ProjectStructureConfiguration projectStructureConfiguration = getConfiguration().getProjectStructureConfiguration();
        return (projectStructureConfiguration == null) ? ProjectStructureConfiguration.emptyConfiguration() : projectStructureConfiguration;
    }

    private ProjectStructureExtensionProvider getProjectStructureExtensionProvider()
    {
        if (this.extensionProvider == null)
        {
            this.extensionProvider = resolveProjectStructureExtensionProvider();
        }
        return this.extensionProvider;
    }

    private ProjectStructureExtensionProvider resolveProjectStructureExtensionProvider()
    {
        ProjectStructureConfiguration projectStructureConfiguration = getConfiguration().getProjectStructureConfiguration();
        if (projectStructureConfiguration != null)
        {
            ProjectStructureExtensionProvider configuredProvider = projectStructureConfiguration.getProjectStructureExtensionProvider();
            if (configuredProvider != null)
            {
                return configuredProvider;
            }
            List<ProjectStructureExtension> extensions = projectStructureConfiguration.getProjectStructureExtensions();
            if ((extensions != null) && !extensions.isEmpty())
            {
                return DefaultProjectStructureExtensionProvider.fromExtensions(extensions);
            }
        }
        return new VoidProjectStructureExtensionProvider();
    }

    @Provides
    @Named("applicationName")
    public String provideApplicationName(LegendSDLCServerConfiguration configuration)
    {
        return configuration.getApplicationName();
    }
}
