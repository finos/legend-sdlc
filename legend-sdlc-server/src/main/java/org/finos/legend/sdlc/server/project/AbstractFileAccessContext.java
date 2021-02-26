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
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider.ProjectFile;

import java.util.stream.Stream;

public abstract class AbstractFileAccessContext implements ProjectFileAccessProvider.FileAccessContext
{
    @Override
    public Stream<ProjectFile> getFiles()
    {
        return getFilesInCanonicalDirectories(Lists.fixedSize.with(ROOT_DIRECTORY));
    }

    @Override
    public Stream<ProjectFile> getFilesInDirectory(String directory)
    {
        return getFilesInCanonicalDirectories(Lists.fixedSize.with(canonicalizeDirectory(directory)));
    }

    @Override
    public Stream<ProjectFile> getFilesInDirectories(Iterable<? extends String> directories)
    {
        MutableList<String> canonicalDirectories = canonicalizeAndReduceDirectories(directories);
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

    private static String canonicalizeDirectory(String directory)
    {
        switch (directory.length())
        {
            case 0:
            {
                return ROOT_DIRECTORY;
            }
            case 1:
            {
                return ROOT_DIRECTORY.equals(directory) ? ROOT_DIRECTORY : ("/" + directory + "/");
            }
            default:
            {
                return directory.startsWith("/") ?
                        (directory.endsWith("/") ? directory : (directory + "/")) :
                        (directory.endsWith("/") ? ("/" + directory) : ("/" + directory + "/"));
            }
        }
    }

    /**
     * Canonicalize and reduce a set of directories. In the resulting list, directory names are in a canonical form
     * (starting and ending with /), there are no duplicates, and no directory in the list is a sub-directory of any
     * other directory in the list. A consequence of this is that if the root directory, /, if present, it will be the
     * only directory in the list.
     *
     * @param directories iterable of directories
     * @return canonicalized and reduced set of directories
     */
    private static MutableList<String> canonicalizeAndReduceDirectories(Iterable<? extends String> directories)
    {
        MutableList<String> canonicalDirectories = Iterate.collect(directories, AbstractFileAccessContext::canonicalizeDirectory, Lists.mutable.empty());
        if (canonicalDirectories.size() > 1)
        {
            // find and remove any sub-directories from the list
            canonicalDirectories.sortThis(AbstractFileAccessContext::compareCanonicalDirectories);

            if (ROOT_DIRECTORY.equals(canonicalDirectories.get(0)))
            {
                // special case: all other directories are sub-directories of /
                return Lists.fixedSize.with(ROOT_DIRECTORY);
            }

            int lastKept = 0;
            for (int i = 1; i < canonicalDirectories.size(); i++)
            {
                String directory = canonicalDirectories.get(i);
                if (canonicalDirectories.subList(0, i).noneSatisfy(directory::startsWith))
                {
                    if (++lastKept != i)
                    {
                        canonicalDirectories.set(lastKept, directory);
                    }
                }
            }
            int newSize = lastKept + 1;
            while (canonicalDirectories.size() > newSize)
            {
                canonicalDirectories.remove(canonicalDirectories.size() - 1);
            }
        }
        return canonicalDirectories;
    }

    private static int compareCanonicalDirectories(String d1, String d2)
    {
        int cmp = Integer.compare(d1.length(), d2.length());
        return (cmp == 0) ? d1.compareTo(d2) : cmp;
    }
}
