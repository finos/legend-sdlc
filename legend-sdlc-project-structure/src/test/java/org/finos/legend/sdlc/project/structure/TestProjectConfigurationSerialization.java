// Copyright 2026 Goldman Sachs
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

package org.finos.legend.sdlc.project.structure;

import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectStructureVersion;
import org.finos.legend.sdlc.project.files.ProjectFiles;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

/**
 * Pins the project.json wire format around the structure-version-scoped option flags (re-architecture seam S1):
 * {@code runDependencyTests} and {@code produceShadedServiceJar} serialize as top-level fields, present only when
 * set. No further top-level option fields may be added; new version-/extension-scoped options arrive as namespaced
 * configuration bags with the project-structure-configuration-options plan, which replaces the expectations here
 * deliberately when it migrates the persisted form.
 */
public class TestProjectConfigurationSerialization
{
    @Test
    public void testWireFormatWithFlagsSet()
    {
        SimpleProjectConfiguration config = newConfiguration(Boolean.TRUE, Boolean.FALSE);

        String json = new String(ProjectStructure.serializeProjectConfiguration(config), StandardCharsets.UTF_8);
        Assert.assertTrue(json, json.contains("\"runDependencyTests\" : true"));
        Assert.assertTrue(json, json.contains("\"produceShadedServiceJar\" : false"));
        Assert.assertFalse(json, json.contains("structureConfiguration"));
        Assert.assertFalse(json, json.contains("extensionConfiguration"));

        ProjectConfiguration read = ProjectStructure.readProjectConfiguration(
                ProjectFiles.newByteArrayProjectFile("/project.json", ProjectStructure.serializeProjectConfiguration(config)));
        Assert.assertEquals(Boolean.TRUE, read.getRunDependencyTests());
        Assert.assertEquals(Boolean.FALSE, read.getProduceShadedServiceJar());
    }

    @Test
    public void testWireFormatWithoutFlags()
    {
        SimpleProjectConfiguration config = newConfiguration(null, null);

        String json = new String(ProjectStructure.serializeProjectConfiguration(config), StandardCharsets.UTF_8);
        Assert.assertFalse(json, json.contains("runDependencyTests"));
        Assert.assertFalse(json, json.contains("produceShadedServiceJar"));

        ProjectConfiguration read = ProjectStructure.readProjectConfiguration(
                ProjectFiles.newByteArrayProjectFile("/project.json", ProjectStructure.serializeProjectConfiguration(config)));
        Assert.assertNull(read.getRunDependencyTests());
        Assert.assertNull(read.getProduceShadedServiceJar());
    }

    private SimpleProjectConfiguration newConfiguration(Boolean runDependencyTests, Boolean produceShadedServiceJar)
    {
        return SimpleProjectConfiguration.newConfiguration("PROJ-1", ProjectType.MANAGED,
                ProjectStructureVersion.newProjectStructureVersion(13), null, "org.finos.test", "test-project",
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), runDependencyTests, produceShadedServiceJar);
    }
}
