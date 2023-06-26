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

package org.finos.legend.sdlc.server.backend.simple.api.entity;

import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.server.backend.simple.domain.model.revision.SimpleBackendRevision;
import org.finos.legend.sdlc.server.domain.api.entity.EntityAccessContext;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class SimpleBackendEntityAccessContext implements EntityAccessContext
{
    private final SimpleBackendRevision revision;

    public SimpleBackendEntityAccessContext(SimpleBackendRevision revision)
    {
        this.revision = revision;
    }

    @Override
    public Entity getEntity(String path)
    {
        List<Entity> matches = this.getEntities((p) -> p.equals(path), null, null);
        if (matches.size() > 1)
        {
            throw new IllegalStateException(String.format("Found %d instead of 1 matches for entity with path %s", matches.size(), path));
        }
        if (matches.size() == 0)
        {
            throw new IllegalStateException(String.format("Entity with path %s not found", path));
        }
        return matches.get(0);
    }

    @Override
    public List<Entity> getEntities(Predicate<String> entityPathPredicate, Predicate<String> classifierPathPredicate, Predicate<? super Map<String, ?>> entityContentPredicate, boolean excludeInvalid)
    {
        Stream<Entity> stream = StreamSupport.stream(this.revision.getEntities().spliterator(), false);
        if (entityPathPredicate != null)
        {
            stream = stream.filter(entity -> entityPathPredicate.test(entity.getPath()));
        }

        if (classifierPathPredicate != null)
        {
            stream = stream.filter(entity -> classifierPathPredicate.test(entity.getClassifierPath()));
        }

        if (entityContentPredicate != null)
        {
            stream = stream.filter(entity -> entityContentPredicate.test(entity.getContent()));
        }

        return stream.collect(Collectors.toList());
    }

    @Override
    public List<String> getEntityPaths(Predicate<String> entityPathPredicate, Predicate<String> classifierPathPredicate, Predicate<? super Map<String, ?>> entityContentPredicate)
    {
        List<Entity> entities = this.getEntities(entityPathPredicate, classifierPathPredicate, entityContentPredicate);
        return entities.stream().map(Entity::getPath).collect(Collectors.toList());
    }
}
