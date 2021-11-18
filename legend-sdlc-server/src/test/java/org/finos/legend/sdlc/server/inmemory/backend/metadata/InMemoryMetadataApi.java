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

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.server.depot.api.MetadataApi;
import org.finos.legend.sdlc.server.depot.model.DepotProjectId;
import org.finos.legend.sdlc.server.depot.model.DepotProjectVersion;

import javax.inject.Inject;
import java.util.List;
import java.util.Set;

public class InMemoryMetadataApi implements MetadataApi
{
    private final InMemoryMetadataBackend backend;

    @Inject
    InMemoryMetadataApi(InMemoryMetadataBackend backend)
    {
        this.backend = backend;
    }

    @Override
    public List<Entity> getEntities(DepotProjectId projectId, String versionId)
    {
        InMemoryProjectMetadata project = this.backend.getProject(projectId.toString());
        if (project == null)
        {
            return Lists.mutable.empty();
        }
        else
        {
            InMemoryVersionMetadata version = this.backend.getProject(projectId.toString()).getVersion(versionId);
            return version.getEntities();
        }
    }

    @Override
    public Set<DepotProjectVersion> getProjectDependencies(DepotProjectId projectId, String versionId, boolean transitive)
    {
        InMemoryProjectMetadata project = this.backend.getProject(projectId.toString());
        if (project == null)
        {
            return Sets.mutable.empty();
        }
        else
        {
            InMemoryVersionMetadata version = project.getVersion(versionId);
            return searchUpstream(version, transitive);
        }
    }

    private Set<DepotProjectVersion> searchUpstream(InMemoryVersionMetadata rootProjectVersion, boolean transitive)
    {
        return transitive ? searchUpstreamRecursive(rootProjectVersion) : Sets.mutable.withAll(rootProjectVersion.getDependencies());
    }

    private Set<DepotProjectVersion> searchUpstreamRecursive(InMemoryVersionMetadata rootProjectVersion)
    {
        Set<DepotProjectVersion> results = Sets.mutable.empty();
        searchUpstreamRecursive(rootProjectVersion, results);
        return results;
    }

    private void searchUpstreamRecursive(InMemoryVersionMetadata projectVersion, Set<DepotProjectVersion> results)
    {
        for (DepotProjectVersion dependency : projectVersion.getDependencies())
        {
            if (results.add(dependency))
            {
                InMemoryVersionMetadata dependencyProjectVersion = this.backend.getProject(dependency.getDepotProjectId().toString()).getVersion(dependency.getVersionId());
                searchUpstreamRecursive(dependencyProjectVersion, results);
            }
        }
    }
}
