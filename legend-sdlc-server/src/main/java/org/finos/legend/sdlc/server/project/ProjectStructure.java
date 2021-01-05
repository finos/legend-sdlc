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

package org.finos.legend.sdlc.server.project;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactGeneration;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactType;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactTypeGenerationConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.Dependency;
import org.finos.legend.sdlc.domain.model.project.configuration.MetamodelDependency;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectStructureVersion;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.serialization.EntitySerializers;
import org.finos.legend.sdlc.serialization.EntityTextSerializer;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider.FileAccessContext;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider.ProjectFile;
import org.finos.legend.sdlc.server.project.extension.ProjectStructureExtension;

import java.io.Reader;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.SourceVersion;
import javax.ws.rs.core.Response.Status;

public abstract class ProjectStructure
{
    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(SerializationFeature.INDENT_OUTPUT, true);

    private static final ProjectStructureFactory PROJECT_STRUCTURE_FACTORY = ProjectStructureFactory.newFactory(ProjectStructure.class.getClassLoader());

    private static final Pattern VALID_ARTIFACT_ID_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*+(-[a-z][a-z0-9_]*+)*+$");

    public static final String PROJECT_CONFIG_PATH = "/project.json";

    private static final String PACKAGE_SEPARATOR = "::";
    private static final String ENTITIES_DIRECTORY_NAME = "entities";

    private static final Set<ArtifactType> FORBIDDEN_ARTIFACT_GENERATION_TYPES = Collections.unmodifiableSet(EnumSet.of(ArtifactType.entities, ArtifactType.versioned_entities, ArtifactType.service_execution));

    private final ProjectConfiguration projectConfiguration;
    private final EntityTextSerializer entitySerializer;

    protected ProjectStructure(ProjectConfiguration projectConfiguration, EntityTextSerializer entitySerializer)
    {
        this.projectConfiguration = projectConfiguration;
        this.entitySerializer = entitySerializer;
    }

    protected ProjectStructure(ProjectConfiguration projectConfiguration)
    {
        this(projectConfiguration, EntitySerializers.getDefaultJsonSerializer());
    }

    @Override
    public String toString()
    {
        return "<ProjectStructure version " + getVersion() + ">";
    }

    public int getVersion()
    {
        return getProjectConfiguration().getProjectStructureVersion().getVersion();
    }

    public ProjectConfiguration getProjectConfiguration()
    {
        return this.projectConfiguration;
    }

    /**
     * Return the file path corresponding to the given entity path.
     * The slash character ('/') is used to separate directories
     * within the path. Paths will always begin with /, and will
     * never be empty. Note that the file path will be returned
     * regardless of whether the file actually exists.
     *
     * @param entityPath entity path
     * @return corresponding file path
     */
    public String entityPathToFilePath(String entityPath)
    {
        StringBuilder builder = new StringBuilder(getEntitiesDirectoryLength() + entityPath.length() + getEntityFileExtensionLength());
        appendEntitiesDirectory(builder);
        appendPackageablePathAsFilePath(builder, entityPath);
        builder.append('.').append(this.entitySerializer.getDefaultFileExtension());
        return builder.toString();
    }

    /**
     * Return the file path corresponding to the given package path.
     * The slash character ('/') is used to separate directories
     * within the path. Paths will always begin with /, and will
     * never be empty. Note the the file path will refer to a
     * directory and will be returned regardless of whether the
     * directory actually exists.
     *
     * @param packagePath package path
     * @return corresponding file path
     */
    public String packagePathToFilePath(String packagePath)
    {
        StringBuilder builder = new StringBuilder(getEntitiesDirectoryLength() + packagePath.length());
        appendEntitiesDirectory(builder);
        appendPackageablePathAsFilePath(builder, packagePath);
        return builder.toString();
    }

    /**
     * Return whether the given file path is an entity file path. Note
     * that this is a purely syntactic check and does not imply anything
     * about whether the file actually exists or what it contains.
     *
     * @param filePath file path
     * @return whether filePath is an entity file path
     */
    public boolean isEntityFilePath(String filePath)
    {
        int entitiesDirectoryLength = getEntitiesDirectoryLength();
        return (filePath.length() > (entitiesDirectoryLength + getEntityFileExtensionLength() + 1)) &&
                startsWithEntitiesDirectory(filePath) && (filePath.charAt(entitiesDirectoryLength) == '/') &&
                endsWithEntityFileExtension(filePath);
    }

    /**
     * Return the packageable (entity or package) path that corresponds
     * to the given file path.
     *
     * @param filePath file path
     * @return corresponding packageable path
     */
    public String filePathToPackageablePath(String filePath)
    {
        int start = getEntitiesDirectoryLength() + 1;
        int end = endsWithEntityFileExtension(filePath) ? (filePath.length() - getEntityFileExtensionLength()) : filePath.length();
        int length = end - start;
        StringBuilder builder = new StringBuilder(length + (length / 4));
        appendFilePathAsPackageablePath(builder, filePath, start, end);
        return builder.toString();
    }

