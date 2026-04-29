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

package org.finos.legend.sdlc.server.domain.api.project;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.list.fixed.ArrayAdapter;
import org.finos.legend.sdlc.domain.model.project.configuration.MetamodelDependency;
import org.finos.legend.sdlc.domain.model.project.configuration.PlatformConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectStructureVersion;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

public class TestProjectConfigurationUpdater
{
    private final JsonMapper jsonMapper = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .disable(StreamWriteFeature.AUTO_CLOSE_TARGET)
            .disable(StreamReadFeature.AUTO_CLOSE_SOURCE)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .build();

    @Test
    public void testVacuousUpdate()
    {
        ProjectConfigurationUpdater vacuous = ProjectConfigurationUpdater.newUpdater();

        assertEquals(newConfig(), vacuous.update(newConfig()));

        TestProjectConfiguration testConfig = newConfig()
                .withProjectId("PROD-93427")
                .withProjectStructureVersion(11, 5)
                .withPlatformConfigurations(newPlatformConfiguration("plat1", "1.2.3"), newPlatformConfiguration("plat2", "3.2.1"))
                .withGroupArtifactIds("org.finos.legend", "test-project")
                .withProjectDependencies(ProjectDependency.newProjectDependency("org.test:some-project", "56.7.7"), ProjectDependency.newProjectDependency("org.test:other-project", "0.0.1"))
                .withMetamodelDependencies(MetamodelDependency.newMetamodelDependency("meta1", 1), MetamodelDependency.newMetamodelDependency("meta2", 3));
        assertEquals(testConfig, vacuous.update(testConfig.copy()));
    }

    @Test
    public void testNonVacuousUpdate()
    {
        ProjectConfigurationUpdater updater = ProjectConfigurationUpdater.newUpdater()
                .withProjectId("NEW-PROJ-1234")
                .withProjectStructureVersion(12)
                .withProjectStructureExtensionVersion(5)
                .withPlatformConfigurations(Lists.fixedSize.with(newPlatformConfiguration("plat1", "1.2.3")))
                .withGroupId("new.group.id")
                .withArtifactId("new-artifact-id")
                .withProjectDependenciesToAdd(ProjectDependency.newProjectDependency("org.test:some-project", "56.7.7"), ProjectDependency.newProjectDependency("org.test:other-project", "0.0.1"))
                .withProjectDependenciesToRemove(ProjectDependency.newProjectDependency("org.test:to-remove", "3.2.1"))
                .withMetamodelDependenciesToAdd(MetamodelDependency.newMetamodelDependency("meta1", 1), MetamodelDependency.newMetamodelDependency("meta2", 3))
                .withMetamodelDependenciesToRemove(MetamodelDependency.newMetamodelDependency("meta-remove", 5));

        ProjectConfiguration emptyConfig = newConfig();
        ProjectConfiguration updatedEmptyConfig = updater.update(emptyConfig);
        ProjectConfiguration expectedUpdatedEmptyConfig = newConfig()
                .withProjectId("NEW-PROJ-1234")
                .withProjectStructureVersion(12, 5)
                .withPlatformConfigurations(newPlatformConfiguration("plat1", "1.2.3"))
                .withGroupArtifactIds("new.group.id", "new-artifact-id")
                .withProjectDependencies(ProjectDependency.newProjectDependency("org.test:other-project", "0.0.1"), ProjectDependency.newProjectDependency("org.test:some-project", "56.7.7"))
                .withMetamodelDependencies(MetamodelDependency.newMetamodelDependency("meta1", 1), MetamodelDependency.newMetamodelDependency("meta2", 3));
        assertNotEquals(emptyConfig, updatedEmptyConfig);
        assertEquals(expectedUpdatedEmptyConfig, updatedEmptyConfig);

        ProjectConfiguration nonEmptyConfig = newConfig()
                .withProjectId("OLD-PROJ-4321")
                .withProjectStructureVersion(11, 1)
                .withPlatformConfigurations(newPlatformConfiguration("plat1", "0.0.1"), newPlatformConfiguration("plat2", "5"))
                .withGroupArtifactIds("old.group.id", "old-artifact-id")
                .withProjectDependencies(ProjectDependency.newProjectDependency("org.test:q-project", "99.0.0.1"), ProjectDependency.newProjectDependency("org.test:to-remove", "3.2.1"))
                .withMetamodelDependencies(MetamodelDependency.newMetamodelDependency("meta0", 5), MetamodelDependency.newMetamodelDependency("meta-remove", 5));
        ProjectConfiguration updatedNonEmptyConfig = updater.update(nonEmptyConfig);
        ProjectConfiguration expectedUpdatedNonEmptyConfig = newConfig()
                .withProjectId("NEW-PROJ-1234")
                .withProjectStructureVersion(12, 5)
                .withPlatformConfigurations(newPlatformConfiguration("plat1", "1.2.3"))
                .withGroupArtifactIds("new.group.id", "new-artifact-id")
                .withProjectDependencies(ProjectDependency.newProjectDependency("org.test:other-project", "0.0.1"), ProjectDependency.newProjectDependency("org.test:q-project", "99.0.0.1"), ProjectDependency.newProjectDependency("org.test:some-project", "56.7.7"))
                .withMetamodelDependencies(MetamodelDependency.newMetamodelDependency("meta0", 5), MetamodelDependency.newMetamodelDependency("meta1", 1), MetamodelDependency.newMetamodelDependency("meta2", 3));
        assertNotEquals(nonEmptyConfig, updatedEmptyConfig);
        assertEquals(expectedUpdatedNonEmptyConfig, updatedNonEmptyConfig);
    }

