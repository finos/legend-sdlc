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
import org.finos.legend.sdlc.domain.model.project.configuration.MetamodelDependency;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectStructureVersion;
import org.finos.legend.sdlc.project.files.ProjectFiles;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Pins the project.json wire format around re-architecture seam S1: the namespaced
 * {@code structureConfiguration}/{@code extensionConfiguration} accessors exist on {@link ProjectConfiguration}
 * (with the legacy flat booleans readable through the structure-configuration bag), but the on-disk format is
 * unchanged — the booleans serialize as top-level fields and the bags are not serialized at all. The
 * project-structure-configuration-options plan owns changing the persisted form; when it does, it replaces the
 * expectations here deliberately.
 */
public class TestProjectConfigurationSerialization
{
    @Test
    public void testWireFormatUnchangedWithFlagsSet()
    {
        SimpleProjectConfiguration config = SimpleProjectConfiguration.newConfiguration("PROJ-1", ProjectType.MANAGED,
                ProjectStructureVersion.newProjectStructureVersion(13), null, "org.finos.test", "test-project",
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Boolean.TRUE, Boolean.FALSE);

        String json = new String(ProjectStructure.serializeProjectConfiguration(config), StandardCharsets.UTF_8);
        Assert.assertTrue(json, json.contains("\"runDependencyTests\" : true"));
        Assert.assertTrue(json, json.contains("\"produceShadedServiceJar\" : false"));
        Assert.assertFalse(json, json.contains("structureConfiguration"));
        Assert.assertFalse(json, json.contains("extensionConfiguration"));
    }

    @Test
    public void testWireFormatUnchangedWithoutFlags()
    {
        SimpleProjectConfiguration config = SimpleProjectConfiguration.newConfiguration("PROJ-1", ProjectType.MANAGED,
                ProjectStructureVersion.newProjectStructureVersion(13), null, "org.finos.test", "test-project",
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), null, null);

        String json = new String(ProjectStructure.serializeProjectConfiguration(config), StandardCharsets.UTF_8);
        Assert.assertFalse(json, json.contains("runDependencyTests"));
        Assert.assertFalse(json, json.contains("produceShadedServiceJar"));
        Assert.assertFalse(json, json.contains("structureConfiguration"));
        Assert.assertFalse(json, json.contains("extensionConfiguration"));
    }

    @Test
    public void testFlagsReadableThroughStructureConfigurationBag()
    {
        SimpleProjectConfiguration config = SimpleProjectConfiguration.newConfiguration("PROJ-1", ProjectType.MANAGED,
                ProjectStructureVersion.newProjectStructureVersion(13), null, "org.finos.test", "test-project",
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Boolean.TRUE, Boolean.FALSE);

        Map<String, Object> bag = config.getStructureConfiguration();
        Assert.assertEquals(Boolean.TRUE, bag.get("runDependencyTests"));
        Assert.assertEquals(Boolean.FALSE, bag.get("produceShadedServiceJar"));
        Assert.assertEquals(Collections.emptyMap(), config.getExtensionConfiguration());

        // round-trip through the serialized (unchanged) form preserves the bag view
        byte[] bytes = ProjectStructure.serializeProjectConfiguration(config);
        ProjectConfiguration read = ProjectStructure.readProjectConfiguration(ProjectFiles.newByteArrayProjectFile("/project.json", bytes));
        Assert.assertEquals(bag, read.getStructureConfiguration());
        Assert.assertEquals(Boolean.TRUE, read.getRunDependencyTests());
        Assert.assertEquals(Boolean.FALSE, read.getProduceShadedServiceJar());

        // a configuration with no flags has an empty bag
        SimpleProjectConfiguration noFlags = SimpleProjectConfiguration.newConfiguration("PROJ-1", ProjectType.MANAGED,
                ProjectStructureVersion.newProjectStructureVersion(13), null, "org.finos.test", "test-project",
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), null, null);
        Assert.assertEquals(Collections.emptyMap(), noFlags.getStructureConfiguration());
    }

    @Test
    public void testDeprecatedGettersReadThroughTheBag()
    {
        // an implementation that provides only the bag surfaces the flags through the deprecated getters
        ProjectConfiguration bagOnly = new ProjectConfiguration()
        {
            @Override
            public String getProjectId()
            {
                return "PROJ-1";
            }

            @Override
            public ProjectStructureVersion getProjectStructureVersion()
            {
                return ProjectStructureVersion.newProjectStructureVersion(13);
            }

            @Override
            public String getGroupId()
            {
                return "org.finos.test";
            }

            @Override
            public String getArtifactId()
            {
                return "test-project";
            }

            @Override
            public List<ProjectDependency> getProjectDependencies()
            {
                return Collections.emptyList();
            }

            @Override
            public List<MetamodelDependency> getMetamodelDependencies()
            {
                return Collections.emptyList();
            }

            @Override
            public Map<String, Object> getStructureConfiguration()
            {
                return Collections.singletonMap("produceShadedServiceJar", Boolean.TRUE);
            }
        };
        Assert.assertEquals(Boolean.TRUE, bagOnly.getProduceShadedServiceJar());
        Assert.assertNull(bagOnly.getRunDependencyTests());
    }
}
