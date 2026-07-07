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

package org.finos.legend.sdlc.server.project;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.tuple.Tuples;
import org.eclipse.collections.impl.utility.Iterate;
import org.eclipse.collections.impl.utility.LazyIterate;
import org.eclipse.collections.impl.utility.ListIterate;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactGeneration;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactType;
import org.finos.legend.sdlc.domain.model.project.configuration.MetamodelDependency;
import org.finos.legend.sdlc.domain.model.project.configuration.PlatformConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectStructureVersion;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.error.LegendSDLCException;
import org.finos.legend.sdlc.server.domain.api.project.ProjectConfigurationUpdater;
import org.finos.legend.sdlc.server.domain.api.project.source.PatchSourceSpecification;
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification;
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecificationConsumer;
import org.finos.legend.sdlc.server.domain.api.project.source.VersionSourceSpecification;
import org.finos.legend.sdlc.server.domain.api.project.source.WorkspaceSourceSpecification;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSpecification;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider.FileAccessContext;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider.ProjectFile;
import org.finos.legend.sdlc.server.project.extension.ProjectStructureExtension;
import org.finos.legend.sdlc.server.project.extension.ProjectStructureExtensionProvider;
import org.finos.legend.sdlc.tools.StringTools;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The imperative write-side of project structure: computes and submits the file operations that realize a project
 * configuration update. Extracted from {@code ProjectStructure} (re-architecture Phase 2) so that the read-side
 * (layout knowledge) can be used without it; this class is destined for the SDLC core module in Phase 3.
 */
public class ProjectStructureUpdater
{
    private static final Set<ArtifactType> FORBIDDEN_ARTIFACT_GENERATION_TYPES = Collections.unmodifiableSet(EnumSet.of(ArtifactType.entities, ArtifactType.versioned_entities, ArtifactType.service_execution));

    private ProjectStructureUpdater()
    {
    }

    public static UpdateBuilder newUpdateBuilder(ProjectFileAccessProvider projectFileAccessProvider, String projectId)
    {
        return newUpdateBuilder(projectFileAccessProvider, projectId, null);
    }

    public static UpdateBuilder newUpdateBuilder(ProjectFileAccessProvider projectFileAccessProvider, String projectId, ProjectConfigurationUpdater configUpdater)
    {
        return new UpdateBuilder(projectFileAccessProvider, projectId, configUpdater);
    }

