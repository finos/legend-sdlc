// Copyright 2026 Goldman Sachs
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

package org.finos.legend.sdlc.server.gitlab;

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
import org.finos.legend.sdlc.backend.api.spi.AbstractBackend;
import org.finos.legend.sdlc.backend.api.spi.BackendCapability;
import org.finos.legend.sdlc.backend.api.spi.BackendEnvironment;
import org.finos.legend.sdlc.backend.api.spi.BackendSession;
import org.finos.legend.sdlc.backend.api.spi.BackendSessionContext;
import org.finos.legend.sdlc.backend.api.user.UserApi;
import org.finos.legend.sdlc.backend.api.version.VersionApi;
import org.finos.legend.sdlc.backend.api.workflow.WorkflowApi;
import org.finos.legend.sdlc.backend.api.workflow.WorkflowJobApi;
import org.finos.legend.sdlc.backend.api.workspace.WorkspaceApi;
import org.finos.legend.sdlc.error.LegendSDLCException;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.backend.ServletBackendSessionContext;
import org.finos.legend.sdlc.server.gitlab.api.GitLabBackupApi;
import org.finos.legend.sdlc.server.gitlab.api.GitLabBuildApi;
import org.finos.legend.sdlc.server.gitlab.api.GitLabComparisonApi;
import org.finos.legend.sdlc.server.gitlab.api.GitLabConflictResolutionApi;
import org.finos.legend.sdlc.server.gitlab.api.GitLabEntityApi;
import org.finos.legend.sdlc.server.gitlab.api.GitLabIssueApi;
import org.finos.legend.sdlc.server.gitlab.api.GitLabPatchApi;
import org.finos.legend.sdlc.server.gitlab.api.GitLabProjectApi;
import org.finos.legend.sdlc.server.gitlab.api.GitLabProjectConfigurationApi;
import org.finos.legend.sdlc.server.gitlab.api.GitLabReviewApi;
import org.finos.legend.sdlc.server.gitlab.api.GitLabRevisionApi;
import org.finos.legend.sdlc.server.gitlab.api.GitLabUserApi;
import org.finos.legend.sdlc.server.gitlab.api.GitLabVersionApi;
import org.finos.legend.sdlc.server.gitlab.api.GitLabWorkspaceApi;
import org.finos.legend.sdlc.server.gitlab.api.GitlabWorkflowApi;
import org.finos.legend.sdlc.server.gitlab.api.GitlabWorkflowJobApi;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.gitlab.tools.GitLabApiTools;
import org.finos.legend.sdlc.server.guice.UserContext;
import org.finos.legend.sdlc.server.project.config.ProjectStructureConfiguration;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The GitLab {@code Backend}. In this phase it lives in the server module and reaches its servlet-bound auth
 * machinery by unwrapping the {@link ServletBackendSessionContext}; when it is extracted to its own module, the
 * per-user token state moves onto the SPI's session state store and this unwrapping disappears.
 */
public class GitLabBackend extends AbstractBackend
{
    private static final Pattern TERMS_OF_SERVICE_MESSAGE_PATTERN = Pattern.compile("terms\\s++of\\s++service", Pattern.CASE_INSENSITIVE);

    private final GitLabConfiguration gitLabConfiguration;
    private final ProjectStructureConfiguration projectStructureConfiguration;

    public GitLabBackend(GitLabConfiguration gitLabConfiguration, ProjectStructureConfiguration projectStructureConfiguration, BackendEnvironment environment)
    {
        super(GitLabBackendFactory.TYPE, EnumSet.allOf(BackendCapability.class), environment);
        this.gitLabConfiguration = Objects.requireNonNull(gitLabConfiguration, "gitLabConfiguration may not be null");
        this.projectStructureConfiguration = (projectStructureConfiguration == null) ? ProjectStructureConfiguration.emptyConfiguration() : projectStructureConfiguration;
    }

    @Override
    public BackendSession newSession(BackendSessionContext context)
    {
        return new Session(context);
    }

    public class Session extends AbstractBackend.Session
    {
        private final GitLabUserContext userContext;

        Session(BackendSessionContext context)
        {
            super(context);
            if (!(context instanceof ServletBackendSessionContext))
            {
                throw new IllegalArgumentException("The GitLab backend currently requires the server's session context; got: " + context.getClass().getName());
            }
            UserContext serverUserContext = ((ServletBackendSessionContext) context).getServerUserContext();
            if (!(serverUserContext instanceof GitLabUserContext))
            {
                throw new IllegalArgumentException("The GitLab backend requires a GitLab user context; got: " + serverUserContext.getClass().getName());
            }
            this.userContext = (GitLabUserContext) serverUserContext;
        }

        @Override
        protected ProjectFileAccessProvider getProjectFileAccessProvider()
        {
            return newEntityApi().getProjectFileAccessProvider();
        }

