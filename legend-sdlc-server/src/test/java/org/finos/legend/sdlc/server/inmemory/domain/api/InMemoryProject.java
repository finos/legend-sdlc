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
import org.finos.legend.sdlc.domain.model.project.Project;
import org.finos.legend.sdlc.domain.model.project.ProjectType;

import javax.inject.Inject;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class InMemoryProject implements Project
{
    private String projectId;
    private final MutableMap<String, InMemoryWorkspace> workspaces = Maps.mutable.empty();
    private final MutableMap<String, InMemoryRevision> revisions = Maps.mutable.empty();
    private final MutableMap<String, InMemoryVersion> versions = Maps.mutable.empty();
    private String currentRevisionId;

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
    public ProjectType getProjectType()
    {
        return ProjectType.PRODUCTION;
    }

    @JsonIgnore
    public boolean containsWorkspace(String workspaceId)
    {
        return this.workspaces.containsKey(workspaceId);
    }

    @JsonIgnore
    public InMemoryWorkspace getWorkspace(String workspaceId)
    {
        return this.workspaces.get(workspaceId);
    }

    @JsonIgnore
    public InMemoryWorkspace addNewWorkspace(String workspaceId)
    {
        return this.addNewWorkspace(workspaceId, null);
    }

    @JsonIgnore
    public InMemoryWorkspace addNewWorkspace(String workspaceId, InMemoryRevision baseRevision)
    {
        InMemoryWorkspace workspace = new InMemoryWorkspace(projectId, workspaceId, baseRevision);
        this.workspaces.put(workspaceId, workspace);
        return this.workspaces.get(workspaceId);
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
}