    public byte[] serializeEntity(Entity entity)
    {
        try
        {
            return this.entitySerializer.serializeToBytes(entity);
        }
        catch (Exception e)
        {
            StringBuilder message = new StringBuilder("Error serializing entity ").append(entity.getPath());
            String eMessage = e.getMessage();
            if (eMessage != null)
            {
                message.append(": ").append(eMessage);
            }
            throw new RuntimeException(message.toString(), e);
        }
    }

    public byte[] serializeEntity(String entityPath, String classifierPath, Map<String, ?> content)
    {
        return serializeEntity(Entity.newEntity(entityPath, classifierPath, content));
    }

    public Entity deserializeEntity(ProjectFile projectFile)
    {
        try
        {
            String entityPath = filePathToPackageablePath(projectFile.getPath());
            try (Reader reader = projectFile.getContentAsReader())
            {
                return deserializeEntity(entityPath, reader);
            }
        }
        catch (Exception e)
        {
            String eMessage = e.getMessage();
            if ((e instanceof RuntimeException) && (eMessage != null) && eMessage.startsWith("Error deserializing entity "))
            {
                throw (RuntimeException) e;
            }
            StringBuilder message = new StringBuilder("Error deserializing entity from file ").append(projectFile.getPath());
            if (eMessage != null)
            {
                message.append(": ").append(eMessage);
            }
            throw new RuntimeException(message.toString(), e);
        }
    }

    public Entity deserializeEntity(String entityPath, Reader reader)
    {
        try
        {
            Entity entity = this.entitySerializer.deserialize(reader);
            if ((entityPath != null) && !entityPath.equals(entity.getPath()))
            {
                throw new RuntimeException("Expected entity path " + entityPath + ", found " + entity.getPath());
            }
            return entity;
        }
        catch (Exception e)
        {
            StringBuilder message = new StringBuilder("Error deserializing entity ").append(entityPath);
            String eMessage = e.getMessage();
            if (eMessage != null)
            {
                message.append(": ").append(eMessage);
            }
            throw new RuntimeException(message.toString(), e);
        }
    }

    public String getEntitiesDirectory()
    {
        return appendEntitiesDirectory(new StringBuilder(getEntitiesDirectoryLength())).toString();
    }

    public String getEntitiesParentDirectory()
    {
        return appendEntitiesParentDirectory(new StringBuilder(getEntitiesParentDirectoryLength())).toString();
    }

    public StringBuilder appendEntitiesDirectory(StringBuilder builder)
    {
        return appendEntitiesParentDirectory(builder).append(ENTITIES_DIRECTORY_NAME);
    }

    public abstract StringBuilder appendEntitiesParentDirectory(StringBuilder builder);

    int getEntitiesDirectoryLength()
    {
        return getEntitiesParentDirectoryLength() + ENTITIES_DIRECTORY_NAME.length();
    }

    public abstract int getEntitiesParentDirectoryLength();

    boolean startsWithEntitiesDirectory(String filePath)
    {
        return startsWithEntitiesParentDirectory(filePath) && filePath.startsWith(ENTITIES_DIRECTORY_NAME, getEntitiesParentDirectoryLength());
    }

    protected int getEntityFileExtensionLength()
    {
        return this.entitySerializer.getDefaultFileExtension().length() + 1;
    }

    protected boolean endsWithEntityFileExtension(String path)
    {
        String fileExtension = this.entitySerializer.getDefaultFileExtension();
        return (path.length() > fileExtension.length()) &&
                path.endsWith(fileExtension) &&
                (path.charAt(path.length() - (fileExtension.length() + 1)) == '.');
    }

    public abstract boolean startsWithEntitiesParentDirectory(String filePath);

    public abstract void collectUpdateProjectConfigurationOperations(ProjectStructure oldStructure, FileAccessContext fileAccessContext, BiFunction<String, VersionId, FileAccessContext> versionFileAccessContextProvider, Consumer<ProjectFileOperation> operationConsumer);

    protected void addOrModifyFile(String path, String newContent, FileAccessContext fileAccessContext, Consumer<ProjectFileOperation> operationConsumer)
    {
        ProjectFile file = fileAccessContext.getFile(path);
        if (file == null)
        {
            operationConsumer.accept(ProjectFileOperation.addFile(path, newContent));
        }
        else if (!newContent.equals(file.getContentAsString()))
        {
            operationConsumer.accept(ProjectFileOperation.modifyFile(path, newContent));
        }
    }

