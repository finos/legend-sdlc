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

package org.finos.legend.sdlc.server.gitlab.api;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.impl.utility.ListIterate;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.entity.change.EntityChange;
import org.finos.legend.sdlc.domain.model.entity.change.EntityChangeType;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.domain.api.entity.EntityAccessContext;
import org.finos.legend.sdlc.server.domain.api.entity.EntityApi;
import org.finos.legend.sdlc.server.domain.api.entity.EntityModificationContext;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.GitLabProjectId;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.project.CachingFileAccessContext;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.project.ProjectFileOperation;
import org.finos.legend.sdlc.server.project.ProjectStructure;
import org.finos.legend.sdlc.server.tools.BackgroundTaskProcessor;
import org.finos.legend.sdlc.server.tools.StringTools;
import org.gitlab4j.api.models.DiffRef;
import org.gitlab4j.api.models.MergeRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.ws.rs.core.Response.Status;

public class GitLabEntityApi extends GitLabApiWithFileAccess implements EntityApi
{
    private static final Logger LOGGER = LoggerFactory.getLogger(GitLabEntityApi.class);

    @Inject
    public GitLabEntityApi(GitLabUserContext userContext, BackgroundTaskProcessor backgroundTaskProcessor)
    {
        super(userContext, backgroundTaskProcessor);
    }

    @Override
    public EntityAccessContext getProjectEntityAccessContext(String projectId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        return new GitLabEntityAccessContext()
        {
            @Override
            protected ProjectFileAccessProvider.FileAccessContext getFileAccessContext(ProjectFileAccessProvider projectFileAccessProvider)
            {
                return projectFileAccessProvider.getFileAccessContext(projectId, null, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE, null);
            }

            @Override
            protected String getInfoForException()
            {
                return "project " + projectId;
            }
        };
    }

    @Override
    public EntityAccessContext getProjectRevisionEntityAccessContext(String projectId, String revisionId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(revisionId, "revisionId may not be null");
        validateRevision(projectId, null, revisionId, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE);
        return new GitLabEntityAccessContext()
        {
            @Override
            protected ProjectFileAccessProvider.FileAccessContext getFileAccessContext(ProjectFileAccessProvider projectFileAccessProvider)
            {
                String resolvedRevisionId;
                try
                {
                    resolvedRevisionId = resolveRevisionId(revisionId, getProjectFileAccessProvider().getRevisionAccessContext(projectId, null, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE));
                }
                catch (Exception e)
                {
                    throw buildException(e,
                            () -> "User " + getCurrentUser() + " is not allowed to get " + getInfoForException(),
                            () -> "Unknown " + getInfoForException(),
                            () -> "Failed to get " + getInfoForException()
                    );
                }
                if (resolvedRevisionId == null)
                {
                    throw new LegendSDLCServerException("Failed to resolve " + getInfoForException());
                }
                return projectFileAccessProvider.getFileAccessContext(projectId, null, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE, resolvedRevisionId);
            }

            @Override
            protected String getInfoForException()
            {
                return "revision " + revisionId + " of project " + projectId;
            }
        };
    }

    public EntityAccessContext getReviewFromEntityAccessContext(String projectId, String reviewId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        MergeRequest mergeRequest = getReviewMergeRequest(getGitLabApi(gitLabProjectId.getGitLabMode()).getMergeRequestApi(), gitLabProjectId, reviewId);
        validateMergeRequestForComparison(mergeRequest);
        DiffRef diffRef = mergeRequest.getDiffRefs();

        if (diffRef != null && diffRef.getStartSha() != null && diffRef.getHeadSha() != null)
        {
            String revisionId = diffRef.getStartSha();
            return new GitLabEntityAccessContext()
            {
                @Override
                protected ProjectFileAccessProvider.FileAccessContext getFileAccessContext(ProjectFileAccessProvider projectFileAccessProvider)
                {
                    return projectFileAccessProvider.getFileAccessContext(projectId, null, null, revisionId);
                }

                @Override
                protected String getInfoForException()
                {
                    return "review " + reviewId + " of project " + projectId;
                }
            };
        }
        else
        {
            throw new LegendSDLCServerException("Unable to get [from] revision info in project " + projectId + " for review " + reviewId);
        }
    }

