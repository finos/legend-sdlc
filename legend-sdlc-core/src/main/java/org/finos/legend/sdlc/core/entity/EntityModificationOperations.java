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

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.impl.utility.ListIterate;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.entity.change.EntityChange;
import org.finos.legend.sdlc.domain.model.entity.change.EntityChangeType;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.error.LegendSDLCException;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider;
import org.finos.legend.sdlc.project.files.ProjectFileOperation;
import org.finos.legend.sdlc.project.structure.EntitySourceDirectory;
import org.finos.legend.sdlc.project.structure.ProjectStructure;
import org.finos.legend.sdlc.server.domain.api.project.source.WorkspaceSourceSpecification;
import org.finos.legend.sdlc.tools.entity.EntityPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Generic entity write operations over a {@link ProjectFileAccessProvider}: computing entity changes for a bulk
 * update, validating entity changes, translating them to file operations against the project structure, and
 * submitting them. Factored out of the (near-duplicate) GitLab and file-system entity api implementations in
 * re-architecture Phase 3; backends delegate here and add their own error translation where they have any.
 */
public class EntityModificationOperations
{
    private static final Logger LOGGER = LoggerFactory.getLogger(EntityModificationOperations.class);

    private EntityModificationOperations()
    {
    }

    /**
     * Bulk-update the entities of a workspace: create entities that do not exist, modify those that differ, and
     * (if {@code replace} is true) delete existing entities absent from {@code newEntities}. The
     * {@code referenceInfo} is a human-readable description of the workspace used in error messages. Returns the
     * revision created, or null if the update requires no changes.
     */
    public static Revision updateEntities(ProjectFileAccessProvider fileProvider, String projectId, WorkspaceSourceSpecification sourceSpecification, Iterable<? extends Entity> newEntities, boolean replace, String message, String referenceInfo)
    {
        MutableMap<String, Entity> newEntityDefinitions = indexAndValidateEntitiesForUpdate(newEntities);

        Revision currentWorkspaceRevision = fileProvider.getRevisionAccessContext(projectId, sourceSpecification, null).getCurrentRevision();
        if (currentWorkspaceRevision == null)
        {
            throw new LegendSDLCException("Could not find current revision for " + referenceInfo + ": it may be corrupt");
        }
        String revisionId = currentWorkspaceRevision.getId();
        LOGGER.debug("Using revision {} for reference in entity update in {} in project {}", revisionId, sourceSpecification, projectId);
        List<EntityChange> entityChanges = Lists.mutable.ofInitialCapacity(newEntityDefinitions.size());
        if (newEntityDefinitions.notEmpty())
        {
            try (Stream<EntityProjectFile> stream = EntityAccessOperations.getEntityProjectFiles(fileProvider.getFileAccessContext(projectId, sourceSpecification, revisionId)))
            {
                stream.forEach(epf ->
                {
                    String path = epf.getEntityPath();
                    Entity newDefinition = newEntityDefinitions.remove(path);
                    if (newDefinition != null)
                    {
                        Entity entity = epf.getEntity();
                        String newClassifierPath = newDefinition.getClassifierPath();
                        Map<String, ?> newContent = newDefinition.getContent();
                        if (!newClassifierPath.equals(entity.getClassifierPath()) || !newContent.equals(entity.getContent()))
                        {
                            entityChanges.add(EntityChange.newModifyEntity(path, newClassifierPath, newContent));
                        }
                    }
                    else if (replace)
                    {
                        entityChanges.add(EntityChange.newDeleteEntity(path));
                    }
                });
            }
            newEntityDefinitions.forEachValue(definition -> entityChanges.add(EntityChange.newCreateEntity(definition.getPath(), definition.getClassifierPath(), definition.getContent())));
        }
        else if (replace)
        {
            try (Stream<EntityProjectFile> stream = EntityAccessOperations.getEntityProjectFiles(fileProvider.getFileAccessContext(projectId, sourceSpecification, revisionId)))
            {
                stream.map(EntityProjectFile::getEntityPath).map(EntityChange::newDeleteEntity).forEach(entityChanges::add);
            }
        }

        return performChanges(fileProvider, projectId, sourceSpecification, revisionId, message, entityChanges);
    }

