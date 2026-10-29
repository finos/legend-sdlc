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

package org.finos.legend.sdlc.server.config;

import io.dropwizard.configuration.ConfigurationFactory;
import io.dropwizard.configuration.FileConfigurationSourceProvider;
import io.dropwizard.core.server.DefaultServerFactory;
import io.dropwizard.core.server.SimpleServerFactory;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.jetty.HttpConnectorFactory;
import org.finos.legend.sdlc.server.BaseLegendSDLCServer;
import org.finos.legend.sdlc.server.LegendSDLCServer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Dropwizard 5 connectors default useForwardedHeaders to false. LegendPac4jBundle defaults it to true for connectors
 * that leave it unset, which deployments behind a TLS-terminating proxy rely on.
 */
public class TestConfigurationForwardedHeaders
{
    private static final String OVERRIDE_PROPERTY = "dw.server.applicationConnectors[0].useForwardedHeaders";

    private static final String TAIL = String.join("\n",
            "pac4j:",
            "  clients:",
            "    - org.finos.legend.server.pac4j.kerberos.LocalKerberosClient: {}",
            "backend:",
            "  type: inMemory",
            "logging:",
            "  level: OFF",
            "");

    @TempDir
    Path tempDir;

    @Test
    public void anApplicationConnectorThatDoesNotSayHonoursForwardedHeaders() throws Exception
    {
        DefaultServerFactory server = (DefaultServerFactory) parse(defaultServer("")).getServerFactory();
        Assertions.assertTrue(applicationConnector(server).isUseForwardedHeaders());
    }

    @Test
    public void anAdminConnectorThatDoesNotSayHonoursForwardedHeadersAsOnDropwizard13() throws Exception
    {
        DefaultServerFactory server = (DefaultServerFactory) parse(defaultServer("")).getServerFactory();
        Assertions.assertTrue(((HttpConnectorFactory) server.getAdminConnectors().get(0)).isUseForwardedHeaders());
    }

    @Test
    public void anExplicitFalseIsKept() throws Exception
    {
        DefaultServerFactory server = (DefaultServerFactory) parse(defaultServer("      useForwardedHeaders: false\n")).getServerFactory();
        Assertions.assertFalse(applicationConnector(server).isUseForwardedHeaders());
    }

    @Test
    public void aSystemPropertyOverrideStillWins() throws Exception
    {
        System.setProperty(OVERRIDE_PROPERTY, "false");
        try
        {
            DefaultServerFactory server = (DefaultServerFactory) parse(defaultServer("")).getServerFactory();
            Assertions.assertFalse(applicationConnector(server).isUseForwardedHeaders());
        }
        finally
        {
            System.clearProperty(OVERRIDE_PROPERTY);
        }
    }

    @Test
    public void theSimpleServersConnectorHonoursForwardedHeaders() throws Exception
    {
        String yaml = "server:\n  type: simple\n  applicationContextPath: /\n  connector:\n    type: http\n    port: 0\n" + TAIL;
        SimpleServerFactory server = (SimpleServerFactory) parse(yaml).getServerFactory();
        Assertions.assertTrue(((HttpConnectorFactory) server.getConnector()).isUseForwardedHeaders());
    }

    private static HttpConnectorFactory applicationConnector(DefaultServerFactory server)
    {
        return (HttpConnectorFactory) server.getApplicationConnectors().get(0);
    }

    private static String defaultServer(String extraConnectorLines)
    {
        return "server:\n  applicationConnectors:\n    - type: http\n      port: 0\n" + extraConnectorLines +
                "  adminConnectors:\n    - type: http\n      port: 0\n" + TAIL;
    }

    private LegendSDLCServerConfiguration parse(String yaml) throws Exception
    {
        Path file = this.tempDir.resolve("config.yaml");
        Files.write(file, yaml.getBytes(StandardCharsets.UTF_8));
        LegendSDLCServer server = new LegendSDLCServer(BaseLegendSDLCServer.GITLAB_MODE);
        Bootstrap<LegendSDLCServerConfiguration> bootstrap = new Bootstrap<>(server);
        server.initialize(bootstrap);
        ConfigurationFactory<LegendSDLCServerConfiguration> factory = bootstrap.getConfigurationFactoryFactory()
                .create(LegendSDLCServerConfiguration.class, bootstrap.getValidatorFactory().getValidator(), bootstrap.getObjectMapper(), "dw");
        return factory.build(new FileConfigurationSourceProvider(), file.toString());
    }
}
