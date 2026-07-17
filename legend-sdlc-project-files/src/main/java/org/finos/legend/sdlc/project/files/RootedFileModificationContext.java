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

import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider.FileModificationContext;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The modification-side counterpart of {@link RootedFileAccessContext}: operation paths are resolved against the
 * subroot before submission to the delegate. See that class for the rooting rationale.
 */
public class RootedFileModificationContext implements FileModificationContext
{
    private final FileModificationContext delegate;
    private final String root;

    private RootedFileModificationContext(FileModificationContext delegate, String canonicalRoot)
    {
        this.delegate = delegate;
        this.root = canonicalRoot;
    }

    @Override
    public Revision submit(String message, List<? extends ProjectFileOperation> operations)
    {
        return this.delegate.submit(message, operations.stream().map(this::resolveOperation).collect(Collectors.toList()));
    }

    private ProjectFileOperation resolveOperation(ProjectFileOperation operation)
    {
        if (operation instanceof ProjectFileOperation.AddFile)
        {
            return ProjectFileOperation.addFile(resolveFile(operation.getPath()), ((ProjectFileOperation.AddFile) operation).getContent());
        }
        if (operation instanceof ProjectFileOperation.ModifyFile)
        {
            return ProjectFileOperation.modifyFile(resolveFile(operation.getPath()), ((ProjectFileOperation.ModifyFile) operation).getNewContent());
        }
        if (operation instanceof ProjectFileOperation.DeleteFile)
        {
            return ProjectFileOperation.deleteFile(resolveFile(operation.getPath()));
        }
        if (operation instanceof ProjectFileOperation.MoveFile)
        {
            ProjectFileOperation.MoveFile moveFile = (ProjectFileOperation.MoveFile) operation;
            return (moveFile.getNewContent() == null) ?
                    ProjectFileOperation.moveFile(resolveFile(moveFile.getPath()), resolveFile(moveFile.getNewPath())) :
                    ProjectFileOperation.moveFile(resolveFile(moveFile.getPath()), resolveFile(moveFile.getNewPath()), moveFile.getNewContent());
        }
        throw new IllegalArgumentException("Unknown file operation type: " + operation);
    }

    private String resolveFile(String path)
    {
        return this.root + ProjectPaths.canonicalizeFile(path).substring(1);
    }

    /**
     * Root a file modification context at a subdirectory. If the root is the root directory, the context is
     * returned unwrapped.
     *
     * @param delegate context to root
     * @param root     subdirectory path (e.g. {@code "/analytics/model"})
     * @return context accepting operations with paths relative to the subdirectory
     */
    public static FileModificationContext root(FileModificationContext delegate, String root)
    {
        Objects.requireNonNull(delegate, "delegate may not be null");
        String canonicalRoot = ProjectPaths.canonicalizeDirectory(Objects.requireNonNull(root, "root may not be null"));
        return ProjectPaths.ROOT_DIRECTORY.equals(canonicalRoot) ? delegate : new RootedFileModificationContext(delegate, canonicalRoot);
    }
}
