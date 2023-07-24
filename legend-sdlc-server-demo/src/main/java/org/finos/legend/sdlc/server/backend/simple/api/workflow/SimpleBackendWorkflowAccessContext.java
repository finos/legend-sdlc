// Copyright 2023 Goldman Sachs
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

package org.finos.legend.sdlc.server.backend.simple.api.workflow;

import org.finos.legend.sdlc.domain.model.workflow.Workflow;
import org.finos.legend.sdlc.domain.model.workflow.WorkflowStatus;
import org.finos.legend.sdlc.server.backend.simple.exception.UnavailableFeature;
import org.finos.legend.sdlc.server.domain.api.workflow.WorkflowAccessContext;

import java.util.Collections;
import java.util.List;

public class SimpleBackendWorkflowAccessContext implements WorkflowAccessContext
{
    @Override
    public Workflow getWorkflow(String workflowId)
    {
        throw UnavailableFeature.exception();
    }

    @Override
    public List<Workflow> getWorkflows(Iterable<String> revisionIds, Iterable<WorkflowStatus> statuses, Integer limit)
    {
        return Collections.emptyList();
    }
}