        @Override
        public ProjectApi getProjectApi()
        {
            return new GitLabProjectApi(GitLabBackend.this.gitLabConfiguration, this.userContext, GitLabBackend.this.projectStructureConfiguration, getEnvironment().getProjectStructureExtensionProvider(), getEnvironment().getTaskProcessor(), getEnvironment().getProjectStructurePlatformExtensions());
        }

        @Override
        public ProjectConfigurationApi getProjectConfigurationApi()
        {
            return new GitLabProjectConfigurationApi(GitLabBackend.this.gitLabConfiguration, this.userContext, getEnvironment().getProjectStructureExtensionProvider(), getEnvironment().getTaskProcessor(), getEnvironment().getProjectStructurePlatformExtensions());
        }

        @Override
        public WorkspaceApi getWorkspaceApi()
        {
            return new GitLabWorkspaceApi(GitLabBackend.this.gitLabConfiguration, this.userContext, getProjectApi(), getRevisionApi(), getEnvironment().getTaskProcessor());
        }

        @Override
        public RevisionApi getRevisionApi()
        {
            return new GitLabRevisionApi(GitLabBackend.this.gitLabConfiguration, this.userContext, getEnvironment().getTaskProcessor());
        }

        @Override
        public EntityApi getEntityApi()
        {
            return newEntityApi();
        }

        @Override
        public ComparisonApi getComparisonApi()
        {
            return new GitLabComparisonApi(GitLabBackend.this.gitLabConfiguration, this.userContext, getEnvironment().getTaskProcessor());
        }

        @Override
        public UserApi getUserApi()
        {
            return new GitLabUserApi(GitLabBackend.this.gitLabConfiguration, this.userContext);
        }

        @Override
        public ReviewApi getReviewApi()
        {
            return new GitLabReviewApi(GitLabBackend.this.gitLabConfiguration, this.userContext, getEnvironment().getTaskProcessor());
        }

        @Override
        public VersionApi getVersionApi()
        {
            return new GitLabVersionApi(GitLabBackend.this.gitLabConfiguration, this.userContext, getEnvironment().getTaskProcessor());
        }

        @Override
        public PatchApi getPatchApi()
        {
            return new GitLabPatchApi(GitLabBackend.this.gitLabConfiguration, this.userContext, getEnvironment().getTaskProcessor());
        }

        @Override
        public WorkflowApi getWorkflowApi()
        {
            return new GitlabWorkflowApi(GitLabBackend.this.gitLabConfiguration, this.userContext, getEnvironment().getTaskProcessor());
        }

        @Override
        public WorkflowJobApi getWorkflowJobApi()
        {
            return new GitlabWorkflowJobApi(GitLabBackend.this.gitLabConfiguration, this.userContext, getEnvironment().getTaskProcessor());
        }

        @Override
        public BuildApi getBuildApi()
        {
            return new GitLabBuildApi(GitLabBackend.this.gitLabConfiguration, this.userContext, getEnvironment().getTaskProcessor());
        }

        @Override
        public BackupApi getBackupApi()
        {
            return new GitLabBackupApi(GitLabBackend.this.gitLabConfiguration, this.userContext, getEnvironment().getTaskProcessor());
        }

        @Override
        public ConflictResolutionApi getConflictResolutionApi()
        {
            return new GitLabConflictResolutionApi(GitLabBackend.this.gitLabConfiguration, this.userContext, getEntityApi(), getEnvironment().getTaskProcessor());
        }

        @Override
        public IssueApi getIssueApi()
        {
            return new GitLabIssueApi(GitLabBackend.this.gitLabConfiguration, this.userContext);
        }

        @Override
        public boolean isAuthorized()
        {
            return this.userContext.isUserAuthorized();
        }

        @Override
        public void authorize()
        {
            this.userContext.getGitLabAPI(true);
        }

        @Override
        public void handleAuthorizationCallback(String code, String state)
        {
            this.userContext.gitLabAuthCallback(code);
        }

        @Override
        public Set<String> getUnacceptedTermsOfService()
        {
            GitLabApi api = this.userContext.getGitLabAPI();
            try
            {
                GitLabApiTools.callWithRetries(() -> api.getUserApi().getCurrentUser(), 5, 1000);
                return Collections.emptySet();
            }
            catch (Exception e)
            {
                if ((e instanceof GitLabApiException) && (((GitLabApiException) e).getHttpStatus() == 403))
                {
                    String message = e.getMessage();
                    if ((message != null) && TERMS_OF_SERVICE_MESSAGE_PATTERN.matcher(message).find())
                    {
                        return Collections.singleton(api.getGitLabServerUrl());
                    }
                }
                if (e instanceof LegendSDLCException)
                {
                    throw (LegendSDLCException) e;
                }
                throw new LegendSDLCException("Failed to check terms of service acceptance", 500, e);
            }
        }

        private GitLabEntityApi newEntityApi()
        {
            return new GitLabEntityApi(GitLabBackend.this.gitLabConfiguration, this.userContext, getEnvironment().getTaskProcessor());
        }
    }
}
