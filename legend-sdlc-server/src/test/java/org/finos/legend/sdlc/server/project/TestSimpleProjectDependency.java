// Copyright 2022 Goldman Sachs
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

package org.finos.legend.sdlc.server.project;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.eclipse.collections.api.factory.Lists;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class TestSimpleProjectDependency
{
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    public void testDeserialization() throws JsonProcessingException
    {
        ProjectDependency dependency1 = ProjectDependency.newProjectDependency("org.finos.legend.test:test-proj", "1.2.3");
        Assert.assertEquals(dependency1, parseDependency("{\"projectId\":\"org.finos.legend.test:test-proj\", \"versionId\":\"1.2.3\"}]}"));
        Assert.assertEquals(dependency1, parseDependency("{\"projectId\":\"org.finos.legend.test:test-proj\", \"versionId\":{\"majorVersion\":1, \"minorVersion\":2, \"patchVersion\":3}}"));

        ProjectDependency dependency2 = ProjectDependency.newProjectDependency("org.finos.legend.test:test-proj2", "master-SNAPSHOT");
        Assert.assertEquals(dependency2, parseDependency("{\"projectId\":\"org.finos.legend.test:test-proj2\", \"versionId\":\"master-SNAPSHOT\"}"));

        Assert.assertEquals(
                Lists.fixedSize.with(dependency1, dependency2),
                parseDependencies("[{\"projectId\":\"org.finos.legend.test:test-proj\", \"versionId\":{\"majorVersion\":1, \"minorVersion\":2, \"patchVersion\":3}}, {\"projectId\":\"org.finos.legend.test:test-proj2\", \"versionId\":\"master-SNAPSHOT\"}]"));
        Assert.assertEquals(
                Lists.fixedSize.with(dependency2, dependency1),
                parseDependencies("[{\"projectId\":\"org.finos.legend.test:test-proj2\", \"versionId\":\"master-SNAPSHOT\"}, {\"projectId\":\"org.finos.legend.test:test-proj\", \"versionId\":\"1.2.3\"}]"));
    }

    @Test
    public void testInvalidDeserialization()
    {
        UnrecognizedPropertyException e1 = Assert.assertThrows(UnrecognizedPropertyException.class, () -> parseDependency("{\"projectId\":\"org.finos.legend.test:test-proj\", \"versionId\":{\"major\":1}}"));
        Assert.assertEquals("major", e1.getPropertyName());

        MismatchedInputException e2 = Assert.assertThrows(MismatchedInputException.class, () -> parseDependency("{\"projectId\":\"org.finos.legend.test:test-proj\", \"versionId\":true}"));
        Assert.assertEquals("Invalid versionId", e2.getOriginalMessage());
        Assert.assertEquals(String.class, e2.getTargetType());
    }

    private SimpleProjectDependency parseDependency(String json) throws JsonProcessingException
    {
        return this.mapper.readValue(json, SimpleProjectDependency.class);
    }

    private List<SimpleProjectDependency> parseDependencies(String json) throws JsonProcessingException
    {
        return this.mapper.readValue(json, this.mapper.getTypeFactory().constructCollectionType(List.class, SimpleProjectDependency.class));
    }
}
