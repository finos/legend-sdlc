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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface EntitySerializer
{
    /**
     * Get the name of the serialization format.
     *
     * @return serialization format name
     */
    String getName();

    /**
     * Get the default file extension for entities serialized in this format.
     *
     * @return default file extension
     */
    String getDefaultFileExtension();

    // Serialization

    /**
     * Serialize an entity to an output stream.
     *
     * @param entity entity to serialize
     * @param stream output stream to serialize to
     * @throws IOException
     */
    void serialize(Entity entity, OutputStream stream) throws IOException;

    /**
     * Serialize an entity to a byte array.
     *
     * @param entity entity to serialize
     * @return byte array serialization of entity
     * @throws IOException
     */
    default byte[] serializeToBytes(Entity entity) throws IOException
    {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        serialize(entity, stream);
        return stream.toByteArray();
    }

    // Deserialization

    /**
     * Deserialize an entity from an input stream.
     *
     * @param stream input stream
     * @return deserialized entity
     * @throws IOException
     */
    Entity deserialize(InputStream stream) throws IOException;

    /**
     * Deserialize an entity from a byte array.
     *
     * @param content input bytes
     * @return deserialized entity
     * @throws IOException
     */
    default Entity deserialize(byte[] content) throws IOException
    {
        return deserialize(new ByteArrayInputStream(content));
    }
}
