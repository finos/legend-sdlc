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

import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Maps;

import java.util.List;
import java.util.Map;

public class ProjectStructurePlatformExtensions
{
    private final Map<String, Platform> platforms;

    private final Map<String, ExtensionsCollection> extensionsCollections;

    private ProjectStructurePlatformExtensions(Map<String, Platform> platformExtensions, Map<String, ExtensionsCollection> collectionExtensions)
    {
        this.platforms = platformExtensions;
        this.extensionsCollections = collectionExtensions;
    }

    public static ProjectStructurePlatformExtensions newPlatformExtensions(Iterable<Platform> platforms, Iterable<ExtensionsCollection> collections)
    {
        MutableMap<String, Platform> platformsMap = Maps.mutable.empty();
        platforms.forEach(platform ->
        {
            if (platformsMap.containsKey(platform.name))
            {
                throw new IllegalArgumentException("Multiple platforms defined for platform '" + platform.name + "'");
            }
            platformsMap.put(platform.name, platform);
        });
        MutableMap<String, ExtensionsCollection> extensionsCollectionsMap = Maps.mutable.empty();
        collections.forEach(collection ->
        {
            if (!platformsMap.containsKey(collection.platform))
            {
                throw new IllegalArgumentException("No platform metadata found for platform '" + collection.platform + "'");
            }
            if (extensionsCollectionsMap.containsKey(collection.name))
            {
                throw new IllegalArgumentException("Multiple extensions collection defined for extension '" + collection.name + "'");
            }
            extensionsCollectionsMap.put(collection.name, collection);
        });
        return new ProjectStructurePlatformExtensions(platformsMap, extensionsCollectionsMap);
    }

    public List<Platform> getPlatforms()
    {
        return Lists.mutable.withAll(this.platforms.values());
    }

    public Platform getPlatform(String platform)
    {
        if (!this.platforms.containsKey(platform))
        {
            throw new IllegalArgumentException("No platform metadata found for platform '" + platform + "'");
        }
        return this.platforms.get(platform);
    }

    public ExtensionsCollection getExtensionsCollection(String extension)
    {
        if (!this.containsExtension(extension))
        {
            throw new IllegalArgumentException("No extension collection found for extension name '" + extension + "'");
        }
        return this.extensionsCollections.get(extension);
    }

    public boolean containsExtension(String extension)
    {
        return this.extensionsCollections.containsKey(extension);
    }

    public static class Platform
    {
        private final String name;
        private final String groupId;
        private final Map<Integer, String> projectStructureVersions;

        public Platform(String name, String groupId, Map<Integer, String> projectStructureVersionsMap)
        {
            this.name = name;
            this.groupId = groupId;
            this.projectStructureVersions = projectStructureVersionsMap;
        }

        public String getName()
        {
            return name;
        }

        public String getGroupId()
        {
            return groupId;
        }

        public String getPublicStructureVersion(int version)
        {
            if (!this.projectStructureVersions.containsKey(version))
            {
                throw new IllegalArgumentException("No platform version given for project structure '" + version + "'");
            }
            return this.projectStructureVersions.get(version);
        }

    }

    public static class ExtensionsCollection
    {
        private final String name;
        private final String platform;
        private final String artifactId;

        public ExtensionsCollection(String name, String platform, String artifactId)
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
