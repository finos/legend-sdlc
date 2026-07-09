// Copyright 2021 Goldman Sachs
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

import org.finos.legend.sdlc.core.entity.EntityAccessOperations;
import org.finos.legend.sdlc.core.entity.EntityModificationOperations;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.entity.change.EntityChange;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.server.domain.api.entity.EntityAccessContext;
import org.finos.legend.sdlc.server.domain.api.entity.EntityApi;
import org.finos.legend.sdlc.server.domain.api.entity.EntityModificationContext;
import org.finos.legend.sdlc.project.source.SourceSpecification;
import org.finos.legend.sdlc.project.source.WorkspaceSourceSpecification;
import org.finos.legend.sdlc.project.workspace.WorkspaceSpecification;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.GitLabConfiguration;
import org.finos.legend.sdlc.server.gitlab.GitLabProjectId;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.tools.BackgroundTaskProcessor;
import org.gitlab4j.api.models.DiffRef;
import org.gitlab4j.api.models.MergeRequest;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import javax.inject.Inject;

public class GitLabEntityApi extends GitLabApiWithFileAccess implements EntityApi
{
    @Inject
    public GitLabEntityApi(GitLabConfiguration gitLabConfiguration, GitLabUserContext userContext, BackgroundTaskProcessor backgroundTaskProcessor)
    {
        super(gitLabConfiguration, userContext, backgroundTaskProcessor);
    }

    @Override
    public EntityAccessContext getEntityAccessContext(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(sourceSpecification, "sourceSpecification may not be null");
        return new GitLabEntityAccessContext(projectId, sourceSpecification, revisionId);
    }

    @Override
    public EntityAccessContext getReviewFromEntityAccessContext(String projectId, String reviewId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        MergeRequest mergeRequest = getReviewMergeRequest(getGitLabApi().getMergeRequestApi(), gitLabProjectId, reviewId);
        validateMergeRequestForComparison(mergeRequest);
        DiffRef diffRef = mergeRequest.getDiffRefs();
        if ((diffRef == null) || (diffRef.getStartSha() == null))
        {
            throw new LegendSDLCServerException("Unable to get [from] revision info in project " + projectId + " for review " + reviewId);
        }

        WorkspaceSpecification workspaceSpec = parseWorkspaceBranchName(mergeRequest.getSourceBranch());
        return new GitLabEntityAccessContext(projectId, workspaceSpec.getSourceSpecification(), diffRef.getStartSha())
        {
            @Override
            protected String getInfoForException()
            {
                return "review " + reviewId + " of project " + projectId;
            }
        };
    }

    @Override
    public EntityAccessContext getReviewToEntityAccessContext(String projectId, String reviewId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        MergeRequest mergeRequest = getReviewMergeRequest(getGitLabApi().getMergeRequestApi(), gitLabProjectId, reviewId);
        validateMergeRequestForComparison(mergeRequest);
        DiffRef diffRef = mergeRequest.getDiffRefs();
        if ((diffRef == null) || (diffRef.getHeadSha() == null))
        {
            throw new LegendSDLCServerException("Unable to get [from] revision info in project " + projectId + " for review " + reviewId);
        }

        WorkspaceSpecification workspaceSpec = parseWorkspaceBranchName(mergeRequest.getSourceBranch());
        return new GitLabEntityAccessContext(projectId, workspaceSpec.getSourceSpecification(), diffRef.getHeadSha())
        {
            @Override
            protected String getInfoForException()
            {
                return "review " + reviewId + " of project " + projectId;
            }
        };
    }

