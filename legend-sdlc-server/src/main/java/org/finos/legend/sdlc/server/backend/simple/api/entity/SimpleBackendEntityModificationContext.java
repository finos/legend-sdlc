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

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.entity.change.EntityChange;
import org.finos.legend.sdlc.domain.model.entity.change.EntityChangeType;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.server.backend.simple.domain.model.revision.SimpleBackendRevision;
import org.finos.legend.sdlc.server.domain.api.entity.EntityModificationContext;

import java.util.List;

public class SimpleBackendEntityModificationContext implements EntityModificationContext
{
    private SimpleBackendRevision revision;

    public SimpleBackendEntityModificationContext(SimpleBackendRevision revision)
    {
        this.revision = revision;
    }

    @Override
    public Revision updateEntities(Iterable<? extends Entity> entities, boolean replace, String message)
    {
        if (!replace)
        {
            revision.delete(entities);
            revision.update(entities);
        }
        else
        {
            revision.update(entities);
        }
        return this.revision;
    }

    @Override
    public Revision performChanges(List<? extends EntityChange> changes, String revisionId, String message)
    {
        MutableList<Entity> toAdd = Lists.mutable.empty();
        MutableList<Entity> toDelete = Lists.mutable.empty();
        MutableList<Entity> toUpdate = Lists.mutable.empty();
        for (EntityChange change : changes)
        {
            if (change.getType() == EntityChangeType.CREATE)
            {
                String entityPath = change.getEntityPath();
                Entity entity = Entity.newEntity(entityPath, change.getClassifierPath(), change.getContent());
                toAdd.add(entity);
            }
            else if (change.getType() == EntityChangeType.DELETE)
            {
                String entityPath = change.getEntityPath();
                Entity entity = Entity.newEntity(entityPath, change.getClassifierPath(), change.getContent());
                toDelete.add(entity);
            }
            else if (change.getType() == EntityChangeType.MODIFY)
            {
                String entityPath = change.getEntityPath();
                Entity entity = Entity.newEntity(entityPath, change.getClassifierPath(), change.getContent());
                toUpdate.add(entity);
            }
        }
        revision.delete(toDelete);
        revision.update(toUpdate);
        revision.add(toAdd);

        return revision;
    }
}
