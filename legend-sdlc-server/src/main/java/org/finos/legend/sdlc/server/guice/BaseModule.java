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
import com.google.inject.Scopes;
import com.google.inject.servlet.RequestScoped;
import org.finos.legend.sdlc.backend.api.backup.BackupApi;
import org.finos.legend.sdlc.backend.api.build.BuildApi;
import org.finos.legend.sdlc.backend.api.comparison.ComparisonApi;
import org.finos.legend.sdlc.backend.api.conflictresolution.ConflictResolutionApi;
import org.finos.legend.sdlc.backend.api.entity.EntityApi;
import org.finos.legend.sdlc.backend.api.issue.IssueApi;
import org.finos.legend.sdlc.backend.api.patch.PatchApi;
import org.finos.legend.sdlc.backend.api.project.ProjectApi;
import org.finos.legend.sdlc.backend.api.project.ProjectConfigurationApi;
import org.finos.legend.sdlc.backend.api.review.ReviewApi;
import org.finos.legend.sdlc.backend.api.revision.RevisionApi;
import org.finos.legend.sdlc.backend.api.spi.Backend;
import org.finos.legend.sdlc.backend.api.spi.BackendSession;
import org.finos.legend.sdlc.backend.api.user.UserApi;
import org.finos.legend.sdlc.backend.api.version.VersionApi;
import org.finos.legend.sdlc.backend.api.workflow.WorkflowApi;
import org.finos.legend.sdlc.backend.api.workflow.WorkflowJobApi;
import org.finos.legend.sdlc.backend.api.workspace.WorkspaceApi;
import org.finos.legend.sdlc.server.BaseLegendSDLCServer;
import org.finos.legend.sdlc.server.backend.ServletBackendSessionContext;
import org.finos.legend.sdlc.server.config.LegendSDLCServerConfiguration;
import org.finos.legend.sdlc.server.depot.api.DepotMetadataApi;
import org.finos.legend.sdlc.server.depot.api.MetadataApi;
import org.finos.legend.sdlc.server.gitlab.GitLabAppInfo;
import org.finos.legend.sdlc.server.gitlab.GitLabConfiguration;
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
        if (getConfiguration().getGitLabConfiguration() != null)
        {
            binder.bind(GitLabUserContext.class);
            binder.bind(GitLabAuthResource.class);
            binder.bind(GitLabAuthCheckResource.class);
            binder.bind(GitLabConfiguration.class).toProvider(() -> getConfiguration().getGitLabConfiguration());
            binder.bind(GitLabAppInfo.class).toProvider(() -> GitLabAppInfo.newAppInfo(getConfiguration().getGitLabConfiguration()));
            binder.bind(GitLabAuthorizerManager.class).toProvider(() -> this.provideGitLabAuthorizerManager(getConfiguration())).in(Scopes.SINGLETON);
        }
        configureMetadataApi(binder);
    }

    @Override
    protected void bindUserContext(Binder binder)
    {
        // The backend session context wraps the UserContext; in a GitLab deployment it must be the GitLab
        // user context (the GitLab backend unwraps it until its extraction re-plumbs auth through the SPI)
        if (getConfiguration().getGitLabConfiguration() != null)
        {
            binder.bind(UserContext.class).to(GitLabUserContext.class);
        }
        else
        {
            super.bindUserContext(binder);
        }
    }

    protected void configureMetadataApi(Binder binder)
    {
        binder.bind(MetadataApi.class).to(DepotMetadataApi.class);
    }

    @Provides
    @RequestScoped
    public BackendSession provideBackendSession(Backend backend, UserContext userContext)
    {
        return backend.newSession(new ServletBackendSessionContext(userContext));
    }

    @Provides
    public ProjectApi provideProjectApi(BackendSession session)
    {
        return session.getProjectApi();
    }

    @Provides
    public ProjectConfigurationApi provideProjectConfigurationApi(BackendSession session)
    {
        return session.getProjectConfigurationApi();
    }

    @Provides
    public WorkspaceApi provideWorkspaceApi(BackendSession session)
    {
        return session.getWorkspaceApi();
    }

    @Provides
    public RevisionApi provideRevisionApi(BackendSession session)
    {
        return session.getRevisionApi();
    }

    @Provides
    public EntityApi provideEntityApi(BackendSession session)
    {
        return session.getEntityApi();
    }

    @Provides
    public ComparisonApi provideComparisonApi(BackendSession session)
    {
        return session.getComparisonApi();
    }

    @Provides
    public UserApi provideUserApi(BackendSession session)
    {
        return session.getUserApi();
    }

    @Provides
    public ReviewApi provideReviewApi(BackendSession session)
    {
        return session.getReviewApi();
    }

    @Provides
    public VersionApi provideVersionApi(BackendSession session)
    {
        return session.getVersionApi();
    }

    @Provides
    public PatchApi providePatchApi(BackendSession session)
    {
        return session.getPatchApi();
    }

    @Provides
    public WorkflowApi provideWorkflowApi(BackendSession session)
    {
        return session.getWorkflowApi();
    }

    @Provides
    public WorkflowJobApi provideWorkflowJobApi(BackendSession session)
    {
        return session.getWorkflowJobApi();
    }

    @Provides
    public BuildApi provideBuildApi(BackendSession session)
    {
        return session.getBuildApi();
    }

    @Provides
    public BackupApi provideBackupApi(BackendSession session)
    {
        return session.getBackupApi();
    }

    @Provides
    public ConflictResolutionApi provideConflictResolutionApi(BackendSession session)
    {
        return session.getConflictResolutionApi();
    }

    @Provides
    public IssueApi provideIssueApi(BackendSession session)
    {
        return session.getIssueApi();
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