    private static Revision updateProjectConfiguration(UpdateBuilder updateBuilder, boolean requireRevisionId)
    {
        String projectId = updateBuilder.getProjectId();
        SourceSpecification sourceSpecification = updateBuilder.getSourceSpecification();
        ProjectFileAccessProvider projectFileAccessProvider = updateBuilder.getProjectFileAccessProvider();

        String revisionId;
        if (updateBuilder.getRevisionId() == null)
        {
            // if revisionId not specified, get the current revision
            Revision currentRevision = projectFileAccessProvider.getRevisionAccessContext(projectId, sourceSpecification, null).getCurrentRevision();
            if (currentRevision != null)
            {
                revisionId = currentRevision.getId();
            }
            else if (requireRevisionId)
            {
                StringBuilder builder = new StringBuilder("Could not find current revision for ");
                sourceSpecification.visit(new SourceSpecificationConsumer()
                {
                    @Override
                    protected void accept(VersionSourceSpecification sourceSpec)
                    {
                        sourceSpec.getVersionId().appendVersionIdString(builder.append("version ")).append(" of ");
                    }

                    @Override
                    protected void accept(PatchSourceSpecification sourceSpec)
                    {
                        sourceSpec.getVersionId().appendVersionIdString(builder.append("patch version ")).append(" of ");
                    }

                    @Override
                    protected void accept(WorkspaceSourceSpecification sourceSpec)
                    {
                        WorkspaceSpecification workspaceSpec = sourceSpec.getWorkspaceSpecification();
                        builder.append(workspaceSpec.getType().getLabel()).append(" ").append(workspaceSpec.getAccessType().getLabel()).append(" ").append(workspaceSpec.getId()).append("in ");
                    }
                });
                builder.append("project ").append(projectId).append(": it may be corrupt");
                throw new LegendSDLCException(builder.toString());
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

        FileAccessContext fileAccessContext = CachingFileAccessContext.wrap(projectFileAccessProvider.getFileAccessContext(projectId, sourceSpecification, revisionId));

        ProjectFile configFile = ProjectStructure.getProjectConfigurationFile(fileAccessContext);
        ProjectConfiguration currentConfig = (configFile == null) ? ProjectStructure.getDefaultProjectConfiguration(projectId) : ProjectStructure.readProjectConfiguration(configFile);
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
                throw new LegendSDLCException("Cannot set extensions on project " + projectId + " with " + newConfig.getProjectType() + " type", 400);
            }
            oldProjectVersion = ProjectStructureVersion.newProjectStructureVersion(oldProjectVersion.getVersion());
        }
        // prevent downgrading project
        if (newConfig.getProjectStructureVersion().compareTo(oldProjectVersion) < 0)
        {
            throw new LegendSDLCException("Cannot change project " + projectId + " from project structure version " + currentConfig.getProjectStructureVersion().toVersionString() + " to version " + newConfig.getProjectStructureVersion().toVersionString(), 400);
        }

        // Serialize new configuration and check if it differs from the old
        byte[] serializedNewConfig = ProjectStructure.serializeProjectConfiguration(newConfig);
        if ((configFile != null) && Arrays.equals(serializedNewConfig, configFile.getContentAsBytes()))
        {
            // new configuration file is the same as the old
            return null;
        }

        List<ProjectFileOperation> operations = Lists.mutable.empty();
        operations.add((configFile == null) ? ProjectFileOperation.addFile(ProjectStructure.PROJECT_CONFIG_PATH, serializedNewConfig) : ProjectFileOperation.modifyFile(ProjectStructure.PROJECT_CONFIG_PATH, serializedNewConfig));

        ProjectStructure currentProjectStructure = ProjectStructure.getProjectStructure(currentConfig, updateBuilder.getProjectStructurePlatformExtensions());
        ProjectStructure newProjectStructure = ProjectStructure.getProjectStructure(newConfig, updateBuilder.getProjectStructurePlatformExtensions());

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
                        throw new LegendSDLCException(builder.toString(), e);
                    }
                    EntitySourceDirectory newSourceDirectory = Iterate.detectWith(newEntityDirectories, EntitySourceDirectory::canSerialize, entity);
                    if (newSourceDirectory == null)
                    {
                        throw new LegendSDLCException("Could not find a new source directory for entity " + entity.getPath() + ", currently in " + currentPath);
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

        collectStructureAndExtensionUpdateOperations(updateBuilder, currentConfig, newConfig, currentProjectStructure, newProjectStructure, fileAccessContext, operations::add);

        // Submit changes
        return projectFileAccessProvider.getFileModificationContext(projectId, sourceSpecification, revisionId).submit(updateBuilder.getMessage(), operations);
    }

    /**
     * Seam R1 (re-architecture, section 6): the single dispatch point through which all imperative structure and
     * extension write logic is reached. The {@code collectUpdateProjectConfigurationOperations} contract is the
     * legacy write-side; do not add further callers or design new SPI around it — the layout-reconciliation plan
     * replaces this dispatch with a declarative build-and-reconcile path beside it.
     */
    private static void collectStructureAndExtensionUpdateOperations(UpdateBuilder updateBuilder, ProjectConfiguration currentConfig, ProjectConfiguration newConfig, ProjectStructure currentProjectStructure, ProjectStructure newProjectStructure, FileAccessContext fileAccessContext, Consumer<ProjectFileOperation> operationConsumer)
    {
        // Collect any further update operations
        if (newConfig.getProjectType() != ProjectType.EMBEDDED)
        {
            newProjectStructure.collectUpdateProjectConfigurationOperations(currentProjectStructure, fileAccessContext, operationConsumer);
        }

        // Call legacy method
        newProjectStructure.collectUpdateProjectConfigurationOperations(currentProjectStructure, fileAccessContext, (x, y) ->
        {
            throw new UnsupportedOperationException();
        }, operationConsumer);

        // Collect update operations from any project structure extension
        if (newConfig.getProjectStructureVersion().getExtensionVersion() != null)
        {
            ProjectStructureExtensionProvider extensionProvider = updateBuilder.getProjectStructureExtensionProvider();
            if (extensionProvider != null)
            {
                ProjectStructureExtension projectStructureExtension = extensionProvider.getProjectStructureExtension(newConfig.getProjectStructureVersion().getVersion(), newConfig.getProjectStructureVersion().getExtensionVersion());
                projectStructureExtension.collectUpdateProjectConfigurationOperations(currentConfig, newConfig, fileAccessContext, operationConsumer);
            }
        }
    }

