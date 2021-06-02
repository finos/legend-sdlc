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
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;

import java.util.function.BiFunction;
import java.util.function.Consumer;

public abstract class AbstractLegendMavenPluginHelper
{
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String phase;
    private final String goal;

    protected AbstractLegendMavenPluginHelper(String groupId, String artifactId, String version, String phase, String goal)
    {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.phase = phase;
        this.goal = goal;
    }

    public final Plugin getPlugin(MavenProjectStructure projectStructure, BiFunction<String, VersionId, ProjectFileAccessProvider.FileAccessContext> versionFileAccessContextProvider)
    {
        Plugin plugin = MavenPluginTools.newPlugin(this.groupId, this.artifactId, this.version);
        MavenPluginTools.addPluginExecution(plugin, this.phase, this.goal);
        configurePlugin(projectStructure, versionFileAccessContextProvider, c -> MavenPluginTools.addConfiguration(plugin, c));
        addDependencies(projectStructure, versionFileAccessContextProvider, plugin::addDependency);
        return plugin;
    }

    protected abstract void configurePlugin(MavenProjectStructure projectStructure, BiFunction<String, VersionId, ProjectFileAccessProvider.FileAccessContext> versionFileAccessContextProvider, Consumer<? super Xpp3Dom> configConsumer);

    protected void addDependencies(MavenProjectStructure projectStructure, BiFunction<String, VersionId, ProjectFileAccessProvider.FileAccessContext> versionFileAccessContextProvider, Consumer<? super Dependency> dependencyConsumer)
    {
        // None by default
    }
}
