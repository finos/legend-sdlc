// Copyright 2021 Goldman Sachs
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

package org.finos.legend.sdlc.server.domain.api.test;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.set.mutable.SetAdapter;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.depot.api.MetadataApi;
import org.finos.legend.sdlc.server.depot.model.DepotProjectId;
import org.finos.legend.sdlc.server.depot.model.DepotProjectVersion;
import org.finos.legend.sdlc.server.domain.api.dependency.DependenciesApi;
import org.finos.legend.sdlc.server.domain.api.entity.EntityApi;
import org.finos.legend.sdlc.server.domain.api.project.ProjectConfigurationApi;
import org.finos.legend.sdlc.server.domain.api.project.SourceSpecification;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.tools.StringTools;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;

public class TestModelBuilder
{
    private final DependenciesApi dependenciesApi;
    private final EntityApi entityApi;
    private final ProjectConfigurationApi projectConfigurationApi;
    private final MetadataApi metadataApi;
    private final MutableMap<String, List<Entity>> entityCache = Maps.mutable.empty();

    @Inject
    public TestModelBuilder(DependenciesApi dependenciesApi, EntityApi entityApi, ProjectConfigurationApi projectConfigurationApi, MetadataApi metadataApi)
    {
        this.dependenciesApi = dependenciesApi;
        this.entityApi = entityApi;
        this.projectConfigurationApi = projectConfigurationApi;
        this.metadataApi = metadataApi;
    }

    public List<Entity> buildEntitiesForTest(String upstreamProjectId, String upstreamWorkspaceId, String upstreamRevisionId, String downstreamProjectId, String downstreamVersionId)
    {
        return buildEntitiesForTest(upstreamProjectId, upstreamWorkspaceId, WorkspaceType.USER, upstreamRevisionId, downstreamProjectId, downstreamVersionId);
    }

    public List<Entity> buildEntitiesForTest(String upstreamProjectId, String upstreamWorkspaceId, WorkspaceType type, String upstreamRevisionId, String downstreamProjectId, String downstreamVersionId)
    {
        DepotProjectId upstreamDepotProjectId = getDepotProjectId(upstreamProjectId);
        DepotProjectId downstreamDepotProjectId = DepotProjectId.parseProjectId(downstreamProjectId);

        Set<ProjectDependency> latestUpstreamLevel1Dependencies = this.dependenciesApi.getWorkspaceRevisionUpstreamProjects(upstreamProjectId, SourceSpecification.newSourceSpecification(upstreamWorkspaceId, type), upstreamRevisionId, false);
        Set<DepotProjectVersion> dependencies = processDependencies(upstreamDepotProjectId, downstreamDepotProjectId, downstreamVersionId, transformProjectDependencySet(latestUpstreamLevel1Dependencies));

        List<Entity> upstreamProjectWorkspaceEntities = this.entityApi.getWorkspaceRevisionEntityAccessContext(upstreamProjectId, SourceSpecification.newSourceSpecification(upstreamWorkspaceId, type), upstreamRevisionId).getEntities(null, null, null);
        return getEntities(downstreamDepotProjectId, downstreamVersionId, dependencies, upstreamProjectWorkspaceEntities);
    }

    public List<Entity> buildEntitiesForTest(String upstreamProjectId, VersionId upstreamVersionId, String downstreamProjectId, String downstreamVersionId)
    {
        DepotProjectId upstreamDepotProjectId = DepotProjectId.parseProjectId(upstreamProjectId);
        DepotProjectId downstreamDepotProjectId = DepotProjectId.parseProjectId(downstreamProjectId);

        Set<DepotProjectVersion> latestUpstreamLevel1Dependencies = this.metadataApi.getProjectDependencies(upstreamDepotProjectId, upstreamVersionId.toVersionIdString(), false);
        Set<DepotProjectVersion> dependencies = processDependencies(upstreamDepotProjectId, downstreamDepotProjectId, downstreamVersionId, latestUpstreamLevel1Dependencies);

        List<Entity> upstreamProjectWorkspaceEntities = this.metadataApi.getEntities(upstreamDepotProjectId, upstreamVersionId.toVersionIdString());
        return getEntities(downstreamDepotProjectId, downstreamVersionId, dependencies, upstreamProjectWorkspaceEntities);
    }

    public List<Entity> buildEntitiesForTest(String upstreamProjectId, String upstreamRevisionId, String downstreamProjectId, String downstreamVersionId)
    {
        DepotProjectId upstreamDepotProjectId = getDepotProjectId(upstreamProjectId);
        DepotProjectId downstreamDepotProjectId = DepotProjectId.parseProjectId(downstreamProjectId);

        Set<ProjectDependency> latestUpstreamLevel1Dependencies = this.dependenciesApi.getProjectRevisionUpstreamProjects(upstreamProjectId, upstreamRevisionId, false);
        Set<DepotProjectVersion> dependencies = processDependencies(upstreamDepotProjectId, downstreamDepotProjectId, downstreamVersionId, transformProjectDependencySet(latestUpstreamLevel1Dependencies));

        List<Entity> upstreamProjectWorkspaceEntities = this.entityApi.getProjectRevisionEntityAccessContext(upstreamProjectId, upstreamRevisionId).getEntities(null, null, null);
        return getEntities(downstreamDepotProjectId, downstreamVersionId, dependencies, upstreamProjectWorkspaceEntities);
    }