    protected void moveOrAddOrModifyFile(String oldPath, String newPath, String newContent, FileAccessContext fileAccessContext, Consumer<ProjectFileOperation> operationConsumer)
    {
        ProjectFile oldFile = fileAccessContext.getFile(oldPath);
        ProjectFile newFile = newPath.equals(oldPath) ? oldFile : fileAccessContext.getFile(newPath);
        if (oldFile == null)
        {
            if (newFile == null)
            {
                // neither old nor new exists: add file in new location
                operationConsumer.accept(ProjectFileOperation.addFile(newPath, newContent));
            }
            else if (!newContent.equals(newFile.getContentAsString()))
            {
                // old does not exist, but new does and it doesn't have the desired content: modify new
                operationConsumer.accept(ProjectFileOperation.modifyFile(newPath, newContent));
            }
        }
        else if (newFile == null)
        {
            // old exists but new does not: move old to new
            operationConsumer.accept(ProjectFileOperation.moveFile(oldPath, newPath, newContent));
        }
        else
        {
            // both old and new exist: delete old (if it's different from new) and modify new (if it doesn't have the desired content)
            if (newFile != oldFile)
            {
                operationConsumer.accept(ProjectFileOperation.deleteFile(oldPath));
            }
            if (!newContent.equals(newFile.getContentAsString()))
            {
                operationConsumer.accept(ProjectFileOperation.modifyFile(newPath, newContent));
            }
        }
    }

    protected void deleteFileIfPresent(String path, FileAccessContext fileAccessContext, Consumer<ProjectFileOperation> operationConsumer)
    {
        if (fileAccessContext.getFile(path) != null)
        {
            operationConsumer.accept(ProjectFileOperation.deleteFile(path));
        }
    }

    public boolean isSupportedArtifactType(ArtifactType type)
    {
        return getSupportedArtifactTypes().contains(type);
    }

    public abstract Set<ArtifactType> getSupportedArtifactTypes();

    public abstract Stream<String> getArtifactIdsForType(ArtifactType type);

    public Stream<String> getAllArtifactIds()
    {
        ProjectConfiguration configuration = getProjectConfiguration();
        return (configuration == null) ? Stream.empty() : Stream.of(configuration.getArtifactId());
    }

    public Stream<String> getArtifactIds(Collection<? extends ArtifactType> types)
    {
        if (types.isEmpty())
        {
            return Stream.empty();
        }

        Stream<String> stream = types.stream().flatMap(this::getArtifactIdsForType);

        if (types.size() > 1)
        {
            stream = stream.distinct();
        }
        return stream;
    }

    protected List<ProjectDependency> getProjectDependencies()
    {
        ProjectConfiguration configuration = getProjectConfiguration();
        List<ProjectDependency> projectDependencies = (configuration == null) ? null : configuration.getProjectDependencies();
        return (projectDependencies == null) ? Collections.emptyList() : projectDependencies;
    }

    protected void validate()
    {
        ProjectConfiguration projectConfig = getProjectConfiguration();
        if (projectConfig != null)
        {
            List<ArtifactGeneration> unsupportedGenerations = projectConfig.getArtifactGenerations().stream().filter(g -> !isSupportedArtifactType(g.getType())).collect(Collectors.toList());
            if (!unsupportedGenerations.isEmpty())
            {
                unsupportedGenerations.sort(Comparator.comparing(ArtifactGeneration::getName));
                throw new IllegalStateException(unsupportedGenerations.stream().map(g -> g.getName() + " (" + g.getType() + ")").collect(Collectors.joining(", ", "Unsupported artifact generations: ", "")));
            }
        }
    }

    protected static ProjectStructure getProjectStructureForProjectDependency(ProjectDependency projectDependency, BiFunction<String, VersionId, FileAccessContext> versionFileAccessContextProvider)
    {
        FileAccessContext versionFileAccessContext = versionFileAccessContextProvider.apply(projectDependency.getProjectId(), projectDependency.getVersionId());
        ProjectConfiguration versionConfig = ProjectStructure.getProjectConfiguration(versionFileAccessContext);
        if (versionConfig == null)
        {
            throw new LegendSDLCServerException("Invalid version of project " + projectDependency.getProjectId() + ": " + projectDependency.getVersionId().toVersionIdString());
        }
        return ProjectStructure.getProjectStructure(versionConfig);
    }

    public static int getLatestProjectStructureVersion()
    {
        return PROJECT_STRUCTURE_FACTORY.getLatestVersion();
    }

    public static ProjectStructure getProjectStructure(String projectId, String workspaceId, String revisionId, ProjectFileAccessProvider projectFileAccessor, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType)
    {
        return getProjectStructure(projectFileAccessor.getFileAccessContext(projectId, workspaceId, revisionId, workspaceAccessType));
    }

    public static ProjectStructure getProjectStructure(FileAccessContext fileAccessContext)
    {
        ProjectConfiguration config = getProjectConfiguration(fileAccessContext);
        return getProjectStructure(config);
    }

    public static ProjectStructure getProjectStructure(ProjectConfiguration projectConfiguration)
    {
        return PROJECT_STRUCTURE_FACTORY.newProjectStructure(projectConfiguration);
    }

