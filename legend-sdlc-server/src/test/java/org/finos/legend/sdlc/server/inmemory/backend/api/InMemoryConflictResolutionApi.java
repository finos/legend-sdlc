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

import org.finos.legend.sdlc.server.application.entity.PerformChangesCommand;
import org.finos.legend.sdlc.server.domain.api.conflictResolution.ConflictResolutionApi;
import org.finos.legend.sdlc.server.domain.api.project.SourceSpecification;

import javax.inject.Inject;

public class InMemoryConflictResolutionApi implements ConflictResolutionApi
{
    @Inject
    public InMemoryConflictResolutionApi()
    {
    }

    @Override
    public void discardConflictResolution(String projectId, SourceSpecification sourceSpecification)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void discardChangesConflictResolution(String projectId, SourceSpecification sourceSpecification)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void acceptConflictResolution(String projectId, SourceSpecification sourceSpecification, PerformChangesCommand command)
    {
        throw new UnsupportedOperationException("Not implemented");
    }
}
