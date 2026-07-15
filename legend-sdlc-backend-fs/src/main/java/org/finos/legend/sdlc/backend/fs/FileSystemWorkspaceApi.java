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

package org.finos.legend.sdlc.backend.fs;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.finos.legend.sdlc.backend.api.spi.BackendCapability;
import org.finos.legend.sdlc.backend.api.workspace.WorkspaceApi;
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.error.LegendSDLCException;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider.WorkspaceAccessType;
import org.finos.legend.sdlc.project.source.SourceSpecification;
import org.finos.legend.sdlc.project.workspace.WorkspaceSource;
import org.finos.legend.sdlc.project.workspace.WorkspaceSpecification;

import java.util.List;
import java.util.Set;

/**
 * Workspace lifecycle over the file-system storage provider: a workspace is a git branch named
 * {@code workspace/{user}/{id}}, created from the {@code master} tip. Only project-sourced user workspaces with
 * the standard access type exist (the backend declares {@code USER_WORKSPACES} only). The provider has no
 * source-into-branch merge, so {@code updateWorkspace} reports {@code NO_OP} when the workspace is current and
 * fails with 501 otherwise.
 */
public class FileSystemWorkspaceApi implements WorkspaceApi
{
    private static final String ALL_WORKSPACE_BRANCHES_PREFIX = "workspace/";

    private final FileSystemBackend backend;
    private final FileSystemProjectFileAccessProvider fileAccessProvider;

    FileSystemWorkspaceApi(FileSystemBackend backend, FileSystemProjectFileAccessProvider fileAccessProvider)
    {
        this.backend = backend;
        this.fileAccessProvider = fileAccessProvider;
    }

    @Override
    public Workspace getWorkspace(String projectId, WorkspaceSpecification workspaceSpecification)
    {
        Ref branch = resolveWorkspaceBranch(projectId, workspaceSpecification);
        if (branch == null)
        {
            throw new LegendSDLCException("Unknown workspace in project " + projectId + ": " + workspaceSpecification, 404);
        }
        return toWorkspace(projectId, branch);
    }

    @Override
    public List<Workspace> getWorkspaces(String projectId, Set<WorkspaceType> types, Set<WorkspaceAccessType> accessTypes, Set<WorkspaceSource> sources)
    {
        return getWorkspaces(projectId, types, accessTypes, sources, this.fileAccessProvider.getUserId());
    }

    @Override
    public List<Workspace> getAllWorkspaces(String projectId, Set<WorkspaceType> types, Set<WorkspaceAccessType> accessTypes, Set<WorkspaceSource> sources)
    {
        return getWorkspaces(projectId, types, accessTypes, sources, null);
    }

    @Override
    public Workspace newWorkspace(String projectId, String workspaceId, WorkspaceType type, WorkspaceSource source)
    {
        LegendSDLCException.validateNonNull(projectId, "projectId may not be null", 400);
        LegendSDLCException.validateNonNull(workspaceId, "workspaceId may not be null", 400);
        LegendSDLCException.validateNonNull(type, "type may not be null", 400);
        validateWorkspaceId(workspaceId);

        WorkspaceSpecification workspaceSpec = WorkspaceSpecification.newWorkspaceSpecification(workspaceId, type, null, source, this.fileAccessProvider.getUserId());
        BackendCapability.checkSourceScope(this.backend, workspaceSpec.getSourceSpecification());

        String branchName = this.fileAccessProvider.getWorkspaceBranchName(workspaceSpec);
        Repository repository = this.fileAccessProvider.getRepository(projectId);
        try
        {
            if (repository.resolve(branchName) != null)
            {
                throw new LegendSDLCException("Workspace " + workspaceId + " already exists in project " + projectId, 409);
            }
            Ref branch = Git.wrap(repository).branchCreate()
                    .setName(branchName)
                    .setStartPoint(FileSystemProjectFileAccessProvider.MASTER_BRANCH)
                    .call();
            return toWorkspace(projectId, branch);
        }
        catch (Exception e)
        {
            throw FileSystemProjectFileAccessProvider.wrapException("Failed to create workspace " + workspaceId + " for project " + projectId, e);
        }
    }

    @Override
    public void deleteWorkspace(String projectId, WorkspaceSpecification workspaceSpecification)
    {
        Ref branch = resolveWorkspaceBranch(projectId, workspaceSpecification);
        if (branch == null)
        {
            throw new LegendSDLCException("Unknown workspace in project " + projectId + ": " + workspaceSpecification, 404);
        }
        Repository repository = this.fileAccessProvider.getRepository(projectId);
        try
        {
            Git.wrap(repository).branchDelete()
                    .setBranchNames(branch.getName())
                    .setForce(true)
                    .call();
        }
        catch (Exception e)
        {
            throw FileSystemProjectFileAccessProvider.wrapException("Failed to delete workspace in project " + projectId + ": " + workspaceSpecification, e);
        }
    }

    @Override
    public boolean isWorkspaceOutdated(String projectId, WorkspaceSpecification workspaceSpecification)
    {
        SourceSpecification workspaceSourceSpec = resolveExistingWorkspaceSourceSpecification(projectId, workspaceSpecification);
        Revision base = this.fileAccessProvider.getRevisionAccessContext(projectId, workspaceSourceSpec).getBaseRevision();
        Revision sourceCurrent = this.fileAccessProvider
                .getRevisionAccessContext(projectId, SourceSpecification.projectSourceSpecification())
                .getCurrentRevision();
        return (sourceCurrent != null) && ((base == null) || !sourceCurrent.getId().equals(base.getId()));
    }

