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

package org.finos.legend.sdlc.server.api.revision;

import org.finos.legend.sdlc.domain.model.revision.RevisionStatus;
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification;
import org.finos.legend.sdlc.server.domain.api.revision.RevisionAccessContext;
import org.finos.legend.sdlc.server.domain.api.revision.RevisionApi;
import org.finos.legend.sdlc.server.domain.model.revision.FileSystemRevision;
import org.finos.legend.sdlc.server.exception.UnavailableFeature;

import javax.inject.Inject;

public class FileSystemRevisionApi implements RevisionApi
{
    @Inject
    public FileSystemRevisionApi()
    {
    }

    @Override
    public RevisionAccessContext getRevisionContext(String projectId, SourceSpecification sourceSpec)
    {
        FileSystemRevision revision = FileSystemRevision.getFileSystemRevision(projectId, sourceSpec.getWorkspaceId());
        FileSystemRevisionAccessContext context = new FileSystemRevisionAccessContext(revision);
        return context;
    }

    @Override
    public RevisionAccessContext getPackageRevisionContext(String projectId, SourceSpecification sourceSpec, String packagePath)
    {
        throw UnavailableFeature.exception();
    }

    @Override
    public RevisionAccessContext getEntityRevisionContext(String projectId, SourceSpecification sourceSpec, String entityPath)
    {
        throw UnavailableFeature.exception();
    }

    @Override
    public RevisionStatus getRevisionStatus(String projectId, String revisionId)
    {
        throw UnavailableFeature.exception();
    }
}
