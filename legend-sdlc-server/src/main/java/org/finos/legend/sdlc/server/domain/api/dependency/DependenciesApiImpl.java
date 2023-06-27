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

package org.finos.legend.sdlc.server.domain.api.dependency;

import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.sdlc.domain.model.project.Project;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.domain.api.project.ProjectApi;
import org.finos.legend.sdlc.server.domain.api.project.ProjectConfigurationApi;
import org.finos.legend.sdlc.server.domain.api.revision.RevisionApi;
import org.finos.legend.sdlc.server.domain.api.project.SourceSpecification;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;

public class DependenciesApiImpl implements DependenciesApi
{
    private final ProjectApi projectApi;
    private final ProjectConfigurationApi projectConfigurationApi;
    private final RevisionApi revisionApi;

    @Inject
    public DependenciesApiImpl(ProjectApi projectApi, ProjectConfigurationApi projectConfigurationApi, RevisionApi revisionApi)
    {
        this.projectApi = projectApi;
        this.projectConfigurationApi = projectConfigurationApi;
        this.revisionApi = revisionApi;
    }

    @Override
    public Set<ProjectDependency> getWorkspaceRevisionUpstreamProjects(String projectId, SourceSpecification sourceSpecification, String revisionId, boolean transitive)
    {
        ProjectConfiguration projectConfiguration = this.projectConfigurationApi.getWorkspaceRevisionProjectConfiguration(projectId, sourceSpecification, revisionId);
        return searchUpstream(projectConfiguration, transitive);
    }

    @Override
    public Set<ProjectDependency> getProjectRevisionUpstreamProjects(String projectId, VersionId patchReleaseVersionId, String revisionId, boolean transitive)
    {
        ProjectConfiguration projectConfiguration = this.projectConfigurationApi.getProjectRevisionProjectConfiguration(projectId, patchReleaseVersionId, revisionId);
        return searchUpstream(projectConfiguration, transitive);
    }

    @Override
    public Set<ProjectDependency> getProjectVersionUpstreamProjects(String projectId, String versionId, boolean transitive)
    {
        ProjectConfiguration projectConfiguration = this.projectConfigurationApi.getVersionProjectConfiguration(projectId, versionId);
        return searchUpstream(projectConfiguration, transitive);
    }

    @Override
    public Set<ProjectRevision> getDownstreamProjects(String projectId)
    {
        /*
            TODO : Maybe enable ElasticSearch for Gitlab https://docs.gitlab.com/ee/integration/elasticsearch.html ??
        */
        List<Project> projects = this.projectApi.getProjects(false,  // false because downstream projects might not be owned by the current user
                null, null, null, null);

        Set<ProjectRevision> results = Sets.mutable.empty();
        for (Project otherProject : projects)
        {
            String otherProjectId = otherProject.getProjectId();
            if (!projectId.equals(otherProjectId))
            {
                Revision otherProjectRevision = this.revisionApi.getProjectRevisionContext(otherProjectId).getCurrentRevision();
                ProjectConfiguration projectConfiguration = this.projectConfigurationApi.getProjectRevisionProjectConfiguration(otherProjectId, otherProjectRevision.getId());
                if (Iterate.anySatisfy(projectConfiguration.getProjectDependencies(), d -> projectId.equals(d.getProjectId())))
                {
                    results.add(new ProjectRevision(otherProject.getProjectId(), otherProjectRevision.getId()));
                }
            }
        }
        return results;
    }

    private Set<ProjectDependency> searchUpstream(ProjectConfiguration rootProjectConfiguration, boolean transitive)
    {
        if (!transitive)
        {
            return Sets.mutable.withAll(rootProjectConfiguration.getProjectDependencies());
        }

        Deque<ProjectDependency> deque = new ArrayDeque<>(rootProjectConfiguration.getProjectDependencies());
        MutableSet<ProjectDependency> results = Sets.mutable.ofInitialCapacity(deque.size());
        while (!deque.isEmpty())
        {
            ProjectDependency dependency = deque.pollFirst();
            if (results.add(dependency))
            {
                ProjectConfiguration dependencyProjectConfig = this.projectConfigurationApi.getVersionProjectConfiguration(dependency.getProjectId(), dependency.getVersionId());
                deque.addAll(dependencyProjectConfig.getProjectDependencies());
            }
        }
        return results;
    }
}