    public EntityAccessContext getReviewToEntityAccessContext(String projectId, String reviewId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        MergeRequest mergeRequest = getReviewMergeRequest(getGitLabApi(gitLabProjectId.getGitLabMode()).getMergeRequestApi(), gitLabProjectId, reviewId);
        validateMergeRequestForComparison(mergeRequest);
        DiffRef diffRef = mergeRequest.getDiffRefs();

        if (diffRef != null && diffRef.getStartSha() != null && diffRef.getHeadSha() != null)
        {
            String revisionId = diffRef.getHeadSha();
            return new GitLabEntityAccessContext()
            {
                @Override
                protected ProjectFileAccessProvider.FileAccessContext getFileAccessContext(ProjectFileAccessProvider projectFileAccessProvider)
                {
                    return projectFileAccessProvider.getFileAccessContext(projectId, null, null, revisionId);
                }

                @Override
                protected String getInfoForException()
                {
                    return "review " + reviewId + " of project " + projectId;
                }
            };
        }
        else
        {
            throw new LegendSDLCServerException("Unable to get [to] revision info in project " + projectId + " for review " + reviewId);
        }
    }

    private void validateMergeRequestForComparison(MergeRequest mergeRequest)
    {
        // We only allow review in OPEN and COMMITTED state. Note that this is the only control point for this restriction
        if (!isOpen(mergeRequest) && !isCommitted(mergeRequest))
        {
            throw new LegendSDLCServerException("Current operation not supported for review state " + getReviewState(mergeRequest) + " on review " + mergeRequest.getIid());
        }
    }

    @Override
    public EntityAccessContext getWorkspaceEntityAccessContext(String projectId, String workspaceId)
    {
        return this.getWorkspaceEntityAccessContextByWorkspaceAccessType(projectId, workspaceId, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE);
    }

    @Override
    public EntityAccessContext getBackupWorkspaceEntityAccessContext(String projectId, String workspaceId)
    {
        return this.getWorkspaceEntityAccessContextByWorkspaceAccessType(projectId, workspaceId, ProjectFileAccessProvider.WorkspaceAccessType.BACKUP);
    }

    @Override
    public EntityAccessContext getWorkspaceWithConflictResolutionEntityAccessContext(String projectId, String workspaceId)
    {
        return this.getWorkspaceEntityAccessContextByWorkspaceAccessType(projectId, workspaceId, ProjectFileAccessProvider.WorkspaceAccessType.CONFLICT_RESOLUTION);
    }

    private EntityAccessContext getWorkspaceEntityAccessContextByWorkspaceAccessType(String projectId, String workspaceId, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceId, "workspaceId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceAccessType, "workspaceAccessType may not be null");
        return new GitLabEntityAccessContext()
        {
            @Override
            protected ProjectFileAccessProvider.FileAccessContext getFileAccessContext(ProjectFileAccessProvider projectFileAccessProvider)
            {
                return projectFileAccessProvider.getFileAccessContext(projectId, workspaceId, workspaceAccessType, null);
            }

            @Override
            protected String getInfoForException()
            {
                return workspaceAccessType.getLabel() + " " + workspaceId + " of project " + projectId;
            }
        };
    }

    @Override
    public EntityAccessContext getWorkspaceRevisionEntityAccessContext(String projectId, String workspaceId, String revisionId)
    {
        return this.getWorkspaceRevisionEntityAccessContextByWorkspaceAccessType(projectId, workspaceId, revisionId, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE);
    }

    @Override
    public EntityAccessContext getBackupWorkspaceRevisionEntityAccessContext(String projectId, String workspaceId, String revisionId)
    {
        return this.getWorkspaceRevisionEntityAccessContextByWorkspaceAccessType(projectId, workspaceId, revisionId, ProjectFileAccessProvider.WorkspaceAccessType.BACKUP);
    }

    @Override
    public EntityAccessContext getWorkspaceWithConflictResolutionRevisionEntityAccessContext(String projectId, String workspaceId, String revisionId)
    {
        return this.getWorkspaceRevisionEntityAccessContextByWorkspaceAccessType(projectId, workspaceId, revisionId, ProjectFileAccessProvider.WorkspaceAccessType.CONFLICT_RESOLUTION);
    }