    @Test
    public void testPlatformConfigurations()
    {
        ProjectConfigurationUpdater toNullUpdater = ProjectConfigurationUpdater.newUpdater().withPlatformConfigurations(null);
        ProjectConfigurationUpdater toEmptyListUpdater = ProjectConfigurationUpdater.newUpdater().withPlatformConfigurations(Lists.fixedSize.empty());
        ProjectConfigurationUpdater toNonEmptyListUpdater = ProjectConfigurationUpdater.newUpdater().withPlatformConfigurations(Lists.fixedSize.with(newPlatformConfiguration("plat", "1.2.3")));

        ProjectConfiguration nullConfig = newConfig();
        ProjectConfiguration emptyListConfig = newConfig().withPlatformConfigurations(Lists.fixedSize.empty());
        ProjectConfiguration nonEmptyListConfig = newConfig().withPlatformConfigurations(Lists.fixedSize.with(newPlatformConfiguration("plat", "1.2.3")));

        assertEquals(nullConfig, toNullUpdater.update(nullConfig));
        assertEquals(nullConfig, toNullUpdater.update(emptyListConfig));
        assertEquals(nullConfig, toNullUpdater.update(nonEmptyListConfig));

        assertEquals(emptyListConfig, toEmptyListUpdater.update(nullConfig));
        assertEquals(emptyListConfig, toEmptyListUpdater.update(emptyListConfig));
        assertEquals(emptyListConfig, toEmptyListUpdater.update(nonEmptyListConfig));

        assertEquals(nonEmptyListConfig, toNonEmptyListUpdater.update(nullConfig));
        assertEquals(nonEmptyListConfig, toNonEmptyListUpdater.update(emptyListConfig));
        assertEquals(nonEmptyListConfig, toNonEmptyListUpdater.update(nonEmptyListConfig));
    }

    private void assertEquals(ProjectConfiguration expected, ProjectConfiguration actual)
    {
        Assert.assertEquals(toJson(expected), toJson(actual));
    }

    private void assertNotEquals(ProjectConfiguration expected, ProjectConfiguration actual)
    {
        Assert.assertNotEquals(toJson(expected), toJson(actual));
    }

