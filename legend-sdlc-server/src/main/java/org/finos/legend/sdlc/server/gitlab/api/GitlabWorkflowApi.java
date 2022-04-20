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

import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.revision.RevisionAlias;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.domain.model.workflow.Workflow;
import org.finos.legend.sdlc.domain.model.workflow.WorkflowStatus;
import org.finos.legend.sdlc.server.domain.api.workflow.WorkflowAccessContext;
import org.finos.legend.sdlc.server.domain.api.workflow.WorkflowApi;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.GitLabConfiguration;
import org.finos.legend.sdlc.server.gitlab.GitLabProjectId;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.gitlab.tools.PagerTools;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.tools.BackgroundTaskProcessor;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.MergeRequestApi;
import org.gitlab4j.api.Pager;
import org.gitlab4j.api.PipelineApi;
import org.gitlab4j.api.models.MergeRequest;
import org.gitlab4j.api.models.Pipeline;
import org.gitlab4j.api.models.PipelineStatus;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.ws.rs.core.Response;

public class GitlabWorkflowApi extends GitLabApiWithFileAccess implements WorkflowApi
{
    @Inject
    public GitlabWorkflowApi(GitLabConfiguration gitLabConfiguration, GitLabUserContext userContext, BackgroundTaskProcessor backgroundTaskProcessor)
    {
        super(gitLabConfiguration, userContext, backgroundTaskProcessor);
    }

