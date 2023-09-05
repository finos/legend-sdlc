// Copyright 2020 Goldman Sachs
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

package org.finos.legend.sdlc.server.gitlab.api;

import org.eclipse.collections.api.factory.Lists;
import org.finos.legend.sdlc.domain.model.comparison.Comparison;
import org.finos.legend.sdlc.domain.model.comparison.EntityDiff;
import org.finos.legend.sdlc.domain.model.entity.change.EntityChangeType;
import org.finos.legend.sdlc.server.domain.api.comparison.ComparisonApi;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSpecification;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.GitLabConfiguration;
import org.finos.legend.sdlc.server.gitlab.GitLabProjectId;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.project.ProjectPaths;
import org.finos.legend.sdlc.server.project.ProjectStructure;
import org.finos.legend.sdlc.server.tools.BackgroundTaskProcessor;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.RepositoryApi;
import org.gitlab4j.api.models.Branch;
import org.gitlab4j.api.models.Commit;
import org.gitlab4j.api.models.CompareResults;
import org.gitlab4j.api.models.Diff;
import org.gitlab4j.api.models.DiffRef;
import org.gitlab4j.api.models.MergeRequest;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.ws.rs.core.Response;

public class GitLabComparisonApi extends GitLabApiWithFileAccess implements ComparisonApi
{
    @Inject
    public GitLabComparisonApi(GitLabConfiguration gitLabConfiguration, GitLabUserContext userContext, BackgroundTaskProcessor backgroundTaskProcessor)
    {
        super(gitLabConfiguration, userContext, backgroundTaskProcessor);
    }

    @Override
    public Comparison getWorkspaceCreationComparison(String projectId, WorkspaceSpecification workspaceSpecification)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceSpecification, "workspace specification may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        RepositoryApi repositoryApi = getGitLabApi().getRepositoryApi();

