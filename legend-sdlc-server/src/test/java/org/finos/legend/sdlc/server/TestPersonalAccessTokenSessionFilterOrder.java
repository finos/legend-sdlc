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

package org.finos.legend.sdlc.server;

import com.sun.net.httpserver.HttpServer;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.testing.DropwizardTestSupport;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import org.finos.legend.sdlc.server.auth.LegendSDLCWebFilter;
import org.finos.legend.sdlc.server.auth.Session;
import org.finos.legend.sdlc.server.config.LegendSDLCServerConfiguration;
import org.finos.legend.sdlc.server.guice.UserContext;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@RunWith(Parameterized.class)
public class TestPersonalAccessTokenSessionFilterOrder
{
    @ClassRule
    public static final TemporaryFolder TMP = new TemporaryFolder();

    private static final String TOKEN_HEADER = "PRIVATE-TOKEN";
    private static final String SESSION_PROBE_HEADER = "X-Session-Probe";
    private static final AtomicInteger HTTP_SESSIONS_CREATED = new AtomicInteger();

    // legend-shared's ChainFixingFilterHandler exposes the order it sorts the filters into only as a java.util.logging record
    private static final String CHAIN_FIXING_LOGGER = "org.finos.legend.server.shared.bundles.ChainFixingFilterHandler";
    private static final String REORDERED = "Reordered filters as ";
    private static final Logger CHAIN_FIXING = Logger.getLogger(CHAIN_FIXING_LOGGER);
    private static final Handler FILTER_ORDER_HANDLER = new Handler()
    {
        @Override
        public void publish(LogRecord record)
        {
            if ((record.getMessage() != null) && record.getMessage().startsWith(REORDERED))
            {
                filterOrder = record.getMessage().substring(REORDERED.length());
            }
        }

        @Override
        public void flush()
        {
        }

        @Override
        public void close()
        {
        }
    };
    private static volatile String filterOrder;

    private static HttpServer gitlab;
    private static DropwizardTestSupport<LegendSDLCServerConfiguration> app;
    private static String appShape;

    @Parameterized.Parameter(0)
    public String shape;

    @Parameterized.Parameter(1)
    public List<String> filterPriorities;

    // whether the session filter stays ahead of the filters without a priority
    @Parameterized.Parameter(2)
    public boolean sessionBeforeUnprioritisedFilters;

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<Object[]> shapes()
    {
        return Arrays.asList(
                // session filter ordered under its historical name, no pac4j filter entries
                new Object[]{"legacy", Arrays.asList("GitLab: 1", "CORS: 4"), false},
                // config.json, config-sample.yaml and config-test.yaml
                new Object[]{"shipped", Arrays.asList("GitLab: 1", "org.pac4j.jee.filter.CallbackFilter: 2", "org.pac4j.jee.filter.SecurityFilter: 3", "CORS: 4"), true},
                // session filter ordered before every pac4j filter
                new Object[]{"highSessionPriority", Arrays.asList("GitLab: 10", "org.pac4j.jee.filter.CallbackFilter: 2", "org.pac4j.jee.filter.SecurityFilter: 3", "CORS: 4"), true},
                // session filter below the default priority, no pac4j filter entries
                new Object[]{"negativeSessionPriority", Arrays.asList("GitLab: -5", "CORS: 4"), false},
                // session filter not named
                new Object[]{"securityFilterLowered", Arrays.asList("org.pac4j.jee.filter.SecurityFilter: -1", "CORS: 4"), false},
                // session filter not named: unnamed filters keep registration order, which has it before pac4j's
                new Object[]{"unnamed", Collections.singletonList("CORS: 4"), false},
                // no filterPriorities (an empty YAML value is null)
                new Object[]{"none", Collections.emptyList(), false});
    }

