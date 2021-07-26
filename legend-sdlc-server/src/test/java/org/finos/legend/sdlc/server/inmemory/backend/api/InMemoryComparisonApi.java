// Copyright 2020 Goldman Sachs
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

import org.finos.legend.sdlc.domain.model.comparison.Comparison;
import org.finos.legend.sdlc.server.domain.api.comparison.ComparisonApi;

import javax.inject.Inject;

public class InMemoryComparisonApi implements ComparisonApi
{
    @Inject
    public InMemoryComparisonApi()
    {
    }

    @Override
    public Comparison getUserWorkspaceCreationComparison(String projectId, String workspaceId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Comparison getGroupWorkspaceCreationComparison(String projectId, String workspaceId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Comparison getWorkspaceCreationComparison(String projectId, String workspaceId, boolean isGroupWorkspace)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Comparison getUserWorkspaceProjectComparison(String projectId, String workspaceId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Comparison getGroupWorkspaceProjectComparison(String projectId, String workspaceId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Comparison getWorkspaceProjectComparison(String projectId, String workspaceId, boolean isGroupWorkspace)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Comparison getReviewComparison(String projectId, String reviewId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Comparison getReviewWorkspaceCreationComparison(String projectId, String reviewId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }
}
