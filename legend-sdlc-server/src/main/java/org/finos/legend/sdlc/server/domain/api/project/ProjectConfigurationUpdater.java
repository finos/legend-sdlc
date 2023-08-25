// Copyright 2022 Goldman Sachs
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

package org.finos.legend.sdlc.server.domain.api.project;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.list.fixed.ArrayAdapter;
import org.eclipse.collections.impl.utility.ListIterate;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactGeneration;
import org.finos.legend.sdlc.domain.model.project.configuration.MetamodelDependency;
import org.finos.legend.sdlc.domain.model.project.configuration.PlatformConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectStructureVersion;

import java.util.List;
import java.util.Set;

public class ProjectConfigurationUpdater
{
    private String projectId;
    private ProjectType projectType;
    private Integer projectStructureVersion;
    private Integer projectStructureExtensionVersion;
    private String groupId;
    private String artifactId;
    private boolean platformConfigurationsIsSet = false;
    private MutableList<PlatformConfiguration> platformConfigurations;
    private final MutableSet<ProjectDependency> projectDependenciesToAdd = Sets.mutable.empty();
    private final MutableSet<ProjectDependency> projectDependenciesToRemove = Sets.mutable.empty();
    private final MutableSet<MetamodelDependency> metamodelDependenciesToAdd = Sets.mutable.empty();
    private final MutableSet<MetamodelDependency> metamodelDependenciesToRemove = Sets.mutable.empty();
    private final MutableList<ArtifactGeneration> generationsToAdd = Lists.mutable.empty();
    private final MutableSet<String> generationNamesToRemove = Sets.mutable.empty();

    // Project id

    public String getProjectId()
    {
        return this.projectId;
    }

    public void setProjectId(String projectId)
    {
        this.projectId = projectId;
    }

    public ProjectConfigurationUpdater withProjectId(String projectId)
    {
        setProjectId(projectId);
        return this;
    }

    // Project type

    public ProjectType getProjectType()
    {
        return this.projectType;
    }

    public void setProjectType(ProjectType projectType)
    {
        this.projectType = projectType;
    }

    public ProjectConfigurationUpdater withProjectType(ProjectType projectType)
    {
        setProjectType(projectType);
        return this;
    }

    // Project structure version

    public Integer getProjectStructureVersion()
    {
        return this.projectStructureVersion;
    }

    public void setProjectStructureVersion(Integer projectStructureVersion)
    {
        this.projectStructureVersion = projectStructureVersion;
    }

    public ProjectConfigurationUpdater withProjectStructureVersion(Integer projectStructureVersion)
    {
        setProjectStructureVersion(projectStructureVersion);
        return this;
    }

    // Project structure extension version

    public Integer getProjectStructureExtensionVersion()
    {
        return this.projectStructureExtensionVersion;
    }

    public void setProjectStructureExtensionVersion(Integer projectStructureExtensionVersion)
    {
        this.projectStructureExtensionVersion = projectStructureExtensionVersion;
    }

    public ProjectConfigurationUpdater withProjectStructureExtensionVersion(Integer projectStructureExtensionVersion)
    {
        setProjectStructureExtensionVersion(projectStructureExtensionVersion);
        return this;
    }

    // Platform configurations

    public List<PlatformConfiguration> getPlatformConfigurations()
    {
        return (this.platformConfigurations == null) ? null : this.platformConfigurations.asUnmodifiable();
    }

    public void setPlatformConfigurations(Iterable<? extends PlatformConfiguration> platformConfigurations)
    {
        this.platformConfigurationsIsSet = true;
        this.platformConfigurations = (platformConfigurations == null) ? null : Lists.mutable.<PlatformConfiguration>withAll(platformConfigurations).sortThisBy(PlatformConfiguration::getName);
    }

    public ProjectConfigurationUpdater withPlatformConfigurations(Iterable<? extends PlatformConfiguration> platformConfigurations)
    {
        setPlatformConfigurations(platformConfigurations);
        return this;
    }

    // Group id

