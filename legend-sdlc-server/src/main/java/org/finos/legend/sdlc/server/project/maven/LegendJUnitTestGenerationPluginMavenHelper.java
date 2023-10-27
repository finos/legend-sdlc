// Copyright 2023 Goldman Sachs
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
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.util.function.Consumer;

public class LegendJUnitTestGenerationPluginMavenHelper extends AbstractLegendMavenPluginHelper
{
    public Boolean runDependencyTests;

    public LegendJUnitTestGenerationPluginMavenHelper(String groupId, String artifactId, String version, Dependency generationExtensionsCollection, Boolean runDependencyTests)
    {
        super(groupId, artifactId, version, "generate-test-sources", "generate-junit-tests", generationExtensionsCollection);
        this.runDependencyTests = runDependencyTests;
    }

    @Override
    protected void configurePlugin(MavenProjectStructure projectStructure, Consumer<? super Xpp3Dom> configConsumer)
    {
        String groupId = projectStructure.getProjectConfiguration().getGroupId();
        if (groupId != null)
        {
            configConsumer.accept(MavenPluginTools.newDom("packagePrefix", groupId));
        }
        if (this.runDependencyTests != null && this.runDependencyTests)
        {
            configConsumer.accept(MavenPluginTools.newDom("runDependencyTests", "true"));
        }
    }
}
