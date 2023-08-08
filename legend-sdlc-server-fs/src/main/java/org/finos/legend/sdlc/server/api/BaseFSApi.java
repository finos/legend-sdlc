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

package org.finos.legend.sdlc.server.api;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.revision.RevisionAlias;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.startup.FSConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;

public class BaseFSApi
{
    private static final Logger LOGGER = LoggerFactory.getLogger(BaseFSApi.class);
    protected static String rootDirectory;

    public static void initRootDirectory(FSConfiguration fsConfiguration)
    {
        rootDirectory = fsConfiguration.getRootDirectory();
    }

    public static Repository retrieveRepo(String projectId)
    {
        try
        {
            String repoDirPath = rootDirectory + File.separator + projectId + File.separator + ".git";
            File repoDir = new File(repoDirPath);
            if (repoDir.exists() && repoDir.isDirectory())
            {
                return FileRepositoryBuilder.create(repoDir);
            }
        }
        catch (IOException e)
        {
            LOGGER.error("Repository {} could not be accessed", projectId, e);
        }
        throw new LegendSDLCServerException("Repo " + projectId + " can't be found ", Response.Status.NOT_FOUND);
    }

    public static Ref getGitBranch(String projectId, String branchName)
    {
        String refBranchName = branchName;
        try
        {
            Repository repository = retrieveRepo(projectId);
            return repository.getRefDatabase().findRef(refBranchName);
        }
        catch (Exception e)
        {
            LOGGER.error("Error occurred during branch list operation for workspace {} in project {}", refBranchName, projectId, e);
        }
        throw new LegendSDLCServerException("Branch " + projectId + " does not exist", Response.Status.NOT_FOUND);
    }

    protected static String resolveRevisionId(String revisionId, ProjectFileAccessProvider.RevisionAccessContext revisionAccessContext)
    {
        if (revisionId == null)
        {
            throw new IllegalArgumentException("Resolving revision alias does not work with null revisionId, null handling must be done before using this method");
        }
        RevisionAlias revisionAlias = getRevisionAlias(revisionId);
        switch (revisionAlias)
        {
            case BASE:
            {
                Revision revision = revisionAccessContext.getBaseRevision();
                return (revision == null) ? null : revision.getId();
            }
            case HEAD:
            {
                Revision revision = revisionAccessContext.getCurrentRevision();
                return (revision == null) ? null : revision.getId();
            }
            case REVISION_ID:
            {
                return revisionId;
            }
            default:
            {
                throw new IllegalArgumentException("Unknown revision alias type: " + revisionAlias);
            }
        }
    }

    protected static RevisionAlias getRevisionAlias(String revisionId)
    {
        if (revisionId == null)
        {
            return null;
        }
        if (RevisionAlias.BASE.getValue().equalsIgnoreCase(revisionId))
        {
            return RevisionAlias.BASE;
        }
        if (RevisionAlias.HEAD.getValue().equalsIgnoreCase(revisionId) || RevisionAlias.CURRENT.getValue().equalsIgnoreCase(revisionId) || RevisionAlias.LATEST.getValue().equalsIgnoreCase(revisionId))
        {
            return RevisionAlias.HEAD;
        }
        return RevisionAlias.REVISION_ID;
    }
}
