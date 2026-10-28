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

package org.finos.legend.sdlc.server.resources;

import org.junit.Assert;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.Response;

/**
 * The generic {@code /auth} surface (which replaced the per-backend auth resources) on the test server, whose only
 * pac4j client ({@code LocalKerberosClient}) authenticates every local request, so every request has a server
 * session; the no-session paths are covered by {@code TestAuthCheckResource}.
 */
public class TestAuthResources extends AbstractLegendSDLCServerResourceTest
{
    @Test
    public void testAuthorizedWithLocalKerberosSession()
    {
        Response response = clientFor("/api/auth/authorized").request().get();
        Assert.assertEquals(200, response.getStatus());
        Assert.assertEquals(Boolean.TRUE, response.readEntity(Boolean.class));
    }

    @Test
    public void testAuthorizeWithLocalKerberosSession()
    {
        Response response = clientFor("/api/auth/authorize").request().get();
        Assert.assertEquals(200, response.getStatus());
    }

    @Test
    public void testTermsOfServiceAcceptanceWithLocalKerberosSession()
    {
        Response response = clientFor("/api/auth/termsOfServiceAcceptance").request().get();
        Assert.assertEquals(200, response.getStatus());
    }
}
