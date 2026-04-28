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

package org.finos.legend.sdlc.server.domain.api.revision;

import org.finos.legend.sdlc.domain.model.revision.RevisionStatus;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification;

/**
 * Note that all of these APIs support revision ID alias as they all essentially calls getRevision() from RevisionAccessContext
 * which takes into account revision ID alias
 */
public interface RevisionApi
{
    RevisionAccessContext getRevisionContext(String projectId, SourceSpecification sourceSpec);

    RevisionAccessContext getPackageRevisionContext(String projectId, SourceSpecification sourceSpec, String packagePath);

    RevisionAccessContext getEntityRevisionContext(String projectId, SourceSpecification sourceSpec, String entityPath);

    RevisionStatus getRevisionStatus(String projectId, String revisionId);

    // Deprecated APIs

    @Deprecated
    default RevisionStatus getRevisionStatus(String projectId, VersionId patchReleaseVersionId, String revisionId)
    {
        return getRevisionStatus(projectId, revisionId);
    }
}
