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

import java.util.Set;

/**
 * A backend: one deployment-scoped object, built once at startup by its {@link BackendFactory} from
 * configuration and the host {@link BackendEnvironment}. Everything user-scoped lives on the
 * {@link BackendSession} obtained from {@link #newSession}.
 */
public interface Backend extends AutoCloseable
{
    /**
     * The backend type, matching {@link BackendFactory#getType()} (e.g. "gitlab", "filesystem").
     *
     * @return backend type
     */
    String getType();

    /**
     * The optional capabilities this backend supports. Deployment-static: the value never varies by user or
     * request. Exercising an undeclared capability throws {@link UnsupportedCapabilityException}.
     *
     * @return declared capabilities
     */
    Set<BackendCapability> getCapabilities();

    /**
     * Create the per-user view for the given session context. Called by the host per request; must be cheap.
     * See {@link BackendSession} for the contract.
     *
     * @param context host-provided user identity and state
     * @return per-user session
     */
    BackendSession newSession(BackendSessionContext context);

    /**
     * Release deployment-scoped resources. The host ties this to its lifecycle.
     */
    @Override
    default void close()
    {
    }
}
