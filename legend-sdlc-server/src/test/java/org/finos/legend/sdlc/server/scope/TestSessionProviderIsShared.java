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
import org.finos.legend.sdlc.server.auth.LegendSDLCWebFilter;
import org.finos.legend.sdlc.server.config.LegendSDLCServerConfiguration;
import org.finos.legend.sdlc.server.tools.SessionProvider;
import org.finos.legend.server.pac4j.mongostore.MongoDbSessionStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.InetSocketAddress;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@ExtendWith(DropwizardExtensionsSupport.class)
public class TestSessionProviderIsShared
{
    private static final MongoServer MONGO = new MongoServer(new MemoryBackend());
    private static final InetSocketAddress MONGO_ADDRESS = MONGO.bind();

    // the driver names its monitor threads after the client's cluster id: one distinct id per live MongoClient
    private static final Pattern CLUSTER_ID = Pattern.compile("ClusterId\\{value='([0-9a-f]+)'");

    @RegisterExtension
    public static final DropwizardAppExtension<LegendSDLCServerConfiguration> APP =
            new DropwizardAppExtension<>(RealModuleServerForTest.class, ResourceHelpers.resourceFilePath("config-real-module-mongo-test.yaml"),
                    ConfigOverride.config("pac4j.mongoUri", "mongodb://localhost:" + MONGO_ADDRESS.getPort()));

    @AfterAll
    public static void stopMongo()
    {
        MONGO.shutdown();
    }

    @Test
    public void theSessionProviderIsProvisionedOnce()
    {
        RealModuleServerForTest server = APP.getApplication();
        SessionProvider first = server.getGuiceBundle().getInjector().getInstance(SessionProvider.class);
        SessionProvider second = server.getGuiceBundle().getInjector().getInstance(SessionProvider.class);
        Assertions.assertSame(first, second);
    }

    @Test
    public void thePac4jBundlePublishesItsMongoSessionStore()
    {
        Object store = APP.getEnvironment().getApplicationContext().getAttribute(LegendSDLCWebFilter.SESSION_STORE);
        Assertions.assertTrue(store instanceof MongoDbSessionStore, String.valueOf(store));
    }

    @Test
    public void authorizationChecksDoNotCreateMongoClients() throws Exception
    {
        Client client = ClientBuilder.newClient();
        try
        {
            authorized(client); // first use may legitimately initialize lazily
            Set<String> before = liveMongoClients();
            for (int i = 0; i < 5; i++)
            {
                authorized(client);
            }
            Set<String> after = liveMongoClients();
            after.removeAll(before);
            Assertions.assertEquals(Set.of(), after, () -> "MongoClients created by 5 authorization checks (before: " + before + ")");
        }
        finally
        {
            client.close();
        }
    }

    private static void authorized(Client client)
    {
        try (Response response = client.target("http://localhost:" + APP.getLocalPort() + "/api/auth/authorized").request(MediaType.APPLICATION_JSON).get())
        {
            String body = response.readEntity(String.class);
            Assertions.assertEquals(200, response.getStatus(), body);
            Assertions.assertEquals("false", body.trim());
        }
    }

    private static Set<String> liveMongoClients()
    {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(Thread::isAlive)
                .map(t -> CLUSTER_ID.matcher(t.getName()))
                .filter(Matcher::find)
                .map(m -> m.group(1))
                .collect(Collectors.toSet());
    }
}
