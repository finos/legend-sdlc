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
import org.finos.legend.sdlc.server.config.LegendSDLCServerConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

@ExtendWith(DropwizardExtensionsSupport.class)
public class TestResourcesArePerRequest
{
    @RegisterExtension
    public static final DropwizardAppExtension<LegendSDLCServerConfiguration> APP =
            new DropwizardAppExtension<>(RealModuleServerForTest.class, ResourceHelpers.resourceFilePath("config-real-module-test.yaml"));

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

    @Test
    public void aRequestWithoutASessionIsRejectedAfterAnAuthenticatedOne()
    {
        Client client = ClientBuilder.newClient();
        try
        {
            Assertions.assertEquals("carol", currentUser(client, "carol"));
            try (Response response = client.target(url("/api/currentUser")).request(MediaType.APPLICATION_JSON).get())
            {
                Assertions.assertNotEquals(200, response.getStatus(), () -> "anonymous request answered: " + response.readEntity(String.class));
            }
        }
        finally
        {
            client.close();
        }
    }

    /**
     * Raw keep-alive connections keep the first caller's connection, and with it the Jetty request object a
     * request-crossing resource would have captured, open and idle while the others are served.
     */
    @Test
    public void theAuthorizationCheckReadsTheCurrentRequest() throws IOException
    {
        try (Socket dave = new Socket("localhost", APP.getLocalPort());
             Socket erin = new Socket("localhost", APP.getLocalPort());
             Socket anonymous = new Socket("localhost", APP.getLocalPort()))
        {
            Assertions.assertEquals("200 true", rawAuthorized(dave, "dave"));
            Assertions.assertEquals("200 true", rawAuthorized(erin, "erin"));
            Assertions.assertEquals("200 false", rawAuthorized(anonymous, null));
            Assertions.assertEquals("200 true", rawAuthorized(dave, "dave"));
        }
    }

    private static String rawAuthorized(Socket socket, String user) throws IOException
    {
        socket.setSoTimeout(30_000);
        String request = "GET /api/auth/authorized HTTP/1.1\r\nHost: localhost\r\nAccept: application/json\r\n" +
                ((user == null) ? "" : (RealModuleServerForTest.USER_HEADER + ": " + user + "\r\n")) + "\r\n";
        OutputStream out = socket.getOutputStream();
        out.write(request.getBytes(StandardCharsets.US_ASCII));
        out.flush();
        InputStream in = socket.getInputStream();
        String statusLine = readLine(in);
        int contentLength = -1;
        boolean chunked = false;
        for (String header = readLine(in); !header.isEmpty(); header = readLine(in))
        {
            String lower = header.toLowerCase(java.util.Locale.ROOT);
            if (lower.startsWith("content-length:"))
            {
                contentLength = Integer.parseInt(header.substring(15).trim());
            }
            else if (lower.startsWith("transfer-encoding:") && lower.contains("chunked"))
            {
                chunked = true;
            }
        }
        StringBuilder body = new StringBuilder();
        if (chunked)
        {
            for (int size = Integer.parseInt(readLine(in).trim(), 16); size > 0; size = Integer.parseInt(readLine(in).trim(), 16))
            {
                body.append(new String(in.readNBytes(size), StandardCharsets.UTF_8));
                readLine(in);
            }
            readLine(in);
        }
        else if (contentLength >= 0)
        {
            body.append(new String(in.readNBytes(contentLength), StandardCharsets.UTF_8));
        }
        return statusLine.split(" ")[1] + " " + body.toString().trim();
    }

    private static String readLine(InputStream in) throws IOException
    {
        StringBuilder line = new StringBuilder();
        for (int c = in.read(); (c != -1) && (c != '\n'); c = in.read())
        {
            if (c != '\r')
            {
                line.append((char) c);
            }
        }
        return line.toString();
    }

    private static String currentUser(Client client, String user)
    {
        try (Response response = client.target(url("/api/currentUser")).request(MediaType.APPLICATION_JSON).header(RealModuleServerForTest.USER_HEADER, user).get())
        {
            String body = response.readEntity(String.class);
            Assertions.assertEquals(200, response.getStatus(), body);
            return body.replaceAll("(?s).*\"userId\"\\s*:\\s*\"([^\"]*)\".*", "$1");
        }
    }

    private static String url(String path)
    {
        return "http://localhost:" + APP.getLocalPort() + path;
    }
}
