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
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactType;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;

import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class LegendVersionPackagePluginMavenHelper extends AbstractLegendMavenPluginHelper
{
    private final List<String> entitySourceDirectories;
    private final String outputDirectory;

    public LegendVersionPackagePluginMavenHelper(String groupId, String artifactId,  String version, List<String> entitySourceDirectories, String outputDirectory)
    {
        super(groupId, artifactId, version, "compile", "version-qualify-packages");
        this.entitySourceDirectories = entitySourceDirectories;
        this.outputDirectory = outputDirectory;
    }

    public LegendVersionPackagePluginMavenHelper(String groupId, String artifactId, String version, String entitySourceDirectory)
    {
        this(groupId, artifactId, version, Collections.singletonList(entitySourceDirectory), null);
    }

    public LegendVersionPackagePluginMavenHelper(String version, List<String> entitySourceDirectories, String outputDirectory)
    {
        this("org.finos.legend.sdlc", "legend-sdlc-version-package-maven-plugin", version, entitySourceDirectories, outputDirectory);
    }

    public LegendVersionPackagePluginMavenHelper(String version, String entitySourceDirectory)
    {
        this(version, Collections.singletonList(entitySourceDirectory), null);
    }

    @Override
    protected void configurePlugin(MavenProjectStructure projectStructure, BiFunction<String, VersionId, ProjectFileAccessProvider.FileAccessContext> versionFileAccessContextProvider, Consumer<? super Xpp3Dom> configConsumer)
    {
        if ((this.entitySourceDirectories != null) && !this.entitySourceDirectories.isEmpty())
        {
            configConsumer.accept(MavenPluginTools.newDom("entitySourceDirectories", this.entitySourceDirectories.stream().map(d -> MavenPluginTools.newDom("entitySourceDirectory", d))));
        }

        if (this.outputDirectory != null)
        {
            configConsumer.accept(MavenPluginTools.newDom("outputDirectory", this.outputDirectory));
        }
    }

    @Override
    protected void addDependencies(MavenProjectStructure projectStructure, BiFunction<String, VersionId, ProjectFileAccessProvider.FileAccessContext> versionFileAccessContextProvider, Consumer<? super Dependency> dependencyConsumer)
    {
        projectStructure.getProjectDependenciesAsMavenDependencies(ArtifactType.versioned_entities, versionFileAccessContextProvider, true).forEach(dependencyConsumer);
    }
}
