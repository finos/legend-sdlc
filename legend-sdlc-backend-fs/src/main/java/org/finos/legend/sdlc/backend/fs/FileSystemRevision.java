// Copyright 2026 Goldman Sachs
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

package org.finos.legend.sdlc.backend.fs;

import org.finos.legend.sdlc.domain.model.revision.Revision;

import java.time.Instant;

class FileSystemRevision implements Revision
{
    private final String id;
    private final String authorName;
    private final Instant authoredTimestamp;
    private final String committerName;
    private final Instant committedTimestamp;
    private final String message;

    FileSystemRevision(String id, String authorName, Instant authoredTimestamp, String committerName, Instant committedTimestamp, String message)
    {
        this.id = id;
        this.authorName = authorName;
        this.authoredTimestamp = authoredTimestamp;
        this.committerName = committerName;
        this.committedTimestamp = committedTimestamp;
        this.message = message;
    }

    @Override
    public String getId()
    {
        return this.id;
    }

    @Override
    public String getAuthorName()
    {
        return this.authorName;
    }

    @Override
    public Instant getAuthoredTimestamp()
    {
        return this.authoredTimestamp;
    }

    @Override
    public String getCommitterName()
    {
        return this.committerName;
    }

    @Override
    public Instant getCommittedTimestamp()
    {
        return this.committedTimestamp;
    }

    @Override
    public String getMessage()
    {
        return this.message;
    }
}
