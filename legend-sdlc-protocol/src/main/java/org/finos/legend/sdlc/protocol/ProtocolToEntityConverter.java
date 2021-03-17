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

package org.finos.legend.sdlc.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.finos.legend.sdlc.domain.model.entity.Entity;

import java.util.Map;
import java.util.Optional;

public abstract class ProtocolToEntityConverter<T>
{
    private final ObjectMapper objectMapper;
    private final JavaType entityContentType;

    protected ProtocolToEntityConverter(ObjectMapper objectMapper)
    {
        this.objectMapper = objectMapper;
        this.entityContentType = objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class);
    }

    public Entity toEntity(T element)
    {
        String classifierPath = getClassifierForClass(element.getClass());
        if (classifierPath == null)
        {
            throw new IllegalArgumentException("Could not convert instance of " + element.getClass().getName() + " to Entity: no appropriate classifier found");
        }
        String path = getEntityPath(element);
        Map<String, ?> content;
        try
        {
            content = convertContent(element);
        }
        catch (Exception e)
        {
            StringBuilder builder = new StringBuilder("Could not convert instance of ").append(element.getClass().getName()).append(" to Entity");
            String message = e.getMessage();
            if (message != null)
            {
                builder.append(": ").append(message);
            }
            throw new IllegalArgumentException(builder.toString(), e);
        }
        return Entity.newEntity(path, classifierPath, content);
    }

    public Optional<Entity> toEntityIfPossible(T element)
    {
        if (element == null)
        {
            return Optional.empty();
        }

        String classifierPath = getClassifierForClass(element.getClass());
        if (classifierPath == null)
        {
            return Optional.empty();
        }

        try
        {
            String path = getEntityPath(element);
            Map<String, ?> content = convertContent(element);
            return Optional.of(Entity.newEntity(path, classifierPath, content));
        }
        catch (Exception ignore)
        {
            return Optional.empty();
        }
    }

    protected abstract String getClassifierForClass(Class<?> cls);

    protected abstract String getEntityPath(T element);

    private Map<String, ?> convertContent(T element) throws JsonProcessingException
    {
        // Note that we cannot use ObjectMapper.convertValue, as some protocol elements may have custom serializers/deserializers which can cause problems with convertValue
        String intermediateJSON = this.objectMapper.writeValueAsString(element);
        return this.objectMapper.readValue(intermediateJSON, this.entityContentType);
    }
}
