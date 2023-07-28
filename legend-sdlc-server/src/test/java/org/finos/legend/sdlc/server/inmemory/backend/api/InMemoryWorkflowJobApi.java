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

package org.finos.legend.sdlc.server.inmemory.backend.api;

import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification;
import org.finos.legend.sdlc.server.domain.api.workflow.WorkflowJobAccessContext;
import org.finos.legend.sdlc.server.domain.api.workflow.WorkflowJobApi;

import javax.inject.Inject;

public class InMemoryWorkflowJobApi implements WorkflowJobApi
{
    @Inject
    public InMemoryWorkflowJobApi()
    {
    }

    @Override
    public WorkflowJobAccessContext getWorkflowJobAccessContext(String projectId, SourceSpecification sourceSpecification)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public WorkflowJobAccessContext getReviewWorkflowJobAccessContext(String projectId, String reviewId)
    {
        throw new UnsupportedOperationException();
    }
}