    public static ProjectConfiguration getProjectConfiguration(String projectId, String workspaceId, String revisionId, ProjectFileAccessProvider projectFileAccessProvider, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType)
    {
        return getProjectConfiguration(projectFileAccessProvider.getFileAccessContext(projectId, workspaceId, revisionId, workspaceAccessType));
    }

    public static ProjectConfiguration getProjectConfiguration(String projectId, VersionId versionId, ProjectFileAccessProvider projectFileAccessProvider)
    {
        return getProjectConfiguration(projectFileAccessProvider.getFileAccessContext(projectId, versionId));
    }

    public static ProjectConfiguration getProjectConfiguration(FileAccessContext fileAccessContext)
    {
        ProjectFile configFile = getProjectConfigurationFile(fileAccessContext);
        return (configFile == null) ? null : readProjectConfiguration(configFile);
    }

    public static ProjectConfiguration getDefaultProjectConfiguration(String projectId, ProjectType projectType)
    {
        return new SimpleProjectConfiguration(projectId, projectType, ProjectStructureVersion.newProjectStructureVersion(0), null, null, null, null, null);
    }

    public static boolean isValidGroupId(String groupId)
    {
        return (groupId != null) && !groupId.isEmpty() && SourceVersion.isName(groupId);
    }

    public static boolean isValidArtifactId(String artifactId)
    {
        return (artifactId != null) && !artifactId.isEmpty() && VALID_ARTIFACT_ID_PATTERN.matcher(artifactId).matches();
    }

    static Revision buildProjectStructure(ProjectConfigurationUpdateBuilder configurationUpdater)
    {
        if (!configurationUpdater.hasProjectStructureVersion())
        {
            configurationUpdater.setProjectStructureVersion(getLatestProjectStructureVersion());
        }
        if (configurationUpdater.hasProjectStructureExtensionVersion() && !configurationUpdater.hasProjectStructureExtensionProvider())
        {
            throw new IllegalArgumentException("Project structure extension version specified (" + configurationUpdater.getProjectStructureExtensionVersion() + ") with no project structure extension provider");
        }
        if (!configurationUpdater.hasProjectStructureExtensionVersion() && configurationUpdater.hasProjectStructureExtensionProvider())
        {
            configurationUpdater.setProjectStructureExtensionVersion(configurationUpdater.getProjectStructureExtensionProvider().getLatestVersionForProjectStructureVersion(configurationUpdater.getProjectStructureVersion()));
        }

        return updateProjectConfiguration(configurationUpdater, false);
    }

    static Revision updateProjectConfiguration(ProjectConfigurationUpdateBuilder configurationUpdater)
    {
        return updateProjectConfiguration(configurationUpdater, true);
    }

