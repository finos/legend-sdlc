// Copyright 2022 Goldman Sachs
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

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.finos.legend.sdlc.server.project.ProjectStructurePlatformExtensions;
import org.finos.legend.sdlc.server.project.ProjectStructurePlatformExtensions.ExtensionsCollection;
import org.finos.legend.sdlc.server.project.ProjectStructurePlatformExtensions.Platform;
import org.finos.legend.sdlc.server.project.config.ProjectPlatformsConfiguration.ExtensionsCollectionMetadata;
import org.finos.legend.sdlc.server.project.config.ProjectPlatformsConfiguration.PlatformMetadata;
import org.finos.legend.sdlc.server.project.config.ProjectPlatformsConfiguration.PlatformVersion;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;

public class TestProjectPlatformsConfiguration
{
    @Test
    public void testResolveNullPlatformVersion()
    {
        Assert.assertNull(ProjectPlatformsConfiguration.resolvePlatformVersion("abc", new PlatformMetadata(null, null, new PlatformVersion(null, null))));
    }

    @Test
    public void testResolveExplicitPlatformVersion()
    {
        Assert.assertEquals("1.2.3", ProjectPlatformsConfiguration.resolvePlatformVersion("abc", new PlatformMetadata(null, null, new PlatformVersion("1.2.3", null))));
    }

    @Test
    public void testResolvePlatformVersionFromPackage()
    {
        String platformName = "test-platform";
        String groupId = "org.finos.legend.sdlc.test";
        String packageName = "test-extension-collection-generation";
        PlatformVersion version = new PlatformVersion(null, packageName);
        String expectedVersion = "90.31.11111";
        Assert.assertEquals(expectedVersion, ProjectPlatformsConfiguration.resolvePlatformVersion(platformName, new PlatformMetadata(groupId, null, version)));

        RuntimeException noGroupId = Assert.assertThrows(RuntimeException.class, () -> ProjectPlatformsConfiguration.resolvePlatformVersion(platformName, new PlatformMetadata(null, null, version)));
        Assert.assertEquals("Cannot get version of platform '" + platformName + "' from package '" + packageName + "' without a group id", noGroupId.getMessage());

        RuntimeException noResource = Assert.assertThrows(RuntimeException.class, () -> ProjectPlatformsConfiguration.resolvePlatformVersion(platformName, new PlatformMetadata(groupId, null, new PlatformVersion(null, "unknown-package"))));
        Assert.assertEquals("Error loading version for platform '" + platformName + "' from groupId '" + groupId + "' and package 'unknown-package': could not find resource 'META-INF/maven/" + groupId + "/unknown-package/pom.properties'", noResource.getMessage());

        // test from a real package org.finos.legend.engine:legend-engine-extensions-collection-generation - we can only assert that it's not null
        Assert.assertNotNull(ProjectPlatformsConfiguration.resolvePlatformVersion("legend-engine", new PlatformMetadata("org.finos.legend.engine", null, new PlatformVersion(null, "legend-engine-extensions-collection-generation"))));
    }

    @Test
    public void testEmptyConfig()
    {
        ProjectPlatformsConfiguration emptyConfig = new ProjectPlatformsConfiguration(null, null);
        ProjectStructurePlatformExtensions emptyExtensions = emptyConfig.buildProjectStructurePlatformExtensions();

        Assert.assertEquals(Lists.fixedSize.empty(), emptyExtensions.getPlatforms());
        assertNoPlatform(emptyExtensions, "unknown");
        assertNoExtensionCollection(emptyExtensions, "unknown");
    }

    @Test
    public void testNonEmptyConfig()
    {
        String platform1Name = "abc";
        String platform1GroupId = "xyz";
        String platform1Version = "1.2.3";
        Platform platform1 = new Platform(platform1Name, platform1GroupId, null, platform1Version);

        String platform2Name = "test-platform";
        String platform2GroupId = "org.finos.legend.sdlc.test";
        String platform2Package = "test-extension-collection-generation";
        Map<Integer, String> platform2Versions = Maps.immutable.with(11, "80.0.1", 12, "85.3.2").castToMap();
        Platform platform2 = new Platform(platform2Name, platform2GroupId, platform2Versions, "90.31.11111");

        ExtensionsCollection generationExtension = new ExtensionsCollection("generation", platform2Name, "test-extension-collection-generation");

        ProjectPlatformsConfiguration config = new ProjectPlatformsConfiguration(
                Maps.mutable.with(
                        platform1Name, new PlatformMetadata(platform1GroupId, null, new PlatformVersion(platform1Version, null)),
                        platform2Name, new PlatformMetadata(platform2GroupId, platform2Versions, new PlatformVersion(null, platform2Package))),
                Collections.singletonMap("generation", new ExtensionsCollectionMetadata(generationExtension.getPlatform(), generationExtension.getArtifactId())));
        ProjectStructurePlatformExtensions extensions = config.buildProjectStructurePlatformExtensions();

        Assert.assertEquals(Lists.fixedSize.with(platform1, platform2), extensions.getPlatforms());
        assertNoPlatform(extensions, "unknown");
        Assert.assertEquals(platform1, extensions.getPlatform(platform1Name));
        Assert.assertEquals(platform2, extensions.getPlatform(platform2Name));

        assertNoExtensionCollection(extensions, "unknown");
        Assert.assertEquals(generationExtension, extensions.getExtensionsCollection("generation"));
    }

    @Test
    public void testInvalidConfig()
    {
        String name = "unknown-platform";
        String groupId = "not.real";
        String packageName = "not-a-package";

        RuntimeException noGroupId = Assert.assertThrows(
                RuntimeException.class,
                () -> new ProjectPlatformsConfiguration(Maps.mutable.with(name, new PlatformMetadata(null, null, new PlatformVersion(null, packageName))), null).buildProjectStructurePlatformExtensions());
        Assert.assertEquals("Cannot get version of platform '" + name + "' from package '" + packageName + "' without a group id", noGroupId.getMessage());

        RuntimeException unknownPackage = Assert.assertThrows(
                RuntimeException.class,
                () -> new ProjectPlatformsConfiguration(Maps.mutable.with(name, new PlatformMetadata(groupId, null, new PlatformVersion(null, packageName))), null).buildProjectStructurePlatformExtensions());
        Assert.assertEquals("Error loading version for platform '" + name + "' from groupId '" + groupId + "' and package '" + packageName + "': could not find resource 'META-INF/maven/" + groupId + "/" + packageName + "/pom.properties'", unknownPackage.getMessage());
    }

    private void assertNoPlatform(ProjectStructurePlatformExtensions extensions, String platformName)
    {
        IllegalArgumentException e = Assert.assertThrows(IllegalArgumentException.class, () -> extensions.getPlatform(platformName));
        Assert.assertEquals("No platform metadata found for platform '" + platformName + "'", e.getMessage());
    }

    private void assertNoExtensionCollection(ProjectStructurePlatformExtensions extensions, String extensionName)
    {
        Assert.assertFalse(extensionName, extensions.containsExtension(extensionName));
        IllegalArgumentException e2 = Assert.assertThrows(IllegalArgumentException.class, () -> extensions.getExtensionsCollection(extensionName));
        Assert.assertEquals("No extension collection found for extension name '" + extensionName + "'", e2.getMessage());
    }
}
