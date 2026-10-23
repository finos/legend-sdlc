// Copyright 2021 Goldman Sachs
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

package org.finos.legend.sdlc.server.resources;

import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.finos.legend.sdlc.server.config.LegendSDLCServerConfiguration;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Optional;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;

@ExtendWith(DropwizardExtensionsSupport.class)
public class TestLegendSDLCServerAccessibility
{
    @RegisterExtension
    public static final DropwizardAppExtension<LegendSDLCServerConfiguration> APP_RULE =
            new DropwizardAppExtension<>(
                    LegendSDLCServerForIntegrationTest.class,
                    ResourceHelpers.resourceFilePath("config-test.yaml"));

    protected Client client;

    @RegisterExtension
    public final SDLCServerClientRule sdlcServerClientRule = new SDLCServerClientRule();

    @BeforeEach
    public void setUp()
    {
        configureClient();
    }

    private void configureClient()
    {
        this.client = this.sdlcServerClientRule.getClient();
        this.client.target(getServerUrl()).request().get();
    }

    protected WebTarget clientFor(String url, int port)
    {
        return this.client.target(getServerUrl(url, port));
    }

    protected String getServerUrl()
    {
        return getServerUrl(null, APP_RULE.getLocalPort());
    }

    protected String getServerUrl(String path, int port)
    {
        return "http://localhost:" + port + Optional.ofNullable(path).orElse("");
    }

    @Test
    public void testSwaggerPageAccessible()
    {
        Response response = clientFor("/api/swagger", APP_RULE.getLocalPort()).request().get();
        Assert.assertEquals(200, response.getStatus());
    }
    
    @Test
    public void testApiInfoPageAccessible()
    {
        Response response = clientFor("/api/info", APP_RULE.getLocalPort()).request().get();
        Assert.assertEquals(200, response.getStatus());
    }
    
    @Test
    public void testHealthCheckPageAccessible()
    {
        Response response = clientFor("/healthcheck", APP_RULE.getAdminPort()).request().get();
        Assert.assertEquals(200, response.getStatus());
    }
}

