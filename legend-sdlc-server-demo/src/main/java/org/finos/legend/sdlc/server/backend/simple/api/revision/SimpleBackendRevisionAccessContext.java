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

package org.finos.legend.sdlc.server.backend.simple.api.revision;

import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.server.domain.api.revision.RevisionAccessContext;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public class SimpleBackendRevisionAccessContext implements RevisionAccessContext
{
    private Revision revision;

    public SimpleBackendRevisionAccessContext(Revision revision)
    {
        this.revision = revision;
    }

    @Override
    public Revision getRevision(String revisionId)
    {
        return revision;
    }

    @Override
    public Revision getBaseRevision()
    {
        return revision;
    }

    @Override
    public Revision getCurrentRevision()
    {
        return revision;
    }

    @Override
    public List<Revision> getRevisions(Predicate<? super Revision> predicate, Instant since, Instant until, Integer limit)
    {
        return Collections.emptyList();
    }
}
