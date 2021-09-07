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
import org.eclipse.collections.impl.utility.Iterate;
import org.eclipse.collections.impl.utility.ListIterate;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.domain.model.workflow.WorkflowJob;
import org.finos.legend.sdlc.domain.model.workflow.WorkflowJobStatus;
import org.finos.legend.sdlc.server.domain.api.workflow.WorkflowJobAccessContext;
import org.finos.legend.sdlc.server.domain.api.workflow.WorkflowJobApi;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.GitLabProjectId;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.tools.BackgroundTaskProcessor;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.JobApi;
import org.gitlab4j.api.PipelineApi;
import org.gitlab4j.api.models.Job;
import org.gitlab4j.api.models.JobStatus;
import org.gitlab4j.api.models.Pipeline;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.ws.rs.core.Response.Status;

public class GitlabWorkflowJobApi extends GitLabApiWithFileAccess implements WorkflowJobApi
{
    @Inject
    public GitlabWorkflowJobApi(GitLabUserContext userContext, BackgroundTaskProcessor backgroundTaskProcessor)
    {
        super(userContext, backgroundTaskProcessor);
    }

    @Override
    public WorkflowJobAccessContext getProjectWorkflowJobAccessContext(String projectId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        return new GitLabWorkflowJobAccessContext(projectId)
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
        };
    }

    @Override
    public WorkflowJobAccessContext getWorkspaceWorkflowJobAccessContext(String projectId, String workspaceId, WorkspaceType workspaceType, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceId, "workspaceId may not be null");
        return new GitLabWorkflowJobAccessContext(projectId)
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
        };
    }

    @Override
    public WorkflowJobAccessContext getVersionWorkflowJobAccessContext(String projectId, VersionId versionId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(versionId, "versionId may not be null");
        return new GitLabWorkflowJobAccessContext(projectId)
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
        };
    }

    private abstract class GitLabWorkflowJobAccessContext implements WorkflowJobAccessContext
    {
        private final GitLabProjectId projectId;

        private GitLabWorkflowJobAccessContext(GitLabProjectId projectId)
        {
            this.projectId = projectId;
        }

        private GitLabWorkflowJobAccessContext(String projectId)
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

            Set<WorkflowJobStatus> statusSet = (statuses == null) ? Collections.emptySet() : ((statuses instanceof Set) ? (Set<WorkflowJobStatus>) statuses : Iterate.addAllTo(statuses, EnumSet.noneOf(WorkflowJobStatus.class)));

            int pipelineId = parseIntegerIdIfNotNull(workflowId);
            GitLabApi gitLabApi = getGitLabApi(this.projectId.getGitLabMode());
            PipelineApi pipelineApi = gitLabApi.getPipelineApi();

            // Validate the pipeline
            Pipeline pipeline;
            try
            {
                pipeline = withRetries(() -> pipelineApi.getPipeline(this.projectId.getGitLabId(), pipelineId));
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to access jobs for workflow " + workflowId + " in " + getRefInfoForException(),
                        () -> "Unknown workflow " + workflowId + " in " + getRefInfoForException(),
                        () -> "Error getting jobs for workflow " + workflowId + " in " + getRefInfoForException());
            }

            if (!getRef().equals(pipeline.getRef()))
            {
                throw new LegendSDLCServerException("Unknown workflow " + workflowId + " in " + getRefInfoForException(), Status.NOT_FOUND);
            }

            // Get the jobs
            JobApi jobApi = gitLabApi.getJobApi();
            List<Job> jobs;
            try
            {
                jobs = withRetries(() -> jobApi.getJobsForPipeline(this.projectId.getGitLabId(), pipelineId));
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to access jobs for workflow " + workflowId + " in " + getRefInfoForException(),
                        () -> "Unknown workflow " + workflowId + " in " + getRefInfoForException(),
                        () -> "Error getting jobs for workflow " + workflowId + " in " + getRefInfoForException());
            }
            MutableList<WorkflowJob> workflowJobs = ListIterate.collect(jobs, this::fromGitLabJob);
            if (!statusSet.isEmpty())
            {
                workflowJobs.removeIf(job -> !statusSet.contains(job.getStatus()));
            }
            return workflowJobs;
        }

        @Override
        public String getWorkflowJobLog(String workflowId, String workflowJobId)
        {
            LegendSDLCServerException.validateNonNull(workflowId, "workflowId may not be null");
            LegendSDLCServerException.validateNonNull(workflowJobId, "workflowJobId may not be null");

            Job job = getJob(workflowId, workflowJobId);
            JobApi jobApi = getGitLabApi(this.projectId.getGitLabMode()).getJobApi();
            try
            {
                return withRetries(() -> jobApi.getTrace(this.projectId.getGitLabId(), job.getId()));
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to access workflow job log for workflow job " + workflowJobId + " in " + getRefInfoForException(),
                        () -> "Unknown workflow job log in " + getRefInfoForException() + ": " + workflowJobId,
                        () -> "Error getting workflow job log for workflow job" + workflowJobId + " in " + getRefInfoForException());
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
                throw new LegendSDLCServerException("Cannot run job " + workflowJobId + " of workflow " + workflowId + " in " + getRefInfoForException() + ": only waiting manual jobs can be run, found status " + ((status == null) ? "null" : status.name().toLowerCase()), Status.CONFLICT);
            }

            JobApi jobApi = getGitLabApi(this.projectId.getGitLabMode()).getJobApi();
            Job result;
            try
            {
                result = withRetries(() -> jobApi.playJob(this.projectId.getGitLabId(), job.getId()));
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to run job " + workflowJobId + " for workflow " + workflowId + " in " + getRefInfoForException(),
                        () -> "Unknown job in workflow " + workflowId + " of " + getRefInfoForException() + ": " + workflowJobId,
                        () -> "Error running job " + workflowJobId + " of workflow " + workflowId + " in " + getRefInfoForException());
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
                throw new LegendSDLCServerException("Cannot retry job " + workflowJobId + " of workflow " + workflowId + " in " + getRefInfoForException() + ": only succeeded, failed, or canceled jobs can be retried, found status " + ((status == null) ? "null" : status.name().toLowerCase()), Status.CONFLICT);
            }
            JobApi jobApi = getGitLabApi(this.projectId.getGitLabMode()).getJobApi();
            Job result;
            try
            {
                result = withRetries(() -> jobApi.retryJob(this.projectId.getGitLabId(), job.getId()));
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to retry job " + workflowJobId + " for workflow " + workflowId + " in " + getRefInfoForException(),
                        () -> "Unknown job in workflow " + workflowId + " of " + getRefInfoForException() + ": " + workflowJobId,
                        () -> "Error retrying job " + workflowJobId + " of workflow " + workflowId + " in " + getRefInfoForException());
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
                throw new LegendSDLCServerException("Cannot cancel job " + workflowJobId + " of workflow " + workflowId + " in " + getRefInfoForException() + ": only jobs in progress can be cancelled, found status " + ((status == null) ? "null" : status.name().toLowerCase()), Status.CONFLICT);
            }

            JobApi jobApi = getGitLabApi(this.projectId.getGitLabMode()).getJobApi();
            Job result;
            try
            {
                result = withRetries(() -> jobApi.cancelJob(this.projectId.getGitLabId(), job.getId()));
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to cancel job " + workflowJobId + " of workflow " + workflowId + " in " + getRefInfoForException(),
                        () -> "Unknown job in workflow " + workflowId + " of " + getRefInfoForException() + ": " + workflowJobId,
                        () -> "Error canceling job " + workflowJobId + " of workflow " + workflowId + " in " + getRefInfoForException());
            }
            return fromGitLabJob(result);
        }

        protected Job getJob(String workflowId, String workflowJobId)
        {
            return getJob(parseIntegerId(workflowId), parseIntegerId(workflowJobId));
        }

        protected Job getJob(int pipelineId, int jobId)
        {
            JobApi jobApi = getGitLabApi(this.projectId.getGitLabMode()).getJobApi();
            Job job;
            try
            {
                job = withRetries(() -> jobApi.getJob(this.projectId.getGitLabId(), jobId));
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to access job " + jobId + " of workflow " + pipelineId + " in " + getRefInfoForException(),
                        () -> "Unknown job in workflow " + pipelineId + " of " + getRefInfoForException() + ": " + jobId,
                        () -> "Error getting workflow job " + jobId + " of workflow " + pipelineId + " in " + getRefInfoForException());
            }

            if (!getRef().equals(job.getRef()) || (job.getPipeline() == null) || (job.getPipeline().getId() == null) || (pipelineId != job.getPipeline().getId()))
            {
                throw new LegendSDLCServerException("Unknown job in workflow " + pipelineId + " of " + getRefInfoForException() + ": " + jobId, Status.NOT_FOUND);
            }

            return job;
        }

        protected WorkflowJob fromGitLabJob(Job job)
        {
            return GitlabWorkflowJobApi.fromGitLabJob(this.projectId.toString(), job);
        }

        protected abstract String getRef();

        protected abstract String getRefInfoForException();
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