    private static Revision updateProjectConfiguration(ProjectConfigurationUpdateBuilder updateBuilder, boolean requireRevisionId)
    {
        ProjectFileAccessProvider projectFileAccessProvider = CachingProjectFileAccessProvider.wrap(updateBuilder.getProjectFileAccessProvider());
        ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType = updateBuilder.getWorkspaceAccessType();

        if (updateBuilder.hasGroupId() && !isValidGroupId(updateBuilder.getGroupId()))
        {
            throw new LegendSDLCServerException("Invalid groupId: " + updateBuilder.getGroupId(), Status.BAD_REQUEST);
        }
        if (updateBuilder.hasArtifactId() && !isValidArtifactId(updateBuilder.getArtifactId()))
        {
            throw new LegendSDLCServerException("Invalid artifactId: " + updateBuilder.getArtifactId(), Status.BAD_REQUEST);
        }

        ProjectType projectType = updateBuilder.getProjectType();
        String projectId = updateBuilder.getProjectId();
        String workspaceId = updateBuilder.getWorkspaceId();
        String revisionId = updateBuilder.getRevisionId();

        // if revisionId not specified, get the current revision
        if (revisionId == null)
        {
            Revision currentRevision = projectFileAccessProvider.getRevisionAccessContext(projectId, workspaceId, null, workspaceAccessType).getCurrentRevision();
            if (currentRevision != null)
            {
                revisionId = currentRevision.getId();
            }
            else if (requireRevisionId)
            {
                StringBuilder builder = new StringBuilder("Could not find current revision for ");
                if (workspaceId != null)
                {
                    builder.append(workspaceAccessType.getLabel()).append(" ").append(workspaceId).append("in ");
                }
                builder.append("project ").append(projectId).append(": it may be corrupt");
                throw new LegendSDLCServerException(builder.toString());
            }
        }

        // find out what we need to update for project structure
        FileAccessContext fileAccessContext = projectFileAccessProvider.getFileAccessContext(projectId, workspaceId, revisionId, workspaceAccessType);
        ProjectFile configFile = getProjectConfigurationFile(fileAccessContext);
        ProjectConfiguration currentConfig = (configFile == null) ? getDefaultProjectConfiguration(projectId, projectType) : readProjectConfiguration(configFile);
        if (projectType != currentConfig.getProjectType())
        {
            throw new LegendSDLCServerException("Project type mismatch for project " + projectId + ": got " + projectType + ", found " + currentConfig.getProjectType(), Status.BAD_REQUEST);
        }
        boolean updateProjectStructureVersion = updateBuilder.hasProjectStructureVersion() && (updateBuilder.getProjectStructureVersion() != currentConfig.getProjectStructureVersion().getVersion());
        boolean updateProjectStructureExtensionVersion = updateBuilder.hasProjectStructureExtensionVersion() && !updateBuilder.getProjectStructureExtensionVersion().equals(currentConfig.getProjectStructureVersion().getExtensionVersion());
        boolean updateGroupId = updateBuilder.hasGroupId() && !updateBuilder.getGroupId().equals(currentConfig.getGroupId());
        boolean updateArtifactId = updateBuilder.hasArtifactId() && !updateBuilder.getArtifactId().equals(currentConfig.getArtifactId());

        // find out which dependencies we need to update
        boolean updateProjectDependencies = false;
        Set<ProjectDependency> projectDependencies = Sets.mutable.withAll(currentConfig.getProjectDependencies());
        if (updateBuilder.hasProjectDependenciesToRemove())
        {
            updateProjectDependencies |= projectDependencies.removeAll(updateBuilder.getProjectDependenciesToRemove());
        }

        // add new dependencies to the list of dependencies while also validate that there are no unknown/non-prod dependencies
        if (updateBuilder.hasProjectDependenciesToAdd())
        {
            List<ProjectDependency> unknownDependencies = Lists.mutable.empty();
            List<ProjectDependency> nonProdDependencies = Lists.mutable.empty();
            SortedMap<ProjectDependency, Exception> accessExceptions = new TreeMap<>();
            for (ProjectDependency projectDependency : updateBuilder.getProjectDependenciesToAdd())
            {
                if (projectDependencies.add(projectDependency))
                {
                    updateProjectDependencies = true;
                    try
                    {
                        ProjectConfiguration dependencyConfig = getProjectConfiguration(projectFileAccessProvider.getFileAccessContext(projectDependency.getProjectId(), projectDependency.getVersionId()));
                        if (dependencyConfig == null)
                        {
                            unknownDependencies.add(projectDependency);
                        }
                        else if (dependencyConfig.getProjectType() != ProjectType.PRODUCTION)
                        {
                            nonProdDependencies.add(projectDependency);
                        }
                    }
                    catch (Exception e)
                    {
                        accessExceptions.put(projectDependency, e);
                    }
                }
            }
            if (!unknownDependencies.isEmpty() || !nonProdDependencies.isEmpty() || !accessExceptions.isEmpty())
            {
                StringBuilder builder = new StringBuilder("There were issues with one or more added project dependencies");
                if (!unknownDependencies.isEmpty())
                {
                    builder.append("; unknown ").append((unknownDependencies.size() == 1) ? "dependency" : "dependencies").append(": ");
                    unknownDependencies.sort(Comparator.naturalOrder());
                    unknownDependencies.forEach(d -> d.appendDependencyString((d == unknownDependencies.get(0)) ? builder : builder.append(", ")));
                }
                if (!nonProdDependencies.isEmpty())
                {
                    builder.append("; non-production ").append((unknownDependencies.size() == 1) ? "dependency" : "dependencies").append(": ");
                    nonProdDependencies.sort(Comparator.naturalOrder());
                    nonProdDependencies.forEach(d -> d.appendDependencyString((d == nonProdDependencies.get(0)) ? builder : builder.append(", ")));
                }
                if (!accessExceptions.isEmpty())
                {
                    builder.append("; access ").append((accessExceptions.size() == 1) ? "exception" : "exceptions").append(": ");
                    ProjectDependency first = accessExceptions.firstKey();
                    accessExceptions.forEach((d, e) -> d.appendDependencyString((d == first) ? builder : builder.append(", ")).append(" (").append(e.getMessage()).append(')'));
                }
                throw new LegendSDLCServerException(builder.toString(), Status.BAD_REQUEST);
            }
        }

        // validate if there are any conflicts between the dependencies
        if (updateProjectDependencies)
        {
            validateDependencyConflicts(
                    projectDependencies,
                    ProjectDependency::getProjectId,
                    (id, deps) ->
                    {
                        if ((deps.size() <= 1) || deps.stream().allMatch(dep -> getProjectStructure(projectFileAccessProvider.getFileAccessContext(dep.getProjectId(), dep.getVersionId())).isSupportedArtifactType(ArtifactType.versioned_entities)))
                        {
                            return null;
                        }
                        List<ProjectDependency> supported = Lists.mutable.empty();
                        List<ProjectDependency> unsupported = Lists.mutable.empty();
                        deps.forEach(dep -> (getProjectStructure(projectFileAccessProvider.getFileAccessContext(dep.getProjectId(), dep.getVersionId())).isSupportedArtifactType(ArtifactType.versioned_entities) ? supported : unsupported).add(dep));
                        StringBuilder message = new StringBuilder();
                        unsupported.forEach(dep -> dep.appendVersionIdString((message.length() == 0) ? message : message.append(", ")));
                        message.append((unsupported.size() == 1) ? " does" : " do").append(" not support multi-version dependency");
                        if (!supported.isEmpty())
                        {
                            int startLen = message.length();
                            supported.forEach(dep -> dep.appendVersionIdString(message.append((message.length() == startLen) ? "; " : ", ")));
                            message.append((supported.size() == 1) ? " does" : " do");
                        }
                        return message.toString();
                    },
                    "projects");
        }

        // check if we need to update any metamodel dependencies
        boolean updateMetamodelDependencies = false;
        Set<MetamodelDependency> metamodelDependencies = Sets.mutable.withAll(currentConfig.getMetamodelDependencies());
        if (updateBuilder.hasMetamodelDependenciesToRemove())
        {
            updateMetamodelDependencies |= metamodelDependencies.removeAll(updateBuilder.getMetamodelDependenciesToRemove());
        }

        // add new metamodel dependencies to the list of metamodel dependencies while also validate that there are no unknown metamodel dependencies
        if (updateBuilder.hasMetamodelDependenciesToAdd())
        {
            List<MetamodelDependency> unknownDependencies = Lists.mutable.empty();
            for (MetamodelDependency metamodelDependency : updateBuilder.getMetamodelDependenciesToAdd())
            {
                if (metamodelDependencies.add(metamodelDependency))
                {
                    updateMetamodelDependencies = true;
                    if (!isKnownMetamodel(metamodelDependency))
                    {
                        unknownDependencies.add(metamodelDependency);
                    }
                }
            }
            if (!unknownDependencies.isEmpty())
            {
                StringBuilder builder = new StringBuilder("There were issues with one or more added metamodel dependencies");
                builder.append("; unknown ").append((unknownDependencies.size() == 1) ? "dependency" : "dependencies").append(": ");
                unknownDependencies.sort(Comparator.naturalOrder());
                unknownDependencies.forEach(d -> d.appendDependencyString((d == unknownDependencies.get(0)) ? builder : builder.append(", ")));
                throw new LegendSDLCServerException(builder.toString(), Status.BAD_REQUEST);
            }
        }

        // validate that there are no conflicts between the metamodel dependencies
        if (updateMetamodelDependencies)
        {
            validateDependencyConflicts(
                    metamodelDependencies,
                    MetamodelDependency::getMetamodel,
                    (id, deps) -> (deps.size() > 1) ? deps.stream().collect(StringBuilder::new, (builder, dep) -> dep.appendVersionIdString(builder.append((builder.length() == 0) ? "multiple versions not allowed: " : ", ")), StringBuilder::append).toString() : null,
                    "metamodels");
        }


        boolean updateGeneration = false;

        Map<String, ArtifactGeneration> generationsByName = currentConfig.getArtifactGenerations().stream().collect(Collectors.toMap(ArtifactGeneration::getName, Function.identity()));

        if (updateBuilder.hasArtifactGenerationsToRemove())
        {
            updateGeneration = generationsByName.keySet().stream().anyMatch(updateBuilder.getArtifactGenerationToRemove()::contains);
            updateBuilder.getArtifactGenerationToRemove().forEach(generationsByName::remove);
        }

        if (updateBuilder.hasArtifactGenerationsToAdd())
        {
            validateArtifactGenerations(generationsByName, updateBuilder.getArtifactGenerationToAdd());

            updateGeneration = updateBuilder.getArtifactGenerationToAdd().stream().noneMatch(generationsByName.values()::contains);

            updateBuilder.getArtifactGenerationToAdd().forEach(art -> generationsByName.put(art.getName(), art));
        }

        // abort if there is no change to make
        if (!updateProjectStructureVersion && !updateProjectStructureExtensionVersion && !updateGroupId && !updateArtifactId && !updateProjectDependencies && !updateMetamodelDependencies && !updateGeneration)
        {
            return null;
        }

        // Collect operations
        List<ProjectFileOperation> operations = Lists.mutable.empty();

        // New configuration
        SimpleProjectConfiguration newConfig = new SimpleProjectConfiguration(currentConfig);
        if (updateProjectStructureVersion)
        {
            if (updateBuilder.hasProjectStructureExtensionVersion())
            {
                newConfig.setProjectStructureVersion(updateBuilder.getProjectStructureVersion(), updateBuilder.getProjectStructureExtensionVersion());
            }
            else if (updateBuilder.hasProjectStructureExtensionProvider())
            {
                newConfig.setProjectStructureVersion(updateBuilder.getProjectStructureVersion(), updateBuilder.getProjectStructureExtensionProvider().getLatestVersionForProjectStructureVersion(updateBuilder.getProjectStructureVersion()));
            }
            else
            {
                newConfig.setProjectStructureVersion(updateBuilder.getProjectStructureVersion(), null);
            }
        }
        else if (updateProjectStructureExtensionVersion)
        {
            newConfig.setProjectStructureVersion(currentConfig.getProjectStructureVersion().getVersion(), updateBuilder.getProjectStructureExtensionVersion());
        }
        if (updateGroupId)
        {
            newConfig.setGroupId(updateBuilder.getGroupId());
        }
        if (updateArtifactId)
        {
            newConfig.setArtifactId(updateBuilder.getArtifactId());
        }
        if (updateProjectDependencies)
        {
            List<ProjectDependency> projectDependencyList = Lists.mutable.withAll(projectDependencies);
            projectDependencyList.sort(Comparator.naturalOrder());
            newConfig.setProjectDependencies(projectDependencyList);
        }
        if (updateMetamodelDependencies)
        {
            List<MetamodelDependency> metamodelDependencyList = Lists.mutable.withAll(metamodelDependencies);
            metamodelDependencyList.sort(Comparator.naturalOrder());
            newConfig.setMetamodelDependencies(metamodelDependencyList);
        }
        if (updateGeneration)
        {
            List<ArtifactGeneration> artifactGenerationsList = Lists.mutable.withAll(generationsByName.values());
            artifactGenerationsList.sort(Comparator.comparing(ArtifactGeneration::getName));
            newConfig.setArtifactGeneration(artifactGenerationsList);
        }

        // prevent downgrading project
        if (newConfig.getProjectStructureVersion().compareTo(currentConfig.getProjectStructureVersion()) < 0)
        {
            throw new LegendSDLCServerException("Cannot change project " + projectId + " from project structure version " + currentConfig.getProjectStructureVersion().toVersionString() + " to version " + newConfig.getProjectStructureVersion().toVersionString(), Status.BAD_REQUEST);
        }

        String serializedNewConfig = serializeProjectConfiguration(newConfig);
        operations.add((configFile == null) ? ProjectFileOperation.addFile(PROJECT_CONFIG_PATH, serializedNewConfig) : ProjectFileOperation.modifyFile(PROJECT_CONFIG_PATH, serializedNewConfig));

        ProjectStructure currentProjectStructure = getProjectStructure(currentConfig);
        ProjectStructure newProjectStructure = getProjectStructure(newConfig);

        // Move entities if necessary
        String currentEntitiesDirectory = currentProjectStructure.getEntitiesDirectory();
        String newEntitiesDirectory = newProjectStructure.getEntitiesDirectory();
        if (!newEntitiesDirectory.equals(currentEntitiesDirectory))
        {
            fileAccessContext.getFilesInDirectory(currentProjectStructure.getEntitiesDirectory())
                    .forEach(file ->
                    {
                        String currentPath = file.getPath();
                        if (currentProjectStructure.isEntityFilePath(currentPath))
                        {
                            // move entities to new directory
                            String newPath = newEntitiesDirectory + currentPath.substring(currentEntitiesDirectory.length());
                            byte[] content = file.getContentAsBytes();
                            operations.add(ProjectFileOperation.moveFile(currentPath, newPath, content));
                        }
                    });
        }

        // Collect any further update operations
        newProjectStructure.collectUpdateProjectConfigurationOperations(currentProjectStructure, fileAccessContext, projectFileAccessProvider::getFileAccessContext, operations::add);

        // Collect update operations from any project structure extension
        if (updateBuilder.hasProjectStructureExtensionProvider() && (newConfig.getProjectStructureVersion().getExtensionVersion() != null))
        {
            ProjectStructureExtension projectStructureExtension = updateBuilder.getProjectStructureExtensionProvider().getProjectStructureExtension(newConfig.getProjectStructureVersion().getVersion(), newConfig.getProjectStructureVersion().getExtensionVersion());
            projectStructureExtension.collectUpdateProjectConfigurationOperations(currentConfig, newConfig, fileAccessContext, operations::add);
        }

        // Submit changes
        return projectFileAccessProvider.getFileModificationContext(projectId, workspaceId, revisionId, workspaceAccessType).submit(updateBuilder.getMessage(), operations);
    }


