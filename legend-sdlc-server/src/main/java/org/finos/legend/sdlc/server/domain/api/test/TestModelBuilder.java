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

package org.finos.legend.sdlc.server.domain.api.test;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.impl.set.mutable.SetAdapter;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.domain.api.dependency.DependenciesApi;
import org.finos.legend.sdlc.server.domain.api.entity.EntityApi;

import javax.inject.Inject;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TestModelBuilder
{
    private final DependenciesApi dependenciesApi;
    private final EntityApi entityApi;
    private final MutableMap<String, List<Entity>> entityCache = Maps.mutable.empty();

    @Inject
    public TestModelBuilder(DependenciesApi dependenciesApi, EntityApi entityApi)
    {
        this.dependenciesApi = dependenciesApi;
        this.entityApi = entityApi;
    }

    public List<Entity> buildEntitiesForTest(String upstreamProjectId, String upstreamWorkspaceId, String upstreamRevisionId, String downstreamProjectId, String downstreamRevisionId)
    {
        return buildEntitiesForTest(upstreamProjectId, upstreamWorkspaceId, WorkspaceType.USER, upstreamRevisionId, downstreamProjectId, downstreamRevisionId);
    }

    public List<Entity> buildEntitiesForTest(String upstreamProjectId, String upstreamWorkspaceId, WorkspaceType type, String upstreamRevisionId, String downstreamProjectId, String downstreamRevisionId)
    {
        Set<ProjectDependency> latestUpstreamLevel1Dependencies = this.dependenciesApi.getWorkspaceRevisionUpstreamProjects(upstreamProjectId, upstreamWorkspaceId, type, upstreamRevisionId, false);
        Set<ProjectDependency> dependencies = processDependencies(upstreamProjectId, downstreamProjectId, downstreamRevisionId, latestUpstreamLevel1Dependencies);

        List<Entity> upstreamProjectWorkspaceEntities = this.entityApi.getWorkspaceRevisionEntityAccessContext(upstreamProjectId, upstreamWorkspaceId, type, upstreamRevisionId).getEntities(null, null, null);
        return getEntities(downstreamProjectId, downstreamRevisionId, dependencies, upstreamProjectWorkspaceEntities);
    }

    public List<Entity> buildEntitiesForTest(String upstreamProjectId, VersionId upstreamVersionId, String downstreamProjectId, String downstreamRevisionId)
    {
        Set<ProjectDependency> latestUpstreamLevel1Dependencies = this.dependenciesApi.getProjectVersionUpstreamProjects(upstreamProjectId, upstreamVersionId.toVersionIdString(), false);
        Set<ProjectDependency> dependencies = processDependencies(upstreamProjectId, downstreamProjectId, downstreamRevisionId, latestUpstreamLevel1Dependencies);

        List<Entity> upstreamProjectWorkspaceEntities = this.entityApi.getVersionEntityAccessContext(upstreamProjectId, upstreamVersionId).getEntities(null, null, null);
        return getEntities(downstreamProjectId, downstreamRevisionId, dependencies, upstreamProjectWorkspaceEntities);
    }

    public List<Entity> buildEntitiesForTest(String upstreamProjectId, String upstreamRevisionId, String downstreamProjectId, String downstreamRevisionId)
    {
        Set<ProjectDependency> latestUpstreamLevel1Dependencies = this.dependenciesApi.getProjectRevisionUpstreamProjects(upstreamProjectId, upstreamRevisionId, false);
        Set<ProjectDependency> dependencies = processDependencies(upstreamProjectId, downstreamProjectId, downstreamRevisionId, latestUpstreamLevel1Dependencies);

        List<Entity> upstreamProjectWorkspaceEntities = this.entityApi.getProjectRevisionEntityAccessContext(upstreamProjectId, upstreamRevisionId).getEntities(null, null, null);
        return getEntities(downstreamProjectId, downstreamRevisionId, dependencies, upstreamProjectWorkspaceEntities);
    }

    public Set<ProjectDependency> processDependencies(String upstreamProjectId, String downstreamProjectId, String downstreamRevisionId, Set<ProjectDependency> latestUpstreamLevel1Dependencies)
    {
        Set<String> latestUpstreamLevel1DependencyProjectIds = SetAdapter.adapt(latestUpstreamLevel1Dependencies).collect(ProjectDependency::getProjectId);

        Set<ProjectDependency> downstreamLevel1Dependencies = this.dependenciesApi.getProjectRevisionUpstreamProjects(downstreamProjectId, downstreamRevisionId, false);

        Set<ProjectDependency> dependencies = Sets.mutable.empty();
        for (ProjectDependency dependency : downstreamLevel1Dependencies)
        {
            // remove the upstream project as it has changed
            if (dependency.getProjectId().equals(upstreamProjectId))
            {
                continue;
            }
            // remove common first level dependencies
            if (latestUpstreamLevel1Dependencies.contains(dependency))
            {
                continue;
            }
            // remove dependency if the upstream project has a different version of this dependency
            if (latestUpstreamLevel1DependencyProjectIds.contains(dependency.getProjectId()))
            {
                continue;
            }
            // include this dependency
            dependencies.add(dependency);
            // and its transitive dependencies
            dependencies.addAll(this.dependenciesApi.getProjectVersionUpstreamProjects(dependency.getProjectId(), dependency.getVersionId().toVersionIdString(), true));
        }
        // finally add the new dependency tree of the upstream project
        dependencies.addAll(latestUpstreamLevel1Dependencies);

        return dependencies;
    }

    private List<Entity> getEntities(String downstreamProjectId, String downstreamRevisionId, Set<ProjectDependency> dependencies, List<Entity> upstreamProjectWorkspaceEntities)
    {
        List<Entity> downstreamProjectEntities = this.entityApi.getProjectRevisionEntityAccessContext(downstreamProjectId, downstreamRevisionId).getEntities(null, null, null);
        List<Entity> dependencyEntities = dependencies.stream().map(this::getEntities).flatMap(Collection::stream).collect(Collectors.toList());
        return Lists.mutable.withAll(upstreamProjectWorkspaceEntities).withAll(downstreamProjectEntities).withAll(dependencyEntities);
    }

    private List<Entity> getEntities(ProjectDependency projectDependency)
    {
        // Avoid fetching entities for a project multiple times
        return this.entityCache.getIfAbsentPut(
                projectDependency.toDependencyString(),
                () -> this.entityApi.getVersionEntityAccessContext(projectDependency.getProjectId(), projectDependency.getVersionId()).getEntities(null, null, null)
        );
    }
}
