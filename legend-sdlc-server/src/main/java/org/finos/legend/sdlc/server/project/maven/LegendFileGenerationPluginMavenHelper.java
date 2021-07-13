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
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;

import java.util.Collections;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class LegendFileGenerationPluginMavenHelper extends AbstractLegendMavenPluginHelper
{
    public LegendFileGenerationPluginMavenHelper(String groupId, String artifactId, String version, Dependency extensionsGenerationCollection)
    {
        super(groupId, artifactId, version, "generate-sources", "generate-file-generations", Collections.singletonList(extensionsGenerationCollection));
    }

    @Override
    protected void configurePlugin(MavenProjectStructure projectStructure, BiFunction<String, VersionId, ProjectFileAccessProvider.FileAccessContext> versionFileAccessContextProvider, Consumer<? super Xpp3Dom> configConsumer)
    {
        String inclusionDirectory;
        if (projectStructure instanceof MultiModuleMavenProjectStructure)
        {
            MultiModuleMavenProjectStructure multiModuleProjectStructure = (MultiModuleMavenProjectStructure) projectStructure;
            inclusionDirectory = "${project.parent.basedir}/" + multiModuleProjectStructure.getModuleFullName(multiModuleProjectStructure.getEntitiesModuleName()) + "/target/classes";
        }
        else
        {
            inclusionDirectory = "${project.basedir}/target/classes";
        }
        configConsumer.accept(MavenPluginTools.newDom("inclusions", MavenPluginTools.newDom("directories", MavenPluginTools.newDom("directory", inclusionDirectory))));
    }

    @Override
    protected void addDependencies(MavenProjectStructure projectStructure, BiFunction<String, VersionId, ProjectFileAccessProvider.FileAccessContext> versionFileAccessContextProvider, Consumer<? super Dependency> dependencyConsumer)
    {
        super.addDependencies(projectStructure, versionFileAccessContextProvider, dependencyConsumer);
        if (projectStructure instanceof MultiModuleMavenProjectStructure)
        {
            MultiModuleMavenProjectStructure multiModuleProjectStructure = (MultiModuleMavenProjectStructure) projectStructure;
            dependencyConsumer.accept(multiModuleProjectStructure.getModuleWithProjectVersionDependency(multiModuleProjectStructure.getEntitiesModuleName()));
        }
    }
}
