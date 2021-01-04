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

package org.finos.legend.sdlc.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.finos.legend.sdlc.domain.model.entity.Entity;

import java.util.Map;
import java.util.Optional;

public abstract class EntityToProtocolConverter<T>
{
    private final ObjectMapper objectMapper;

    protected EntityToProtocolConverter(ObjectMapper objectMapper)
    {
        this.objectMapper = objectMapper;
    }

    public T fromEntity(Entity entity)
    {
        if (entity == null)
        {
            throw new IllegalArgumentException("Cannot convert null entity");
        }

        Map<String, ?> content = entity.getContent();
        if (content == null)
        {
            throw new IllegalArgumentException("Could not convert entity " + entity.getPath() + " with classifier " + entity.getClassifierPath() + ": null content");
        }

        Class<? extends T> targetClass = getTargetClass(entity);
        if (targetClass == null)
        {
            throw new IllegalArgumentException("Could not convert entity " + entity.getPath() + " with classifier " + entity.getClassifierPath() + ": no appropriate target class found");
        }

        try
        {
            return this.objectMapper.convertValue(content, targetClass);
        }
        catch (Exception e)
        {
            StringBuilder builder = new StringBuilder("Could not convert entity ").append(entity.getPath()).append(" with classifier ").append(entity.getClassifierPath()).append(" to class ").append(targetClass.getSimpleName());
            String eMessage = e.getMessage();
            if (eMessage != null)
            {
                builder.append(": ").append(eMessage);
            }
            throw new IllegalArgumentException(builder.toString(), e);
        }
    }

    public Optional<T> fromEntityIfPossible(Entity entity)
    {
        if (entity != null)
        {
            Map<String, ?> content = entity.getContent();
            if (content != null)
            {
                Class<? extends T> targetClass = getTargetClass(entity);
                if (targetClass != null)
                {
                    try
                    {
                        return Optional.ofNullable(this.objectMapper.convertValue(content, targetClass));
                    }
                    catch (Exception ignore)
                    {
                        // could not convert
                    }
                }
            }
        }
        return Optional.empty();
    }

    protected abstract Class<? extends T> getTargetClass(Entity entity);
}
