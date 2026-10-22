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

package org.finos.legend.sdlc.project.files;

import org.eclipse.collections.api.list.MutableList;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider.FileAccessContext;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider.ProjectFile;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * A file access context rooted at a subdirectory of another context: paths given to this context are resolved
 * against the subroot of the delegate, and paths of files returned by this context have the subroot stripped, so
 * that a consumer sees the subtree as a complete project. Rooting is a storage-layer concern (re-architecture
 * section 4.2): everything above the storage SPI — structure, entities, configuration — is oblivious to it. This is
 * how a Legend model living at a subpath of a larger repository is presented as a project of its own.
 */
public class RootedFileAccessContext extends AbstractFileAccessContext
{
    private final FileAccessContext delegate;
    private final String root;

    private RootedFileAccessContext(FileAccessContext delegate, String canonicalRoot)
    {
        this.delegate = delegate;
        this.root = canonicalRoot;
    }

    @Override
    protected Stream<ProjectFile> getFilesInCanonicalDirectories(MutableList<String> directories)
    {
        return this.delegate.getFilesInDirectories(directories.collect(this::resolveDirectory))
                .map(file -> ProjectFiles.newDelegatingProjectFile(stripRoot(file.getPath()), path -> file));
    }

    @Override
    public ProjectFile getFile(String path)
    {
        String rootedPath = resolveFile(path);
        ProjectFile file = this.delegate.getFile(rootedPath);
        return (file == null) ? null : ProjectFiles.newDelegatingProjectFile(stripRoot(file.getPath()), p -> file);
    }

    @Override
    public boolean fileExists(String path)
    {
        return this.delegate.fileExists(resolveFile(path));
    }

    String resolveFile(String path)
    {
        return this.root + ProjectPaths.canonicalizeFile(path).substring(1);
    }

    private String resolveDirectory(String canonicalDirectory)
    {
        // both root and the directory are in canonical directory form (leading and trailing slash)
        return ProjectPaths.ROOT_DIRECTORY.equals(canonicalDirectory) ? this.root : (this.root + canonicalDirectory.substring(1));
    }

    private String stripRoot(String path)
    {
        if (!path.startsWith(this.root))
        {
            throw new IllegalStateException("Path \"" + path + "\" from the delegate context is not under the root \"" + this.root + "\"");
        }
        return path.substring(this.root.length() - 1);
    }

    /**
     * Root a file access context at a subdirectory. If the root is the root directory, the context is returned
     * unwrapped.
     *
     * @param delegate context to root
     * @param root     subdirectory path (e.g. {@code "/analytics/model"})
     * @return context presenting the subdirectory as a complete project
     */
    public static FileAccessContext root(FileAccessContext delegate, String root)
    {
        Objects.requireNonNull(delegate, "delegate may not be null");
        String canonicalRoot = ProjectPaths.canonicalizeDirectory(Objects.requireNonNull(root, "root may not be null"));
        return ProjectPaths.ROOT_DIRECTORY.equals(canonicalRoot) ? delegate : new RootedFileAccessContext(delegate, canonicalRoot);
    }
}
