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
import org.junit.Test;

import javax.ws.rs.core.Response;

/**
 * The generic {@code /auth} surface (which replaced the per-backend auth resources) on a server with no
 * authenticated session: the authorization check reports not-authorized without erroring; the session-bound
 * routes fail as any session-bound route does on a session-less request (a 500 from the provision failure of
 * the session-scoped user context - the same wire behavior the per-backend resources had).
 */
public class TestAuthResources extends AbstractLegendSDLCServerResourceTest
{
    @Test
    public void testAuthorizedWithoutSession()
    {
        Response response = clientFor("/api/auth/authorized").request().get();
        Assert.assertEquals(200, response.getStatus());
        Assert.assertEquals(Boolean.FALSE, response.readEntity(Boolean.class));
    }

    @Test
    public void testAuthorizeWithoutSession()
    {
        Response response = clientFor("/api/auth/authorize").request().get();
        Assert.assertEquals(500, response.getStatus());
    }

    @Test
    public void testTermsOfServiceAcceptanceWithoutSession()
    {
        Response response = clientFor("/api/auth/termsOfServiceAcceptance").request().get();
        Assert.assertEquals(500, response.getStatus());
    }
}