    public Set<DepotProjectVersion> processDependencies(DepotProjectId upstreamProjectId, DepotProjectId downstreamProjectId, String downstreamVersionId, Set<DepotProjectVersion> latestUpstreamDependencies)
    {
        try
        {
            if (Iterate.anySatisfy(latestUpstreamDependencies, dependency -> downstreamProjectId.equals(dependency.getDepotProjectId())))
            {
                throw new IllegalArgumentException("Project " + downstreamProjectId + " was specified as downstream but in fact it is a direct dependency for upstream project " + upstreamProjectId);
            }

            //get transitive dependencies for upstream project from metadata
            MutableSet<DepotProjectVersion> latestUpstreamTransitiveDependencies = Iterate.flatCollect(latestUpstreamDependencies, depotProjectVersion -> this.metadataApi.getProjectDependencies(depotProjectVersion.getDepotProjectId(), depotProjectVersion.getVersionId(), true), Sets.mutable.withAll(latestUpstreamDependencies));
            latestUpstreamTransitiveDependencies.removeIf(dependency -> upstreamProjectId.equals(dependency.getDepotProjectId()));

            Set<DepotProjectId> transitiveUpstreamDependenciesIds = SetAdapter.adapt(latestUpstreamDependencies).collect(DepotProjectVersion::getDepotProjectId);
            if (transitiveUpstreamDependenciesIds.contains(downstreamProjectId))
            {
                throw new IllegalArgumentException("Project " + downstreamProjectId + " was specified as downstream but in fact it is an indirect dependency for upstream project " + upstreamProjectId);
            }

            Set<DepotProjectVersion> downstreamLevel1Dependencies = this.metadataApi.getProjectDependencies(downstreamProjectId, downstreamVersionId, false);

            Set<DepotProjectVersion> dependencies = Sets.mutable.empty();
            for (DepotProjectVersion dependency : downstreamLevel1Dependencies)
            {
                // remove the upstream project as it has changed
                if (dependency.getDepotProjectId().equals(upstreamProjectId))
                {
                    continue;
                }
                // remove common first level dependencies
                if (latestUpstreamDependencies.contains(dependency))
                {
                    continue;
                }
                // remove dependency if the upstream project has a different version of this dependency
                if (transitiveUpstreamDependenciesIds.contains(dependency.getDepotProjectId()))
                {
                    continue;
                }
                // include this dependency
                dependencies.add(dependency);
                // and its transitive dependencies
                dependencies.addAll(this.metadataApi.getProjectDependencies(dependency.getDepotProjectId(), dependency.getVersionId(), true));
            }

            // finally add the new dependency tree of the upstream project
            dependencies.addAll(latestUpstreamTransitiveDependencies);

            //make sure that downstream or upstream projects were not included in dependencies list (it will cause entity duplication error)
            dependencies.removeIf(project -> project.getDepotProjectId().equals(downstreamProjectId) || project.getDepotProjectId().equals(upstreamProjectId));

            return dependencies;
        }
        catch (LegendSDLCServerException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new LegendSDLCServerException(StringTools.appendThrowableMessageIfPresent("Error processing dependencies", e), e);
        }
    }

    private List<Entity> getEntities(DepotProjectId downstreamProjectId, String downstreamVersionId, Set<DepotProjectVersion> dependencies, List<Entity> upstreamProjectWorkspaceEntities)
    {
        List<Entity> downstreamProjectEntities = this.metadataApi.getEntities(downstreamProjectId, downstreamVersionId);
        List<Entity> dependencyEntities = dependencies.stream().map(this::getEntities).flatMap(Collection::stream).collect(Collectors.toList());
        return Lists.mutable.withAll(upstreamProjectWorkspaceEntities).withAll(downstreamProjectEntities).withAll(dependencyEntities);
    }

    private List<Entity> getEntities(DepotProjectVersion depotProjectVersion)
    {
        // Avoid fetching entities for a project multiple times
        return this.entityCache.getIfAbsentPut(
                depotProjectVersion.toString(),
                () -> this.metadataApi.getEntities(depotProjectVersion.getDepotProjectId(), depotProjectVersion.getVersionId())
        );
    }

    private DepotProjectId getDepotProjectId(String gitLabProjectId)
    {
        ProjectConfiguration projectConfiguration = this.projectConfigurationApi.getProjectProjectConfiguration(gitLabProjectId);
        return DepotProjectId.newDepotProjectId(projectConfiguration.getGroupId(), projectConfiguration.getArtifactId());
    }

    private Set<DepotProjectVersion> transformProjectDependencySet(Set<ProjectDependency> dependencies)
    {
        return dependencies.stream().map(dependency ->
                DepotProjectVersion.newDepotProjectVersion(dependency.getProjectId(), dependency.getVersionId())).collect(Collectors.toSet());
    }
}
