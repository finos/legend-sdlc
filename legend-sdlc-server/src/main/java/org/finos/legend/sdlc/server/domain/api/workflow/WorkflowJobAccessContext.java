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

package org.finos.legend.sdlc.server.domain.api.workflow;

import org.finos.legend.sdlc.domain.model.workflow.WorkflowJob;
import org.finos.legend.sdlc.domain.model.workflow.WorkflowJobStatus;

import java.util.List;

public interface WorkflowJobAccessContext
{
    WorkflowJob getWorkflowJob(String workflowId, String workflowJobId);

    List<WorkflowJob> getWorkflowJobs(String workflowId, Iterable<WorkflowJobStatus> statuses);

    String getWorkflowJobLog(String workflowId, String workflowJobId);

    WorkflowJob runWorkflowJob(String workflowId, String workflowJobId);

    WorkflowJob retryWorkflowJob(String workflowId, String workflowJobId);

    WorkflowJob cancelWorkflowJob(String workflowId, String workflowJobId);
}