    protected static <T extends Dependency & Comparable<? super T>, K extends Comparable<? super K>> void validateDependencyConflicts(Collection<T> dependencies, Function<? super T, ? extends K> indexKeyFn, BiFunction<? super K, ? super SortedSet<T>, String> conflictFn, String description)
    {
        Map<K, SortedSet<T>> index = dependencies.stream().collect(Collectors.groupingBy(indexKeyFn, Collectors.toCollection(TreeSet::new)));
        SortedMap<K, String> conflictMessages = new TreeMap<>();
        index.forEach((key, deps) ->
        {
            String conflictMessage = conflictFn.apply(key, deps);
            if (conflictMessage != null)
            {
                conflictMessages.put(key, conflictMessage);
            }
        });
        if (!conflictMessages.isEmpty())
        {
            StringBuilder builder = new StringBuilder(conflictMessages.size() * 64);
            conflictMessages.forEach((key, message) -> ((builder.length() == 0) ? builder.append("The following ").append(description).append(" have conflicts: ") : builder.append(", ")).append(key).append(" (").append(message).append(')'));
            throw new LegendSDLCServerException(builder.toString(), Status.BAD_REQUEST);
        }
    }

    private static void appendPackageablePathAsFilePath(StringBuilder builder, String packageablePath)
    {
        int current = 0;
        int next = packageablePath.indexOf(PACKAGE_SEPARATOR);
        while (next != -1)
        {
            builder.append('/').append(packageablePath, current, next);
            current = next + 2;
            next = packageablePath.indexOf(PACKAGE_SEPARATOR, current);
        }
        builder.append('/').append(packageablePath, current, packageablePath.length());
    }