        Commit currentCommit;
        try
        {
            String workspaceBranchName = getWorkspaceBranchName(workspaceSpecification);
            Branch workspaceBranch = withRetries(() -> repositoryApi.getBranch(gitLabProjectId.getGitLabId(), workspaceBranchName));
            currentCommit = workspaceBranch.getCommit();
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to access the current revision for " + getReferenceInfo(projectId, workspaceSpecification),
                    () -> "Could not find current revision for " + getReferenceInfo(projectId, workspaceSpecification),
                    () -> "Failed to find current revision for " + getReferenceInfo(projectId, workspaceSpecification));
        }
        if (currentCommit == null)
        {
            throw new LegendSDLCServerException("Could not access current revision for " + getReferenceInfo(projectId, workspaceSpecification));
        }

        Commit creationCommit;
        try
        {
            String sourceBranchName = getSourceBranch(gitLabProjectId, workspaceSpecification);
            creationCommit = repositoryApi.getMergeBase(gitLabProjectId.getGitLabId(), Arrays.asList(sourceBranchName, currentCommit.getId()));
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to access the creation revision for " + getReferenceInfo(projectId, workspaceSpecification),
                    () -> "Could not find creation revision for " + getReferenceInfo(projectId, workspaceSpecification),
                    () -> "Failed to find creation revision for " + getReferenceInfo(projectId, workspaceSpecification));
        }
        if (creationCommit == null)
        {
            throw new LegendSDLCServerException("Could not access creation revision for " + getReferenceInfo(projectId, workspaceSpecification));
        }

        ProjectStructure currentProjectStructure = getProjectStructure(projectId, workspaceSpecification.getSourceSpecification(), currentCommit.getId());
        ProjectStructure creationProjectStructure = getProjectStructure(projectId, workspaceSpecification.getSourceSpecification(), creationCommit.getId());
        return getComparisonResult(gitLabProjectId, repositoryApi, creationCommit.getId(), currentCommit.getId(), creationProjectStructure, currentProjectStructure);
    }

    @Override
    public Comparison getWorkspaceSourceComparison(String projectId, WorkspaceSpecification workspaceSpecification)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceSpecification, "workspace specification may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        RepositoryApi repositoryApi = getGitLabApi().getRepositoryApi();

        Commit workspaceCommit;
        try
        {
            String workspaceBranchName = getWorkspaceBranchName(workspaceSpecification);
            Branch workspaceBranch = withRetries(() -> repositoryApi.getBranch(gitLabProjectId.getGitLabId(), workspaceBranchName));
            workspaceCommit = workspaceBranch.getCommit();
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to access the current revision for " + getReferenceInfo(projectId, workspaceSpecification),
                    () -> "Could not find current revision for " + getReferenceInfo(projectId, workspaceSpecification),
                    () -> "Failed to find current revision for " + getReferenceInfo(projectId, workspaceSpecification));
        }
        if (workspaceCommit == null)
        {
            throw new LegendSDLCServerException("Could not access current revision for " + getReferenceInfo(projectId, workspaceSpecification));
        }

        Commit sourceCommit;
        try
        {
            String sourceBranchName = getSourceBranch(gitLabProjectId, workspaceSpecification);
            Branch sourceBranch = withRetries(() -> repositoryApi.getBranch(gitLabProjectId.getGitLabId(), sourceBranchName));
            sourceCommit = sourceBranch.getCommit();
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to access the current revision for " + getReferenceInfo(projectId, workspaceSpecification.getSource()),
                    () -> "Could not find current revision for " + getReferenceInfo(projectId, workspaceSpecification.getSource()),
                    () -> "Failed to find current revision for " + getReferenceInfo(projectId, workspaceSpecification.getSource()));
        }
        if (sourceCommit == null)
        {
            throw new LegendSDLCServerException("Could not access current revision for " + getReferenceInfo(projectId, workspaceSpecification.getSource()));
        }

        ProjectStructure workspaceProjectStructure = getProjectStructure(projectId, workspaceSpecification.getSourceSpecification(), workspaceCommit.getId());
        ProjectStructure sourceProjectStructure = getProjectStructure(projectId, workspaceSpecification.getSource().getSourceSpecification(), sourceCommit.getId());
        return getComparisonResult(gitLabProjectId, repositoryApi, sourceCommit.getId(), workspaceCommit.getId(), sourceProjectStructure, workspaceProjectStructure);
    }

    @Override
    public Comparison getReviewComparison(String projectId, String reviewId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        GitLabApi gitLabApi = getGitLabApi();
        RepositoryApi repositoryApi = gitLabApi.getRepositoryApi();

        MergeRequest mergeRequest = getReviewMergeRequest(gitLabApi.getMergeRequestApi(), gitLabProjectId, reviewId);

        WorkspaceSpecification workspaceSpec = parseWorkspaceBranchName(mergeRequest.getSourceBranch());
        if (workspaceSpec == null)
        {
            throw new LegendSDLCServerException("Unknown review in project " + projectId + ": " + reviewId, Response.Status.NOT_FOUND);
        }

        DiffRef diffRef = mergeRequest.getDiffRefs();
        if ((diffRef == null) || (diffRef.getStartSha() == null) || (diffRef.getHeadSha() == null))
        {
            throw new LegendSDLCServerException("Unable to get revision info for review " + reviewId + " in project " + projectId);
        }

        String fromRevisionId = diffRef.getStartSha();
        String toRevisionId = diffRef.getHeadSha();
        ProjectStructure fromProjectStructure = getProjectStructure(projectId, workspaceSpec.getSourceSpecification(), fromRevisionId);
        ProjectStructure toProjectStructure = getProjectStructure(projectId, workspaceSpec.getSource().getSourceSpecification(), toRevisionId);
        return getComparisonResult(gitLabProjectId, repositoryApi, fromRevisionId, toRevisionId, fromProjectStructure, toProjectStructure);
    }

    @Override
    public Comparison getReviewWorkspaceCreationComparison(String projectId, String reviewId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        GitLabApi gitLabApi = getGitLabApi();
        RepositoryApi repositoryApi = gitLabApi.getRepositoryApi();
        MergeRequest mergeRequest = getReviewMergeRequest(gitLabApi.getMergeRequestApi(), gitLabProjectId, reviewId);

        WorkspaceSpecification workspaceSpec = parseWorkspaceBranchName(mergeRequest.getSourceBranch());
        if (workspaceSpec == null)
        {
            throw new LegendSDLCServerException("Unknown review in project " + projectId + ": " + reviewId, Response.Status.NOT_FOUND);
        }

        DiffRef diffRef = mergeRequest.getDiffRefs();
        if ((diffRef == null) || (diffRef.getBaseSha() == null) || (diffRef.getHeadSha() == null))
        {
            throw new LegendSDLCServerException("Unable to get revision info for review " + reviewId + " in project " + projectId);
        }

        String fromRevisionId = diffRef.getBaseSha();
        String toRevisionId = diffRef.getHeadSha();
        ProjectStructure fromProjectStructure = getProjectStructure(projectId, workspaceSpec.getSourceSpecification(), fromRevisionId);
        ProjectStructure toProjectStructure = getProjectStructure(projectId, workspaceSpec.getSourceSpecification(), toRevisionId);
        return getComparisonResult(gitLabProjectId, repositoryApi, fromRevisionId, toRevisionId, fromProjectStructure, toProjectStructure);
    }

    /**
     * FIXME: right now this file comparison might not mean much to us since what we ultimately care about is
     * entity comparison, but that might be obfuscated by a change in project structure. So we have to handle such case.
     */
    private Comparison getComparisonResult(GitLabProjectId gitLabProjectId, RepositoryApi repositoryApi, String fromRevisionId, String toRevisionId, ProjectStructure fromProjectStructure, ProjectStructure toProjectStructure)
    {
        try
        {
            CompareResults comparisonResult = repositoryApi.compare(gitLabProjectId.getGitLabId(), fromRevisionId, toRevisionId, true);
            return fromGitCompareResults(fromRevisionId, toRevisionId, comparisonResult, fromProjectStructure, toProjectStructure);
        }
        catch (Exception e)
        {
            throw buildException(e,
                () -> "User " + getCurrentUser() + " is not allowed to get Comparison Information from revision " + fromRevisionId + "  to revision " + toRevisionId + " on project" + gitLabProjectId.toString(),
                () -> "Could not find revisions " + fromRevisionId + " ," + toRevisionId + " on project" + gitLabProjectId.toString(),
                () -> "Failed to fetch Comparison Information from revision " + fromRevisionId + "  to revision " + toRevisionId + " on project" + gitLabProjectId.toString());
        }
    }

    private Comparison fromGitCompareResults(String fromRevisionId, String toRevisionId, CompareResults comparisonResult, ProjectStructure fromProjectStructure, ProjectStructure toProjectStructure)
    {
        if (comparisonResult == null)
        {
            return null;
        }
        Commit comparisonResultCommit = comparisonResult.getCommit();
        if (comparisonResultCommit != null && !comparisonResultCommit.getId().equals(toRevisionId))
        {
            throw new LegendSDLCServerException("Unexpected Comparison Result: toRevisionId does not match expected. Expected: " + toRevisionId + ", Actual: " + comparisonResultCommit.getId());
        }
        return newComparison(fromRevisionId, toRevisionId, comparisonResult.getDiffs(), fromProjectStructure, toProjectStructure);
    }

    private static Comparison newComparison(String fromRevisionId, String toRevisionId, List<Diff> deltas, ProjectStructure fromProjectStructure, ProjectStructure toProjectStructure)
    {
        List<EntityDiff> entityDiffs = Lists.mutable.empty();
        AtomicBoolean isProjectConfigurationUpdated = new AtomicBoolean(false);
        deltas.forEach(diff ->
        {
            // File changes can be of three types:
            // 1. entity file changes - which we should handle without any problem
            // 2. project configuration changes - which we will just capture by a boolean flag to indicate if there are any changes
            // 3. other files: e.g. users can go in and modify pom.xml, add some non-entity files, etc. we DO NOT keep track of these
            String oldPath = ProjectPaths.canonicalizeFile(diff.getOldPath());
            String newPath = ProjectPaths.canonicalizeFile(diff.getNewPath());

            // project configuration change
            if (ProjectStructure.PROJECT_CONFIG_PATH.equals(oldPath) || ProjectStructure.PROJECT_CONFIG_PATH.equals(newPath))
            {
                // technically, we know the only probable operation that can happen is MODIFICATION, CREATE is really an edge case
                isProjectConfigurationUpdated.set(true);
                return;
            }

            ProjectStructure.EntitySourceDirectory oldPathSourceDirectory = fromProjectStructure.findSourceDirectoryForEntityFilePath(oldPath);
            ProjectStructure.EntitySourceDirectory newPathSourceDirectory = toProjectStructure.findSourceDirectoryForEntityFilePath(newPath);

            // entity file change
            if ((oldPathSourceDirectory != null) || (newPathSourceDirectory != null))
            {
                String oldEntityPath = (oldPathSourceDirectory == null) ? oldPath : oldPathSourceDirectory.filePathToEntityPath(oldPath);
                String newEntityPath = (newPathSourceDirectory == null) ? newPath : newPathSourceDirectory.filePathToEntityPath(newPath);
                EntityChangeType entityChangeType;
                if (diff.getDeletedFile())
                {
                    entityChangeType = EntityChangeType.DELETE;
                }
                else if (diff.getNewFile())
                {
                    entityChangeType = EntityChangeType.CREATE;
                }
                else if (diff.getRenamedFile())
                {
                    entityChangeType = EntityChangeType.RENAME;
                }
                else
                {
                    entityChangeType = EntityChangeType.MODIFY;
                }
                entityDiffs.add(new EntityDiff()
                {
                    @Override
                    public EntityChangeType getEntityChangeType()
                    {
                        return entityChangeType;
                    }

                    @Override
                    public String getNewPath()
                    {
                        return newEntityPath;
                    }

                    @Override
                    public String getOldPath()
                    {
                        return oldEntityPath;
                    }
                });
            }

            // SKIP non-entity, non-config file
        });
        return new Comparison()
        {
            @Override
            public String getToRevisionId()
            {
                return toRevisionId;
            }

            @Override
            public String getFromRevisionId()
            {
                return fromRevisionId;
            }

            @Override
            public List<EntityDiff> getEntityDiffs()
            {
                return entityDiffs;
            }

            @Override
            public boolean isProjectConfigurationUpdated()
            {
                return isProjectConfigurationUpdated.get();
            }
        };
    }
}
