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
import org.finos.legend.sdlc.server.config.LegendSDLCServerFeaturesConfiguration;
import org.finos.legend.sdlc.server.depot.DepotConfiguration;
import org.finos.legend.sdlc.server.depot.auth.AuthClientInjector;
import org.finos.legend.sdlc.server.domain.api.dependency.DependenciesApi;
import org.finos.legend.sdlc.server.domain.api.dependency.DependenciesApiImpl;
import org.finos.legend.sdlc.server.domain.api.test.TestModelBuilder;
import org.finos.legend.sdlc.server.project.ProjectStructurePlatformExtensions;
import org.finos.legend.sdlc.server.project.config.ProjectPlatformsConfiguration;
import org.finos.legend.sdlc.server.project.config.ProjectStructureConfiguration;
import org.finos.legend.sdlc.server.project.extension.DefaultProjectStructureExtensionProvider;
import org.finos.legend.sdlc.server.project.extension.ProjectStructureExtension;
import org.finos.legend.sdlc.server.project.extension.ProjectStructureExtensionProvider;
import org.finos.legend.sdlc.server.project.extension.VoidProjectStructureExtensionProvider;
import org.finos.legend.sdlc.server.resources.backup.patch.group.BackupPatchesGroupWorkspaceEntitiesResource;
import org.finos.legend.sdlc.server.resources.backup.patch.group.BackupPatchesGroupWorkspaceRevisionEntityPathsResource;
import org.finos.legend.sdlc.server.resources.backup.patch.group.BackupPatchesGroupWorkspaceRevisionsResource;
import org.finos.legend.sdlc.server.resources.backup.patch.user.BackupPatchesWorkspaceEntitiesResource;
import org.finos.legend.sdlc.server.resources.backup.patch.user.BackupPatchesWorkspaceEntityPathsResource;
import org.finos.legend.sdlc.server.resources.backup.patch.user.BackupPatchesWorkspaceResource;
import org.finos.legend.sdlc.server.resources.backup.patch.user.BackupPatchesWorkspaceRevisionEntitiesResource;
import org.finos.legend.sdlc.server.resources.backup.patch.user.BackupPatchesWorkspaceRevisionEntityPathsResource;
import org.finos.legend.sdlc.server.resources.backup.patch.user.BackupPatchesWorkspaceRevisionProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.backup.patch.user.BackupPatchesWorkspaceRevisionsResource;
import org.finos.legend.sdlc.server.resources.backup.project.BackupProjectResource;
import org.finos.legend.sdlc.server.resources.backup.project.user.BackupWorkspaceEntitiesResource;
import org.finos.legend.sdlc.server.resources.backup.project.user.BackupWorkspaceEntityPathsResource;
import org.finos.legend.sdlc.server.resources.backup.patch.user.BackupPatchesWorkspaceProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.backup.project.user.BackupWorkspaceProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.backup.project.user.BackupWorkspaceResource;
import org.finos.legend.sdlc.server.resources.backup.project.user.BackupWorkspaceRevisionEntitiesResource;
import org.finos.legend.sdlc.server.resources.backup.project.user.BackupWorkspaceRevisionEntityPathsResource;
import org.finos.legend.sdlc.server.resources.backup.project.user.BackupWorkspaceRevisionProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.backup.project.user.BackupWorkspaceRevisionsResource;
import org.finos.legend.sdlc.server.resources.comparison.patch.ComparisonPatchReviewEntitiesResource;
import org.finos.legend.sdlc.server.resources.comparison.patch.ComparisonPatchReviewProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.comparison.patch.ComparisonPatchReviewResource;
import org.finos.legend.sdlc.server.resources.comparison.patch.user.ComparisonPatchesWorkspaceResource;
import org.finos.legend.sdlc.server.resources.comparison.project.ComparisonReviewEntitiesResource;
import org.finos.legend.sdlc.server.resources.comparison.project.ComparisonReviewProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.comparison.project.ComparisonReviewResource;
import org.finos.legend.sdlc.server.resources.comparison.project.user.ComparisonWorkspaceResource;
import org.finos.legend.sdlc.server.resources.conflictResolution.patch.group.ConflictResolutionPatchesGroupWorkspaceProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.conflictResolution.patch.group.ConflictResolutionPatchesGroupWorkspaceRevisionEntitiesResource;
import org.finos.legend.sdlc.server.resources.conflictResolution.patch.group.ConflictResolutionPatchesGroupWorkspaceRevisionProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.entity.patch.group.PatchesGroupWorkspaceEntityChangesResource;
import org.finos.legend.sdlc.server.resources.entity.patch.group.PatchesGroupWorkspaceEntityPathsResource;
import org.finos.legend.sdlc.server.resources.entity.patch.group.PatchesGroupWorkspaceRevisionEntitiesResource;
import org.finos.legend.sdlc.server.resources.entity.patch.group.PatchesGroupWorkspaceRevisionEntityPathsResource;
import org.finos.legend.sdlc.server.resources.entity.patch.user.PatchesWorkspaceEntityChangesResource;
import org.finos.legend.sdlc.server.resources.entity.patch.user.PatchesWorkspaceEntityPathsResource;
import org.finos.legend.sdlc.server.resources.entity.patch.user.PatchesWorkspaceRevisionEntitiesResource;
import org.finos.legend.sdlc.server.resources.entity.patch.user.PatchesWorkspaceRevisionEntityPathsResource;
import org.finos.legend.sdlc.server.resources.project.ConfigurationResource;
import org.finos.legend.sdlc.server.resources.conflictResolution.patch.ConflictResolutionPatchResource;
import org.finos.legend.sdlc.server.resources.conflictResolution.patch.user.ConflictResolutionPatchesWorkspaceEntitiesResource;
import org.finos.legend.sdlc.server.resources.conflictResolution.patch.user.ConflictResolutionPatchesWorkspaceEntityPathsResource;
import org.finos.legend.sdlc.server.resources.conflictResolution.patch.user.ConflictResolutionPatchesWorkspaceProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.conflictResolution.patch.user.ConflictResolutionPatchesWorkspaceResource;
import org.finos.legend.sdlc.server.resources.conflictResolution.patch.user.ConflictResolutionPatchesWorkspaceRevisionEntitiesResource;
import org.finos.legend.sdlc.server.resources.conflictResolution.patch.user.ConflictResolutionPatchesWorkspaceRevisionEntityPathsResource;
import org.finos.legend.sdlc.server.resources.conflictResolution.patch.user.ConflictResolutionPatchesWorkspaceRevisionProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.conflictResolution.patch.user.ConflictResolutionPatchesWorkspaceRevisionsResource;
import org.finos.legend.sdlc.server.resources.conflictResolution.project.ConflictResolutionProjectResource;
import org.finos.legend.sdlc.server.resources.conflictResolution.project.user.ConflictResolutionWorkspaceEntitiesResource;
import org.finos.legend.sdlc.server.resources.conflictResolution.project.user.ConflictResolutionWorkspaceEntityPathsResource;
import org.finos.legend.sdlc.server.resources.conflictResolution.project.user.ConflictResolutionWorkspaceProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.conflictResolution.project.user.ConflictResolutionWorkspaceResource;
import org.finos.legend.sdlc.server.resources.conflictResolution.project.user.ConflictResolutionWorkspaceRevisionEntitiesResource;
import org.finos.legend.sdlc.server.resources.conflictResolution.project.user.ConflictResolutionWorkspaceRevisionEntityPathsResource;
import org.finos.legend.sdlc.server.resources.conflictResolution.project.user.ConflictResolutionWorkspaceRevisionProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.conflictResolution.project.user.ConflictResolutionWorkspaceRevisionsResource;
import org.finos.legend.sdlc.server.resources.project.patch.group.PatchesGroupWorkspaceRevisionProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.project.patch.user.PatchesWorkspaceProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.project.patch.user.PatchesWorkspaceRevisionProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.revision.patch.PatchPackageRevisionsResource;
import org.finos.legend.sdlc.server.resources.revision.patch.user.PatchesWorkspaceRevisionsResource;
import org.finos.legend.sdlc.server.resources.user.CurrentUserResource;
import org.finos.legend.sdlc.server.resources.dependency.project.DownstreamDependenciesResource;
import org.finos.legend.sdlc.server.resources.backup.project.group.GroupBackupWorkspaceEntitiesResource;
import org.finos.legend.sdlc.server.resources.backup.project.group.GroupBackupWorkspaceEntityPathsResource;
import org.finos.legend.sdlc.server.resources.backup.project.group.GroupBackupWorkspaceProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.backup.project.group.GroupBackupWorkspaceResource;
import org.finos.legend.sdlc.server.resources.backup.project.group.GroupBackupWorkspaceRevisionEntitiesResource;
import org.finos.legend.sdlc.server.resources.backup.project.group.GroupBackupWorkspaceRevisionEntityPathsResource;
import org.finos.legend.sdlc.server.resources.backup.project.group.GroupBackupWorkspaceRevisionProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.backup.project.group.GroupBackupWorkspaceRevisionsResource;
import org.finos.legend.sdlc.server.resources.comparison.project.group.GroupComparisonWorkspaceResource;
import org.finos.legend.sdlc.server.resources.conflictResolution.project.group.GroupConflictResolutionWorkspaceEntitiesResource;
import org.finos.legend.sdlc.server.resources.conflictResolution.project.group.GroupConflictResolutionWorkspaceEntityPathsResource;
import org.finos.legend.sdlc.server.resources.conflictResolution.project.group.GroupConflictResolutionWorkspaceProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.conflictResolution.project.group.GroupConflictResolutionWorkspaceResource;
import org.finos.legend.sdlc.server.resources.conflictResolution.project.group.GroupConflictResolutionWorkspaceRevisionEntitiesResource;
import org.finos.legend.sdlc.server.resources.conflictResolution.project.group.GroupConflictResolutionWorkspaceRevisionEntityPathsResource;
import org.finos.legend.sdlc.server.resources.conflictResolution.project.group.GroupConflictResolutionWorkspaceRevisionProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.conflictResolution.project.group.GroupConflictResolutionWorkspaceRevisionsResource;
import org.finos.legend.sdlc.server.resources.entity.project.group.GroupWorkspaceEntitiesResource;
import org.finos.legend.sdlc.server.resources.entity.project.group.GroupWorkspaceEntityChangesResource;
import org.finos.legend.sdlc.server.resources.entity.project.group.GroupWorkspaceEntityPathsResource;
import org.finos.legend.sdlc.server.resources.revision.project.group.GroupWorkspaceEntityRevisionsResource;
import org.finos.legend.sdlc.server.resources.revision.project.group.GroupWorkspacePackageRevisionsResource;
import org.finos.legend.sdlc.server.resources.project.project.group.GroupWorkspaceProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.pmcd.project.group.GroupWorkspacePureModelContextDataResource;
import org.finos.legend.sdlc.server.resources.dependency.project.group.GroupWorkspaceRevisionDependenciesResource;
import org.finos.legend.sdlc.server.resources.entity.project.group.GroupWorkspaceRevisionEntitiesResource;
import org.finos.legend.sdlc.server.resources.entity.project.group.GroupWorkspaceRevisionEntityPathsResource;
import org.finos.legend.sdlc.server.resources.project.project.group.GroupWorkspaceRevisionProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.pmcd.project.group.GroupWorkspaceRevisionPureModelContextDataResource;
import org.finos.legend.sdlc.server.resources.revision.project.group.GroupWorkspaceRevisionsResource;
import org.finos.legend.sdlc.server.resources.workflow.project.group.GroupWorkspaceWorkflowJobsResource;
import org.finos.legend.sdlc.server.resources.workflow.project.group.GroupWorkspaceWorkflowsResource;
import org.finos.legend.sdlc.server.resources.workspace.project.group.GroupWorkspacesResource;
import org.finos.legend.sdlc.server.resources.InfoResource;
import org.finos.legend.sdlc.server.resources.issue.IssuesResource;
import org.finos.legend.sdlc.server.resources.revision.patch.PatchEntityRevisionsResource;
import org.finos.legend.sdlc.server.resources.project.patch.group.PatchesGroupWorkspaceProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.workspace.patch.group.PatchesGroupWorkspacesResource;
import org.finos.legend.sdlc.server.resources.pmcd.patch.PatchPureModelContextDataResource;
import org.finos.legend.sdlc.server.resources.workflow.patch.PatchReviewWorkflowJobsResource;
import org.finos.legend.sdlc.server.resources.workflow.patch.PatchReviewWorkflowsResource;
import org.finos.legend.sdlc.server.resources.review.patch.PatchReviewsResource;
import org.finos.legend.sdlc.server.resources.dependency.patch.PatchRevisionDependenciesResource;
import org.finos.legend.sdlc.server.resources.pmcd.patch.PatchRevisionPureModelContextDataResource;
import org.finos.legend.sdlc.server.resources.revision.patch.PatchRevisionsResource;
import org.finos.legend.sdlc.server.resources.workflow.patch.PatchWorkflowJobsResource;
import org.finos.legend.sdlc.server.resources.workflow.patch.PatchWorkflowsResource;
import org.finos.legend.sdlc.server.resources.workspace.patch.user.PatchesWorkspacesResource;
import org.finos.legend.sdlc.server.resources.backup.patch.group.BackupPatchesGroupWorkspaceEntityPathsResource;
import org.finos.legend.sdlc.server.resources.backup.patch.group.BackupPatchesGroupWorkspaceProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.backup.patch.group.BackupPatchesGroupWorkspaceResource;
import org.finos.legend.sdlc.server.resources.backup.patch.group.BackupPatchesGroupWorkspaceRevisionEntitiesResource;
import org.finos.legend.sdlc.server.resources.backup.patch.group.BackupPatchesGroupWorkspaceRevisionProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.comparison.patch.group.PatchesGroupComparisonWorkspaceResource;
import org.finos.legend.sdlc.server.resources.conflictResolution.patch.group.ConflictResolutionPatchesGroupWorkspaceEntitiesResource;
import org.finos.legend.sdlc.server.resources.conflictResolution.patch.group.ConflictResolutionPatchesGroupWorkspaceEntityPathsResource;
import org.finos.legend.sdlc.server.resources.conflictResolution.patch.group.ConflictResolutionPatchesGroupWorkspaceResource;
import org.finos.legend.sdlc.server.resources.conflictResolution.patch.group.ConflictResolutionPatchesGroupWorkspaceRevisionEntityPathsResource;
import org.finos.legend.sdlc.server.resources.conflictResolution.patch.group.ConflictResolutionPatchesGroupWorkspaceRevisionsResource;
import org.finos.legend.sdlc.server.resources.entity.patch.group.PatchesGroupWorkspaceEntitiesResource;
import org.finos.legend.sdlc.server.resources.revision.patch.group.PatchesGroupWorkspaceEntityRevisionsResource;
import org.finos.legend.sdlc.server.resources.revision.patch.group.PatchesGroupWorkspacePackageRevisionsResource;
import org.finos.legend.sdlc.server.resources.pmcd.patch.group.PatchesGroupWorkspacePureModelContextDataResource;
import org.finos.legend.sdlc.server.resources.dependency.patch.group.PatchesGroupWorkspaceRevisionDependenciesResource;
import org.finos.legend.sdlc.server.resources.pmcd.patch.group.PatchesGroupWorkspaceRevisionPureModelContextDataResource;
import org.finos.legend.sdlc.server.resources.revision.patch.group.PatchesGroupWorkspaceRevisionsResource;
import org.finos.legend.sdlc.server.resources.workflow.patch.group.PatchesGroupWorkspaceWorkflowJobsResource;
import org.finos.legend.sdlc.server.resources.workflow.patch.group.PatchesGroupWorkspaceWorkflowsResource;
import org.finos.legend.sdlc.server.resources.project.patch.PatchProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.entity.patch.PatchesProjectEntitiesResource;
import org.finos.legend.sdlc.server.resources.entity.patch.PatchesProjectEntityPathsResource;
import org.finos.legend.sdlc.server.resources.patch.PatchesResource;
import org.finos.legend.sdlc.server.resources.entity.patch.user.PatchesWorkspaceEntitiesResource;
import org.finos.legend.sdlc.server.resources.pmcd.patch.user.PatchesWorkspacePureModelContextDataResource;
import org.finos.legend.sdlc.server.resources.dependency.patch.user.PatchesWorkspaceRevisionDependenciesResource;
import org.finos.legend.sdlc.server.resources.workflow.patch.user.PatchesWorkspaceWorkflowJobsResource;
import org.finos.legend.sdlc.server.resources.workflow.patch.user.PatchesWorkspaceWorkflowsResource;
import org.finos.legend.sdlc.server.resources.build.ProjectBuildsResource;
import org.finos.legend.sdlc.server.resources.project.project.ProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.entity.project.ProjectEntitiesResource;
import org.finos.legend.sdlc.server.resources.entity.project.ProjectEntityPathsResource;
import org.finos.legend.sdlc.server.resources.revision.project.ProjectEntityRevisionsResource;
import org.finos.legend.sdlc.server.resources.revision.project.ProjectPackageRevisionsResource;
import org.finos.legend.sdlc.server.resources.pmcd.project.ProjectPureModelContextDataResource;
import org.finos.legend.sdlc.server.resources.dependency.project.ProjectRevisionDependenciesResource;
import org.finos.legend.sdlc.server.resources.entity.project.ProjectRevisionEntitiesResource;
import org.finos.legend.sdlc.server.resources.entity.project.ProjectRevisionEntityPathsResource;
import org.finos.legend.sdlc.server.resources.project.project.ProjectRevisionProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.project.project.ProjectRevisionPureModelContextDataResource;
import org.finos.legend.sdlc.server.resources.revision.project.ProjectRevisionsResource;
import org.finos.legend.sdlc.server.resources.dependency.project.ProjectVersionDependenciesResource;
import org.finos.legend.sdlc.server.resources.workflow.project.ProjectWorkflowJobsResource;
import org.finos.legend.sdlc.server.resources.workflow.project.ProjectWorkflowsResource;
import org.finos.legend.sdlc.server.resources.project.project.ProjectsResource;
import org.finos.legend.sdlc.server.resources.workflow.ReviewWorkflowJobsResource;
import org.finos.legend.sdlc.server.resources.workflow.ReviewWorkflowsResource;
import org.finos.legend.sdlc.server.resources.review.ReviewsOnlyResource;
import org.finos.legend.sdlc.server.resources.review.project.ReviewsResource;
import org.finos.legend.sdlc.server.resources.ServerResource;
import org.finos.legend.sdlc.server.resources.user.UsersResource;
import org.finos.legend.sdlc.server.resources.build.VersionBuildsResource;
import org.finos.legend.sdlc.server.resources.entity.VersionEntitiesResource;
import org.finos.legend.sdlc.server.resources.entity.VersionEntityPathsResource;
import org.finos.legend.sdlc.server.resources.project.VersionProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.pmcd.VersionPureModelContextDataResource;
import org.finos.legend.sdlc.server.resources.workflow.VersionWorkflowJobsResource;
import org.finos.legend.sdlc.server.resources.workflow.VersionWorkflowsResource;
import org.finos.legend.sdlc.server.resources.version.VersionsResource;
import org.finos.legend.sdlc.server.resources.build.WorkspaceBuildsResource;
import org.finos.legend.sdlc.server.resources.entity.project.user.WorkspaceEntitiesResource;
import org.finos.legend.sdlc.server.resources.entity.project.user.WorkspaceEntityChangesResource;
import org.finos.legend.sdlc.server.resources.entity.project.user.WorkspaceEntityPathsResource;
import org.finos.legend.sdlc.server.resources.revision.project.user.WorkspaceEntityRevisionsResource;
import org.finos.legend.sdlc.server.resources.revision.project.user.WorkspacePackageRevisionsResource;
import org.finos.legend.sdlc.server.resources.project.project.user.WorkspaceProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.pmcd.project.user.WorkspacePureModelContextDataResource;
import org.finos.legend.sdlc.server.resources.dependency.project.user.WorkspaceRevisionDependenciesResource;
import org.finos.legend.sdlc.server.resources.entity.project.user.WorkspaceRevisionEntitiesResource;
import org.finos.legend.sdlc.server.resources.entity.project.user.WorkspaceRevisionEntityPathsResource;
import org.finos.legend.sdlc.server.resources.project.project.user.WorkspaceRevisionProjectConfigurationResource;
import org.finos.legend.sdlc.server.resources.pmcd.project.user.WorkspaceRevisionPureModelContextDataResource;
import org.finos.legend.sdlc.server.resources.revision.project.user.WorkspaceRevisionsResource;
import org.finos.legend.sdlc.server.resources.workflow.project.user.WorkspaceWorkflowJobsResource;
import org.finos.legend.sdlc.server.resources.workflow.project.user.WorkspaceWorkflowsResource;
import org.finos.legend.sdlc.server.resources.workspace.project.user.WorkspacesResource;
import org.finos.legend.sdlc.server.tools.BackgroundTaskProcessor;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.inject.Named;

