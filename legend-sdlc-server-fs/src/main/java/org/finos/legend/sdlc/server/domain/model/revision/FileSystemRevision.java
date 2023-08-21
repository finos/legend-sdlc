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

package org.finos.legend.sdlc.server.domain.model.revision;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.server.exception.FSException;

import java.time.Instant;

public class FileSystemRevision implements Revision
{
    private final String id;
    private final String authorName;
    private final Instant authoredTimestamp;
    private final String committerName;
    private final Instant committedTimestamp;
    private final String message;

    public FileSystemRevision(String revisionId, String authorName, Instant authoredTimeStamp, String committerName, Instant committedTimeStamp, String message)
    {
        this.id = revisionId;
        this.authorName = authorName;
        this.authoredTimestamp = authoredTimeStamp;
        this.committerName = committerName;
        this.committedTimestamp = committedTimeStamp;
        this.message = message;
    }

    public static FileSystemRevision getFileSystemRevision(String projectId, String workspaceId, Repository repo, Ref branchRef)
    {
        try
        {
            RevWalk revWalk = new RevWalk(repo);
            RevCommit commit = revWalk.parseCommit(branchRef.getObjectId());
            revWalk.dispose();
            String revisionId = commit.getId().getName();
            String authorName = commit.getAuthorIdent().getName();
            Instant authoredTimeStamp = commit.getAuthorIdent().getWhenAsInstant();
            String committerName = commit.getCommitterIdent().getName();
            Instant committedTimeStamp = commit.getCommitterIdent().getWhenAsInstant();
            String message = commit.getFullMessage();
            return new FileSystemRevision(revisionId, authorName, authoredTimeStamp, committerName, committedTimeStamp, message);
        }
        catch (Exception e)
        {
            throw FSException.getLegendSDLCServerException("Failed to get revision for workspace " + workspaceId + " in project " + projectId, e);
        }
    }

    @Override
    public String getId()
    {
        return id;
    }

    @Override
    public String getAuthorName()
    {
        return authorName;
    }

    @Override
    public Instant getAuthoredTimestamp()
    {
        return authoredTimestamp;
    }

    @Override
    public String getCommitterName()
    {
        return committerName;
    }

    @Override
    public Instant getCommittedTimestamp()
    {
        return committedTimestamp;
    }

    @Override
    public String getMessage()
    {
        return message;
    }
}
