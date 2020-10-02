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

package org.finos.legend.sdlc.server.domain.api.entity;

import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.entity.change.EntityChange;
import org.finos.legend.sdlc.domain.model.revision.Revision;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public interface EntityModificationContext
{
    default Revision createEntity(String entityPath, String classifierPath, Map<String, ?> content, String message)
    {
        return performChanges(Collections.singletonList(EntityChange.newCreateEntity(entityPath, classifierPath, content)), null, message);
    }

    default Revision updateEntity(String entityPath, String classifierPath, Map<String, ?> content, String message)
    {
        return performChanges(Collections.singletonList(EntityChange.newModifyEntity(entityPath, classifierPath, content)), null, message);
    }

    default Revision createOrUpdateEntity(String entityPath, String classifierPath, Map<String, ?> content, String message)
    {
        return updateEntities(Collections.singletonList(Entity.newEntity(entityPath, classifierPath, content)), false, message);
    }

    default Revision deleteEntity(String entityPath, String message)
    {
        return performChanges(Collections.singletonList(EntityChange.newDeleteEntity(entityPath)), null, message);
    }

    default Revision deleteEntities(Iterable<String> entityPaths, String message)
    {
        return performChanges(StreamSupport.stream(entityPaths.spliterator(), false).map(EntityChange::newDeleteEntity).collect(Collectors.toList()), null, message);
    }

    default Revision deleteAllEntities(String message)
    {
        return updateEntities(Collections.emptyList(), true, message);
    }

    default Revision renameEntity(String entityPath, String newEntityPath, String message)
    {
        return performChanges(Collections.singletonList(EntityChange.newRenameEntity(entityPath, newEntityPath)), null, message);
    }

    default Revision renameAndUpdateEntity(String entityPath, String newEntityPath, String classifierPath, Map<String, ?> content, String message)
    {
        return performChanges(Arrays.asList(EntityChange.newRenameEntity(entityPath, newEntityPath), EntityChange.newModifyEntity(newEntityPath, classifierPath, content)), null, message);
    }

    /**
     * Update entities with new definitions. If replace is true, then all entities
     * are replaced. This means that existing entities are deleted unless a new
     * definition is supplied. Returns the new revision if there is one, and null
     * otherwise.
     *
     * @param entities entity definitions
     * @param replace  whether to replace all existing entities
     * @param message  change message
     * @return new revision or null
     */
    Revision updateEntities(Iterable<? extends Entity> entities, boolean replace, String message);

    /**
     * Perform the given changes and return the new revision. Returns null if the
     * changes do not result in a new revision.
     *
     * @param changes    entity changes to perform
     * @param revisionId reference revision id
     * @param message    change message
     * @return new revision or null
     */
    Revision performChanges(List<? extends EntityChange> changes, String revisionId, String message);
}
