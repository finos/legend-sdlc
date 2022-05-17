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

package org.finos.legend.sdlc.server.gitlab.finos;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.impl.utility.MapIterate;
import org.finos.legend.sdlc.server.project.config.ProjectFileConfiguration;
import org.finos.legend.sdlc.server.project.extension.DefaultProjectStructureExtension;
import org.finos.legend.sdlc.server.project.extension.ProjectStructureExtension;
import org.finos.legend.sdlc.server.project.extension.SimpleProjectStructureExtensionProvider;

import java.util.Map;

public class FinosGitlabProjectStructureExtensionProvider extends SimpleProjectStructureExtensionProvider
{
    public FinosGitlabProjectStructureExtensionProvider()
    {
        super(newBuilder(FinosExtensionConfig.class).withConfigFromResource("org/finos/legend/sdlc/server/gitlab/finos/finos-extension.yaml"));
    }

    private static class FinosExtensionConfig implements ExtensionConfiguration
    {
        private final Map<String, String> files;

        private FinosExtensionConfig(Map<String, String> files)
        {
            this.files = files;
        }

        @Override
        public ProjectStructureExtension build(int projectStructureVersion, int extensionVersion)
        {
            return DefaultProjectStructureExtension.newProjectStructureExtensionFromConfig(projectStructureVersion, extensionVersion, MapIterate.collect(this.files, f -> f, ProjectFileConfiguration::newResourceName, Maps.mutable.ofInitialCapacity(this.files.size())));
        }

        @JsonCreator
        static FinosExtensionConfig newConfig(@JsonProperty("files") Map<String, String> files)
        {
            return new FinosExtensionConfig(files);
        }
    }
}
