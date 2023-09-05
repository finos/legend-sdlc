// Copyright 2023 Goldman Sachs
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

package org.finos.legend.sdlc.server.api.entity;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.impl.utility.ListIterate;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.entity.change.EntityChange;
import org.finos.legend.sdlc.domain.model.entity.change.EntityChangeType;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.server.domain.api.entity.EntityAccessContext;
import org.finos.legend.sdlc.server.domain.api.entity.EntityApi;
import org.finos.legend.sdlc.server.domain.api.entity.EntityModificationContext;
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification;
import org.finos.legend.sdlc.server.domain.api.project.source.WorkspaceSourceSpecification;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.exception.FSException;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.project.ProjectFileOperation;
import org.finos.legend.sdlc.server.project.ProjectFiles;
import org.finos.legend.sdlc.server.project.ProjectStructure;
import org.finos.legend.sdlc.server.project.CachingFileAccessContext;
import org.finos.legend.sdlc.server.startup.FSConfiguration;
import org.finos.legend.sdlc.server.tools.StringTools;
import org.finos.legend.sdlc.tools.entity.EntityPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileSystemEntityApi extends FileSystemApiWithFileAccess implements EntityApi
{
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemEntityApi.class);

    @Inject
    public FileSystemEntityApi(FSConfiguration fsConfiguration)
    {
        super(fsConfiguration);
    }

    @Override
    public EntityAccessContext getEntityAccessContext(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
        String branchName = getRefBranchName(sourceSpecification);
        Repository repo = retrieveRepo(projectId);
        return new FileSystemEntityAccessContext(branchName, repo)
        {
            @Override
            protected ProjectFileAccessProvider.FileAccessContext getFileAccessContext(ProjectFileAccessProvider projectFileAccessProvider)
            {
                return projectFileAccessProvider.getFileAccessContext(projectId, sourceSpecification, null);
            }
        };
    }

    @Override
    public EntityAccessContext getReviewFromEntityAccessContext(String projectId, String reviewId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public EntityAccessContext getReviewToEntityAccessContext(String projectId, String reviewId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public EntityModificationContext getEntityModificationContext(String projectId, WorkspaceSourceSpecification sourceSpecification)
    {
        return new FileSystemEntityModificationContext(projectId, sourceSpecification);
    }


    public abstract class FileSystemEntityAccessContext implements EntityAccessContext
    {
        private final String branchName;
        private final Repository repo;

        protected FileSystemEntityAccessContext(String branchName, Repository repo)
        {
            this.branchName = branchName;
            this.repo = repo;
        }

        @Override
        public Entity getEntity(String path)
        {
            ProjectFileAccessProvider.FileAccessContext fileAccessContext = getFileAccessContext(getProjectFileAccessProvider());
            ProjectStructure projectStructure = ProjectStructure.getProjectStructure(fileAccessContext);
            for (ProjectStructure.EntitySourceDirectory sourceDirectory : projectStructure.getEntitySourceDirectories())
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
                        throw new LegendSDLCServerException(builder.toString(), e);
                    }
                }
            }
            throw new LegendSDLCServerException("Unknown entity " + path, Response.Status.NOT_FOUND);
        }

        @Override
        public List<Entity> getEntities(Predicate<String> entityPathPredicate, Predicate<String> classifierPathPredicate, Predicate<? super Map<String, ?>> entityContentPredicate, boolean excludeInvalid)
        {
            try (Stream<EntityProjectFile> stream = getEntityProjectFiles(getFileAccessContext(getProjectFileAccessProvider()), entityPathPredicate, classifierPathPredicate, entityContentPredicate, excludeInvalid, branchName, repo))
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

        @Override
        public List<String> getEntityPaths(Predicate<String> entityPathPredicate, Predicate<String> classifierPathPredicate, Predicate<? super Map<String, ?>> entityContentPredicate)
        {
            try (Stream<EntityProjectFile> stream = getEntityProjectFiles(getFileAccessContext(getProjectFileAccessProvider()), entityPathPredicate, classifierPathPredicate, entityContentPredicate))
            {
                return stream.map(EntityProjectFile::getEntityPath).collect(Collectors.toList());
            }
        }

        protected abstract ProjectFileAccessProvider.FileAccessContext getFileAccessContext(ProjectFileAccessProvider projectFileAccessProvider);
    }

    public class FileSystemEntityModificationContext implements EntityModificationContext
    {
        private final String projectId;
        private final WorkspaceSourceSpecification sourceSpecification;

        private FileSystemEntityModificationContext(String projectId, WorkspaceSourceSpecification sourceSpecification)
        {
            this.projectId = projectId;
            this.sourceSpecification = Objects.requireNonNull(sourceSpecification, "source specification may not be null");
        }

        @Override
        public Revision updateEntities(Iterable<? extends Entity> entities, boolean replace, String message)
        {
            LegendSDLCServerException.validateNonNull(entities, "entities may not be null");
            LegendSDLCServerException.validateNonNull(message, "message may not be null");
            return FileSystemEntityApi.this.updateEntities(this.projectId, this.sourceSpecification, entities, replace, message);
        }

        @Override
        public Revision performChanges(List<? extends EntityChange> changes, String revisionId, String message)
        {
            LegendSDLCServerException.validateNonNull(changes, "changes may not be null");
            LegendSDLCServerException.validateNonNull(message, "message may not be null");
            validateEntityChanges(changes);
            return FileSystemEntityApi.this.performChanges(this.projectId, this.sourceSpecification, revisionId, message, changes);
        }
    }

    private static void validateEntityChanges(List<? extends EntityChange> entityChanges)
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
            throw new LegendSDLCServerException(builder.toString(), Response.Status.BAD_REQUEST);
        }
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

    public Revision updateEntities(String projectId, WorkspaceSourceSpecification sourceSpecification, Iterable<? extends Entity> newEntities, boolean replace, String message)
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
            else if (path != null)
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
                    builder.append(") properties");
                    errorMessages.add(builder.toString());
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
            throw new LegendSDLCServerException((errorMessages.size() == 1) ? errorMessages.get(0) : "There are errors with entity definitions:\n\t" + String.join("\n\t", errorMessages), Response.Status.BAD_REQUEST);
        }

        ProjectFileAccessProvider fileProvider = getProjectFileAccessProvider();
        Revision currentWorkspaceRevision = fileProvider.getRevisionAccessContext(projectId, sourceSpecification, null).getCurrentRevision();
        if (currentWorkspaceRevision == null)
        {
            throw new LegendSDLCServerException("Could not find current revision for " + sourceSpecification + ": it may be corrupt");
        }
        String revisionId = currentWorkspaceRevision.getId();
        LOGGER.debug("Using revision {} for reference in entity update in {} in project {}", revisionId, sourceSpecification, projectId);
        List<EntityChange> entityChanges = Lists.mutable.ofInitialCapacity(newEntityDefinitions.size());
        if (newEntityDefinitions.isEmpty())
        {
            if (replace)
            {
                try (Stream<EntityProjectFile> stream = getEntityProjectFiles(fileProvider.getFileAccessContext(projectId, sourceSpecification, revisionId)))
                {
                    stream.map(EntityProjectFile::getEntityPath).map(EntityChange::newDeleteEntity).forEach(entityChanges::add);
                }
            }
        }
        else
        {
            try (Stream<EntityProjectFile> stream = getEntityProjectFiles(fileProvider.getFileAccessContext(projectId, sourceSpecification, revisionId)))
            {
                stream.forEach(epf ->
                {
                    String path = epf.getEntityPath();
                    Entity newDefinition = newEntityDefinitions.remove(path);
                    if (newDefinition == null)
                    {
                        if (replace)
                        {
                            entityChanges.add(EntityChange.newDeleteEntity(path));
                        }
                    }
                    else
                    {
                        Entity entity = epf.getEntity();
                        String newClassifierPath = newDefinition.getClassifierPath();
                        Map<String, ?> newContent = newDefinition.getContent();
                        if (!newClassifierPath.equals(entity.getClassifierPath()) || !newContent.equals(entity.getContent()))
                        {
                            entityChanges.add(EntityChange.newModifyEntity(path, newClassifierPath, newContent));
                        }
                    }
                });
            }
            newEntityDefinitions.forEachValue(definition -> entityChanges.add(EntityChange.newCreateEntity(definition.getPath(), definition.getClassifierPath(), definition.getContent())));
        }

        return performChanges(projectId, sourceSpecification, revisionId, message, entityChanges);
    }

    private Stream<EntityProjectFile> getEntityProjectFiles(ProjectFileAccessProvider.FileAccessContext accessContext, Predicate<String> entityPathPredicate, Predicate<String> classifierPathPredicate, Predicate<? super Map<String, ?>> contentPredicate)
    {
        return getEntityProjectFiles(accessContext, entityPathPredicate, classifierPathPredicate, contentPredicate, false, null, null);
    }

    private Stream<EntityProjectFile> getEntityProjectFiles(ProjectFileAccessProvider.FileAccessContext accessContext, Predicate<String> entityPathPredicate, Predicate<String> classifierPathPredicate, Predicate<? super Map<String,?>> contentPredicate, boolean excludeInvalid, String branchName, Repository repo)
    {
        Stream<EntityProjectFile> stream = (repo != null && branchName != null) ? getEntityProjectFiles(branchName, repo, accessContext) : getEntityProjectFiles(accessContext);
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
                    entity =  epf.getEntity();
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
                    entity =  epf.getEntity();
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

    private Stream<EntityProjectFile> getEntityProjectFiles(String branchName, Repository repo, ProjectFileAccessProvider.FileAccessContext fileAccessContext)
    {
        ProjectStructure projectStructure = ProjectStructure.getProjectStructure(fileAccessContext);
        List<ProjectStructure.EntitySourceDirectory> sourceDirectories = projectStructure.getEntitySourceDirectories();
        return sourceDirectories.stream().flatMap(sd -> getSourceDirectoryProjectFiles(sd, branchName, repo, projectStructure.getProjectConfiguration().getProjectId()));
    }

    private Stream<EntityProjectFile> getSourceDirectoryProjectFiles(ProjectStructure.EntitySourceDirectory sourceDirectory, String workspaceId, Repository repo, String projectID)
    {
        List<EntityProjectFile> files = new ArrayList<>();
        try
        {
            ObjectId headCommitId = repo.findRef(workspaceId).getObjectId();
            TreeWalk treeWalk = new TreeWalk(repo);
            treeWalk.addTree(repo.parseCommit(headCommitId).getTree());
            treeWalk.setRecursive(true);
            while (treeWalk.next())
            {
                File file = new File(repo.getWorkTree(), treeWalk.getPathString());
                ObjectId entityId = treeWalk.getObjectId(0);
                ObjectLoader loader = repo.open(entityId);
                byte[] entityContentBytes = loader.getBytes();
                String entityContent = new String(entityContentBytes, StandardCharsets.UTF_8);
                ProjectFileAccessProvider.ProjectFile projectFile = ProjectFiles.newStringProjectFile(file.getCanonicalPath(), entityContent);
                if (sourceDirectory.isPossiblyEntityFilePath(getEntityPath(file, projectID)))
                {
                    files.add(new EntityProjectFile(sourceDirectory, projectFile, projectID, getRootDirectory()));
                }
            }
        }
        catch (Exception e)
        {
            LOGGER.error("Error occurred while parsing Git commit for workspace {}", workspaceId, e);
            throw FSException.getLegendSDLCServerException("Failed to get project files for workspace " + workspaceId + " of project " + projectID, e);
        }
        return files.stream();
    }

    private String getEntityPath(File file, String projectID)
    {
        Path filePath = Paths.get(file.getPath());
        Path pathToProject = Paths.get(getRootDirectory() + "/" + projectID);
        if (!filePath.startsWith(pathToProject))
        {
            throw new LegendSDLCServerException("Paths " + filePath + " and " + pathToProject + " are not related");
        }
        Path relativePath = filePath.subpath(pathToProject.getNameCount(), filePath.getNameCount());
        return "/" + relativePath;
    }

    private Stream<EntityProjectFile> getEntityProjectFiles(ProjectFileAccessProvider.FileAccessContext accessContext)
    {
        ProjectStructure projectStructure = ProjectStructure.getProjectStructure(accessContext);
        List<ProjectStructure.EntitySourceDirectory> sourceDirectories = projectStructure.getEntitySourceDirectories();
        ProjectFileAccessProvider.FileAccessContext cachingAccessContext = (sourceDirectories.size() > 1) ? CachingFileAccessContext.wrap(accessContext) : accessContext;
        return sourceDirectories.stream().flatMap(sd -> getSourceDirectoryProjectFiles(cachingAccessContext, sd, projectStructure.getProjectConfiguration().getProjectId()));
    }

    private Stream<EntityProjectFile> getSourceDirectoryProjectFiles(ProjectFileAccessProvider.FileAccessContext accessContext, ProjectStructure.EntitySourceDirectory sourceDirectory, String projectID)
    {
        return accessContext.getFilesInDirectory(sourceDirectory.getDirectory())
                .filter(f -> sourceDirectory.isPossiblyEntityFilePath(f.getPath()))
                .map(f -> new EntityProjectFile(sourceDirectory, f, projectID, getRootDirectory()));
    }

    private Revision performChanges(String projectId, WorkspaceSourceSpecification sourceSpecification, String referenceRevisionId, String message, List<? extends EntityChange> changes)
    {
        int changeCount = changes.size();
        if (changeCount == 0)
        {
            LOGGER.debug("No changes for {} in project {}", sourceSpecification, projectId);
            return null;
        }
        LOGGER.debug("Committing {} changes to {} in project {}: {}", changeCount, sourceSpecification, projectId, message);
        ProjectFileAccessProvider.FileAccessContext fileAccessContext = getProjectFileAccessProvider().getFileAccessContext(projectId, sourceSpecification, referenceRevisionId);
        ProjectStructure projectStructure = ProjectStructure.getProjectStructure(fileAccessContext);
        MutableList<ProjectFileOperation> fileOperations = ListIterate.collect(changes, c -> entityChangeToFileOperation(c, projectStructure, fileAccessContext));
        fileOperations.removeIf(Objects::isNull);
        if (fileOperations.isEmpty())
        {
            LOGGER.debug("No changes for {} in project {}", sourceSpecification, projectId);
            return null;
        }
        return getProjectFileAccessProvider().getFileModificationContext(projectId, sourceSpecification, referenceRevisionId).submit(message, fileOperations);
    }

    private ProjectFileOperation entityChangeToFileOperation(EntityChange change, ProjectStructure projectStructure, ProjectFileAccessProvider.FileAccessContext fileAccessContext)
    {
        switch (change.getType())
        {
            case CREATE:
            {
                String entityPath = change.getEntityPath();

                // check if a file already exists for this entity
                if (projectStructure.findEntityFile(entityPath, fileAccessContext) != null)
                {
                    throw new LegendSDLCServerException("Unable to handle operation " + change + ": entity \"" + entityPath + "\" already exists");
                }

                Entity entity = Entity.newEntity(entityPath, change.getClassifierPath(), change.getContent());
                ProjectStructure.EntitySourceDirectory sourceDirectory = projectStructure.findSourceDirectoryForEntity(entity);
                if (sourceDirectory == null)
                {
                    throw new LegendSDLCServerException("Unable to handle operation " + change + ": cannot serialize entity \"" + entityPath + "\"");
                }
                return ProjectFileOperation.addFile(sourceDirectory.entityPathToFilePath(change.getEntityPath()), sourceDirectory.serializeToBytes(entity));
            }
            case DELETE:
            {
                String entityPath = change.getEntityPath();
                String filePath = projectStructure.findEntityFile(entityPath, fileAccessContext);
                if (filePath == null)
                {
                    throw new LegendSDLCServerException("Unable to handle operation " + change + ": could not find entity \"" + entityPath + "\"");
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
                    throw new LegendSDLCServerException("Unable to handle operation " + change + ": could not find entity \"" + entityPath + "\"");
                }

                ProjectStructure.EntitySourceDirectory newSourceDirectory = projectStructure.findSourceDirectoryForEntity(entity);
                if (newSourceDirectory == null)
                {
                    throw new LegendSDLCServerException("Unable to handle operation " + change + ": cannot serialize entity \"" + entityPath + "\"");
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
                for (ProjectStructure.EntitySourceDirectory sourceDirectory : projectStructure.getEntitySourceDirectories())
                {
                    String filePath = sourceDirectory.entityPathToFilePath(currentEntityPath);
                    if (fileAccessContext.fileExists(filePath))
                    {
                        String newFilePath = sourceDirectory.entityPathToFilePath(change.getNewEntityPath());
                        return ProjectFileOperation.moveFile(filePath, newFilePath);
                    }
                }
                throw new LegendSDLCServerException("Unable to handle operation " + change + ": could not find entity \"" + currentEntityPath + "\"");
            }
            default:
            {
                throw new RuntimeException("Unknown entity change type: " + change.getType());
            }
        }
    }

    static class EntityProjectFile
    {
        private final ProjectStructure.EntitySourceDirectory sourceDirectory;
        private final ProjectFileAccessProvider.ProjectFile file;
        private String path;
        private Entity entity;
        private String projectID;
        private String rootDirectory;

        private EntityProjectFile(ProjectStructure.EntitySourceDirectory sourceDirectory, ProjectFileAccessProvider.ProjectFile file, String projectID, String rootDirectory)
        {
            this.sourceDirectory = sourceDirectory;
            this.file = file;
            this.projectID = projectID;
            this.rootDirectory = rootDirectory;
        }

        synchronized String getEntityPath()
        {
            if (this.path == null)
            {
                Path filePath = Paths.get(this.file.getPath());
                Path pathToProject = Paths.get(rootDirectory + "/" + this.projectID);
                if (!filePath.startsWith(pathToProject))
                {
                    throw new LegendSDLCServerException("Paths " + filePath + " and " + pathToProject + " are not related");
                }
                Path relativePath = filePath.subpath(pathToProject.getNameCount(), filePath.getNameCount());
                this.path = this.sourceDirectory.filePathToEntityPath("/" + relativePath);
            }
            return this.path;
        }

        synchronized Entity getEntity()
        {
            if (this.entity == null)
            {
                Entity localEntity = this.sourceDirectory.deserialize(this.file);
                if (!Objects.equals(localEntity.getPath(), getEntityPath()))
                {
                    throw new RuntimeException("Expected entity path " + getEntityPath() + ", found " + localEntity.getPath());
                }
                this.entity = localEntity;
            }
            return this.entity;
        }
    }

}
