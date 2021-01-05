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

import org.eclipse.collections.api.factory.Lists;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class EntityLoader implements AutoCloseable
{
    private static final Logger LOGGER = LoggerFactory.getLogger(EntityLoader.class);

    private static final EntitySerializer ENTITY_SERIALIZER = EntitySerializers.getDefaultJsonSerializer();
    private static final String ENTITIES_DIRECTORY = "entities";
    private static final String ENTITY_FILE_EXTENSION = "." + ENTITY_SERIALIZER.getDefaultFileExtension();

    private final List<EntityFileSearch> searchList;

    private EntityLoader(List<EntityFileSearch> searchList)
    {
        this.searchList = searchList;
    }

    public Entity getEntity(String entityPath)
    {
        String entityFilePath = entityPathToFilePath(entityPath);
        return this.searchList.stream()
                .map(s -> s.getPath(entityFilePath))
                .filter(EntityLoader::isPossiblyEntityFile)
                .map(EntityLoader::readEntity)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    public Stream<Entity> getAllEntities()
    {
        try
        {
            return getEntitiesInDirectory(ENTITIES_DIRECTORY);
        }
        catch (Exception e)
        {
            StringBuilder builder = new StringBuilder("Error getting all entities");
            String eMessage = e.getMessage();
            if (eMessage != null)
            {
                builder.append(": ").append(eMessage);
            }
            throw new RuntimeException(builder.toString(), e);
        }
    }

    public Stream<Entity> getEntitiesInPackage(String packagePath)
    {
        try
        {
            return getEntitiesInDirectory(packagePathToDirectoryPath(packagePath));
        }
        catch (Exception e)
        {
            StringBuilder builder = new StringBuilder("Error getting all entities from package '").append(packagePath).append('\'');
            String eMessage = e.getMessage();
            if (eMessage != null)
            {
                builder.append(": ").append(eMessage);
            }
            throw new RuntimeException(builder.toString(), e);
        }
    }

    @Override
    public synchronized void close() throws Exception
    {
        Exception exception = null;
        for (AutoCloseable closeable : this.searchList)
        {
            try
            {
                closeable.close();
            }
            catch (Exception e)
            {
                if (exception == null)
                {
                    exception = e;
                }
                else
                {
                    try
                    {
                        exception.addSuppressed(e);
                    }
                    catch (Exception ignore)
                    {
                        // ignore exception trying to add suppressed exception
                    }
                }
            }
        }
        if (exception != null)
        {
            throw exception;
        }
    }

    private Stream<Entity> getEntitiesInDirectory(String directoryPath)
    {
        return this.searchList.stream()
                .flatMap(s -> s.getPathsInDirectory(directoryPath))
                .filter(EntityLoader::isPossiblyEntityFile)
                .map(EntityLoader::readEntity)
                .filter(Objects::nonNull);
    }

    public static EntityLoader newEntityLoader(ClassLoader classLoader)
    {
        return new EntityLoader(Collections.singletonList(new ClassLoaderEntityFileSearch(classLoader)));
    }

    public static EntityLoader newEntityLoader(Path path)
    {
        EntityFileSearch search = newPathEntityFileSearch(path);
        return new EntityLoader((search == null) ? Collections.emptyList() : Collections.singletonList(search));
    }

    public static EntityLoader newEntityLoader(File path)
    {
        return newEntityLoader(path.toPath());
    }

    public static EntityLoader newEntityLoader(Path... paths)
    {
        if ((paths == null) || (paths.length == 0))
        {
            return new EntityLoader(Collections.emptyList());
        }
        if (paths.length == 1)
        {
            return newEntityLoader(paths[0]);
        }
        List<EntityFileSearch> searchList = Lists.mutable.ofInitialCapacity(paths.length);
        Arrays.stream(paths).map(EntityLoader::newPathEntityFileSearch).filter(Objects::nonNull).forEach(searchList::add);
        return new EntityLoader(searchList);
    }

    public static EntityLoader newEntityLoader(File... directories)
    {
        return newEntityLoader((directories == null) ? null : Arrays.stream(directories).map(File::toPath).toArray(Path[]::new));
    }

    public static EntityLoader newEntityLoader(ClassLoader classLoader, Path... paths)
    {
        if ((paths == null) || (paths.length == 0))
        {
            return newEntityLoader(classLoader);
        }

        List<EntityFileSearch> searchList = Lists.mutable.ofInitialCapacity(paths.length + 1);
        searchList.add(new ClassLoaderEntityFileSearch(classLoader));
        Arrays.stream(paths).map(EntityLoader::newPathEntityFileSearch).filter(Objects::nonNull).forEach(searchList::add);
        return new EntityLoader(searchList);
    }

    public static EntityLoader newEntityLoader(ClassLoader classLoader, File... paths)
    {
        return newEntityLoader(classLoader, (paths == null) ? null : Arrays.stream(paths).map(File::toPath).toArray(Path[]::new));
    }

    private static Entity readEntity(Path path)
    {
        try (InputStream stream = Files.newInputStream(path))
        {
            return ENTITY_SERIALIZER.deserialize(stream);
        }
        catch (IOException e)
        {
            LOGGER.error("Error reading entity from file: " + path, e);
            return null;
        }
    }

    private static String entityPathToFilePath(String entityPath)
    {
        StringBuilder builder = new StringBuilder(ENTITIES_DIRECTORY.length() + entityPath.length() + ENTITY_FILE_EXTENSION.length());
        builder.append(ENTITIES_DIRECTORY);
        writePackageablePathAsFilePath(builder, entityPath);
        builder.append(ENTITY_FILE_EXTENSION);
        return builder.toString();
    }

    private static String packagePathToDirectoryPath(String packagePath)
    {
        if ("::".equals(packagePath))
        {
            // special case for root package
            return ENTITIES_DIRECTORY;
        }

        StringBuilder builder = new StringBuilder(ENTITIES_DIRECTORY.length() + packagePath.length());
        builder.append(ENTITIES_DIRECTORY);
        writePackageablePathAsFilePath(builder, packagePath);
        return builder.toString();
    }

    private static void writePackageablePathAsFilePath(StringBuilder builder, String packageablePath)
    {
        int current = 0;
        for (int nextDelim = packageablePath.indexOf(':'); nextDelim != -1; nextDelim = packageablePath.indexOf(':', current))
        {
            builder.append('/').append(packageablePath, current, nextDelim);
            current = nextDelim + 2;
        }
        builder.append('/').append(packageablePath, current, packageablePath.length());
    }

    private static boolean isPossiblyEntityFile(Path path)
    {
        return (path != null) && isPossiblyEntityFileName(path.toString()) && Files.isRegularFile(path);
    }

    private static boolean isPossiblyEntityFileName(String name)
    {
        return (name != null) && name.regionMatches(true, name.length() - ENTITY_FILE_EXTENSION.length(), ENTITY_FILE_EXTENSION, 0, ENTITY_FILE_EXTENSION.length());
    }

    private static Stream<Path> getDirectoryStream(Path dirPath)
    {
        try
        {
            return Files.walk(dirPath, FileVisitOption.FOLLOW_LINKS);
        }
        catch (IOException ignore)
        {
            return Stream.empty();
        }
    }

    private static Path getPathFromURL(URL url)
    {
        try
        {
            return getPathFromURI(url.toURI());
        }
        catch (URISyntaxException e)
        {
            LOGGER.error("Error converting URL to URI: " + url, e);
            return null;
        }
    }

    private static Path getPathFromURI(URI uri)
    {
        try
        {
            return getOrCreateFileSystem(uri).provider().getPath(uri);
        }
        catch (Exception e)
        {
            LOGGER.error("Error getting path from URI: " + uri, e);
            return null;
        }
    }

    private static FileSystem getOrCreateFileSystem(URI uri) throws IOException
    {
        if ("file".equalsIgnoreCase(uri.getScheme()))
        {
            return FileSystems.getDefault();
        }

        try
        {
            // Try to get the FS for the URI
            return FileSystems.getFileSystem(uri);
        }
        catch (FileSystemNotFoundException ignore)
        {
            try
            {
                // If the FS doesn't already exist, try to create it
                return FileSystems.newFileSystem(uri, Collections.emptyMap());
            }
            catch (FileSystemAlreadyExistsException ignoreAlso)
            {
                // If the FS has been created in the meantime, try again to get it (which should work)
                return FileSystems.getFileSystem(uri);
            }
        }
    }

    private static EntityFileSearch newPathEntityFileSearch(Path path)
    {
        try
        {
            BasicFileAttributes attributes;
            try
            {
                attributes = Files.readAttributes(path, BasicFileAttributes.class);
            }
            catch (NoSuchFileException e)
            {
                return null;
            }

            if (attributes.isDirectory())
            {
                return new DirectoryEntityFileSearch(path);
            }

            FileSystem fs = FileSystems.newFileSystem(path, EntityLoader.class.getClassLoader());
            return new DirectoryEntityFileSearchWithCloseable(fs.getPath(fs.getSeparator()), fs);
        }
        catch (Exception e)
        {
            StringBuilder builder = new StringBuilder("Error handling ").append(path);
            String eMessage = e.getMessage();
            if (eMessage != null)
            {
                builder.append(": ").append(eMessage);
            }
            throw new RuntimeException(builder.toString(), e);
        }
    }

    private interface EntityFileSearch extends AutoCloseable
    {
        Path getPath(String filePath);

        Stream<Path> getPathsInDirectory(String dirPath);
    }

    private static class ClassLoaderEntityFileSearch implements EntityFileSearch
    {
        private final ClassLoader classLoader;

        private ClassLoaderEntityFileSearch(ClassLoader classLoader)
        {
            this.classLoader = classLoader;
        }

        @Override
        public Path getPath(String filePath)
        {
            URL url = this.classLoader.getResource(filePath);
            return (url == null) ? null : getPathFromURL(url);
        }

        @Override
        public Stream<Path> getPathsInDirectory(String dirPath)
        {
            Enumeration<URL> urls;
            try
            {
                urls = this.classLoader.getResources(dirPath);
            }
            catch (IOException ignore)
            {
                return Stream.empty();
            }
            return StreamSupport.stream(Spliterators.spliteratorUnknownSize(new Iterator<URL>()
            {
                @Override
                public boolean hasNext()
                {
                    return urls.hasMoreElements();
                }

                @Override
                public URL next()
                {
                    return urls.nextElement();
                }
            }, 0), false)
                    .map(EntityLoader::getPathFromURL)
                    .filter(Objects::nonNull)
                    .filter(Files::isDirectory)
                    .flatMap(EntityLoader::getDirectoryStream);
        }

        @Override
        public void close()
        {
        }
    }

    private static class DirectoryEntityFileSearch implements EntityFileSearch
    {
        private final Path directory;

        private DirectoryEntityFileSearch(Path directory)
        {
            this.directory = directory;
        }

        @Override
        public Path getPath(String filePath)
        {
            return this.directory.resolve(filePath);
        }

        @Override
        public Stream<Path> getPathsInDirectory(String dirPath)
        {
            Path resolvedPath = this.directory.resolve(dirPath);
            return Files.isDirectory(resolvedPath) ? EntityLoader.getDirectoryStream(resolvedPath) : Stream.empty();
        }

        @Override
        public void close() throws Exception
        {
        }
    }

    private static class DirectoryEntityFileSearchWithCloseable extends DirectoryEntityFileSearch
    {
        private final AutoCloseable closeable;

        private DirectoryEntityFileSearchWithCloseable(Path directory, AutoCloseable closeable)
        {
            super(directory);
            this.closeable = closeable;
        }

        @Override
        public void close() throws Exception
        {
            this.closeable.close();
        }
    }
}
