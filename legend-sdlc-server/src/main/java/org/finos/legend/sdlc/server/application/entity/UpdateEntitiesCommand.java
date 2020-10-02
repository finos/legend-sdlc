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
import org.finos.legend.sdlc.domain.model.entity.Entity;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class UpdateEntitiesCommand extends AbstractEntityChangeCommand
{
    private List<Entity> entities = Collections.emptyList();
    private boolean replace = false;

    public List<Entity> getEntities()
    {
        return this.entities;
    }

    @JsonSetter("entities")
    void setEntityDefinitions(List<? extends EntityDefinition> entities)
    {
        this.entities = Lists.mutable.withAll(entities);
    }

    public boolean isReplace()
    {
        return this.replace;
    }

    public void setReplace(boolean replace)
    {
        this.replace = replace;
    }

    static class EntityDefinition implements Entity
    {
        String path;
        String classifierPath;
        Map<String, ?> content;

        @Override
        public String getPath()
        {
            return this.path;
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
    }
}