    private String toJson(ProjectConfiguration config)
    {
        try
        {
            return this.jsonMapper.writeValueAsString(config);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    private TestProjectConfiguration newConfig()
    {
        return new TestProjectConfiguration();
    }

    private PlatformConfiguration newPlatformConfiguration(String name, String version)
    {
        return new PlatformConfiguration()
        {
            @Override
            public String getName()
            {
                return name;
            }

            @Override
            public String getVersion()
            {
                return version;
            }
        };
    }

    private static class TestProjectConfiguration implements ProjectConfiguration
    {
        private String projectId;
        private ProjectStructureVersion projectStructureVersion;
        private List<PlatformConfiguration> platformConfigurations;
        private String groupId;
        private String artifactId;
        private final MutableList<ProjectDependency> projectDependencies = Lists.mutable.empty();
        private final MutableList<MetamodelDependency> metamodelDependencies = Lists.mutable.empty();

        @Override
        public String getProjectId()
        {
            return this.projectId;
        }

        public TestProjectConfiguration withProjectId(String projectId)
        {
            this.projectId = projectId;
            return this;
        }

        @Override
        public ProjectStructureVersion getProjectStructureVersion()
        {
            return this.projectStructureVersion;
        }

        public TestProjectConfiguration withProjectStructureVersion(ProjectStructureVersion projectStructureVersion)
        {
            this.projectStructureVersion = projectStructureVersion;
            return this;
        }

        public TestProjectConfiguration withProjectStructureVersion(int version, Integer extensionVersion)
        {
            return withProjectStructureVersion(ProjectStructureVersion.newProjectStructureVersion(version, extensionVersion));
        }

        public TestProjectConfiguration withProjectStructureVersion(int version)
        {
            return withProjectStructureVersion(ProjectStructureVersion.newProjectStructureVersion(version));
        }

        @Override
        public List<PlatformConfiguration> getPlatformConfigurations()
        {
            return this.platformConfigurations;
        }

        public TestProjectConfiguration withPlatformConfigurations(List<PlatformConfiguration> platformConfigurations)
        {
            this.platformConfigurations = platformConfigurations;
            return this;
        }

        public TestProjectConfiguration withPlatformConfigurations(PlatformConfiguration... platformConfigurations)
        {
            return withPlatformConfigurations((platformConfigurations == null) ? null : ArrayAdapter.adapt(platformConfigurations));
        }

        @Override
        public String getGroupId()
        {
            return this.groupId;
        }

        public TestProjectConfiguration withGroupId(String groupId)
        {
            this.groupId = groupId;
            return this;
        }

        @Override
        public String getArtifactId()
        {
            return this.artifactId;
        }

        public TestProjectConfiguration withArtifactId(String artifactId)
        {
            this.artifactId = artifactId;
            return this;
        }

        public TestProjectConfiguration withGroupArtifactIds(String groupId, String artifactId)
        {
            return withGroupId(groupId).withArtifactId(artifactId);
        }

        @Override
        public List<ProjectDependency> getProjectDependencies()
        {
            return this.projectDependencies;
        }

        public TestProjectConfiguration withProjectDependencies(Iterable<? extends ProjectDependency> dependencies)
        {
            this.projectDependencies.addAllIterable(dependencies);
            return this;
        }

        public TestProjectConfiguration withProjectDependencies(ProjectDependency... dependencies)
        {
            return withProjectDependencies(ArrayAdapter.adapt(dependencies));
        }

        @Override
        public List<MetamodelDependency> getMetamodelDependencies()
        {
            return this.metamodelDependencies;
        }

        public TestProjectConfiguration withMetamodelDependencies(Iterable<? extends MetamodelDependency> dependencies)
        {
            this.metamodelDependencies.addAllIterable(dependencies);
            return this;
        }

        public TestProjectConfiguration withMetamodelDependencies(MetamodelDependency... dependencies)
        {
            return withMetamodelDependencies(ArrayAdapter.adapt(dependencies));
        }

        public TestProjectConfiguration copy()
        {
            return new TestProjectConfiguration()
                    .withProjectId(this.projectId)
                    .withProjectStructureVersion(this.projectStructureVersion)
                    .withPlatformConfigurations((this.platformConfigurations == null) ? null : Lists.mutable.withAll(this.platformConfigurations))
                    .withGroupArtifactIds(this.groupId, this.artifactId)
                    .withProjectDependencies(this.projectDependencies)
                    .withMetamodelDependencies(this.metamodelDependencies);
        }
    }
}