    @Override
    public WorkflowAccessContext getProjectWorkflowAccessContext(String projectId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        return new RefWorkflowAccessContext(projectId, MASTER_BRANCH)
        {
            @Override
            protected String getInfoForException()
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
        return new RefWorkflowAccessContext(projectId, getBranchName(workspaceId, workspaceType, workspaceAccessType))
        {
            @Override
            protected String getInfoForException()
            {
                return workspaceType.getLabel() + " workspace " + workspaceId + " in project " + projectId;
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
        return new RefWorkflowAccessContext(projectId, buildVersionTagName(versionId))
        {
            @Override
            protected String getInfoForException()
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

    @Override
    public WorkflowAccessContext getReviewWorkflowAccessContext(String projectId, String reviewId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        MergeRequestApi mergeRequestApi = getGitLabApi().getMergeRequestApi();
        MergeRequest mergeRequest = getReviewMergeRequest(mergeRequestApi, gitLabProjectId, reviewId, true);
        WorkspaceInfo workspaceInfo = parseWorkspaceBranchName(mergeRequest.getSourceBranch());
        if (workspaceInfo == null)
        {
            throw new LegendSDLCServerException("Unknown review in project " + projectId + ": " + reviewId, Response.Status.NOT_FOUND);
        }

        return new GitLabWorkflowAccessContext(gitLabProjectId)
        {
            @Override
            protected Pipeline getPipeline(int pipelineId) throws GitLabApiException
            {
                return PagerTools.stream(getPipelines())
                        .filter(p -> (p.getId() != null) && (p.getId() == pipelineId))
                        .findAny()
                        .orElse(null);
            }

            @Override
            protected Pager<Pipeline> getPipelines() throws GitLabApiException
            {
                return withRetries(() -> mergeRequestApi.getMergeRequestPipelines(this.gitLabProjectId.getGitLabId(), mergeRequest.getIid(), ITEMS_PER_PAGE));
            }

            @Override
            protected String getInfoForException()
            {
                return "review " + reviewId + " of project " + projectId;
            }

            @Override
            protected ProjectFileAccessProvider.RevisionAccessContext getRevisionAccessContext()
            {
                return getProjectFileAccessProvider().getWorkspaceRevisionAccessContext(projectId, workspaceInfo.getWorkspaceId(), workspaceInfo.getWorkspaceType(), workspaceInfo.getWorkspaceAccessType());
            }
        };
    }

    private abstract class GitLabWorkflowAccessContext implements WorkflowAccessContext
    {
        protected final GitLabProjectId gitLabProjectId;

        protected GitLabWorkflowAccessContext(GitLabProjectId projectId)
        {
            this.gitLabProjectId = projectId;
        }

        protected GitLabWorkflowAccessContext(String projectId)
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
                pipeline = getPipeline(pipelineId);
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to access workflow " + workflowId + " in " + getInfoForException(),
                        () -> "Unknown workflow in " + getInfoForException() + ": " + workflowId,
                        () -> "Error getting workflow " + workflowId + " in " + getInfoForException());
            }

            if (pipeline == null)
            {
                throw new LegendSDLCServerException("Unknown workflow in " + getInfoForException() + ": " + workflowId, Response.Status.NOT_FOUND);
            }

            return fromGitLabPipeline(this.gitLabProjectId.toString(), pipeline);
        }

        @Override
        public List<Workflow> getWorkflows(Iterable<String> revisionIds, Iterable<WorkflowStatus> statuses, Integer limit)
        {
            if (limit != null)
            {
                if (limit == 0)
                {
                    return Collections.emptyList();
                }
                if (limit < 0)
                {
                    throw new LegendSDLCServerException("Invalid limit: " + limit, Response.Status.BAD_REQUEST);
                }
            }
            try
            {
                GitLabApi gitLabApi = getGitLabApi();
                Pager<Pipeline> pager = getPipelines();
                Stream<Pipeline> pipelineStream = PagerTools.stream(pager);

                // Filter by revision id, if provided
                if (revisionIds != null)
                {
                    MutableSet<String> revisionIdSet = computeRevisionIdSet(revisionIds);
                    if (revisionIdSet.notEmpty())
                    {
                        pipelineStream = pipelineStream.filter(pipeline -> revisionIdSet.contains(pipeline.getSha()));
                    }
                }

                // Filter by status, if provided
                if (statuses != null)
                {
                    Set<WorkflowStatus> statusSet = (statuses instanceof Set) ? (Set<WorkflowStatus>) statuses : Iterate.addAllTo(statuses, EnumSet.noneOf(WorkflowStatus.class));
                    if (!statusSet.isEmpty())
                    {
                        pipelineStream = pipelineStream.filter(pipeline -> statusSet.contains(fromGitLabPipelineStatus(pipeline.getStatus())));
                    }
                }

                // Limit results, if provided
                if (limit != null)
                {
                    pipelineStream = pipelineStream.limit(limit);
                }

                // Convert pipelines to workflows
                String projectIdString = this.gitLabProjectId.toString();
                return pipelineStream.map(p ->
                        {
                            if (p.getCreatedAt() == null)
                            {
                                try
                                {
                                    return withRetries(() -> gitLabApi.getPipelineApi().getPipeline(this.gitLabProjectId.getGitLabId(), p.getId()));
                                }
                                catch (Exception ignore)
                                {
                                    // ignore exception
                                }
                            }
                            return p;
                        })
                        .map(p -> fromGitLabPipeline(projectIdString, p))
                        .collect(PagerTools.listCollector(pager, limit));
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to access workflows for " + getInfoForException(),
                        () -> "Unknown " + getInfoForException(),
                        () -> "Error getting workflows for " + getInfoForException());
            }
        }

        protected abstract Pipeline getPipeline(int pipelineId) throws GitLabApiException;

        protected abstract Pager<Pipeline> getPipelines() throws GitLabApiException;

        protected abstract String getInfoForException();

        protected abstract ProjectFileAccessProvider.RevisionAccessContext getRevisionAccessContext();

        private MutableSet<String> computeRevisionIdSet(Iterable<String> revisionIds)
        {
            MutableSet<String> revisionIdSet = Sets.mutable.empty();
            Set<RevisionAlias> revisionAliases = EnumSet.noneOf(RevisionAlias.class);
            revisionIds.forEach(revisionId ->
            {
                RevisionAlias alias = getRevisionAlias(revisionId);
                if (alias == RevisionAlias.REVISION_ID)
                {
                    revisionIdSet.add(revisionId);
                }
                else
                {
                    revisionAliases.add(alias);
                }
            });
            if (!revisionAliases.isEmpty())
            {
                ProjectFileAccessProvider.RevisionAccessContext revisionAccessContext = getRevisionAccessContext();
                if (revisionAliases.contains(RevisionAlias.BASE))
                {
                    Revision baseRevision = revisionAccessContext.getBaseRevision();
                    if (baseRevision != null)
                    {
                        revisionIdSet.add(baseRevision.getId());
                    }
                }
                if (revisionAliases.contains(RevisionAlias.HEAD) || revisionAliases.contains(RevisionAlias.CURRENT) || revisionAliases.contains(RevisionAlias.LATEST))
                {
                    Revision currentRevision = revisionAccessContext.getCurrentRevision();
                    if (currentRevision != null)
                    {
                        revisionIdSet.add(currentRevision.getId());
                    }
                }
            }
            return revisionIdSet;
        }
    }

    private abstract class RefWorkflowAccessContext extends GitLabWorkflowAccessContext
    {
        private final String ref;

        private RefWorkflowAccessContext(String projectId, String ref)
        {
            super(projectId);
            this.ref = ref;
        }

        @Override
        protected Pipeline getPipeline(int pipelineId) throws GitLabApiException
        {
            PipelineApi pipelineApi = getGitLabApi().getPipelineApi();
            Pipeline pipeline = withRetries(() -> pipelineApi.getPipeline(this.gitLabProjectId.getGitLabId(), pipelineId));
            return ((pipeline != null) && this.ref.equals(pipeline.getRef())) ? pipeline : null;
        }

        @Override
        protected Pager<Pipeline> getPipelines() throws GitLabApiException
        {
            PipelineApi pipelineApi = getGitLabApi().getPipelineApi();
            return withRetries(() -> pipelineApi.getPipelines(this.gitLabProjectId.getGitLabId(), null, null, this.ref, false, null, null, null, null, ITEMS_PER_PAGE));
        }
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
