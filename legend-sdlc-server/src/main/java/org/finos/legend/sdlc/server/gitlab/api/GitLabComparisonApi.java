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
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.domain.api.comparison.ComparisonApi;
import org.finos.legend.sdlc.server.domain.api.revision.RevisionApi;
import org.finos.legend.sdlc.server.domain.api.project.SourceSpecification;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.GitLabConfiguration;
import org.finos.legend.sdlc.server.gitlab.GitLabProjectId;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.project.ProjectStructure;
import org.finos.legend.sdlc.server.tools.BackgroundTaskProcessor;
import org.gitlab4j.api.RepositoryApi;
import org.gitlab4j.api.models.Commit;
import org.gitlab4j.api.models.CompareResults;
import org.gitlab4j.api.models.Diff;
import org.gitlab4j.api.models.DiffRef;
import org.gitlab4j.api.models.MergeRequest;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class GitLabComparisonApi extends GitLabApiWithFileAccess implements ComparisonApi
{
    private final RevisionApi revisionApi;
    protected static final String FILE_PATH_DELIMITER = "/";

    @Inject
    public GitLabComparisonApi(GitLabConfiguration gitLabConfiguration, GitLabUserContext userContext, RevisionApi revisionApi, BackgroundTaskProcessor backgroundTaskProcessor)
    {
        super(gitLabConfiguration, userContext, backgroundTaskProcessor);
        this.revisionApi = revisionApi;
    }

    @Override
    public Comparison getWorkspaceCreationComparison(String projectId, SourceSpecification sourceSpecification)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(sourceSpecification.getWorkspaceId(), "workspaceId may not be null");
        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        RepositoryApi repositoryApi = getGitLabApi().getRepositoryApi();
        String currentWorkspaceRevisionId = this.revisionApi.getWorkspaceRevisionContext(projectId, sourceSpecification).getCurrentRevision().getId();
        ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType = ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE;
        ProjectStructure toProjectStructure = getProjectStructure(gitLabProjectId.toString(), SourceSpecification.newSourceSpecification(sourceSpecification.getWorkspaceId(), sourceSpecification.getWorkspaceType(), workspaceAccessType, sourceSpecification.getPatchReleaseVersionId()), currentWorkspaceRevisionId);
        String workspaceCreationRevisionId;
        String sourceBranch = getSourceBranch(gitLabProjectId, sourceSpecification.getPatchReleaseVersionId());
        try
        {
            Commit commit = repositoryApi.getMergeBase(gitLabProjectId.getGitLabId(), Arrays.asList(sourceBranch, currentWorkspaceRevisionId));
            workspaceCreationRevisionId = commit.getId();
        }
        catch (Exception e)
        {
            throw buildException(e,
                () -> "User " + getCurrentUser() + " is not allowed to get merged based revision for revisions " + sourceBranch + ", " + currentWorkspaceRevisionId + " from project " + gitLabProjectId,
                () -> "Could not find revisions " + sourceBranch + ", " + currentWorkspaceRevisionId + " from project " + gitLabProjectId,
                () -> "Failed to fetch Merged Base Information for revisions " + sourceBranch + ", " + currentWorkspaceRevisionId + " from project " + gitLabProjectId);
        }
        ProjectStructure fromProjectStructure = getProjectStructure(gitLabProjectId.toString(), SourceSpecification.newSourceSpecification(sourceSpecification.getWorkspaceId(), sourceSpecification.getWorkspaceType(), workspaceAccessType, sourceSpecification.getPatchReleaseVersionId()), workspaceCreationRevisionId);
        return getComparisonResult(gitLabProjectId, repositoryApi, workspaceCreationRevisionId, currentWorkspaceRevisionId, fromProjectStructure, toProjectStructure);
    }

    @Override
    public Comparison getWorkspaceProjectComparison(String projectId, SourceSpecification sourceSpecification)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(sourceSpecification.getWorkspaceId(), "workspaceId may not be null");
        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        RepositoryApi repositoryApi = getGitLabApi().getRepositoryApi();
        String currentProjectRevisionId = this.revisionApi.getProjectRevisionContext(projectId).getCurrentRevision().getId();
        ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType = ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE;

        ProjectStructure fromProjectStructure = getProjectStructure(gitLabProjectId.toString(), SourceSpecification.newSourceSpecification(getSourceBranch(gitLabProjectId, sourceSpecification.getPatchReleaseVersionId()), sourceSpecification.getWorkspaceType(), workspaceAccessType, sourceSpecification.getPatchReleaseVersionId()), currentProjectRevisionId);
        String currentWorkspaceRevisionId = this.revisionApi.getWorkspaceRevisionContext(projectId, sourceSpecification).getCurrentRevision().getId();
        ProjectStructure toProjectStructure = getProjectStructure(gitLabProjectId.toString(), SourceSpecification.newSourceSpecification(sourceSpecification.getWorkspaceId(), sourceSpecification.getWorkspaceType(), workspaceAccessType, sourceSpecification.getPatchReleaseVersionId()), currentWorkspaceRevisionId);
        return getComparisonResult(gitLabProjectId, repositoryApi, currentProjectRevisionId, currentWorkspaceRevisionId, fromProjectStructure, toProjectStructure);
    }

    @Override
    public Comparison getReviewComparison(String projectId, VersionId patchReleaseVersionId, String reviewId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");
        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        RepositoryApi repositoryApi = getGitLabApi().getRepositoryApi();
        MergeRequest mergeRequest = getReviewMergeRequest(getGitLabApi().getMergeRequestApi(), gitLabProjectId, patchReleaseVersionId, reviewId);

        WorkspaceInfo workspaceInfo = parseWorkspaceBranchName(mergeRequest.getSourceBranch());
        if (workspaceInfo == null)
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
        ProjectStructure fromProjectStructure = getProjectStructure(projectId, SourceSpecification.newSourceSpecification(workspaceInfo.getWorkspaceId(),workspaceInfo.getWorkspaceType(), workspaceInfo.getWorkspaceAccessType(), patchReleaseVersionId), fromRevisionId);
        ProjectStructure toProjectStructure = getProjectStructure(gitLabProjectId.toString(), SourceSpecification.newSourceSpecification(patchReleaseVersionId), toRevisionId);
        return getComparisonResult(gitLabProjectId, repositoryApi, fromRevisionId, toRevisionId, fromProjectStructure, toProjectStructure);
    }

    @Override
    public Comparison getReviewWorkspaceCreationComparison(String projectId, VersionId patchReleaseVersionId, String reviewId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");
        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        RepositoryApi repositoryApi = getGitLabApi().getRepositoryApi();
        MergeRequest mergeRequest = getReviewMergeRequest(getGitLabApi().getMergeRequestApi(), gitLabProjectId, patchReleaseVersionId, reviewId);

        WorkspaceInfo workspaceInfo = parseWorkspaceBranchName(mergeRequest.getSourceBranch());
        if (workspaceInfo == null)
        {
            throw new LegendSDLCServerException("Unknown review in project " + projectId + ": " + reviewId, Response.Status.NOT_FOUND);
        }

        DiffRef diffRef = mergeRequest.getDiffRefs();
        if ((diffRef == null) || (diffRef.getStartSha() == null) || (diffRef.getHeadSha() == null))
        {
            throw new LegendSDLCServerException("Unable to get revision info for review " + reviewId + " in project " + projectId);
        }

        String fromRevisionId = diffRef.getBaseSha();
        String toRevisionId = diffRef.getHeadSha();
        ProjectStructure fromProjectStructure = getProjectStructure(projectId, SourceSpecification.newSourceSpecification(workspaceInfo.getWorkspaceId(), workspaceInfo.getWorkspaceType(), workspaceInfo.getWorkspaceAccessType(), patchReleaseVersionId), fromRevisionId);
        ProjectStructure toProjectStructure = getProjectStructure(projectId, SourceSpecification.newSourceSpecification(workspaceInfo.getWorkspaceId(), workspaceInfo.getWorkspaceType(), workspaceInfo.getWorkspaceAccessType(), patchReleaseVersionId), toRevisionId);
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
            String oldPath = diff.getOldPath().startsWith(FILE_PATH_DELIMITER) ? diff.getOldPath() : FILE_PATH_DELIMITER + diff.getOldPath();
            String newPath = diff.getNewPath().startsWith(FILE_PATH_DELIMITER) ? diff.getNewPath() : FILE_PATH_DELIMITER + diff.getNewPath();

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
