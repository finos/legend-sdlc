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

package org.finos.legend.sdlc.server.api.workflow;

import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification;
import org.finos.legend.sdlc.server.exception.UnavailableFeature;
import org.finos.legend.sdlc.server.domain.api.workflow.WorkflowAccessContext;
import org.finos.legend.sdlc.server.domain.api.workflow.WorkflowApi;

import javax.inject.Inject;

public class FileSystemWorkflowApi implements WorkflowApi
{
    @Inject
    public FileSystemWorkflowApi()
    {
    }

    @Override
    public WorkflowAccessContext getWorkflowAccessContext(String projectId, SourceSpecification sourceSpecification)
    {
        throw UnavailableFeature.exception();
    }

    @Override
    public WorkflowAccessContext getReviewWorkflowAccessContext(String projectId, String reviewId)
    {
        throw UnavailableFeature.exception();
    }
}