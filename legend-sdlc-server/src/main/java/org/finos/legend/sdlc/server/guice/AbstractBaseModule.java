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

package org.finos.legend.sdlc.server.guice;

import com.google.inject.Binder;
import com.google.inject.Provides;
import com.hubspot.dropwizard.guicier.DropwizardAwareModule;
import org.finos.legend.sdlc.server.BaseLegendSDLCServer;
import org.finos.legend.sdlc.server.BaseServer.ServerInfo;
import org.finos.legend.sdlc.server.config.LegendSDLCServerConfiguration;
import org.finos.legend.sdlc.server.depot.auth.AuthClientInjector;
import org.finos.legend.sdlc.server.depot.DepotConfiguration;
import org.finos.legend.sdlc.server.domain.api.dependency.DependenciesApi;
import org.finos.legend.sdlc.server.domain.api.dependency.DependenciesApiImpl;
import org.finos.legend.sdlc.server.domain.api.test.TestModelBuilder;
import org.finos.legend.sdlc.server.project.ProjectStructurePlatformExtensions;
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
import org.finos.legend.sdlc.server.resources.GroupBackupWorkspaceEntitiesResource;
import org.finos.legend.sdlc.server.resources.GroupBackupWorkspaceEntityPathsResource;
import org.finos.legend.sdlc.server.resources.GroupBackupWorkspaceProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.GroupBackupWorkspaceResource;
import org.finos.legend.sdlc.server.resources.GroupBackupWorkspaceRevisionEntitiesResource;
import org.finos.legend.sdlc.server.resources.GroupBackupWorkspaceRevisionEntityPathsResource;
import org.finos.legend.sdlc.server.resources.GroupBackupWorkspaceRevisionProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.GroupBackupWorkspaceRevisionsResource;
import org.finos.legend.sdlc.server.resources.GroupComparisonWorkspaceResource;
import org.finos.legend.sdlc.server.resources.GroupConflictResolutionWorkspaceEntitiesResource;
import org.finos.legend.sdlc.server.resources.GroupConflictResolutionWorkspaceEntityPathsResource;
import org.finos.legend.sdlc.server.resources.GroupConflictResolutionWorkspaceProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.GroupConflictResolutionWorkspaceResource;
import org.finos.legend.sdlc.server.resources.GroupConflictResolutionWorkspaceRevisionEntitiesResource;
import org.finos.legend.sdlc.server.resources.GroupConflictResolutionWorkspaceRevisionEntityPathsResource;
import org.finos.legend.sdlc.server.resources.GroupConflictResolutionWorkspaceRevisionProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.GroupConflictResolutionWorkspaceRevisionsResource;
import org.finos.legend.sdlc.server.resources.GroupWorkspaceEntitiesResource;
import org.finos.legend.sdlc.server.resources.GroupWorkspaceEntityChangesResource;
import org.finos.legend.sdlc.server.resources.GroupWorkspaceEntityPathsResource;
import org.finos.legend.sdlc.server.resources.GroupWorkspaceEntityRevisionsResource;
import org.finos.legend.sdlc.server.resources.GroupWorkspacePackageRevisionsResource;
import org.finos.legend.sdlc.server.resources.GroupWorkspaceProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.GroupWorkspaceRevisionDependenciesResource;
import org.finos.legend.sdlc.server.resources.GroupWorkspaceRevisionEntitiesResource;
import org.finos.legend.sdlc.server.resources.GroupWorkspaceRevisionEntityPathsResource;
import org.finos.legend.sdlc.server.resources.GroupWorkspaceRevisionProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.GroupWorkspaceRevisionsResource;
import org.finos.legend.sdlc.server.resources.GroupWorkspaceWorkflowJobsResource;
import org.finos.legend.sdlc.server.resources.GroupWorkspaceWorkflowsResource;
import org.finos.legend.sdlc.server.resources.GroupWorkspacesResource;
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
import org.finos.legend.sdlc.server.resources.ProjectWorkflowJobsResource;
import org.finos.legend.sdlc.server.resources.ProjectWorkflowsResource;
import org.finos.legend.sdlc.server.resources.ProjectsResource;
import org.finos.legend.sdlc.server.resources.ReviewsOnlyResource;
import org.finos.legend.sdlc.server.resources.ReviewsResource;
import org.finos.legend.sdlc.server.resources.UsersResource;
import org.finos.legend.sdlc.server.resources.VersionBuildsResource;
import org.finos.legend.sdlc.server.resources.VersionEntitiesResource;
import org.finos.legend.sdlc.server.resources.VersionEntityPathsResource;
import org.finos.legend.sdlc.server.resources.VersionProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.VersionWorkflowJobsResource;
import org.finos.legend.sdlc.server.resources.VersionWorkflowsResource;
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
import org.finos.legend.sdlc.server.resources.WorkspaceWorkflowJobsResource;
import org.finos.legend.sdlc.server.resources.WorkspaceWorkflowsResource;
import org.finos.legend.sdlc.server.resources.WorkspacesResource;
import org.finos.legend.sdlc.server.tools.BackgroundTaskProcessor;

import javax.inject.Named;
import java.util.List;

