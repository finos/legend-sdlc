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

package org.finos.legend.sdlc.backend.gitlab;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.finos.legend.sdlc.backend.api.spi.BackendConfiguration;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * The GitLab backend configuration through the host's configuration machinery: the polymorphic {@code type}
 * discriminator, and the legacy-adapter path — a raw legacy {@code gitLab:} section (including the deprecated
 * {@code uat}/{@code prod} mode forms) stamped with the type and converted through the mapper.
 */
public class TestGitLabBackendConfiguration
{
    private ObjectMapper objectMapper;

    @Before
    public void setUp()
    {
        this.objectMapper = new ObjectMapper();
        GitLabBackendFactory factory = new GitLabBackendFactory();
        this.objectMapper.registerSubtypes(new NamedType(factory.getConfigurationClass(), factory.getType()));
        factory.configureObjectMapper(this.objectMapper);
    }

    @Test
    public void testFlatForm()
    {
        ObjectNode node = newGitLabNode();
        BackendConfiguration configuration = this.objectMapper.convertValue(node, BackendConfiguration.class);
        assertGitLabConfiguration(configuration);
    }

    @Test
    public void testLegacyProdModeForm()
    {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("type", "gitlab");
        ObjectNode prod = node.putObject("prod");
        addServerAndApp(prod);
        BackendConfiguration configuration = this.objectMapper.convertValue(node, BackendConfiguration.class);
        assertGitLabConfiguration(configuration);
        Assert.assertEquals("PROD", ((GitLabBackendConfiguration) configuration).getGitLabConfiguration().getProjectIdPrefix());
    }

    private ObjectNode newGitLabNode()
    {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("type", "gitlab");
        addServerAndApp(node);
        return node;
    }

    private void addServerAndApp(ObjectNode node)
    {
        ObjectNode server = node.putObject("server");
        server.put("scheme", "https");
        server.put("host", "gitlab.example.com");
        ObjectNode app = node.putObject("app");
        app.put("id", "someAppId");
        app.put("secret", "someSecret");
        app.put("redirectURI", "https://sdlc.example.com/api/auth/callback");
    }

    private void assertGitLabConfiguration(BackendConfiguration configuration)
    {
        Assert.assertTrue(configuration instanceof GitLabBackendConfiguration);
        GitLabConfiguration gitLabConfiguration = ((GitLabBackendConfiguration) configuration).getGitLabConfiguration();
        Assert.assertEquals("gitlab.example.com", gitLabConfiguration.getServerConfiguration().getHost());
        Assert.assertEquals("someAppId", gitLabConfiguration.getAppConfiguration().getId());
    }
}
