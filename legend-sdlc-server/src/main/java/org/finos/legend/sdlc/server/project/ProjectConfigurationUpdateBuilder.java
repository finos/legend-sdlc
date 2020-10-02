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

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactGeneration;
import org.finos.legend.sdlc.domain.model.project.configuration.MetamodelDependency;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.server.project.extension.ProjectStructureExtensionProvider;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class ProjectConfigurationUpdateBuilder
{
    private final ProjectFileAccessProvider projectFileAccessProvider;
    private final ProjectType projectType;
    private final String projectId;
    private String workspaceId;
    private ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType;
    private String revisionId;
    private Integer projectStructureVersion;
    private Integer projectStructureExtensionVersion;
    private ProjectStructureExtensionProvider projectStructureExtensionProvider;
    private String groupId;
    private String artifactId;
    private final Set<ProjectDependency> projectDependenciesToAdd = Sets.mutable.empty();
    private final Set<ProjectDependency> projectDependenciesToRemove = Sets.mutable.empty();
    private final Set<MetamodelDependency> metamodelDependenciesToAdd = Sets.mutable.empty();
    private final Set<MetamodelDependency> metamodelDependenciesToRemove = Sets.mutable.empty();
    private final List<ArtifactGeneration> generationsToAdd = Lists.mutable.empty();
    private final Set<String> generationNamesToRemove = Sets.mutable.empty();

    private String message;

    private ProjectConfigurationUpdateBuilder(ProjectFileAccessProvider projectFileAccessProvider, ProjectType projectType, String projectId)
    {
        this.projectFileAccessProvider = projectFileAccessProvider;
        this.projectType = projectType;
        this.projectId = projectId;
    }

    public ProjectFileAccessProvider getProjectFileAccessProvider()
    {
        return this.projectFileAccessProvider;
    }


    public ProjectType getProjectType()
    {
        return this.projectType;
    }

    public String getProjectId()
    {
        return this.projectId;
    }

    public boolean hasWorkspaceId()
    {
        return this.workspaceId != null;
    }

    public String getWorkspaceId()
    {
        return this.workspaceId;
    }

    public void setWorkspaceId(String workspaceId)
    {
        this.workspaceId = workspaceId;
    }


    public boolean hasRevisionId()
    {
        return this.revisionId != null;
    }

    public String getRevisionId()
    {
        return this.revisionId;
    }

    public void setRevisionId(String revisionId)
    {
        this.revisionId = revisionId;
    }


    public boolean hasProjectStructureVersion()
    {
        return this.projectStructureVersion != null;
    }

    public Integer getProjectStructureVersion()
    {
        return this.projectStructureVersion;
    }

    public void setProjectStructureVersion(Integer projectStructureVersion)
    {
        this.projectStructureVersion = projectStructureVersion;
    }


    public boolean hasProjectStructureExtensionVersion()
    {
        return this.projectStructureExtensionVersion != null;
    }

    public Integer getProjectStructureExtensionVersion()
    {
        return this.projectStructureExtensionVersion;
    }

    public void setProjectStructureExtensionVersion(Integer projectStructureExtensionVersion)
    {
        this.projectStructureExtensionVersion = projectStructureExtensionVersion;
    }


    public boolean hasProjectStructureExtensionProvider()
    {
        return this.projectStructureExtensionProvider != null;
    }

    public ProjectStructureExtensionProvider getProjectStructureExtensionProvider()
    {
        return this.projectStructureExtensionProvider;
    }

    public void setProjectStructureExtensionProvider(ProjectStructureExtensionProvider projectStructureExtensionProvider)
    {
        this.projectStructureExtensionProvider = projectStructureExtensionProvider;
    }


    public boolean hasGroupId()
    {
        return this.groupId != null;
    }

    public String getGroupId()
    {
        return this.groupId;
    }

    public void setGroupId(String groupId)
    {
        this.groupId = groupId;
    }


    public boolean hasArtifactId()
    {
        return this.artifactId != null;
    }

    public String getArtifactId()
    {
        return this.artifactId;
    }

    public void setArtifactId(String artifactId)
    {
        this.artifactId = artifactId;
    }


    public boolean hasProjectDependenciesToAdd()
    {
        return !this.projectDependenciesToAdd.isEmpty();
    }

    public Set<ProjectDependency> getProjectDependenciesToAdd()
    {
        return Collections.unmodifiableSet(this.projectDependenciesToAdd);
    }

    public void addProjectDependenciesToAdd(Iterable<? extends ProjectDependency> projectDependencies)
    {
        if (projectDependencies != null)
        {
            projectDependencies.forEach(this.projectDependenciesToAdd::add);
        }
    }


    public boolean hasProjectDependenciesToRemove()
    {
        return !this.projectDependenciesToRemove.isEmpty();
    }

    public Set<ProjectDependency> getProjectDependenciesToRemove()
    {
        return Collections.unmodifiableSet(this.projectDependenciesToRemove);
    }

    public void addProjectDependenciesToRemove(Iterable<? extends ProjectDependency> projectDependencies)
    {
        if (projectDependencies != null)
        {
            projectDependencies.forEach(this.projectDependenciesToRemove::add);
        }
    }


    public boolean hasMetamodelDependenciesToAdd()
    {
        return !this.metamodelDependenciesToAdd.isEmpty();
    }

    public Set<MetamodelDependency> getMetamodelDependenciesToAdd()
    {
        return Collections.unmodifiableSet(this.metamodelDependenciesToAdd);
    }

    public void addMetamodelDependenciesToAdd(Iterable<? extends MetamodelDependency> metamodelDependencies)
    {
        if (metamodelDependencies != null)
        {
            metamodelDependencies.forEach(this.metamodelDependenciesToAdd::add);
        }
    }


    public boolean hasMetamodelDependenciesToRemove()
    {
        return !this.metamodelDependenciesToRemove.isEmpty();
    }

    public boolean hasArtifactGenerationsToRemove()
    {
        return !this.generationNamesToRemove.isEmpty();
    }

    public Set<MetamodelDependency> getMetamodelDependenciesToRemove()
    {
        return Collections.unmodifiableSet(this.metamodelDependenciesToRemove);
    }

    public void addMetamodelDependenciesToRemove(Iterable<? extends MetamodelDependency> metamodelDependencies)
    {
        if (metamodelDependencies != null)
        {
            metamodelDependencies.forEach(this.metamodelDependenciesToRemove::add);
        }
    }

    public boolean hasMessage()
    {
        return this.message != null;
    }

    public String getMessage()
    {
        return this.message;
    }

    public void setMessage(String message)
    {
        this.message = message;
    }


    public ProjectFileAccessProvider.WorkspaceAccessType getWorkspaceAccessType()
    {
        return this.workspaceAccessType;
    }

    public void setWorkspaceAccessType(ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType)
    {
        this.workspaceAccessType = workspaceAccessType;
    }

    public ProjectConfigurationUpdateBuilder withWorkspace(String workspaceId, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType)
    {
        if (workspaceId != null && workspaceAccessType == null)
        {
            throw new RuntimeException("workspace access type is required when workspace ID is specified");
        }
        setWorkspaceId(workspaceId);
        setWorkspaceAccessType(workspaceAccessType);
        return this;
    }

    public ProjectConfigurationUpdateBuilder withRevisionId(String revisionId)
    {
        setRevisionId(revisionId);
        return this;
    }

    public ProjectConfigurationUpdateBuilder withProjectStructureVersion(Integer projectStructureVersion)
    {
        setProjectStructureVersion(projectStructureVersion);
        return this;
    }

    public ProjectConfigurationUpdateBuilder withProjectStructureExtensionVersion(Integer projectStructureExtensionVersion)
    {
        setProjectStructureExtensionVersion(projectStructureExtensionVersion);
        return this;
    }

    public ProjectConfigurationUpdateBuilder withProjectStructureExtensionProvider(ProjectStructureExtensionProvider projectStructureExtensionProvider)
    {
        setProjectStructureExtensionProvider(projectStructureExtensionProvider);
        return this;
    }

    public ProjectConfigurationUpdateBuilder withGroupId(String groupId)
    {
        setGroupId(groupId);
        return this;
    }

    public ProjectConfigurationUpdateBuilder withArtifactId(String artifactId)
    {
        setArtifactId(artifactId);
        return this;
    }

    public ProjectConfigurationUpdateBuilder withProjectDependenciesToAdd(Iterable<? extends ProjectDependency> projectDependencies)
    {
        addProjectDependenciesToAdd(projectDependencies);
        return this;
    }

    public ProjectConfigurationUpdateBuilder withProjectDependenciesToRemove(Iterable<? extends ProjectDependency> projectDependencies)
    {
        addProjectDependenciesToRemove(projectDependencies);
        return this;
    }

    public ProjectConfigurationUpdateBuilder withMetamodelDependenciesToAdd(Iterable<? extends MetamodelDependency> metamodelDependencies)
    {
        addMetamodelDependenciesToAdd(metamodelDependencies);
        return this;
    }

    public ProjectConfigurationUpdateBuilder withMetamodelDependenciesToRemove(Iterable<? extends MetamodelDependency> metamodelDependencies)
    {
        addMetamodelDependenciesToRemove(metamodelDependencies);
        return this;
    }

    public ProjectConfigurationUpdateBuilder withMessage(String message)
    {
        setMessage(message);
        return this;
    }

    public ProjectConfigurationUpdateBuilder withArtifactGenerationToAdd(ArtifactGeneration generation)
    {
        addArtifactGenerationToAdd(generation);
        return this;
    }

    public ProjectConfigurationUpdateBuilder withArtifactGenerationsToAdd(Iterable<? extends ArtifactGeneration> generations)
    {
        addArtifactGenerationsToAdd(generations);
        return this;
    }

    public ProjectConfigurationUpdateBuilder withArtifactGenerationToRemove(String generationName)
    {
        addArtifactGenerationToRemove(generationName);
        return this;
    }

    public ProjectConfigurationUpdateBuilder withArtifactGenerationsToRemove(Iterable<String> generationName)
    {
        addArtifactGenerationsToRemove(generationName);
        return this;
    }

    public void addArtifactGenerationToAdd(ArtifactGeneration generation)
    {
        this.generationsToAdd.add(generation);
    }

    public void addArtifactGenerationsToAdd(Iterable<? extends ArtifactGeneration> generations)
    {
        if (generations != null)
        {
            generations.forEach(this::addArtifactGenerationToAdd);
        }
    }

    public void addArtifactGenerationToRemove(String generationName)
    {
        this.generationNamesToRemove.add(generationName);
    }

    public void addArtifactGenerationsToRemove(Iterable<String> generationNames)
    {
        if (generationNames != null)
        {
            generationNames.forEach(this::addArtifactGenerationToRemove);
        }
    }

    public List<ArtifactGeneration> getArtifactGenerationToAdd()
    {
        return this.generationsToAdd;
    }

    public boolean hasArtifactGenerationsToAdd()
    {
        return !this.generationsToAdd.isEmpty();
    }

    public Set<String> getArtifactGenerationToRemove()
    {
        return this.generationNamesToRemove;
    }

    public Revision buildProjectStructure()
    {
        return ProjectStructure.buildProjectStructure(this);
    }

    public Revision updateProjectConfiguration()
    {
        return ProjectStructure.updateProjectConfiguration(this);
    }

    public static ProjectConfigurationUpdateBuilder newBuilder(ProjectFileAccessProvider projectFileAccessProvider, ProjectType projectType, String projectId)
    {
        Objects.requireNonNull(projectFileAccessProvider, "projectFileAccessProvider may not be null");
        Objects.requireNonNull(projectType, "projectType may not be null");
        Objects.requireNonNull(projectId, "projectId may not be null");
        return new ProjectConfigurationUpdateBuilder(projectFileAccessProvider, projectType, projectId);
    }


}
