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
import org.apache.maven.model.PluginExecution;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;

import java.util.Collections;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class LegendServiceExecutionGenerationPluginMavenHelper extends AbstractLegendMavenPluginHelper
{
    public LegendServiceExecutionGenerationPluginMavenHelper(String groupId, String artifactId, String version, Dependency generationExtensionsCollection)
    {
        super(groupId, artifactId, version, "generate-sources", "generate-service-executions", Collections.singletonList(generationExtensionsCollection));
    }

    public Plugin getBuildHelperPlugin(String version)
    {
        Plugin plugin = MavenPluginTools.newPlugin("org.codehaus.mojo", "build-helper-maven-plugin", version);
        PluginExecution execution = MavenPluginTools.newPluginExecution("initialize", "add-source");
        MavenPluginTools.addPluginExecution(plugin, execution);
        MavenPluginTools.addConfiguration(execution, MavenPluginTools.newDom("sources", MavenPluginTools.newDom("source", "${project.basedir}/target/generated-sources")));
        return plugin;
    }

    @Override
    protected void configurePlugin(MavenProjectStructure projectStructure, BiFunction<String, VersionId, ProjectFileAccessProvider.FileAccessContext> versionFileAccessContextProvider, Consumer<? super Xpp3Dom> configConsumer)
    {
        configConsumer.accept(MavenPluginTools.newDom("packagePrefix", getPackagePrefix(projectStructure.getProjectConfiguration())));

        String serviceIncludeDirectory;
        if (projectStructure instanceof MultiModuleMavenProjectStructure)
        {
            MultiModuleMavenProjectStructure multiModuleProjectStructure = (MultiModuleMavenProjectStructure) projectStructure;
            serviceIncludeDirectory = "${project.parent.basedir}/" + multiModuleProjectStructure.getModuleFullName(multiModuleProjectStructure.getEntitiesModuleName()) + "/target/classes";
        }
        else
        {
            serviceIncludeDirectory = "${project.basedir}/target/classes";
        }
        configConsumer.accept(MavenPluginTools.newDom("inclusions", MavenPluginTools.newDom("directories", MavenPluginTools.newDom("directory", serviceIncludeDirectory))));
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

    private static String getPackagePrefix(ProjectConfiguration projectConfiguration)
    {
        return getPackagePrefix(projectConfiguration.getGroupId(), projectConfiguration.getArtifactId());
    }

    private static String getPackagePrefix(String groupId, String artifactId)
    {
        StringBuilder builder = new StringBuilder(groupId.length() + artifactId.length() + 2).append(groupId).append('.');

        int index = builder.length();
        builder.append(artifactId);
        if (!Character.isJavaIdentifierStart(builder.charAt(index)))
        {
            if (Character.isJavaIdentifierPart(builder.charAt(index)))
            {
                builder.insert(index++, '_');
            }
            else
            {
                builder.setCharAt(index, '_');
            }
        }
        while (++index < builder.length())
        {
            if (!Character.isJavaIdentifierPart(builder.charAt(index)))
            {
                builder.setCharAt(index, '_');
            }
        }
        return builder.toString();
    }
}