public abstract class AbstractBaseModule extends DropwizardAwareModule<LegendSDLCServerConfiguration>
{
    protected final BaseLegendSDLCServer<?> server;
    protected ProjectStructureExtensionProvider extensionProvider;
    protected ProjectStructurePlatformExtensions projectStructurePlatformExtensions;
    private AuthClientInjector authClientInjector;

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
        binder.bind(TestModelBuilder.class);
        binder.bind(ProjectStructureConfiguration.class).toProvider(this::getProjectStructureConfiguration);
        binder.bind(ProjectStructureExtensionProvider.class).toProvider(this::getProjectStructureExtensionProvider);
        binder.bind(DepotConfiguration.class).toProvider(this::getDepotConfiguration);
        binder.bind(AuthClientInjector.class).toProvider(this::getAuthClientInjector);
        binder.bind(ServerInfo.class).toProvider(this.server::getServerInfo);
        binder.bind(BackgroundTaskProcessor.class).toProvider(this.server::getBackgroundTaskProcessor);
        binder.bind(ProjectStructurePlatformExtensions.class).toProvider(this::getProjectStructurePlatformExtensions);

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
        binder.bind(ReviewsOnlyResource.class);
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
        binder.bind(ProjectWorkflowsResource.class);
        binder.bind(VersionWorkflowsResource.class);
        binder.bind(WorkspaceWorkflowsResource.class);
        binder.bind(ProjectWorkflowJobsResource.class);
        binder.bind(VersionWorkflowJobsResource.class);
        binder.bind(WorkspaceWorkflowJobsResource.class);
        binder.bind(GroupBackupWorkspaceEntitiesResource.class);
        binder.bind(GroupBackupWorkspaceEntityPathsResource.class);
        binder.bind(GroupBackupWorkspaceProjectConfigurationResource.class);
        binder.bind(GroupBackupWorkspaceResource.class);
        binder.bind(GroupBackupWorkspaceRevisionEntitiesResource.class);
        binder.bind(GroupBackupWorkspaceRevisionEntityPathsResource.class);
        binder.bind(GroupBackupWorkspaceRevisionProjectConfigurationResource.class);
        binder.bind(GroupBackupWorkspaceRevisionsResource.class);
        binder.bind(GroupComparisonWorkspaceResource.class);
        binder.bind(GroupConflictResolutionWorkspaceEntitiesResource.class);
        binder.bind(GroupConflictResolutionWorkspaceEntityPathsResource.class);
        binder.bind(GroupConflictResolutionWorkspaceProjectConfigurationResource.class);
        binder.bind(GroupConflictResolutionWorkspaceResource.class);
        binder.bind(GroupConflictResolutionWorkspaceRevisionEntitiesResource.class);
        binder.bind(GroupConflictResolutionWorkspaceRevisionEntityPathsResource.class);
        binder.bind(GroupConflictResolutionWorkspaceRevisionProjectConfigurationResource.class);
        binder.bind(GroupConflictResolutionWorkspaceRevisionsResource.class);
        binder.bind(GroupWorkspaceEntitiesResource.class);
        binder.bind(GroupWorkspaceEntityChangesResource.class);
        binder.bind(GroupWorkspaceEntityPathsResource.class);
        binder.bind(GroupWorkspaceEntityRevisionsResource.class);
        binder.bind(GroupWorkspacePackageRevisionsResource.class);
        binder.bind(GroupWorkspaceProjectConfigurationResource.class);
        binder.bind(GroupWorkspaceRevisionDependenciesResource.class);
        binder.bind(GroupWorkspaceRevisionEntitiesResource.class);
        binder.bind(GroupWorkspaceRevisionEntityPathsResource.class);
        binder.bind(GroupWorkspaceRevisionProjectConfigurationResource.class);
        binder.bind(GroupWorkspaceRevisionsResource.class);
        binder.bind(GroupWorkspacesResource.class);
        binder.bind(GroupWorkspaceWorkflowJobsResource.class);
        binder.bind(GroupWorkspaceWorkflowsResource.class);
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

    private ProjectStructurePlatformExtensions getProjectStructurePlatformExtensions()
    {
        if (this.projectStructurePlatformExtensions == null)
        {
            this.projectStructurePlatformExtensions = buildProjectStructurePlatformExtensions();
        }
        return this.projectStructurePlatformExtensions;
    }

    private ProjectStructurePlatformExtensions buildProjectStructurePlatformExtensions()
    {
        if (this.getProjectStructureConfiguration().getProjectPlatformsConfiguration() == null)
        {
            return null;
        }
        return this.getProjectStructureConfiguration().getProjectPlatformsConfiguration().buildProjectStructurePlatformExtensions();
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

    private DepotConfiguration getDepotConfiguration()
    {
        DepotConfiguration depotConfiguration = getConfiguration().getDepotConfiguration();
        return (depotConfiguration == null) ? DepotConfiguration.emptyConfiguration() : depotConfiguration;
    }

    private AuthClientInjector getAuthClientInjector()
    {
        if (this.authClientInjector == null)
        {
            this.authClientInjector = resolveAuthClientInjector();
        }
        return this.authClientInjector;
    }

    private AuthClientInjector resolveAuthClientInjector()
    {
        DepotConfiguration depotConfiguration = getConfiguration().getDepotConfiguration();
        if (depotConfiguration != null)
        {
            AuthClientInjector authClientInjector = depotConfiguration.getAuthClientInjector();
            if (authClientInjector != null)
            {
                return authClientInjector;
            }
        }
        return builder -> builder;
    }

    @Provides
    @Named("applicationName")
    public String provideApplicationName(LegendSDLCServerConfiguration configuration)
    {
        return configuration.getApplicationName();
    }
}
