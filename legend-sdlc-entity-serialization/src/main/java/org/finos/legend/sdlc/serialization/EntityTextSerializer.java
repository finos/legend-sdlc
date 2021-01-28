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

import org.finos.legend.sdlc.domain.model.entity.Entity;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

public interface EntityTextSerializer extends EntitySerializer
{
    // Serialization

    /**
     * Serialize an entity to a writer.
     *
     * @param entity entity to serialize
     * @param writer writer to serialize to
     * @throws IOException if an I/O error occurs
     */
    void serialize(Entity entity, Writer writer) throws IOException;

    @Override
    default void serialize(Entity entity, OutputStream stream) throws IOException
    {
        serialize(entity, new OutputStreamWriter(stream, StandardCharsets.UTF_8));
    }

    /**
     * Serialize an entity to a string.
     *
     * @param entity entity to serialize
     * @return string serialization of entity
     * @throws IOException if an I/O error occurs
     */
    default String serializeToString(Entity entity) throws IOException
    {
        StringWriter writer = new StringWriter();
        serialize(entity, writer);
        return writer.toString();
    }

    // Deserialization

    /**
     * Deserialize an entity from a reader.
     *
     * @param reader input reader
     * @return deserialized entity
     * @throws IOException if an I/O error occurs
     */
    Entity deserialize(Reader reader) throws IOException;

    @Override
    default Entity deserialize(InputStream stream) throws IOException
    {
        return deserialize(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    /**
     * Deserialize an entity from a string.
     *
     * @param content input string
     * @return deserialized entity
     * @throws IOException if an I/O error occurs
     */
    default Entity deserialize(String content) throws IOException
    {
        return deserialize(new StringReader(content));
    }
}