    /**
     * Perform a list of (pre-validated) entity changes against a workspace, using {@code referenceRevisionId} (null
     * for the current revision) as the reference for both reading current state and submitting. Returns the revision
     * created, or null if the changes require no file operations. Callers should validate the changes first with
     * {@link #validateEntityChanges}.
     */
    public static Revision performChanges(ProjectFileAccessProvider fileProvider, String projectId, WorkspaceSourceSpecification sourceSpecification, String referenceRevisionId, String message, List<? extends EntityChange> changes)
    {
        int changeCount = changes.size();
        if (changeCount == 0)
        {
            LOGGER.debug("No changes for {} in project {}", sourceSpecification, projectId);
            return null;
        }
        LOGGER.debug("Committing {} changes to {} in project {}: {}", changeCount, sourceSpecification, projectId, message);
        ProjectFileAccessProvider.FileAccessContext fileAccessContext = fileProvider.getFileAccessContext(projectId, sourceSpecification, referenceRevisionId);
        ProjectStructure projectStructure = ProjectStructure.getProjectStructure(fileAccessContext);
        MutableList<ProjectFileOperation> fileOperations = ListIterate.collect(changes, c -> entityChangeToFileOperation(c, projectStructure, fileAccessContext));
        fileOperations.removeIf(Objects::isNull);
        if (fileOperations.isEmpty())
        {
            LOGGER.debug("No changes for {} in project {}", sourceSpecification, projectId);
            return null;
        }
        return fileProvider.getFileModificationContext(projectId, sourceSpecification, referenceRevisionId).submit(message, fileOperations);
    }

    public static void validateEntityChanges(List<? extends EntityChange> entityChanges)
    {
        StringBuilder builder = new StringBuilder();
        List<String> errorMessages = Lists.mutable.ofInitialCapacity(4);
        int i = 0;
        for (EntityChange change : entityChanges)
        {
            collectErrorsForEntityChange(change, errorMessages);
            if (!errorMessages.isEmpty())
            {
                if (builder.length() == 0)
                {
                    builder.append("There are entity change errors:");
                }
                builder.append("\n\tEntity change #").append(i + 1).append(" (").append(change).append("):");
                errorMessages.forEach(m -> builder.append("\n\t\t").append(m));
                errorMessages.clear();
            }
            i++;
        }
        if (builder.length() > 0)
        {
            throw new LegendSDLCException(builder.toString(), 400);
        }
    }

    private static MutableMap<String, Entity> indexAndValidateEntitiesForUpdate(Iterable<? extends Entity> newEntities)
    {
        MutableMap<String, Entity> newEntityDefinitions = Maps.mutable.empty();
        MutableList<String> errorMessages = Lists.mutable.empty();
        newEntities.forEach(entity ->
        {
            if (entity == null)
            {
                errorMessages.add("Invalid entity: null");
                return;
            }

            String path = entity.getPath();
            if (!EntityPaths.isValidEntityPath(path))
            {
                errorMessages.add("Invalid entity path: \"" + path + "\"");
                return;
            }

            String classifierPath = entity.getClassifierPath();
            if (!EntityPaths.isValidClassifierPath(classifierPath))
            {
                errorMessages.add("Entity: " + path + "; error: invalid classifier path \"" + classifierPath + "\"");
            }

            Map<String, ?> content = entity.getContent();
            if (content == null)
            {
                errorMessages.add("Entity: " + path + "; error: missing content");
            }
            else
            {
                Object pkg = content.get("package");
                Object name = content.get("name");
                if (!(pkg instanceof String) || !(name instanceof String) || !path.equals(pkg + EntityPaths.PACKAGE_SEPARATOR + name))
                {
                    StringBuilder builder = new StringBuilder("Entity: ").append(path).append("; mismatch between entity path and package (");
                    if (pkg instanceof String)
                    {
                        builder.append('"').append(pkg).append('"');
                    }
                    else
                    {
                        builder.append(pkg);
                    }
                    builder.append(") and name (");
                    if (name instanceof String)
                    {
                        builder.append('"').append(name).append('"');
                    }
                    else
                    {
                        builder.append(name);
                    }
                    errorMessages.add(builder.append(") properties").toString());
                }
            }

            Entity oldDefinition = newEntityDefinitions.put(path, entity);
            if (oldDefinition != null)
            {
                errorMessages.add("Entity: " + path + "; error: multiple definitions");
            }
        });
        if (errorMessages.notEmpty())
        {
            throw new LegendSDLCException((errorMessages.size() == 1) ? errorMessages.get(0) : errorMessages.makeString("There are errors with entity definitions:\n\t", "\n\t", ""), 400);
        }
        return newEntityDefinitions;
    }

