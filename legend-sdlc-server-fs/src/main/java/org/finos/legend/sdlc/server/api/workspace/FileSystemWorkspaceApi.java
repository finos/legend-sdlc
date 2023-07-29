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

package org.finos.legend.sdlc.server.api.workspace;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.FSConfiguration;
import org.finos.legend.sdlc.server.domain.api.workspace.*;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.exception.UnavailableFeature;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import javax.inject.Inject;

public class FileSystemWorkspaceApi implements WorkspaceApi
{

    @Inject
    public FileSystemWorkspaceApi()
    {
    }

    @Override
    public Workspace getWorkspace(String projectId, WorkspaceSpecification workspaceSpecification)
    {
        Ref branch = getGitBranch(projectId, workspaceSpecification.getId());
        return workspaceBranchToWorkspace(projectId, null, branch, workspaceSpecification.getType(), workspaceSpecification.getAccessType());
    }

    @Override
    public List<Workspace> getWorkspaces(String projectId, Set<WorkspaceType> types, Set<ProjectFileAccessProvider.WorkspaceAccessType> accessTypes, Set<WorkspaceSource> sources)
    {
        if (sources == null)
        {
            throw new UnsupportedOperationException("Not implemented");
        }

        Set<WorkspaceType> resolvedTypes = (types == null) ? EnumSet.allOf(WorkspaceType.class) : types;
        Set<ProjectFileAccessProvider.WorkspaceAccessType> resolvedAccessTypes = (accessTypes == null) ? EnumSet.allOf(ProjectFileAccessProvider.WorkspaceAccessType.class) : accessTypes;

        Repository repository = null;
        try
        {
            repository = retrieveRepo(projectId);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        System.out.println("Getting workspaces for project:" + repository.getConfig().getString("project", null, "name"));
        MutableList<Workspace> result = Lists.mutable.empty();
        // currently only WORKSPACE access type is supported
        if (resolvedAccessTypes.contains(ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE))
        {
            if (resolvedTypes.contains(WorkspaceType.GROUP))
            {
                Collection<? extends Workspace> UserWS = getBranchesByType(repository, "user", projectId, WorkspaceType.USER);
                result.addAllIterable(UserWS);
            }
            if (resolvedTypes.contains(WorkspaceType.USER))
            {
                Collection<? extends Workspace> GroupWS = getBranchesByType(repository, "group", projectId, WorkspaceType.GROUP);
                result.addAllIterable(GroupWS);
            }
        }
        return result;
    }

    @Override
    public List<Workspace> getAllWorkspaces(String projectId, Set<WorkspaceType> types, Set<ProjectFileAccessProvider.WorkspaceAccessType> accessTypes, Set<WorkspaceSource> sources)
    {
        return this.getWorkspaces(projectId, types, accessTypes, sources);
    }

    @Override
    public Workspace newWorkspace(String projectId, String workspaceId, WorkspaceType type, WorkspaceSource source)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceId, "workspaceId may not be null");

        Ref branchRef = null;
        try
        {
            Repository repository = retrieveRepo(projectId);
            Git git = new Git(repository);
            git.checkout().setName("master").call();
            branchRef = git.branchCreate().setName(workspaceId).call();
            git.getRepository().getConfig().setString("branch", workspaceId, "type", type.getLabel());
            git.getRepository().getConfig().save();
            System.out.println("Created branch: " + branchRef.getName());
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return workspaceBranchToWorkspace(projectId, null, branchRef, type, null);
    }

    @Override
    public void deleteWorkspace(String projectId, WorkspaceSpecification workspaceSpecification)
    {
    }

    @Override
    public boolean isWorkspaceOutdated(String projectId, WorkspaceSpecification workspaceSpecification)
    {
        return false;
    }

    @Override
    public boolean isWorkspaceInConflictResolutionMode(String projectId, WorkspaceSpecification workspaceSpecification)
    {
        return false;
    }

    @Override
    public WorkspaceUpdateReport updateWorkspace(String projectId, WorkspaceSpecification workspaceSpecification)
    {
        throw UnavailableFeature.exception();
    }

    private Collection<? extends Workspace> getBranchesByType(Repository repository, String branchType, String projectId, WorkspaceType wType)
    {
        List<Ref> branchesOfType = new ArrayList<>();
        try
        {
            List<Ref> allBranches = Git.wrap(repository).branchList().setListMode(ListBranchCommand.ListMode.ALL).call();
            for (Ref branch : allBranches)
            {
                String type = repository.getConfig().getString("branch", Repository.shortenRefName(branch.getName()), "type");
                if (type != null && type.equals(branchType))
                {
                    branchesOfType.add(branch);
                }
            }
        }
        catch (GitAPIException e)
        {
            e.printStackTrace();
        }
        return branchesOfType.stream().map(branch -> workspaceBranchToWorkspace(projectId, null, branch, wType, null)).collect(Collectors.toList());
    }

    private static Workspace workspaceBranchToWorkspace(String projectId, VersionId patchReleaseVersionId, Ref branch, WorkspaceType workspaceType, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType)
    {
        return (branch == null) ? null : fromWorkspaceBranchName(projectId, patchReleaseVersionId, branch, workspaceType, workspaceAccessType);
    }

    protected static Workspace fromWorkspaceBranchName(String projectId, VersionId patchReleaseVersionId, Ref branch, WorkspaceType workspaceType, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType)
    {
        String userId = workspaceType == WorkspaceType.GROUP ? null : "user";
        return new Workspace()
        {
            @Override
            public String getProjectId()
            {
                return projectId;
            }

            @Override
            public String getUserId()
            {
                return userId;
            }

            @Override
            public String getWorkspaceId()
            {
                return Repository.shortenRefName(branch.getName());
            }
        };
    }

    public static  Repository retrieveRepo(String projectId) throws IOException
    {
        File[] repoDirs = new File(FSConfiguration.getRootDirectory()).listFiles(File::isDirectory);
        if (repoDirs != null)
        {
            for (File repoDir : repoDirs)
            {
                Repository repository = FileRepositoryBuilder.create(new File(repoDir, ".git"));
                String storedProjectId = repository.getConfig().getString("project", null, "id");
                if (projectId.equals(storedProjectId))
                {
                    return repository;
                }
            }
        }
        return null;
    }

    public static Ref getGitBranch(String projectId, String workspaceId)
    {
        try
        {
            Repository repository = retrieveRepo(projectId);
            List<Ref> allBranches = Git.wrap(repository).branchList().setListMode(ListBranchCommand.ListMode.ALL).call();
            for (Ref branch : allBranches)
            {
                if (Repository.shortenRefName(branch.getName()).equals(workspaceId))
                {
                    return branch;
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return null;
    }

}
