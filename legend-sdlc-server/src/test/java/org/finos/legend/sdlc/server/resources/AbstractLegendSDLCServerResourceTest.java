// Copyright 2020 Goldman Sachs
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
import org.finos.legend.sdlc.server.LegendSDLCServerForTest;
import org.finos.legend.sdlc.server.config.LegendSDLCServerConfiguration;
import org.finos.legend.sdlc.server.inmemory.backend.InMemoryBackend;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Optional;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.WebTarget;

@ExtendWith(DropwizardExtensionsSupport.class)
public abstract class AbstractLegendSDLCServerResourceTest
{
    @RegisterExtension
    public static final DropwizardAppExtension<LegendSDLCServerConfiguration> APP_RULE =
            new DropwizardAppExtension<>(
                    LegendSDLCServerForTest.class,
                    ResourceHelpers.resourceFilePath("config-test.yaml"));

    protected Client client;

    @RegisterExtension
    public final SDLCServerClientRule sdlcServerClientRule = new SDLCServerClientRule();
    protected InMemoryBackend backend;

    @BeforeEach
    public void setUp()
    {
        this.backend = ((LegendSDLCServerForTest) APP_RULE.getApplication()).getGuiceBundle().getInjector().getInstance(InMemoryBackend.class);
        configureClient();
    }

    private void configureClient()
    {
        this.client = this.sdlcServerClientRule.getClient();
        this.client.target(getServerUrl()).request().get();
    }

    protected WebTarget clientFor(String url)
    {
        return this.client.target(getServerUrl(url));
    }

    protected String getServerUrl()
    {
        return getServerUrl(null);
    }

    protected String getServerUrl(String path)
    {
        return "http://localhost:" + APP_RULE.getLocalPort() + Optional.ofNullable(path).orElse("");
    }
}