    private static void collectErrorsForEntityChange(EntityChange entityChange, Collection<? super String> errorMessages)
    {
        if (entityChange == null)
        {
            errorMessages.add("Invalid entity change: null");
            return;
        }

        EntityChangeType type = entityChange.getType();
        if (type == null)
        {
            errorMessages.add("Missing entity change type");
        }

        String path = entityChange.getEntityPath();
        String classifierPath = entityChange.getClassifierPath();
        Map<String, ?> content = entityChange.getContent();
        String newPath = entityChange.getNewEntityPath();

        if (path == null)
        {
            errorMessages.add("Missing entity path");
        }
        else if (!EntityPaths.isValidEntityPath(path))
        {
            errorMessages.add("Invalid entity path: " + path);
        }
        else if (content != null)
        {
            Object pkg = content.get("package");
            Object name = content.get("name");
            if (!(pkg instanceof String) || !(name instanceof String) || !path.equals(pkg + EntityPaths.PACKAGE_SEPARATOR + name))
            {
                StringBuilder builder = new StringBuilder("Mismatch between entity path (\"").append(path).append("\") and package (");
                if (pkg instanceof String)
                {
                    builder.append('"').append(pkg).append('"');
                }
                else
                {
                    builder.append(pkg);
                }
                builder.append(") and name (");
                if (name instanceof String)
                {
                    builder.append('"').append(name).append('"');
                }
                else
                {
                    builder.append(name);
                }
                builder.append(") properties");
                errorMessages.add(builder.toString());
            }
        }
        if (type != null)
        {
            switch (type)
            {
                case CREATE:
                case MODIFY:
                {
                    if (classifierPath == null)
                    {
                        errorMessages.add("Missing classifier path");
                    }
                    else if (!EntityPaths.isValidClassifierPath(classifierPath))
                    {
                        errorMessages.add("Invalid classifier path: " + classifierPath);
                    }
                    if (content == null)
                    {
                        errorMessages.add("Missing content");
                    }
                    if (newPath != null)
                    {
                        errorMessages.add("Unexpected new entity path: " + newPath);
                    }
                    break;
                }
                case RENAME:
                {
                    if (classifierPath != null)
                    {
                        errorMessages.add("Unexpected classifier path: " + classifierPath);
                    }
                    if (content != null)
                    {
                        errorMessages.add("Unexpected content");
                    }
                    if (newPath == null)
                    {
                        errorMessages.add("Missing new entity path");
                    }
                    else if (!EntityPaths.isValidEntityPath(newPath))
                    {
                        errorMessages.add("Invalid new entity path: " + newPath);
                    }
                    break;
                }
                case DELETE:
                {
                    if (classifierPath != null)
                    {
                        errorMessages.add("Unexpected classifier path: " + classifierPath);
                    }
                    if (content != null)
                    {
                        errorMessages.add("Unexpected content");
                    }
                    if (newPath != null)
                    {
                        errorMessages.add("Unexpected new entity path: " + newPath);
                    }
                    break;
                }
                default:
                {
                    errorMessages.add("Unexpected entity change type: " + type);
                }
            }
        }
    }

