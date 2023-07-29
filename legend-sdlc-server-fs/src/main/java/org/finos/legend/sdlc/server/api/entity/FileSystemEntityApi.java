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
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.server.api.workspace.FileSystemWorkspaceApi;
import org.finos.legend.sdlc.server.domain.api.entity.EntityAccessContext;
import org.finos.legend.sdlc.server.domain.api.entity.EntityApi;
import org.finos.legend.sdlc.server.domain.api.entity.EntityModificationContext;
import org.finos.legend.sdlc.server.domain.api.project.source.ProjectSourceSpecification;
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification;
import org.finos.legend.sdlc.server.domain.api.project.source.WorkspaceSourceSpecification;
import org.finos.legend.sdlc.server.domain.model.revision.FileSystemRevision;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.project.*;
import org.finos.legend.sdlc.server.tools.StringTools;
import org.finos.legend.sdlc.tools.entity.EntityPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileSystemEntityApi implements EntityApi
{
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemEntityApi.class);

    @Inject
    public FileSystemEntityApi()
    {
    }

    @Override
    public EntityAccessContext getEntityAccessContext(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
        try
        {
            String branchName = (sourceSpecification.getClass() == ProjectSourceSpecification.class) ? "master" : sourceSpecification.getWorkspaceId();
            Repository repo = FileSystemWorkspaceApi.retrieveRepo(projectId);
            return new FileSystemEntityAccessContext(branchName, repo)
            {
                @Override
                protected ProjectFileAccessProvider.FileAccessContext getFileAccessContext(ProjectFileAccessProvider projectFileAccessProvider)
                {
                    return projectFileAccessProvider.getFileAccessContext(projectId, sourceSpecification, null);
                }

                @Override
                protected String getInfoForException()
                {
                    return sourceSpecification.getWorkspaceType().getLabel() + " " + sourceSpecification.getWorkspaceAccessType().getLabel() + " " + sourceSpecification.getWorkspaceId() + " of project " + projectId;
                }
            };
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public EntityAccessContext getReviewFromEntityAccessContext(String projectId, String reviewId)
    {
        return null;
    }

    @Override
    public EntityAccessContext getReviewToEntityAccessContext(String projectId, String reviewId)
    {
        return null;
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
            try
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
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            throw new LegendSDLCServerException("Unknown entity " + path + " for " + getInfoForException(), Response.Status.NOT_FOUND);
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
            catch (Exception e)
            {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public List<String> getEntityPaths(Predicate<String> entityPathPredicate, Predicate<String> classifierPathPredicate, Predicate<? super Map<String, ?>> entityContentPredicate)
        {
            try (Stream<EntityProjectFile> stream = getEntityProjectFiles(getFileAccessContext(getProjectFileAccessProvider()), entityPathPredicate, classifierPathPredicate, entityContentPredicate))
            {
                return stream.map(EntityProjectFile::getEntityPath).collect(Collectors.toList());
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            return null;
        }

        protected abstract ProjectFileAccessProvider.FileAccessContext getFileAccessContext(ProjectFileAccessProvider projectFileAccessProvider);

        protected abstract String getInfoForException();
    }

    public class FileSystemEntityModificationContext implements EntityModificationContext
    {
        private final String projectId;
        private final SourceSpecification sourceSpecification;

        private FileSystemEntityModificationContext(String projectId, SourceSpecification sourceSpecification)
        {
            this.projectId = projectId;
            this.sourceSpecification = sourceSpecification;
            if (sourceSpecification == null)
            {
                throw new RuntimeException("source specification may not be null");
            }
            if ((this.sourceSpecification.getWorkspaceId() != null) && ((this.sourceSpecification.getWorkspaceType() == null) || (this.sourceSpecification.getWorkspaceAccessType() == null)))
            {
                throw new RuntimeException("workspace type and access type are required when workspace id is specified");
            }
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
            //validateEntityChanges(changes);
            return FileSystemEntityApi.this.performChanges(this.projectId, this.sourceSpecification, revisionId, message, changes);
        }
    }

    public Revision updateEntities(String projectId, SourceSpecification sourceSpecification, Iterable<? extends Entity> newEntities, boolean replace, String message)
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

        FileSystemRevision currentWorkspaceRevision = FileSystemRevision.getFileSystemRevision(projectId, sourceSpecification.getWorkspaceId());
        //Revision currentWorkspaceRevision = getProjectFileAccessProvider().getRevisionAccessContext(projectId, sourceSpecification, null).getCurrentRevision();
        if (currentWorkspaceRevision == null)
        {
            throw new LegendSDLCServerException("Could not find current revision for " + sourceSpecification.getWorkspaceType().getLabel() + " " + sourceSpecification.getWorkspaceAccessType().getLabel() + " " + sourceSpecification.getWorkspaceId() + " in project " + projectId + ": " + sourceSpecification.getWorkspaceType().getLabel() + " " + sourceSpecification.getWorkspaceAccessType().getLabel() + " may be corrupt");
        }
        String revisionId = currentWorkspaceRevision.getId();
        LOGGER.debug("Using revision {} for reference in entity update in {} {} {} in project {}", revisionId, sourceSpecification.getWorkspaceType().getLabel(), sourceSpecification.getWorkspaceAccessType().getLabel(), sourceSpecification.getWorkspaceId(), projectId);
        List<EntityChange> entityChanges = Lists.mutable.ofInitialCapacity(newEntityDefinitions.size());
        if (newEntityDefinitions.isEmpty())
        {
            if (replace)
            {
                try (Stream<EntityProjectFile> stream = getEntityProjectFiles(projectId, sourceSpecification, revisionId))
                {
                    stream.map(EntityProjectFile::getEntityPath).map(EntityChange::newDeleteEntity).forEach(entityChanges::add);
                }
            }
        }
        else
        {
            try (Stream<EntityProjectFile> stream = getEntityProjectFiles(projectId, sourceSpecification, revisionId))
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

    private Stream<EntityProjectFile> getEntityProjectFiles(String projectId, SourceSpecification sourceSpecification, String revisionId)
    {
        return getEntityProjectFiles(getProjectFileAccessProvider().getFileAccessContext(projectId, sourceSpecification, revisionId));
    }

    protected ProjectFileAccessProvider getProjectFileAccessProvider()
    {
        return new FileSystemProjectFileAccessProvider();
    }

    public Stream<EntityProjectFile> getEntityProjectFiles(ProjectFileAccessProvider.FileAccessContext accessContext, Predicate<String> entityPathPredicate, Predicate<String> classifierPathPredicate, Predicate<? super Map<String,?>> contentPredicate, boolean excludeInvalid, String branchName, Repository repo)
    {
        Stream<EntityProjectFile> stream = (repo != null && branchName != null) ? getEntityProjectFiles(branchName, repo) : getEntityProjectFiles(accessContext);
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

    public Stream<EntityProjectFile> getEntityProjectFiles(String branchName, Repository repo)
    {
        ProjectStructure projectStructure = ProjectStructure.getProjectStructure((ProjectConfiguration) null);
        List<ProjectStructure.EntitySourceDirectory> sourceDirectories = projectStructure.getEntitySourceDirectories();
        return sourceDirectories.stream().flatMap(sd -> getSourceDirectoryProjectFiles(sd, branchName, repo));
    }

    private Stream<EntityProjectFile> getSourceDirectoryProjectFiles(ProjectStructure.EntitySourceDirectory sourceDirectory, String branchName, Repository repo)
    {
        List<EntityProjectFile> files = new ArrayList<>();
        try
        {
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
                files.add(new EntityProjectFile(sourceDirectory, projectFile));
            }

        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return files.stream();
    }

    public Stream<EntityProjectFile> getEntityProjectFiles(ProjectFileAccessProvider.FileAccessContext accessContext)
    {
        ProjectStructure projectStructure = ProjectStructure.getProjectStructure(accessContext);
        List<ProjectStructure.EntitySourceDirectory> sourceDirectories = projectStructure.getEntitySourceDirectories();
        ProjectFileAccessProvider.FileAccessContext cachingAccessContext = (sourceDirectories.size() > 1) ? CachingFileAccessContext.wrap(accessContext) : accessContext;
        return sourceDirectories.stream().flatMap(sd -> getSourceDirectoryProjectFiles(cachingAccessContext, sd));
    }

    private Stream<EntityProjectFile> getSourceDirectoryProjectFiles(ProjectFileAccessProvider.FileAccessContext accessContext, ProjectStructure.EntitySourceDirectory sourceDirectory)
    {
        return accessContext.getFilesInDirectory(sourceDirectory.getDirectory())
                .filter(f -> sourceDirectory.isPossiblyEntityFilePath(f.getPath()))
                .map(f -> new EntityProjectFile(sourceDirectory, f));
    }

    private Revision performChanges(String projectId, SourceSpecification sourceSpecification, String referenceRevisionId, String message, List<? extends EntityChange> changes)
    {
        int changeCount = changes.size();
        if (changeCount == 0)
        {
            LOGGER.debug("No changes for {} {} {} in project {}", sourceSpecification.getWorkspaceType().getLabel(), sourceSpecification.getWorkspaceAccessType().getLabel(), sourceSpecification.getWorkspaceId(), projectId);
            return null;
        }
        LOGGER.debug("Committing {} changes to {} {} {} in project {}: {}", changeCount, sourceSpecification.getWorkspaceType().getLabel(), sourceSpecification.getWorkspaceAccessType().getLabel(), sourceSpecification.getWorkspaceId(), projectId, message);
        try
        {
            ProjectFileAccessProvider.FileAccessContext fileAccessContext = getProjectFileAccessProvider().getFileAccessContext(projectId, sourceSpecification, referenceRevisionId);
            ProjectStructure projectStructure = ProjectStructure.getProjectStructure((ProjectConfiguration) null);
            MutableList<ProjectFileOperation> fileOperations = ListIterate.collect(changes, c -> entityChangeToFileOperation(c, projectStructure, fileAccessContext));
            fileOperations.removeIf(Objects::isNull);
            if (fileOperations.isEmpty())
            {
                LOGGER.debug("No changes for {} {} {} in project {}", sourceSpecification.getWorkspaceType().getLabel(), sourceSpecification.getWorkspaceAccessType().getLabel(), sourceSpecification.getWorkspaceId(), projectId);
                return null;
            }
            return getProjectFileAccessProvider().getFileModificationContext(projectId, sourceSpecification, referenceRevisionId).submit(message, fileOperations);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return null;
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

    private class FileSystemProjectFileAccessProvider implements ProjectFileAccessProvider
    {
        @Override
        public FileAccessContext getFileAccessContext(String projectId, SourceSpecification sourceSpecification, String revisionId)
        {
            return (sourceSpecification.getClass() == ProjectSourceSpecification.class) ? null : new FileSystemProjectFileAccessContext(projectId, sourceSpecification, revisionId);
        }

        @Override
        public RevisionAccessContext getRevisionAccessContext(String projectId, SourceSpecification sourceSpecification, Iterable<? extends String> paths)
        {
            return null;
        }

        @Override
        public FileModificationContext getFileModificationContext(String projectId, SourceSpecification sourceSpecification, String revisionId)
        {
            return new FileSystemProjectFileFileModificationContext(projectId, sourceSpecification, revisionId);
        }
    }

    private class FileSystemProjectFileFileModificationContext implements ProjectFileAccessProvider.FileModificationContext
    {
        private final String projectId;
        private final String revisionId;
        private final SourceSpecification sourceSpecification;

        private FileSystemProjectFileFileModificationContext(String projectId, SourceSpecification sourceSpecification, String revisionId)
        {
            this.projectId = projectId;
            this.revisionId = revisionId;
            this.sourceSpecification = sourceSpecification;
            if (sourceSpecification == null)
            {
                throw new RuntimeException("source specification may not be null");
            }
            if ((this.sourceSpecification.getWorkspaceId() != null) && ((this.sourceSpecification.getWorkspaceType() == null) || (this.sourceSpecification.getWorkspaceAccessType() == null)))
            {
                throw new RuntimeException("workspace type and access type are required when workspace id is specified");
            }
        }

        @Override
        public Revision submit(String message, List<? extends ProjectFileOperation> operations)
        {
            try
            {
                int changeCount = operations.size();
                List<LocalCommitAction> commitActions = operations.stream().map(this::fileOperationToCommitAction).collect(Collectors.toCollection(() -> Lists.mutable.ofInitialCapacity(changeCount)));
                String referenceRevisionId = this.revisionId;
                if (referenceRevisionId != null)
                {
                    if (LOGGER.isDebugEnabled())
                    {
                        LOGGER.debug("Checking that {} is at revision {}", "getDescription()", referenceRevisionId);
                    }
                    String targetBranchRevision = FileSystemRevision.getFileSystemRevision(this.projectId, this.sourceSpecification.getWorkspaceId()).getId();
                    if (!referenceRevisionId.equals(targetBranchRevision))
                    {
                        String msg = "Expected " + "getDescription()" + " to be at revision " + referenceRevisionId + "; instead it was at revision " + targetBranchRevision;
                        LOGGER.info(msg);
                        throw new LegendSDLCServerException(msg, Response.Status.CONFLICT);
                    }
                }
                String branchName = this.sourceSpecification.getWorkspaceId();
                Repository repo = FileSystemWorkspaceApi.retrieveRepo(this.projectId);
                Git git = new Git(repo);
                git.checkout().setName(branchName).call();
                for (LocalCommitAction commitAction : commitActions)
                {
                    switch (commitAction.getAction())
                    {
                        case CREATE:
                            File newFile = new File(repo.getDirectory().getParent(), commitAction.getFilePath());
                            newFile.getParentFile().mkdirs();
                            newFile.createNewFile();
                            FileWriter writer = new FileWriter(newFile);
                            writer.write(LocalCommitAction.encodeBase64(commitAction.getContent()));
                            writer.close();
                            git.add().addFilepattern(".").call();
                            break;
                        case UPDATE:
                            File file = new File(repo.getDirectory().getParent(), commitAction.getFilePath());
                            if (file.exists())
                            {
                                FileWriter writer1 = new FileWriter(file);
                                writer1.write(LocalCommitAction.encodeBase64(commitAction.getContent()));
                                writer1.close();
                            }
                            git.add().addFilepattern(".").call();
                            System.out.println("File updated successfully in the branch");
                            break;
                        case MOVE:
                            System.out.println("Unhandled operation : MOVE");
                            break;
                        case DELETE:
                            File fileToRemove = new File(repo.getWorkTree(), commitAction.getFilePath().substring(1));
                            if (!fileToRemove.exists())
                            {
                                System.out.println("File does not exist in the branch");
                                break;
                            }
                            fileToRemove.delete();
                            git.rm().addFilepattern(commitAction.getFilePath().substring(1)).call();
                            if (git.status().call().isClean())
                            {
                                System.out.println("No changes to commit, file has already been removed");
                                break;
                            }
                            break;
                        default:
                            break;
                    }
                }
                RevCommit revCommit = git.commit().setMessage(message).call();
                System.out.println("Commit created: " + revCommit.getId().getName());
                repo.close();
                if (LOGGER.isDebugEnabled())
                {
                    LOGGER.debug("Committed {} changes to {}: {}", changeCount, "getDescription()", revCommit.getId());
                }
                return FileSystemRevision.getFileSystemRevision(projectId, sourceSpecification.getWorkspaceId());
            }
            catch (Exception e)
            {
                throw new LegendSDLCServerException("Unhandled exception", e);
            }
        }

        private LocalCommitAction fileOperationToCommitAction(ProjectFileOperation fileOperation)
        {
            if (fileOperation instanceof ProjectFileOperation.AddFile)
            {
                return new LocalCommitAction()
                        .withAction(LocalCommitAction.Action.CREATE)
                        .withFilePath(fileOperation.getPath())
                        //.withEncoding(Constants.Encoding.BASE64)
                        .withContent(((ProjectFileOperation.AddFile) fileOperation).getContent());
            }
            if (fileOperation instanceof ProjectFileOperation.ModifyFile)
            {
                return new LocalCommitAction()
                        .withAction(LocalCommitAction.Action.UPDATE)
                        .withFilePath(fileOperation.getPath())
                        //.withEncoding(Constants.Encoding.BASE64)
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
                ProjectFileOperation.MoveFile moveFileOperation = (ProjectFileOperation.MoveFile) fileOperation;
                LocalCommitAction commitAction = new LocalCommitAction()
                        .withAction(LocalCommitAction.Action.MOVE)
                        .withPreviousPath(moveFileOperation.getPath())
                        .withFilePath(moveFileOperation.getNewPath());

                byte[] newContent = moveFileOperation.getNewContent();
                return commitAction;
            }
            throw new IllegalArgumentException("Unsupported project file operation: " + fileOperation);
        }
    }

    private class FileSystemProjectFileAccessContext extends AbstractFileSystemFileAccessContext
    {
        private final String revisionId;
        private final SourceSpecification sourceSpecification;

        private FileSystemProjectFileAccessContext(String projectId, SourceSpecification sourceSpecification, String revisionId)
        {
            super(projectId, sourceSpecification);
            this.sourceSpecification = sourceSpecification;
            this.revisionId = revisionId;
            if (sourceSpecification == null)
            {
                throw new RuntimeException("source specification may not be null");
            }
            if ((this.sourceSpecification.getWorkspaceId() != null) && ((this.sourceSpecification.getWorkspaceType() == null) || (this.sourceSpecification.getWorkspaceAccessType() == null)))
            {
                throw new RuntimeException("workspace type and access type are required when workspace id is specified");
            }
        }

        @Override
        protected String getReference()
        {
            return null;
        }

        @Override
        protected String getDescriptionForExceptionMessage()
        {
            return null;
        }
    }

    private abstract class AbstractFileSystemFileAccessContext extends AbstractFileAccessContext
    {
        protected final String projectId;
        private final SourceSpecification sourceSpecification;

        AbstractFileSystemFileAccessContext(String projectId, SourceSpecification sourceSpecification)
        {
            this.projectId = projectId;
            this.sourceSpecification = sourceSpecification;
        }

        @Override
        protected Stream<ProjectFileAccessProvider.ProjectFile> getFilesInCanonicalDirectories(MutableList<String> directories)
        {
            try
            {
                return getFilesFromRepoArchive();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            return null;
        }

        private Stream<ProjectFileAccessProvider.ProjectFile> getFilesFromRepoArchive()
        {
            return null;
        }

        @Override
        public ProjectFileAccessProvider.ProjectFile getFile(String path)
        {
            try
            {
                Repository repo = FileSystemWorkspaceApi.retrieveRepo(this.projectId);
                RevWalk revWalk = new RevWalk(repo);
                RevCommit branchCommit = revWalk.parseCommit(repo.resolve(sourceSpecification.getWorkspaceId()));
                RevTree branchTree = branchCommit.getTree();
                if (path.startsWith("/"))
                {
                    path = path.substring(1);
                }
                byte[] fileBytes = new byte[0];
                try (TreeWalk treeWalk = TreeWalk.forPath(repo, path, branchTree))
                {
                    if (treeWalk != null)
                    {
                        ObjectId objectId = treeWalk.getObjectId(0);
                        ObjectReader objectReader = repo.newObjectReader();
                        fileBytes = objectReader.open(objectId).getBytes();
                    }
                }
                String encoding = detectEncodingFormat(fileBytes);
                if (encoding == null)
                {
                    throw new RuntimeException("Unknown encoding: null");
                }
                switch (encoding)
                {
                    case "TEXT":
                    {
                        return ProjectFiles.newStringProjectFile(path, fileBytes.toString());
                    }
                    case "BASE64":
                    {
                        return ProjectFiles.newByteArrayProjectFile(path, fileBytes);
                    }
                    default:
                    {
                        throw new RuntimeException("Unknown encoding: " + encoding);
                    }
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            return null;
        }

        public String detectEncodingFormat(byte[] fileBytes)
        {
            String fileContent = new String(fileBytes, Charset.forName("UTF-8"));
            boolean isText = fileContent.equals(new String(fileBytes, Charset.forName("UTF-8")));
            if (isText)
            {
                return "TEXT";
            }
            else
            {
                try
                {
                    byte[] decodedBytes = Base64.getDecoder().decode(fileBytes);
                    boolean isBase64 = Base64.getEncoder().encodeToString(decodedBytes).equals(fileContent);
                    if (isBase64)
                    {
                        return "BASE64";
                    }
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
            return "UNKNOWN";
        }

        @Override
        public boolean fileExists(String path)
        {
            try
            {
                Repository repo = FileSystemWorkspaceApi.retrieveRepo(this.projectId);
                RevWalk revWalk = new RevWalk(repo);
                RevCommit branchCommit = revWalk.parseCommit(repo.resolve(sourceSpecification.getWorkspaceId()));
                RevTree branchTree = branchCommit.getTree();
                if (path.startsWith("/"))
                {
                    path = path.substring(1);
                }
                try (TreeWalk treeWalk = TreeWalk.forPath(repo, path, branchTree))
                {
                    return treeWalk != null;
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
            return false;
        }

        protected abstract String getReference();

        protected abstract String getDescriptionForExceptionMessage();
    }

    static class EntityProjectFile
    {
        private final ProjectStructure.EntitySourceDirectory sourceDirectory;
        private final ProjectFileAccessProvider.ProjectFile file;
        private String path;
        private Entity entity;

        private EntityProjectFile(ProjectStructure.EntitySourceDirectory sourceDirectory, ProjectFileAccessProvider.ProjectFile file)
        {
            this.sourceDirectory = sourceDirectory;
            this.file = file;
        }

        synchronized String getEntityPath()
        {
            if (this.path == null)
            {
                this.path = this.sourceDirectory.filePathToEntityPath(this.file.getPath());
            }
            return this.path;
        }

        synchronized Entity getEntity()
        {
            if (this.entity == null)
            {
                Entity localEntity = this.sourceDirectory.deserialize(this.file);
//                if (!Objects.equals(localEntity.getPath(), getEntityPath())) // revisit this logic
//                {
//                    throw new RuntimeException("Expected entity path " + getEntityPath() + ", found " + localEntity.getPath());
//                }
                this.entity = localEntity;
            }
            return this.entity;
        }
    }

}