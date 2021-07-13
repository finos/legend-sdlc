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

package org.finos.legend.sdlc.server.project;

import java.util.Map;

public class ProjectStructurePlatformExtensions
{
    private final Map<String, PlatformCoordinates> platformCoordinatesMap;

    private final Map<String, ExtensionsCollectionCoordinates> extensionsCollectionCoordinatesMap;

    public ProjectStructurePlatformExtensions(Map<String, PlatformCoordinates> platformExtensions, Map<String, ExtensionsCollectionCoordinates> collectionExtensions)
    {
        this.platformCoordinatesMap = platformExtensions;
        this.extensionsCollectionCoordinatesMap = collectionExtensions;
    }

    public Map<String, ExtensionsCollectionCoordinates> getExtensionsCollectionCoordinatesMap()
    {
        return extensionsCollectionCoordinatesMap;
    }

    public Map<String, PlatformCoordinates> getPlatformCoordinatesMap()
    {
        return platformCoordinatesMap;
    }

    public PlatformCoordinates getPlatformCoordinates(String platform)
    {
        if (!this.platformCoordinatesMap.containsKey(platform))
        {
            throw new IllegalArgumentException("No platform metadata found for platform '" + platform + "'");
        }
        return this.platformCoordinatesMap.get(platform);
    }

    public static class PlatformCoordinates
    {
        String name;
        String groupId;
        String version;

        public PlatformCoordinates(String name, String groupId, String version)
        {
            this.name = name;
            this.groupId = groupId;
            this.version = version;
        }

        public String getName()
        {
            return name;
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
        String name;
        String platform;
        String artifactId;

        public ExtensionsCollectionCoordinates(String name, String platform, String artifactId)
        {
            this.name = name;
            this.platform = platform;
            this.artifactId = artifactId;
        }

        public String getName()
        {
            return name;
        }

        public String getArtifactId()
        {
            return artifactId;
        }

        public String getPlatform()
        {
            return platform;
        }
    }

}