    private EntityAccessContext getWorkspaceRevisionEntityAccessContextByWorkspaceAccessType(String projectId, String workspaceId, String revisionId, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceId, "workspaceId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceAccessType, "workspaceAccessType may not be null");
        LegendSDLCServerException.validateNonNull(revisionId, "revisionId may not be null");
        validateRevision(projectId, workspaceId, revisionId, workspaceAccessType);
        return new GitLabEntityAccessContext()
        {
            @Override
            protected ProjectFileAccessProvider.FileAccessContext getFileAccessContext(ProjectFileAccessProvider projectFileAccessProvider)
            {
                String resolvedRevisionId;
                try
                {
                    resolvedRevisionId = resolveRevisionId(revisionId, getProjectFileAccessProvider().getRevisionAccessContext(projectId, workspaceId, workspaceAccessType));
                }
                catch (Exception e)
                {
                    throw buildException(e,
                            () -> "User " + getCurrentUser() + " is not allowed to get " + getInfoForException(),
                            () -> "Unknown " + getInfoForException(),
                            () -> "Failed to get " + getInfoForException()
                    );
                }
                if (resolvedRevisionId == null)
                {
                    throw new LegendSDLCServerException("Failed to resolve " + getInfoForException());
                }
                return projectFileAccessProvider.getFileAccessContext(projectId, workspaceId, workspaceAccessType, resolvedRevisionId);
            }

            @Override
            protected String getInfoForException()
            {
                return "revision " + revisionId + " in " + workspaceAccessType.getLabel() + " " + workspaceId + " of project " + projectId;
            }
        };
    }

    @Override
    public EntityAccessContext getVersionEntityAccessContext(String projectId, VersionId versionId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(versionId, "versionId may not be null");
        return new GitLabEntityAccessContext()
        {
            @Override
            protected ProjectFileAccessProvider.FileAccessContext getFileAccessContext(ProjectFileAccessProvider projectFileAccessProvider)
            {
                return projectFileAccessProvider.getFileAccessContext(projectId, versionId);
            }

            @Override
            protected String getInfoForException()
            {
                return "version " + versionId.toVersionIdString() + " of project " + projectId;
            }
        };
    }

    @Override
    public EntityModificationContext getWorkspaceEntityModificationContext(String projectId, String workspaceId)
    {
        return this.getWorkspaceEntityModificationContextByWorkspaceAccessType(projectId, workspaceId, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE);
    }

    @Override
    public EntityModificationContext getWorkspaceWithConflictResolutionEntityModificationContext(String projectId, String workspaceId)
    {
        return this.getWorkspaceEntityModificationContextByWorkspaceAccessType(projectId, workspaceId, ProjectFileAccessProvider.WorkspaceAccessType.CONFLICT_RESOLUTION);
    }

