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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.configuration.ConfigurationSourceProvider;
import io.dropwizard.configuration.DefaultConfigurationFactoryFactory;
import io.dropwizard.configuration.ResourceConfigurationSourceProvider;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.jersey.validation.Validators;
import org.apache.commons.text.StringSubstitutor;
import org.finos.legend.sdlc.server.BaseLegendSDLCServer;
import org.finos.legend.sdlc.server.LegendSDLCServer;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class TestShippedConfigurations
{
    private static final Map<String, String> ENVIRONMENT = environment();

    /**
     * {@code ChainFixingFilterHandler} silently ignores a {@code filterPriorities} key that names no
     * registered filter. {@code "GitLab"} stays valid as an alias of {@code "LegendSDLCSession"}
     * (see {@link LegendSDLCServerConfiguration#getFilterPriorities()}).
     */
    private static final Set<String> REGISTERED_FILTERS = new LinkedHashSet<>(Arrays.asList(
            "CORS",
            "GitLab",
            "LegendSDLCSession",
            "Username",
            "org.pac4j.jee.filter.SecurityFilter",
            "org.pac4j.jee.filter.CallbackFilter",
            "org.pac4j.jee.filter.LogoutFilter"));

    @Test
    public void testDockerConfigurationParses()
    {
        assertParses("docker/config/config.json");
    }

    @Test
    public void testTestConfigurationParses()
    {
        assertParses("config-test.yaml");
    }

    @Test
    public void testSampleConfigurationParses()
    {
        assertParses("config-sample.yaml");
    }

    private static void assertParses(String resource)
    {
        LegendSDLCServerConfiguration configuration;
        try
        {
            // Not a directly constructed YamlConfigurationFactory: only the factory factory, as
            // Dropwizard uses it, enables FAIL_ON_UNKNOWN_PROPERTIES.
            configuration = new DefaultConfigurationFactoryFactory<LegendSDLCServerConfiguration>()
                    .create(
                            LegendSDLCServerConfiguration.class,
                            Validators.newValidator(),
                            objectMapper(),
                            "dw")
                    .build(sourceProvider(), resource);
        }
        catch (Exception e)
        {
            throw new AssertionError("Shipped configuration " + resource + " does not parse: " + e.getMessage(), e);
        }
        Assert.assertNotNull(resource, configuration);
        Assert.assertNotNull(resource + ": server factory", configuration.getServerFactory());
        if (configuration.getFilterPriorities() != null)
        {
            for (String filter : configuration.getFilterPriorities().keySet())
            {
                Assert.assertTrue(
                        resource + ": filterPriorities names \"" + filter + "\", which is not a registered filter " + REGISTERED_FILTERS,
                        REGISTERED_FILTERS.contains(filter));
            }
        }
    }

    // Not a bare mapper: the polymorphic sections (pac4j clients, project structure extension
    // provider, backend configuration) only deserialize after the application's initialize.
    private static ObjectMapper objectMapper()
    {
        LegendSDLCServer application = new LegendSDLCServer(BaseLegendSDLCServer.GITLAB_MODE);
        Bootstrap<LegendSDLCServerConfiguration> bootstrap = new Bootstrap<>(application);
        application.initialize(bootstrap);
        return bootstrap.getObjectMapper();
    }

    private static ConfigurationSourceProvider sourceProvider()
    {
        return new SubstitutingSourceProvider(
                new ResourceConfigurationSourceProvider(),
                new StringSubstitutor(ENVIRONMENT));
    }

    private static Map<String, String> environment()
    {
        Map<String, String> environment = new LinkedHashMap<>();
        // Unquoted in the JSON, so the substitutions have to supply valid JSON numeric literals.
        environment.put("SDLC_PORT", "6100");
        environment.put("SDLC_ADMIN_PORT", "6101");
        environment.put("ENGINE_MAVEN_VERSION", "0.0.0");
        environment.put("SDLC_MAVEN_VERSION", "0.0.0");
        environment.put("MONGODB_URI", "mongodb://localhost:27017");
        environment.put("MONGODB_NAME", "legend_sdlc");
        environment.put("MONGO_SESSION_ENABLED", "false");
        environment.put("GITLAB_HOST", "gitlab.example.com");
        environment.put("GITLAB_APP_ID", "test-app-id");
        environment.put("GITLAB_APP_SECRET", "test-app-secret");
        environment.put("SDLC_PROJECT_TAG", "legendsdlctest");
        environment.put("SDLC_REDIRECT_URI", "http://localhost:6100/api/auth/callback");
        // config-sample.yaml's own substitution names, unbraced ($VAR rather than ${VAR}).
        environment.put("APP_ID", "test-app-id");
        environment.put("APP_SECRET", "test-app-secret");
        environment.put("SDLC_SERVER_HOST", "localhost:6100");
        return environment;
    }
}
