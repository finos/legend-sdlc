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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.list.mutable.ListAdapter;
import org.eclipse.collections.impl.tuple.Tuples;
import org.eclipse.collections.impl.utility.Iterate;
import org.eclipse.collections.impl.utility.LazyIterate;
import org.eclipse.collections.impl.utility.ListIterate;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactGeneration;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactType;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactTypeGenerationConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.MetamodelDependency;
import org.finos.legend.sdlc.domain.model.project.configuration.PlatformConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectStructureVersion;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.serialization.EntitySerializer;
import org.finos.legend.sdlc.serialization.EntitySerializers;
import org.finos.legend.sdlc.server.domain.api.project.ProjectConfigurationUpdater;
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSpecification;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider.FileAccessContext;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider.ProjectFile;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider.WorkspaceAccessType;
import org.finos.legend.sdlc.server.project.extension.ProjectStructureExtension;
import org.finos.legend.sdlc.server.project.extension.ProjectStructureExtensionProvider;
import org.finos.legend.sdlc.server.tools.StringTools;
import org.finos.legend.sdlc.tools.entity.EntityPaths;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.lang.model.SourceVersion;
import javax.ws.rs.core.Response.Status;

public abstract class ProjectStructure
{
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .disable(StreamWriteFeature.AUTO_CLOSE_TARGET)
            .disable(StreamReadFeature.AUTO_CLOSE_SOURCE)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .build();

    private static final ProjectStructureFactory PROJECT_STRUCTURE_FACTORY = ProjectStructureFactory.newFactory(ProjectStructure.class.getClassLoader());

    private static final Pattern VALID_ARTIFACT_ID_PATTERN = Pattern.compile("[a-z][a-z\\d_]*+(-[a-z][a-z\\d_]*+)*+");
    private static final Pattern STRICT_VERSION_ID_PATTERN = Pattern.compile("((0|([1-9]\\d*+))\\.){2}(0|([1-9]\\d*+))");

    public static final String PROJECT_CONFIG_PATH = "/project.json";

    private static final Set<ArtifactType> FORBIDDEN_ARTIFACT_GENERATION_TYPES = Collections.unmodifiableSet(EnumSet.of(ArtifactType.entities, ArtifactType.versioned_entities, ArtifactType.service_execution));

    private final ProjectConfiguration projectConfiguration;
    private final MutableList<EntitySourceDirectory> entitySourceDirectories;

    protected ProjectStructure(ProjectConfiguration projectConfiguration, MutableList<EntitySourceDirectory> entitySourceDirectories)
    {
        this.projectConfiguration = projectConfiguration;
        this.entitySourceDirectories = entitySourceDirectories.asUnmodifiable();
    }

    protected ProjectStructure(ProjectConfiguration projectConfiguration, List<EntitySourceDirectory> entitySourceDirectories)
    {
        this(projectConfiguration, ListAdapter.adapt(entitySourceDirectories));
    }

    protected ProjectStructure(ProjectConfiguration projectConfiguration, EntitySourceDirectory entitySourceDirectory)
    {
        this(projectConfiguration, Lists.fixedSize.with(entitySourceDirectory));
    }

    protected ProjectStructure(ProjectConfiguration projectConfiguration, String entitiesDirectory, EntitySerializer entitySerializer)
    {
        this(projectConfiguration, newEntitySourceDirectory(entitiesDirectory, entitySerializer));
    }

