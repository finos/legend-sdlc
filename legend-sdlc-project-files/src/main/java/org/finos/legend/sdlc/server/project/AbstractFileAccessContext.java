// Copyright 2021 Goldman Sachs
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

package org.finos.legend.sdlc.server.project;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider.ProjectFile;

import java.util.stream.Stream;

public abstract class AbstractFileAccessContext implements ProjectFileAccessProvider.FileAccessContext
{
    @Override
    public Stream<ProjectFile> getFiles()
    {
        return getFilesInCanonicalDirectories(Lists.fixedSize.with(ProjectPaths.ROOT_DIRECTORY));
    }

    @Override
    public Stream<ProjectFile> getFilesInDirectory(String directory)
    {
        return getFilesInCanonicalDirectories(Lists.fixedSize.with(ProjectPaths.canonicalizeDirectory(directory)));
    }

    @Override
    public Stream<ProjectFile> getFilesInDirectories(Stream<? extends String> directories)
    {
        MutableList<String> canonicalDirectories = ProjectPaths.canonicalizeAndReduceDirectories(directories);
        return canonicalDirectories.isEmpty() ? Stream.empty() : getFilesInCanonicalDirectories(canonicalDirectories);
    }

    @Override
    public Stream<ProjectFile> getFilesInDirectories(Iterable<? extends String> directories)
    {
        MutableList<String> canonicalDirectories = ProjectPaths.canonicalizeAndReduceDirectories(directories);
        return canonicalDirectories.isEmpty() ? Stream.empty() : getFilesInCanonicalDirectories(canonicalDirectories);
    }

    /**
     * Get all the files in a non-empty, canonicalized, and reduced list of directories. Directory names are in a
     * canonical form (starting and ending with /), there are no duplicates, and no directory in the list is a
     * sub-directory of any other directory in the list. A consequence of this is that if the root directory, /, is
     * present, it will be the only element in the list.
     *
     * @param directories non-empty, canonicalized, reduced list of directories
     * @return stream of project files in the given directories
     */
    protected abstract Stream<ProjectFile> getFilesInCanonicalDirectories(MutableList<String> directories);
}
