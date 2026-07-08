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

package org.finos.legend.sdlc.project.structure;

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
import org.eclipse.collections.impl.list.mutable.ListAdapter;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactGeneration;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactType;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactTypeGenerationConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.MetamodelDependency;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectStructureVersion;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.serialization.EntitySerializer;
import org.finos.legend.sdlc.serialization.EntitySerializers;
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSpecification;
import org.finos.legend.sdlc.error.LegendSDLCException;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider.FileAccessContext;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider.ProjectFile;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider.WorkspaceAccessType;
import org.finos.legend.sdlc.server.project.ProjectFileOperation;
import org.finos.legend.sdlc.tools.StringTools;

import javax.lang.model.SourceVersion;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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

    public void collectUpdateProjectConfigurationOperations(ProjectStructure oldStructure, FileAccessContext fileAccessContext, Consumer<ProjectFileOperation> operationConsumer)
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
        return getProjectConfiguration(projectId, SourceSpecification.workspaceSourceSpecification(WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.USER, workspaceAccessType)), revisionId, projectFileAccessProvider);
    }

    public static ProjectConfiguration getProjectConfiguration(String projectId, SourceSpecification sourceSpecification, String revisionId, ProjectFileAccessProvider projectFileAccessProvider)
    {
        return getProjectConfiguration(projectFileAccessProvider.getFileAccessContext(projectId, sourceSpecification, revisionId));
    }

    public static ProjectConfiguration getProjectConfiguration(String projectId, VersionId versionId, ProjectFileAccessProvider projectFileAccessProvider)
    {
        return getProjectConfiguration(projectFileAccessProvider.getFileAccessContext(projectId, SourceSpecification.versionSourceSpecification(versionId)));
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

    public static boolean isLegacyProjectDependency(ProjectDependency projectDependency)
    {
        return (projectDependency != null) &&
                (projectDependency.getProjectId() != null) &&
                (projectDependency.getProjectId().indexOf(':') == -1);
    }

    public static Comparator<ProjectDependency> getProjectDependencyComparator()
    {
        return ProjectDependency.getDefaultComparator();
    }

    public static Comparator<MetamodelDependency> getMetamodelDependencyComparator()
    {
        return MetamodelDependency.getDefaultComparator();
    }

    public static <T, K extends Comparable<? super K>> void validateDependencyConflicts(Collection<T> dependencies, Function<? super T, ? extends K> indexKeyFn, BiFunction<? super K, ? super Set<T>, String> conflictFn, String description)
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
            throw new LegendSDLCException(builder.toString(), 400);
        }
    }

    public static ProjectFile getProjectConfigurationFile(FileAccessContext accessContext)
    {
        return accessContext.getFile(PROJECT_CONFIG_PATH);
    }

    public static byte[] serializeProjectConfiguration(ProjectConfiguration projectConfiguration)
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

    public static ProjectConfiguration readProjectConfiguration(ProjectFile file)
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

    public List<ArtifactTypeGenerationConfiguration> getAvailableGenerationConfigurations()
    {
        // None by default
        return Collections.emptyList();
    }

    public static EntitySourceDirectory newEntitySourceDirectory(String directory, EntitySerializer serializer)
    {
        return new EntitySourceDirectory(directory, serializer);
    }
}
