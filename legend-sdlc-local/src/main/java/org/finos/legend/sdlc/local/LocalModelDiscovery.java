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

package org.finos.legend.sdlc.local;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Discovery of Legend models in a directory tree (re-architecture section 4.2): a checkout may contain any number
 * of models, each at its own root, alongside arbitrary non-Legend content. A directory is a model root when it
 * contains a {@code project.json}; discovery reports every such directory (in stable path order) and does not
 * judge — a checkout that nests {@code project.json} files (for example, in test resources) sees them all reported,
 * and the caller decides which to open.
 */
public class LocalModelDiscovery
{
    private static final String PROJECT_CONFIG_FILE_NAME = "project.json";

    private LocalModelDiscovery()
    {
    }

    /**
     * Find model roots under a directory tree, with the default pruning: directories whose name starts with a dot
     * (version control and IDE metadata) and the common build-output directories {@code target} and
     * {@code node_modules} are not descended into.
     *
     * @param treeRoot directory tree to scan
     * @return model root directories, in stable path order
     */
    public static List<Path> findModelRoots(Path treeRoot)
    {
        return findModelRoots(treeRoot, LocalModelDiscovery::defaultDescendInto);
    }

    /**
     * Find model roots under a directory tree. The predicate controls pruning: it is consulted for each
     * subdirectory (never for the tree root itself), and subtrees for which it returns false are skipped entirely.
     *
     * @param treeRoot    directory tree to scan
     * @param descendInto predicate deciding whether a subdirectory is scanned
     * @return model root directories, in stable path order
     */
    public static List<Path> findModelRoots(Path treeRoot, Predicate<? super Path> descendInto)
    {
        Objects.requireNonNull(treeRoot, "treeRoot may not be null");
        Objects.requireNonNull(descendInto, "descendInto may not be null");
        if (!Files.isDirectory(treeRoot))
        {
            throw new IllegalArgumentException("Not a directory: " + treeRoot);
        }
        Path start = treeRoot.toAbsolutePath().normalize();
        List<Path> modelRoots = new ArrayList<>();
        try
        {
            Files.walkFileTree(start, new SimpleFileVisitor<Path>()
            {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs)
                {
                    if (!start.equals(directory) && !descendInto.test(directory))
                    {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                {
                    if (attrs.isRegularFile() && PROJECT_CONFIG_FILE_NAME.equals(String.valueOf(file.getFileName())))
                    {
                        modelRoots.add(file.getParent());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        catch (IOException e)
        {
            throw new UncheckedIOException("Error scanning " + treeRoot, e);
        }
        modelRoots.sort(Comparator.comparing(Path::toString));
        return modelRoots;
    }

    private static boolean defaultDescendInto(Path directory)
    {
        String name = String.valueOf(directory.getFileName());
        return !name.startsWith(".") && !"target".equals(name) && !"node_modules".equals(name);
    }
}
