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

package org.finos.legend.sdlc.backend.api.spi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.finos.legend.sdlc.backend.api.tools.BackgroundTaskProcessor;
import org.finos.legend.sdlc.project.structure.ProjectStructurePlatformExtensions;
import org.finos.legend.sdlc.project.structure.extension.ProjectStructureExtensionProvider;

/**
 * Deployment-scoped services the host supplies to {@link BackendFactory#build}. Everything here is built once
 * and user-independent; per-user material crosses the SPI on {@link BackendSessionContext} instead.
 * <p>
 * The project structure extension provider and platform extensions are the deployment's, not the backend's:
 * they pass through this environment to the generic default implementations, and a backend jar must not bundle
 * them (extensions are deployment-scoped configuration). This interface grows by {@code default} methods.
 */
public interface BackendEnvironment
{
    /**
     * A general-purpose object mapper for the backend's internal serialization needs. Deliberately not the
     * host's wire-format mapper: wire concerns (mix-ins, serialization features, protocol extensions) are the
     * host's contract with its clients, not with backends, and neither side's serialization should drift with
     * the other's tuning. Backends needing particular modules configure their own mapper. (Backend
     * configuration parsing does not run on this mapper either — that is the host's configuration mapper,
     * which {@link BackendFactory#configureObjectMapper} can adjust.)
     *
     * @return object mapper
     */
    ObjectMapper getObjectMapper();

    BackgroundTaskProcessor getTaskProcessor();

    ProjectStructureExtensionProvider getProjectStructureExtensionProvider();

    ProjectStructurePlatformExtensions getProjectStructurePlatformExtensions();

    /**
     * The deployment's project-creation policy, as data (default structure version, coordinate patterns). Null
     * means no policy — backends use their own defaults.
     *
     * @return project creation configuration or null
     */
    default ProjectCreationConfiguration getProjectCreationConfiguration()
    {
        return null;
    }

    /**
     * A deployment service published by the host under the given type, or null if the host publishes none. An
     * escape hatch for backend-specific needs that are not part of the SPI contract proper (a backend that is
     * co-deployed with a particular host may look up that host's services); generic code must not rely on it.
     *
     * @param serviceType service type
     * @param <T>         service type
     * @return service or null
     */
    default <T> T getService(Class<T> serviceType)
    {
        return null;
    }
}