    private EntityModificationContext getWorkspaceEntityModificationContextByWorkspaceAccessType(String projectId, String workspaceId, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceId, "workspaceId may not be null");
        LegendSDLCServerException.validateNonNull(workspaceAccessType, "workspaceAccessType may not be null");
        return new GitLabEntityModificationContext(projectId, workspaceId, workspaceAccessType);
    }

    private void validateRevision(String projectId, String workspaceId, String revisionId, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType)
    {
        Revision revision = getProjectFileAccessProvider().getRevisionAccessContext(projectId, workspaceId, workspaceAccessType).getRevision(revisionId);
        if (revision == null)
        {
            throw new LegendSDLCServerException("Revision " + revisionId + " is unknown for " + getReferenceInfo(projectId, workspaceId, null), Status.NOT_FOUND);
        }
    }

    private abstract class GitLabEntityAccessContext implements EntityAccessContext
    {
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
                            return sourceDirectory.deserialize(file);
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
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to get entity " + path + " for " + getInfoForException(),
                        () -> "Unknown entity " + path + " for " + getInfoForException(),
                        () -> "Failed to get entity " + path + " for " + getInfoForException()
                );
            }
            throw new LegendSDLCServerException("Unknown entity " + path + " for " + getInfoForException(), Status.NOT_FOUND);
        }

        @Override
        public List<Entity> getEntities(Predicate<String> entityPathPredicate, Predicate<String> classifierPathPredicate, Predicate<? super Map<String, ?>> entityContentPredicate)
        {
            try (Stream<EntityProjectFile> stream = getEntityProjectFiles(getFileAccessContext(getProjectFileAccessProvider()), entityPathPredicate, classifierPathPredicate, entityContentPredicate))
            {
                return stream.map(EntityProjectFile::getEntity).collect(Collectors.toList());
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to get entities for " + getInfoForException(),
                        () -> "Unknown entities for " + getInfoForException(),
                        () -> "Failed to get entities for " + getInfoForException()
                );
            }
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
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to get entity paths for " + getInfoForException(),
                        () -> "Unknown entity paths for " + getInfoForException(),
                        () -> "Failed to get entity paths for " + getInfoForException()
                );
            }
        }

        protected abstract ProjectFileAccessProvider.FileAccessContext getFileAccessContext(ProjectFileAccessProvider projectFileAccessProvider);

        protected abstract String getInfoForException();
    }

    private class GitLabEntityModificationContext implements EntityModificationContext
    {
        private final String projectId;
        private final String workspaceId;
        private final ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType;

        private GitLabEntityModificationContext(String projectId, String workspaceId, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType)
        {
            this.projectId = projectId;
            this.workspaceId = workspaceId;
            this.workspaceAccessType = workspaceAccessType;
            if (this.workspaceId != null && this.workspaceAccessType == null)
            {
                throw new RuntimeException("workspace access type is required when workspace ID is specified");
            }
        }

        @Override
        public Revision updateEntities(Iterable<? extends Entity> entities, boolean replace, String message)
        {
            LegendSDLCServerException.validateNonNull(entities, "entities may not be null");
            LegendSDLCServerException.validateNonNull(message, "message may not be null");
            return GitLabEntityApi.this.updateEntities(this.projectId, this.workspaceId, entities, replace, message, this.workspaceAccessType);
        }

        @Override
        public Revision performChanges(List<? extends EntityChange> changes, String revisionId, String message)
        {
            LegendSDLCServerException.validateNonNull(changes, "changes may not be null");
            LegendSDLCServerException.validateNonNull(message, "message may not be null");
            validateEntityChanges(changes);
            return GitLabEntityApi.this.performChanges(this.projectId, this.workspaceId, revisionId, message, changes, this.workspaceAccessType);
        }
    }

    private Revision updateEntities(String projectId, String workspaceId, Iterable<? extends Entity> newEntities, boolean replace, String message, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType)
    {
        Map<String, Entity> newEntityDefinitions = Maps.mutable.empty();
        List<String> errorMessages = Lists.mutable.empty();
        for (Entity entity : newEntities)
        {
            if (entity == null)
            {
                errorMessages.add("Invalid entity: null");
                continue;
            }

            String path = entity.getPath();
            if (!isValidEntityPath(path))
            {
                errorMessages.add("Invalid entity path: \"" + path + "\"");
                continue;
            }

            String classifierPath = entity.getClassifierPath();
            if (!isValidClassifierPath(classifierPath))
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
                if (!(pkg instanceof String) || !(name instanceof String) || !path.equals(pkg + "::" + name))
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
        }
        if (!errorMessages.isEmpty())
        {
            throw new LegendSDLCServerException((errorMessages.size() == 1) ? errorMessages.get(0) : "There are errors with entity definitions:\n\t" + String.join("\n\t", errorMessages), Status.BAD_REQUEST);
        }

        Revision currentWorkspaceRevision = getProjectFileAccessProvider().getRevisionAccessContext(projectId, workspaceId, workspaceAccessType).getCurrentRevision();
        if (currentWorkspaceRevision == null)
        {
            throw new LegendSDLCServerException("Could not find current revision for " + workspaceAccessType.getLabel() + " " + workspaceId + " in project " + projectId + ": " + workspaceAccessType.getLabel() + " may be corrupt");
        }
        String revisionId = currentWorkspaceRevision.getId();
        LOGGER.debug("Using revision {} for reference in entity update in {} {} in project {}", revisionId, workspaceAccessType.getLabel(), workspaceId, projectId);
        List<EntityChange> entityChanges = Lists.mutable.ofInitialCapacity(newEntityDefinitions.size());
        if (newEntityDefinitions.isEmpty())
        {
            if (replace)
            {
                try (Stream<EntityProjectFile> stream = getEntityProjectFiles(projectId, workspaceId, revisionId, workspaceAccessType))
                {
                    stream.map(EntityProjectFile::getEntityPath).map(EntityChange::newDeleteEntity).forEach(entityChanges::add);
                }
            }
        }
        else
        {
            try (Stream<EntityProjectFile> stream = getEntityProjectFiles(projectId, workspaceId, revisionId, workspaceAccessType))
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
            newEntityDefinitions.values().forEach(definition -> entityChanges.add(EntityChange.newCreateEntity(definition.getPath(), definition.getClassifierPath(), definition.getContent())));
        }

        return performChanges(projectId, workspaceId, revisionId, message, entityChanges, workspaceAccessType);
    }

    private Revision performChanges(String projectId, String workspaceId, String referenceRevisionId, String message, List<? extends EntityChange> changes, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType)
    {
        int changeCount = changes.size();
        if (changeCount == 0)
        {
            LOGGER.debug("No changes for {} {} in project {}", workspaceAccessType.getLabel(), workspaceId, projectId);
            return null;
        }
        LOGGER.debug("Committing {} changes to {} {} in project {}: {}", changeCount, workspaceAccessType.getLabel(), workspaceId, projectId, message);
        try
        {
            ProjectFileAccessProvider.FileAccessContext fileAccessContext = getProjectFileAccessProvider().getFileAccessContext(projectId, workspaceId, workspaceAccessType, referenceRevisionId);
            ProjectStructure projectStructure = ProjectStructure.getProjectStructure(fileAccessContext);
            List<ProjectFileOperation> fileOperations = ListIterate.collect(changes, c -> entityChangeToFileOperation(c, projectStructure, fileAccessContext));
            return getProjectFileAccessProvider().getFileModificationContext(projectId, workspaceId, workspaceAccessType, referenceRevisionId).submit(message, fileOperations);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to perform changes on " + workspaceAccessType.getLabel() + " " + workspaceId + " in project " + projectId,
                    () -> "Unknown " + workspaceAccessType.getLabel() + " (" + workspaceId + ") or project (" + projectId + ")",
                    () -> "Failed to perform changes on " + workspaceAccessType.getLabel() + " " + workspaceId + " in project " + projectId + " (message: " + message + ")");
        }
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
                return currentFilePath.equals(newFilePath) ? ProjectFileOperation.modifyFile(currentFilePath, serialized) : ProjectFileOperation.moveFile(currentFilePath, newFilePath, serialized);
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

    private Stream<EntityProjectFile> getEntityProjectFiles(String projectId, String workspaceId, String revisionId, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType)
    {
        return getEntityProjectFiles(getProjectFileAccessProvider().getFileAccessContext(projectId, workspaceId, workspaceAccessType, revisionId));
    }

    private Stream<EntityProjectFile> getEntityProjectFiles(ProjectFileAccessProvider.FileAccessContext accessContext, Predicate<String> entityPathPredicate, Predicate<String> classifierPathPredicate, Predicate<? super Map<String, ?>> contentPredicate)
    {
        Stream<EntityProjectFile> stream = getEntityProjectFiles(accessContext);
        if (entityPathPredicate != null)
        {
            stream = stream.filter(epf -> entityPathPredicate.test(epf.getEntityPath()));
        }
        if (classifierPathPredicate != null)
        {
            stream = stream.filter(epf -> classifierPathPredicate.test(epf.getEntity().getClassifierPath()));
        }
        if (contentPredicate != null)
        {
            stream = stream.filter(epf -> contentPredicate.test(epf.getEntity().getContent()));
        }
        return stream;
    }

    private Stream<EntityProjectFile> getEntityProjectFiles(ProjectFileAccessProvider.FileAccessContext accessContext)
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
            throw new LegendSDLCServerException(builder.toString(), Status.BAD_REQUEST);
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
        else if (!isValidEntityPath(path))
        {
            errorMessages.add("Invalid entity path: " + path);
        }
        else if (content != null)
        {
            Object pkg = content.get("package");
            Object name = content.get("name");
            if (!(pkg instanceof String) || !(name instanceof String) || !path.equals(pkg + "::" + name))
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
                    else if (!isValidClassifierPath(classifierPath))
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
                    else if (!isValidEntityPath(newPath))
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

    private static class EntityProjectFile
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