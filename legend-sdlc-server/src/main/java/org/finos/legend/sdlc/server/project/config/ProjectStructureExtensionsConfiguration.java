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

package org.finos.legend.sdlc.server.project.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.collections.api.factory.Maps;
import org.finos.legend.sdlc.server.project.ProjectStructureExtensions;

import java.util.Collections;
import java.util.Map;

public class ProjectStructureExtensionsConfiguration
{

    Map<String, ExtensionsPlatformMetadata> platforms;

    Map<String, ExtensionsCollectionMetadata> collections;

    public ProjectStructureExtensionsConfiguration(Map<String, ExtensionsPlatformMetadata> platforms, Map<String, ExtensionsCollectionMetadata> collections)
    {
        if ((platforms == null) || platforms.isEmpty())
        {
            this.platforms = Collections.emptyMap();
        }
        else
        {
            this.platforms = platforms;
        }
        if ((collections == null) || collections.isEmpty())
        {
            this.collections = Collections.emptyMap();
        }
        else
        {
            this.collections = Maps.mutable.empty();
            collections.forEach((k, v) ->
            {
                String platform = v.platform;
                if (!this.platforms.containsKey(platform))
                {
                    throw new IllegalArgumentException("No platform metadata found for platform '" + platform  + "'");
                }
                this.collections.put(k, v);
            });
        }
    }


    public static ProjectStructureExtensions buildProjectExtensionsOverride(ProjectStructureExtensionsConfiguration configuration)
    {
        if (configuration == null)
        {
            return null;
        }
        Map<String, ProjectStructureExtensions.PlatformMetadata> platforms = Maps.mutable.empty();
        Map<String, ProjectStructureExtensions.CollectionMetadata> collections = Maps.mutable.empty();
        configuration.platforms.forEach((k, v) -> platforms.put(k, new ProjectStructureExtensions.PlatformMetadata(k, v.getGroupId(), v.getVersion())));
        configuration.collections.forEach((k, v) -> collections.put(k, new ProjectStructureExtensions.CollectionMetadata(k, v.platform, v.artifactId)));
        return new ProjectStructureExtensions(platforms, collections);
    }

    public static class ExtensionsPlatformMetadata
    {
        String groupId;
        String version;

        public ExtensionsPlatformMetadata(String groupId, String version)
        {
            this.groupId = groupId;
            this.version = version;
        }

        @JsonCreator
        public static ExtensionsPlatformMetadata newConfig(@JsonProperty("groupId") String groupId, @JsonProperty("version") String version)
        {
            return new ExtensionsPlatformMetadata(groupId, version);
        }

        public String getGroupId()
        {
            return groupId;
        }

        public String getVersion()
        {
            return version;
        }
    }


    public static class ExtensionsCollectionMetadata
    {
        String platform;
        String artifactId;

        public ExtensionsCollectionMetadata(String platform, String artifactId)
        {
            this.platform = platform;
            this.artifactId = artifactId;
        }

        @JsonCreator
        public static ExtensionsCollectionMetadata newConfig(@JsonProperty("platform") String platform, @JsonProperty("artifactId") String artifactId)
        {
            return new ExtensionsCollectionMetadata(platform, artifactId);
        }
    }

}
