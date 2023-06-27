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
import com.google.inject.Scopes;
import org.finos.legend.sdlc.server.BaseLegendSDLCServer;
import org.finos.legend.sdlc.server.config.LegendSDLCServerConfiguration;
import org.finos.legend.sdlc.server.depot.api.DepotMetadataApi;
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
import org.finos.legend.sdlc.server.gitlab.GitLabAppInfo;
import org.finos.legend.sdlc.server.gitlab.GitLabConfiguration;
import org.finos.legend.sdlc.server.gitlab.api.GitLabBackupApi;
import org.finos.legend.sdlc.server.gitlab.api.GitLabBuildApi;
import org.finos.legend.sdlc.server.gitlab.api.GitLabComparisonApi;
import org.finos.legend.sdlc.server.gitlab.api.GitLabConflictResolutionApi;
import org.finos.legend.sdlc.server.gitlab.api.GitLabEntityApi;
import org.finos.legend.sdlc.server.gitlab.api.GitLabIssueApi;
import org.finos.legend.sdlc.server.gitlab.api.GitLabProjectApi;
import org.finos.legend.sdlc.server.gitlab.api.GitLabProjectConfigurationApi;
import org.finos.legend.sdlc.server.gitlab.api.GitLabReviewApi;
import org.finos.legend.sdlc.server.gitlab.api.GitLabRevisionApi;
import org.finos.legend.sdlc.server.gitlab.api.GitLabUserApi;
import org.finos.legend.sdlc.server.gitlab.api.GitLabVersionApi;
import org.finos.legend.sdlc.server.gitlab.api.GitLabWorkspaceApi;
import org.finos.legend.sdlc.server.gitlab.api.GitLabPatchApi;
import org.finos.legend.sdlc.server.gitlab.api.GitlabWorkflowApi;
import org.finos.legend.sdlc.server.gitlab.api.GitlabWorkflowJobApi;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabAuthorizer;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabAuthorizerManager;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.gitlab.resources.GitLabAuthCheckResource;
import org.finos.legend.sdlc.server.gitlab.resources.GitLabAuthResource;

import java.util.Collections;
import java.util.List;

public class BaseModule extends AbstractBaseModule
{
    public BaseModule(BaseLegendSDLCServer<?> server)
    {
        super(server);
    }

    @Override
    protected void configureApis(Binder binder)
    {
        if (BaseLegendSDLCServer.GITLAB_MODE.equals(this.server.getMode()))
        {
            binder.bind(ProjectApi.class).to(GitLabProjectApi.class);
            binder.bind(ProjectConfigurationApi.class).to(GitLabProjectConfigurationApi.class);
            binder.bind(UserApi.class).to(GitLabUserApi.class);
            binder.bind(IssueApi.class).to(GitLabIssueApi.class);
            binder.bind(EntityApi.class).to(GitLabEntityApi.class);
            binder.bind(WorkspaceApi.class).to(GitLabWorkspaceApi.class);
            binder.bind(PatchApi.class).to(GitLabPatchApi.class);
            binder.bind(RevisionApi.class).to(GitLabRevisionApi.class);
            binder.bind(ReviewApi.class).to(GitLabReviewApi.class);
            binder.bind(BuildApi.class).to(GitLabBuildApi.class);
            binder.bind(VersionApi.class).to(GitLabVersionApi.class);
            binder.bind(ComparisonApi.class).to(GitLabComparisonApi.class);
            binder.bind(ConflictResolutionApi.class).to(GitLabConflictResolutionApi.class);
            binder.bind(BackupApi.class).to(GitLabBackupApi.class);
            binder.bind(WorkflowApi.class).to(GitlabWorkflowApi.class);
            binder.bind(WorkflowJobApi.class).to(GitlabWorkflowJobApi.class);
            binder.bind(GitLabUserContext.class);
            binder.bind(GitLabAuthResource.class);
            binder.bind(GitLabAuthCheckResource.class);
            binder.bind(GitLabConfiguration.class).toProvider(() -> getConfiguration().getGitLabConfiguration());
            binder.bind(GitLabAppInfo.class).toProvider(() -> GitLabAppInfo.newAppInfo(getConfiguration().getGitLabConfiguration()));
            binder.bind(GitLabAuthorizerManager.class).toProvider(() -> this.provideGitLabAuthorizerManager(getConfiguration())).in(Scopes.SINGLETON);
        }
        configureMetadataApi(binder);
    }

    protected void configureMetadataApi(Binder binder)
    {
        binder.bind(MetadataApi.class).to(DepotMetadataApi.class);
    }

    private GitLabAuthorizerManager provideGitLabAuthorizerManager(LegendSDLCServerConfiguration configuration)
    {
        List<GitLabAuthorizer> gitLabAuthorizers = configuration.getGitLabConfiguration().getGitLabAuthorizers();
        if (gitLabAuthorizers == null)
        {
            return GitLabAuthorizerManager.newManager(Collections.emptyList());
        }
        else
        {
            return GitLabAuthorizerManager.newManager(gitLabAuthorizers);
        }
    }
}