    private static void validateProjectConfiguration(ProjectConfiguration config)
    {
        // Group id
        if (!ProjectStructure.isValidGroupId(config.getGroupId()))
        {
            throw new LegendSDLCException("Invalid groupId: " + config.getGroupId(), 400);
        }

        // Artifact id
        if (!ProjectStructure.isValidArtifactId(config.getArtifactId()))
        {
            throw new LegendSDLCException("Invalid artifactId: " + config.getArtifactId() + ". ArtifactId must follow pattern that starts with a lowercase letter and can include lowercase letters, digits, underscores, and hyphens between segments.", 400);
        }

        // Project type
        if (!ProjectStructure.isValidProjectType(config.getProjectType()))
        {
            throw new LegendSDLCException("Invalid projectType: " + config.getProjectType(), 400);
        }

        // Platform configurations
        if (config.getPlatformConfigurations() != null)
        {
            MutableSet<String> platformNames = Sets.mutable.empty();
            MutableSet<String> platformNameConflicts = LazyIterate.collect(config.getPlatformConfigurations(), PlatformConfiguration::getName).reject(platformNames::add, Sets.mutable.empty());
            if (platformNameConflicts.notEmpty())
            {
                throw new LegendSDLCException(platformNameConflicts.toSortedList().makeString("Platform configuration conflicts: \"", "\", \"", "\""), 400);
            }
        }

        // Metamodel dependencies
        validateMetamodelDependencies(config.getMetamodelDependencies());

        // Artifact generations
        validateArtifactGenerations(config.getArtifactGenerations());
    }

    private static void validateMetamodelDependencies(List<MetamodelDependency> metamodelDependencies)
    {
        MutableList<MetamodelDependency> unknownDependencies = ListIterate.reject(metamodelDependencies, ProjectStructureUpdater::isKnownMetamodel);
        if (unknownDependencies.notEmpty())
        {
            StringBuilder builder = new StringBuilder("There were issues with one or more added metamodel dependencies");
            builder.append("; unknown ").append((unknownDependencies.size() == 1) ? "dependency" : "dependencies").append(": ");
            unknownDependencies.sort(ProjectStructure.getMetamodelDependencyComparator());
            unknownDependencies.forEach(d -> d.appendDependencyString((d == unknownDependencies.get(0)) ? builder : builder.append(", ")));
            throw new LegendSDLCException(builder.toString(), 400);
        }
        ProjectStructure.validateDependencyConflicts(
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
            throw new LegendSDLCException(builder.toString(), 400);
        }
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
                ProjectConfiguration dependencyConfig = ProjectStructure.getProjectConfiguration(projectFileAccessProvider.getFileAccessContext(dep.getProjectId(), SourceSpecification.versionSourceSpecification(VersionId.parseVersionId(dep.getVersionId()))));
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
        Comparator<ProjectDependency> comparator = ProjectStructure.getProjectDependencyComparator();
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
            throw new LegendSDLCException(builder.toString(), 400);
        }

        SimpleProjectConfiguration newConfig = new SimpleProjectConfiguration(config);
        newConfig.setProjectDependencies(projectDependencies.toSortedList(comparator));
        return newConfig;
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
                this.configUpdater.setProjectStructureVersion(ProjectStructure.getLatestProjectStructureVersion());
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
