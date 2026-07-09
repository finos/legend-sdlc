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

package org.finos.legend.sdlc.core.comparison;

/**
 * A backend-neutral description of a single file change between two revisions: the old and new paths plus which kind
 * of change it was. Backends with native diffing (e.g. git compare results) translate their diff type to this one to
 * use the generic comparison assembly in {@link ComparisonOperations}.
 */
public class FileDiff
{
    private final String oldPath;
    private final String newPath;
    private final boolean deleted;
    private final boolean created;
    private final boolean renamed;

    private FileDiff(String oldPath, String newPath, boolean deleted, boolean created, boolean renamed)
    {
        this.oldPath = oldPath;
        this.newPath = newPath;
        this.deleted = deleted;
        this.created = created;
        this.renamed = renamed;
    }

    public String getOldPath()
    {
        return this.oldPath;
    }

    public String getNewPath()
    {
        return this.newPath;
    }

    public boolean isDeleted()
    {
        return this.deleted;
    }

    public boolean isCreated()
    {
        return this.created;
    }

    public boolean isRenamed()
    {
        return this.renamed;
    }

    public static FileDiff newFileDiff(String oldPath, String newPath, boolean deleted, boolean created, boolean renamed)
    {
        return new FileDiff(oldPath, newPath, deleted, created, renamed);
    }
}
