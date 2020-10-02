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
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.server.project.config.ProjectCreationConfiguration.DisallowedType;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class TestProjectCreationConfiguration
{
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void testEmptyConfig() throws IOException
    {
        List<String> emptyConfigs = Arrays.asList(
                "{}",
                "{\"defaultProjectStructureVersion\":null}",
                "{\"defaultProjectStructureVersion\":null, \"groupIdPattern\":null}",
                "{\"defaultProjectStructureVersion\":null, \"groupIdPattern\":null, \"artifactIdPattern\":null}",
                "{\"defaultProjectStructureVersion\":null, \"groupIdPattern\":null, \"artifactIdPattern\":null, \"disallowedTypes\":null}");
        for (String emptyConfig : emptyConfigs)
        {
            assertConfig(emptyConfig, null, null, null, Collections.emptyList());
        }
    }

    @Test
    public void testDefaultProjectStructureVersion() throws IOException
    {
        assertConfig("{\"defaultProjectStructureVersion\":0}", 0, null, null, Collections.emptyList());
        assertConfig("{\"defaultProjectStructureVersion\":5}", 5, null, null, Collections.emptyList());
    }

    @Test
    public void testGroupIdPattern() throws IOException
    {
        assertConfig("{\"groupIdPattern\":\"^abc$\"}", null, "^abc$", null, Collections.emptyList());
        assertConfig("{\"groupIdPattern\":\"^org\\\\.finos\\\\.legend(\\\\.[\\\\w-])+$\"}", null, "^org\\.finos\\.legend(\\.[\\w-])+$", null, Collections.emptyList());
    }

    @Test
    public void testArtifactIdPattern() throws IOException
    {
        assertConfig("{\"artifactIdPattern\":\"^abc$\"}", null, null, "^abc$", Collections.emptyList());
        assertConfig("{\"artifactIdPattern\":\"^\\\\w+(-\\\\w+)*$\"}", null, null, "^\\w+(-\\w+)*$", Collections.emptyList());
    }

    @Test
    public void testDisallowedTypes() throws IOException
    {
        assertConfig("{\"disallowedTypes\":[{\"type\":\"PRODUCTION\"}]}", null, null, null, Collections.singletonList(DisallowedType.newDisallowedType(ProjectType.PRODUCTION, null)));
        assertConfig("{\"disallowedTypes\":[{\"type\":\"PRODUCTION\"}, {\"type\":\"PROTOTYPE\", \"message\":\"Don't do it!\"}]}", null, null, null, Arrays.asList(DisallowedType.newDisallowedType(ProjectType.PRODUCTION, null), DisallowedType.newDisallowedType(ProjectType.PROTOTYPE, "Don't do it!")));
    }

    @Test
    public void testAll() throws IOException
    {
        assertConfig(
                "{\"defaultProjectStructureVersion\":0, \"groupIdPattern\":\"^abc$\", \"artifactIdPattern\":\"^abc$\", \"disallowedTypes\":[{\"type\":\"PRODUCTION\"}]}",
                0,
                "^abc$",
                "^abc$",
                Collections.singletonList(DisallowedType.newDisallowedType(ProjectType.PRODUCTION, null)));
        assertConfig(
                "{\"defaultProjectStructureVersion\":5, \"groupIdPattern\":\"^org\\\\.finos\\\\.legend(\\\\.[\\\\w-])+$\", \"artifactIdPattern\":\"^\\\\w+(-\\\\w+)*$\", \"disallowedTypes\":[{\"type\":\"PRODUCTION\"}, {\"type\":\"PROTOTYPE\", \"message\":\"Don't do it!\"}]}",
                5,
                "^org\\.finos\\.legend(\\.[\\w-])+$",
                "^\\w+(-\\w+)*$",
                Arrays.asList(DisallowedType.newDisallowedType(ProjectType.PRODUCTION, null), DisallowedType.newDisallowedType(ProjectType.PROTOTYPE, "Don't do it!")));
    }

    private void assertConfig(String json, Integer expectedDefaultProjectStructureVersion, String expectedGroupIdPattern, String expectedArtifactIdPattern, Collection<DisallowedType> expectedDisallowedTypes) throws IOException
    {
        ProjectCreationConfiguration config = MAPPER.readValue(json, ProjectCreationConfiguration.class);
        Assert.assertEquals(json, ProjectCreationConfiguration.newConfig(expectedDefaultProjectStructureVersion, expectedGroupIdPattern, expectedArtifactIdPattern, expectedDisallowedTypes), config);
    }
}
