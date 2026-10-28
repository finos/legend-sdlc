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

import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.finos.legend.sdlc.server.config.LegendSDLCServerConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.InetSocketAddress;

@ExtendWith(DropwizardExtensionsSupport.class)
public class TestSessionStoreOutage
{
    private static final MongoServer MONGO = new MongoServer(new MemoryBackend());
    private static final InetSocketAddress MONGO_ADDRESS = MONGO.bind();

    @RegisterExtension
    public static final DropwizardAppExtension<LegendSDLCServerConfiguration> APP =
            new DropwizardAppExtension<>(RealModuleServerForTest.class, ResourceHelpers.resourceFilePath("config-real-module-mongo-test.yaml"),
                    ConfigOverride.config("pac4j.mongoUri", "mongodb://localhost:" + MONGO_ADDRESS.getPort() + "/?serverSelectionTimeoutMS=2000"));

    @Test
    public void anAnonymousAuthorizationCheckSurvivesAStoreOutage()
    {
        MONGO.shutdownNow();
        Client client = ClientBuilder.newClient();
        try
        {
            for (int i = 0; i < 2; i++)
            {
                long start = System.nanoTime();
                try (Response response = client.target("http://localhost:" + APP.getLocalPort() + "/api/auth/authorized").request(MediaType.APPLICATION_JSON).get())
                {
                    String result = response.getStatus() + " " + response.readEntity(String.class).trim();
                    long millis = (System.nanoTime() - start) / 1_000_000;
                    Assertions.assertEquals("200 false", result, () -> "after " + millis + "ms");
                }
            }
        }
        finally
        {
            client.close();
        }
    }
}
