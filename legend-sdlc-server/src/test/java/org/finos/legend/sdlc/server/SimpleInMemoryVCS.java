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

package org.finos.legend.sdlc.server;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.finos.legend.sdlc.domain.model.version.VersionId;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class SimpleInMemoryVCS
{
    private static final Pattern PATH_PATTERN = Pattern.compile("(/[\\w\\h-.]+)*");

    private final MutableList<Revision> revisions;
    private final MutableMap<String, SimpleInMemoryVCS> branches = Maps.mutable.empty();
    private final MutableMap<VersionId, ImmutableSimpleInMemoryVCS> versionTags = Maps.mutable.empty();

    public SimpleInMemoryVCS()
    {
        this.revisions = Lists.mutable.empty();
    }

    protected SimpleInMemoryVCS(List<Revision> revisions)
    {
        this.revisions = Lists.mutable.withAll(revisions);
    }

    public File getFile(String path)
    {
        return getFile(path, null);
    }

    public File getFile(String path, String revision)
    {
        Revision rev = getRevision(revision, true);
        return (rev == null) ? null : rev.getFile(path);
    }

    public Stream<File> getFiles()
    {
        return getFiles(null);
    }

    public Stream<File> getFiles(String revision)
    {
        Revision rev = getRevision(revision, true);
        return (rev == null) ? Stream.empty() : rev.getFiles();
    }

    public Revision getRevision(String revision)
    {
        return getRevision(revision, false);
    }

    public Revision getLatestRevision()
    {
        return getRevision(null, true);
    }

    public Stream<Revision> getAllRevisions()
    {
        return getRevisions(null, null, null, null);
    }

    public Stream<Revision> getRevisions(Collection<String> paths, Instant since, Instant until, Integer limit)
    {
        if (this.revisions.isEmpty())
        {
            return Stream.empty();
        }

        Stream<Revision> stream = StreamSupport.stream(this.revisions.asReversed().spliterator(), false);
        if (paths != null)
        {
            Set<String> pathSet = Sets.mutable.withAll(paths);
            stream = stream.filter(r ->
            {
                List<String> newPaths = null;
                boolean found = false;
                for (String path : pathSet)
                {
                    if (r.added.contains(path) || r.deleted.contains(path) || r.modified.contains(path) || r.movedTo.containsKey(path))
                    {
                        found = true;
                    }
                    else if (r.movedFrom.containsKey(path))
                    {
                        found = true;
                        String oldPath = r.movedFrom.get(path);
                        if (newPaths == null)
                        {
                            newPaths = Lists.mutable.empty();
                        }
                        newPaths.add(oldPath);
                    }
                    else if (r.added.stream().anyMatch(p -> pathStartsWith(p, path)) ||
                            r.deleted.stream().anyMatch(p -> pathStartsWith(p, path)) ||
                            r.modified.stream().anyMatch(p -> pathStartsWith(p, path)) ||
                            r.movedFrom.keySet().stream().anyMatch(p -> pathStartsWith(p, path)) ||
                            r.movedTo.keySet().stream().anyMatch(p -> pathStartsWith(p, path)))
                    {
                        found = true;
                    }
                }
                if (newPaths != null)
                {
                    pathSet.addAll(newPaths);
                }
                return found;
            });
        }
        if (since != null)
        {
            stream = stream.filter(r -> since.compareTo(r.getTimestamp()) <= 0);
        }
        if (until != null)
        {
            stream = stream.filter(r -> until.compareTo(r.getTimestamp()) >= 0);
        }
        if (limit != null)
        {
            stream = stream.limit(limit);
        }
        return stream;
    }

    public CommitBuilder newCommitBuilder()
    {
        return newCommitBuilder(null);
    }

    public CommitBuilder newCommitBuilder(String author)
    {
        synchronized (this.revisions)
        {
            int latestRevisionInt = getLatestRevisionInt();
            Revision latestRevision = getRevision(latestRevisionInt, true);
            Map<String, File> commitFiles = (latestRevision == null) ? Maps.mutable.empty() : Maps.mutable.withMap(latestRevision.files);
            return new CommitBuilder(author, latestRevisionInt, commitFiles);
        }
    }

    public SimpleInMemoryVCS getBranch(String branchName)
    {
        synchronized (this.branches)
        {
            return this.branches.get(branchName);
        }
    }

    public void deleteBranch(String branchName)
    {
        synchronized (this.branches)
        {
            this.branches.remove(branchName);
        }
    }

    public SimpleInMemoryVCS newBranch(String branchName)
    {
        synchronized (this.branches)
        {
            if (this.branches.containsKey(branchName))
            {
                throw new IllegalArgumentException("Branch already exists: " + branchName);
            }
            synchronized (this.revisions)
            {
                SimpleInMemoryVCS branch = new SimpleInMemoryVCS(this.revisions);
                this.branches.put(branchName, branch);
                return branch;
            }
        }
    }

    public void mergeBranchToTrunk(String branchName)
    {
        SimpleInMemoryVCS branch;
        synchronized (this.branches)
        {
            branch = this.branches.remove(branchName);
            if (branch == null)
            {
                throw new IllegalArgumentException("Unknown branch: " + branchName);
            }
        }
        synchronized (branch.revisions)
        {
            synchronized (this.revisions)
            {
                int trunkSize = this.revisions.size();
                if (trunkSize == 0)
                {
                    this.revisions.addAll(branch.revisions);
                }
                else
                {
                    int branchSize = branch.revisions.size();
                    if ((trunkSize > branchSize) || (branch.revisions.get(trunkSize - 1) != this.revisions.get(trunkSize - 1)))
                    {
                        throw new IllegalStateException("Cannot merge branch into trunk: " + branchName);
                    }
                    this.revisions.addAll(branch.revisions.subList(trunkSize, branchSize));
                }
            }
        }
    }

    public SimpleInMemoryVCS newVersionTag(VersionId versionId)
    {
        synchronized (this.versionTags)
        {
            if (this.versionTags.containsKey(versionId))
            {
                throw new IllegalArgumentException("Tag already exists for version " + versionId.toVersionIdString());
            }
            synchronized (this.revisions)
            {
                ImmutableSimpleInMemoryVCS tag = new ImmutableSimpleInMemoryVCS(this.revisions);
                this.versionTags.put(versionId, tag);
                return tag;
            }
        }
    }

    public SimpleInMemoryVCS getVersionTag(VersionId versionId)
    {
        synchronized (this.versionTags)
        {
            return this.versionTags.get(versionId);
        }
    }

    private Revision getRevision(String revision, boolean nullForZero)
    {
        int revisionInt;
        try
        {
            revisionInt = (revision == null) ? getLatestRevisionInt() : Integer.parseInt(revision);
        }
        catch (NumberFormatException e)
        {
            throw new IllegalArgumentException("Invalid revision: " + revision);
        }

        return getRevision(revisionInt, nullForZero);
    }

    private Revision getRevision(int revision, boolean nullForZero)
    {
        if (revision < 0)
        {
            throw new IllegalArgumentException("Invalid revision: " + revision);
        }
        if (revision == 0)
        {
            if (nullForZero)
            {
                return null;
            }
            throw new IllegalArgumentException("Invalid revision: " + revision);
        }
        try
        {
            return this.revisions.get(revision - 1);
        }
        catch (IndexOutOfBoundsException e)
        {
            throw new IllegalArgumentException("Unknown revision: " + revision);
        }
    }

    private int getLatestRevisionInt()
    {
        return this.revisions.size();
    }

    private Revision commit(CommitBuilder builder, String committer, String message)
    {
        synchronized (this.revisions)
        {
            int latestRevision = getLatestRevisionInt();
            if (latestRevision != builder.startRevision)
            {
                // TODO validate substantively
                throw new IllegalStateException("CommitBuilder is stale: latest revision is now " + getLatestRevision().getId());
            }
            String nextRevisionId = Integer.toString(latestRevision + 1);
            String author = (builder.author == null) ? committer : builder.author;
            Instant timestamp = Instant.now();
            Set<String> added = builder.added.isEmpty() ? Collections.emptySet() : builder.added;
            Set<String> modified = builder.modified.isEmpty() ? Collections.emptySet() : builder.modified;
            Set<String> deleted = builder.deleted.isEmpty() ? Collections.emptySet() : builder.deleted;
            Map<String, String> movedFrom = builder.movedFrom.isEmpty() ? Collections.emptyMap() : builder.movedFrom;
            Map<String, String> movedTo = builder.movedTo.isEmpty() ? Collections.emptyMap() : builder.movedTo;
            Revision revision = new Revision(nextRevisionId, committer, author, timestamp, message, builder.files, added, modified, deleted, movedFrom, movedTo);
            this.revisions.add(revision);
            return revision;
        }
    }

    private static void validatePath(String path)
    {
        if ((path == null) || !PATH_PATTERN.matcher(path).matches())
        {
            throw new IllegalArgumentException("Invalid path: " + path);
        }
    }

    private static boolean pathStartsWith(String path, String prefixPath)
    {
        if (!path.startsWith(prefixPath))
        {
            return false;
        }

        char lastPrefixChar = prefixPath.charAt(prefixPath.length() - 1);
        char nextPathChar = path.charAt(prefixPath.length());
        return (lastPrefixChar == '/') ? (nextPathChar != '/') : (nextPathChar == '/');
    }

    public class CommitBuilder
    {
        private final String author;
        private final int startRevision;
        private final Map<String, File> files;
        private final Set<String> added = Sets.mutable.empty();
        private final Set<String> modified = Sets.mutable.empty();
        private final Set<String> deleted = Sets.mutable.empty();
        private final Map<String, String> movedFrom = Maps.mutable.empty();
        private final Map<String, String> movedTo = Maps.mutable.empty();
        private boolean closed = false;

        private CommitBuilder(String author, int startRevision, Map<String, File> files)
        {
            this.author = author;
            this.startRevision = startRevision;
            this.files = files;
        }

        public synchronized CommitBuilder add(String path, byte[] content)
        {
            validateOpen();
            validatePath(path);
            if (this.files.containsKey(path))
            {
                throw new IllegalStateException("File " + path + " already exists");
            }
            if (content == null)
            {
                throw new IllegalArgumentException("Content cannot be null");
            }
            this.files.put(path, new File(path, content));
            this.added.add(path);
            return this;
        }

        public synchronized CommitBuilder delete(String path)
        {
            validateOpen();
            if (!this.files.containsKey(path))
            {
                throw new IllegalStateException("File " + path + " does not exist");
            }
            this.files.remove(path);
            if (!this.added.remove(path))
            {
                this.deleted.add(path);
                this.modified.remove(path);
            }
            return this;
        }

        public synchronized CommitBuilder modify(String path, byte[] newContent)
        {
            validateOpen();
            File current = this.files.get(path);
            if (current == null)
            {
                throw new IllegalStateException("File " + path + " does not exist");
            }
            if (newContent == null)
            {
                throw new IllegalArgumentException("Content cannot be null");
            }
            this.files.put(path, new File(path, Arrays.copyOf(newContent, newContent.length)));
            if (!this.added.contains(path))
            {
                this.modified.add(path);
            }
            return this;
        }

        public synchronized CommitBuilder move(String oldPath, String newPath)
        {
            return move(oldPath, newPath, null);
        }

        public synchronized CommitBuilder move(String oldPath, String newPath, byte[] content)
        {
            validateOpen();
            if (!this.files.containsKey(oldPath))
            {
                throw new IllegalStateException("File " + oldPath + " does not exist");
            }
            validatePath(newPath);
            if (this.files.containsKey(newPath))
            {
                throw new IllegalStateException("File " + newPath + " already exists");
            }

            File oldFile = this.files.remove(oldPath);
            File newFile = new File(newPath, (content == null) ? oldFile.content : Arrays.copyOf(content, content.length));
            this.files.put(newPath, newFile);
            if (this.added.remove(oldPath))
            {
                this.added.add(newPath);
            }
            else
            {
                // TODO handle move chains
                // TODO handle modifications
                this.movedFrom.put(newPath, oldPath);
                this.movedTo.put(oldPath, newPath);
            }
            return this;
        }

        public synchronized Revision commit(String committer, String message)
        {
            validateOpen();
            this.closed = true;
            return SimpleInMemoryVCS.this.commit(this, committer, message);
        }

        public synchronized void discard()
        {
            this.closed = true;
        }

        private void validateOpen()
        {
            if (this.closed)
            {
                throw new IllegalStateException("Commit builder closed");
            }
        }
    }

    public static class File
    {
        private final String path;
        private final byte[] content;

        private File(String path, byte[] content)
        {
            this.path = path;
            this.content = content;
        }

        public String getPath()
        {
            return this.path;
        }

        public byte[] getContent()
        {
            return Arrays.copyOf(this.content, this.content.length);
        }

        public InputStream getContentAsStream()
        {
            return new ByteArrayInputStream(this.content);
        }

        public String getContentAsString()
        {
            return new String(this.content, StandardCharsets.UTF_8);
        }
    }

    public static class Revision
    {
        private final String id;
        private final String committer;
        private final String author;
        private final Instant timestamp;
        private final String message;
        private final Map<String, File> files;
        private final Set<String> added;
        private final Set<String> modified;
        private final Set<String> deleted;
        private final Map<String, String> movedFrom;
        private final Map<String, String> movedTo;

        private Revision(String id, String committer, String author, Instant timestamp, String message, Map<String, File> files, Set<String> added, Set<String> modified, Set<String> deleted, Map<String, String> movedFrom, Map<String, String> movedTo)
        {
            this.id = id;
            this.committer = committer;
            this.author = author;
            this.timestamp = timestamp;
            this.message = message;
            this.files = files;
            this.added = added;
            this.modified = modified;
            this.deleted = deleted;
            this.movedFrom = movedFrom;
            this.movedTo = movedTo;
        }

        public String getId()
        {
            return this.id;
        }

        public String getCommitter()
        {
            return this.committer;
        }

        public String getAuthor()
        {
            return this.author;
        }

        public Instant getTimestamp()
        {
            return this.timestamp;
        }

        public String getMessage()
        {
            return this.message;
        }

        public boolean isPathAffectedByRevision(String path)
        {
            return isFilePathAffectedByRevision(path) || isDirectoryPathAffectedByRevision(path);
        }

        public boolean isFilePathAffectedByRevision(String filePath)
        {
            return this.added.contains(filePath) ||
                    this.deleted.contains(filePath) ||
                    this.modified.contains(filePath) ||
                    this.movedFrom.containsKey(filePath) ||
                    this.movedTo.containsKey(filePath);
        }

        public boolean isDirectoryPathAffectedByRevision(String dirPath)
        {
            Predicate<String> pred = path -> pathStartsWith(path, dirPath);
            return this.added.stream().anyMatch(pred) ||
                    this.deleted.stream().anyMatch(pred) ||
                    this.modified.stream().anyMatch(pred) ||
                    this.movedFrom.keySet().stream().anyMatch(pred) ||
                    this.movedTo.keySet().stream().anyMatch(pred);
        }

        private File getFile(String path)
        {
            return this.files.get(path);
        }

        private Stream<File> getFiles()
        {
            return this.files.values().stream();
        }
    }

    private static class ImmutableSimpleInMemoryVCS extends SimpleInMemoryVCS
    {
        private ImmutableSimpleInMemoryVCS(List<Revision> revisions)
        {
            super(revisions);
        }

        @Override
        public CommitBuilder newCommitBuilder(String author)
        {
            throw new UnsupportedOperationException("Not allowed");
        }

        @Override
        public void deleteBranch(String branchName)
        {
            throw new UnsupportedOperationException("Not allowed");
        }

        @Override
        public SimpleInMemoryVCS newBranch(String branchName)
        {
            throw new UnsupportedOperationException("Not allowed");
        }

        @Override
        public void mergeBranchToTrunk(String branchName)
        {
            throw new UnsupportedOperationException("Not allowed");
        }

        @Override
        public SimpleInMemoryVCS newVersionTag(VersionId versionId)
        {
            throw new UnsupportedOperationException("Not allowed");
        }
    }
}