    public String getGroupId()
    {
        return this.groupId;
    }

    public void setGroupId(String groupId)
    {
        this.groupId = groupId;
    }

    public ProjectConfigurationUpdater withGroupId(String groupId)
    {
        setGroupId(groupId);
        return this;
    }

    // Artifact id

    public String getArtifactId()
    {
        return this.artifactId;
    }

    public void setArtifactId(String artifactId)
    {
        this.artifactId = artifactId;
    }

    public ProjectConfigurationUpdater withArtifactId(String artifactId)
    {
        setArtifactId(artifactId);
        return this;
    }

    // Project dependencies to add

    public Set<ProjectDependency> getProjectDependenciesToAdd()
    {
        return this.projectDependenciesToAdd.asUnmodifiable();
    }

    public void addProjectDependencyToAdd(ProjectDependency projectDependency)
    {
        this.projectDependenciesToAdd.add(projectDependency);
    }

    public void addProjectDependenciesToAdd(Iterable<? extends ProjectDependency> projectDependencies)
    {
        if (projectDependencies != null)
        {
            this.projectDependenciesToAdd.addAllIterable(projectDependencies);
        }
    }

    public ProjectConfigurationUpdater withProjectDependencyToAdd(ProjectDependency projectDependency)
    {
        addProjectDependencyToAdd(projectDependency);
        return this;
    }

    public ProjectConfigurationUpdater withProjectDependenciesToAdd(Iterable<? extends ProjectDependency> projectDependencies)
    {
        addProjectDependenciesToAdd(projectDependencies);
        return this;
    }

    public ProjectConfigurationUpdater withProjectDependenciesToAdd(ProjectDependency... projectDependencies)
    {
        return withProjectDependenciesToAdd((projectDependencies == null) ? null : ArrayAdapter.adapt(projectDependencies));
    }

    // Project dependencies to remove

    public Set<ProjectDependency> getProjectDependenciesToRemove()
    {
        return this.projectDependenciesToRemove.asUnmodifiable();
    }

    public void addProjectDependencyToRemove(ProjectDependency projectDependency)
    {
        this.projectDependenciesToRemove.add(projectDependency);
    }

    public void addProjectDependenciesToRemove(Iterable<? extends ProjectDependency> projectDependencies)
    {
        if (projectDependencies != null)
        {
            this.projectDependenciesToRemove.addAllIterable(projectDependencies);
        }
    }

    public ProjectConfigurationUpdater withProjectDependencyToRemove(ProjectDependency projectDependency)
    {
        addProjectDependencyToRemove(projectDependency);
        return this;
    }

    public ProjectConfigurationUpdater withProjectDependenciesToRemove(Iterable<? extends ProjectDependency> projectDependencies)
    {
        addProjectDependenciesToRemove(projectDependencies);
        return this;
    }

    public ProjectConfigurationUpdater withProjectDependenciesToRemove(ProjectDependency... projectDependencies)
    {
        return withProjectDependenciesToRemove((projectDependencies == null) ? null : ArrayAdapter.adapt(projectDependencies));
    }


    // Metamodel dependencies to add

    public Set<MetamodelDependency> getMetamodelDependenciesToAdd()
    {
        return this.metamodelDependenciesToAdd.asUnmodifiable();
    }

    public void addMetamodelDependencyToAdd(MetamodelDependency metamodelDependency)
    {
        this.metamodelDependenciesToAdd.add(metamodelDependency);
    }

    public void addMetamodelDependenciesToAdd(Iterable<? extends MetamodelDependency> metamodelDependencies)
    {
        if (metamodelDependencies != null)
        {
            this.metamodelDependenciesToAdd.addAllIterable(metamodelDependencies);
        }
    }

    public ProjectConfigurationUpdater withMetamodelDependencyToAdd(MetamodelDependency metamodelDependency)
    {
        addMetamodelDependencyToAdd(metamodelDependency);
        return this;
    }

    public ProjectConfigurationUpdater withMetamodelDependenciesToAdd(Iterable<? extends MetamodelDependency> metamodelDependencies)
    {
        addMetamodelDependenciesToAdd(metamodelDependencies);
        return this;
    }

