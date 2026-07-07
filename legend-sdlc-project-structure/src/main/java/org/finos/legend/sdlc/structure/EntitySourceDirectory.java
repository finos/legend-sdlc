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

package org.finos.legend.sdlc.structure;

import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.serialization.EntitySerializer;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
import org.finos.legend.sdlc.error.LegendSDLCException;
import org.finos.legend.sdlc.tools.StringTools;
import org.finos.legend.sdlc.tools.entity.EntityPaths;

import java.io.IOException;
import java.io.InputStream;

public class EntitySourceDirectory
{
    private final String directory;
    private final EntitySerializer serializer;

    EntitySourceDirectory(String directory, EntitySerializer serializer)
    {
        this.directory = directory;
        this.serializer = serializer;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }
        if (!(other instanceof EntitySourceDirectory))
        {
            return false;
        }
        EntitySourceDirectory that = (EntitySourceDirectory) other;
        return this.directory.equals(that.directory) && this.serializer.getName().equals(that.serializer.getName());
    }

    @Override
    public int hashCode()
    {
        return this.directory.hashCode() ^ this.serializer.getName().hashCode();
    }

    @Override
    public String toString()
    {
        return "<EntitySourceDirectory directory=" + this.directory + " serializer=" + this.serializer.getName() + ">";
    }

    // File paths

    public String getDirectory()
    {
        return this.directory;
    }

    /**
     * Return whether the given file path is possibly an entity file path. Note that this is a purely syntactic
     * check and does not imply anything about whether the file actually exists or what it contains.
     *
     * @param filePath file path
     * @return whether filePath is possibly an entity file path
     */
    public boolean isPossiblyEntityFilePath(String filePath)
    {
        return (filePath != null) && (filePath.length() > (this.directory.length() + this.serializer.getDefaultFileExtension().length() + 2)) && filePathStartsWithDirectory(filePath) && filePathHasEntityExtension(filePath);
    }

    /**
     * Return the file path corresponding to the given entity path. The slash character ('/') is used to separate
     * directories within the path. Paths will always begin with /, and will never be empty. Note that the file
     * path will be returned regardless of whether the file actually exists.
     *
     * @param entityPath entity path
     * @return corresponding file path
     */
    public String entityPathToFilePath(String entityPath)
    {
        StringBuilder builder = new StringBuilder(this.directory.length() + entityPath.length() + this.serializer.getDefaultFileExtension().length());
        builder.append(this.directory);
        appendPackageablePathAsFilePath(builder, entityPath);
        builder.append('.').append(this.serializer.getDefaultFileExtension());
        return builder.toString();
    }

    /**
     * Return the entity path that corresponds to the given file path.
     *
     * @param filePath file path
     * @return corresponding entity path
     * @throws IllegalArgumentException if filePath is not a valid file path
     */
    public String filePathToEntityPath(String filePath)
    {
        int start = this.directory.length() + 1;
        int end = filePath.length() - (this.serializer.getDefaultFileExtension().length() + 1);
        int length = end - start;
        StringBuilder builder = new StringBuilder(length + (length / 4));
        appendFilePathAsPackageablePath(builder, filePath, start, end);
        return builder.toString();
    }

    /**
     * Return the file path corresponding to the given package path. The slash character ('/') is used to separate
     * directories within the path. Paths will always begin with /, and will never be empty. Note the the file path
     * will refer to a directory and will be returned regardless of whether the directory actually exists.
     *
     * @param packagePath package path
     * @return corresponding file path
     */
    public String packagePathToFilePath(String packagePath)
    {
        StringBuilder builder = new StringBuilder(this.directory.length() + packagePath.length());
        builder.append(this.directory);
        appendPackageablePathAsFilePath(builder, packagePath);
        return builder.toString();
    }

    private boolean filePathStartsWithDirectory(String filePath)
    {
        return filePath.startsWith(this.directory) && ((filePath.length() == this.directory.length()) || (filePath.charAt(this.directory.length()) == '/'));
    }

    private boolean filePathHasEntityExtension(String filePath)
    {
        String extension = this.serializer.getDefaultFileExtension();
        return filePath.endsWith(extension) && (filePath.length() > extension.length()) && (filePath.charAt(filePath.length() - (extension.length() + 1)) == '.');
    }

    // Serialization

    public EntitySerializer getSerializer()
    {
        return this.serializer;
    }

    public boolean canSerialize(Entity entity)
    {
        return this.serializer.canSerialize(entity);
    }

    public byte[] serializeToBytes(Entity entity)
    {
        try
        {
            return this.serializer.serializeToBytes(entity);
        }
        catch (Exception e)
        {
            StringBuilder message = new StringBuilder("Error serializing entity ").append(entity.getPath());
            StringTools.appendThrowableMessageIfPresent(message, e);
            throw new LegendSDLCException(message.toString(), e);
        }
    }

    public Entity deserialize(ProjectFileAccessProvider.ProjectFile projectFile)
    {
        try (InputStream stream = projectFile.getContentAsInputStream())
        {
            return this.serializer.deserialize(stream);
        }
        catch (Exception e)
        {
            String eMessage = e.getMessage();
            if ((e instanceof RuntimeException) && (eMessage != null) && eMessage.startsWith("Error deserializing entity "))
            {
                throw (RuntimeException) e;
            }
            StringBuilder builder = new StringBuilder("Error deserializing entity from file ").append(projectFile.getPath());
            if (eMessage != null)
            {
                builder.append(": ").append(eMessage);
            }
            throw new LegendSDLCException(builder.toString(), e);
        }
    }

    public Entity deserialize(byte[] content) throws IOException
    {
        return this.serializer.deserialize(content);
    }

    private static void appendPackageablePathAsFilePath(StringBuilder builder, String packageablePath)
    {
        EntityPaths.forEachPathElement(packageablePath, elt -> builder.append('/').append(elt));
    }

    private static void appendFilePathAsPackageablePath(StringBuilder builder, String filePath, int start, int end)
    {
        int current = start;
        int next = filePath.indexOf('/', current);
        while ((next != -1) && (next < end))
        {
            builder.append(filePath, current, next).append(EntityPaths.PACKAGE_SEPARATOR);
            current = next + 1;
            next = filePath.indexOf('/', current);
        }
        builder.append(filePath, current, end);
    }
}
