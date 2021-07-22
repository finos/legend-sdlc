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

import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.revision.RevisionStatus;
import org.finos.legend.sdlc.server.domain.api.revision.RevisionAccessContext;
import org.finos.legend.sdlc.server.domain.api.revision.RevisionApi;
import org.finos.legend.sdlc.server.inmemory.backend.InMemoryBackend;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryProject;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryWorkspace;

import javax.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;

public class InMemoryRevisionApi implements RevisionApi
{
    private final InMemoryBackend backend;

    @Inject
    public InMemoryRevisionApi(InMemoryBackend backend)
    {
        this.backend = backend;
    }

    @Override
    public RevisionAccessContext getProjectRevisionContext(String projectId)
    {
        InMemoryProject project = this.backend.getProject(projectId);
        return new RevisionAccessContext()
        {
            @Override
            public Revision getRevision(String revisionId)
            {
                return project.getRevision(revisionId);
            }

            @Override
            public Revision getBaseRevision()
            {
                throw new UnsupportedOperationException("Not implemented");
            }

            @Override
            public Revision getCurrentRevision()
            {
                return project.getCurrentRevision();
            }

            @Override
            public List<Revision> getRevisions(Predicate<? super Revision> predicate, Instant since, Instant until, Integer limit)
            {
                throw new UnsupportedOperationException("Not implemented");
            }
        };
    }

    @Override
    public RevisionAccessContext getProjectEntityRevisionContext(String projectId, String entityPath)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public RevisionAccessContext getProjectPackageRevisionContext(String projectId, String packagePath)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public RevisionAccessContext getUserWorkspaceRevisionContext(String projectId, String workspaceId)
    {
        return this.getWorkspaceRevisionContext(projectId, workspaceId, false);
    }

    @Override
    public RevisionAccessContext getGroupWorkspaceRevisionContext(String projectId, String workspaceId)
    {
        return this.getWorkspaceRevisionContext(projectId, workspaceId, true);
    }

    @Override
    public RevisionAccessContext getWorkspaceRevisionContext(String projectId, String workspaceId, boolean isGroup)
    {
        InMemoryWorkspace workspace = backend.getProject(projectId).getWorkspace(workspaceId);
        return new RevisionAccessContext()
        {
            @Override
            public Revision getRevision(String revisionId)
            {
                throw new UnsupportedOperationException("Not implemented");
            }

            @Override
            public Revision getBaseRevision()
            {
                throw new UnsupportedOperationException("Not implemented");
            }

            @Override
            public Revision getCurrentRevision()
            {
                return workspace.getCurrentRevision();
            }

            @Override
            public List<Revision> getRevisions(Predicate<? super Revision> predicate, Instant since, Instant until, Integer limit)
            {
                throw new UnsupportedOperationException("Not implemented");
            }
        };
    }

    @Override
    public RevisionAccessContext getBackupWorkspaceRevisionContext(String projectId, String workspaceId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public RevisionAccessContext getWorkspaceWithConflictResolutionRevisionContext(String projectId, String workspaceId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public RevisionAccessContext getUserWorkspaceEntityRevisionContext(String projectId, String workspaceId, String entityPath)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public RevisionAccessContext getGroupWorkspaceEntityRevisionContext(String projectId, String workspaceId, String entityPath)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public RevisionAccessContext getWorkspaceEntityRevisionContext(String projectId, String workspaceId, boolean isGroup, String entityPath)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public RevisionAccessContext getUserWorkspacePackageRevisionContext(String projectId, String workspaceId, String packagePath)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public RevisionAccessContext getGroupWorkspacePackageRevisionContext(String projectId, String workspaceId, String packagePath)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public RevisionAccessContext getWorkspacePackageRevisionContext(String projectId, String workspaceId, boolean isGroup, String packagePath)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public RevisionStatus getRevisionStatus(String projectId, String revisionId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }
}