    public ProjectConfigurationUpdater withMetamodelDependenciesToAdd(MetamodelDependency... metamodelDependencies)
    {
        return withMetamodelDependenciesToAdd((metamodelDependencies == null) ? null : ArrayAdapter.adapt(metamodelDependencies));
    }


    // Metamodel dependencies to remove

    public Set<MetamodelDependency> getMetamodelDependenciesToRemove()
    {
        return this.metamodelDependenciesToRemove.asUnmodifiable();
    }

    public void addMetamodelDependencyToRemove(MetamodelDependency metamodelDependency)
    {
        this.metamodelDependenciesToRemove.add(metamodelDependency);
    }

    public void addMetamodelDependenciesToRemove(Iterable<? extends MetamodelDependency> metamodelDependencies)
    {
        if (metamodelDependencies != null)
        {
            this.metamodelDependenciesToRemove.addAllIterable(metamodelDependencies);
        }
    }

    public ProjectConfigurationUpdater withMetamodelDependencyToRemove(MetamodelDependency metamodelDependency)
    {
        addMetamodelDependencyToRemove(metamodelDependency);
        return this;
    }

    public ProjectConfigurationUpdater withMetamodelDependenciesToRemove(Iterable<? extends MetamodelDependency> metamodelDependencies)
    {
        addMetamodelDependenciesToRemove(metamodelDependencies);
        return this;
    }

    public ProjectConfigurationUpdater withMetamodelDependenciesToRemove(MetamodelDependency... metamodelDependencies)
    {
        return withMetamodelDependenciesToRemove((metamodelDependencies == null) ? null : ArrayAdapter.adapt(metamodelDependencies));
    }

    // Artifact generations to add

    @Deprecated
    public List<ArtifactGeneration> getArtifactGenerationsToAdd()
    {
        return this.generationsToAdd.asUnmodifiable();
    }

    @Deprecated
    public void addArtifactGenerationsToAdd(Iterable<? extends ArtifactGeneration> generations)
    {
        if (generations != null)
        {
            this.generationsToAdd.addAllIterable(generations);
        }
    }

    @Deprecated
    public ProjectConfigurationUpdater withArtifactGenerationsToAdd(Iterable<? extends ArtifactGeneration> generations)
    {
        addArtifactGenerationsToAdd(generations);
        return this;
    }

    // Artifact generations to remove

    @Deprecated
    public Set<String> getArtifactGenerationsToRemove()
    {
        return this.generationNamesToRemove.asUnmodifiable();
    }

    @Deprecated
    public void addArtifactGenerationsToRemove(Iterable<String> generationNames)
    {
        if (generationNames != null)
        {
            this.generationNamesToRemove.addAllIterable(generationNames);
        }
    }

    @Deprecated
    public ProjectConfigurationUpdater withArtifactGenerationsToRemove(Iterable<String> generationName)
    {
        addArtifactGenerationsToRemove(generationName);
        return this;
    }

    // Update

