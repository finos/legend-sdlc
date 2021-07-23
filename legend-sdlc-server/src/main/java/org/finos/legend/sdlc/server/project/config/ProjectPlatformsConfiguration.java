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
import org.finos.legend.sdlc.server.project.ProjectStructurePlatformExtensions;

import java.util.Collections;
import java.util.Map;

public class ProjectPlatformsConfiguration
{

    Map<String, PlatformCoordinates> platformCoordinates;

    Map<String, ExtensionsCollectionCoordinates> extensionsCollectionCoordinates;

    public ProjectPlatformsConfiguration(Map<String, PlatformCoordinates> platformCoordinates, Map<String, ExtensionsCollectionCoordinates> collections)
    {
        if ((platformCoordinates == null) || platformCoordinates.isEmpty())
        {
            this.platformCoordinates = Collections.emptyMap();
        }
        else
        {
            this.platformCoordinates = platformCoordinates;
        }
        if ((collections == null) || collections.isEmpty())
        {
            this.extensionsCollectionCoordinates = Collections.emptyMap();
        }
        else
        {
            this.extensionsCollectionCoordinates = Maps.mutable.empty();
            collections.forEach((k, v) ->
            {
                String platform = v.platform;
                if (!this.platformCoordinates.containsKey(platform))
                {
                    throw new IllegalArgumentException("No platform metadata found for platform '" + platform  + "'");
                }
                this.extensionsCollectionCoordinates.put(k, v);
            });
        }
    }


    public static ProjectStructurePlatformExtensions buildProjectExtensionsOverride(ProjectPlatformsConfiguration configuration)
    {
        if (configuration == null)
        {
            return null;
        }
        Map<String, ProjectStructurePlatformExtensions.PlatformCoordinates> platforms = Maps.mutable.empty();
        Map<String, ProjectStructurePlatformExtensions.ExtensionsCollectionCoordinates> collections = Maps.mutable.empty();
        configuration.platformCoordinates.forEach((k, v) -> platforms.put(k, new ProjectStructurePlatformExtensions.PlatformCoordinates(k, v.getGroupId(), v.getVersion())));
        configuration.extensionsCollectionCoordinates.forEach((k, v) -> collections.put(k, new ProjectStructurePlatformExtensions.ExtensionsCollectionCoordinates(k, v.platform, v.artifactId)));
        return new ProjectStructurePlatformExtensions(platforms, collections);
    }

    public static class PlatformCoordinates
    {
        String groupId;
        String version;

        public PlatformCoordinates(String groupId, String version)
        {
            this.groupId = groupId;
            this.version = version;
        }

        @JsonCreator
        public static PlatformCoordinates newConfig(@JsonProperty("groupId") String groupId, @JsonProperty("version") String version)
        {
            return new PlatformCoordinates(groupId, version);
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


    public static class ExtensionsCollectionCoordinates
    {
        String platform;
        String artifactId;

        public ExtensionsCollectionCoordinates(String platform, String artifactId)
        {
            this.platform = platform;
            this.artifactId = artifactId;
        }

        @JsonCreator
        public static ExtensionsCollectionCoordinates newConfig(@JsonProperty("platform") String platform, @JsonProperty("artifactId") String artifactId)
        {
            return new ExtensionsCollectionCoordinates(platform, artifactId);
        }
    }

}
