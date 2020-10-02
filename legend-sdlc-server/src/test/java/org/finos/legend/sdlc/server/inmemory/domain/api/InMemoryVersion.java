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

import org.finos.legend.sdlc.domain.model.version.Version;
import org.finos.legend.sdlc.domain.model.version.VersionId;

public class InMemoryVersion implements Version
{
    private final String projectId;
    private final InMemoryRevision revision;
    private final String versionId;
    private final InMemoryProjectConfiguration configuration;

    public InMemoryVersion(String projectId, InMemoryRevision revision, String versionId, InMemoryProjectConfiguration configuration)
    {
        this.projectId = projectId;
        this.revision = revision;
        this.versionId = versionId;
        this.configuration = configuration;
    }

    @Override
    public VersionId getId()
    {
        return VersionId.parseVersionId(this.versionId);
    }

    @Override
    public String getProjectId()
    {
        return this.projectId;
    }

    @Override
    public String getRevisionId()
    {
        return this.revision.getId();
    }

    @Override
    public String getNotes()
    {
        return null;
    }

    public InMemoryRevision getRevision()
    {
        return this.revision;
    }

    public InMemoryProjectConfiguration getConfiguration()
    {
        return this.configuration;
    }
}
