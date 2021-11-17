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
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.set.MutableSet;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.server.depot.model.DepotProjectVersion;

import java.util.List;
import java.util.Set;

public class InMemoryVersionMetadata
{
    private final MutableSet<DepotProjectVersion> dependencies = Sets.mutable.empty();
    private final MutableList<Entity> entities = Lists.mutable.empty();

    public void addEntity(Entity entity)
    {
        this.entities.add(entity);
    }

    public void addEntities(Iterable<? extends Entity> newEntities)
    {
        this.entities.addAllIterable(newEntities);
    }

    public void addDependency(DepotProjectVersion dependency)
    {
        this.dependencies.add(dependency);
    }

    public Set<DepotProjectVersion> getDependencies()
    {
        return this.dependencies.asUnmodifiable();
    }

    public List<Entity> getEntities()
    {
        return this.entities.asUnmodifiable();
    }
}
