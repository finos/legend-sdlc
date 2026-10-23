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

package org.finos.legend.sdlc.server.tools;

import com.sun.net.httpserver.HttpServer;
import org.finos.legend.sdlc.server.auth.Session;
import org.finos.legend.server.pac4j.gitlab.GitlabPersonalAccessTokenClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestSessionProvider
{
    private static final String HEADER_NAME = "PRIVATE-TOKEN";

    private HttpServer server;
    private volatile int responseStatus = 200;
    private volatile String responseBody = "{\"username\":\"octocat\",\"name\":\"The Octocat\"}";

    @Before
    public void startServer() throws IOException
    {
        this.server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        this.server.createContext("/api/v4/user", exchange ->
        {
            byte[] body = this.responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(this.responseStatus, body.length);
            try (OutputStream out = exchange.getResponseBody())
            {
                out.write(body);
            }
        });
        this.server.start();
    }

    @After
    public void stopServer()
    {
        this.server.stop(0);
    }

    private GitlabPersonalAccessTokenClient clientWith(String host, String gitlabHost)
    {
        GitlabPersonalAccessTokenClient client = new GitlabPersonalAccessTokenClient();
        client.headerTokenName = HEADER_NAME;
        client.scheme = "http";
        client.host = host;
        client.gitlabHost = gitlabHost;
        client.port = this.server.getAddress().getPort();
        client.apiVersion = "v4";
        return client;
    }

    private HttpServletRequest requestWithToken(String token)
    {
        HttpServletRequest request = mock(HttpServletRequest.class);
        // JEEContext.getRequestHeader enumerates getHeaderNames() before calling getHeader(name), so
        // both must be stubbed. A fresh Enumeration per call, since Enumeration is single-use.
        when(request.getHeaderNames()).thenAnswer(invocation -> (Enumeration<String>) ((token == null)
                ? Collections.emptyEnumeration()
                : Collections.enumeration(Collections.singletonList(HEADER_NAME))));
        when(request.getHeader(HEADER_NAME)).thenReturn(token);
        return request;
    }

    @Test
    public void testGetSessionUsingGitlabPersonalAccessToken_resolvesHostFromHostField()
    {
        GitlabPersonalAccessTokenClient client = clientWith("localhost", null);
        HttpServletRequest request = requestWithToken("sometoken123");
        HttpServletResponse response = mock(HttpServletResponse.class);

        Session session = SessionProvider.getSessionUsingGitlabPersonalAccessToken(request, response, client);

        assertEquals("octocat", session.getUserId());
    }

    @Test
    public void testGetSessionUsingGitlabPersonalAccessToken_fallsBackToLegacyGitlabHostField()
    {
        GitlabPersonalAccessTokenClient client = clientWith(null, "localhost");
        HttpServletRequest request = requestWithToken("sometoken123");
        HttpServletResponse response = mock(HttpServletResponse.class);

        Session session = SessionProvider.getSessionUsingGitlabPersonalAccessToken(request, response, client);

        assertEquals("octocat", session.getUserId());
    }

    @Test
    public void testGetSessionUsingGitlabPersonalAccessToken_returnsNullWhenNoTokenHeaderPresent()
    {
        GitlabPersonalAccessTokenClient client = clientWith("localhost", null);
        HttpServletRequest request = requestWithToken(null);
        HttpServletResponse response = mock(HttpServletResponse.class);

        Session session = SessionProvider.getSessionUsingGitlabPersonalAccessToken(request, response, client);

        assertNull(session);
    }

    @Test
    public void testGetSessionUsingGitlabPersonalAccessToken_returnsNullWhenValidationFails()
    {
        this.responseStatus = 401;
        this.responseBody = "{}";
        GitlabPersonalAccessTokenClient client = clientWith("localhost", null);
        HttpServletRequest request = requestWithToken("badtoken");
        HttpServletResponse response = mock(HttpServletResponse.class);

        Session session = SessionProvider.getSessionUsingGitlabPersonalAccessToken(request, response, client);

        assertNull(session);
    }
}
