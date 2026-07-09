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

package org.finos.legend.sdlc.core.dependency;

import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.set.MutableSet;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.function.Function;

/**
 * Generic project-dependency resolution over project configurations. Factored out of the server's
 * {@code DependenciesApiImpl} in re-architecture Phase 3: the traversal is backend-neutral, parameterized over a
 * resolver that produces the {@link ProjectConfiguration} of a dependency (at its depended-upon version) — a server
 * backend resolves through its configuration api, local tooling can resolve through project files.
 */
public class DependencyOperations
{
    private DependencyOperations()
    {
    }

    /**
     * Get the upstream project dependencies of the given configuration. When {@code transitive} is false, this is
     * simply the configuration's own dependency list (as a set); when true, the dependency graph is walked
     * breadth-first, resolving each dependency's configuration with {@code dependencyConfigurationResolver}.
     */
    public static Set<ProjectDependency> getUpstreamDependencies(ProjectConfiguration rootProjectConfiguration, boolean transitive, Function<? super ProjectDependency, ? extends ProjectConfiguration> dependencyConfigurationResolver)
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
                ProjectConfiguration dependencyProjectConfig = dependencyConfigurationResolver.apply(dependency);
                deque.addAll(dependencyProjectConfig.getProjectDependencies());
            }
        }
        return results;
    }
}