    public ProjectConfiguration update(ProjectConfiguration configuration)
    {
        // Project id
        String newProjectId = (this.projectId == null) ? configuration.getProjectId() : this.projectId;

        // Project type
        ProjectType newProjectType = (this.projectType == null) ? configuration.getProjectType() : this.projectType;

        // Project structure version
        ProjectStructureVersion newProjectStructureVersion;
        if (this.projectStructureVersion != null)
        {
            newProjectStructureVersion = ProjectStructureVersion.newProjectStructureVersion(this.projectStructureVersion, this.projectStructureExtensionVersion);
        }
        // For EMBEDDED projects drop extension version from current configuration
        else if (this.projectStructureExtensionVersion != null || newProjectType == ProjectType.EMBEDDED)
        {
            newProjectStructureVersion = ProjectStructureVersion.newProjectStructureVersion(configuration.getProjectStructureVersion().getVersion(), this.projectStructureExtensionVersion);
        }
        else
        {
            newProjectStructureVersion = configuration.getProjectStructureVersion();
        }

        // Platform configurations
        List<PlatformConfiguration> newPlatformConfigurations;
        if (this.platformConfigurationsIsSet)
        {
            newPlatformConfigurations = (this.platformConfigurations == null) ? null : this.platformConfigurations.toList();
        }
        else
        {
            List<PlatformConfiguration> currentPlatformConfigurations = configuration.getPlatformConfigurations();
            newPlatformConfigurations = (currentPlatformConfigurations == null) ? null : Lists.mutable.withAll(currentPlatformConfigurations);
        }

        // Group id
        String newGroupId = (this.groupId == null) ? configuration.getGroupId() : this.groupId;

        // Artifact id
        String newArtifactId = (this.artifactId == null) ? configuration.getArtifactId() : this.artifactId;

        // Project dependencies
        List<ProjectDependency> newProjectDependencies;
        if (this.projectDependenciesToAdd.isEmpty() && this.projectDependenciesToRemove.isEmpty())
        {
            newProjectDependencies = Lists.mutable.withAll(configuration.getProjectDependencies());
        }
        else
        {
            MutableSet<ProjectDependency> projectDependencies = Sets.mutable.withAll(configuration.getProjectDependencies());
            projectDependencies.removeAll(this.projectDependenciesToRemove);
            projectDependencies.addAll(this.projectDependenciesToAdd);
            newProjectDependencies = projectDependencies.toSortedList(ProjectDependency.getDefaultComparator());
        }

        // Metamodel dependencies
        List<MetamodelDependency> newMetamodelDependencies;
        if (this.metamodelDependenciesToAdd.isEmpty() && this.metamodelDependenciesToRemove.isEmpty())
        {
            newMetamodelDependencies = Lists.mutable.withAll(configuration.getMetamodelDependencies());
        }
        else
        {
            MutableSet<MetamodelDependency> metamodelDependencies = Sets.mutable.withAll(configuration.getMetamodelDependencies());
            metamodelDependencies.removeAll(this.metamodelDependenciesToRemove);
            metamodelDependencies.addAll(this.metamodelDependenciesToAdd);
            newMetamodelDependencies = metamodelDependencies.toSortedList(MetamodelDependency.getDefaultComparator());
        }

        // Artifact generations
        List<ArtifactGeneration> newArtifactGenerations;
        if (this.generationsToAdd.isEmpty() && this.generationNamesToRemove.isEmpty())
        {
            newArtifactGenerations = Lists.mutable.withAll(configuration.getArtifactGenerations());
        }
        else
        {
            newArtifactGenerations = ListIterate.select(configuration.getArtifactGenerations(), g -> !this.generationNamesToRemove.contains(g.getName()))
                    .withAll(this.generationsToAdd)
                    .sortThisBy(ArtifactGeneration::getName);
        }

        // Build configuration
        return new ProjectConfiguration()
        {
            @Override
            public String getProjectId()
            {
                return newProjectId;
            }

            @Override
            public ProjectType getProjectType()
            {
                return newProjectType;
            }

            @Override
            public ProjectStructureVersion getProjectStructureVersion()
            {
                return newProjectStructureVersion;
            }

            @Override
            public List<PlatformConfiguration> getPlatformConfigurations()
            {
                return newPlatformConfigurations;
            }

            @Override
            public String getGroupId()
            {
                return newGroupId;
            }

            @Override
            public String getArtifactId()
            {
                return newArtifactId;
            }

            @Override
            public List<ProjectDependency> getProjectDependencies()
            {
                return newProjectDependencies;
            }

            @Override
            public List<MetamodelDependency> getMetamodelDependencies()
            {
                return newMetamodelDependencies;
            }

            @Override
            public List<ArtifactGeneration> getArtifactGenerations()
            {
                return newArtifactGenerations;
            }
        };
    }

    // Factory

    public static ProjectConfigurationUpdater newUpdater()
    {
        return new ProjectConfigurationUpdater();
    }
}
