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
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.finos.legend.sdlc.server.LegendSDLCServerForTest;
import org.finos.legend.sdlc.server.config.LegendSDLCServerConfiguration;
import org.finos.legend.sdlc.server.inmemory.backend.InMemoryBackend;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;

import java.util.Optional;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;

public abstract class AbstractLegendSDLCServerResourceTest
{
    @ClassRule
    public static final DropwizardAppRule<LegendSDLCServerConfiguration> APP_RULE =
            new DropwizardAppRule<>(
                    LegendSDLCServerForTest.class,
                    ResourceHelpers.resourceFilePath("config-test.yaml"));

    protected Client client;

    @Rule
    public final SDLCServerClientRule sdlcServerClientRule = new SDLCServerClientRule();
    protected InMemoryBackend backend;

    @Before
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