public abstract class AbstractBaseModule extends DropwizardAwareModule<LegendSDLCServerConfiguration>
{
    protected final BaseLegendSDLCServer<?> server;
    protected ProjectStructureExtensionProvider extensionProvider;
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
        binder.bind(LegendSDLCServerFeaturesConfiguration.class).toProvider(this::getFeaturesConfiguration);
        binder.bind(BackgroundTaskProcessor.class).toProvider(this.server::getBackgroundTaskProcessor);
        binder.bind(ProjectStructurePlatformExtensions.class).toInstance(buildProjectStructurePlatformExtensions());

        bindResources(binder);
    }

    private void bindResources(Binder binder)
    {
        binder.bind(InfoResource.class);
        binder.bind(ServerResource.class);
        binder.bind(ProjectsResource.class);
        binder.bind(WorkspacesResource.class);
        binder.bind(ProjectConfigurationResource.class);
        binder.bind(ConfigurationResource.class);
        binder.bind(ProjectRevisionProjectConfigurationResource.class);
        binder.bind(WorkspaceProjectConfigurationResource.class);
        binder.bind(WorkspaceRevisionProjectConfigurationResource.class);
        binder.bind(ProjectEntitiesResource.class);
        binder.bind(ProjectPureModelContextDataResource.class);
        binder.bind(ProjectEntityPathsResource.class);
        binder.bind(ProjectRevisionEntitiesResource.class);
        binder.bind(ProjectRevisionPureModelContextDataResource.class);
        binder.bind(ProjectRevisionEntityPathsResource.class);
        binder.bind(WorkspaceEntitiesResource.class);
        binder.bind(WorkspacePureModelContextDataResource.class);
        binder.bind(WorkspaceEntityPathsResource.class);
        binder.bind(WorkspaceEntityChangesResource.class);
        binder.bind(WorkspaceRevisionEntitiesResource.class);
        binder.bind(WorkspaceRevisionPureModelContextDataResource.class);
        binder.bind(WorkspaceRevisionEntityPathsResource.class);
        binder.bind(VersionEntitiesResource.class);
        binder.bind(VersionPureModelContextDataResource.class);
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
        binder.bind(ReviewWorkflowsResource.class);
        binder.bind(ProjectWorkflowJobsResource.class);
        binder.bind(VersionWorkflowJobsResource.class);
        binder.bind(WorkspaceWorkflowJobsResource.class);
        binder.bind(ReviewWorkflowJobsResource.class);
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
        binder.bind(GroupWorkspacePureModelContextDataResource.class);
        binder.bind(GroupWorkspaceEntityChangesResource.class);
        binder.bind(GroupWorkspaceEntityPathsResource.class);
        binder.bind(GroupWorkspaceEntityRevisionsResource.class);
        binder.bind(GroupWorkspacePackageRevisionsResource.class);
        binder.bind(GroupWorkspaceProjectConfigurationResource.class);
        binder.bind(GroupWorkspaceRevisionDependenciesResource.class);
        binder.bind(GroupWorkspaceRevisionEntitiesResource.class);
        binder.bind(GroupWorkspaceRevisionPureModelContextDataResource.class);
        binder.bind(GroupWorkspaceRevisionEntityPathsResource.class);
        binder.bind(GroupWorkspaceRevisionProjectConfigurationResource.class);
        binder.bind(GroupWorkspaceRevisionsResource.class);
        binder.bind(GroupWorkspacesResource.class);
        binder.bind(GroupWorkspaceWorkflowJobsResource.class);
        binder.bind(GroupWorkspaceWorkflowsResource.class);
        binder.bind(PatchProjectConfigurationResource.class);
        binder.bind(PatchesResource.class);
        binder.bind(PatchesWorkspacesResource.class);
        binder.bind(PatchesGroupWorkspacesResource.class);
        binder.bind(PatchesGroupWorkspaceProjectConfigurationResource.class);
        binder.bind(PatchesProjectEntitiesResource.class);
        binder.bind(PatchesProjectEntityPathsResource.class);
        binder.bind(PatchesGroupWorkspaceEntitiesResource.class);
        binder.bind(PatchesWorkspaceEntitiesResource.class);
        binder.bind(PatchRevisionDependenciesResource.class);
        binder.bind(PatchesGroupWorkspaceRevisionDependenciesResource.class);
        binder.bind(PatchesWorkspaceRevisionDependenciesResource.class);
        binder.bind(ConflictResolutionPatchResource.class);
        binder.bind(ConflictResolutionPatchesWorkspaceEntitiesResource.class);
        binder.bind(ConflictResolutionPatchesWorkspaceEntityPathsResource.class);
        binder.bind(ConflictResolutionPatchesWorkspaceProjectConfigurationResource.class);
        binder.bind(ConflictResolutionPatchesWorkspaceResource.class);
        binder.bind(ConflictResolutionPatchesWorkspaceRevisionEntitiesResource.class);
        binder.bind(ConflictResolutionPatchesWorkspaceRevisionEntityPathsResource.class);
        binder.bind(ConflictResolutionPatchesWorkspaceRevisionProjectConfigurationResource.class);
        binder.bind(ConflictResolutionPatchesWorkspaceRevisionsResource.class);
        binder.bind(ConflictResolutionPatchesGroupWorkspaceEntitiesResource.class);
        binder.bind(ConflictResolutionPatchesGroupWorkspaceEntityPathsResource.class);
        binder.bind(ConflictResolutionPatchesGroupWorkspaceProjectConfigurationResource.class);
        binder.bind(ConflictResolutionPatchesGroupWorkspaceResource.class);
        binder.bind(ConflictResolutionPatchesGroupWorkspaceRevisionEntitiesResource.class);
        binder.bind(ConflictResolutionPatchesGroupWorkspaceRevisionEntityPathsResource.class);
        binder.bind(ConflictResolutionPatchesGroupWorkspaceRevisionProjectConfigurationResource.class);
        binder.bind(ConflictResolutionPatchesGroupWorkspaceRevisionsResource.class);
        binder.bind(PatchRevisionsResource.class);
        binder.bind(PatchPackageRevisionsResource.class);
        binder.bind(PatchEntityRevisionsResource.class);
        binder.bind(PatchesGroupWorkspaceEntityRevisionsResource.class);
        binder.bind(PatchesGroupWorkspaceRevisionsResource.class);
        binder.bind(PatchesGroupWorkspacePackageRevisionsResource.class);
        binder.bind(BackupPatchesWorkspaceEntitiesResource.class);
        binder.bind(BackupPatchesWorkspaceEntityPathsResource.class);
        binder.bind(BackupPatchesWorkspaceProjectConfigurationResource.class);
        binder.bind(BackupPatchesWorkspaceResource.class);
        binder.bind(BackupPatchesWorkspaceRevisionEntitiesResource.class);
        binder.bind(BackupPatchesWorkspaceRevisionEntityPathsResource.class);
        binder.bind(BackupPatchesWorkspaceRevisionProjectConfigurationResource.class);
        binder.bind(BackupPatchesWorkspaceRevisionsResource.class);
        binder.bind(BackupPatchesGroupWorkspaceEntitiesResource.class);
        binder.bind(BackupPatchesGroupWorkspaceEntityPathsResource.class);
        binder.bind(BackupPatchesGroupWorkspaceProjectConfigurationResource.class);
        binder.bind(BackupPatchesGroupWorkspaceResource.class);
        binder.bind(BackupPatchesGroupWorkspaceRevisionEntitiesResource.class);
        binder.bind(BackupPatchesGroupWorkspaceRevisionEntityPathsResource.class);
        binder.bind(BackupPatchesGroupWorkspaceRevisionProjectConfigurationResource.class);
        binder.bind(BackupPatchesGroupWorkspaceRevisionsResource.class);
        binder.bind(PatchesGroupWorkspaceRevisionPureModelContextDataResource.class);
        binder.bind(PatchesGroupWorkspacePureModelContextDataResource.class);
        binder.bind(PatchPureModelContextDataResource.class);
        binder.bind(PatchRevisionPureModelContextDataResource.class);
        binder.bind(PatchesWorkspacePureModelContextDataResource.class);
        binder.bind(PatchReviewsResource.class);
        binder.bind(ComparisonPatchReviewEntitiesResource.class);
        binder.bind(ComparisonPatchReviewProjectConfigurationResource.class);
        binder.bind(ComparisonPatchReviewResource.class);
        binder.bind(ComparisonPatchesWorkspaceResource.class);
        binder.bind(PatchesGroupComparisonWorkspaceResource.class);
        binder.bind(PatchesGroupWorkspaceWorkflowJobsResource.class);
        binder.bind(PatchesGroupWorkspaceWorkflowsResource.class);
        binder.bind(PatchesWorkspaceWorkflowsResource.class);
        binder.bind(PatchesWorkspaceWorkflowJobsResource.class);
        binder.bind(PatchWorkflowJobsResource.class);
        binder.bind(PatchWorkflowsResource.class);
        binder.bind(PatchReviewWorkflowJobsResource.class);
        binder.bind(PatchReviewWorkflowsResource.class);
        binder.bind(PatchesGroupWorkspaceEntityPathsResource.class);
        binder.bind(PatchesGroupWorkspaceEntityChangesResource.class);
        binder.bind(PatchesGroupWorkspaceRevisionEntitiesResource.class);
        binder.bind(PatchesGroupWorkspaceRevisionEntityPathsResource.class);
        binder.bind(PatchesWorkspaceEntityChangesResource.class);
        binder.bind(PatchesWorkspaceEntityPathsResource.class);
        binder.bind(PatchesWorkspaceRevisionEntitiesResource.class);
        binder.bind(PatchesWorkspaceRevisionEntityPathsResource.class);
        binder.bind(PatchesWorkspaceRevisionsResource.class);
        binder.bind(PatchesGroupWorkspaceRevisionProjectConfigurationResource.class);
        binder.bind(PatchesWorkspaceProjectConfigurationResource.class);
        binder.bind(PatchesWorkspaceRevisionProjectConfigurationResource.class);
    }

    private void configureCommonApis(Binder binder)
    {
        binder.bind(DependenciesApi.class).to(DependenciesApiImpl.class);
    }

    protected abstract void configureApis(Binder binder);

    private ProjectStructureConfiguration getProjectStructureConfiguration()
    {
        ProjectStructureConfiguration projectStructureConfiguration = getConfiguration().getProjectStructureConfiguration();
        return (projectStructureConfiguration == null) ? ProjectStructureConfiguration.emptyConfiguration() : projectStructureConfiguration;
    }

    private ProjectStructurePlatformExtensions buildProjectStructurePlatformExtensions()
    {
        return Optional.ofNullable(getConfiguration().getProjectStructureConfiguration())
                .map(ProjectStructureConfiguration::getProjectPlatformsConfiguration)
                .map(ProjectPlatformsConfiguration::buildProjectStructurePlatformExtensions)
                .orElseGet(() -> ProjectStructurePlatformExtensions.newPlatformExtensions(Collections.emptyList(), Collections.emptyList()));
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

    private LegendSDLCServerFeaturesConfiguration getFeaturesConfiguration()
    {
        LegendSDLCServerFeaturesConfiguration featuresConfiguration = getConfiguration().getFeaturesConfiguration();
        return (featuresConfiguration == null) ? LegendSDLCServerFeaturesConfiguration.emptyConfiguration() : featuresConfiguration;
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
