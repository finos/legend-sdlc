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

import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ProjectPaths
{
    public static final String PATH_SEPARATOR = "/";
    public static final String ROOT_DIRECTORY = PATH_SEPARATOR;

    public static String canonicalizeDirectory(String path)
    {
        switch (path.length())
        {
            case 0:
            {
                return ProjectPaths.ROOT_DIRECTORY;
            }
            case 1:
            {
                return ProjectPaths.ROOT_DIRECTORY.equals(path) ? ProjectPaths.ROOT_DIRECTORY : (PATH_SEPARATOR + path + PATH_SEPARATOR);
            }
            default:
            {
                return path.startsWith(PATH_SEPARATOR) ?
                        (path.endsWith(PATH_SEPARATOR) ? path : (path + PATH_SEPARATOR)) :
                        (path.endsWith(PATH_SEPARATOR) ? (PATH_SEPARATOR + path) : (PATH_SEPARATOR + path + PATH_SEPARATOR));
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
    public static MutableList<String> canonicalizeAndReduceDirectories(Stream<? extends String> directories)
    {
        MutableList<String> canonicalDirectories = directories.map(ProjectPaths::canonicalizeDirectory).collect(Collectors.toCollection(Lists.mutable::empty));
        return reduceCanonicalDirectories(canonicalDirectories);
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
    public static MutableList<String> canonicalizeAndReduceDirectories(Iterable<? extends String> directories)
    {
        MutableList<String> canonicalDirectories = Iterate.collect(directories, ProjectPaths::canonicalizeDirectory, Lists.mutable.empty());
        return reduceCanonicalDirectories(canonicalDirectories);
    }

    /**
     * Reduce a set of canonical directories by removing duplicates and sub-directories. The result is a list where
     * there are no duplicates and no directory is a sub-directory of any other.
     *
     * @param canonicalDirectories list of canonicalized directories
     * @return reduced list of directories
     */
    private static MutableList<String> reduceCanonicalDirectories(MutableList<String> canonicalDirectories)
    {
        if (canonicalDirectories.size() > 1)
        {
            // find and remove any sub-directories from the list
            canonicalDirectories.sort(Comparator.comparing(String::length).thenComparing(Comparator.naturalOrder()));

            if (ProjectPaths.ROOT_DIRECTORY.equals(canonicalDirectories.get(0)))
            {
                // special case: all other directories are sub-directories of /
                return Lists.fixedSize.with(ProjectPaths.ROOT_DIRECTORY);
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
}
