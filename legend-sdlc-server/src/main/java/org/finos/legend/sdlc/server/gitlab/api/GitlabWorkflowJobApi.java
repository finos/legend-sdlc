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

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.primitive.IntObjectMap;
import org.eclipse.collections.impl.utility.Iterate;
import org.eclipse.collections.impl.utility.ListIterate;
import org.finos.legend.sdlc.domain.model.workflow.WorkflowJob;
import org.finos.legend.sdlc.domain.model.workflow.WorkflowJobStatus;
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification;
import org.finos.legend.sdlc.server.domain.api.workflow.WorkflowJobAccessContext;
import org.finos.legend.sdlc.server.domain.api.workflow.WorkflowJobApi;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.GitLabConfiguration;
import org.finos.legend.sdlc.server.gitlab.GitLabProjectId;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.tools.BackgroundTaskProcessor;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.JobApi;
import org.gitlab4j.api.models.Job;
import org.gitlab4j.api.models.JobStatus;
import org.gitlab4j.api.models.MergeRequest;
import org.gitlab4j.api.models.Pipeline;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.ws.rs.core.Response.Status;

public class GitlabWorkflowJobApi extends AbstractGitlabWorkflowApi implements WorkflowJobApi
{
    @Inject
    public GitlabWorkflowJobApi(GitLabConfiguration gitLabConfiguration, GitLabUserContext userContext, BackgroundTaskProcessor backgroundTaskProcessor)
    {
        super(gitLabConfiguration, userContext, backgroundTaskProcessor);
    }

    @Override
    public WorkflowJobAccessContext getWorkflowJobAccessContext(String projectId, SourceSpecification sourceSpecification)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(sourceSpecification, "source specification may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        return new RefWorkflowJobAccessContext(gitLabProjectId, getBranchName(gitLabProjectId, sourceSpecification))
        {
            @Override
            protected String getInfoForException()
            {
                return getReferenceInfo(projectId, sourceSpecification);
            }
        };
    }

    @Override
    public WorkflowJobAccessContext getReviewWorkflowJobAccessContext(String projectId, String reviewId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        MergeRequest mergeRequest = getReviewMergeRequest(getGitLabApi().getMergeRequestApi(), gitLabProjectId, reviewId, true);

        return new GitLabWorkflowJobAccessContext(projectId)
        {
            private IntObjectMap<Pipeline> pipelinesById;

            @Override
            protected Pipeline getPipeline(int pipelineId) throws GitLabApiException
            {
                return getPipelinesById().get(pipelineId);
            }

            @Override
            protected Job getJob(int pipelineId, int jobId) throws GitLabApiException
            {
                Job job = super.getJob(pipelineId, jobId);
                return ((job != null) && (getPipeline(pipelineId) != null)) ? job : null;
            }

            @Override
            protected String getInfoForException()
            {
                return "review " + reviewId + " of project " + projectId;
            }

            private IntObjectMap<Pipeline> getPipelinesById() throws GitLabApiException
            {
                if (this.pipelinesById == null)
                {
                    this.pipelinesById = indexPipelinesById(getMergeRequestPipelines(this.gitLabProjectId.getGitLabId(), mergeRequest.getIid()), true, false);
                }
                return this.pipelinesById;
            }
        };
    }

    private abstract class GitLabWorkflowJobAccessContext implements WorkflowJobAccessContext
    {
        protected final GitLabProjectId gitLabProjectId;

        protected GitLabWorkflowJobAccessContext(GitLabProjectId projectId)
        {
            this.gitLabProjectId = projectId;
        }

        protected GitLabWorkflowJobAccessContext(String projectId)
        {
            this(parseProjectId(projectId));
        }

        @Override
        public WorkflowJob getWorkflowJob(String workflowId, String workflowJobId)
        {
            LegendSDLCServerException.validateNonNull(workflowId, "workflowId may not be null");
            LegendSDLCServerException.validateNonNull(workflowJobId, "workflowJobId may not be null");
            Job job = getJob(workflowId, workflowJobId);
            return fromGitLabJob(job);
        }

        @Override
        public List<WorkflowJob> getWorkflowJobs(String workflowId, Iterable<WorkflowJobStatus> statuses)
        {
            LegendSDLCServerException.validateNonNull(workflowId, "workflowId may not be null");

            int pipelineId = getWorkflowPipelineId(workflowId);

            JobApi jobApi = getGitLabApi().getJobApi();
            List<Job> jobs;
            try
            {
                jobs = withRetries(() -> jobApi.getJobsForPipeline(this.gitLabProjectId.getGitLabId(), pipelineId));
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to access jobs for workflow " + workflowId + " in " + getInfoForException(),
                        () -> "Unknown workflow " + workflowId + " in " + getInfoForException(),
                        () -> "Error getting jobs for workflow " + workflowId + " in " + getInfoForException());
            }
            MutableList<WorkflowJob> workflowJobs = ListIterate.collect(jobs, this::fromGitLabJob);
            if (statuses != null)
            {
                Set<WorkflowJobStatus> statusSet = (statuses instanceof Set) ? (Set<WorkflowJobStatus>) statuses : Iterate.addAllTo(statuses, EnumSet.noneOf(WorkflowJobStatus.class));
                if (!statusSet.isEmpty())
                {
                    workflowJobs.removeIf(job -> !statusSet.contains(job.getStatus()));
                }
            }
            return workflowJobs;
        }