    private static void appendFilePathAsPackageablePath(StringBuilder builder, String filePath, int start, int end)
    {
        int current = start;
        int next = filePath.indexOf('/', current);
        while ((next != -1) && (next < end))
        {
            builder.append(filePath, current, next).append(PACKAGE_SEPARATOR);
            current = next + 1;
            next = filePath.indexOf('/', current);
        }
        builder.append(filePath, current, end);
    }

    private static String serializeProjectConfiguration(ProjectConfiguration projectConfiguration)
    {
        try
        {
            return JSON.writeValueAsString(projectConfiguration);
        }
        catch (Exception e)
        {
            StringBuilder message = new StringBuilder("Error creating project configuration file");
            String errorMessage = e.getMessage();
            if (errorMessage != null)
            {
                message.append(": ").append(errorMessage);
            }
            throw new RuntimeException(message.toString(), e);
        }
    }

    private static ProjectFile getProjectConfigurationFile(FileAccessContext accessContext)
    {
        return accessContext.getFile(PROJECT_CONFIG_PATH);
    }

    private static ProjectConfiguration readProjectConfiguration(ProjectFile file)
    {
        try (Reader reader = file.getContentAsReader())
        {
            return JSON.readValue(reader, SimpleProjectConfiguration.class);
        }
        catch (Exception e)
        {
            StringBuilder message = new StringBuilder("Error reading project configuration");
            String errorMessage = e.getMessage();
            if (errorMessage != null)
            {
                message.append(": ").append(errorMessage);
            }
            throw new RuntimeException(message.toString(), e);
        }
    }

