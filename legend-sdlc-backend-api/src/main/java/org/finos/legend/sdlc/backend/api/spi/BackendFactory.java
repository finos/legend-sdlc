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

/**
 * How a backend is bootstrapped. Implementations are registered via
 * {@code META-INF/services/org.finos.legend.sdlc.backend.api.spi.BackendFactory}; the host discovers them,
 * registers each factory's configuration class as a Jackson subtype of {@link BackendConfiguration} under
 * {@link #getType()}, and builds the one backend its configuration selects. The {@code ServiceLoader} lookup
 * itself is a host concern — nothing below the host captures its results.
 */
public interface BackendFactory
{
    /**
     * The backend type this factory builds (e.g. "gitlab", "filesystem"). Also the configuration discriminator
     * value.
     *
     * @return backend type
     */
    String getType();

    /**
     * The configuration class for this backend, deserialized from the host configuration's {@code backend:}
     * section when its {@code type} names this factory.
     *
     * @return configuration class
     */
    Class<? extends BackendConfiguration> getConfigurationClass();

    /**
     * Build the deployment-scoped {@link Backend}.
     *
     * @param configuration this backend's configuration (of {@link #getConfigurationClass()}'s type)
     * @param environment   host-supplied deployment services
     * @return backend
     */
    Backend build(BackendConfiguration configuration, BackendEnvironment environment);
}
