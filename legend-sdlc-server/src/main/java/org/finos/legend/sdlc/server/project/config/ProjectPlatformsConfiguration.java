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
import org.eclipse.collections.api.factory.Lists;
import org.finos.legend.sdlc.server.project.ProjectStructurePlatformExtensions;
import org.finos.legend.sdlc.server.tools.StringTools;

import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class ProjectPlatformsConfiguration
{
    private final Map<String, PlatformMetadata> platformMetadata;
    private final Map<String, ExtensionsCollectionMetadata> extensionsCollectionMetadata;

    public ProjectPlatformsConfiguration(Map<String, PlatformMetadata> platformMetadata, Map<String, ExtensionsCollectionMetadata> collections)
    {
        this.platformMetadata = (platformMetadata == null || platformMetadata.isEmpty()) ? Collections.emptyMap() : platformMetadata;
        this.extensionsCollectionMetadata = (collections == null || collections.isEmpty()) ? Collections.emptyMap() : collections;
    }

    public ProjectStructurePlatformExtensions buildProjectStructurePlatformExtensions()
    {
        List<ProjectStructurePlatformExtensions.Platform> platforms = Lists.mutable.ofInitialCapacity(this.platformMetadata.size());
        this.platformMetadata.forEach((k, v) -> platforms.add(new ProjectStructurePlatformExtensions.Platform(k, v.getGroupId(), v.getProjectStructureStartingVersions(), resolvePlatformVersion(k, v))));

        List<ProjectStructurePlatformExtensions.ExtensionsCollection> collections = Lists.mutable.ofInitialCapacity(this.extensionsCollectionMetadata.size());
        this.extensionsCollectionMetadata.forEach((k, v) -> collections.add(new ProjectStructurePlatformExtensions.ExtensionsCollection(k, v.platform, v.artifactId)));

        return ProjectStructurePlatformExtensions.newPlatformExtensions(platforms, collections);
    }

    public static class PlatformMetadata
    {
        private final String groupId;
        private final Map<Integer, String> projectStructureStartingVersions;
        private final PlatformVersion platformVersion;

        public PlatformMetadata(String groupId, Map<Integer, String> startingProjectStructureVersions, PlatformVersion platformVersion)
        {
            this.groupId = groupId;
            this.projectStructureStartingVersions = startingProjectStructureVersions;
            this.platformVersion = platformVersion;
        }

        @JsonCreator
        public static PlatformMetadata newConfig(@JsonProperty("groupId") String groupId,
                                                 @JsonProperty("version") Map<Integer, String> version,
                                                 @JsonProperty("platformVersion") PlatformVersion platformVersion)
        {
            return new PlatformMetadata(groupId, version, platformVersion);
        }

        public String getGroupId()
        {
            return this.groupId;
        }

        public Map<Integer, String> getProjectStructureStartingVersions()
        {
            return this.projectStructureStartingVersions;
        }

        public PlatformVersion getPlatformVersion()
        {
            return this.platformVersion;
        }
    }

    // package private for testing
    static String resolvePlatformVersion(String platformName, PlatformMetadata platformMetadata)
    {
        if (platformMetadata == null)
        {
            return null;
        }

        PlatformVersion platformVersion = platformMetadata.getPlatformVersion();
        if (platformVersion == null)
        {
            return null;
        }

        if (platformVersion.getVersion() != null)
        {
            return platformVersion.getVersion();
        }

        String packageName = platformVersion.getFromPackage();
        if (packageName != null)
        {
            String groupId = platformMetadata.getGroupId();
            if (groupId == null)
            {
                throw new RuntimeException("Cannot get version of platform '" + platformName + "' from package '" + packageName + "' without a group id");
            }

            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            String resourceName = "META-INF/maven/" + groupId + "/" + packageName + "/pom.properties";
            URL resourceURL = classLoader.getResource(resourceName);
            if (resourceURL == null)
            {
                throw new RuntimeException("Error loading version for platform '" + platformName + "' from groupId '" + groupId + "' and package '" + packageName + "': could not find resource '" + resourceName + "'");
            }

            try (InputStream is = resourceURL.openStream())
            {
                Properties properties = new Properties();
                properties.load(is);
                return properties.getProperty("version");
            }
            catch (Exception e)
            {
                StringBuilder builder = new StringBuilder("Error loading version for platform '").append(platformName)
                        .append("' from groupId '").append(groupId)
                        .append("' and package '").append(packageName).append("'");
                StringTools.appendThrowableMessageIfPresent(builder, e);
                throw new RuntimeException(builder.toString(), e);
            }
        }

        return null;
    }

    public static class PlatformVersion
    {
        private final String version;
        private final String fromPackage;

        public PlatformVersion(String version, String fromPackage)
        {
            this.version = version;
            this.fromPackage = fromPackage;
        }

        public String getVersion()
        {
            return this.version;
        }

        public String getFromPackage()
        {
            return this.fromPackage;
        }

        @JsonCreator
        public static PlatformVersion newConfig(@JsonProperty("version") String version, @JsonProperty("fromPackage") String fromPackage)
        {
            return new PlatformVersion(version, fromPackage);
        }
    }

    public static class ExtensionsCollectionMetadata
    {
        private final String platform;
        private final String artifactId;

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
