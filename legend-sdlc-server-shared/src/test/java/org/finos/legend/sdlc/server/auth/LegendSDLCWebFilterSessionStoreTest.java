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

package org.finos.legend.sdlc.server.auth;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import org.bson.Document;
import org.finos.legend.server.pac4j.mongostore.MongoDbSessionStore;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.jee.context.JEEContext;
import org.pac4j.jee.context.session.JEESessionStore;
import org.springframework.mock.web.MockCookie;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class LegendSDLCWebFilterSessionStoreTest
{
    private static final String SSO_COOKIE = "LegendSSO";

    private static MongoServer server;
    private static MongoClient client;
    private static MongoDatabase db;

    private MongoCollection<Document> collection;
    private MongoDbSessionStore store;
    private final List<List<CommonProfile>> newSessionCalls = new ArrayList<>();

    @BeforeClass
    public static void startMongo()
    {
        server = new MongoServer(new MemoryBackend());
        InetSocketAddress address = server.bind();
        client = MongoClients.create("mongodb://" + address.getHostName() + ":" + address.getPort());
        db = client.getDatabase("test");
    }

    @AfterClass
    public static void stopMongo()
    {
        client.close();
        server.shutdown();
    }

    @Before
    public void setUp()
    {
        this.collection = db.getCollection("userSessions");
        this.store = new MongoDbSessionStore("AES", 100, this.collection, Collections.singletonMap(JEEContext.class, new JEESessionStore()), Collections.emptyList(), SSO_COOKIE);
    }

    @After
    public void tearDown()
    {
        this.collection.drop();
    }

    @Test
    public void anAnonymousRequestLeavesNoSessionBehind() throws Exception
    {
        MockHttpServletRequest request = newRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        newFilter().doFilter(request, response, chain);

        Assert.assertSame("an anonymous request must still reach the application", request, chain.getRequest());
        Assert.assertEquals("an anonymous request must not write a Mongo row", 0, this.collection.countDocuments());
        Assert.assertEquals("an anonymous request must not be sent any cookie", Collections.emptyList(), response.getHeaders("Set-Cookie"));
        Assert.assertEquals("no SDLC session may be minted without a profile", Collections.emptyList(), this.newSessionCalls);
    }

    @Test
    public void aReturningRequestWithAnSsoCookieReadsItsProfileFromTheStore() throws Exception
    {
        MockHttpServletRequest login = newRequest();
        MockHttpServletResponse loginResponse = new MockHttpServletResponse();
        saveProfile(login, loginResponse, "alice");
        Assert.assertEquals(1, this.collection.countDocuments());

        // A fresh request object, so no HTTP session: the profile can only come from Mongo.
        MockHttpServletRequest next = newRequest();
        next.setCookies(loginResponse.getCookies());
        MockFilterChain chain = new MockFilterChain();

        newFilter().doFilter(next, new MockHttpServletResponse(), chain);

        Assert.assertNotNull("an authenticated request must reach the application", chain.getRequest());
        Assert.assertEquals(1, this.newSessionCalls.size());
        Assert.assertEquals(Collections.singletonList("alice"), this.newSessionCalls.get(0).stream().map(CommonProfile::getId).collect(Collectors.toList()));
    }

    @Test
    public void theSessionCookieDoesNotReplaceTheSsoCookie() throws Exception
    {
        MockHttpServletRequest request = newRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        saveProfile(request, response, "alice");
        Assert.assertNotNull("precondition: the store wrote the SSO cookie", response.getCookie(SSO_COOKIE));

        newFilter().doFilter(request, response, new MockFilterChain());

        Assert.assertNotNull("the SDLC session cookie was not written", response.getCookie(LegendSDLCWebFilter.SESSION_TOKEN_COOKIE_NAME));
        Assert.assertNotNull("the SDLC session cookie replaced the SSO cookie; headers were " + response.getHeaders("Set-Cookie"), response.getCookie(SSO_COOKIE));
        Assert.assertEquals(response.getHeaders("Set-Cookie").toString(), 2, response.getHeaders("Set-Cookie").size());
    }

    @Test
    public void theSessionCookieCarriesTheSsoCookieAttributes() throws Exception
    {
        MockHttpServletRequest request = newRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        saveProfile(request, response, "alice");

        newFilter().doFilter(request, response, new MockFilterChain());

        String header = response.getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith(LegendSDLCWebFilter.SESSION_TOKEN_COOKIE_NAME + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no SDLC session cookie in " + response.getHeaders("Set-Cookie")));
        Cookie cookie = response.getCookie(LegendSDLCWebFilter.SESSION_TOKEN_COOKIE_NAME);
        Assert.assertTrue(header, cookie.isHttpOnly());
        Assert.assertEquals(header, "/", cookie.getPath());
        Assert.assertEquals(header, "Lax", ((MockCookie) cookie).getSameSite());
        // Secure is left off, as it is on LegendSSO, so plain-http deployments keep working.
        Assert.assertFalse(header, cookie.getSecure());
    }

    @Test
    public void theSessionCookieTakesTheSameSitePolicyOfTheSessionStore() throws Exception
    {
        this.store.setSameSitePolicy("strict");
        MockHttpServletRequest request = newRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        saveProfile(request, response, "alice");

        newFilter().doFilter(request, response, new MockFilterChain());

        MockCookie sso = (MockCookie) response.getCookie(SSO_COOKIE);
        MockCookie sdlc = (MockCookie) response.getCookie(LegendSDLCWebFilter.SESSION_TOKEN_COOKIE_NAME);
        Assert.assertNotNull(response.getHeaders("Set-Cookie").toString(), sdlc);
        Assert.assertEquals(response.getHeaders("Set-Cookie").toString(), "Strict", sdlc.getSameSite());
        Assert.assertEquals(response.getHeaders("Set-Cookie").toString(), sso.getSameSite(), sdlc.getSameSite());
    }

    @Test
    public void withoutALegendSessionStoreTheSessionCookieIsLax()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        LegendSDLCWebFilter.setSessionCookie(request, response, new TestSession("alice"));

        Assert.assertEquals(response.getHeaders("Set-Cookie").toString(), "Lax", ((MockCookie) response.getCookie(LegendSDLCWebFilter.SESSION_TOKEN_COOKIE_NAME)).getSameSite());
    }

    @Test
    public void rewritingTheSessionCookieReplacesOnlyTheEarlierSessionCookie()
    {
        MockHttpServletRequest request = newRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        LegendSDLCWebFilter.setSessionCookie(request, response, new TestSession("alice"));
        response.addHeader("Set-Cookie", SSO_COOKIE + "=abc; Path=/; HttpOnly; SameSite=Lax");
        TestSession refreshed = new TestSession("bob");
        LegendSDLCWebFilter.setSessionCookie(request, response, refreshed);

        List<String> headers = response.getHeaders("Set-Cookie");
        Assert.assertEquals(headers.toString(), 2, headers.size());
        Assert.assertEquals(headers.toString(), refreshed.encode(), response.getCookie(LegendSDLCWebFilter.SESSION_TOKEN_COOKIE_NAME).getValue());
        Assert.assertEquals(headers.toString(), "abc", response.getCookie(SSO_COOKIE).getValue());
    }

    @Test
    public void aSessionStoreFailureIsAnsweredWith503() throws Exception
    {
        SessionStore failing = new JEESessionStore()
        {
            @Override
            public Optional<Object> get(WebContext context, String key)
            {
                throw new IllegalStateException("store unreachable");
            }
        };
        MockHttpServletRequest request = newRequest();
        request.getServletContext().setAttribute(LegendSDLCWebFilter.SESSION_STORE, failing);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        newFilter().doFilter(request, response, chain);

        Assert.assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, response.getStatus());
        Assert.assertNull("a request whose session could not be read must not reach the application as anonymous", chain.getRequest());
        Assert.assertEquals(Collections.emptyList(), this.newSessionCalls);
    }

    @Test
    public void aMalformedSsoCookieIsNoSession() throws Exception
    {
        for (String value : new String[]{"x", "a-b", "zz-zz/1-2", "1-2/"})
        {
            MockHttpServletRequest request = newRequest();
            request.setCookies(new Cookie(SSO_COOKIE, value));
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            newFilter().doFilter(request, response, chain);

            Assert.assertNotEquals("malformed " + SSO_COOKIE + "=" + value + " was answered as a retryable store outage", HttpServletResponse.SC_SERVICE_UNAVAILABLE, response.getStatus());
            Assert.assertEquals("malformed " + SSO_COOKIE + "=" + value, HttpServletResponse.SC_OK, response.getStatus());
            Assert.assertSame("malformed " + SSO_COOKIE + "=" + value + " must pass through as no session", request, chain.getRequest());
            Assert.assertEquals(Collections.emptyList(), this.newSessionCalls);
        }
    }

    @Test
    public void anUnreachableMongoIsStillAnsweredWith503() throws Exception
    {
        MockHttpServletRequest login = newRequest();
        MockHttpServletResponse loginResponse = new MockHttpServletResponse();
        saveProfile(login, loginResponse, "alice");

        MongoServer dyingServer = new MongoServer(new MemoryBackend());
        InetSocketAddress address = dyingServer.bind();
        try (MongoClient dyingClient = MongoClients.create("mongodb://" + address.getHostName() + ":" + address.getPort() + "/?serverSelectionTimeoutMS=500&connectTimeoutMS=500&socketTimeoutMS=500"))
        {
            MongoDbSessionStore dyingStore = new MongoDbSessionStore("AES", 100, dyingClient.getDatabase("test").getCollection("userSessions"),
                    Collections.singletonMap(JEEContext.class, new JEESessionStore()), Collections.emptyList(), SSO_COOKIE);
            dyingServer.shutdownNow();

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.getServletContext().setAttribute(LegendSDLCWebFilter.SESSION_STORE, dyingStore);
            request.setCookies(loginResponse.getCookies());
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            newFilter().doFilter(request, response, chain);

            Assert.assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, response.getStatus());
            Assert.assertNull(chain.getRequest());
        }
    }

    private MockHttpServletRequest newRequest()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getServletContext().setAttribute(LegendSDLCWebFilter.SESSION_STORE, this.store);
        return request;
    }

    private void saveProfile(MockHttpServletRequest request, MockHttpServletResponse response, String userId)
    {
        CommonProfile profile = new CommonProfile();
        profile.setId(userId);
        profile.setClientName("TestClient");
        new ProfileManager(new JEEContext(request, response), this.store).save(true, profile, false);
    }

    private LegendSDLCWebFilter<CommonProfile> newFilter()
    {
        return new LegendSDLCWebFilter<CommonProfile>(CommonProfile.class)
        {
            @Override
            protected Session newSession(List<CommonProfile> profiles, Cookie sessionCookie)
            {
                newSessionCalls.add(new ArrayList<>(profiles));
                return new TestSession(profiles.get(0).getId());
            }
        };
    }

    private static class TestSession implements Session
    {
        private final String userId;
        private final Instant creationTime = Instant.now();

        private TestSession(String userId)
        {
            this.userId = userId;
        }

        @Override
        public String getUserId()
        {
            return this.userId;
        }

        @Override
        public Instant getCreationTime()
        {
            return this.creationTime;
        }

        @Override
        public boolean isValid()
        {
            return true;
        }
    }
}
