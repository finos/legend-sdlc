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


package org.finos.legend.sdlc.server.inmemory.domain.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.eclipse.collections.api.factory.Maps;
import org.finos.legend.sdlc.domain.model.patch.Patch;
import org.finos.legend.sdlc.domain.model.version.VersionId;

import javax.inject.Inject;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class InMemoryPatch implements Patch
{
    private final Map<String, InMemoryRevision> revisions = Maps.mutable.empty();
    private String currentRevisionId;
    private String projectId;
    private VersionId patchReleaseVersionId;

    @Inject
    public InMemoryPatch()
    {
    }

    public InMemoryPatch(String projectId, VersionId patchReleaseVersionId, InMemoryRevision baseRevision)
    {
        this.projectId = projectId;
        this.patchReleaseVersionId = patchReleaseVersionId;

        InMemoryRevision revision = new InMemoryRevision("", baseRevision);
        this.currentRevisionId = revision.getId();
        this.revisions.put(revision.getId(), revision);
    }

    @Override
    public String getProjectId()
    {
        return this.projectId;
    }

    @Override
    public VersionId getPatchReleaseVersionId()
    {
        return this.patchReleaseVersionId;
    }

    @JsonIgnore
    public Iterable<InMemoryRevision> getRevisions()
    {
        return this.revisions.values();
    }

    @JsonIgnore
    public InMemoryRevision getRevision(String revisionId)
    {
        return this.revisions.get(revisionId);
    }

    @JsonIgnore
    public InMemoryRevision getCurrentRevision()
    {
        return this.revisions.get(this.currentRevisionId);
    }

    @JsonIgnore
    public void addNewRevision(InMemoryRevision revision)
    {
        this.revisions.put(revision.getId(), revision);
        this.currentRevisionId = revision.getId();
    }
}
