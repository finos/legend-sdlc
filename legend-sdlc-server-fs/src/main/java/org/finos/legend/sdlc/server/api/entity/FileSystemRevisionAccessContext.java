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

package org.finos.legend.sdlc.server.api.entity;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.server.api.BaseFSApi;
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification;
import org.finos.legend.sdlc.server.domain.model.revision.FileSystemRevision;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.exception.FSException;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;

import javax.ws.rs.core.Response;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class FileSystemRevisionAccessContext extends BaseFSApi implements ProjectFileAccessProvider.RevisionAccessContext
{
    private final String projectId;
    private final SourceSpecification sourceSpecification;

    public FileSystemRevisionAccessContext(String projectId, SourceSpecification sourceSpecification)
    {
        this.projectId = projectId;
        this.sourceSpecification = Objects.requireNonNull(sourceSpecification, "source specification may not be null");
    }

    @Override
    public Revision getCurrentRevision()
    {
        String branchName = getRefBranchName(sourceSpecification);
        Repository repo = BaseFSApi.retrieveRepo(this.projectId);
        try
        {
            ObjectId commitId = repo.resolve(branchName);
            RevWalk revWalk = new RevWalk(repo);
            RevCommit commit = revWalk.parseCommit(commitId);
            revWalk.dispose();
            return getRevisionInfo(commit);
        }
        catch (Exception e)
        {
            throw FSException.getLegendSDLCServerException("Failed to get current revision for branch " + branchName + " in project " + this.projectId, e);
        }
    }

    @Override
    public Revision getBaseRevision()
    {
        String branchName = getRefBranchName(sourceSpecification);
        Repository repo = BaseFSApi.retrieveRepo(this.projectId);
        try
        {
            Ref branchRef = repo.exactRef(Constants.R_HEADS + branchName);
            ObjectId commitId = branchRef.getObjectId();
            RevWalk revWalk = new RevWalk(repo);
            RevCommit commit = revWalk.parseCommit(commitId);
            if (commit.getParentCount() == 1)
            {
                RevCommit parentCommit = revWalk.parseCommit(commit.getParent(0));
                revWalk.dispose();
                return getRevisionInfo(parentCommit);
            }
            else
            {
                return null;
            }
        }
        catch (Exception e)
        {
            throw FSException.getLegendSDLCServerException("Failed to get current revision for branch " + branchName + " in project " + this.projectId, e);
        }
    }

    @Override
    public Revision getRevision(String revisionId)
    {
        LegendSDLCServerException.validateNonNull(revisionId, "revisionId may not be null");
        String resolvedRevisionId = resolveRevisionId(revisionId, this);
        if (resolvedRevisionId == null)
        {
            throw new LegendSDLCServerException("Failed to resolve revision " + revisionId + " of project " + this.projectId, Response.Status.NOT_FOUND);
        }
        String branchName = getRefBranchName(sourceSpecification);
        Repository repo = BaseFSApi.retrieveRepo(this.projectId);
        try
        {
            ObjectId commitId = ObjectId.fromString(resolvedRevisionId);
            RevWalk revWalk = new RevWalk(repo);
            RevCommit commit = revWalk.parseCommit(commitId);
            revWalk.dispose();
            return getRevisionInfo(commit);
        }
        catch (Exception e)
        {
            throw FSException.getLegendSDLCServerException("Failed to get " + resolvedRevisionId + " revision for branch " + branchName + " in project " + this.projectId, e);
        }
    }

    @Override
    public Stream<Revision> getAllRevisions(Predicate<? super Revision> predicate, Instant since, Instant until, Integer limit)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    private Revision getRevisionInfo(RevCommit commit)
    {
        String revisionID = commit.getId().getName();
        String authorName = commit.getAuthorIdent().getName();
        Instant authoredTimeStamp = commit.getAuthorIdent().getWhenAsInstant();
        String committerName = commit.getCommitterIdent().getName();
        Instant committedTimeStamp = commit.getCommitterIdent().getWhenAsInstant();
        String message = commit.getFullMessage();
        return new FileSystemRevision(revisionID, authorName, authoredTimeStamp, committerName, committedTimeStamp, message);
    }
}
