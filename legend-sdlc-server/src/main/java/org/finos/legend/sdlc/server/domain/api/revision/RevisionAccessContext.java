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

import org.finos.legend.sdlc.domain.model.revision.Revision;

import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;

public interface RevisionAccessContext
{
    /**
     * Get a particular revision. Throws an exception if the revision does not exist in the context.
     *
     * @param revisionId revision id
     * @return revision
     */
    Revision getRevision(String revisionId);

    /**
     * Get the base revision of the context. If the context concerns a workspace, get the revision of project
     * from where the workspace is created. If the context concerns a project, get the very first revision of the project
     *
     * @return base revision
     */
    Revision getBaseRevision();

    /**
     * Get the current revision of the context.
     *
     * @return current revision
     */
    Revision getCurrentRevision();

    /**
     * Get all revisions for the context.
     *
     * @return all revisions
     */
    default List<Revision> getRevisions()
    {
        return getRevisions(null, null, null, null);
    }

    default List<Revision> getRevisions(int limit)
    {
        return getRevisions(null, null, null, limit);
    }

    default List<Revision> getRevisions(Instant since, Instant until)
    {
        return getRevisions(null, since, until, null);
    }

    /**
     * Get all revisions, subject to the provided constraints.
     *
     * @param predicate filter predicate for revisions (null means no constraint)
     * @param since     only revisions since this time, inclusive (null means no limit)
     * @param until     only revisions until this time, inclusive (null means no limit)
     * @param limit     limit on the number of revisions returned (null or non-positive means no limit)
     * @return revisions
     */
    List<Revision> getRevisions(Predicate<? super Revision> predicate, Instant since, Instant until, Integer limit);
}
