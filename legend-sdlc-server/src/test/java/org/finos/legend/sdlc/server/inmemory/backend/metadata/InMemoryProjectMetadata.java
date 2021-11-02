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

package org.finos.legend.sdlc.server.inmemory.backend.metadata;

import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;
import org.finos.legend.sdlc.server.depot.model.DepotProjectId;

public class InMemoryProjectMetadata
{
    private DepotProjectId projectId;
    private String currentVersionId;
    private final MutableMap<String, InMemoryVersionMetadata> versionMetadata = Maps.mutable.empty();

    public InMemoryProjectMetadata(String projectId)
    {
        this.projectId = DepotProjectId.parseProjectId(projectId);
    }

    public InMemoryVersionMetadata getOrCreateVersion(String versionId)
    {
        return this.versionMetadata.getOrDefault(versionId, new InMemoryVersionMetadata());
    }

    public InMemoryVersionMetadata getVersion(String versionId)
    {
        return this.versionMetadata.get(versionId);
    }

    public DepotProjectId getProjectId()
    {
        return this.projectId;
    }

    public String getCurrentVersionId()
    {
        return this.currentVersionId;
    }

    public void setCurrentVersionId(String currentVersionId)
    {
        this.currentVersionId = currentVersionId;
    }

    public void addNewVersion(String versionId, InMemoryVersionMetadata versionMetadata)
    {
        this.versionMetadata.put(versionId, versionMetadata);
        setCurrentVersionId(versionId);
    }
}
