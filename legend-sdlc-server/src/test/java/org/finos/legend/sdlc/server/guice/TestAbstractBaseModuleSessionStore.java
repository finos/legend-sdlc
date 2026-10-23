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

package org.finos.legend.sdlc.server.guice;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import org.bson.Document;
import org.finos.legend.server.pac4j.LegendPac4jConfiguration;
import org.finos.legend.server.pac4j.hazelcaststore.HazelcastSessionStore;
import org.finos.legend.server.pac4j.mongostore.MongoDbSessionStore;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.pac4j.core.context.session.SessionStore;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Must choose the same store {@code LegendPac4jBundle} chooses for pac4j: Hazelcast first, then
 * Mongo, otherwise none.
 */
public class TestAbstractBaseModuleSessionStore
{
    private static final String SESSION_COLLECTION = "userSessions";

    private static MongoServer server;
    private static String mongoUri;

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    private final List<String> hazelcastInstanceNames = new ArrayList<>();

    @BeforeClass
    public static void startMongo()
    {
        server = new MongoServer(new MemoryBackend());
        InetSocketAddress address = server.bind();
        mongoUri = "mongodb://" + address.getHostName() + ":" + address.getPort();
    }

    @AfterClass
    public static void stopMongo()
    {
        server.shutdown();
    }

    @After
    public void stopHazelcast()
    {
        for (String name : this.hazelcastInstanceNames)
        {
            HazelcastInstance instance = Hazelcast.getHazelcastInstanceByName(name);
            if (instance != null)
            {
                instance.shutdown();
            }
        }
    }

    @Test
    public void noSessionStoreConfiguredGivesNone()
    {
        Assert.assertNull(AbstractBaseModule.getSessionStoreFromConfig(new LegendPac4jConfiguration()));
    }

    @Test
    public void aDisabledMongoSessionGivesNone()
    {
        LegendPac4jConfiguration config = mongoConfig(mongoUri, "test");
        config.getMongoSession().setEnabled(false);
        Assert.assertNull(AbstractBaseModule.getSessionStoreFromConfig(config));
    }

    @Test
    public void hazelcastSessionGivesAHazelcastStore() throws IOException
    {
        LegendPac4jConfiguration config = new LegendPac4jConfiguration();
        config.setHazelcastSession(hazelcastSession("hazelcastOnly"));

        Assert.assertTrue(AbstractBaseModule.getSessionStoreFromConfig(config) instanceof HazelcastSessionStore);
    }

    /**
     * MongoDbSessionStore creates its TTL index on the collection it is given, so finding that index
     * proves the configured URI, database and collection were used.
     */
    @Test
    public void mongoSessionGivesAMongoStoreOnTheConfiguredCollection()
    {
        LegendPac4jConfiguration config = mongoConfig(mongoUri, "mongoSessionDb");

        SessionStore store = AbstractBaseModule.getSessionStoreFromConfig(config);

        Assert.assertTrue(String.valueOf(store), store instanceof MongoDbSessionStore);
        try (MongoClient client = MongoClients.create(mongoUri))
        {
            List<String> indexes = new ArrayList<>();
            for (Document index : client.getDatabase("mongoSessionDb").getCollection(SESSION_COLLECTION).listIndexes())
            {
                indexes.add(index.getString("name"));
            }
            Assert.assertTrue(indexes.toString(), indexes.contains("ttl"));
        }
    }

    @Test
    public void mongoSessionWithoutAMongoUriIsRefused()
    {
        assertRefused(mongoConfig(null, "test"));
        assertRefused(mongoConfig(mongoUri, null));
        assertRefused(mongoConfig("", "test"));
    }

    @Test
    public void hazelcastTakesPrecedenceOverMongo() throws IOException
    {
        LegendPac4jConfiguration config = mongoConfig(mongoUri, "test");
        config.setHazelcastSession(hazelcastSession("bothEnabled"));

        Assert.assertTrue(AbstractBaseModule.getSessionStoreFromConfig(config) instanceof HazelcastSessionStore);
    }

    private static void assertRefused(LegendPac4jConfiguration config)
    {
        RuntimeException e = Assert.assertThrows(RuntimeException.class, () -> AbstractBaseModule.getSessionStoreFromConfig(config));
        Assert.assertEquals("MongoDB needs to be configured if MongoSession is used", e.getMessage());
    }

    private static LegendPac4jConfiguration mongoConfig(String uri, String db)
    {
        LegendPac4jConfiguration.MongoSessionConfiguration mongoSession = new LegendPac4jConfiguration.MongoSessionConfiguration();
        mongoSession.setEnabled(true);
        mongoSession.setCollection(SESSION_COLLECTION);
        LegendPac4jConfiguration config = new LegendPac4jConfiguration();
        config.setMongoSession(mongoSession);
        config.setMongoUri(uri);
        config.setMongoDb(db);
        return config;
    }

    // Standalone member: own cluster name, no joining and an auto-incremented port, so it neither
    // collides with nor joins anything else on the machine.
    private LegendPac4jConfiguration.HazelcastSessionConfiguration hazelcastSession(String name) throws IOException
    {
        String instanceName = "legend-sdlc-test-" + name + "-" + System.nanoTime();
        this.hazelcastInstanceNames.add(instanceName);
        Path file = this.tempFolder.newFile(name + ".yaml").toPath();
        Files.write(file, String.join("\n",
                "hazelcast:",
                "  cluster-name: " + instanceName,
                "  instance-name: " + instanceName,
                "  network:",
                "    port:",
                "      port: 5701",
                "      auto-increment: true",
                "    join:",
                "      auto-detection:",
                "        enabled: false",
                "      multicast:",
                "        enabled: false",
                "      tcp-ip:",
                "        enabled: false",
                "  map:",
                "    session-store:",
                "      time-to-live-seconds: 7200",
                "").getBytes(StandardCharsets.UTF_8));
        LegendPac4jConfiguration.HazelcastSessionConfiguration hazelcastSession = new LegendPac4jConfiguration.HazelcastSessionConfiguration();
        hazelcastSession.setEnabled(true);
        hazelcastSession.setConfigFilePath(file.toString());
        return hazelcastSession;
    }
}
