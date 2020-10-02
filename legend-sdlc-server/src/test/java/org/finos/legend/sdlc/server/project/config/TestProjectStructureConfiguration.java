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

package org.finos.legend.sdlc.server.project.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.collections.api.factory.Sets;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.server.project.config.ProjectCreationConfiguration.DisallowedType;
import org.finos.legend.sdlc.server.project.extension.DefaultProjectStructureExtension;
import org.finos.legend.sdlc.server.project.extension.DefaultProjectStructureExtensionProvider;
import org.finos.legend.sdlc.server.project.extension.ProjectStructureExtension;
import org.finos.legend.sdlc.server.project.extension.ProjectStructureExtensionProvider;
import org.finos.legend.sdlc.server.project.extension.VoidProjectStructureExtensionProvider;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class TestProjectStructureConfiguration
{
    private final ObjectMapper mapper = ProjectStructureConfiguration.configureObjectMapper(new ObjectMapper());

    @Test
    public void testEmptyConfig() throws IOException
    {
        List<String> emptyConfigs = Arrays.asList(
                "{}",
                "{\"demisedVersions\":null}",
                "{\"extensions\":null}",
                "{\"projectCreation\":null}",
                "{\"demisedVersions\":null, \"extensions\":null}",
                "{\"demisedVersions\":null, \"projectCreation\":null}",
                "{\"extensionProvider\":null, \"projectCreation\":null}",
                "{\"extensions\":null, \"projectCreation\":null}",
                "{\"demisedVersions\":null, \"extensionProvider\":null, \"extensions\":null, \"projectCreation\":null}");
        for (String emptyConfig : emptyConfigs)
        {
            assertConfig(emptyConfig, Collections.emptySet(), null, Collections.emptyList(), null);
        }
    }

    @Test
    public void testDemisedVersions() throws IOException
    {
        assertConfig("{\"demisedVersions\":[]}", Collections.emptySet(), null, Collections.emptyList(), null);
        assertConfig("{\"demisedVersions\":[1]}", Collections.singleton(1), null, Collections.emptyList(), null);
        assertConfig("{\"demisedVersions\":[0, 1, 2, 3, 4]}", Sets.mutable.with(0, 1, 2, 3, 4), null, Collections.emptyList(), null);
    }

    @Test
    public void testExtensionProvider() throws IOException
    {
        assertConfig("{\"extensionProvider\":null}", Collections.emptySet(), null, Collections.emptyList(), null);
        assertConfig("{\"extensionProvider\":{\"org.finos.legend.sdlc.server.project.extension.VoidProjectStructureExtensionProvider\":{}}}", Collections.emptySet(), new VoidProjectStructureExtensionProvider(), Collections.emptyList(), null);
        assertConfig("{\"extensionProvider\":{\"org.finos.legend.sdlc.server.project.extension.DefaultProjectStructureExtensionProvider\":{}}}", Collections.emptySet(), DefaultProjectStructureExtensionProvider.fromExtensions(), Collections.emptyList(), null);
        assertConfig("{\"extensionProvider\":{\"org.finos.legend.sdlc.server.project.extension.DefaultProjectStructureExtensionProvider\":{\"extensions\":[{\"org.finos.legend.sdlc.server.project.extension.DefaultProjectStructureExtension\":{\"projectStructureVersion\": 0, \"extensionVersion\": 1, \"files\":{\"PANGRAM.TXT\":{\"content\":\"THE QUICK BROWN FOX JUMPED OVER THE LAZY DOG\"}}}}, {\"org.finos.legend.sdlc.server.project.extension.DefaultProjectStructureExtension\":{\"projectStructureVersion\": 0, \"extensionVersion\": 2, \"files\":{\"pangram.txt\":{\"content\":\"the quick brown fox jumped over the lazy dog\"}}}}]}}}",
                Collections.emptySet(),
                DefaultProjectStructureExtensionProvider.fromExtensions(
                        DefaultProjectStructureExtension.newProjectStructureExtension(0, 1, Collections.singletonMap("/PANGRAM.TXT", "THE QUICK BROWN FOX JUMPED OVER THE LAZY DOG")),
                        DefaultProjectStructureExtension.newProjectStructureExtension(0, 2, Collections.singletonMap("/pangram.txt", "the quick brown fox jumped over the lazy dog"))),
                Collections.emptyList(),
                null);
    }

    @Test
    public void testExtensions() throws IOException
    {
        assertConfig("{\"extensions\":[]}", Collections.emptySet(), null, Collections.emptyList(), null);
        assertConfig(
                "{\"extensions\":[{\"org.finos.legend.sdlc.server.project.extension.DefaultProjectStructureExtension\":{\"projectStructureVersion\": 0, \"extensionVersion\": 1, \"files\":{\"PANGRAM.TXT\":{\"content\":\"THE QUICK BROWN FOX JUMPED OVER THE LAZY DOG\"}}}}, {\"org.finos.legend.sdlc.server.project.extension.DefaultProjectStructureExtension\":{\"projectStructureVersion\": 0, \"extensionVersion\": 2, \"files\":{\"pangram.txt\":{\"content\":\"the quick brown fox jumped over the lazy dog\"}}}}]}",
                Collections.emptySet(),
                null,
                Arrays.asList(
                        DefaultProjectStructureExtension.newProjectStructureExtension(0, 1, Collections.singletonMap("/PANGRAM.TXT", "THE QUICK BROWN FOX JUMPED OVER THE LAZY DOG")),
                        DefaultProjectStructureExtension.newProjectStructureExtension(0, 2, Collections.singletonMap("/pangram.txt", "the quick brown fox jumped over the lazy dog"))),
                null);
    }

    @Test
    public void testExtensionProviderAndExtensions() throws IOException
    {
        assertConfig("{\"extensionProvider\":{\"org.finos.legend.sdlc.server.project.extension.VoidProjectStructureExtensionProvider\":{}}, \"extensions\":null}", Collections.emptySet(), new VoidProjectStructureExtensionProvider(), Collections.emptyList(), null);
        assertConfig("{\"extensionProvider\":{\"org.finos.legend.sdlc.server.project.extension.VoidProjectStructureExtensionProvider\":{}}, \"extensions\":[]}", Collections.emptySet(), new VoidProjectStructureExtensionProvider(), Collections.emptyList(), null);
        assertDoesNotParse("{\"extensionProvider\":{\"org.finos.legend.sdlc.server.project.extension.VoidProjectStructureExtensionProvider\":{}}, \"extensions\":[{\"org.finos.legend.sdlc.server.project.extension.DefaultProjectStructureExtension\":{\"projectStructureVersion\": 0, \"extensionVersion\": 1, \"files\":{\"PANGRAM.TXT\":{\"content\":\"THE QUICK BROWN FOX JUMPED OVER THE LAZY DOG\"}}}}, {\"org.finos.legend.sdlc.server.project.extension.DefaultProjectStructureExtension\":{\"projectStructureVersion\": 0, \"extensionVersion\": 2, \"files\":{\"pangram.txt\":{\"content\":\"the quick brown fox jumped over the lazy dog\"}}}}]}", "May not specify both extensionProvider and extensions");
    }

    @Test
    public void testProjectCreation() throws IOException
    {
        assertConfig("{\"projectCreation\":{}}", Collections.emptySet(), null, Collections.emptyList(), ProjectCreationConfiguration.emptyConfig());
        assertConfig("{\"projectCreation\":{\"defaultProjectStructureVersion\":0, \"groupIdPattern\":\"^abc$\", \"artifactIdPattern\":\"^xyz$\", \"disallowedTypes\":[{\"type\":\"PRODUCTION\"}]}}", Collections.emptySet(), null, Collections.emptyList(), ProjectCreationConfiguration.newConfig(0, "^abc$", "^xyz$", DisallowedType.newDisallowedType(ProjectType.PRODUCTION, null)));
    }

    @Test
    public void testAll() throws IOException
    {
        assertConfig(
                "{\"demisedVersions\":[], \"extensionProvider\":null, \"extensions\":[], \"projectCreation\":null}",
                Collections.emptySet(),
                null,
                Collections.emptyList(),
                null);
        assertConfig(
                "{\"demisedVersions\":[0, 1, 2, 3, 4], \"extensionProvider\":null, \"extensions\":[{\"org.finos.legend.sdlc.server.project.extension.DefaultProjectStructureExtension\":{\"projectStructureVersion\": 0, \"extensionVersion\": 1, \"files\":{\"PANGRAM.TXT\":{\"content\":\"THE QUICK BROWN FOX JUMPED OVER THE LAZY DOG\"}}}}, {\"org.finos.legend.sdlc.server.project.extension.DefaultProjectStructureExtension\":{\"projectStructureVersion\": 0, \"extensionVersion\": 2, \"files\":{\"pangram.txt\":{\"content\":\"the quick brown fox jumped over the lazy dog\"}}}}], \"projectCreation\":{\"defaultProjectStructureVersion\":0, \"groupIdPattern\":\"^abc$\", \"artifactIdPattern\":\"^xyz$\", \"disallowedTypes\":[{\"type\":\"PRODUCTION\"}]}}",
                Sets.mutable.with(0, 1, 2, 3, 4),
                null,
                Arrays.asList(
                        DefaultProjectStructureExtension.newProjectStructureExtension(0, 1, Collections.singletonMap("/PANGRAM.TXT", "THE QUICK BROWN FOX JUMPED OVER THE LAZY DOG")),
                        DefaultProjectStructureExtension.newProjectStructureExtension(0, 2, Collections.singletonMap("/pangram.txt", "the quick brown fox jumped over the lazy dog"))),
                ProjectCreationConfiguration.newConfig(0, "^abc$", "^xyz$", DisallowedType.newDisallowedType(ProjectType.PRODUCTION, null)));
        assertConfig(
                "{\"demisedVersions\":[0, 1, 2, 3, 4], \"extensionProvider\":{\"org.finos.legend.sdlc.server.project.extension.DefaultProjectStructureExtensionProvider\":{\"extensions\":[{\"org.finos.legend.sdlc.server.project.extension.DefaultProjectStructureExtension\":{\"projectStructureVersion\": 0, \"extensionVersion\": 1, \"files\":{\"PANGRAM.TXT\":{\"content\":\"THE QUICK BROWN FOX JUMPED OVER THE LAZY DOG\"}}}}, {\"org.finos.legend.sdlc.server.project.extension.DefaultProjectStructureExtension\":{\"projectStructureVersion\": 0, \"extensionVersion\": 2, \"files\":{\"pangram.txt\":{\"content\":\"the quick brown fox jumped over the lazy dog\"}}}}]}}, \"extensions\":[], \"projectCreation\":{\"defaultProjectStructureVersion\":0, \"groupIdPattern\":\"^abc$\", \"artifactIdPattern\":\"^xyz$\", \"disallowedTypes\":[{\"type\":\"PRODUCTION\"}]}}",
                Sets.mutable.with(0, 1, 2, 3, 4),
                DefaultProjectStructureExtensionProvider.fromExtensions(
                        DefaultProjectStructureExtension.newProjectStructureExtension(0, 1, Collections.singletonMap("/PANGRAM.TXT", "THE QUICK BROWN FOX JUMPED OVER THE LAZY DOG")),
                        DefaultProjectStructureExtension.newProjectStructureExtension(0, 2, Collections.singletonMap("/pangram.txt", "the quick brown fox jumped over the lazy dog"))),
                Collections.emptyList(),
                ProjectCreationConfiguration.newConfig(0, "^abc$", "^xyz$", DisallowedType.newDisallowedType(ProjectType.PRODUCTION, null)));
    }

    private void assertConfig(String json, Set<Integer> expectedDemisedVersions, ProjectStructureExtensionProvider expectedExtensionProvider, List<ProjectStructureExtension> expectedExtensions, ProjectCreationConfiguration expectedProjectCreation) throws IOException
    {
        ProjectStructureConfiguration config = parseConfig(json);
        Assert.assertNotNull(json, config);
        Assert.assertEquals(json, expectedDemisedVersions, config.getDemisedVersions());
        Assert.assertEquals(json, expectedExtensionProvider, config.getProjectStructureExtensionProvider());
        Assert.assertEquals(json, expectedExtensions, config.getProjectStructureExtensions());
        Assert.assertEquals(json, expectedProjectCreation, config.getProjectCreationConfiguration());
    }

    private void assertDoesNotParse(String json, String expectedErrorMessage)
    {
        try
        {
            parseConfig(json);
            Assert.fail("Expected parse failure: " + json);
        }
        catch (IOException e)
        {
            if (expectedErrorMessage != null)
            {
                String actualMessage = e.getMessage();
                if ((actualMessage == null) || !actualMessage.contains(expectedErrorMessage))
                {
                    Assert.assertEquals(expectedErrorMessage, actualMessage);
                }
            }
        }
    }

    private ProjectStructureConfiguration parseConfig(String json) throws IOException
    {
        return this.mapper.readValue(json, ProjectStructureConfiguration.class);
    }
}
