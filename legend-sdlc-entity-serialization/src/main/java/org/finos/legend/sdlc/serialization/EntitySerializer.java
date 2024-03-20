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
import org.finos.legend.sdlc.tools.entity.EntityPaths;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

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
     * Return whether the entity can be serialized by this serializer.
     *
     * @param entity entity
     * @return whether entity can be serialized
     */
    boolean canSerialize(Entity entity);

    /**
     * Serialize an entity to an output stream.
     *
     * @param entity entity to serialize
     * @param stream output stream to serialize to
     * @throws IOException if an I/O error occurs
     */
    void serialize(Entity entity, OutputStream stream) throws IOException;

    /**
     * Serialize an entity to a byte array.
     *
     * @param entity entity to serialize
     * @return byte array serialization of entity
     * @throws IOException if an I/O error occurs
     */
    default byte[] serializeToBytes(Entity entity) throws IOException
    {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        serialize(entity, stream);
        return stream.toByteArray();
    }

    /**
     * Serialize an entity to a file. The file is created relative to rootDirectory by using the entity's path to
     * generate a file path (see {@link #filePathForEntity(Entity, Path)} for more details). Any directories that need
     * to be created will be created.
     *
     * @param entity        entity to serialize
     * @param rootDirectory root directory for entity serialization
     * @param openOptions   options specifying how the file is to be opened
     * @return the path the entity is written to
     * @throws IOException if an I/O error occurs
     */
    default Path serializeToFile(Entity entity, Path rootDirectory, OpenOption... openOptions) throws IOException
    {
        return serializeToFile(entity, rootDirectory, getDefaultFileExtension(), openOptions);
    }

    /**
     * Serialize an entity to a file. The file is created relative to rootDirectory by using the entity's path and
     * fileExtension to generate a file path (see {@link #filePathForEntity(Entity, Path, String)} for more details).
     * Any directories that need to be created will be created.
     *
     * @param entity        entity to serialize
     * @param rootDirectory root directory for entity serialization
     * @param fileExtension extension for the entity file
     * @param openOptions   options specifying how the file is to be opened
     * @return the path the entity is written to
     * @throws IOException if an I/O error occurs
     */
    default Path serializeToFile(Entity entity, Path rootDirectory, String fileExtension, OpenOption... openOptions) throws IOException
    {
        Path filePath = filePathForEntity(entity, rootDirectory, fileExtension);
        Files.createDirectories(filePath.getParent());
        try (OutputStream stream = new BufferedOutputStream(Files.newOutputStream(filePath, openOptions)))
        {
            serialize(entity, stream);
        }
        return filePath;
    }

    /**
     * Create a file path for an entity in a directory. The file will have the same name as the entity with the default
     * file extension, and will be in a subdirectory based on the entity's package. For example, if the entity path is
     * {@code "a::b::c::Name"}, the root directory is {@code target/entities}, and the default extension is
     * {@code "json"}, then the file path will be {@code target/entities/a/b/c/Name.json}.
     *
     * @param entity        entity
     * @param rootDirectory root directory for entity files
     * @return file path for an entity
     */
    default Path filePathForEntity(Entity entity, Path rootDirectory)
    {
        return filePathForEntity(entity, rootDirectory, getDefaultFileExtension());
    }

    /**
     * Create a file path for an entity in a directory. The file will have the same name as the entity with the given
     * file extension, and will be in a subdirectory based on the entity's package. For example, if the entity path is
     * {@code "a::b::c::Name"}, the root directory is {@code target/entities}, and the extension is {@code "json"}, then
     * the file path will be {@code target/entities/a/b/c/Name.json}.
     *
     * @param entity        entity
     * @param rootDirectory root directory for entity files
     * @param extension     extension for the entity file
     * @return file path for an entity
     */
    default Path filePathForEntity(Entity entity, Path rootDirectory, String extension)
    {
        String relativePath = entity.getPath().replace(EntityPaths.PACKAGE_SEPARATOR, rootDirectory.getFileSystem().getSeparator()) + ((extension == null) ? "" : ("." + extension));
        return rootDirectory.resolve(relativePath);
    }

    // Deserialization

    /**
     * Deserialize an entity from an input stream.
     *
     * @param stream input stream
     * @return deserialized entity
     * @throws IOException if an I/O error occurs
     */
    Entity deserialize(InputStream stream) throws IOException;

    /**
     * Deserialize an entity from a byte array.
     *
     * @param content input bytes
     * @return deserialized entity
     * @throws IOException if an I/O error occurs
     */
    default Entity deserialize(byte[] content) throws IOException
    {
        return deserialize(new ByteArrayInputStream(content));
    }

    /**
     * Deserialize entities from an input stream.
     *
     * @param stream input stream
     * @return deserialized entities
     * @throws IOException if an I/O error occurs
     */
    default List<Entity> deserializeMany(InputStream stream) throws IOException
    {
        return Collections.singletonList(deserialize(stream));
    }

    /**
     * Deserialize entities from a byte array.
     *
     * @param content input bytes
     * @return deserialized entities
     * @throws IOException if an I/O error occurs
     */
    default List<Entity> deserializeMany(byte[] content) throws IOException
    {
        return deserializeMany(new ByteArrayInputStream(content));
    }
}
