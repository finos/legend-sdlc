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

package org.finos.legend.sdlc.backend.gitlab;

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
import org.finos.legend.sdlc.backend.gitlab.api.GitLabBackupApi;
import org.finos.legend.sdlc.backend.gitlab.api.GitLabBuildApi;
import org.finos.legend.sdlc.backend.gitlab.api.GitLabComparisonApi;
import org.finos.legend.sdlc.backend.gitlab.api.GitLabConflictResolutionApi;
import org.finos.legend.sdlc.backend.gitlab.api.GitLabEntityApi;
import org.finos.legend.sdlc.backend.gitlab.api.GitLabIssueApi;
import org.finos.legend.sdlc.backend.gitlab.api.GitLabPatchApi;
import org.finos.legend.sdlc.backend.gitlab.api.GitLabProjectApi;
import org.finos.legend.sdlc.backend.gitlab.api.GitLabProjectConfigurationApi;
import org.finos.legend.sdlc.backend.gitlab.api.GitLabReviewApi;
import org.finos.legend.sdlc.backend.gitlab.api.GitLabRevisionApi;
import org.finos.legend.sdlc.backend.gitlab.api.GitLabUserApi;
import org.finos.legend.sdlc.backend.gitlab.api.GitLabVersionApi;
import org.finos.legend.sdlc.backend.gitlab.api.GitLabWorkspaceApi;
import org.finos.legend.sdlc.backend.gitlab.api.GitlabWorkflowApi;
import org.finos.legend.sdlc.backend.gitlab.api.GitlabWorkflowJobApi;
import org.finos.legend.sdlc.backend.gitlab.auth.GitLabAuthAccessException;
import org.finos.legend.sdlc.backend.gitlab.auth.GitLabAuthorizerManager;
import org.finos.legend.sdlc.backend.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.backend.gitlab.tools.GitLabApiTools;
import org.finos.legend.sdlc.tools.StringTools;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The GitLab {@code Backend}. Per-user state (OAuth tokens) crosses the SPI through the session context's state
 * store; interactive authorization crosses back as {@code AuthorizationRequiredException}. Nothing here touches
 * the host's frameworks.
 */
public class GitLabBackend extends AbstractBackend
{
    private static final Logger LOGGER = LoggerFactory.getLogger(GitLabBackend.class);

    private static final Pattern TERMS_OF_SERVICE_MESSAGE_PATTERN = Pattern.compile("terms\\s++of\\s++service", Pattern.CASE_INSENSITIVE);

    private final GitLabConfiguration gitLabConfiguration;
    private final GitLabAppInfo appInfo;
    private final GitLabAuthorizerManager authorizerManager;

    public GitLabBackend(GitLabConfiguration gitLabConfiguration, BackendEnvironment environment)
    {
        super(GitLabBackendFactory.TYPE, EnumSet.allOf(BackendCapability.class), environment);
        this.gitLabConfiguration = Objects.requireNonNull(gitLabConfiguration, "gitLabConfiguration may not be null");
        this.appInfo = GitLabAppInfo.newAppInfo(gitLabConfiguration);
        this.authorizerManager = GitLabAuthorizerManager.newManager(gitLabConfiguration.getGitLabAuthorizers());
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
            this.userContext = new GitLabUserContext(context, GitLabBackend.this.authorizerManager, GitLabBackend.this.appInfo);
        }

        @Override
        protected ProjectFileAccessProvider getProjectFileAccessProvider()
        {
            return newEntityApi().getProjectFileAccessProvider();
        }

        @Override
        public ProjectApi getProjectApi()
        {
            return new GitLabProjectApi(GitLabBackend.this.gitLabConfiguration, this.userContext, getEnvironment().getProjectCreationConfiguration(), getEnvironment().getProjectStructureExtensionProvider(), getEnvironment().getTaskProcessor(), getEnvironment().getProjectStructurePlatformExtensions());
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
            try
            {
                return this.userContext.isUserAuthorized();
            }
            catch (GitLabAuthAccessException e)
            {
                // an error accessing the auth server means we cannot establish authorization
                LOGGER.error("Access exception occurred while checking authorization", e);
                return false;
            }
        }

        @Override
        public void authorize()
        {
            this.userContext.getGitLabAPI();
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
                int errorStatus;
                if (e instanceof GitLabApiException)
                {
                    switch (((GitLabApiException) e).getHttpStatus())
                    {
                        case 403:
                        {
                            String message = e.getMessage();
                            if ((message != null) && TERMS_OF_SERVICE_MESSAGE_PATTERN.matcher(message).find())
                            {
                                // error indicates terms of service need to be accepted
                                return Collections.singleton(api.getGitLabServerUrl());
                            }
                            errorStatus = 403;
                            break;
                        }
                        case 401:
                        {
                            errorStatus = 403;
                            break;
                        }
                        default:
                        {
                            errorStatus = 500;
                        }
                    }
                }
                else
                {
                    errorStatus = 500;
                }
                throw new LegendSDLCException(StringTools.appendThrowableMessageIfPresent("Error checking acceptance of terms of service", e), errorStatus, e);
            }
        }

        private GitLabEntityApi newEntityApi()
        {
            return new GitLabEntityApi(GitLabBackend.this.gitLabConfiguration, this.userContext, getEnvironment().getTaskProcessor());
        }
    }
}
