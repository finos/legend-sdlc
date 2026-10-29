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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import org.finos.legend.sdlc.server.config.LegendSDLCServerConfiguration;
import org.finos.legend.server.pac4j.gitlab.GitlabClient;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.core.util.Pac4jConstants;
import org.pac4j.jee.context.JEEContext;
import org.pac4j.jee.context.session.JEESessionStore;
import org.pac4j.oidc.profile.OidcProfile;

import java.io.File;
import java.io.OutputStream;
import java.net.CookieManager;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An endpoint that needs no authentication (a pac4j bypass path) is served whoever calls it: an anonymous caller, a
 * caller sending a personal access token, and a caller logged in through GitLab.
 */
public class TestPublicEndpointsAreServed
{
    @ClassRule
    public static final TemporaryFolder TMP = new TemporaryFolder();

    private static final String TOKEN_HEADER = "PRIVATE-TOKEN";
    private static final String TOKEN = "glpat-secret";

    private static HttpServer gitlab;
    private static DropwizardTestSupport<LegendSDLCServerConfiguration> app;

    @BeforeClass
    public static void start() throws Exception
    {
        gitlab = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        gitlab.createContext("/api/v4/user", exchange ->
        {
            boolean ok = TOKEN.equals(exchange.getRequestHeaders().getFirst(TOKEN_HEADER));
            byte[] body = (ok ? "{\"id\":42,\"username\":\"someone\",\"name\":\"Some One\"}" : "{}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(ok ? 200 : 401, body.length);
            try (OutputStream out = exchange.getResponseBody())
            {
                out.write(body);
            }
        });
        gitlab.start();

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
                "    appenders: []",
                "  rootPath: /api",
                "filterPriorities:",
                "  GitLab: 1",
                "  org.pac4j.jee.filter.CallbackFilter: 2",
                "  org.pac4j.jee.filter.SecurityFilter: 3",
                "  CORS: 4",
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
                "    - /api/login/gitlab",
                "backend:",
                "  type: inMemory",
                "logging:",
                "  level: OFF",
                "swagger:",
                "  resourcePackage: org.finos.legend.sdlc.server.resources",
                "  title: Legend SDLC",
                "  version: test",
                "  schemes: []",
                "");
        File configFile = TMP.newFile("config-public-endpoints.yaml");
        Files.write(configFile.toPath(), config.getBytes(StandardCharsets.UTF_8));
        app = new DropwizardTestSupport<>(ServerWithGitLabLogin.class, configFile.getAbsolutePath());
        app.before();
    }

    @AfterClass
    public static void stop()
    {
        if (app != null)
        {
            app.after();
        }
        if (gitlab != null)
        {
            gitlab.stop(0);
        }
    }

    @Test
    public void anonymousCallerIsServed() throws Exception
    {
        assertServed(info(HttpClient.newHttpClient(), false));
    }

    @Test
    public void tokenCallerIsServed() throws Exception
    {
        assertServed(info(HttpClient.newHttpClient(), true));
    }

    @Test
    public void callerLoggedInThroughGitLabIsServed() throws Exception
    {
        HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        HttpResponse<String> login = client.send(HttpRequest.newBuilder(url("/api/login/gitlab")).GET().build(), HttpResponse.BodyHandlers.ofString());
        Assert.assertEquals(login.body(), 200, login.statusCode());
        assertServed(info(client, false));
    }

    private static void assertServed(HttpResponse<String> response)
    {
        Assert.assertEquals(response.body(), 200, response.statusCode());
        Assert.assertFalse("empty body", response.body().isEmpty());
    }

    private static HttpResponse<String> info(HttpClient client, boolean token) throws Exception
    {
        HttpRequest.Builder request = HttpRequest.newBuilder(url("/api/info")).header("Accept", MediaType.APPLICATION_JSON).GET();
        if (token)
        {
            request.header(TOKEN_HEADER, TOKEN);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static URI url(String path)
    {
        return URI.create("http://localhost:" + app.getLocalPort() + path);
    }

    public static class ServerWithGitLabLogin extends LegendSDLCServerForTest
    {
        @Override
        public void run(LegendSDLCServerConfiguration configuration, Environment environment)
        {
            super.run(configuration, environment);
            environment.jersey().register(new GitLabLoginResource());
        }
    }

    /**
     * Stands in for a GitLab OAuth login: stores a GitLab (OIDC) profile where pac4j's default session store keeps
     * profiles.
     */
    @Path("/login")
    public static class GitLabLoginResource
    {
        @GET
        @Path("gitlab")
        @Produces(MediaType.TEXT_PLAIN)
        public String login(@Context HttpServletRequest request, @Context HttpServletResponse response)
        {
            OidcProfile profile = new OidcProfile();
            profile.setId("alice");
            profile.setClientName(GitlabClient.GITLAB_CLIENT_NAME);
            Map<String, UserProfile> profiles = new LinkedHashMap<>();
            profiles.put(GitlabClient.GITLAB_CLIENT_NAME, profile);
            new JEESessionStore().set(new JEEContext(request, response), Pac4jConstants.USER_PROFILES, profiles);
            return "logged in";
        }
    }
}
