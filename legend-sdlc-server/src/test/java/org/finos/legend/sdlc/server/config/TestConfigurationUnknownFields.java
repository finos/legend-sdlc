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
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.testing.ResourceHelpers;
import org.finos.legend.sdlc.server.BaseLegendSDLCServer;
import org.finos.legend.sdlc.server.LegendSDLCServer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Dropwizard rejects unknown configuration keys by default, but deployed configurations carry keys that were never
 * real properties, such as {@code server.requestLog.level} and the authorizers' {@code id}.
 */
public class TestConfigurationUnknownFields
{
    @TempDir
    Path tempDir;

    @Test
    public void theTestConfigurationParses() throws Exception
    {
        Assertions.assertNotNull(parse(Files.readString(testConfig())));
    }

    @Test
    public void aRequestLogLevelIsTolerated() throws Exception
    {
        String config = Files.readString(testConfig()).replace("  requestLog:\n    type: classic\n", "  requestLog:\n    type: classic\n    level: OFF\n");
        Assertions.assertTrue(config.contains("level: OFF\n    # An empty"), config);
        Assertions.assertNotNull(parse(config));
    }

    @Test
    public void anAuthorizerIdIsTolerated() throws Exception
    {
        String config = Files.readString(testConfig()).replace("  bypassPaths:\n", "  authorizers:\n    - id: isAuthenticated\n      class: org.pac4j.core.authorization.authorizer.IsAuthenticatedAuthorizer\n  bypassPaths:\n");
        Assertions.assertTrue(config.contains("id: isAuthenticated"), config);
        Assertions.assertNotNull(parse(config));
    }

    @Test
    public void anUnknownTopLevelKeyIsTolerated() throws Exception
    {
        Assertions.assertNotNull(parse(Files.readString(testConfig()) + "\nsomeRetiredSetting: true\n"));
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

    private static Path testConfig()
    {
        return Paths.get(ResourceHelpers.resourceFilePath("config-test.yaml"));
    }
}
