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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.sdlc.serialization.EntitySerializer;
import org.finos.legend.sdlc.serialization.EntitySerializers;

import java.io.File;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

@Mojo(name = "process-entities", defaultPhase = LifecyclePhase.COMPILE)
public class EntityMojo extends AbstractMojo
{
    @Parameter
    public List<SourceDirectory> sourceDirectories;

    @Parameter(defaultValue = "${project.build.outputDirectory}")
    public File outputDirectory;

    @Parameter(defaultValue = "${project.basedir}", readonly = true)
    public File baseDir;

    @Override
    public void execute() throws MojoExecutionException
    {
        long start = System.nanoTime();
        getLog().info("Starting entity processing");
        getLog().info("source directories: " + this.sourceDirectories);
        getLog().info("output directory: " + this.outputDirectory);
        getLog().info("base directory: " + this.baseDir);

        EntitySerializer outputSerializer = EntitySerializers.getDefaultJsonSerializer();
        List<SerializationSpec> serializationSpecs = getSerializationSpecs();
        int totalCount = 0;
        for (SerializationSpec serializationSpec : serializationSpecs)
        {
            long sourceStart = System.nanoTime();
            getLog().info("Reserializing entities from " + serializationSpec.directory + " using serializer \"" + serializationSpec.serializer.getName() + "\" to " + this.outputDirectory);
            EntityReserializer reserializer = EntityReserializer.newReserializer(serializationSpec.serializer, outputSerializer);
            Predicate<Path> filter = (serializationSpec.fileExtensions == null) ? null : EntityReserializer.getExtensionsFilter(serializationSpec.fileExtensions);
            try
            {
                List<String> paths = reserializer.reserializeDirectoryTree(serializationSpec.directory, filter, this.outputDirectory.toPath());
                long sourceEnd = System.nanoTime();
                getLog().info(String.format("Finished reserializing %,d entities from %s using serializer \"%s\" to %s (%.9fs)", paths.size(), serializationSpec.directory, serializationSpec.serializer.getName(), this.outputDirectory, nanoDuration(sourceStart, sourceEnd)));
                if (getLog().isDebugEnabled())
                {
                    getLog().debug(Iterate.makeString(paths, "Reserialized: ", ", ", ""));
                }
                totalCount += paths.size();
            }
            catch (Exception e)
            {
                long sourceEnd = System.nanoTime();
                getLog().info(String.format("Error reserializing entities from %s using serializer \"%s\" to %s (%.9fs)", serializationSpec.directory, serializationSpec.serializer.getName(), this.outputDirectory, nanoDuration(sourceStart, sourceEnd)), e);
                StringBuilder builder = new StringBuilder("Error reserializing entities from ").append(serializationSpec.directory)
                        .append(" using serializer \"").append(serializationSpec.serializer.getName()).append('"')
                        .append(" to ").append(this.outputDirectory);
                String eMessage = e.getMessage();
                if (eMessage != null)
                {
                    builder.append(": ").append(eMessage);
                }
                throw new MojoExecutionException(builder.toString(), e);
            }
        }
        long end = System.nanoTime();
        getLog().info(String.format("Finished processing %,d entities (%.9fs)", totalCount, nanoDuration(start, end)));
    }

    private List<SerializationSpec> getSerializationSpecs() throws MojoExecutionException
    {
        Map<String, EntitySerializer> entitySerializers = EntitySerializers.getAvailableSerializersByName();
        if (this.sourceDirectories == null)
        {
            return getDefaultSerializationSpecs(entitySerializers);
        }

        List<SerializationSpec> serializationSpecs = Lists.mutable.empty();
        for (SourceDirectory sourceDirectory : this.sourceDirectories)
        {
            SerializationSpec serializationSpec = getSerializationSpec(sourceDirectory, entitySerializers);
            serializationSpecs.add(serializationSpec);
        }
        return serializationSpecs;
    }

    private SerializationSpec getSerializationSpec(SourceDirectory sourceDirectory, Map<String, EntitySerializer> entitySerializers) throws MojoExecutionException
    {
        Path dir = resolveSourceDirectoryPath(sourceDirectory);

        String serializerName = sourceDirectory.serializer;
        if ((serializerName == null) || serializerName.isEmpty())
        {
            serializerName = sourceDirectory.directory.getName();
        }
        EntitySerializer serializer = entitySerializers.get(serializerName);
        if (serializer == null)
        {
            throw new MojoExecutionException("Unknown entity serializer: " + serializerName);
        }

        return new SerializationSpec(dir, serializer, sourceDirectory.extensions);
    }