    @Override
    public EntityModificationContext getEntityModificationContext(String projectId, WorkspaceSourceSpecification sourceSpecification)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(sourceSpecification, "source specification may not be null");
        return new GitLabEntityModificationContext(projectId, sourceSpecification);
    }

    private void validateMergeRequestForComparison(MergeRequest mergeRequest)
    {
        // We only allow review in OPEN and COMMITTED state. Note that this is the only control point for this restriction
        if (!isOpen(mergeRequest) && !isCommitted(mergeRequest))
        {
            throw new LegendSDLCServerException("Current operation not supported for review state " + getReviewState(mergeRequest) + " on review " + mergeRequest.getIid());
        }
    }

    private class GitLabEntityAccessContext implements EntityAccessContext
    {
        private final String projectId;
        private final SourceSpecification sourceSpecification;
        private final String revisionId;

        private GitLabEntityAccessContext(String projectId, SourceSpecification sourceSpecification, String revisionId)
        {
            this.projectId = projectId;
            this.sourceSpecification = sourceSpecification;
            this.revisionId = revisionId;
        }

        @Override
        public Entity getEntity(String path)
        {
            try
            {
                return EntityAccessOperations.getEntity(getFileAccessContext(getProjectFileAccessProvider()), path, getInfoForException());
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to get entity " + path + " for " + getInfoForException(),
                        () -> "Unknown entity " + path + " for " + getInfoForException(),
                        () -> "Failed to get entity " + path + " for " + getInfoForException());
            }
        }

        @Override
        public List<Entity> getEntities(Predicate<String> entityPathPredicate, Predicate<String> classifierPathPredicate, Predicate<? super Map<String, ?>> entityContentPredicate, boolean excludeInvalid)
        {
            try
            {
                return EntityAccessOperations.getEntities(getFileAccessContext(getProjectFileAccessProvider()), entityPathPredicate, classifierPathPredicate, entityContentPredicate, excludeInvalid);
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to get entities for " + getInfoForException(),
                        () -> "Unknown entities for " + getInfoForException(),
                        () -> "Failed to get entities for " + getInfoForException());
            }
        }

        @Override
        public List<String> getEntityPaths(Predicate<String> entityPathPredicate, Predicate<String> classifierPathPredicate, Predicate<? super Map<String, ?>> entityContentPredicate)
        {
            try
            {
                return EntityAccessOperations.getEntityPaths(getFileAccessContext(getProjectFileAccessProvider()), entityPathPredicate, classifierPathPredicate, entityContentPredicate);
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to get entity paths for " + getInfoForException(),
                        () -> "Unknown entity paths for " + getInfoForException(),
                        () -> "Failed to get entity paths for " + getInfoForException());
            }
        }

        protected ProjectFileAccessProvider.FileAccessContext getFileAccessContext(ProjectFileAccessProvider projectFileAccessProvider)
        {
            return projectFileAccessProvider.getFileAccessContext(this.projectId, this.sourceSpecification, resolveRevisionId(this.projectId, this.sourceSpecification, this.revisionId));
        }

        protected String getInfoForException()
        {
            return getReferenceInfo(this.projectId, this.sourceSpecification, this.revisionId);
        }
    }

    private class GitLabEntityModificationContext implements EntityModificationContext
    {
        private final String projectId;
        private final WorkspaceSourceSpecification sourceSpecification;

        private GitLabEntityModificationContext(String projectId, WorkspaceSourceSpecification sourceSpecification)
        {
            this.projectId = projectId;
            this.sourceSpecification = Objects.requireNonNull(sourceSpecification, "source specification may not be null");
        }

        @Override
        public Revision updateEntities(Iterable<? extends Entity> entities, boolean replace, String message)
        {
            LegendSDLCServerException.validateNonNull(entities, "entities may not be null");
            LegendSDLCServerException.validateNonNull(message, "message may not be null");
            try
            {
                return EntityModificationOperations.updateEntities(getProjectFileAccessProvider(), this.projectId, this.sourceSpecification, entities, replace, message, getReferenceInfo(this.projectId, this.sourceSpecification));
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to perform changes on " + getReferenceInfo(this.projectId, this.sourceSpecification),
                        () -> "Unknown " + getReferenceInfo(this.projectId, this.sourceSpecification),
                        () -> "Failed to perform changes on " + getReferenceInfo(this.projectId, this.sourceSpecification) + " (message: " + message + ")");
            }
        }

        @Override
        public Revision performChanges(List<? extends EntityChange> changes, String revisionId, String message)
        {
            LegendSDLCServerException.validateNonNull(changes, "changes may not be null");
            LegendSDLCServerException.validateNonNull(message, "message may not be null");
            EntityModificationOperations.validateEntityChanges(changes);
            try
            {
                return EntityModificationOperations.performChanges(getProjectFileAccessProvider(), this.projectId, this.sourceSpecification, revisionId, message, changes);
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to perform changes on " + getReferenceInfo(this.projectId, this.sourceSpecification),
                        () -> "Unknown " + getReferenceInfo(this.projectId, this.sourceSpecification),
                        () -> "Failed to perform changes on " + getReferenceInfo(this.projectId, this.sourceSpecification) + " (message: " + message + ")");
            }
        }
    }
}
