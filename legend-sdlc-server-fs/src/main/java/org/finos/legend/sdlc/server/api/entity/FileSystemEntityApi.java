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

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.finos.legend.sdlc.core.entity.EntityAccessOperations;
import org.finos.legend.sdlc.core.entity.EntityModificationOperations;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.entity.change.EntityChange;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.server.domain.api.entity.EntityAccessContext;
import org.finos.legend.sdlc.server.domain.api.entity.EntityApi;
import org.finos.legend.sdlc.server.domain.api.entity.EntityModificationContext;
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification;
import org.finos.legend.sdlc.server.domain.api.project.source.WorkspaceSourceSpecification;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.exception.FSException;
import org.finos.legend.sdlc.project.structure.EntitySourceDirectory;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider;
import org.finos.legend.sdlc.project.files.ProjectFiles;
import org.finos.legend.sdlc.project.structure.ProjectStructure;
import org.finos.legend.sdlc.server.startup.FSConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
            return EntityAccessOperations.getEntity(getFileAccessContext(getProjectFileAccessProvider()), path, null);
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
            return EntityAccessOperations.getEntityPaths(getFileAccessContext(getProjectFileAccessProvider()), entityPathPredicate, classifierPathPredicate, entityContentPredicate);
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
            EntityModificationOperations.validateEntityChanges(changes);
            return EntityModificationOperations.performChanges(getProjectFileAccessProvider(), this.projectId, this.sourceSpecification, revisionId, message, changes);
        }
    }

    public Revision updateEntities(String projectId, WorkspaceSourceSpecification sourceSpecification, Iterable<? extends Entity> newEntities, boolean replace, String message)
    {
        return EntityModificationOperations.updateEntities(getProjectFileAccessProvider(), projectId, sourceSpecification, newEntities, replace, message, String.valueOf(sourceSpecification));
    }

    private Stream<EntityProjectFile> getEntityProjectFiles(ProjectFileAccessProvider.FileAccessContext accessContext, Predicate<String> entityPathPredicate, Predicate<String> classifierPathPredicate, Predicate<? super Map<String, ?>> contentPredicate, boolean excludeInvalid, String branchName, Repository repo)
    {
        Stream<EntityProjectFile> stream = getEntityProjectFiles(branchName, repo, accessContext);
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
        List<EntitySourceDirectory> sourceDirectories = projectStructure.getEntitySourceDirectories();
        return sourceDirectories.stream().flatMap(sd -> getSourceDirectoryProjectFiles(sd, branchName, repo, projectStructure.getProjectConfiguration().getProjectId()));
    }

    private Stream<EntityProjectFile> getSourceDirectoryProjectFiles(EntitySourceDirectory sourceDirectory, String workspaceId, Repository repo, String projectID)
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

    static class EntityProjectFile
    {
        private final EntitySourceDirectory sourceDirectory;
        private final ProjectFileAccessProvider.ProjectFile file;
        private String path;
        private Entity entity;
        private String projectID;
        private String rootDirectory;

        private EntityProjectFile(EntitySourceDirectory sourceDirectory, ProjectFileAccessProvider.ProjectFile file, String projectID, String rootDirectory)
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