    private Path resolveSourceDirectoryPath(SourceDirectory sourceDirectory) throws MojoExecutionException
    {
        Path path = this.baseDir.toPath().resolve(sourceDirectory.directory.toPath());
        BasicFileAttributes attributes;
        try
        {
            attributes = Files.readAttributes(path, BasicFileAttributes.class);
        }
        catch (NoSuchFileException e)
        {
            // source directory does not exist, which is fine
            return path;
        }
        catch (Exception e)
        {
            StringBuilder builder = new StringBuilder("Error accessing source directory \"").append(sourceDirectory.directory).append("\" (").append(path).append(')');
            String eMessage = e.getMessage();
            if (eMessage != null)
            {
                builder.append(": ").append(eMessage);
            }
            throw new MojoExecutionException(builder.toString(), e);
        }
        if (!attributes.isDirectory())
        {
            // source directory exists, but is not a directory
            throw new MojoExecutionException("Invalid source directory \"" + sourceDirectory.directory + "\": " + path + " is not a directory");
        }
        return path;
    }

    private List<SerializationSpec> getDefaultSerializationSpecs(Map<String, EntitySerializer> entitySerializers) throws MojoExecutionException
    {
        Path srcMain = this.baseDir.toPath().resolve("src").resolve("main");
        if (!Files.isDirectory(srcMain))
        {
            getLog().info("using default source directories: []");
            return Collections.emptyList();
        }

        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(srcMain, Files::isDirectory))
        {
            List<SerializationSpec> serializationSpecs = Lists.mutable.empty();
            for (Path dir : dirStream)
            {
                EntitySerializer serializer = entitySerializers.get(dir.getFileName().toString());
                if (serializer != null)
                {
                    serializationSpecs.add(new SerializationSpec(dir, serializer));
                }
            }
            getLog().info("using default source directories: " + serializationSpecs);
            return serializationSpecs;
        }
        catch (Exception e)
        {
            StringBuilder builder = new StringBuilder("Error accessing source directories");
            String eMessage = e.getMessage();
            if (eMessage != null)
            {
                builder.append(": ").append(eMessage);
            }
            throw new MojoExecutionException(builder.toString(), e);
        }
    }

    private static double nanoDuration(long nanoStart, long nanoEnd)
    {
        return (nanoEnd - nanoStart) / 1_000_000_000.0;
    }

    public static class SourceDirectory
    {
        @Parameter(required = true)
        public File directory;

        @Parameter
        public String serializer;

        @Parameter
        public Set<String> extensions;

        @Override
        public String toString()
        {
            StringBuilder builder = new StringBuilder("<directory=").append(this.directory);
            if (this.serializer != null)
            {
                builder.append(" serializer=\"").append(this.serializer).append('"');
            }
            if (this.extensions != null)
            {
                builder.append(" fileExtensions=");
                if (this.extensions.isEmpty())
                {
                    builder.append("[]");
                }
                else
                {
                    Iterate.appendString(this.extensions, builder, "[\"", "\", \"", "\"]");
                }
            }
            return builder.append('>').toString();
        }
    }

    private static class SerializationSpec
    {
        private final Path directory;
        private final EntitySerializer serializer;
        private final Set<String> fileExtensions;

        private SerializationSpec(Path directory, EntitySerializer serializer, Set<String> fileExtensions)
        {
            this.directory = directory;
            this.serializer = serializer;
            this.fileExtensions = fileExtensions;
        }

        private SerializationSpec(Path directory, EntitySerializer serializer)
        {
            this(directory, serializer, null);
        }

        @Override
        public String toString()
        {
            StringBuilder builder = new StringBuilder("<directory=").append(this.directory);
            if (this.serializer != null)
            {
                builder.append(" serializer=\"").append(this.serializer.getName()).append('"');
            }
            if (this.fileExtensions != null)
            {
                builder.append(" fileExtensions=");
                if (this.fileExtensions.isEmpty())
                {
                    builder.append("[]");
                }
                else
                {
                    Iterate.appendString(this.fileExtensions, builder, "[\"", "\", \"", "\"]");
                }
            }
            return builder.append('>').toString();
        }
    }
}
