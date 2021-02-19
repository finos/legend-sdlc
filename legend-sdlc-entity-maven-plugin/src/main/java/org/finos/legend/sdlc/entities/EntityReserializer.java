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

package org.finos.legend.sdlc.entities;

import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.serialization.EntitySerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.function.Predicate;

public class EntityReserializer
{
    private static final Logger LOGGER = LoggerFactory.getLogger(EntityReserializer.class);

    private final EntitySerializer sourceSerializer;
    private final EntitySerializer targetSerializer;
    private final String targetFileExtension;

    private EntityReserializer(EntitySerializer sourceSerializer, EntitySerializer targetSerializer, String targetFileExtension)
    {
        this.sourceSerializer = sourceSerializer;
        this.targetSerializer = targetSerializer;
        this.targetFileExtension = (targetFileExtension == null) ? this.targetSerializer.getDefaultFileExtension() : targetFileExtension;
    }

    public List<String> reserializeDirectoryTree(Path sourceDirectory, Path targetDirectory) throws IOException
    {
        return reserializeDirectoryTree(sourceDirectory, null, targetDirectory);
    }

    public List<String> reserializeDirectoryTree(Path sourceDirectory, Predicate<? super Path> filter, Path targetDirectory) throws IOException
    {
        if (Files.notExists(sourceDirectory))
        {
            LOGGER.debug("Source directory {} does not exist: no entities reserialized to {}", sourceDirectory, targetDirectory);
            return Collections.emptyList();
        }

        List<String> entityPaths = Lists.mutable.empty();
        Deque<Path> directories = new ArrayDeque<>();
        directories.add(sourceDirectory);
        while (!directories.isEmpty())
        {
            Path directory = directories.removeFirst();
            try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(directory))
            {
                for (Path entry : dirStream)
                {
                    if (Files.isDirectory(entry))
                    {
                        directories.addLast(entry);
                    }
                    else if ((filter == null) || filter.test(entry))
                    {
                        entityPaths.add(reserializeFile(entry, targetDirectory));
                    }
                }
            }
        }

        return entityPaths;
    }

    public Predicate<Path> getDefaultExtensionFilter()
    {
        return getExtensionFilter(this.sourceSerializer.getDefaultFileExtension());
    }

    private String reserializeFile(Path sourceFile, Path targetDirectory) throws IOException
    {
        LOGGER.debug("Reading {}", sourceFile);
        Entity entity;
        try (InputStream inputStream = new BufferedInputStream(Files.newInputStream(sourceFile)))
        {
            entity = this.sourceSerializer.deserialize(inputStream);
        }
        catch (Exception e)
        {
            StringBuilder builder = new StringBuilder("Error deserializing entity from ").append(sourceFile);
            String eMessage = e.getMessage();
            if (eMessage != null)
            {
                builder.append(": ").append(eMessage);
            }
            String message = builder.toString();
            LOGGER.debug(message, e);
            if (e instanceof IOException)
            {
                throw new IOException(message, e);
            }
            throw new RuntimeException(message, e);
        }
        LOGGER.debug("Finished reading {} from {}", entity.getPath(), sourceFile);

        Path targetFile = generateTargetFilePath(targetDirectory, entity);
        LOGGER.debug("Writing {} to {}", entity.getPath(), targetFile);
        Files.createDirectories(targetFile.getParent());
        try (OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(targetFile, StandardOpenOption.CREATE_NEW)))
        {
            this.targetSerializer.serialize(entity, outputStream);
        }
        catch (Exception e)
        {
            StringBuilder builder = new StringBuilder("Error serializing entity '").append(entity.getPath()).append("' to ").append(targetFile);
            if (e instanceof FileAlreadyExistsException)
            {
                builder.append(": target file already exists");
            }
            else
            {
                String eMessage = e.getMessage();
                if (eMessage != null)
                {
                    builder.append(": ").append(eMessage);
                }
            }
            String message = builder.toString();
            LOGGER.debug(message, e);
            if (e instanceof IOException)
            {
                throw new IOException(message, e);
            }
            throw new RuntimeException(message, e);
        }
        LOGGER.debug("Finished writing {} to {}", entity.getPath(), targetFile);
        return entity.getPath();
    }

    private Path generateTargetFilePath(Path targetDirectory, Entity entity)
    {
        return generateTargetFilePath(targetDirectory, entity.getPath());
    }

    private Path generateTargetFilePath(Path targetDirectory, String entityPath)
    {
        String separator = targetDirectory.getFileSystem().getSeparator();
        String relativePath = "entities" + separator + entityPath.replace("::", separator) + "." + this.targetFileExtension;
        return targetDirectory.resolve(relativePath);
    }

    public static EntityReserializer newReserializer(EntitySerializer sourceSerializer, EntitySerializer targetSerializer, String targetFileExtension)
    {
        return new EntityReserializer(sourceSerializer, targetSerializer, targetFileExtension);
    }

    public static EntityReserializer newReserializer(EntitySerializer sourceSerializer, EntitySerializer targetSerializer)
    {
        return newReserializer(sourceSerializer, targetSerializer, null);
    }

    public static Predicate<Path> getExtensionFilter(String extension)
    {
        String canonicalExtension = canonicalizeFileExtension(extension);
        return (canonicalExtension == null) ? EntityReserializer::hasNoExtension : p -> hasExtension(p, canonicalExtension);
    }

    public static Predicate<Path> getExtensionsFilter(String... extensions)
    {
        return getExtensionsFilter(Arrays.asList(extensions));
    }

    public static Predicate<Path> getExtensionsFilter(Collection<? extends String> extensions)
    {
        switch (extensions.size())
        {
            case 0:
            {
                return p -> false;
            }
            case 1:
            {
                return getExtensionFilter(Iterate.getFirst(extensions));
            }
            default:
            {
                MutableList<String> canonicalExtensions = Iterate.collect(extensions, EntityReserializer::canonicalizeFileExtension, Lists.mutable.ofInitialCapacity(extensions.size()));
                return p -> hasAnyExtension(p, canonicalExtensions);
            }
        }
    }

    private static boolean hasNoExtension(Path path)
    {
        return hasNoExtension(path.getFileName().toString());
    }

    private static boolean hasNoExtension(String fileName)
    {
        return fileName.lastIndexOf('.') == -1;
    }

    private static boolean hasExtension(Path path, String canonicalExtension)
    {
        return hasExtension(path.getFileName().toString(), canonicalExtension);
    }

    private static boolean hasExtension(String fileName, String canonicalExtension)
    {
        return fileName.endsWith(canonicalExtension);
    }

    private static boolean hasAnyExtension(Path path, RichIterable<String> canonicalExtensions)
    {
        String fileName = path.getFileName().toString();
        return canonicalExtensions.anySatisfy(ex -> (ex == null) ? hasNoExtension(fileName) : hasExtension(fileName, ex));
    }

    private static String canonicalizeFileExtension(String extension)
    {
        if ((extension == null) || extension.isEmpty())
        {
            return null;
        }
        if (extension.charAt(0) != '.')
        {
            return "." + extension;
        }
        return extension;
    }
}
