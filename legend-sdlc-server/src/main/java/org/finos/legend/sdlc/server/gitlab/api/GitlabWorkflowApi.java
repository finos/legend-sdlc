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

package org.finos.legend.sdlc.server.gitlab.api;

import org.finos.legend.sdlc.domain.model.revision.RevisionAlias;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.domain.model.workflow.Workflow;
import org.finos.legend.sdlc.domain.model.workflow.WorkflowStatus;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.server.domain.api.workflow.WorkflowAccessContext;
import org.finos.legend.sdlc.server.domain.api.workflow.WorkflowApi;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.GitLabProjectId;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.gitlab.tools.PagerTools;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.tools.BackgroundTaskProcessor;
import org.gitlab4j.api.Pager;
import org.gitlab4j.api.PipelineApi;
import org.gitlab4j.api.models.Pipeline;
import org.gitlab4j.api.models.PipelineStatus;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class GitlabWorkflowApi extends GitLabApiWithFileAccess implements WorkflowApi
{
    @Inject
    public GitlabWorkflowApi(GitLabUserContext userContext, BackgroundTaskProcessor backgroundTaskProcessor)
    {
        super(userContext, backgroundTaskProcessor);
    }

    @Override
    public WorkflowAccessContext getProjectWorkflowAccessContext(String projectId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        return new GitLabWorkflowAccessContext(projectId)
        {
            @Override
            protected String getRef()
            {
                return MASTER_BRANCH;
            }

            @Override
            protected String getRefInfoForException()
            {
                return "project " + projectId;
            }

            @Override
            protected ProjectFileAccessProvider.RevisionAccessContext getRevisionAccessContext()
            {
                return getProjectFileAccessProvider().getProjectRevisionAccessContext(projectId);
            }
        };
    }

    @Override
    public WorkflowAccessContext getWorkspaceWorkflowAccessContext(String projectId, String workspaceId, WorkspaceType workspaceType, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceId, "workspaceId may not be null");
        return new GitLabWorkflowAccessContext(projectId)
        {
            @Override
            protected String getRef()
            {
                return getBranchName(workspaceId, workspaceType, workspaceAccessType);
            }

            @Override
            protected String getRefInfoForException()
            {
                return "workspace " + workspaceId + " in project " + projectId;
            }

            @Override
            protected ProjectFileAccessProvider.RevisionAccessContext getRevisionAccessContext()
            {
                return getProjectFileAccessProvider().getWorkspaceRevisionAccessContext(projectId, workspaceId, workspaceType, workspaceAccessType);
            }
        };
    }

    @Override
    public WorkflowAccessContext getVersionWorkflowAccessContext(String projectId, VersionId versionId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(versionId, "versionId may not be null");
        return new GitLabWorkflowAccessContext(projectId)
        {
            @Override
            protected String getRef()
            {
                return buildVersionTagName(versionId);
            }

            @Override
            protected String getRefInfoForException()
            {
                return "version " + versionId.toVersionIdString() + " of project " + projectId;
            }

            @Override
            protected ProjectFileAccessProvider.RevisionAccessContext getRevisionAccessContext()
            {
                return getProjectFileAccessProvider().getVersionRevisionAccessContext(projectId, versionId);
            }
        };
    }

    private abstract class GitLabWorkflowAccessContext implements WorkflowAccessContext
    {
        private final GitLabProjectId projectId;

        private GitLabWorkflowAccessContext(GitLabProjectId projectId)
        {
            this.projectId = projectId;
        }

        private GitLabWorkflowAccessContext(String projectId)
        {
            this(parseProjectId(projectId));
        }

        @Override
        public Workflow getWorkflow(String workflowId)
        {
            int pipelineId = parseIntegerIdIfNotNull(workflowId);
            Pipeline pipeline;
            try
            {
                pipeline = withRetries(() -> getGitLabApi(this.projectId.getGitLabMode()).getPipelineApi().getPipeline(this.projectId.getGitLabId(), pipelineId));
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to access workflow " + workflowId + " in " + getRefInfoForException(),
                        () -> "Unknown workflow in " + getRefInfoForException() + ": " + workflowId,
                        () -> "Error getting workflow " + workflowId + " in " + getRefInfoForException());
            }

            if (!getRef().equals(pipeline.getRef()))
            {
                throw new LegendSDLCServerException("Unknown workflow in " + getRefInfoForException() + ": " + workflowId, Response.Status.NOT_FOUND);
            }

            return fromGitLabPipeline(this.projectId.toString(), pipeline);
        }

        @Override
        public List<Workflow> getWorkflows(Iterable<String> revisionIds, Iterable<WorkflowStatus> statuses, Integer limit)
        {
            try
            {
                boolean limited = (limit != null) && (limit > 0);
                int itemsPerPage = limited ? Math.min(limit, ITEMS_PER_PAGE) : ITEMS_PER_PAGE;
                PipelineApi pipelineApi = getGitLabApi(this.projectId.getGitLabMode()).getPipelineApi();
                Pager<Pipeline> pager = withRetries(() -> pipelineApi.getPipelines(this.projectId.getGitLabId(), null, null, getRef(), false, null, null, null, null, itemsPerPage));
                Stream<Pipeline> pipelineStream = PagerTools.stream(pager);
                Set<String> revisionIdSet = (revisionIds == null)
                        ? Collections.emptySet()
                        : StreamSupport.stream(revisionIds.spliterator(), false)
                        // make sure various synonymous aliases are grouped together properly
                        .filter(Objects::nonNull).map(revisionId ->
                        {
                            RevisionAlias alias = getRevisionAlias(revisionId);
                            return (alias == RevisionAlias.REVISION_ID) ? revisionId : alias.getValue();
                        })
                        .distinct()
                        // resolve revision alias when possible
                        .map(revisionId -> resolveRevisionId(revisionId, getRevisionAccessContext()))
                        .collect(Collectors.toSet());
                if (!revisionIdSet.isEmpty())
                {
                    pipelineStream = pipelineStream.filter(pipeline -> revisionIdSet.contains(pipeline.getSha()));
                }

                Set<WorkflowStatus> statusSet = (statuses == null) ? Collections.emptySet() : ((statuses instanceof Set) ? (Set<WorkflowStatus>) statuses : StreamSupport.stream(statuses.spliterator(), false).collect(Collectors.toCollection(() -> EnumSet.noneOf(WorkflowStatus.class))));
                if (!statusSet.isEmpty())
                {
                    pipelineStream = pipelineStream.filter(pipeline -> statusSet.contains(fromGitLabPipelineStatus(pipeline.getStatus())));
                }

                if (limited)
                {
                    pipelineStream = pipelineStream.limit(limit);
                }

                return pipelineStream
                        .map(p ->
                        {
                            if (p.getCreatedAt() == null)
                            {
                                try
                                {
                                    return withRetries(() -> pipelineApi.getPipeline(this.projectId.getGitLabId(), p.getId()));
                                }
                                catch (Exception ignore)
                                {
                                    // ignore exception
                                }
                            }
                            return p;
                        })
                        .map(p -> fromGitLabPipeline(this.projectId.toString(), p))
                        .collect(PagerTools.listCollector(pager, limit));
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to access workflows for " + getRefInfoForException(),
                        () -> "Unknown " + getRefInfoForException(),
                        () -> "Error getting workflows for " + getRefInfoForException());
            }
        }

        protected abstract String getRef();

        protected abstract String getRefInfoForException();

        protected abstract ProjectFileAccessProvider.RevisionAccessContext getRevisionAccessContext();
    }

    private static Workflow fromGitLabPipeline(String projectId, Pipeline pipeline)
    {
        String id = toStringIfNotNull(pipeline.getId());
        String revisionId = pipeline.getSha();
        String webURL = pipeline.getWebUrl();
        WorkflowStatus status = fromGitLabPipelineStatus(pipeline.getStatus());
        Instant createdAt = toInstantIfNotNull(pipeline.getCreatedAt());
        Instant startedAt = toInstantIfNotNull(pipeline.getStartedAt());
        Instant finishedAt = toInstantIfNotNull(pipeline.getFinishedAt());
        return new Workflow()
        {
            @Override
            public String getId()
            {
                return id;
            }

            @Override
            public String getProjectId()
            {
                return projectId;
            }

            @Override
            public String getRevisionId()
            {
                return revisionId;
            }

            @Override
            public WorkflowStatus getStatus()
            {
                return status;
            }

            @Override
            public Instant getCreatedAt()
            {
                return createdAt;
            }

            @Override
            public Instant getStartedAt()
            {
                return startedAt;
            }

            @Override
            public Instant getFinishedAt()
            {
                return finishedAt;
            }

            @Override
            public String getWebURL()
            {
                return webURL;
            }
        };
    }

    private static WorkflowStatus fromGitLabPipelineStatus(PipelineStatus pipelineStatus)
    {
        if (pipelineStatus == null)
        {
            return null;
        }
        switch (pipelineStatus)
        {
            case PENDING:
            {
                return WorkflowStatus.PENDING;
            }
            case RUNNING:
            {
                return WorkflowStatus.IN_PROGRESS;
            }
            case SUCCESS:
            {
                return WorkflowStatus.SUCCEEDED;
            }
            case FAILED:
            {
                return WorkflowStatus.FAILED;
            }
            case SKIPPED:
            case CANCELED:
            {
                return WorkflowStatus.CANCELED;
            }
            default:
            {
                return WorkflowStatus.UNKNOWN;
            }
        }
    }
}
