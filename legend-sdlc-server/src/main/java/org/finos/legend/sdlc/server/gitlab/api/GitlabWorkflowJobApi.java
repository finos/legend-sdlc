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
import org.gitlab4j.api.JobApi;
import org.gitlab4j.api.models.Job;
import org.gitlab4j.api.models.JobStatus;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class GitlabWorkflowJobApi extends GitLabApiWithFileAccess implements WorkflowJobApi
{
    @Inject
    protected GitlabWorkflowJobApi(GitLabUserContext userContext, BackgroundTaskProcessor backgroundTaskProcessor)
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
    public WorkflowJobAccessContext getWorkspaceWorkflowJobAccessContext(String projectId, String workspaceId, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceId, "workspaceId may not be null");
        return new GitLabWorkflowJobAccessContext(projectId)
        {
            @Override
            protected String getRef()
            {
                return getBranchName(workspaceId, workspaceAccessType);
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
            int jobId = parseIntegerIdIfNotNull(workflowJobId);
            Job job;
            try
            {
                job = withRetries(() -> getGitLabApi(this.projectId.getGitLabMode()).getJobApi().getJob(this.projectId.getGitLabId(), jobId));
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to access workflow job " + workflowJobId + " in " + getRefInfoForException(),
                        () -> "Unknown workflow job in " + getRefInfoForException() + ": " + workflowJobId,
                        () -> "Error getting workflow job " + workflowJobId + " in " + getRefInfoForException());
            }

            if (!getRef().equals(job.getRef()))
            {
                throw new LegendSDLCServerException("Unknown workflow job in " + getRefInfoForException() + ": " + workflowJobId, Response.Status.NOT_FOUND);
            }

            if (!workflowId.equals(toStringIfNotNull(job.getPipeline().getId())))
            {
                throw new LegendSDLCServerException("Unknown workflow job in " + getRefInfoForException() + ", workflow " + workflowId + ": " + workflowJobId, Response.Status.NOT_FOUND);
            }

            return fromGitLabJob(this.projectId.toString(), job);
        }

        @Override
        public List<WorkflowJob> getWorkflowJobs(String workflowId, Iterable<WorkflowJobStatus> statuses)
        {
            try
            {
                int pipelineId = parseIntegerIdIfNotNull(workflowId);
                JobApi jobApi = getGitLabApi(this.projectId.getGitLabMode()).getJobApi();
                List<Job> jobs = withRetries(() -> jobApi.getJobsForPipeline(this.projectId.getGitLabId(), pipelineId));
                Stream<Job> jobStream = jobs.stream();

                Set<WorkflowJobStatus> statusSet = (statuses == null) ? Collections.emptySet() : ((statuses instanceof Set) ? (Set<WorkflowJobStatus>) statuses : StreamSupport.stream(statuses.spliterator(), false).collect(Collectors.toCollection(() -> EnumSet.noneOf(WorkflowJobStatus.class))));
                if (!statusSet.isEmpty())
                {
                    jobStream = jobStream.filter(job -> statusSet.contains(fromGitLabJobStatus(job.getStatus())));
                }

                return jobStream
                        .map(p -> fromGitLabJob(this.projectId.toString(), p))
                        .collect(Collectors.toList());
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to access workflow job for " + getRefInfoForException(),
                        () -> "Unknown " + getRefInfoForException(),
                        () -> "Error getting workflow job for " + getRefInfoForException());
            }
        }

        @Override
        public String getWorkflowJobLog(String workflowId, String workflowJobId)
        {
            int jobId = parseIntegerIdIfNotNull(workflowJobId);
            Job job;
            String jobTrace;
            try
            {
                job = withRetries(() -> getGitLabApi(this.projectId.getGitLabMode()).getJobApi().getJob(this.projectId.getGitLabId(), jobId));

                if (!getRef().equals(job.getRef()))
                {
                    throw new LegendSDLCServerException("Unknown workflow job in " + getRefInfoForException() + ": " + workflowJobId, Response.Status.NOT_FOUND);
                }

                if (!workflowId.equals(toStringIfNotNull(job.getPipeline().getId())))
                {
                    throw new LegendSDLCServerException("Unknown workflow job in " + getRefInfoForException() + ", workflow " + workflowId + ": " + workflowJobId, Response.Status.NOT_FOUND);
                }
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to access workflow job " + workflowJobId + " in " + getRefInfoForException(),
                        () -> "Unknown workflow job in " + getRefInfoForException() + ": " + workflowJobId,
                        () -> "Error getting workflow job " + workflowJobId + " in " + getRefInfoForException());
            }

            try
            {
                jobTrace = withRetries(() -> getGitLabApi(this.projectId.getGitLabMode()).getJobApi().getTrace(this.projectId.getGitLabId(), job.getId()));
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to access workflow job log for workflow job " + workflowJobId + " in " + getRefInfoForException(),
                        () -> "Unknown workflow job log in " + getRefInfoForException() + ": " + workflowJobId,
                        () -> "Error getting workflow job log for workflow job" + workflowJobId + " in " + getRefInfoForException());
            }

            return jobTrace;
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