    private static boolean isKnownMetamodel(MetamodelDependency metamodelDependency)
    {
        // This is a hack until we have a proper metamodel registry
        String metamodel = metamodelDependency.getMetamodel();
        int version = metamodelDependency.getVersion();
        if (metamodel == null)
        {
            return false;
        }
        switch (metamodel)
        {
            case "pure":
            {
                return (version == 0) || (version == 1);
            }
            case "lineage":
            case "service":
            case "tds":
            {
                return version == 1;
            }
            default:
            {
                return false;
            }
        }
    }

    public List<ArtifactTypeGenerationConfiguration> getAvailableGenerationConfigurations()
    {
        // None by default
        return Collections.emptyList();
    }

    private static void validateArtifactGenerations(Map<String, ArtifactGeneration> artifactGenerations, List<ArtifactGeneration> artifactGenerationToAdd)
    {
        boolean isValid = true;
        StringBuilder builder = new StringBuilder("There were issues with one or more added artifact generations");

        if (artifactGenerationToAdd.stream().map(ArtifactGeneration::getType).anyMatch(FORBIDDEN_ARTIFACT_GENERATION_TYPES::contains))
        {
            isValid = false;
            builder.append(FORBIDDEN_ARTIFACT_GENERATION_TYPES.stream().map(ArtifactType::getLabel).sorted().collect(Collectors.joining(", ", ": generation types ", " are not allowed")));
        }
        if (artifactGenerationToAdd.stream().map(ArtifactGeneration::getName).anyMatch(artifactGenerations::containsKey))
        {
            isValid = false;
            builder.append(": trying to add duplicate artifact generations");
        }
        if (artifactGenerationToAdd.stream().map(ArtifactGeneration::getName).distinct().count() != artifactGenerationToAdd.size())
        {
            isValid = false;
            builder.append(": generations to add contain duplicates");
        }

        if (!isValid)
        {
            throw new LegendSDLCServerException(builder.toString(), Status.BAD_REQUEST);
        }
    }
}
