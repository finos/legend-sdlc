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

package org.finos.legend.sdlc.server.depot;

import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.server.depot.api.MetadataApi;
import org.finos.legend.sdlc.server.depot.model.DepotProjectId;
import org.finos.legend.sdlc.server.depot.model.DepotProjectVersion;
import org.finos.legend.sdlc.server.exception.FSException;

import java.util.List;
import java.util.Set;
import javax.inject.Inject;

public class FileSystemMetadataApi implements MetadataApi
{
    @Inject
    public FileSystemMetadataApi()
    {
    }

    @Override
    public List<Entity> getEntities(DepotProjectId projectId, String versionId)
    {
        throw FSException.unavailableFeature();
    }

    @Override
    public Set<DepotProjectVersion> getProjectDependencies(DepotProjectId projectId, String versionId, boolean transitive)
    {
        throw FSException.unavailableFeature();
    }
}
