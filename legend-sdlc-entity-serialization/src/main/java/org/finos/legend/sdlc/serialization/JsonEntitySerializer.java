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

package org.finos.legend.sdlc.serialization;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.finos.legend.sdlc.domain.model.entity.Entity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.Map;

class JsonEntitySerializer implements EntityTextSerializer
{
    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(SerializationFeature.INDENT_OUTPUT, true)
            .configure(SerializationFeature.CLOSE_CLOSEABLE, false);
    private static final JavaType ENTITY_FILE_TYPE = JSON.getTypeFactory().constructType(EntityFile.class);

    static final JsonEntitySerializer INSTANCE = new JsonEntitySerializer();

    private JsonEntitySerializer()
    {
    }

    @Override
    public String getDefaultFileExtension()
    {
        return "json";
    }

    // Serialization

    @Override
    public void serialize(Entity entity, OutputStream stream) throws IOException
    {
        JSON.writeValue(stream, toEntityFile(entity));
    }

    @Override
    public void serialize(Entity entity, Writer writer) throws IOException
    {
        JSON.writeValue(writer, toEntityFile(entity));
    }

    @Override
    public byte[] serializeToBytes(Entity entity) throws IOException
    {
        return JSON.writeValueAsBytes(toEntityFile(entity));
    }

    @Override
    public String serializeToString(Entity entity) throws IOException
    {
        return JSON.writeValueAsString(toEntityFile(entity));
    }

    // Deserialization

    @Override
    public Entity deserialize(InputStream stream) throws IOException
    {
        return toEntity(JSON.readValue(stream, ENTITY_FILE_TYPE));
    }

    @Override
    public Entity deserialize(Reader reader) throws IOException
    {
        return toEntity(JSON.readValue(reader, ENTITY_FILE_TYPE));
    }

    @Override
    public Entity deserialize(byte[] content) throws IOException
    {
        return toEntity(JSON.readValue(content, ENTITY_FILE_TYPE));
    }

    @Override
    public Entity deserialize(String content) throws IOException
    {
        return toEntity(JSON.readValue(content, ENTITY_FILE_TYPE));
    }

    // Helpers

    private static EntityFile toEntityFile(Entity entity)
    {
        return new EntityFile(entity.getClassifierPath(), entity.getContent());
    }

    private static Entity toEntity(EntityFile entityFile)
    {
        return Entity.newEntity(computeEntityPath(entityFile), entityFile.classifierPath, entityFile.content);
    }

    private static String computeEntityPath(EntityFile entityFile)
    {
        Map<String, ?> content = entityFile.content;
        if (content != null)
        {
            Object pkg = content.get("package");
            Object name = content.get("name");
            if ((pkg instanceof String) && (name instanceof String))
            {
                return pkg + "::" + name;
            }
        }
        throw new RuntimeException("Could not compute entity path");
    }

    private static class EntityFile
    {
        public String classifierPath;
        public Map<String, ?> content;

        EntityFile()
        {
        }

        EntityFile(String classifierPath, Map<String, ?> content)
        {
            this.classifierPath = classifierPath;
            this.content = content;
        }
    }
}
