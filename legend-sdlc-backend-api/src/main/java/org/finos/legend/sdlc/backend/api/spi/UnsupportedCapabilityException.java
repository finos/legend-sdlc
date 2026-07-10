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

import org.finos.legend.sdlc.error.LegendSDLCException;

import java.util.Objects;

/**
 * Thrown when an optional {@link BackendCapability} is exercised against a backend that does not declare it —
 * from the {@link BackendSession} accessor for a whole-concept API, or from a cross-API scope method (e.g. a
 * version-scoped access context when {@link BackendCapability#VERSIONS} is absent).
 * <p>
 * Carries status 501 (Not Implemented): the route exists, but this deployment's backend does not support the
 * functionality. It is deliberately not 404, which throughout the API means "no such
 * project/workspace/entity/review".
 */
public class UnsupportedCapabilityException extends LegendSDLCException
{
    private static final long serialVersionUID = 1L;

    public static final int STATUS_CODE = 501;

    private final BackendCapability capability;
    private final String backendType;

    public UnsupportedCapabilityException(BackendCapability capability, String backendType)
    {
        super(buildMessage(capability, backendType), STATUS_CODE);
        this.capability = capability;
        this.backendType = backendType;
    }

    public BackendCapability getCapability()
    {
        return this.capability;
    }

    /**
     * The backend type (as in {@link Backend#getType()}), if known; otherwise, null.
     *
     * @return backend type or null
     */
    public String getBackendType()
    {
        return this.backendType;
    }

    private static String buildMessage(BackendCapability capability, String backendType)
    {
        Objects.requireNonNull(capability, "capability may not be null");
        StringBuilder builder = new StringBuilder("The backend");
        if (backendType != null)
        {
            builder.append(" \"").append(backendType).append('"');
        }
        return builder.append(" does not support ").append(capability).toString();
    }
}
