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

package org.finos.legend.sdlc.backend.tck;

import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;
import org.finos.legend.sdlc.backend.api.spi.Backend;
import org.finos.legend.sdlc.backend.api.spi.BackendCapability;
import org.finos.legend.sdlc.backend.api.spi.BackendSession;
import org.finos.legend.sdlc.backend.api.spi.BackendSessionContext;
import org.finos.legend.sdlc.backend.api.spi.BackendSessionStateStore;
import org.finos.legend.sdlc.backend.api.spi.UnsupportedCapabilityException;
import org.junit.Assert;
import org.junit.Test;

import java.util.Set;
import java.util.function.Function;

/**
 * Executable contract for the {@code Backend} SPI's capability model: declared capabilities' accessors return an
 * api; undeclared capabilities' accessors throw {@link UnsupportedCapabilityException} carrying the capability
 * and status 501 — never {@code UnsupportedOperationException} or a null. Backend authors subclass this (and
 * {@link LayoutInvariantsTestSuite} for the storage contract) and supply their backend.
 */
public abstract class BackendContractTestSuite
{
    /**
     * Create the backend under test. Called per test; may return a shared instance.
     *
     * @return backend
     */
    protected abstract Backend newBackend();

    /**
     * A session context for a plain authenticated user, suitable for {@code Backend.newSession}. The default is
     * a simple in-memory context with user id "tck-user"; backends that require a richer context override this.
     *
     * @return session context
     */
    protected BackendSessionContext newSessionContext()
    {
        return newSessionContext("tck-user");
    }

    protected BackendSessionContext newSessionContext(String userId)
    {
        MutableMap<String, String> state = Maps.mutable.empty();
        return new BackendSessionContext()
        {
            @Override
            public String getUserId()
            {
                return userId;
            }

            @Override
            public BackendSessionStateStore getStateStore()
            {
                return new BackendSessionStateStore()
                {
                    @Override
                    public String get(String key)
                    {
                        return state.get(key);
                    }

                    @Override
                    public void put(String key, String value)
                    {
                        if (value == null)
                        {
                            state.remove(key);
                        }
                        else
                        {
                            state.put(key, value);
                        }
                    }
                };
            }
        };
    }

    @Test
    public void testBackendIdentity()
    {
        try (Backend backend = newBackend())
        {
            Assert.assertNotNull("backend type", backend.getType());
            Set<BackendCapability> capabilities = backend.getCapabilities();
            Assert.assertNotNull("capabilities", capabilities);
            Assert.assertTrue("a backend must declare at least one workspace flavor",
                    capabilities.contains(BackendCapability.USER_WORKSPACES) || capabilities.contains(BackendCapability.GROUP_WORKSPACES));
        }
    }

    @Test
    public void testSessionUserId()
    {
        try (Backend backend = newBackend())
        {
            BackendSession session = backend.newSession(newSessionContext("tck-user"));
            Assert.assertNotNull("session", session);
            Assert.assertEquals("tck-user", session.getUserId());
        }
    }

    @Test
    public void testCapabilityGatedAccessors()
    {
        try (Backend backend = newBackend())
        {
            Set<BackendCapability> capabilities = backend.getCapabilities();
            BackendSession session = backend.newSession(newSessionContext());

            assertAccessor(session, capabilities, BackendCapability.REVIEWS, BackendSession::getReviewApi);
            assertAccessor(session, capabilities, BackendCapability.VERSIONS, BackendSession::getVersionApi);
            assertAccessor(session, capabilities, BackendCapability.PATCHES, BackendSession::getPatchApi);
            assertAccessor(session, capabilities, BackendCapability.WORKFLOWS, BackendSession::getWorkflowApi);
            assertAccessor(session, capabilities, BackendCapability.WORKFLOWS, BackendSession::getWorkflowJobApi);
            assertAccessor(session, capabilities, BackendCapability.BUILDS, BackendSession::getBuildApi);
            assertAccessor(session, capabilities, BackendCapability.BACKUP, BackendSession::getBackupApi);
            assertAccessor(session, capabilities, BackendCapability.CONFLICT_RESOLUTION, BackendSession::getConflictResolutionApi);
            assertAccessor(session, capabilities, BackendCapability.ISSUES, BackendSession::getIssueApi);
        }
    }

    private void assertAccessor(BackendSession session, Set<BackendCapability> capabilities, BackendCapability capability, Function<? super BackendSession, ?> accessor)
    {
        if (capabilities.contains(capability))
        {
            Assert.assertNotNull("declared capability " + capability + " must yield an api", accessor.apply(session));
        }
        else
        {
            UnsupportedCapabilityException e = Assert.assertThrows(
                    "undeclared capability " + capability + " must throw UnsupportedCapabilityException",
                    UnsupportedCapabilityException.class,
                    () -> accessor.apply(session));
            Assert.assertEquals(capability, e.getCapability());
            Assert.assertEquals(UnsupportedCapabilityException.STATUS_CODE, e.getStatusCode());
        }
    }
}
