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

package org.finos.legend.sdlc.server.scope;

import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.finos.legend.sdlc.server.config.LegendSDLCServerConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import ru.vyarus.dropwizard.guice.GuiceBundle;

/**
 * A server that builds its own GuiceBundle, rather than extending buildGuiceBundle()'s, still gets per-request
 * resources.
 */
@ExtendWith(DropwizardExtensionsSupport.class)
public class TestResourcesArePerRequestWithOwnGuiceBundle
{
    @RegisterExtension
    public static final DropwizardAppExtension<LegendSDLCServerConfiguration> APP =
            new DropwizardAppExtension<>(OwnGuiceBundleServer.class, ResourceHelpers.resourceFilePath("config-real-module-test.yaml"));

    @Test
    public void eachCallerSeesTheirOwnCurrentUser()
    {
        Client client = ClientBuilder.newClient();
        try
        {
            Assertions.assertEquals("alice", currentUser(client, "alice"));
            Assertions.assertEquals("bob", currentUser(client, "bob"));
            Assertions.assertEquals("alice", currentUser(client, "alice"));
        }
        finally
        {
            client.close();
        }
    }

    private static String currentUser(Client client, String user)
    {
        try (Response response = client.target("http://localhost:" + APP.getLocalPort() + "/api/currentUser").request(MediaType.APPLICATION_JSON).header(RealModuleServerForTest.USER_HEADER, user).get())
        {
            String body = response.readEntity(String.class);
            Assertions.assertEquals(200, response.getStatus(), body);
            return body.replaceAll("(?s).*\"userId\"\\s*:\\s*\"([^\"]*)\".*", "$1");
        }
    }

    public static class OwnGuiceBundleServer extends RealModuleServerForTest
    {
        @Override
        protected GuiceBundle buildGuiceBundle()
        {
            return GuiceBundle.builder()
                    .modules(buildBaseModule())
                    .build();
        }
    }
}