    @BeforeClass
    public static void startGitLab() throws Exception
    {
        gitlab = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        gitlab.createContext("/api/v4/user", exchange ->
        {
            boolean ok = "glpat-secret".equals(exchange.getRequestHeaders().getFirst(TOKEN_HEADER));
            byte[] body = (ok ? "{\"id\":42,\"username\":\"someone\",\"name\":\"Some One\"}" : "{}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(ok ? 200 : 401, body.length);
            try (OutputStream out = exchange.getResponseBody())
            {
                out.write(body);
            }
        });
        gitlab.start();
    }

    @Before
    public void startServer() throws Exception
    {
        // Parameterized runs every test of one shape before the next shape, so each shape starts one server
        if (this.shape.equals(appShape))
        {
            return;
        }
        stopServer();

        StringBuilder priorities = new StringBuilder("filterPriorities:");
        this.filterPriorities.forEach(p -> priorities.append("\n  ").append(p));
        String config = String.join("\n",
                "applicationName: Legend SDLC",
                "sessionCookie: LEGEND_SDLC_JSESSIONID",
                "server:",
                "  applicationConnectors:",
                "    - type: http",
                "      port: 0",
                "  adminConnectors:",
                "    - type: http",
                "      port: 0",
                "  requestLog:",
                "    type: classic",
                "    appenders: []",
                "  rootPath: /api",
                priorities.toString(),
                "pac4j:",
                "  clients:",
                "    - org.finos.legend.server.pac4j.gitlab.GitlabPersonalAccessTokenClient:",
                "        name: gitlabPAToken",
                "        headerTokenName: " + TOKEN_HEADER,
                "        scheme: http",
                "        host: 127.0.0.1",
                "        port: " + gitlab.getAddress().getPort(),
                "        apiVersion: v4",
                "  bypassPaths:",
                "    - /api/info",
                "backend:",
                "  type: inMemory",
                "logging:",
                "  level: OFF",
                "  loggers:",
                "    " + CHAIN_FIXING_LOGGER + ": INFO",
                "swagger:",
                "  resourcePackage: org.finos.legend.sdlc.server.resources",
                "  title: Legend SDLC",
                "  version: test",
                "  schemes: []",
                "");
        File configFile = TMP.newFile("config-pat-" + this.shape + ".yaml");
        Files.write(configFile.toPath(), config.getBytes(StandardCharsets.UTF_8));

        filterOrder = null;
        app = new DropwizardTestSupport<>(ServerWithUserProbe.class, configFile.getAbsolutePath());
        app.before();
        appShape = this.shape;
        CHAIN_FIXING.setLevel(Level.INFO);
        CHAIN_FIXING.removeHandler(FILTER_ORDER_HANDLER);
        CHAIN_FIXING.addHandler(FILTER_ORDER_HANDLER);
        // the chain is reordered lazily, on the first request
        HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + app.getLocalPort() + "/api/info")).GET().build(), HttpResponse.BodyHandlers.discarding());
    }

    @AfterClass
    public static void stop()
    {
        stopServer();
        CHAIN_FIXING.removeHandler(FILTER_ORDER_HANDLER);
        if (gitlab != null)
        {
            gitlab.stop(0);
        }
    }

    private static void stopServer()
    {
        if (app != null)
        {
            app.after();
            app = null;
            appShape = null;
        }
    }

    @Test
    public void tokenCallerGetsSessionOnEveryRequest() throws Exception
    {
        HttpClient client = HttpClient.newBuilder().cookieHandler(new java.net.CookieManager()).build();
        URI probe = URI.create("http://localhost:" + app.getLocalPort() + "/api/probe/user?client_name=gitlabPAToken");

        HttpResponse<String> first = client.send(HttpRequest.newBuilder(probe).header(TOKEN_HEADER, "glpat-secret").GET().build(), HttpResponse.BodyHandlers.ofString());
        String order = filterOrder();
        Assert.assertEquals("first token request; filter order " + order + "; body " + first.body(), 200, first.statusCode());
        Assert.assertEquals("someone", first.body());

        // the second request also carries whatever cookies the first response set
        HttpResponse<String> second = client.send(HttpRequest.newBuilder(probe).header(TOKEN_HEADER, "glpat-secret").GET().build(), HttpResponse.BodyHandlers.ofString());
        Assert.assertEquals("second token request; filter order " + order + "; body " + second.body(), 200, second.statusCode());
        Assert.assertEquals("someone", second.body());
    }

    @Test
    public void sessionFilterRunsAfterEveryPac4jFilter() throws Exception
    {
        List<String> order = Arrays.asList(filterOrder().split(" -> "));
        int session = order.indexOf(BaseLegendSDLCServer.SESSION_FILTER_NAME);
        Assert.assertTrue(order.toString(), session >= 0);
        // the logout and callback filters end the chain on their paths, so the session filter never runs there
        for (String pac4jFilter : Arrays.asList("org.pac4j.jee.filter.SecurityFilter", "org.pac4j.jee.filter.CallbackFilter", "org.pac4j.jee.filter.LogoutFilter"))
        {
            int pac4j = order.indexOf(pac4jFilter);
            Assert.assertTrue(pac4jFilter + " is not registered: " + order, pac4j >= 0);
            Assert.assertTrue("session filter must follow " + pac4jFilter + ": " + order, session > pac4j);
        }
    }

    @Test
    public void filterWithoutPriorityAfterTheSessionFilterSeesTheSession() throws Exception
    {
        Assume.assumeTrue("the session filter runs after the filters without a priority in this shape", this.sessionBeforeUnprioritisedFilters);
        HttpClient client = HttpClient.newBuilder().cookieHandler(new java.net.CookieManager()).build();
        URI probe = URI.create("http://localhost:" + app.getLocalPort() + "/api/probe/user?client_name=gitlabPAToken");
        for (int i = 1; i <= 2; i++)
        {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(probe).header(TOKEN_HEADER, "glpat-secret").GET().build(), HttpResponse.BodyHandlers.ofString());
            Assert.assertEquals("token request " + i + "; filter order " + filterOrder() + "; body " + response.body(), 200, response.statusCode());
            Assert.assertEquals("token request " + i + "; filter order " + filterOrder(), "someone", response.headers().firstValue(SESSION_PROBE_HEADER).orElse(null));
        }
    }

    @Test
    public void cookielessTokenCallerCreatesNoHttpSession() throws Exception
    {
        // java.net.http.HttpClient keeps no cookies unless given a cookie handler
        HttpClient client = HttpClient.newHttpClient();
        URI probe = URI.create("http://localhost:" + app.getLocalPort() + "/api/probe/user?client_name=gitlabPAToken");
        int before = HTTP_SESSIONS_CREATED.get();
        for (int i = 1; i <= 3; i++)
        {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(probe).header(TOKEN_HEADER, "glpat-secret").GET().build(), HttpResponse.BodyHandlers.ofString());
            Assert.assertEquals("token request " + i + "; body " + response.body(), 200, response.statusCode());
            Assert.assertEquals("someone", response.body());
            Assert.assertEquals(response.headers().allValues("Set-Cookie").toString(), Collections.emptyList(),
                    response.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("LEGEND_SDLC_JSESSIONID=")).collect(Collectors.toList()));
        }
        Assert.assertEquals("HttpSessions created by cookieless token requests", 0, HTTP_SESSIONS_CREATED.get() - before);
    }

    private static String filterOrder()
    {
        Assert.assertNotNull("ChainFixingFilterHandler did not log the filter order", filterOrder);
        return filterOrder;
    }

    public static class ServerWithUserProbe extends LegendSDLCServerForTest
    {
        @Override
        public void run(LegendSDLCServerConfiguration configuration, Environment environment)
        {
            super.run(configuration, environment);
            environment.jersey().register(new UserProbeResource());
            // registered without a priority, as a deployment's own filter would be
            environment.servlets().addFilter("SessionProbe", new SessionProbeFilter())
                    .addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
            environment.servlets().addServletListeners(new HttpSessionListener()
            {
                @Override
                public void sessionCreated(HttpSessionEvent event)
                {
                    HTTP_SESSIONS_CREATED.incrementAndGet();
                }
            });
        }
    }

    /**
     * Reports the SDLC session it sees in a response header.
     */
    public static class SessionProbeFilter implements Filter
    {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException
        {
            Session session = LegendSDLCWebFilter.getSessionFromServletRequestAttribute(request);
            ((HttpServletResponse) response).setHeader(SESSION_PROBE_HEADER, (session == null) ? "none" : session.getUserId());
            chain.doFilter(request, response);
        }
    }

    @Path("/probe")
    public static class UserProbeResource
    {
        @GET
        @Path("user")
        @Produces(MediaType.TEXT_PLAIN)
        public String user(@Context HttpServletRequest request, @Context HttpServletResponse response)
        {
            return new UserContext(request, response).getCurrentUser();
        }
    }
}
