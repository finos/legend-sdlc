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

package org.finos.legend.sdlc.server.backend.simple.guice;

import com.google.inject.Binder;
import com.google.inject.Scopes;
import org.finos.legend.sdlc.domain.model.project.Project;
import org.finos.legend.sdlc.server.BaseLegendSDLCServer;
import org.finos.legend.sdlc.server.backend.simple.api.backup.SimpleBackendBackupApi;
import org.finos.legend.sdlc.server.backend.simple.api.build.SimpleBackendBuildApi;
import org.finos.legend.sdlc.server.backend.simple.api.comparison.SimpleBackendComparisonApi;
import org.finos.legend.sdlc.server.backend.simple.api.conflictresolution.SimpleBackendConflictResolutionApi;
import org.finos.legend.sdlc.server.backend.simple.api.entity.SimpleBackendEntityApi;
import org.finos.legend.sdlc.server.backend.simple.api.issue.SimpleBackendIssueApi;
import org.finos.legend.sdlc.server.backend.simple.api.project.SimpleBackendProjectApi;
import org.finos.legend.sdlc.server.backend.simple.api.project.SimpleBackendProjectConfigurationApi;
import org.finos.legend.sdlc.server.backend.simple.api.review.SimpleBackendReviewApi;
import org.finos.legend.sdlc.server.backend.simple.api.revision.SimpleBackendRevisionApi;
import org.finos.legend.sdlc.server.backend.simple.api.user.SimpleBackendUserApi;
import org.finos.legend.sdlc.server.backend.simple.api.version.SimpleBackendVersionApi;
import org.finos.legend.sdlc.server.backend.simple.api.workflow.SimpleBackendWorkflowApi;
import org.finos.legend.sdlc.server.backend.simple.api.workflow.SimpleBackendWorkflowJobApi;
import org.finos.legend.sdlc.server.backend.simple.api.workspace.SimpleBackendWorkspaceApi;
import org.finos.legend.sdlc.server.backend.simple.depot.SimpleBackendMetadataApi;
import org.finos.legend.sdlc.server.backend.simple.domain.model.project.SimpleBackendProject;
import org.finos.legend.sdlc.server.resources.backend.simple.SimpleBackendAuthCheckResource;
import org.finos.legend.sdlc.server.resources.backend.simple.SimpleBackendAuthResource;
import org.finos.legend.sdlc.server.backend.simple.state.SimpleBackendState;
import org.finos.legend.sdlc.server.depot.api.MetadataApi;
import org.finos.legend.sdlc.server.domain.api.backup.BackupApi;
import org.finos.legend.sdlc.server.domain.api.build.BuildApi;
import org.finos.legend.sdlc.server.domain.api.comparison.ComparisonApi;
import org.finos.legend.sdlc.server.domain.api.conflictResolution.ConflictResolutionApi;
import org.finos.legend.sdlc.server.domain.api.entity.EntityApi;
import org.finos.legend.sdlc.server.domain.api.issue.IssueApi;
import org.finos.legend.sdlc.server.domain.api.project.ProjectApi;
import org.finos.legend.sdlc.server.domain.api.project.ProjectConfigurationApi;
import org.finos.legend.sdlc.server.domain.api.review.ReviewApi;
import org.finos.legend.sdlc.server.domain.api.revision.RevisionApi;
import org.finos.legend.sdlc.server.domain.api.user.UserApi;
import org.finos.legend.sdlc.server.domain.api.version.VersionApi;
import org.finos.legend.sdlc.server.domain.api.workflow.WorkflowApi;
import org.finos.legend.sdlc.server.domain.api.workflow.WorkflowJobApi;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceApi;
import org.finos.legend.sdlc.server.guice.AbstractBaseModule;

public class SimpleBackendModule extends AbstractBaseModule
{
    public SimpleBackendModule(BaseLegendSDLCServer<?> server)
    {
        super(server);
    }

    @Override
    protected void configureApis(Binder binder)
    {
        configureLegendApis(binder);
    }

    public static void configureLegendApis(Binder binder)
    {
        binder.bind(SimpleBackendState.class).in(Scopes.SINGLETON);
        binder.bind(MetadataApi.class).to(SimpleBackendMetadataApi.class);
        binder.bind(ProjectApi.class).to(SimpleBackendProjectApi.class);
        binder.bind(ProjectConfigurationApi.class).to(SimpleBackendProjectConfigurationApi.class);
        binder.bind(UserApi.class).to(SimpleBackendUserApi.class);
        binder.bind(IssueApi.class).to(SimpleBackendIssueApi.class);
        binder.bind(EntityApi.class).to(SimpleBackendEntityApi.class);
        binder.bind(WorkspaceApi.class).to(SimpleBackendWorkspaceApi.class);
        binder.bind(RevisionApi.class).to(SimpleBackendRevisionApi.class);
        binder.bind(ReviewApi.class).to(SimpleBackendReviewApi.class);
        binder.bind(BuildApi.class).to(SimpleBackendBuildApi.class);
        binder.bind(VersionApi.class).to(SimpleBackendVersionApi.class);
        binder.bind(ComparisonApi.class).to(SimpleBackendComparisonApi.class);
        binder.bind(ConflictResolutionApi.class).to(SimpleBackendConflictResolutionApi.class);
        binder.bind(BackupApi.class).to(SimpleBackendBackupApi.class);
        binder.bind(WorkflowApi.class).to(SimpleBackendWorkflowApi.class);
        binder.bind(WorkflowJobApi.class).to(SimpleBackendWorkflowJobApi.class);
        binder.bind(SimpleBackendAuthCheckResource.class);
        binder.bind(SimpleBackendAuthResource.class);
        binder.bind(Project.class).to(SimpleBackendProject.class);
    }
}
