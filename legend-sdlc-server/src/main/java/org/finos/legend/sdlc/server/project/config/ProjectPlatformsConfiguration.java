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
import org.eclipse.collections.impl.factory.Lists;
import org.finos.legend.sdlc.server.project.ProjectStructurePlatformExtensions;

import java.util.Collections;
import java.util.List;
import java.util.Map;

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
        List<ProjectStructurePlatformExtensions.Platform> platforms = Lists.mutable.empty();
        List<ProjectStructurePlatformExtensions.ExtensionsCollection> collections = Lists.mutable.empty();
        this.platformMetadata.forEach((k, v) -> platforms.add(new ProjectStructurePlatformExtensions.Platform(k, v.getGroupId(), v.getProjectStructureStartingVersions())));
        this.extensionsCollectionMetadata.forEach((k, v) -> collections.add(new ProjectStructurePlatformExtensions.ExtensionsCollection(k, v.platform, v.artifactId)));
        return ProjectStructurePlatformExtensions.newPlatformExtensions(platforms, collections);
    }

    public static class PlatformMetadata
    {
        private final String groupId;
        private final Map<Integer, String> projectStructureStartingVersions;

        public PlatformMetadata(String groupId, Map<Integer, String> startingProjectStructureVersions)
        {
            this.groupId = groupId;
            this.projectStructureStartingVersions = startingProjectStructureVersions;
        }

        @JsonCreator
        public static PlatformMetadata newConfig(@JsonProperty("groupId") String groupId, @JsonProperty("version") Map<Integer, String> version)
        {
            return new PlatformMetadata(groupId, version);
        }

        public String getGroupId()
        {
            return groupId;
        }

        public Map<Integer, String> getProjectStructureStartingVersions()
        {
            return projectStructureStartingVersions;
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
