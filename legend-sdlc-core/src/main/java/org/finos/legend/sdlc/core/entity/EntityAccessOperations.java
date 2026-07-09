// Copyright 2026 Goldman Sachs
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

package org.finos.legend.sdlc.core.entity;

import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.error.LegendSDLCException;
import org.finos.legend.sdlc.project.files.CachingFileAccessContext;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider;
import org.finos.legend.sdlc.project.structure.EntitySourceDirectory;
import org.finos.legend.sdlc.project.structure.ProjectStructure;
import org.finos.legend.sdlc.tools.StringTools;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generic entity read operations over a {@link ProjectFileAccessProvider.FileAccessContext} and the
 * {@link ProjectStructure} it describes: entity-file discovery, deserialization, and predicate filtering.
 * Factored out of the (near-duplicate) GitLab and file-system entity api implementations in re-architecture
 * Phase 3; backends delegate here and add their own error translation where they have any.
 */
public class EntityAccessOperations
{
    private EntityAccessOperations()
    {
    }

    /**
     * Get an entity by path. The optional {@code referenceInfo} is a human-readable description of the source being
     * read (e.g. a workspace/revision description) appended to the not-found message; it may be null.
     *
     * @throws LegendSDLCException with status 404 if no entity file exists for the path, or with status 500 if an
     *         entity file exists but cannot be deserialized (including when its declared path does not match)
     */
    public static Entity getEntity(ProjectFileAccessProvider.FileAccessContext fileAccessContext, String path, String referenceInfo)
    {
        ProjectStructure projectStructure = ProjectStructure.getProjectStructure(fileAccessContext);
        for (EntitySourceDirectory sourceDirectory : projectStructure.getEntitySourceDirectories())
        {
            String filePath = sourceDirectory.entityPathToFilePath(path);
            ProjectFileAccessProvider.ProjectFile file = fileAccessContext.getFile(filePath);
            if (file != null)
            {
                try
                {
                    Entity localEntity = sourceDirectory.deserialize(file);
                    if (!Objects.equals(localEntity.getPath(), path))
                    {
                        throw new RuntimeException("Expected entity path " + path + ", found " + localEntity.getPath());
                    }
                    return localEntity;
                }
                catch (Exception e)
                {
                    StringBuilder builder = new StringBuilder("Error deserializing entity \"").append(path).append("\" from file \"").append(filePath).append('"');
                    StringTools.appendThrowableMessageIfPresent(builder, e);
                    throw new LegendSDLCException(builder.toString(), e);
                }
            }
        }
        StringBuilder builder = new StringBuilder("Unknown entity ").append(path);
        if (referenceInfo != null)
        {
            builder.append(" for ").append(referenceInfo);
        }
        throw new LegendSDLCException(builder.toString(), 404);
    }

    public static List<Entity> getEntities(ProjectFileAccessProvider.FileAccessContext fileAccessContext, Predicate<String> entityPathPredicate, Predicate<String> classifierPathPredicate, Predicate<? super Map<String, ?>> entityContentPredicate, boolean excludeInvalid)
    {
        try (Stream<EntityProjectFile> stream = getEntityProjectFiles(fileAccessContext, entityPathPredicate, classifierPathPredicate, entityContentPredicate, excludeInvalid))
        {
            return stream.map(excludeInvalid ? epf ->
            {
                try
                {
                    return epf.getEntity();
                }
                catch (Exception ignore)
                {
                    return null;
                }
            } : EntityProjectFile::getEntity).filter(Objects::nonNull).collect(Collectors.toList());
        }
    }

    public static List<String> getEntityPaths(ProjectFileAccessProvider.FileAccessContext fileAccessContext, Predicate<String> entityPathPredicate, Predicate<String> classifierPathPredicate, Predicate<? super Map<String, ?>> entityContentPredicate)
    {
        try (Stream<EntityProjectFile> stream = getEntityProjectFiles(fileAccessContext, entityPathPredicate, classifierPathPredicate, entityContentPredicate))
        {
            return stream.map(EntityProjectFile::getEntityPath).collect(Collectors.toList());
        }
    }

    public static Stream<EntityProjectFile> getEntityProjectFiles(ProjectFileAccessProvider.FileAccessContext fileAccessContext, Predicate<String> entityPathPredicate, Predicate<String> classifierPathPredicate, Predicate<? super Map<String, ?>> contentPredicate)
    {
        return getEntityProjectFiles(fileAccessContext, entityPathPredicate, classifierPathPredicate, contentPredicate, false);
    }

    public static Stream<EntityProjectFile> getEntityProjectFiles(ProjectFileAccessProvider.FileAccessContext fileAccessContext, Predicate<String> entityPathPredicate, Predicate<String> classifierPathPredicate, Predicate<? super Map<String, ?>> contentPredicate, boolean excludeInvalid)
    {
        Stream<EntityProjectFile> stream = getEntityProjectFiles(fileAccessContext);
        if (entityPathPredicate != null)
        {
            stream = stream.filter(epf -> entityPathPredicate.test(epf.getEntityPath()));
        }
        if (classifierPathPredicate != null)
        {
            stream = stream.filter(excludeInvalid ? epf ->
            {
                Entity entity;
                try
                {
                    entity = epf.getEntity();
                }
                catch (Exception ignore)
                {
                    return false;
                }
                return classifierPathPredicate.test(entity.getClassifierPath());
            } : epf -> classifierPathPredicate.test(epf.getEntity().getClassifierPath()));
        }
        if (contentPredicate != null)
        {
            stream = stream.filter(excludeInvalid ? epf ->
            {
                Entity entity;
                try
                {
                    entity = epf.getEntity();
                }
                catch (Exception ignore)
                {
                    return false;
                }
                return contentPredicate.test(entity.getContent());
            } : epf -> contentPredicate.test(epf.getEntity().getContent()));
        }
        return stream;
    }

    public static Stream<EntityProjectFile> getEntityProjectFiles(ProjectFileAccessProvider.FileAccessContext accessContext)
    {
        ProjectStructure projectStructure = ProjectStructure.getProjectStructure(accessContext);
        List<EntitySourceDirectory> sourceDirectories = projectStructure.getEntitySourceDirectories();
        ProjectFileAccessProvider.FileAccessContext cachingAccessContext = (sourceDirectories.size() > 1) ? CachingFileAccessContext.wrap(accessContext) : accessContext;
        return sourceDirectories.stream().flatMap(sd -> getSourceDirectoryProjectFiles(cachingAccessContext, sd));
    }

    private static Stream<EntityProjectFile> getSourceDirectoryProjectFiles(ProjectFileAccessProvider.FileAccessContext accessContext, EntitySourceDirectory sourceDirectory)
    {
        return accessContext.getFilesInDirectory(sourceDirectory.getDirectory())
                .filter(f -> sourceDirectory.isPossiblyEntityFilePath(f.getPath()))
                .map(f -> new EntityProjectFile(sourceDirectory, f));
    }
}
