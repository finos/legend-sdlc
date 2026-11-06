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

import com.sun.net.httpserver.HttpServer;
import org.finos.legend.server.pac4j.gitlab.GitlabClient;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.pac4j.core.context.CallContext;
import org.pac4j.core.exception.http.FoundAction;
import org.pac4j.core.exception.http.RedirectionAction;
import org.pac4j.test.context.MockWebContext;
import org.pac4j.test.context.session.MockSessionStore;

import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Runs on legend-sdlc-server's own test classpath so that the nimbus-jose-jwt and oauth2-oidc-sdk versions the
 * server resolves are the ones exercised.
 */
public class TestGitlabOidcClientRuntimeClasspath
{
    private HttpServer provider;
    private String baseUrl;

    @Before
    public void startStubProvider() throws Exception
    {
        this.provider = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        this.baseUrl = "http://127.0.0.1:" + this.provider.getAddress().getPort();
        String discovery = "{"
                + "\"issuer\":\"" + this.baseUrl + "\","
                + "\"authorization_endpoint\":\"" + this.baseUrl + "/oauth/authorize\","
                + "\"token_endpoint\":\"" + this.baseUrl + "/oauth/token\","
                + "\"userinfo_endpoint\":\"" + this.baseUrl + "/oauth/userinfo\","
                + "\"jwks_uri\":\"" + this.baseUrl + "/oauth/discovery/keys\","
                + "\"response_types_supported\":[\"code\"],"
                + "\"subject_types_supported\":[\"public\"],"
                + "\"id_token_signing_alg_values_supported\":[\"RS256\"],"
                + "\"scopes_supported\":[\"openid\",\"profile\",\"api\"],"
                + "\"token_endpoint_auth_methods_supported\":[\"client_secret_basic\",\"client_secret_post\"]"
                + "}";
        serve("/.well-known/openid-configuration", discovery);
        serve("/oauth/discovery/keys", "{\"keys\":[]}");
        this.provider.start();
    }

    @After
    public void stopStubProvider()
    {
        if (this.provider != null)
        {
            this.provider.stop(0);
        }
    }

    @Test
    public void gitlabOidcClientRetrievesMetadataAndRedirects() throws Exception
    {
        GitlabClient client = new GitlabClient();
        setField(client, "clientId", "sdlc-client");
        setField(client, "secret", "sdlc-secret");
        setField(client, "discoveryUri", this.baseUrl + "/.well-known/openid-configuration");
        setField(client, "scope", "openid profile api");
        client.setCallbackUrl("http://localhost:6100/api/pac4j/login/callback");

        client.init();

        MockWebContext context = MockWebContext.create();
        RedirectionAction action = client.getRedirectionAction(new CallContext(context, new MockSessionStore())).orElse(null);
        Assert.assertTrue("expected a redirect to the provider, got " + action, action instanceof FoundAction);
        String location = ((FoundAction) action).getLocation();
        Assert.assertTrue(location, location.startsWith(this.baseUrl + "/oauth/authorize?"));
        Assert.assertTrue(location, location.contains("client_id=sdlc-client"));
    }

    private void serve(String path, String json)
    {
        this.provider.createContext(path, exchange ->
        {
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody())
            {
                out.write(body);
            }
        });
    }

    private static void setField(GitlabClient client, String name, String value) throws Exception
    {
        Field field = GitlabClient.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(client, value);
    }
}
