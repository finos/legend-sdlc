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

import org.eclipse.collections.api.factory.Lists;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.revision.RevisionStatus;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.server.domain.api.revision.RevisionAccessContext;
import org.finos.legend.sdlc.server.domain.api.revision.RevisionApi;
import org.finos.legend.sdlc.server.inmemory.backend.InMemoryBackend;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryPatch;
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
    public RevisionAccessContext getProjectRevisionContext(String projectId, String patchReleaseVersion)
    {
        InMemoryProject project = this.backend.getProject(projectId);
        InMemoryPatch patch = project.getPatch(patchReleaseVersion);
        return new RevisionAccessContext()
        {
            @Override
            public Revision getRevision(String revisionId)
            {
                return patch == null ? project.getRevision(revisionId) : patch.getRevision(revisionId);
            }

            @Override
            public Revision getBaseRevision()
            {
                throw new UnsupportedOperationException("Not implemented");
            }

            @Override
            public Revision getCurrentRevision()
            {
                return patch == null ? project.getCurrentRevision() : patch.getCurrentRevision();
            }

            @Override
            public List<Revision> getRevisions(Predicate<? super Revision> predicate, Instant since, Instant until, Integer limit)
            {
                throw new UnsupportedOperationException("Not implemented");
            }
        };
    }

    @Override
    public RevisionAccessContext getProjectEntityRevisionContext(String projectId, String patchReleaseVersion, String entityPath)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public RevisionAccessContext getProjectPackageRevisionContext(String projectId, String patchReleaseVersion, String packagePath)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public RevisionAccessContext getWorkspaceRevisionContext(String projectId, String patchReleaseVersion, String workspaceId, WorkspaceType workspaceType)
    {
        InMemoryProject project = backend.getProject(projectId);
        InMemoryWorkspace workspace = workspaceType == WorkspaceType.GROUP ? project.getGroupWorkspace(workspaceId, patchReleaseVersion) : project.getUserWorkspace(workspaceId, patchReleaseVersion);
        return new RevisionAccessContext()
        {
            @Override
            public Revision getRevision(String revisionId)
            {
                return workspace.getRevision(revisionId);
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
                return Lists.mutable.withAll(workspace.getRevisions());
            }
        };
    }

    @Override
    public RevisionAccessContext getBackupWorkspaceRevisionContext(String projectId, String patchReleaseVersion, String workspaceId, WorkspaceType workspaceType)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public RevisionAccessContext getWorkspaceWithConflictResolutionRevisionContext(String projectId, String patchReleaseVersion, String workspaceId, WorkspaceType workspaceType)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public RevisionAccessContext getWorkspaceEntityRevisionContext(String projectId, String patchReleaseVersion, String workspaceId, WorkspaceType workspaceType, String entityPath)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public RevisionAccessContext getWorkspacePackageRevisionContext(String projectId, String patchReleaseVersion, String workspaceId, WorkspaceType workspaceType, String packagePath)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public RevisionStatus getRevisionStatus(String projectId, String patchReleaseVersion, String revisionId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }
}
