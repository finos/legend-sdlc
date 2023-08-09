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
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.server.api.BaseFSApi;
import org.finos.legend.sdlc.server.api.user.FileSystemUserApi;
import org.finos.legend.sdlc.server.domain.api.workspace.PatchWorkspaceSource;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceApi;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSource;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSpecification;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.exception.FSException;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import javax.inject.Inject;
import javax.ws.rs.core.Response;

import static org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification.workspaceSourceSpecification;

public class FileSystemWorkspaceApi extends BaseFSApi implements WorkspaceApi
{
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemWorkspaceApi.class);

    private static final String WORKSPACE_BRANCH_PREFIX = "workspace";
    private static final String CONFLICT_RESOLUTION_BRANCH_PREFIX = "resolution";
    private static final String BACKUP_BRANCH_PREFIX = "backup";
    private static final String GROUP_WORKSPACE_BRANCH_PREFIX = "group";
    private static final String GROUP_CONFLICT_RESOLUTION_BRANCH_PREFIX = "group-resolution";
    private static final String GROUP_BACKUP_BRANCH_PREFIX = "group-backup";
    private static final String PATCH_WORKSPACE_BRANCH_PREFIX = "patch";

    protected static final char BRANCH_DELIMITER = '/';

    @Inject
    public FileSystemWorkspaceApi()
    {
    }

    @Override
    public Workspace getWorkspace(String projectId, WorkspaceSpecification workspaceSpecification)
    {
        String refBranchName = Constants.R_HEADS + getRefBranchName(workspaceSourceSpecification(workspaceSpecification));
        Ref branch = getGitBranch(projectId, refBranchName);
        return workspaceBranchToWorkspace(projectId, branch, workspaceSpecification.getType());
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

        Repository repository = retrieveRepo(projectId);
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
        LegendSDLCServerException.validateNonNull(type, "workspace type may not be null");
        LegendSDLCServerException.validateNonNull(source, "workspace source may not be null");
        validateWorkspaceId(workspaceId);

        WorkspaceSpecification workspaceSpecification = WorkspaceSpecification.newWorkspaceSpecification(workspaceId, type, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE, source);
        String workspaceBranchName = getWorkspaceBranchName(workspaceSpecification);

        Ref branchRef;
        try
        {
            Repository repository = retrieveRepo(projectId);
            Git git = new Git(repository);
            git.checkout().setName("master").call();
            branchRef = git.branchCreate().setName(workspaceBranchName).call();
            git.getRepository().getConfig().setString("branch", workspaceBranchName, "type", type.getLabel());
            git.getRepository().getConfig().save();
        }
        catch (Exception e)
        {
            throw FSException.getLegendSDLCServerException("Failed to create workspace " + workspaceBranchName + " for project " + projectId, e);
        }
        return workspaceBranchToWorkspace(projectId, branchRef, workspaceSpecification.getType());
    }

    private static void validateWorkspaceId(String idString)
    {
        validateWorkspaceId(idString, null);
    }

    private static void validateWorkspaceId(String idString, Response.Status errorStatus)
    {
        if (!isValidWorkspaceId(idString))
        {
            throw new LegendSDLCServerException("Invalid workspace id: \"" + idString + "\". A workspace id must be a non-empty string consisting of characters from the following set: {a-z, A-Z, 0-9, _, ., -}. The id may not contain \"..\" and may not start or end with '.' or '-'.", (errorStatus == null) ? Response.Status.BAD_REQUEST : errorStatus);
        }
    }

    private static boolean isValidWorkspaceId(String string)
    {
        if ((string == null) || string.isEmpty())
        {
            return false;
        }

        if (!isValidWorkspaceStartEndChar(string.charAt(0)))
        {
            return false;
        }
        int lastIndex = string.length() - 1;
        for (int i = 1; i < lastIndex; i++)
        {
            char c = string.charAt(i);
            boolean isValid = isValidWorkspaceStartEndChar(c) || (c == '-') || ((c == '.') && (string.charAt(i - 1) != '.'));
            if (!isValid)
            {
                return false;
            }
        }
        return isValidWorkspaceStartEndChar(string.charAt(lastIndex));
    }

    private static boolean isValidWorkspaceStartEndChar(char c)
    {
        return (c == '_') || (('a' <= c) && (c <= 'z')) || (('A' <= c) && (c <= 'Z')) || (('0' <= c) && (c <= '9'));
    }

    public static String getWorkspaceBranchName(WorkspaceSpecification workspaceSpec)
    {
        String userId = (workspaceSpec.getType() == WorkspaceType.GROUP) ? null : FileSystemUserApi.LOCAL_USER.getUserId();
        return getWorkspaceBranchName(workspaceSpec, userId);
    }

    protected static String getWorkspaceBranchName(WorkspaceSpecification workspaceSpec, String currentUser)
    {
        StringBuilder builder = new StringBuilder();
        WorkspaceSource source = workspaceSpec.getSource();
        if (source instanceof PatchWorkspaceSource)
        {
            builder.append(PATCH_WORKSPACE_BRANCH_PREFIX).append(BRANCH_DELIMITER);
            ((PatchWorkspaceSource) source).getPatchVersionId().appendVersionIdString(builder).append(BRANCH_DELIMITER);
        }
        builder.append(getWorkspaceBranchNamePrefix(workspaceSpec.getType(), workspaceSpec.getAccessType())).append(BRANCH_DELIMITER);
        if (workspaceSpec.getType() == WorkspaceType.USER)
        {
            String userId = workspaceSpec.getUserId();
            builder.append((userId == null) ? currentUser : userId).append(BRANCH_DELIMITER);
        }
        return builder.append(workspaceSpec.getId()).toString();
    }

    protected static String getWorkspaceBranchNamePrefix(WorkspaceType workspaceType, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType)
    {
        assert workspaceAccessType != null : "Workspace access type has been used but it is null, which should not be the case";

        switch (workspaceType)
        {
            case GROUP:
            {
                switch (workspaceAccessType)
                {
                    case WORKSPACE:
                    {
                        return GROUP_WORKSPACE_BRANCH_PREFIX;
                    }
                    case CONFLICT_RESOLUTION:
                    {
                        return GROUP_CONFLICT_RESOLUTION_BRANCH_PREFIX;
                    }
                    case BACKUP:
                    {
                        return GROUP_BACKUP_BRANCH_PREFIX;
                    }
                    default:
                    {
                        throw new RuntimeException("Unknown workspace access type: " + workspaceAccessType);
                    }
                }
            }
            case USER:
            {
                switch (workspaceAccessType)
                {
                    case WORKSPACE:
                    {
                        return WORKSPACE_BRANCH_PREFIX;
                    }
                    case CONFLICT_RESOLUTION:
                    {
                        return CONFLICT_RESOLUTION_BRANCH_PREFIX;
                    }
                    case BACKUP:
                    {
                        return BACKUP_BRANCH_PREFIX;
                    }
                    default:
                    {
                        throw new RuntimeException("Unknown workspace access type: " + workspaceAccessType);
                    }
                }
            }
            default:
            {
                throw new RuntimeException("Unknown workspace type: " + workspaceType);
            }
        }
    }

    @Override
    public void deleteWorkspace(String projectId, WorkspaceSpecification workspaceSpecification)
    {
        throw new UnsupportedOperationException("Not implemented");
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
        throw FSException.unavailableFeature();
    }

    private Collection<? extends Workspace> getBranchesByType(Repository repository, String branchType, String projectId, WorkspaceType wType)
    {
        List<Workspace> branchesOfType = new ArrayList<>();
        try
        {
            List<Ref> allBranches = Git.wrap(repository).branchList().setListMode(ListBranchCommand.ListMode.ALL).call();
            for (Ref branch : allBranches)
            {
                String type = repository.getConfig().getString("branch", Repository.shortenRefName(branch.getName()), "type");
                if (type != null && type.equals(branchType))
                {
                    branchesOfType.add(workspaceBranchToWorkspace(projectId, branch, wType));
                }
            }
        }
        catch (Exception e)
        {
            LOGGER.error("Error occurred during branch list operation for project {}", projectId, e);
            throw FSException.getLegendSDLCServerException("Failed to fetch " + branchType + "workspaces for project " + projectId, e);
        }
        return branchesOfType;
    }

    private static Workspace workspaceBranchToWorkspace(String projectId, Ref branch, WorkspaceType workspaceType)
    {
        String workspaceIdWithType = Repository.shortenRefName(branch.getName());
        String workspaceId = workspaceIdWithType.substring(workspaceIdWithType.lastIndexOf('/') + 1);

        WorkspaceSpecification workspaceSpecification = WorkspaceSpecification.newWorkspaceSpecification(workspaceId, workspaceType, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE, null);
        return (branch == null) ? null : fromWorkspaceBranchName(projectId, workspaceSpecification);
    }

    protected static Workspace fromWorkspaceBranchName(String projectId, WorkspaceSpecification workspaceSpecification)
    {
        String userId = workspaceSpecification.getType() == WorkspaceType.GROUP ? null : FileSystemUserApi.LOCAL_USER.getUserId();
        String workspaceId = workspaceSpecification.getId();
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
                return workspaceId;
            }
        };
    }
}