    private static ProjectFileOperation entityChangeToFileOperation(EntityChange change, ProjectStructure projectStructure, ProjectFileAccessProvider.FileAccessContext fileAccessContext)
    {
        switch (change.getType())
        {
            case CREATE:
            {
                String entityPath = change.getEntityPath();

                // check if a file already exists for this entity
                if (projectStructure.findEntityFile(entityPath, fileAccessContext) != null)
                {
                    throw new LegendSDLCException("Unable to handle operation " + change + ": entity \"" + entityPath + "\" already exists");
                }

                Entity entity = Entity.newEntity(entityPath, change.getClassifierPath(), change.getContent());
                EntitySourceDirectory sourceDirectory = projectStructure.findSourceDirectoryForEntity(entity);
                if (sourceDirectory == null)
                {
                    throw new LegendSDLCException("Unable to handle operation " + change + ": cannot serialize entity \"" + entityPath + "\"");
                }
                return ProjectFileOperation.addFile(sourceDirectory.entityPathToFilePath(change.getEntityPath()), sourceDirectory.serializeToBytes(entity));
            }
            case DELETE:
            {
                String entityPath = change.getEntityPath();
                String filePath = projectStructure.findEntityFile(entityPath, fileAccessContext);
                if (filePath == null)
                {
                    throw new LegendSDLCException("Unable to handle operation " + change + ": could not find entity \"" + entityPath + "\"");
                }
                return ProjectFileOperation.deleteFile(filePath);
            }
            case MODIFY:
            {
                String entityPath = change.getEntityPath();
                Entity entity = Entity.newEntity(entityPath, change.getClassifierPath(), change.getContent());

                // find current file
                String currentFilePath = projectStructure.findEntityFile(entityPath, fileAccessContext);
                if (currentFilePath == null)
                {
                    throw new LegendSDLCException("Unable to handle operation " + change + ": could not find entity \"" + entityPath + "\"");
                }

                EntitySourceDirectory newSourceDirectory = projectStructure.findSourceDirectoryForEntity(entity);
                if (newSourceDirectory == null)
                {
                    throw new LegendSDLCException("Unable to handle operation " + change + ": cannot serialize entity \"" + entityPath + "\"");
                }

                String newFilePath = newSourceDirectory.entityPathToFilePath(entityPath);
                byte[] serialized = newSourceDirectory.serializeToBytes(entity);

                if (!currentFilePath.equals(newFilePath))
                {
                    return ProjectFileOperation.moveFile(currentFilePath, newFilePath, serialized);
                }
                if (!Arrays.equals(serialized, fileAccessContext.getFile(currentFilePath).getContentAsBytes()))
                {
                    return ProjectFileOperation.modifyFile(currentFilePath, serialized);
                }

                return null;
            }
            case RENAME:
            {
                String currentEntityPath = change.getEntityPath();
                for (EntitySourceDirectory sourceDirectory : projectStructure.getEntitySourceDirectories())
                {
                    String filePath = sourceDirectory.entityPathToFilePath(currentEntityPath);
                    if (fileAccessContext.fileExists(filePath))
                    {
                        String newFilePath = sourceDirectory.entityPathToFilePath(change.getNewEntityPath());
                        return ProjectFileOperation.moveFile(filePath, newFilePath);
                    }
                }
                throw new LegendSDLCException("Unable to handle operation " + change + ": could not find entity \"" + currentEntityPath + "\"");
            }
            default:
            {
                throw new RuntimeException("Unknown entity change type: " + change.getType());
            }
        }
    }
}
