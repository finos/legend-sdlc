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

package org.finos.legend.sdlc.server.guice;

import com.google.inject.Binder;
import org.finos.legend.sdlc.server.BaseLegendSDLCServer;
import org.finos.legend.sdlc.server.api.backup.FileSystemBackupApi;
import org.finos.legend.sdlc.server.api.build.FileSystemBuildApi;
import org.finos.legend.sdlc.server.api.comparison.FileSystemComparisonApi;
import org.finos.legend.sdlc.server.api.conflictresolution.FileSystemConflictResolutionApi;
import org.finos.legend.sdlc.server.api.entity.FileSystemEntityApi;
import org.finos.legend.sdlc.server.api.issue.FileSystemIssueApi;
import org.finos.legend.sdlc.server.api.patch.FileSystemPatchApi;
import org.finos.legend.sdlc.server.api.project.FileSystemProjectApi;
import org.finos.legend.sdlc.server.api.project.FileSystemProjectConfigurationApi;
import org.finos.legend.sdlc.server.api.review.FileSystemReviewApi;
import org.finos.legend.sdlc.server.api.revision.FileSystemRevisionApi;
import org.finos.legend.sdlc.server.api.user.FileSystemUserApi;
import org.finos.legend.sdlc.server.api.version.FileSystemVersionApi;
import org.finos.legend.sdlc.server.api.workflow.FileSystemWorkflowApi;
import org.finos.legend.sdlc.server.api.workflow.FileSystemWorkflowJobApi;
import org.finos.legend.sdlc.server.api.workspace.FileSystemWorkspaceApi;
import org.finos.legend.sdlc.server.depot.FileSystemMetadataApi;
import org.finos.legend.sdlc.server.depot.api.MetadataApi;
import org.finos.legend.sdlc.server.domain.api.backup.BackupApi;
import org.finos.legend.sdlc.server.domain.api.build.BuildApi;
import org.finos.legend.sdlc.server.domain.api.comparison.ComparisonApi;
import org.finos.legend.sdlc.server.domain.api.conflictResolution.ConflictResolutionApi;
import org.finos.legend.sdlc.server.domain.api.entity.EntityApi;
import org.finos.legend.sdlc.server.domain.api.issue.IssueApi;
import org.finos.legend.sdlc.server.domain.api.patch.PatchApi;
import org.finos.legend.sdlc.server.domain.api.project.ProjectApi;
import org.finos.legend.sdlc.server.domain.api.project.ProjectConfigurationApi;
import org.finos.legend.sdlc.server.domain.api.review.ReviewApi;
import org.finos.legend.sdlc.server.domain.api.revision.RevisionApi;
import org.finos.legend.sdlc.server.domain.api.user.UserApi;
import org.finos.legend.sdlc.server.domain.api.version.VersionApi;
import org.finos.legend.sdlc.server.domain.api.workflow.WorkflowApi;
import org.finos.legend.sdlc.server.domain.api.workflow.WorkflowJobApi;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceApi;
import org.finos.legend.sdlc.server.resources.FileSystemAuthCheckResource;
import org.finos.legend.sdlc.server.resources.FileSystemAuthResource;

public class FileSystemModule extends AbstractBaseModule
{
    public FileSystemModule(BaseLegendSDLCServer<?> server)
    {
        super(server);
    }

    @Override
    protected void configureApis(Binder binder)
    {
        configureLegendApis(binder);
    }

    public void configureLegendApis(Binder binder)
    {
        binder.bind(MetadataApi.class).to(FileSystemMetadataApi.class);
        binder.bind(ProjectApi.class).to(FileSystemProjectApi.class);
        binder.bind(ProjectConfigurationApi.class).to(FileSystemProjectConfigurationApi.class);
        binder.bind(UserApi.class).to(FileSystemUserApi.class);
        binder.bind(IssueApi.class).to(FileSystemIssueApi.class);
        binder.bind(EntityApi.class).to(FileSystemEntityApi.class);
        binder.bind(WorkspaceApi.class).to(FileSystemWorkspaceApi.class);
        binder.bind(RevisionApi.class).to(FileSystemRevisionApi.class);
        binder.bind(ReviewApi.class).to(FileSystemReviewApi.class);
        binder.bind(BuildApi.class).to(FileSystemBuildApi.class);
        binder.bind(VersionApi.class).to(FileSystemVersionApi.class);
        binder.bind(ComparisonApi.class).to(FileSystemComparisonApi.class);
        binder.bind(ConflictResolutionApi.class).to(FileSystemConflictResolutionApi.class);
        binder.bind(BackupApi.class).to(FileSystemBackupApi.class);
        binder.bind(WorkflowApi.class).to(FileSystemWorkflowApi.class);
        binder.bind(WorkflowJobApi.class).to(FileSystemWorkflowJobApi.class);
        binder.bind(PatchApi.class).to(FileSystemPatchApi.class);
        binder.bind(FileSystemAuthCheckResource.class);
        binder.bind(FileSystemAuthResource.class);
    }
}
