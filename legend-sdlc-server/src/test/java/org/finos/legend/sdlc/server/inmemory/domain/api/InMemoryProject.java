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

package org.finos.legend.sdlc.server.inmemory.domain.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.finos.legend.sdlc.domain.model.project.Project;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.review.ReviewState;
import org.finos.legend.sdlc.domain.model.version.VersionId;

import javax.inject.Inject;
import java.util.List;
import java.time.Instant;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public class InMemoryProject implements Project
{
    private String projectId;
    private final MutableMap<String, InMemoryPatch> patches = Maps.mutable.empty();
    private final MutableMap<String, InMemoryWorkspace> userWorkspaces = Maps.mutable.empty(); // Store the key as default_{workspaceId} if sourceBranch is default branch otherwise key ad {branchName}_{workspaceId}
    private final MutableMap<String, InMemoryWorkspace> groupWorkspaces = Maps.mutable.empty();
    private final MutableMap<String, InMemoryRevision> revisions = Maps.mutable.empty();
    private final MutableMap<String, InMemoryVersion> versions = Maps.mutable.empty();
    private String currentRevisionId;
    private final MutableMap<String, InMemoryReview> reviews = Maps.mutable.empty();

    @Inject
    public InMemoryProject()
    {
    }

    public InMemoryProject(String projectId)
    {
        this.projectId = projectId;
        this.addNewRevision(new InMemoryRevision(projectId));
    }

    @Override
    public String getProjectId()
    {
        return this.projectId;
    }

    @Override
    public String getName()
    {
        return "project-" + this.projectId;
    }

    @Override
    public String getDescription()
    {
        return "project-" + this.projectId;
    }

    @Override
    public List<String> getTags()
    {
        return null;
    }

    @Override
    @JsonIgnore
    public ProjectType getProjectType()
    {
        return null;
    }

    @Override
    public String getWebUrl()
    {
        return null;
    }

    @JsonIgnore
    public boolean containsUserWorkspace(String branchName)
    {
        return this.userWorkspaces.containsKey(branchName);
    }

    @JsonIgnore
    public boolean containsGroupWorkspace(String branchName)
    {
        return this.groupWorkspaces.containsKey(branchName);
    }

    @JsonIgnore
    public Iterable<InMemoryWorkspace> getUserWorkspaces(VersionId patchReleaseVersionId)
    {
        String sourceBranchName = getSourceBranch(patchReleaseVersionId);
        return this.userWorkspaces.entrySet().stream().filter(w -> w.getKey().startsWith(sourceBranchName)).map(w -> w.getValue()).collect(Collectors.toList());
    }

    @JsonIgnore
    public Iterable<InMemoryWorkspace> getGroupWorkspaces(VersionId patchReleaseVersionId)
    {
        String sourceBranchName = getSourceBranch(patchReleaseVersionId);
        return this.groupWorkspaces.entrySet().stream().filter(w -> w.getKey().startsWith(sourceBranchName)).map(w -> w.getValue()).collect(Collectors.toList());
    }

    @JsonIgnore
    public InMemoryWorkspace getUserWorkspace(String workspaceId, VersionId patchReleaseVersionId)
    {
        String sourceBranchName = getSourceBranch(patchReleaseVersionId);
        return this.userWorkspaces.get(sourceBranchName + "_" + workspaceId);
    }

    @JsonIgnore
    public InMemoryWorkspace getGroupWorkspace(String workspaceId, VersionId patchReleaseVersionId)
    {
        String sourceBranchName = getSourceBranch(patchReleaseVersionId);
        return this.groupWorkspaces.get(sourceBranchName + "_" + workspaceId);
    }

    @JsonIgnore
    public InMemoryWorkspace addNewUserWorkspace(String workspaceId)
    {
        return this.addNewUserWorkspace(workspaceId, null, null);
    }

    @JsonIgnore
    public InMemoryWorkspace addNewGroupWorkspace(String workspaceId)
    {
        return this.addNewGroupWorkspace(workspaceId, null, null);
    }

    public InMemoryWorkspace addNewUserWorkspace(String workspaceId, InMemoryRevision baseRevision)
    {
       return this.addNewUserWorkspace(workspaceId, baseRevision, null);
    }

    public InMemoryWorkspace addNewGroupWorkspace(String workspaceId, InMemoryRevision baseRevision)
    {
       return this.addNewGroupWorkspace(workspaceId, baseRevision, null);
    }

    @JsonIgnore
    public InMemoryWorkspace addNewUserWorkspace(String workspaceId, InMemoryRevision baseRevision, VersionId patchReleaseVersionId)
    {
        String sourceBranchName = getSourceBranch(patchReleaseVersionId);
        InMemoryWorkspace workspace = new InMemoryWorkspace(projectId, workspaceId, baseRevision);
        this.userWorkspaces.put(sourceBranchName + "_" + workspaceId, workspace);
        return this.userWorkspaces.get(sourceBranchName + "_" + workspaceId);
    }

    @JsonIgnore
    public InMemoryWorkspace addNewGroupWorkspace(String workspaceId, InMemoryRevision baseRevision, VersionId patchReleaseVersionId)
    {
        String sourceBranchName = getSourceBranch(patchReleaseVersionId);
        InMemoryWorkspace workspace = new InMemoryWorkspace(projectId, workspaceId, baseRevision);
        this.groupWorkspaces.put(sourceBranchName + "_" + workspaceId, workspace);
        return this.groupWorkspaces.get(sourceBranchName + "_" + workspaceId);
    }

    @JsonIgnore
    public void deleteUserWorkspace(String workspaceId)
    {
        this.deleteUserWorkspace(workspaceId, null);
    }

    @JsonIgnore
    public void deleteUserWorkspace(String workspaceId, VersionId patchReleaseVersionId)
    {
        String sourceBranchName = getSourceBranch(patchReleaseVersionId);
        this.userWorkspaces.remove(sourceBranchName + "_" + workspaceId);
    }

    @JsonIgnore
    public void deleteGroupWorkspace(String workspaceId)
    {
        this.deleteGroupWorkspace(workspaceId, null);
    }

    @JsonIgnore
    public void deleteGroupWorkspace(String workspaceId,VersionId patchReleaseVersionId)
    {
        String sourceBranchName = getSourceBranch(patchReleaseVersionId);
        this.groupWorkspaces.remove(sourceBranchName + "_" + workspaceId);
    }

    @JsonIgnore
    public InMemoryVersion getVersion(String versionId)
    {
        return this.versions.get(versionId);
    }

    @JsonIgnore
    public void addNewVersion(String versionId, InMemoryRevision revision)
    {
        this.versions.put(versionId, new InMemoryVersion(projectId, revision, versionId, revision.getConfiguration()));
    }

    @JsonIgnore
    public void addNewRevision(InMemoryRevision revision)
    {
        this.revisions.put(revision.getId(), revision);
        this.currentRevisionId = revision.getId();
    }

    @JsonIgnore
    public InMemoryRevision getCurrentRevision()
    {
        return (this.currentRevisionId == null) ? null : this.revisions.get(this.currentRevisionId);
    }

    @JsonIgnore
    public InMemoryRevision getRevision(String revisionId)
    {
        return this.revisions.get(revisionId);
    }

    @JsonIgnore
    public Iterable<InMemoryRevision> getRevisions()
    {
        return this.revisions.valuesView();
    }

    @JsonIgnore
    public InMemoryReview getReview(String reviewId)
    {
        return this.reviews.get(reviewId);
    }

    @JsonIgnore
    public MutableList<InMemoryReview> getReviews(VersionId patchReleaseVersionId, ReviewState state, Iterable<String> revisionIds, Instant since, Instant until, Integer limit)
    {
        MutableList<InMemoryReview> filteredReviews = Lists.mutable.empty();
        Iterable<InMemoryReview> reviews = this.reviews.valuesView();

        for (InMemoryReview rev : reviews)
        {

            if (((state == null) || (state == rev.getState())) && rev.getTargetBranch().equals(getSourceBranch(patchReleaseVersionId)))
            {
                filteredReviews.add(rev);
            }
        }
        
        return filteredReviews;
    }

    @JsonIgnore
    public InMemoryReview addReview(String reviewId, VersionId patchReleaseVersionId)
    {
        InMemoryReview review = new InMemoryReview(projectId, reviewId);
        if (patchReleaseVersionId != null)
        {
            review.setTargetBranch(getSourceBranch(patchReleaseVersionId));
        }
        this.reviews.put(reviewId, review);
        return this.reviews.get(reviewId);
    }

    @JsonIgnore
    public InMemoryPatch addPatch(VersionId patchVersionId, InMemoryRevision baseRevision)
    {
        InMemoryPatch patch = new InMemoryPatch(projectId, patchVersionId, baseRevision);
        this.patches.put(patchVersionId.toVersionIdString(), patch);
        return this.patches.get(patchVersionId);
    }

    @JsonIgnore
    public InMemoryPatch getPatch(VersionId patchReleaseVersionId)
    {
        return patchReleaseVersionId != null ? this.patches.get(patchReleaseVersionId.toVersionIdString()) : null;
    }

    @JsonIgnore
    public Iterable<InMemoryPatch> getPatches()
    {
        return this.patches.valuesView();
    }

    @JsonIgnore
    private String getSourceBranch(VersionId patchReleaseVersionId)
    {
        return patchReleaseVersionId == null ? "default" : "patch-" + patchReleaseVersionId.toVersionIdString();
    }
}
