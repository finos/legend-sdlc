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

import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;

/**
 * The backend discovery chain end to end: the test configuration declares {@code backend: {type: inMemory}},
 * so the ServiceLoader'd in-memory factory, the polymorphic configuration resolution, and the capabilities
 * discovery endpoint are all exercised for real.
 */
public class TestBackendDiscovery extends AbstractLegendSDLCServerResourceTest
{
    @Test
    @SuppressWarnings("unchecked")
    public void testCapabilities()
    {
        Response response = clientFor("/api/configuration/capabilities").request().get();
        Assert.assertEquals(200, response.getStatus());
        Map<String, ?> capabilitiesInfo = response.readEntity(Map.class);
        Assert.assertEquals("inMemory", capabilitiesInfo.get("backendType"));
        Assert.assertTrue("expected USER_WORKSPACES among capabilities, got " + capabilitiesInfo.get("capabilities"),
                ((List<String>) capabilitiesInfo.get("capabilities")).contains("USER_WORKSPACES"));
    }
}
