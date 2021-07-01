// Copyright 2021 Goldman Sachs
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

package org.finos.legend.sdlc.server.project.maven;

import org.apache.maven.model.Plugin;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TestMavenPluginTools
{
    @Test
    public void testNewPlugin()
    {
        String groupId = "my.group";
        String artifactId = "my-artifact";
        String version = "1.2.3";

        assertPlugin(groupId, artifactId, version, MavenPluginTools.newPlugin(groupId, artifactId, version));
        assertPlugin(groupId, artifactId, null, MavenPluginTools.newPlugin(groupId, artifactId, null));
        assertPlugin(groupId, null, version, MavenPluginTools.newPlugin(groupId, null, version));
        assertPlugin(groupId, null, null, MavenPluginTools.newPlugin(groupId, null, null));
        assertPlugin(null, artifactId, version, MavenPluginTools.newPlugin(null, artifactId, version));
        assertPlugin(null, artifactId, null, MavenPluginTools.newPlugin(null, artifactId, null));
        assertPlugin(null, null, version, MavenPluginTools.newPlugin(null, null, version));
        assertPlugin(null, null, null, MavenPluginTools.newPlugin(null, null, null));
    }

    private void assertPlugin(String groupId, String artifactId, String version, Plugin plugin)
    {
        Assert.assertEquals((groupId == null) ? new Plugin().getGroupId() : groupId, plugin.getGroupId());
        Assert.assertEquals(artifactId, plugin.getArtifactId());
        Assert.assertEquals(version, plugin.getVersion());
    }

    @Test
    public void testNewDom()
    {
        String name = "name";
        String value = "value";

        String childNameBase = "childName";
        String childValueBase = "childValue";

        assertDom(name, null, null, MavenPluginTools.newDom(name));
        assertDom(name, value, null, MavenPluginTools.newDom(name, value));
        for (int i = 1; i < 5; i++)
        {
            List<Xpp3Dom> children = IntStream.rangeClosed(1, i).mapToObj(n -> MavenPluginTools.newDom(childNameBase + n, childValueBase + n)).collect(Collectors.toList());
            assertDom(name, null, children, MavenPluginTools.newDom(name, children));
        }
    }

    private void assertDom(String name, String value, List<Xpp3Dom> children, Xpp3Dom dom)
    {
        Assert.assertEquals(name, dom.getName());
        Assert.assertEquals(value, dom.getValue());
        Assert.assertEquals((children == null) ? Collections.emptyList() : children, Arrays.asList(dom.getChildren()));
    }
}
