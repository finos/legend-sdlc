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

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;

public class LegendTestUtilsMavenHelper
{
    private static final ImmutableList<String> INCLUSION_PATTERNS = Lists.immutable.with("**/Test*.java", "**/*Test.java", "**/*Tests.java", "**/*TestCase.java", "**/*TestSuite.java");

    private final String version;
    private final String group_id;
    private final String artifact_id;

    public LegendTestUtilsMavenHelper(String groupId, String artifactId, String version)
    {
        this.group_id = groupId;
        this.artifact_id = artifactId;
        this.version = version;
    }

    public Dependency getDependency(boolean includeVersion)
    {
        return MavenProjectStructure.newMavenTestDependency(group_id, artifact_id, includeVersion ? this.version : null);
    }

    public Plugin getMavenSurefirePlugin()
    {
        Plugin plugin = MavenPluginTools.newPlugin(null, "maven-surefire-plugin", null);
        MavenPluginTools.setConfiguration(plugin,
                MavenPluginTools.newDom("includes", INCLUSION_PATTERNS.stream().map(p -> MavenPluginTools.newDom("include", p))),
                MavenPluginTools.newDom("useSystemClassLoader", "false"));
        return plugin;
    }
}