    protected ProjectStructure(ProjectConfiguration projectConfiguration, String entitiesDirectory)
    {
        this(projectConfiguration, entitiesDirectory, EntitySerializers.getDefaultJsonSerializer());
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

    public List<EntitySourceDirectory> getEntitySourceDirectories()
    {
        return this.entitySourceDirectories;
    }

    /**
     * Find the path for the file that represents the given entity.
     *
     * @param entityPath        entity path
     * @param fileAccessContext file access context
     * @return entity file path, if it exists
     */
    public String findEntityFile(String entityPath, FileAccessContext fileAccessContext)
    {
        for (EntitySourceDirectory sourceDirectory : this.entitySourceDirectories)
        {
            String filePath = sourceDirectory.entityPathToFilePath(entityPath);
            if (fileAccessContext.fileExists(filePath))
            {
                return filePath;
            }
        }
        return null;
    }

    /**
     * Find the first source directory where the given entity can be serialized.
     *
     * @param entity entity to serialize
     * @return source directory where the entity can be serialized
     */
    public EntitySourceDirectory findSourceDirectoryForEntity(Entity entity)
    {
        return this.entitySourceDirectories.detectWith(EntitySourceDirectory::canSerialize, entity);
    }

    /**
     * Find the first source directory where the given file is possibly an entity file.
     *
     * @param path entity file path
     * @return source directory where the file is possibly an entity file
     */
    public EntitySourceDirectory findSourceDirectoryForEntityFilePath(String path)
    {
        return this.entitySourceDirectories.detectWith(EntitySourceDirectory::isPossiblyEntityFilePath, path);
    }

    @Deprecated
    public void collectUpdateProjectConfigurationOperations(ProjectStructure oldStructure, FileAccessContext fileAccessContext, BiFunction<String, VersionId, FileAccessContext> versionFileAccessContextProvider, Consumer<ProjectFileOperation> operationConsumer)
    {
        // retained for backward compatibility
    }

    protected void collectUpdateProjectConfigurationOperations(ProjectStructure oldStructure, FileAccessContext fileAccessContext, Consumer<ProjectFileOperation> operationConsumer)
    {
        // Nothing by default
    }

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
            MutableList<ArtifactGeneration> unsupportedGenerations = Iterate.select(projectConfig.getArtifactGenerations(), g -> !isSupportedArtifactType(g.getType()), Lists.mutable.empty());
            if (unsupportedGenerations.notEmpty())
            {
                StringBuilder builder = new StringBuilder("Unsupported artifact generations: ");
                unsupportedGenerations.sortThis(Comparator.comparing(ArtifactGeneration::getName))
                        .forEachWithIndex((g, i) -> ((i == 0) ? builder : builder.append(", ")).append(g.getName()).append(" (").append(g.getType()).append(")"));
                throw new IllegalStateException(builder.toString());
            }
        }
    }

    public static int getLatestProjectStructureVersion()
    {
        return PROJECT_STRUCTURE_FACTORY.getLatestVersion();
    }

    public static ProjectStructure getProjectStructure(String projectId, SourceSpecification sourceSpecification, String revisionId, ProjectFileAccessProvider projectFileAccessor)
    {
        return getProjectStructure(projectFileAccessor.getFileAccessContext(projectId, sourceSpecification, revisionId));
    }

    public static ProjectStructure getProjectStructure(FileAccessContext fileAccessContext)
    {
        ProjectConfiguration config = getProjectConfiguration(fileAccessContext);
        return getProjectStructure(config);
    }

    public static ProjectStructure getProjectStructure(ProjectConfiguration projectConfiguration)
    {
        return getProjectStructure(projectConfiguration, null);
    }

    public static ProjectStructure getProjectStructure(ProjectConfiguration projectConfiguration, ProjectStructurePlatformExtensions projectStructurePlatformExtensions)
    {
        return PROJECT_STRUCTURE_FACTORY.newProjectStructure(projectConfiguration, projectStructurePlatformExtensions);
    }

    // for backward compatibility
    @Deprecated
    public static ProjectConfiguration getProjectConfiguration(String projectId, String workspaceId, String revisionId, ProjectFileAccessProvider projectFileAccessProvider, WorkspaceAccessType workspaceAccessType)
    {
        return getProjectConfiguration(projectId, SourceSpecification.newSourceSpecification(workspaceId, WorkspaceType.USER, workspaceAccessType), revisionId, projectFileAccessProvider);
    }

    public static ProjectConfiguration getProjectConfiguration(String projectId, SourceSpecification sourceSpecification, String revisionId, ProjectFileAccessProvider projectFileAccessProvider)
    {
        return getProjectConfiguration(projectFileAccessProvider.getFileAccessContext(projectId, sourceSpecification, revisionId));
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

    public static ProjectConfiguration getDefaultProjectConfiguration(String projectId)
    {
        return SimpleProjectConfiguration.newConfiguration(projectId, ProjectStructureVersion.newProjectStructureVersion(0), null, null, null, null, null);
    }

    public static boolean isValidProjectType(ProjectType projectType)
    {
        return projectType == ProjectType.MANAGED || projectType == ProjectType.EMBEDDED;
    }

    public static boolean isValidGroupId(String groupId)
    {
        return (groupId != null) && !groupId.isEmpty() && SourceVersion.isName(groupId);
    }

    public static boolean isValidArtifactId(String artifactId)
    {
        return (artifactId != null) && !artifactId.isEmpty() && VALID_ARTIFACT_ID_PATTERN.matcher(artifactId).matches();
    }

    public static boolean isProperProjectDependency(ProjectDependency dependency)
    {
        return (dependency != null) && isValidProjectDependencyProjectId(dependency.getProjectId()) && isStrictVersionId(dependency.getVersionId());
    }

    private static boolean isValidProjectDependencyProjectId(String projectId)
    {
        return (projectId != null) && projectId.codePoints().anyMatch(c -> !Character.isWhitespace(c));
    }

    public static boolean isStrictVersionId(String versionId)
    {
        return (versionId != null) && (versionId.length() >= 5) && STRICT_VERSION_ID_PATTERN.matcher(versionId).matches();
    }

    private static Revision updateProjectConfiguration(UpdateBuilder updateBuilder, boolean requireRevisionId)
    {
        String projectId = updateBuilder.getProjectId();
        String workspaceId = updateBuilder.getSourceSpecification().getWorkspaceId();
        VersionId patchReleaseVersionId = updateBuilder.getSourceSpecification().getPatchReleaseVersionId();
        WorkspaceType workspaceType = updateBuilder.getSourceSpecification().getWorkspaceType();
        WorkspaceAccessType workspaceAccessType = updateBuilder.getSourceSpecification().getWorkspaceAccessType();
        ProjectFileAccessProvider projectFileAccessProvider = updateBuilder.getProjectFileAccessProvider();

        String revisionId;
        if (updateBuilder.getRevisionId() == null)
        {
            // if revisionId not specified, get the current revision
            Revision currentRevision = projectFileAccessProvider.getRevisionAccessContext(projectId, workspaceId, workspaceType, workspaceAccessType).getCurrentRevision();
            if (currentRevision != null)
            {
                revisionId = currentRevision.getId();
            }
            else if (requireRevisionId)
            {
                StringBuilder builder = new StringBuilder("Could not find current revision for ");
                if (workspaceId != null)
                {
                    builder.append(workspaceType.getLabel()).append(" ").append(workspaceAccessType.getLabel()).append(" ").append(workspaceId).append("in ");
                }
                builder.append("project ").append(projectId).append(": it may be corrupt");
                throw new LegendSDLCServerException(builder.toString());
            }
            else
            {
                revisionId = null;
            }
        }
        else
        {
            revisionId = updateBuilder.getRevisionId();
        }

        FileAccessContext fileAccessContext = CachingFileAccessContext.wrap(projectFileAccessProvider.getFileAccessContext(projectId, SourceSpecification.newSourceSpecification(workspaceId, workspaceType, workspaceAccessType, patchReleaseVersionId), revisionId));

        ProjectFile configFile = getProjectConfigurationFile(fileAccessContext);
        ProjectConfiguration currentConfig = (configFile == null) ? getDefaultProjectConfiguration(projectId) : readProjectConfiguration(configFile);
        ProjectType oldProjectType = currentConfig.getProjectType();
        // Upgrade old project types
        if (!ProjectStructure.isValidProjectType(oldProjectType))
        {
            oldProjectType = ProjectType.MANAGED;
            if (updateBuilder.configUpdater.getProjectType() == null)
            {
                updateBuilder.configUpdater.setProjectType(oldProjectType);
            }
        }
        ProjectType newProjectType = updateBuilder.configUpdater.getProjectType() == null ? oldProjectType : updateBuilder.configUpdater.getProjectType();
        // For MANAGED projects
        if (updateBuilder.projectStructureExtensionProvider != null && newProjectType != ProjectType.EMBEDDED)
        {
            // converted from EMBEDDED set version
            if (oldProjectType == ProjectType.EMBEDDED && updateBuilder.configUpdater.getProjectStructureVersion() == null)
            {
                updateBuilder.configUpdater.setProjectStructureVersion(currentConfig.getProjectStructureVersion().getVersion());
            }
            // if extension version was not specified, use the latest one for given version
            if (updateBuilder.configUpdater.getProjectStructureVersion() != null && updateBuilder.configUpdater.getProjectStructureExtensionVersion() == null)
            {
                updateBuilder.configUpdater.setProjectStructureExtensionVersion(updateBuilder.projectStructureExtensionProvider.getLatestVersionForProjectStructureVersion(updateBuilder.configUpdater.getProjectStructureVersion()));
            }
        }
        ProjectConfiguration newConfig = updateLegacyDependencies(updateBuilder.getProjectConfigurationUpdater().update(currentConfig), projectFileAccessProvider);

        validateProjectConfiguration(newConfig);

        ProjectStructureVersion oldProjectVersion = currentConfig.getProjectStructureVersion();
        // prevent extensions on non-managed projects
        if (newConfig.getProjectType() == ProjectType.EMBEDDED)
        {
            if (newConfig.getProjectStructureVersion().getExtensionVersion() != null)
            {
                throw new LegendSDLCServerException("Cannot set extensions on project " + projectId + " with " + newConfig.getProjectType() + " type", Status.BAD_REQUEST);
            }
            oldProjectVersion = ProjectStructureVersion.newProjectStructureVersion(oldProjectVersion.getVersion());
        }
        // prevent downgrading project
        if (newConfig.getProjectStructureVersion().compareTo(oldProjectVersion) < 0)
        {
            throw new LegendSDLCServerException("Cannot change project " + projectId + " from project structure version " + currentConfig.getProjectStructureVersion().toVersionString() + " to version " + newConfig.getProjectStructureVersion().toVersionString(), Status.BAD_REQUEST);
        }

        // Serialize new configuration and check if it differs from the old
        byte[] serializedNewConfig = serializeProjectConfiguration(newConfig);
        if ((configFile != null) && Arrays.equals(serializedNewConfig, configFile.getContentAsBytes()))
        {
            // new configuration file is the same as the old
            return null;
        }

        List<ProjectFileOperation> operations = Lists.mutable.empty();
        operations.add((configFile == null) ? ProjectFileOperation.addFile(PROJECT_CONFIG_PATH, serializedNewConfig) : ProjectFileOperation.modifyFile(PROJECT_CONFIG_PATH, serializedNewConfig));

        ProjectStructure currentProjectStructure = getProjectStructure(currentConfig, updateBuilder.getProjectStructurePlatformExtensions());
        ProjectStructure newProjectStructure = getProjectStructure(newConfig, updateBuilder.getProjectStructurePlatformExtensions());

        // Move or re-serialize entities if necessary
        List<EntitySourceDirectory> currentEntityDirectories = currentProjectStructure.getEntitySourceDirectories();
        List<EntitySourceDirectory> newEntityDirectories = newProjectStructure.getEntitySourceDirectories();
        if (!currentEntityDirectories.equals(newEntityDirectories))
        {
            currentEntityDirectories.forEach(currentSourceDirectory -> fileAccessContext.getFilesInDirectory(currentSourceDirectory.getDirectory()).forEach(file ->
            {
                String currentPath = file.getPath();
                if (currentSourceDirectory.isPossiblyEntityFilePath(currentPath))
                {
                    byte[] currentBytes = file.getContentAsBytes();
                    Entity entity;
                    try
                    {
                        entity = currentSourceDirectory.deserialize(currentBytes);
                    }
                    catch (Exception e)
                    {
                        StringBuilder builder = new StringBuilder("Error deserializing entity from file \"").append(currentPath).append('"');
                        StringTools.appendThrowableMessageIfPresent(builder, e);
                        throw new LegendSDLCServerException(builder.toString(), e);
                    }
                    EntitySourceDirectory newSourceDirectory = Iterate.detectWith(newEntityDirectories, EntitySourceDirectory::canSerialize, entity);
                    if (newSourceDirectory == null)
                    {
                        throw new LegendSDLCServerException("Could not find a new source directory for entity " + entity.getPath() + ", currently in " + currentPath);
                    }
                    if (!currentSourceDirectory.equals(newSourceDirectory))
                    {
                        String newPath = newSourceDirectory.entityPathToFilePath(entity.getPath());
                        byte[] newBytes = newSourceDirectory.serializeToBytes(entity);
                        if (!newPath.equals(currentPath))
                        {
                            operations.add(ProjectFileOperation.moveFile(currentPath, newPath, newBytes));
                        }
                        else if (!Arrays.equals(currentBytes, newBytes))
                        {
                            operations.add(ProjectFileOperation.modifyFile(currentPath, newBytes));
                        }
                    }
                }
            }));
        }

        // Collect any further update operations
        if (newConfig.getProjectType() != ProjectType.EMBEDDED)
        {
            newProjectStructure.collectUpdateProjectConfigurationOperations(currentProjectStructure, fileAccessContext, operations::add);
        }

        // Call legacy method
        newProjectStructure.collectUpdateProjectConfigurationOperations(currentProjectStructure, fileAccessContext, (x, y) ->
        {
            throw new UnsupportedOperationException();
        }, operations::add);

        // Collect update operations from any project structure extension
        if (newConfig.getProjectStructureVersion().getExtensionVersion() != null)
        {
            ProjectStructureExtensionProvider extensionProvider = updateBuilder.getProjectStructureExtensionProvider();
            if (extensionProvider != null)
            {
                ProjectStructureExtension projectStructureExtension = extensionProvider.getProjectStructureExtension(newConfig.getProjectStructureVersion().getVersion(), newConfig.getProjectStructureVersion().getExtensionVersion());
                projectStructureExtension.collectUpdateProjectConfigurationOperations(currentConfig, newConfig, fileAccessContext, operations::add);
            }
        }

        // Submit changes
        return projectFileAccessProvider.getFileModificationContext(projectId, workspaceId, workspaceType, workspaceAccessType, revisionId).submit(updateBuilder.getMessage(), operations);
    }

    private static void validateProjectConfiguration(ProjectConfiguration config)
    {
        // Group id
        if (!isValidGroupId(config.getGroupId()))
        {
            throw new LegendSDLCServerException("Invalid groupId: " + config.getGroupId(), Status.BAD_REQUEST);
        }

        // Artifact id
        if (!isValidArtifactId(config.getArtifactId()))
        {
            throw new LegendSDLCServerException("Invalid artifactId: " + config.getArtifactId(), Status.BAD_REQUEST);
        }

        // Project type
        if (!isValidProjectType(config.getProjectType()))
        {
            throw new LegendSDLCServerException("Invalid projectType: " + config.getArtifactId(), Status.BAD_REQUEST);
        }

        // Platform configurations
        if (config.getPlatformConfigurations() != null)
        {
            MutableSet<String> platformNames = Sets.mutable.empty();
            MutableSet<String> platformNameConflicts = LazyIterate.collect(config.getPlatformConfigurations(), PlatformConfiguration::getName).reject(platformNames::add, Sets.mutable.empty());
            if (platformNameConflicts.notEmpty())
            {
                throw new LegendSDLCServerException(platformNameConflicts.toSortedList().makeString("Platform configuration conflicts: \"", "\", \"", "\""), Status.BAD_REQUEST);
            }
        }

        // Metamodel dependencies
        validateMetamodelDependencies(config.getMetamodelDependencies());

        // Artifact generations
        validateArtifactGenerations(config.getArtifactGenerations());
    }

    private static void validateMetamodelDependencies(List<MetamodelDependency> metamodelDependencies)
    {
        MutableList<MetamodelDependency> unknownDependencies = ListIterate.reject(metamodelDependencies, ProjectStructure::isKnownMetamodel);
        if (unknownDependencies.notEmpty())
        {
            StringBuilder builder = new StringBuilder("There were issues with one or more added metamodel dependencies");
            builder.append("; unknown ").append((unknownDependencies.size() == 1) ? "dependency" : "dependencies").append(": ");
            unknownDependencies.sort(getMetamodelDependencyComparator());
            unknownDependencies.forEach(d -> d.appendDependencyString((d == unknownDependencies.get(0)) ? builder : builder.append(", ")));
            throw new LegendSDLCServerException(builder.toString(), Status.BAD_REQUEST);
        }
        validateDependencyConflicts(
                metamodelDependencies,
                MetamodelDependency::getMetamodel,
                (id, deps) -> (deps.size() > 1) ? deps.stream().collect(StringBuilder::new, (builder, dep) -> dep.appendVersionIdString(builder.append((builder.length() == 0) ? "multiple versions not allowed: " : ", ")), StringBuilder::append).toString() : null,
                "metamodels");
    }

    private static void validateArtifactGenerations(List<ArtifactGeneration> artifactGenerations)
    {
        String initString = "There were issues with one or more added artifact generations";
        StringBuilder builder = null;

        if (Iterate.anySatisfy(artifactGenerations, g -> FORBIDDEN_ARTIFACT_GENERATION_TYPES.contains(g.getType())))
        {
            builder = new StringBuilder(initString);
            LazyIterate.collect(FORBIDDEN_ARTIFACT_GENERATION_TYPES, ArtifactType::getLabel)
                    .toSortedList()
                    .appendString(builder, ": generation types ", ", ", " are not allowed");
        }

        MutableSet<String> artifactGenerationNames = Sets.mutable.empty();
        MutableSet<String> artifactGenerationConflicts = LazyIterate.collect(artifactGenerations, ArtifactGeneration::getName).reject(artifactGenerationNames::add, Sets.mutable.empty());
        if (artifactGenerationConflicts.notEmpty())
        {
            if (builder == null)
            {
                builder = new StringBuilder(initString).append(": ");
            }
            else
            {
                builder.append("; ");
            }
            artifactGenerationConflicts.toSortedList().appendString(builder, "duplicate generations: \"", "\", \"", "\"");
        }

        if (builder != null)
        {
            throw new LegendSDLCServerException(builder.toString(), Status.BAD_REQUEST);
        }
    }

    protected static boolean isLegacyProjectDependency(ProjectDependency projectDependency)
    {
        return (projectDependency != null) &&
                (projectDependency.getProjectId() != null) &&
                (projectDependency.getProjectId().indexOf(':') == -1);
    }

    private static ProjectConfiguration updateLegacyDependencies(ProjectConfiguration config, ProjectFileAccessProvider projectFileAccessProvider)
    {
        MutableList<ProjectDependency> legacyProjectDependencies = Iterate.select(config.getProjectDependencies(), ProjectStructure::isLegacyProjectDependency, Lists.mutable.empty());
        if (legacyProjectDependencies.isEmpty())
        {
            return config;
        }

        MutableSet<ProjectDependency> projectDependencies = Sets.mutable.withAll(config.getProjectDependencies());
        MutableList<ProjectDependency> unknownDependencies = Lists.mutable.empty();
        MutableList<Pair<ProjectDependency, Exception>> accessExceptions = Lists.mutable.empty();
        legacyProjectDependencies.forEach(dep ->
        {
            try
            {
                ProjectConfiguration dependencyConfig = getProjectConfiguration(projectFileAccessProvider.getFileAccessContext(dep.getProjectId(), VersionId.parseVersionId(dep.getVersionId())));
                if ((dependencyConfig == null) || (dependencyConfig.getArtifactId() == null) || (dependencyConfig.getGroupId() == null))
                {
                    unknownDependencies.add(dep);
                }
                else
                {
                    projectDependencies.remove(dep);
                    projectDependencies.add(ProjectDependency.newProjectDependency(dependencyConfig.getGroupId() + ":" + dependencyConfig.getArtifactId(), dep.getVersionId()));
                }
            }
            catch (Exception e)
            {
                accessExceptions.add(Tuples.pair(dep, e));
            }
        });
        Comparator<ProjectDependency> comparator = getProjectDependencyComparator();
        if (unknownDependencies.notEmpty() || accessExceptions.notEmpty())
        {
            StringBuilder builder = new StringBuilder("There were issues with one or more legacy project dependencies");
            if (unknownDependencies.notEmpty())
            {
                builder.append("; unknown ").append((unknownDependencies.size() == 1) ? "dependency" : "dependencies").append(": ");
                unknownDependencies.sortThis(comparator).forEach(d -> d.appendDependencyString((d == unknownDependencies.get(0)) ? builder : builder.append(", ")));
            }
            if (accessExceptions.notEmpty())
            {
                builder.append("; access ").append((accessExceptions.size() == 1) ? "exception" : "exceptions").append(": ");
                accessExceptions.sortThis(Comparator.comparing(Pair::getOne, comparator)).forEach(p -> p.getOne().appendDependencyString((p == accessExceptions.get(0)) ? builder : builder.append(", ")).append(" (").append(p.getTwo().getMessage()).append(')'));
            }
            throw new LegendSDLCServerException(builder.toString(), Status.BAD_REQUEST);
        }

        SimpleProjectConfiguration newConfig = new SimpleProjectConfiguration(config);
        newConfig.setProjectDependencies(projectDependencies.toSortedList(comparator));
        return newConfig;
    }

    protected static Comparator<ProjectDependency> getProjectDependencyComparator()
    {
        return ProjectDependency.getDefaultComparator();
    }

    protected static Comparator<MetamodelDependency> getMetamodelDependencyComparator()
    {
        return MetamodelDependency.getDefaultComparator();
    }

    protected static <T, K extends Comparable<? super K>> void validateDependencyConflicts(Collection<T> dependencies, Function<? super T, ? extends K> indexKeyFn, BiFunction<? super K, ? super Set<T>, String> conflictFn, String description)
    {
        MutableMap<K, MutableSet<T>> index = Maps.mutable.empty();
        dependencies.forEach(dep -> index.getIfAbsentPut(indexKeyFn.apply(dep), Sets.mutable::empty).add(dep));
        SortedMap<K, String> conflictMessages = new TreeMap<>();
        index.forEachKeyValue((key, deps) ->
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

    private static ProjectFile getProjectConfigurationFile(FileAccessContext accessContext)
    {
        return accessContext.getFile(PROJECT_CONFIG_PATH);
    }

    private static byte[] serializeProjectConfiguration(ProjectConfiguration projectConfiguration)
    {
        try
        {
            return JSON.writeValueAsBytes(projectConfiguration);
        }
        catch (Exception e)
        {
            throw new RuntimeException(StringTools.appendThrowableMessageIfPresent("Error creating project configuration file", e), e);
        }
    }

    private static ProjectConfiguration readProjectConfiguration(ProjectFile file)
    {
        try (InputStream stream = file.getContentAsInputStream())
        {
            return JSON.readValue(stream, SimpleProjectConfiguration.class);
        }
        catch (Exception e)
        {
            throw new RuntimeException(StringTools.appendThrowableMessageIfPresent("Error reading project configuration", e), e);
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

    protected static EntitySourceDirectory newEntitySourceDirectory(String directory, EntitySerializer serializer)
    {
        return new EntitySourceDirectory(directory, serializer);
    }

    public static class EntitySourceDirectory
    {
        private final String directory;
        private final EntitySerializer serializer;

        private EntitySourceDirectory(String directory, EntitySerializer serializer)
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
                throw new LegendSDLCServerException(message.toString(), e);
            }
        }

        public Entity deserialize(ProjectFile projectFile)
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
                throw new LegendSDLCServerException(builder.toString(), e);
            }
        }

        public Entity deserialize(byte[] content) throws IOException
        {
            return this.serializer.deserialize(content);
        }
    }

    public static UpdateBuilder newUpdateBuilder(ProjectFileAccessProvider projectFileAccessProvider, String projectId)
    {
        return newUpdateBuilder(projectFileAccessProvider, projectId, null);
    }

    public static UpdateBuilder newUpdateBuilder(ProjectFileAccessProvider projectFileAccessProvider, String projectId, ProjectConfigurationUpdater configUpdater)
    {
        return new UpdateBuilder(projectFileAccessProvider, projectId, configUpdater);
    }

    public static class UpdateBuilder
    {
        private final ProjectFileAccessProvider projectFileAccessProvider;
        private final String projectId;
        private SourceSpecification sourceSpecification;
        private ProjectConfigurationUpdater configUpdater;
        private String revisionId;
        private String message;
        private ProjectStructureExtensionProvider projectStructureExtensionProvider;
        private ProjectStructurePlatformExtensions projectStructurePlatformExtensions;

        private UpdateBuilder(ProjectFileAccessProvider projectFileAccessProvider, String projectId, ProjectConfigurationUpdater configUpdater)
        {
            this.projectFileAccessProvider = projectFileAccessProvider;
            this.projectId = projectId;
            this.sourceSpecification = SourceSpecification.projectSourceSpecification();
            this.configUpdater = (configUpdater == null) ? getDefaultProjectConfigurationUpdater() : configUpdater;
        }

        // Project id

        public String getProjectId()
        {
            return this.projectId;
        }

        public SourceSpecification getSourceSpecification()
        {
            return this.sourceSpecification;
        }

        // Project file access provider

        public ProjectFileAccessProvider getProjectFileAccessProvider()
        {
            return this.projectFileAccessProvider;
        }

        // Project configuration updater

        public ProjectConfigurationUpdater getProjectConfigurationUpdater()
        {
            return this.configUpdater;
        }

        public void setProjectConfigurationUpdater(ProjectConfigurationUpdater updater)
        {
            this.configUpdater = (updater == null) ? getDefaultProjectConfigurationUpdater() : updater;
        }

        public UpdateBuilder withProjectConfigurationUpdater(ProjectConfigurationUpdater updater)
        {
            setProjectConfigurationUpdater(updater);
            return this;
        }

        private ProjectConfigurationUpdater getDefaultProjectConfigurationUpdater()
        {
            return ProjectConfigurationUpdater.newUpdater().withProjectId(this.projectId);
        }

        // Source specification

        public void setSourceSpecification(SourceSpecification sourceSpec)
        {
            this.sourceSpecification = sourceSpec;
        }

        public UpdateBuilder withSourceSpecification(SourceSpecification sourceSpec)
        {
            setSourceSpecification(sourceSpec);
            return this;
        }

        public UpdateBuilder withWorkspace(WorkspaceSpecification workspaceSpec)
        {
            return withSourceSpecification(SourceSpecification.workspaceSourceSpecification(workspaceSpec));
        }

        @Deprecated
        public UpdateBuilder withWorkspace(SourceSpecification sourceSpecification)
        {
            return withSourceSpecification(sourceSpecification);
        }

        // Revision id

        public String getRevisionId()
        {
            return this.revisionId;
        }

        public void setRevisionId(String revisionId)
        {
            this.revisionId = revisionId;
        }

        public UpdateBuilder withRevisionId(String revisionId)
        {
            setRevisionId(revisionId);
            return this;
        }

        // Message

        public String getMessage()
        {
            return this.message;
        }

        public void setMessage(String message)
        {
            this.message = message;
        }

        public UpdateBuilder withMessage(String message)
        {
            setMessage(message);
            return this;
        }

        // Project structure extension provider

        public ProjectStructureExtensionProvider getProjectStructureExtensionProvider()
        {
            return this.projectStructureExtensionProvider;
        }

        public void setProjectStructureExtensionProvider(ProjectStructureExtensionProvider projectStructureExtensionProvider)
        {
            this.projectStructureExtensionProvider = projectStructureExtensionProvider;
        }

        public UpdateBuilder withProjectStructureExtensionProvider(ProjectStructureExtensionProvider projectStructureExtensionProvider)
        {
            setProjectStructureExtensionProvider(projectStructureExtensionProvider);
            return this;
        }

        // Project structure platform extensions

        public ProjectStructurePlatformExtensions getProjectStructurePlatformExtensions()
        {
            return this.projectStructurePlatformExtensions;
        }

        public void setProjectStructurePlatformExtensions(ProjectStructurePlatformExtensions projectStructurePlatformExtensions)
        {
            this.projectStructurePlatformExtensions = projectStructurePlatformExtensions;
        }

        public UpdateBuilder withProjectStructurePlatformExtensions(ProjectStructurePlatformExtensions projectStructurePlatformExtensions)
        {
            setProjectStructurePlatformExtensions(projectStructurePlatformExtensions);
            return this;
        }

        // Update

        public Revision update()
        {
            return update(true);
        }

        public Revision build()
        {
            if (this.configUpdater.getProjectStructureVersion() == null)
            {
                this.configUpdater.setProjectStructureVersion(getLatestProjectStructureVersion());
            }
            return update(false);
        }

        private Revision update(boolean requireRevisionId)
        {
            if (this.projectId != null)
            {
                if (this.configUpdater.getProjectId() == null)
                {
                    this.configUpdater.setProjectId(this.projectId);
                }
                else if (!this.projectId.equals(this.configUpdater.getProjectId()))
                {
                    throw new IllegalArgumentException("Conflicting project ids: \"" + this.projectId + "\" vs \"" + this.configUpdater.getProjectId() + "\"");
                }
            }
            else if (this.configUpdater.getProjectId() == null)
            {
                throw new IllegalArgumentException("No project id specified");
            }
            if (this.projectStructureExtensionProvider == null && this.configUpdater.getProjectStructureExtensionVersion() != null)
            {
                    throw new IllegalArgumentException("Project structure extension version specified (" + this.configUpdater.getProjectStructureExtensionVersion() + ") with no project structure extension provider");
            }
            return updateProjectConfiguration(this, requireRevisionId);
        }
    }
}