        @Override
        public String getWorkflowJobLog(String workflowId, String workflowJobId)
        {
            LegendSDLCServerException.validateNonNull(workflowId, "workflowId may not be null");
            LegendSDLCServerException.validateNonNull(workflowJobId, "workflowJobId may not be null");

            Job job = getJob(workflowId, workflowJobId);
            JobApi jobApi = getGitLabApi().getJobApi();
            try
            {
                return withRetries(() -> jobApi.getTrace(this.gitLabProjectId.getGitLabId(), job.getId()));
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to access workflow job log for workflow job " + workflowJobId + " in " + getInfoForException(),
                        () -> "Unknown workflow job log in " + getInfoForException() + ": " + workflowJobId,
                        () -> "Error getting workflow job log for workflow job" + workflowJobId + " in " + getInfoForException());
            }
        }

        @Override
        public WorkflowJob runWorkflowJob(String workflowId, String workflowJobId)
        {
            LegendSDLCServerException.validateNonNull(workflowId, "workflowId may not be null");
            LegendSDLCServerException.validateNonNull(workflowJobId, "workflowJobId may not be null");

            Job job = getJob(workflowId, workflowJobId);
            WorkflowJobStatus status = fromGitLabJobStatus(job.getStatus());
            if (status != WorkflowJobStatus.WAITING_MANUAL)
            {
                throw new LegendSDLCServerException("Cannot run job " + workflowJobId + " of workflow " + workflowId + " in " + getInfoForException() + ": only waiting manual jobs can be run, found status " + ((status == null) ? "null" : status.name().toLowerCase()), Status.CONFLICT);
            }

            JobApi jobApi = getGitLabApi().getJobApi();
            Job result;
            try
            {
                result = withRetries(() -> jobApi.playJob(this.gitLabProjectId.getGitLabId(), job.getId()));
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to run job " + workflowJobId + " for workflow " + workflowId + " in " + getInfoForException(),
                        () -> "Unknown job in workflow " + workflowId + " of " + getInfoForException() + ": " + workflowJobId,
                        () -> "Error running job " + workflowJobId + " of workflow " + workflowId + " in " + getInfoForException());
            }
            return fromGitLabJob(result);
        }

        @Override
        public WorkflowJob retryWorkflowJob(String workflowId, String workflowJobId)
        {
            LegendSDLCServerException.validateNonNull(workflowId, "workflowId may not be null");
            LegendSDLCServerException.validateNonNull(workflowJobId, "workflowJobId may not be null");

            Job job = getJob(workflowId, workflowJobId);
            WorkflowJobStatus status = fromGitLabJobStatus(job.getStatus());
            if ((status != WorkflowJobStatus.FAILED) && (status != WorkflowJobStatus.CANCELED) && (status != WorkflowJobStatus.SUCCEEDED))
            {
                throw new LegendSDLCServerException("Cannot retry job " + workflowJobId + " of workflow " + workflowId + " in " + getInfoForException() + ": only succeeded, failed, or canceled jobs can be retried, found status " + ((status == null) ? "null" : status.name().toLowerCase()), Status.CONFLICT);
            }
            JobApi jobApi = getGitLabApi().getJobApi();
            Job result;
            try
            {
                result = withRetries(() -> jobApi.retryJob(this.gitLabProjectId.getGitLabId(), job.getId()));
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to retry job " + workflowJobId + " for workflow " + workflowId + " in " + getInfoForException(),
                        () -> "Unknown job in workflow " + workflowId + " of " + getInfoForException() + ": " + workflowJobId,
                        () -> "Error retrying job " + workflowJobId + " of workflow " + workflowId + " in " + getInfoForException());
            }
            return fromGitLabJob(result);
        }

        @Override
        public WorkflowJob cancelWorkflowJob(String workflowId, String workflowJobId)
        {
            LegendSDLCServerException.validateNonNull(workflowId, "workflowId may not be null");
            LegendSDLCServerException.validateNonNull(workflowJobId, "workflowJobId may not be null");

            Job job = getJob(workflowId, workflowJobId);
            WorkflowJobStatus status = fromGitLabJobStatus(job.getStatus());
            if (status != WorkflowJobStatus.IN_PROGRESS)
            {
                throw new LegendSDLCServerException("Cannot cancel job " + workflowJobId + " of workflow " + workflowId + " in " + getInfoForException() + ": only jobs in progress can be cancelled, found status " + ((status == null) ? "null" : status.name().toLowerCase()), Status.CONFLICT);
            }

            JobApi jobApi = getGitLabApi().getJobApi();
            Job result;
            try
            {
                result = withRetries(() -> jobApi.cancelJob(this.gitLabProjectId.getGitLabId(), job.getId()));
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to cancel job " + workflowJobId + " of workflow " + workflowId + " in " + getInfoForException(),
                        () -> "Unknown job in workflow " + workflowId + " of " + getInfoForException() + ": " + workflowJobId,
                        () -> "Error canceling job " + workflowJobId + " of workflow " + workflowId + " in " + getInfoForException());
            }
            return fromGitLabJob(result);
        }

        protected int getWorkflowPipelineId(String workflowId)
        {
            int pipelineId = parseIntegerId(workflowId);

            // Validate that the pipeline exists and is a workflow pipeline
            Pipeline pipeline;
            try
            {
                pipeline = getPipeline(pipelineId);
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to access jobs for workflow " + workflowId + " in " + getInfoForException(),
                        () -> "Unknown workflow " + workflowId + " in " + getInfoForException(),
                        () -> "Error getting jobs for workflow " + workflowId + " in " + getInfoForException());
            }
            if (pipeline == null)
            {
                throw new LegendSDLCServerException("Unknown workflow " + workflowId + " in " + getInfoForException(), Status.NOT_FOUND);
            }
            return pipelineId;
        }

        protected abstract Pipeline getPipeline(int pipelineId) throws GitLabApiException;

        protected Job getJob(String workflowId, String workflowJobId)
        {
            Job job;
            try
            {
                job = getJob(parseIntegerId(workflowId), parseIntegerId(workflowJobId));
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to access job " + workflowJobId + " of workflow " + workflowId + " in " + getInfoForException(),
                        () -> "Unknown job in workflow " + workflowId + " of " + getInfoForException() + ": " + workflowJobId,
                        () -> "Error getting workflow job " + workflowJobId + " of workflow " + workflowId + " in " + getInfoForException());
            }
            if (job == null)
            {
                throw new LegendSDLCServerException("Unknown job in workflow " + workflowId + " of " + getInfoForException() + ": " + workflowJobId, Status.NOT_FOUND);
            }
            return job;
        }

        protected Job getJob(int pipelineId, int jobId) throws GitLabApiException
        {
            JobApi jobApi = getGitLabApi().getJobApi();
            Job job = withRetries(() -> jobApi.getJob(this.gitLabProjectId.getGitLabId(), jobId));
            return ((job.getPipeline() != null) && (job.getPipeline().getId() != null) && (pipelineId == job.getPipeline().getId())) ? job : null;
        }

        protected WorkflowJob fromGitLabJob(Job job)
        {
            return GitlabWorkflowJobApi.fromGitLabJob(this.gitLabProjectId.toString(), job);
        }

        protected abstract String getInfoForException();
    }

    private abstract class RefWorkflowJobAccessContext extends GitLabWorkflowJobAccessContext
    {
        private final String ref;

        private RefWorkflowJobAccessContext(GitLabProjectId projectId, String ref)
        {
            super(projectId);
            this.ref = ref;
        }

        @Override
        protected Pipeline getPipeline(int pipelineId) throws GitLabApiException
        {
            return getRefPipeline(this.gitLabProjectId.getGitLabId(), this.ref, pipelineId);
        }

        @Override
        protected Job getJob(int pipelineId, int jobId) throws GitLabApiException
        {
            Job job = super.getJob(pipelineId, jobId);
            return ((job != null) && this.ref.equals(job.getRef())) ? job : null;
        }
    }

    private static WorkflowJob fromGitLabJob(String projectId, Job job)
    {
        String id = toStringIfNotNull(job.getId());
        String workflowId = toStringIfNotNull(job.getPipeline().getId());
        String name = toStringIfNotNull(job.getName());
        String revisionId = job.getPipeline().getSha();
        String webURL = job.getWebUrl();
        WorkflowJobStatus status = fromGitLabJobStatus(job.getStatus());
        Instant createdAt = toInstantIfNotNull(job.getCreatedAt());
        Instant startedAt = toInstantIfNotNull(job.getStartedAt());
        Instant finishedAt = toInstantIfNotNull(job.getFinishedAt());
        return new WorkflowJob()
        {
            @Override
            public String getId()
            {
                return id;
            }

            @Override
            public String getWorkflowId()
            {
                return workflowId;
            }

            @Override
            public String getName()
            {
                return name;
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
            public WorkflowJobStatus getStatus()
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

    private static WorkflowJobStatus fromGitLabJobStatus(JobStatus jobStatus)
    {
        if (jobStatus == null)
        {
            return null;
        }
        switch (jobStatus)
        {
            case CREATED:
            case PENDING:
            {
                return WorkflowJobStatus.WAITING;
            }
            case RUNNING:
            {
                return WorkflowJobStatus.IN_PROGRESS;
            }
            case SUCCESS:
            {
                return WorkflowJobStatus.SUCCEEDED;
            }
            case FAILED:
            {
                return WorkflowJobStatus.FAILED;
            }
            case SKIPPED:
            {
                return WorkflowJobStatus.SKIPPED;
            }
            case CANCELED:
            {
                return WorkflowJobStatus.CANCELED;
            }
            case MANUAL:
            {
                return WorkflowJobStatus.WAITING_MANUAL;
            }
            default:
            {
                return WorkflowJobStatus.UNKNOWN;
            }
        }
    }
}
