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

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public abstract class AbstractLegendMavenPluginHelper
{
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String phase;
    private final String goal;
    private final List<Dependency> extensionsCollections;

    protected AbstractLegendMavenPluginHelper(String groupId, String artifactId, String version, String phase, String goal, List<Dependency> extensionsCollections)
    {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.phase = phase;
        this.goal = goal;
        this.extensionsCollections = Objects.requireNonNull(extensionsCollections);
    }

    protected AbstractLegendMavenPluginHelper(String groupId, String artifactId, String version, String phase, String goal, Dependency extensionsCollection)
    {
        this(groupId, artifactId, version, phase, goal, Collections.singletonList(Objects.requireNonNull(extensionsCollection)));
    }

    protected AbstractLegendMavenPluginHelper(String groupId, String artifactId, String version, String phase, String goal)
    {
        this(groupId, artifactId, version, phase, goal, Collections.emptyList());
    }

    public final Plugin getPlugin(MavenProjectStructure projectStructure)
    {
        Plugin plugin = MavenPluginTools.newPlugin(this.groupId, this.artifactId, null);
        MavenPluginTools.addPluginExecution(plugin, this.phase, this.goal);
        configurePlugin(projectStructure, c -> MavenPluginTools.addConfiguration(plugin, c));
        return plugin;
    }

    public final Plugin getPluginManagementPlugin(MavenProjectStructure projectStructure)
    {
        Plugin plugin = MavenPluginTools.newPlugin(this.groupId, this.artifactId, this.version);
        addDependencies(projectStructure, plugin::addDependency);
        return plugin;
    }

    @Deprecated
    public final Plugin getPlugin(MavenProjectStructure projectStructure, BiFunction<String, VersionId, ProjectFileAccessProvider.FileAccessContext> versionFileAccessContextProvider)
    {
        return getPlugin(projectStructure);
    }

    @Deprecated
    public final Plugin getPluginManagementPlugin(MavenProjectStructure projectStructure, BiFunction<String, VersionId, ProjectFileAccessProvider.FileAccessContext> versionFileAccessContextProvider)
    {
        return getPluginManagementPlugin(projectStructure);
    }

    protected abstract void configurePlugin(MavenProjectStructure projectStructure, Consumer<? super Xpp3Dom> configConsumer);

    protected void addDependencies(MavenProjectStructure projectStructure, Consumer<? super Dependency> dependencyConsumer)
    {
        this.extensionsCollections.forEach(dependencyConsumer);
    }
}