    @Override
    public boolean isWorkspaceInConflictResolutionMode(String projectId, WorkspaceSpecification workspaceSpecification)
    {
        return false;
    }

    @Override
    public WorkspaceUpdateReport updateWorkspace(String projectId, WorkspaceSpecification workspaceSpecification)
    {
        SourceSpecification workspaceSourceSpec = resolveExistingWorkspaceSourceSpecification(projectId, workspaceSpecification);
        if (isWorkspaceOutdated(projectId, workspaceSpecification))
        {
            throw new LegendSDLCException("Updating an outdated workspace is not supported by the file-system backend", 501);
        }
        Revision base = this.fileAccessProvider.getRevisionAccessContext(projectId, workspaceSourceSpec).getBaseRevision();
        Revision current = this.fileAccessProvider.getRevisionAccessContext(projectId, workspaceSourceSpec).getCurrentRevision();
        String baseId = (base == null) ? null : base.getId();
        String currentId = (current == null) ? null : current.getId();
        return new WorkspaceUpdateReport()
        {
            @Override
            public WorkspaceUpdateReportStatus getStatus()
            {
                return WorkspaceUpdateReportStatus.NO_OP;
            }

            @Override
            public String getWorkspaceMergeBaseRevisionId()
            {
                return baseId;
            }

            @Override
            public String getWorkspaceRevisionId()
            {
                return currentId;
            }
        };
    }

    private List<Workspace> getWorkspaces(String projectId, Set<WorkspaceType> types, Set<WorkspaceAccessType> accessTypes, Set<WorkspaceSource> sources, String ownerId)
    {
        LegendSDLCException.validateNonNull(projectId, "projectId may not be null", 400);
        if (((types != null) && !types.contains(WorkspaceType.USER))
                || ((accessTypes != null) && !accessTypes.contains(WorkspaceAccessType.WORKSPACE))
                || ((sources != null) && !sources.contains(WorkspaceSource.projectWorkspaceSource())))
        {
            return Lists.mutable.empty();
        }
        Repository repository = this.fileAccessProvider.getRepository(projectId);
        String prefix = (ownerId == null) ? ALL_WORKSPACE_BRANCHES_PREFIX : this.fileAccessProvider.getWorkspaceBranchPrefix(ownerId);
        MutableList<Workspace> workspaces = Lists.mutable.empty();
        try
        {
            for (Ref branch : Git.wrap(repository).branchList().call())
            {
                String branchName = Repository.shortenRefName(branch.getName());
                if (branchName.startsWith(prefix) && (parseWorkspaceBranchName(branchName) != null))
                {
                    workspaces.add(toWorkspace(projectId, branch));
                }
            }
        }
        catch (Exception e)
        {
            throw FileSystemProjectFileAccessProvider.wrapException("Failed to fetch workspaces for project " + projectId, e);
        }
        return workspaces;
    }

    private SourceSpecification resolveExistingWorkspaceSourceSpecification(String projectId, WorkspaceSpecification workspaceSpecification)
    {
        Ref branch = resolveWorkspaceBranch(projectId, workspaceSpecification);
        if (branch == null)
        {
            throw new LegendSDLCException("Unknown workspace in project " + projectId + ": " + workspaceSpecification, 404);
        }
        return workspaceSpecification.getSourceSpecification();
    }

    private Ref resolveWorkspaceBranch(String projectId, WorkspaceSpecification workspaceSpecification)
    {
        LegendSDLCException.validateNonNull(projectId, "projectId may not be null", 400);
        LegendSDLCException.validateNonNull(workspaceSpecification, "workspace specification may not be null", 400);
        BackendCapability.checkSourceScope(this.backend, workspaceSpecification.getSourceSpecification());

        String branchName = this.fileAccessProvider.getWorkspaceBranchName(workspaceSpecification);
        Repository repository = this.fileAccessProvider.getRepository(projectId);
        try
        {
            return repository.exactRef(Constants.R_HEADS + branchName);
        }
        catch (Exception e)
        {
            throw FileSystemProjectFileAccessProvider.wrapException("Failed to resolve workspace in project " + projectId + ": " + workspaceSpecification, e);
        }
    }

    /**
     * Parse a (shortened) workspace branch name of the form {@code workspace/{user}/{id}}; return
     * {user, id}, or null if the branch is not a workspace branch.
     */
    private static String[] parseWorkspaceBranchName(String branchName)
    {
        String[] parts = branchName.split("/");
        return ((parts.length == 3) && "workspace".equals(parts[0])) ? new String[] {parts[1], parts[2]} : null;
    }

    private static Workspace toWorkspace(String projectId, Ref branch)
    {
        String branchName = Repository.shortenRefName(branch.getName());
        String[] userAndId = parseWorkspaceBranchName(branchName);
        if (userAndId == null)
        {
            throw new LegendSDLCException("Not a workspace branch: " + branchName, 500);
        }
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
                return userAndId[0];
            }

            @Override
            public String getWorkspaceId()
            {
                return userAndId[1];
            }
        };
    }

    private static void validateWorkspaceId(String idString)
    {
        if (!isValidWorkspaceId(idString))
        {
            throw new LegendSDLCException("Invalid workspace id: \"" + idString + "\". A workspace id must be a non-empty string consisting of characters from the following set: {a-z, A-Z, 0-9, _, ., -}. The id may not contain \"..\" and may not start or end with '.' or '-'.", 400);
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
}
