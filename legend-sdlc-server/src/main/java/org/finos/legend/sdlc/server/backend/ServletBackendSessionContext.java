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

package org.finos.legend.sdlc.server.backend;

import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;
import org.finos.legend.sdlc.backend.api.spi.BackendSessionContext;
import org.finos.legend.sdlc.backend.api.spi.BackendSessionStateStore;
import org.finos.legend.sdlc.server.guice.UserContext;

import java.util.Objects;

/**
 * The server's {@link BackendSessionContext}: identity from the authenticated {@link UserContext}.
 * <p>
 * Interim notes for the backend-extraction phase: the state store is request-transient — persistent write-back
 * (through the server session store and cookie refresh) lands when the first backend actually consumes it, i.e.
 * when the GitLab backend leaves the server and stops using {@code GitLabSession} directly. Until then the
 * GitLab backend reaches its servlet-bound machinery by unwrapping {@link #getServerUserContext()}.
 */
public class ServletBackendSessionContext implements BackendSessionContext
{
    private final UserContext userContext;
    private final MutableMap<String, String> transientState = Maps.mutable.empty();

    public ServletBackendSessionContext(UserContext userContext)
    {
        this.userContext = Objects.requireNonNull(userContext, "userContext may not be null");
    }

    @Override
    public String getUserId()
    {
        return this.userContext.getCurrentUser();
    }

    @Override
    public BackendSessionStateStore getStateStore()
    {
        return new BackendSessionStateStore()
        {
            @Override
            public String get(String key)
            {
                return ServletBackendSessionContext.this.transientState.get(key);
            }

            @Override
            public void put(String key, String value)
            {
                if (value == null)
                {
                    ServletBackendSessionContext.this.transientState.remove(key);
                }
                else
                {
                    ServletBackendSessionContext.this.transientState.put(key, value);
                }
            }
        };
    }

    public UserContext getServerUserContext()
    {
        return this.userContext;
    }
}
