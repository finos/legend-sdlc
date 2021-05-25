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

import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.collections.impl.utility.ListIterate;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.serialization.EntitySerializer;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.project.ProjectStructure;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class LegendEntityPluginMavenHelper extends AbstractLegendMavenPluginHelper
{

    public LegendEntityPluginMavenHelper(String groupId, String artifactId, String version)
    {
        super(groupId, artifactId, version, "compile", "process-entities");
    }

    public LegendEntityPluginMavenHelper(String version)
    {
        this("org.finos.legend.sdlc", "legend-sdlc-entity-maven-plugin", version);
    }

    @Override
    protected void configurePlugin(MavenProjectStructure projectStructure, BiFunction<String, VersionId, ProjectFileAccessProvider.FileAccessContext> versionFileAccessContextProvider, Consumer<? super Xpp3Dom> configConsumer)
    {
        List<Xpp3Dom> sourceDirectories;
        if (projectStructure instanceof MultiModuleMavenProjectStructure)
        {
            MultiModuleMavenProjectStructure multiModuleProjectStructure = (MultiModuleMavenProjectStructure) projectStructure;
            String entitiesModulePath = multiModuleProjectStructure.getModulePath(multiModuleProjectStructure.getEntitiesModuleName()) + "/";
            sourceDirectories = ListIterate.collect(projectStructure.getEntitySourceDirectories(), sd -> getSourceDirectoryConfig(sd, entitiesModulePath));
        }
        else
        {
            sourceDirectories = ListIterate.collect(projectStructure.getEntitySourceDirectories(), this::getSourceDirectoryConfig);
        }
        configConsumer.accept(MavenPluginTools.newDom("sourceDirectories", sourceDirectories));
    }

    private Xpp3Dom getSourceDirectoryConfig(ProjectStructure.EntitySourceDirectory sourceDirectory)
    {
        return getSourceDirectoryConfig(sourceDirectory.getDirectory(), sourceDirectory.getSerializer());
    }

    private Xpp3Dom getSourceDirectoryConfig(ProjectStructure.EntitySourceDirectory sourceDirectory, String modulePath)
    {
        String rawDirectory = sourceDirectory.getDirectory();
        String directory = rawDirectory.startsWith(modulePath) ? rawDirectory.substring(modulePath.length()) : rawDirectory;
        return getSourceDirectoryConfig(directory, sourceDirectory.getSerializer());
    }

    private Xpp3Dom getSourceDirectoryConfig(String sourceDirectory, EntitySerializer serializer)
    {
        Xpp3Dom sourceDirectoryConfig = MavenPluginTools.newDom("sourceDirectory", MavenPluginTools.newDom("directory", sourceDirectory));
        if (!sourceDirectory.endsWith("/" + serializer.getName()))
        {
            sourceDirectoryConfig.addChild(MavenPluginTools.newDom("serializer", serializer.getName()));
        }
        return sourceDirectoryConfig;
    }
}
