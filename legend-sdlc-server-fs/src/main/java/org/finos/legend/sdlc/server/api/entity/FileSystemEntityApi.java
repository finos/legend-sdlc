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
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.entity.change.EntityChange;
import org.finos.legend.sdlc.domain.model.entity.change.EntityChangeType;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.server.api.BaseFSApi;
import org.finos.legend.sdlc.server.api.project.FileSystemProjectApi;
import org.finos.legend.sdlc.server.api.workspace.FileSystemWorkspaceApi;
import org.finos.legend.sdlc.server.domain.api.entity.EntityAccessContext;
import org.finos.legend.sdlc.server.domain.api.entity.EntityApi;
import org.finos.legend.sdlc.server.domain.api.entity.EntityModificationContext;
import org.finos.legend.sdlc.server.domain.api.project.source.*;
import org.finos.legend.sdlc.server.domain.model.revision.FileSystemRevision;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.project.*;
import org.finos.legend.sdlc.server.tools.StringTools;
import org.finos.legend.sdlc.tools.entity.EntityPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileSystemEntityApi extends BaseFSApi implements EntityApi
{
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemEntityApi.class);
    private static final Lock gitLock = new ReentrantLock();

    @Inject
    public FileSystemEntityApi()
    {
    }

    @Override
    public EntityAccessContext getEntityAccessContext(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
        String branchName = getRefBranchName(sourceSpecification);
        Repository repo = FileSystemWorkspaceApi.retrieveRepo(projectId);
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

    public static String getRefBranchName(SourceSpecification sourceSpecification)
    {
        if (sourceSpecification.getClass() == ProjectSourceSpecification.class)
        {
            return "master";
        }
        else
        {
            return sourceSpecification.visit(new SourceSpecificationVisitor<String>()
            {
                public String visit(WorkspaceSourceSpecification workspaceSourceSpecification)
                {
                    return FileSystemWorkspaceApi.getWorkspaceBranchName(workspaceSourceSpecification.getWorkspaceSpecification());
                }
            });
        }
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
                ProjectFileAccessProvider.FileAccessContext fileAccessContext = getFileAccessContext(FileSystemProjectApi.getProjectFileAccessProvider());
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
            try (Stream<EntityProjectFile> stream = getEntityProjectFiles(getFileAccessContext(FileSystemProjectApi.getProjectFileAccessProvider()), entityPathPredicate, classifierPathPredicate, entityContentPredicate, excludeInvalid, branchName, repo))
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
            try (Stream<EntityProjectFile> stream = getEntityProjectFiles(getFileAccessContext(FileSystemProjectApi.getProjectFileAccessProvider()), entityPathPredicate, classifierPathPredicate, entityContentPredicate))
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

        ProjectFileAccessProvider fileProvider = FileSystemProjectApi.getProjectFileAccessProvider();
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

    public Stream<EntityProjectFile> getEntityProjectFiles(ProjectFileAccessProvider.FileAccessContext accessContext, Predicate<String> entityPathPredicate, Predicate<String> classifierPathPredicate, Predicate<? super Map<String,?>> contentPredicate, boolean excludeInvalid, String branchName, Repository repo)
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

    public static Stream<EntityProjectFile> getEntityProjectFiles(String branchName, Repository repo, ProjectFileAccessProvider.FileAccessContext fileAccessContext)
    {
        ProjectStructure projectStructure = ProjectStructure.getProjectStructure(fileAccessContext);
        List<ProjectStructure.EntitySourceDirectory> sourceDirectories = projectStructure.getEntitySourceDirectories();
        return sourceDirectories.stream().flatMap(sd -> getSourceDirectoryProjectFiles(sd, branchName, repo, projectStructure.getProjectConfiguration().getProjectId()));
    }

    private static Stream<EntityProjectFile> getSourceDirectoryProjectFiles(ProjectStructure.EntitySourceDirectory sourceDirectory, String branchName, Repository repo, String projectID)
    {
        List<EntityProjectFile> files = new ArrayList<>();
        try
        {
            gitLock.lock();
            Git git = new Git(repo);
            git.checkout().setName(branchName).call();
            ObjectId headCommitId = repo.findRef(branchName).getObjectId();
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
                if (isPossiblyEntityFilePath(sourceDirectory, file.getCanonicalPath()))
                {
                    files.add(new EntityProjectFile(sourceDirectory, projectFile, projectID, rootDirectory));
                }
            }

        }
        catch (Exception e)
        {
            LOGGER.error("Error occurred while parsing Git commit for workspace {}", branchName, e);
        }
        finally
        {
            gitLock.unlock();
        }
        return files.stream();
    }

    private static boolean isPossiblyEntityFilePath(ProjectStructure.EntitySourceDirectory sourceDirectory, String filePath)
    {
        return (filePath != null) && (filePath.length() > (sourceDirectory.getDirectory().length() + sourceDirectory.getSerializer().getDefaultFileExtension().length() + 2)) && filePathHasEntityExtension(sourceDirectory, filePath) && !filePath.endsWith("project.json");
    }

    private static boolean filePathHasEntityExtension(ProjectStructure.EntitySourceDirectory sourceDirectory, String filePath)
    {
        String extension = sourceDirectory.getSerializer().getDefaultFileExtension();
        return filePath.endsWith(extension) && (filePath.length() > extension.length()) && (filePath.charAt(filePath.length() - (extension.length() + 1)) == '.');
    }

    public Stream<EntityProjectFile> getEntityProjectFiles(ProjectFileAccessProvider.FileAccessContext accessContext)
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
                .map(f -> new EntityProjectFile(sourceDirectory, f, projectID, rootDirectory));
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
        ProjectFileAccessProvider.FileAccessContext fileAccessContext = FileSystemProjectApi.getProjectFileAccessProvider().getFileAccessContext(projectId, sourceSpecification, referenceRevisionId);
        ProjectStructure projectStructure = ProjectStructure.getProjectStructure(fileAccessContext);
        MutableList<ProjectFileOperation> fileOperations = ListIterate.collect(changes, c -> entityChangeToFileOperation(c, projectStructure, fileAccessContext));
        fileOperations.removeIf(Objects::isNull);
        if (fileOperations.isEmpty())
        {
            LOGGER.debug("No changes for {} in project {}", sourceSpecification, projectId);
            return null;
        }
        return FileSystemProjectApi.getProjectFileAccessProvider().getFileModificationContext(projectId, sourceSpecification, referenceRevisionId).submit(message, fileOperations);
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

    public static class FileSystemRevisionAccessContext implements ProjectFileAccessProvider.RevisionAccessContext
    {
        private final String projectId;
        private final SourceSpecification sourceSpecification;
        private final MutableList<String> paths;

        public FileSystemRevisionAccessContext(String projectId, SourceSpecification sourceSpecification, Iterable<? extends String> canonicalPaths)
        {
            this.projectId = projectId;
            this.sourceSpecification = Objects.requireNonNull(sourceSpecification, "source specification may not be null");
            this.paths = (canonicalPaths == null) ? null : ProjectPaths.canonicalizeAndReduceDirectories(canonicalPaths);
        }

        @Override
        public Revision getCurrentRevision()
        {
            String branchName = getRefBranchName(sourceSpecification);
            try
            {
                gitLock.lock();
                Repository repo = FileSystemWorkspaceApi.retrieveRepo(this.projectId);
                Git git = new Git(repo);
                git.checkout().setName(branchName).call();
                ObjectId commitId = repo.resolve("HEAD");
                RevWalk revWalk = new RevWalk(repo);
                RevCommit commit = revWalk.parseCommit(commitId);
                revWalk.dispose();
                return getRevisionInfo(commit);
            }
            catch (Exception e)
            {
                throw new LegendSDLCServerException("Failed to get current revision for branch " + branchName + " in project " + this.projectId);
            }
            finally
            {
                gitLock.unlock();
            }
        }

        @Override
        public Revision getBaseRevision()
        {
            String branchName = getRefBranchName(sourceSpecification);
            try
            {
                gitLock.lock();
                Repository repo = FileSystemWorkspaceApi.retrieveRepo(this.projectId);
                Git git = new Git(repo);
                git.checkout().setName(branchName).call();
                String remoteName = repo.getConfig().getString("branch", branchName, "remote");
                String remoteBranch = repo.getConfig().getString("branch", branchName, "merge");
                ObjectId commitId = (remoteName != null && remoteBranch != null) ? repo.resolve("BASE.." + remoteName + "/" + remoteBranch) : repo.resolve("HEAD");
                RevWalk revWalk = new RevWalk(repo);
                RevCommit commit = revWalk.parseCommit(commitId);
                revWalk.dispose();
                return getRevisionInfo(commit);
            }
            catch (Exception e)
            {
                throw new LegendSDLCServerException("Failed to get base revision for branch " + branchName + " in project " + this.projectId);
            }
            finally
            {
                gitLock.unlock();
            }
        }

        @Override
        public Revision getRevision(String revisionId)
        {
            LegendSDLCServerException.validateNonNull(revisionId, "revisionId may not be null");
            String resolvedRevisionId = resolveRevisionId(revisionId, this);
            if (resolvedRevisionId == null)
            {
                throw new LegendSDLCServerException("Failed to resolve revision " + revisionId + " of project " + this.projectId, Response.Status.NOT_FOUND);
            }
            String branchName = getRefBranchName(sourceSpecification);
            try
            {
                gitLock.lock();
                Repository repo = FileSystemWorkspaceApi.retrieveRepo(this.projectId);
                Git git = new Git(repo);
                git.checkout().setName(branchName).call();
                ObjectId commitId = ObjectId.fromString(resolvedRevisionId);
                RevWalk revWalk = new RevWalk(repo);
                RevCommit commit = revWalk.parseCommit(commitId);
                revWalk.dispose();
                return getRevisionInfo(commit);
            }
            catch (Exception e)
            {
                throw new LegendSDLCServerException("Failed to get " + resolvedRevisionId + " revision for branch " + branchName + " in project " + this.projectId);
            }
            finally
            {
                gitLock.unlock();
            }
        }

        @Override
        public Stream<Revision> getAllRevisions(Predicate<? super Revision> predicate, Instant since, Instant until, Integer limit)
        {
            return null;
        }

        public Revision getRevisionInfo(RevCommit commit)
        {
            String revisionID = commit.getId().getName();
            String authorName = commit.getAuthorIdent().getName();
            Instant authoredTimeStamp = commit.getAuthorIdent().getWhenAsInstant();
            String committerName = commit.getCommitterIdent().getName();
            Instant committedTimeStamp = commit.getCommitterIdent().getWhenAsInstant();
            String message = commit.getFullMessage();
            return new FileSystemRevision(revisionID, authorName, authoredTimeStamp, committerName, committedTimeStamp, message);
        }
    }

    public static class FileSystemProjectFileFileModificationContext implements ProjectFileAccessProvider.FileModificationContext
    {
        private final String projectId;
        private final String revisionId;
        private final SourceSpecification sourceSpecification;

        public FileSystemProjectFileFileModificationContext(String projectId, SourceSpecification sourceSpecification, String revisionId)
        {
            this.projectId = projectId;
            this.revisionId = revisionId;
            this.sourceSpecification = Objects.requireNonNull(sourceSpecification, "source specification may not be null");
        }

        @Override
        public Revision submit(String message, List<? extends ProjectFileOperation> operations)
        {
            String branchName = getRefBranchName(sourceSpecification);
            try
            {
                int changeCount = operations.size();
                List<LocalCommitAction> commitActions = operations.stream().map(this::fileOperationToCommitAction).collect(Collectors.toCollection(() -> Lists.mutable.ofInitialCapacity(changeCount)));
                String referenceRevisionId = this.revisionId;
                if (referenceRevisionId != null)
                {
                    String targetBranchRevision = FileSystemRevision.getFileSystemRevision(this.projectId, branchName).getId();
                    if (!referenceRevisionId.equals(targetBranchRevision))
                    {
                        String msg = "Expected " + sourceSpecification + " to be at revision " + referenceRevisionId + "; instead it was at revision " + targetBranchRevision;
                        LOGGER.info(msg);
                        throw new LegendSDLCServerException("Exp", Response.Status.CONFLICT);
                    }
                }
                Repository repo = FileSystemWorkspaceApi.retrieveRepo(this.projectId);
                Git git = new Git(repo);
                git.checkout().setName(branchName).call();
                for (LocalCommitAction commitAction : commitActions)
                {
                    switch (commitAction.getAction())
                    {
                        case CREATE:
                        {
                            File newFile = new File(repo.getDirectory().getParent(), commitAction.getFilePath());
                            newFile.getParentFile().mkdirs();
                            newFile.createNewFile();
                            Files.write(newFile.toPath(), commitAction.getContent());
                            git.add().addFilepattern(".").call();
                            break;
                        }
                        case UPDATE:
                        {
                            File file = new File(repo.getDirectory().getParent(), commitAction.getFilePath());
                            if (file.exists())
                            {
                                Files.write(file.toPath(), commitAction.getContent());
                            }
                            else
                            {
                                throw new LegendSDLCServerException("File " + file + " does not exist");
                            }
                            git.add().addFilepattern(".").call();
                            break;
                        }
                        case MOVE:
                        {
                            throw new LegendSDLCServerException("MOVE operation is not yet supported");
                        }
                        case DELETE:
                        {
                            File fileToRemove = new File(repo.getWorkTree(), commitAction.getFilePath().substring(1));
                            if (!fileToRemove.exists())
                            {
                                throw new LegendSDLCServerException("File " + fileToRemove + " does not exist");
                            }
                            fileToRemove.delete();
                            git.rm().addFilepattern(commitAction.getFilePath().substring(1)).call();
                            if (git.status().call().isClean())
                            {
                                break;
                            }
                            break;
                        }
                        default:
                        {
                            break;
                        }
                    }
                }
                RevCommit revCommit = git.commit().setMessage(message).call();
                repo.close();
                if (LOGGER.isDebugEnabled())
                {
                    LOGGER.debug("Committed {} changes to {}: {}", changeCount, sourceSpecification, revCommit.getId());
                }
                return FileSystemRevision.getFileSystemRevision(projectId, branchName);
            }
            catch (Exception e)
            {
                throw new LegendSDLCServerException("Error occurred while committing changes to workspace " + branchName + " of project " + projectId, e);
            }
        }

        private LocalCommitAction fileOperationToCommitAction(ProjectFileOperation fileOperation)
        {
            if (fileOperation instanceof ProjectFileOperation.AddFile)
            {
                return new LocalCommitAction()
                        .withAction(LocalCommitAction.Action.CREATE)
                        .withFilePath(fileOperation.getPath())
                        .withContent(((ProjectFileOperation.AddFile) fileOperation).getContent());
            }
            if (fileOperation instanceof ProjectFileOperation.ModifyFile)
            {
                return new LocalCommitAction()
                        .withAction(LocalCommitAction.Action.UPDATE)
                        .withFilePath(fileOperation.getPath())
                        .withContent(((ProjectFileOperation.ModifyFile) fileOperation).getNewContent());
            }
            if (fileOperation instanceof ProjectFileOperation.DeleteFile)
            {
                return new LocalCommitAction()
                        .withAction(LocalCommitAction.Action.DELETE)
                        .withFilePath(fileOperation.getPath());
            }
            if (fileOperation instanceof ProjectFileOperation.MoveFile)
            {
                throw new LegendSDLCServerException("MOVE operation is not yet supported");
            }
            throw new IllegalArgumentException("Unsupported project file operation: " + fileOperation);
        }
    }

    public static class AbstractFileSystemFileAccessContext extends AbstractFileAccessContext
    {
        protected final String projectId;
        private final SourceSpecification sourceSpecification;
        private final String revisionId;

        public AbstractFileSystemFileAccessContext(String projectId, SourceSpecification sourceSpecification, String revisionId)
        {
            this.projectId = projectId;
            this.sourceSpecification = sourceSpecification;
            this.revisionId = revisionId;
        }

        @Override
        protected Stream<ProjectFileAccessProvider.ProjectFile> getFilesInCanonicalDirectories(MutableList<String> directories)
        {
            Repository repo = FileSystemWorkspaceApi.retrieveRepo(this.projectId);
            Git git = new Git(repo);
            String branchName = getRefBranchName(sourceSpecification);
            try
            {
                git.checkout().setName(branchName).call();

                ObjectId commitId = ObjectId.fromString(revisionId);
                RevCommit commit = repo.parseCommit(commitId);
                RevTree tree = commit.getTree();
                List<String> files = new ArrayList<>();
                TreeWalk treeWalk = new TreeWalk(repo);
                treeWalk.addTree(tree);
                treeWalk.setRecursive(true);

                for (String dir : directories)
                {
                    while (treeWalk.next())
                    {
                        String path = treeWalk.getPathString();
                        if (path.startsWith(dir))
                        {
                            files.add(path);
                        }
                    }
                }
                return files.stream().map(filePath -> getFile(filePath));
            }
            catch (Exception e)
            {
                throw new LegendSDLCServerException("Error getting files in directories for " + projectId, e);
            }
        }

        @Override
        public ProjectFileAccessProvider.ProjectFile getFile(String path)
        {
            String branchName = getRefBranchName(sourceSpecification);
            try
            {
                Repository repo = FileSystemWorkspaceApi.retrieveRepo(this.projectId);
                RevWalk revWalk = new RevWalk(repo);
                RevCommit branchCommit = revWalk.parseCommit(repo.resolve(branchName));
                RevTree branchTree = branchCommit.getTree();
                path = path.startsWith("/") ? path.substring(1) : path;
                try (TreeWalk treeWalk = TreeWalk.forPath(repo, path, branchTree))
                {
                    if (treeWalk != null)
                    {
                        ObjectId objectId = treeWalk.getObjectId(0);
                        ObjectReader objectReader = repo.newObjectReader();
                        byte[] fileBytes = objectReader.open(objectId).getBytes();
                        return ProjectFiles.newByteArrayProjectFile(path, fileBytes);
                    }
                }
            }
            catch (Exception e)
            {
                LOGGER.error("Error occurred while parsing Git commit for workspace {}", branchName, e);
            }
            throw new LegendSDLCServerException("Error getting file " + path);
        }

        @Override
        public boolean fileExists(String path)
        {
            String branchName = getRefBranchName(sourceSpecification);
            try
            {
                Repository repo = FileSystemWorkspaceApi.retrieveRepo(this.projectId);
                RevWalk revWalk = new RevWalk(repo);
                RevCommit branchCommit = revWalk.parseCommit(repo.resolve(branchName));
                RevTree branchTree = branchCommit.getTree();
                path = path.startsWith("/") ? path.substring(1) : path;
                try (TreeWalk treeWalk = TreeWalk.forPath(repo, path, branchTree))
                {
                    return treeWalk != null;
                }
            }
            catch (IOException e)
            {
                throw new LegendSDLCServerException("Error occurred while parsing Git commit for workspace " + branchName, e);
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
