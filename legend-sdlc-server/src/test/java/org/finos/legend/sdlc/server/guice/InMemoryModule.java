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
import com.google.inject.Scopes;
import org.finos.legend.sdlc.domain.model.patch.Patch;
import org.finos.legend.sdlc.domain.model.project.Project;
import org.finos.legend.sdlc.server.BaseLegendSDLCServer;
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
import org.finos.legend.sdlc.server.inmemory.backend.InMemoryBackend;
import org.finos.legend.sdlc.server.inmemory.backend.api.InMemoryBackupApi;
import org.finos.legend.sdlc.server.inmemory.backend.api.InMemoryBuildApi;
import org.finos.legend.sdlc.server.inmemory.backend.api.InMemoryComparisonApi;
import org.finos.legend.sdlc.server.inmemory.backend.api.InMemoryConflictResolutionApi;
import org.finos.legend.sdlc.server.inmemory.backend.api.InMemoryEntityApi;
import org.finos.legend.sdlc.server.inmemory.backend.api.InMemoryIssueApi;
import org.finos.legend.sdlc.server.inmemory.backend.api.InMemoryPatchApi;
import org.finos.legend.sdlc.server.inmemory.backend.api.InMemoryProjectApi;
import org.finos.legend.sdlc.server.inmemory.backend.api.InMemoryProjectConfigurationApi;
import org.finos.legend.sdlc.server.inmemory.backend.api.InMemoryReviewApi;
import org.finos.legend.sdlc.server.inmemory.backend.api.InMemoryRevisionApi;
import org.finos.legend.sdlc.server.inmemory.backend.api.InMemoryUserApi;
import org.finos.legend.sdlc.server.inmemory.backend.api.InMemoryVersionApi;
import org.finos.legend.sdlc.server.inmemory.backend.api.InMemoryWorkflowApi;
import org.finos.legend.sdlc.server.inmemory.backend.api.InMemoryWorkflowJobApi;
import org.finos.legend.sdlc.server.inmemory.backend.api.InMemoryWorkspaceApi;
import org.finos.legend.sdlc.server.inmemory.backend.metadata.InMemoryMetadataApi;
import org.finos.legend.sdlc.server.inmemory.backend.metadata.InMemoryMetadataBackend;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryPatch;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryProject;

public class InMemoryModule extends AbstractBaseModule
{
    public InMemoryModule(BaseLegendSDLCServer<?> server)
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
        binder.bind(InMemoryBackend.class);
        binder.bind(InMemoryMetadataBackend.class).in(Scopes.SINGLETON);
        binder.bind(MetadataApi.class).to(InMemoryMetadataApi.class);
        binder.bind(ProjectApi.class).to(InMemoryProjectApi.class);
        binder.bind(ProjectConfigurationApi.class).to(InMemoryProjectConfigurationApi.class);
        binder.bind(UserApi.class).to(InMemoryUserApi.class);
        binder.bind(IssueApi.class).to(InMemoryIssueApi.class);
        binder.bind(EntityApi.class).to(InMemoryEntityApi.class);
        binder.bind(WorkspaceApi.class).to(InMemoryWorkspaceApi.class);
        binder.bind(RevisionApi.class).to(InMemoryRevisionApi.class);
        binder.bind(ReviewApi.class).to(InMemoryReviewApi.class);
        binder.bind(BuildApi.class).to(InMemoryBuildApi.class);
        binder.bind(VersionApi.class).to(InMemoryVersionApi.class);
        binder.bind(ComparisonApi.class).to(InMemoryComparisonApi.class);
        binder.bind(ConflictResolutionApi.class).to(InMemoryConflictResolutionApi.class);
        binder.bind(BackupApi.class).to(InMemoryBackupApi.class);
        binder.bind(WorkflowApi.class).to(InMemoryWorkflowApi.class);
        binder.bind(PatchApi.class).to(InMemoryPatchApi.class);
        binder.bind(WorkflowJobApi.class).to(InMemoryWorkflowJobApi.class);

        binder.bind(Project.class).to(InMemoryProject.class);
        binder.bind(Patch.class).to(InMemoryPatch.class);
    }
}
