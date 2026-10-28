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

import io.dropwizard.core.setup.Environment;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.finos.legend.sdlc.backend.api.spi.StaleAuthorizationException;
import org.finos.legend.sdlc.server.config.LegendSDLCServerConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * config-real-module-test.yaml leaves useForwardedHeaders unset on purpose, like deployed configurations: Dropwizard
 * defaults it to false and legend-shared's LegendPac4jBundle turns it on. The stale authorization retry redirect
 * stands for every URL built from the request: AuthResource's OAuth state uses the same getRequestURL().
 */
@ExtendWith(DropwizardExtensionsSupport.class)
public class TestForwardedHeaders
{
    private static final String PUBLIC_URL = "https://sdlc.example.com/api/forwardedHeadersTest/stale";

    @RegisterExtension
    public static final DropwizardAppExtension<LegendSDLCServerConfiguration> APP =
            new DropwizardAppExtension<>(StaleServer.class, ResourceHelpers.resourceFilePath("config-real-module-test.yaml"));

    @Test
    public void theRetryRedirectGoesToThePublicUrlWhenTheProxyRewritesTheHost() throws IOException
    {
        Assertions.assertEquals(PUBLIC_URL, staleRedirect("sdlc-internal.svc:8080"));
    }

    @Test
    public void theRetryRedirectKeepsHttpsWhenTheProxyKeepsTheHost() throws IOException
    {
        Assertions.assertEquals(PUBLIC_URL, staleRedirect("sdlc.example.com"));
    }

    private static String staleRedirect(String host) throws IOException
    {
        try (Socket socket = new Socket("localhost", APP.getLocalPort()))
        {
            socket.setSoTimeout(30_000);
            String request = "GET /api/forwardedHeadersTest/stale HTTP/1.1\r\n" +
                    "Host: " + host + "\r\n" +
                    "X-Forwarded-Proto: https\r\n" +
                    "X-Forwarded-Host: sdlc.example.com\r\n" +
                    "X-Forwarded-For: 203.0.113.7\r\n" +
                    "Connection: close\r\n\r\n";
            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(StandardCharsets.US_ASCII));
            out.flush();
            String response = readAll(socket.getInputStream());
            Assertions.assertTrue(response.startsWith("HTTP/1.1 302"), response);
            for (String line : response.split("\r\n"))
            {
                if (line.regionMatches(true, 0, "Location:", 0, 9))
                {
                    return line.substring(9).trim();
                }
                if (line.isEmpty())
                {
                    break;
                }
            }
            throw new AssertionError("no Location header: " + response);
        }
    }

    private static String readAll(InputStream in) throws IOException
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) != -1)
        {
            bytes.write(buffer, 0, read);
        }
        return bytes.toString(StandardCharsets.ISO_8859_1);
    }

    public static class StaleServer extends RealModuleServerForTest
    {
        @Override
        public void run(LegendSDLCServerConfiguration configuration, Environment environment)
        {
            super.run(configuration, environment);
            environment.jersey().register(StaleResource.class);
        }
    }

    /**
     * What a GitLab-backed API does when GitLab rejects the stored token (BaseGitLabApi): the real
     * StaleAuthorizationExceptionMapper turns it into a redirect back to the request.
     */
    @Path("/forwardedHeadersTest")
    public static class StaleResource
    {
        @GET
        @Path("stale")
        public String stale()
        {
            throw new StaleAuthorizationException("GitLab access token was stale and has been discarded; please retry request", null);
        }
    }
}
