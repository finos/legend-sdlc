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

import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.map.MapIterable;
import org.eclipse.collections.api.map.MutableMap;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ProjectStructurePlatformExtensions
{
    private final MapIterable<String, Platform> platformsByName;
    private final ImmutableList<Platform> platforms;
    private final MapIterable<String, ExtensionsCollection> extensionsCollections;

    private ProjectStructurePlatformExtensions(MapIterable<String, Platform> platformsByName, MapIterable<String, ExtensionsCollection> collectionExtensions)
    {
        this.platformsByName = platformsByName;
        this.platforms = this.platformsByName.valuesView().toSortedListBy(Platform::getName).toImmutable();
        this.extensionsCollections = collectionExtensions;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }

        if (!(other instanceof ProjectStructurePlatformExtensions))
        {
            return false;
        }

        ProjectStructurePlatformExtensions that = (ProjectStructurePlatformExtensions) other;
        return this.platformsByName.equals(that.platformsByName) && this.extensionsCollections.equals(that.extensionsCollections);
    }

    @Override
    public int hashCode()
    {
        return this.platforms.hashCode() ^ this.extensionsCollections.size();
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder(getClass().getSimpleName()).append('{');
        this.platforms.forEachWithIndex((p, i) ->
        {
            if (i > 0)
            {
                builder.append(", ");
            }
            builder.append(p);
            ExtensionsCollection extensionsCollection = this.extensionsCollections.get(p.getName());
            if (extensionsCollection != null)
            {
                builder.append(" (").append(extensionsCollection).append(")");
            }
        });
        return builder.append('}').toString();
    }

    public List<Platform> getPlatforms()
    {
        return this.platforms.castToList();
    }

    public Platform getPlatform(String platformName)
    {
        Platform platform = this.platformsByName.get(platformName);
        if (platform == null)
        {
            throw new IllegalArgumentException("No platform metadata found for platform '" + platformName + "'");
        }
        return platform;
    }

    public ExtensionsCollection getExtensionsCollection(String extension)
    {
        ExtensionsCollection collection = this.extensionsCollections.get(extension);
        if (collection == null)
        {
            throw new IllegalArgumentException("No extension collection found for extension name '" + extension + "'");
        }
        return collection;
    }

    public boolean containsExtension(String extension)
    {
        return this.extensionsCollections.containsKey(extension);
    }

    public static ProjectStructurePlatformExtensions newPlatformExtensions(Iterable<? extends Platform> platforms, Iterable<? extends ExtensionsCollection> collections)
    {
        MutableMap<String, Platform> platformsMap = Maps.mutable.empty();
        platforms.forEach(platform ->
        {
            Platform old = platformsMap.put(platform.getName(), platform);
            if ((old != null) && (old != platform))
            {
                throw new IllegalArgumentException("Multiple platforms defined for platform '" + platform.getName() + "'");
            }
        });
        MutableMap<String, ExtensionsCollection> extensionsCollectionsMap = Maps.mutable.empty();
        collections.forEach(collection ->
        {
            if (!platformsMap.containsKey(collection.getPlatform()))
            {
                throw new IllegalArgumentException("No platform metadata found for platform '" + collection.getPlatform() + "'");
            }
            ExtensionsCollection old = extensionsCollectionsMap.put(collection.getName(), collection);
            if ((old != null) && (old != collection))
            {
                throw new IllegalArgumentException("Multiple extensions collection defined for extension '" + collection.getName() + "'");
            }
        });
        return new ProjectStructurePlatformExtensions(platformsMap, extensionsCollectionsMap);
    }

    public static class Platform
    {
        private final String name;
        private final String groupId;
        private final String platformVersion;
        private final Map<Integer, String> projectStructureVersions;

        public Platform(String name, String groupId, Map<Integer, String> projectStructureVersionsMap, String platformVersion)
        {
            this.name = name;
            this.groupId = groupId;
            this.platformVersion = platformVersion;
            this.projectStructureVersions = projectStructureVersionsMap;
        }

        @Override
        public boolean equals(Object other)
        {
            if (this == other)
            {
                return true;
            }

            if (!(other instanceof Platform))
            {
                return false;
            }

            Platform that = (Platform) other;
            return Objects.equals(this.name, that.name) &&
                    Objects.equals(this.groupId, that.groupId) &&
                    Objects.equals(this.platformVersion, that.platformVersion) &&
                    Objects.equals(this.projectStructureVersions, that.projectStructureVersions);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(this.name, this.groupId, this.platformVersion);
        }

        @Override
        public String toString()
        {
            StringBuilder builder = new StringBuilder(getClass().getSimpleName());
            appendPossiblyNullString(builder.append("{name="), this.name);
            appendPossiblyNullString(builder.append(", groupId="), this.groupId);
            appendPossiblyNullString(builder.append(", platformVersion="), this.platformVersion);
            builder.append(", projectStructureVersions=").append(this.projectStructureVersions).append('}');
            return builder.toString();
        }

        public String getName()
        {
            return this.name;
        }

        public String getGroupId()
        {
            return this.groupId;
        }

        public String getPlatformVersion()
        {
            return this.platformVersion;
        }

        public String getPublicStructureVersion(int version)
        {
            if (this.projectStructureVersions != null)
            {
                String platformVersion = this.projectStructureVersions.get(version);
                if (platformVersion != null)
                {
                    return platformVersion;
                }
            }

            return getPlatformVersion();
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

        @Override
        public boolean equals(Object other)
        {
            if (this == other)
            {
                return true;
            }

            if (!(other instanceof ExtensionsCollection))
            {
                return false;
            }

            ExtensionsCollection that = (ExtensionsCollection) other;
            return Objects.equals(this.name, that.name) &&
                    Objects.equals(this.platform, that.platform) &&
                    Objects.equals(this.artifactId, that.artifactId);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(this.name, this.platform, this.artifactId);
        }

        @Override
        public String toString()
        {
            StringBuilder builder = new StringBuilder(getClass().getSimpleName());
            appendPossiblyNullString(builder.append("{name="), this.name);
            appendPossiblyNullString(builder.append(", platform="), this.platform);
            appendPossiblyNullString(builder.append(", artifactId="), this.artifactId).append('}');
            return builder.toString();
        }

        public String getName()
        {
            return this.name;
        }

        public String getArtifactId()
        {
            return this.artifactId;
        }

        public String getPlatform()
        {
            return this.platform;
        }
    }

    private static StringBuilder appendPossiblyNullString(StringBuilder builder, String string)
    {
        return (string == null) ?
                builder.append((String) null) :
                builder.append("'").append(string).append("'");
    }
}
