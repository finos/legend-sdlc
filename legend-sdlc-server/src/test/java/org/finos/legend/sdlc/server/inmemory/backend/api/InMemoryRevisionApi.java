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
import org.finos.legend.sdlc.server.domain.api.project.source.PatchSourceSpecification;
import org.finos.legend.sdlc.server.domain.api.project.source.ProjectSourceSpecification;
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification;
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecificationVisitor;
import org.finos.legend.sdlc.server.domain.api.project.source.WorkspaceSourceSpecification;
import org.finos.legend.sdlc.server.domain.api.revision.RevisionAccessContext;
import org.finos.legend.sdlc.server.domain.api.revision.RevisionApi;
import org.finos.legend.sdlc.server.domain.api.workspace.PatchWorkspaceSource;
import org.finos.legend.sdlc.server.domain.api.workspace.ProjectWorkspaceSource;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSourceVisitor;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSpecification;
import org.finos.legend.sdlc.server.inmemory.backend.InMemoryBackend;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryPatch;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryProject;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryWorkspace;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;

import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;
import javax.inject.Inject;

public class InMemoryRevisionApi implements RevisionApi
{
    private final InMemoryBackend backend;

    @Inject
    public InMemoryRevisionApi(InMemoryBackend backend)
    {
        this.backend = backend;
    }

    @Override
    public RevisionAccessContext getRevisionContext(String projectId, SourceSpecification sourceSpec)
    {
        InMemoryProject project = this.backend.getProject(projectId);
        return sourceSpec.visit(new SourceSpecificationVisitor<RevisionAccessContext>()
        {
            @Override
            public RevisionAccessContext visit(ProjectSourceSpecification sourceSpec)
            {
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
            public RevisionAccessContext visit(PatchSourceSpecification sourceSpec)
            {
                InMemoryPatch patch = project.getPatch(sourceSpec.getVersionId());
                return new RevisionAccessContext()
                {
                    @Override
                    public Revision getRevision(String revisionId)
                    {
                        return patch.getRevision(revisionId);
                    }

                    @Override
                    public Revision getBaseRevision()
                    {
                        throw new UnsupportedOperationException("Not implemented");
                    }

                    @Override
                    public Revision getCurrentRevision()
                    {
                        return patch.getCurrentRevision();
                    }

                    @Override
                    public List<Revision> getRevisions(Predicate<? super Revision> predicate, Instant since, Instant until, Integer limit)
                    {
                        throw new UnsupportedOperationException("Not implemented");
                    }
                };
            }

            @Override
            public RevisionAccessContext visit(WorkspaceSourceSpecification sourceSpec)
            {
                InMemoryWorkspace workspace = getWorkspace(project, sourceSpec.getWorkspaceSpecification());
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
        });
    }

    private InMemoryWorkspace getWorkspace(InMemoryProject project, WorkspaceSpecification workspaceSpec)
    {
        if (workspaceSpec.getAccessType() != ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE)
        {
            throw new UnsupportedOperationException("Not implemented");
        }
        switch (workspaceSpec.getType())
        {
            case USER:
            {
                return workspaceSpec.getSource().visit(new WorkspaceSourceVisitor<InMemoryWorkspace>()
                {
                    @Override
                    public InMemoryWorkspace visit(ProjectWorkspaceSource source)
                    {
                        return project.getUserWorkspace(workspaceSpec.getId(), null);
                    }

                    @Override
                    public InMemoryWorkspace visit(PatchWorkspaceSource source)
                    {
                        return project.getUserWorkspace(workspaceSpec.getId(), source.getPatchVersionId());
                    }
                });
            }
            case GROUP:
            {
                return workspaceSpec.getSource().visit(new WorkspaceSourceVisitor<InMemoryWorkspace>()
                {
                    @Override
                    public InMemoryWorkspace visit(ProjectWorkspaceSource source)
                    {
                        return project.getGroupWorkspace(workspaceSpec.getId(), null);
                    }

                    @Override
                    public InMemoryWorkspace visit(PatchWorkspaceSource source)
                    {
                        return project.getGroupWorkspace(workspaceSpec.getId(), source.getPatchVersionId());
                    }
                });
            }
            default:
            {
                throw new UnsupportedOperationException("Unsupported workspace type: " + workspaceSpec.getType());
            }
        }
    }

    @Override
    public RevisionAccessContext getPackageRevisionContext(String projectId, SourceSpecification sourceSpec, String packagePath)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public RevisionAccessContext getEntityRevisionContext(String projectId, SourceSpecification sourceSpec, String entityPath)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public RevisionStatus getRevisionStatus(String projectId, String revisionId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }
}
