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

package org.finos.legend.sdlc.server.application.entity;

import com.fasterxml.jackson.annotation.JsonSetter;
import org.eclipse.collections.api.factory.Lists;
import org.finos.legend.sdlc.domain.model.entity.change.EntityChange;
import org.finos.legend.sdlc.domain.model.entity.change.EntityChangeType;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PerformChangesCommand extends AbstractEntityChangeCommand
{
    private List<EntityChange> entityChanges = Collections.emptyList();
    private String revisionId;

    public List<EntityChange> getEntityChanges()
    {
        return this.entityChanges;
    }

    public String getRevisionId()
    {
        return this.revisionId;
    }

    @JsonSetter("entityChanges")
    void setMutableEntityChanges(List<MutableEntityChange> entityChanges)
    {
        this.entityChanges = Lists.mutable.withAll(entityChanges);
    }

    static class MutableEntityChange extends EntityChange
    {
        EntityChangeType type;
        String entityPath;
        String classifierPath;
        Map<String, ?> content;
        String newEntityPath;

        @Override
        public EntityChangeType getType()
        {
            return this.type;
        }

        @Override
        public String getEntityPath()
        {
            return this.entityPath;
        }

        @Override
        public String getClassifierPath()
        {
            return this.classifierPath;
        }

        @Override
        public Map<String, ?> getContent()
        {
            return this.content;
        }

        @Override
        public String getNewEntityPath()
        {
            return this.newEntityPath;
        }
    }
}
