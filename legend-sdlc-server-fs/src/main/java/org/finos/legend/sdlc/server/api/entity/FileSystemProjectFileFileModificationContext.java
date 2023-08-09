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

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.server.api.BaseFSApi;
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification;
import org.finos.legend.sdlc.server.domain.model.revision.FileSystemRevision;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.exception.FSException;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.project.ProjectFileOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class FileSystemProjectFileFileModificationContext extends BaseFSApi implements ProjectFileAccessProvider.FileModificationContext
{
    private final String projectId;
    private final String revisionId;
    private final SourceSpecification sourceSpecification;

    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemProjectFileFileModificationContext.class);

    public FileSystemProjectFileFileModificationContext(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
        this.projectId = projectId;
        this.revisionId = revisionId;
        this.sourceSpecification = Objects.requireNonNull(sourceSpecification, "source specification may not be null");
    }

    @Override
    public Revision submit(String message, List<? extends ProjectFileOperation> operations)
    {
        String workspaceId = getRefBranchName(sourceSpecification);
        try
        {
            int changeCount = operations.size();
            List<ProjectFileOperation> fileOperations = operations.stream().collect(Collectors.toCollection(() -> Lists.mutable.ofInitialCapacity(changeCount)));
            String referenceRevisionId = this.revisionId;
            if (referenceRevisionId != null)
            {
                String targetBranchRevision = FileSystemRevision.getFileSystemRevision(this.projectId, workspaceId).getId();
                if (!referenceRevisionId.equals(targetBranchRevision))
                {
                    String msg = "Expected " + sourceSpecification + " to be at revision " + referenceRevisionId + "; instead it was at revision " + targetBranchRevision;
                    LOGGER.info(msg);
                    throw new LegendSDLCServerException(msg, Response.Status.CONFLICT);
                }
            }
            Repository repo = BaseFSApi.retrieveRepo(this.projectId);
            Git git = new Git(repo);
            git.checkout().setName(workspaceId).call();
            for (ProjectFileOperation fileOperation : fileOperations)
            {
                if (fileOperation instanceof ProjectFileOperation.AddFile)
                {
                    File newFile = new File(repo.getDirectory().getParent(), fileOperation.getPath());
                    Files.createDirectories(newFile.toPath().getParent());
                    Files.write(newFile.toPath(), ((ProjectFileOperation.AddFile) fileOperation).getContent(), StandardOpenOption.CREATE_NEW);
                    git.add().addFilepattern(".").call();
                }
                else if (fileOperation instanceof ProjectFileOperation.ModifyFile)
                {
                    File file = new File(repo.getDirectory().getParent(), fileOperation.getPath());
                    if (file.exists())
                    {
                        Files.write(file.toPath(), ((ProjectFileOperation.ModifyFile) fileOperation).getNewContent());
                    }
                    else
                    {
                        throw new LegendSDLCServerException("File " + file + " does not exist");
                    }
                    git.add().addFilepattern(".").call();
                }
                else if (fileOperation instanceof ProjectFileOperation.DeleteFile)
                {
                    File fileToRemove = new File(repo.getWorkTree(), fileOperation.getPath().substring(1));
                    if (!fileToRemove.exists())
                    {
                        throw new LegendSDLCServerException("File " + fileToRemove + " does not exist");
                    }
                    fileToRemove.delete();
                    git.rm().addFilepattern(fileOperation.getPath().substring(1)).call();
                }
                else
                {
                    throw new LegendSDLCServerException(fileOperation + "operation is not yet supported");
                }
            }
            RevCommit revCommit = git.commit().setMessage(message).call();
            repo.close();
            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Committed {} changes to {}: {}", changeCount, sourceSpecification, revCommit.getId());
            }
            return FileSystemRevision.getFileSystemRevision(projectId, workspaceId);
        }
        catch (Exception e)
        {
            throw FSException.getLegendSDLCServerException("Error occurred while committing changes to workspace " + workspaceId + " of project " + projectId, e);
        }
    }
}
