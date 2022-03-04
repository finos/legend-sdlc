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
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
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
            "{\"defaultProjectStructureVersion\":null, \"groupIdPattern\":null, \"artifactIdPattern\":null}");
        for (String emptyConfig : emptyConfigs)
        {
            assertConfig(emptyConfig, null, null, null);
        }
    }

    @Test
    public void testDefaultProjectStructureVersion() throws IOException
    {
        assertConfig("{\"defaultProjectStructureVersion\":0}", 0, null, null);
        assertConfig("{\"defaultProjectStructureVersion\":5}", 5, null, null);
    }

    @Test
    public void testGroupIdPattern() throws IOException
    {
        assertConfig("{\"groupIdPattern\":\"^abc$\"}", null, "^abc$", null);
        assertConfig("{\"groupIdPattern\":\"^org\\\\.finos\\\\.legend(\\\\.[\\\\w-])+$\"}", null, "^org\\.finos\\.legend(\\.[\\w-])+$", null);
    }

    @Test
    public void testArtifactIdPattern() throws IOException
    {
        assertConfig("{\"artifactIdPattern\":\"^abc$\"}", null, null, "^abc$");
        assertConfig("{\"artifactIdPattern\":\"^\\\\w+(-\\\\w+)*$\"}", null, null, "^\\w+(-\\w+)*$");
    }

    @Test
    public void testAll() throws IOException
    {
        assertConfig("{\"defaultProjectStructureVersion\":0, \"groupIdPattern\":\"^abc$\", \"artifactIdPattern\":\"^abc$\"}", 0, "^abc$", "^abc$");
        assertConfig("{\"defaultProjectStructureVersion\":5, \"groupIdPattern\":\"^org\\\\.finos\\\\.legend(\\\\.[\\\\w-])+$\", \"artifactIdPattern\":\"^\\\\w+(-\\\\w+)*$\"}", 5, "^org\\.finos\\.legend(\\.[\\w-])+$", "^\\w+(-\\w+)*$");
    }

    private void assertConfig(String json, Integer expectedDefaultProjectStructureVersion, String expectedGroupIdPattern, String expectedArtifactIdPattern) throws IOException
    {
        ProjectCreationConfiguration config = MAPPER.readValue(json, ProjectCreationConfiguration.class);
        Assert.assertEquals(json, ProjectCreationConfiguration.newConfig(expectedDefaultProjectStructureVersion, expectedGroupIdPattern, expectedArtifactIdPattern), config);
    }
}
