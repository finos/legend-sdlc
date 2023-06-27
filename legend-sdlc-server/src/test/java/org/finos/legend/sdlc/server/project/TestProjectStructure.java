// Copyright 2020 Goldman Sachs
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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.set.primitive.ImmutableIntSet;
import org.eclipse.collections.impl.factory.primitive.IntSets;
import org.eclipse.collections.impl.utility.Iterate;
import org.eclipse.collections.impl.utility.ListIterate;
import org.finos.legend.sdlc.domain.model.TestTools;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactGeneration;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactType;
import org.finos.legend.sdlc.domain.model.project.configuration.ArtifactTypeGenerationConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.MetamodelDependency;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.domain.api.project.ProjectConfigurationUpdater;
import org.finos.legend.sdlc.server.domain.api.project.SourceSpecification;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.project.extension.DefaultProjectStructureExtension;
import org.finos.legend.sdlc.server.project.extension.DefaultProjectStructureExtensionProvider;
import org.finos.legend.sdlc.server.project.extension.ProjectStructureExtensionProvider;
import org.finos.legend.sdlc.tools.entity.EntityPaths;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class TestProjectStructure<T extends ProjectStructure>
{
    protected static final String PROJECT_ID = "TestProject";
    protected static final String BUILD_WORKSPACE_ID = "BuildProjectStructure";
    protected static final String GROUP_ID = "org.finos.legend.sdlc.test";
    protected static final String GROUP_ID_2 = "org.finos.legend.sdlc.test.again";
    protected static final String ARTIFACT_ID = "test-model";
    protected static final String ARTIFACT_ID_2 = "another-test-model";
    protected static final String AUTHOR = "author";
    protected static final String COMMITTER = "committer";
    public static final String JAVA_TEST_MODULE = "javatest";
    public static final String AVRO_TEST_MODULE = "avrotest";

    protected final ProjectStructurePlatformExtensions projectStructurePlatformExtensions;
    protected final ImmutableIntSet unpublishedVersion;

    protected InMemoryProjectFileAccessProvider fileAccessProvider;
    protected int projectStructureVersion;
    protected Integer projectStructureExtensionVersion;
    protected ProjectStructureExtensionProvider projectStructureExtensionProvider;

    public TestProjectStructure(ImmutableIntSet unpublishedVersion, ProjectStructurePlatformExtensions projectStructurePlatformExtensions)
    {
        this.unpublishedVersion = unpublishedVersion;
        this.projectStructurePlatformExtensions = projectStructurePlatformExtensions;
    }

    public TestProjectStructure()
    {
        this(IntSets.immutable.with(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), null);
    }

    @Before
    public void setUp()
    {
        this.fileAccessProvider = newProjectFileAccessProvider();
        this.projectStructureVersion = getProjectStructureVersion();
        this.projectStructureExtensionVersion = getProjectStructureExtensionVersion();
        this.projectStructureExtensionProvider = getProjectStructureExtensionProvider();
    }

    @Test
    public void testBuild()
    {
        String message = "Build project structure (version " + this.projectStructureVersion + ((this.projectStructureExtensionVersion == null) ? "" : (" extension version " + this.projectStructureExtensionVersion)) + ")";
        Instant before = Instant.now();
        buildProjectStructure(message);
        Revision revision = this.fileAccessProvider.getProjectRevisionAccessContext(PROJECT_ID).getCurrentRevision();
        Instant after = Instant.now();
        Assert.assertEquals(AUTHOR, revision.getAuthorName());
        Assert.assertEquals(COMMITTER, revision.getCommitterName());
        Assert.assertEquals(message, revision.getMessage());
        Assert.assertTrue(before.compareTo(revision.getAuthoredTimestamp()) <= 0);
        Assert.assertTrue(before.compareTo(revision.getCommittedTimestamp()) <= 0);
        Assert.assertTrue(after.compareTo(revision.getAuthoredTimestamp()) >= 0);
        Assert.assertTrue(after.compareTo(revision.getCommittedTimestamp()) >= 0);

        ProjectConfiguration configuration = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);
        Assert.assertEquals(PROJECT_ID, configuration.getProjectId());
        Assert.assertEquals(GROUP_ID, configuration.getGroupId());
        Assert.assertEquals(ARTIFACT_ID, configuration.getArtifactId());
        Assert.assertEquals(this.projectStructureVersion, configuration.getProjectStructureVersion().getVersion());
        Assert.assertEquals(this.projectStructureExtensionVersion, configuration.getProjectStructureVersion().getExtensionVersion());
        Assert.assertEquals(Collections.emptyList(), configuration.getMetamodelDependencies());
        Assert.assertEquals(Collections.emptyList(), configuration.getProjectDependencies());

        assertStateValid(PROJECT_ID, null, null);
    }

    @Test
    public void testEntities()
    {
        buildProjectStructure();
        ProjectStructure projectStructure = ProjectStructure.getProjectStructure(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);
        List<Entity> testEntities = getTestEntities();
        List<ProjectFileOperation> addEntityOperations = ListIterate.collectWith(testEntities, this::generateAddOperationForEntity, projectStructure);
        String message = "Add entities";

        Instant before = Instant.now();
        Revision revision = this.fileAccessProvider.getProjectFileModificationContext(PROJECT_ID).submit(message, addEntityOperations);
        Instant after = Instant.now();
        Assert.assertEquals(AUTHOR, revision.getAuthorName());
        Assert.assertEquals(COMMITTER, revision.getCommitterName());
        Assert.assertTrue(before.compareTo(revision.getAuthoredTimestamp()) <= 0);
        Assert.assertTrue(before.compareTo(revision.getCommittedTimestamp()) <= 0);
        Assert.assertTrue(after.compareTo(revision.getAuthoredTimestamp()) >= 0);
        Assert.assertTrue(after.compareTo(revision.getCommittedTimestamp()) >= 0);
        Assert.assertEquals(message, revision.getMessage());

        assertStateValid(PROJECT_ID, null, null);

        assertEntitiesEquivalent(testEntities, getActualEntities(PROJECT_ID));

//        System.out.println("==========\nProject Structure version: " + this.projectStructureVersion + "\nProject Structure Extension version: " + this.projectStructureExtensionVersion + "\nProject type: " + projectType + "\n==========\n");
//        this.fileAccessProvider.getProjectFileAccessContext(PROJECT_ID).getFiles().forEach(f -> System.out.println("==========\n" + f.getPath() + "\n==========\n" + f.getContentAsString() + "\n==========\n"));
    }

    @Test
    public void testUpdateGroupAndArtifactIds()
    {
        buildProjectStructure();
        ProjectStructure projectStructure = ProjectStructure.getProjectStructure(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);
        List<Entity> testEntities = getTestEntities();

        List<ProjectFileOperation> addEntityOperations = ListIterate.collectWith(testEntities, this::generateAddOperationForEntity, projectStructure);
        Revision entitiesRevision = this.fileAccessProvider.getProjectFileModificationContext(PROJECT_ID).submit("Add entities", addEntityOperations);

        ProjectConfiguration beforeProjectConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);
        Assert.assertEquals(PROJECT_ID, beforeProjectConfig.getProjectId());
        Assert.assertEquals(GROUP_ID, beforeProjectConfig.getGroupId());
        Assert.assertEquals(ARTIFACT_ID, beforeProjectConfig.getArtifactId());
        Assert.assertEquals(this.projectStructureVersion, beforeProjectConfig.getProjectStructureVersion().getVersion());
        Assert.assertEquals(this.projectStructureExtensionVersion, beforeProjectConfig.getProjectStructureVersion().getExtensionVersion());
        Assert.assertEquals(Collections.emptyList(), beforeProjectConfig.getMetamodelDependencies());
        Assert.assertEquals(Collections.emptyList(), beforeProjectConfig.getProjectDependencies());
        assertStateValid(PROJECT_ID, null, null);

        String workspaceId = "UpdateGroupAndArtifactIds";
        this.fileAccessProvider.createWorkspace(PROJECT_ID, workspaceId);
        ProjectConfiguration beforeWorkspaceConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);
        Assert.assertEquals(PROJECT_ID, beforeWorkspaceConfig.getProjectId());
        Assert.assertEquals(GROUP_ID, beforeWorkspaceConfig.getGroupId());
        Assert.assertEquals(ARTIFACT_ID, beforeWorkspaceConfig.getArtifactId());
        Assert.assertEquals(this.projectStructureVersion, beforeWorkspaceConfig.getProjectStructureVersion().getVersion());
        Assert.assertEquals(this.projectStructureExtensionVersion, beforeWorkspaceConfig.getProjectStructureVersion().getExtensionVersion());
        Assert.assertEquals(Collections.emptyList(), beforeWorkspaceConfig.getMetamodelDependencies());
        Assert.assertEquals(Collections.emptyList(), beforeWorkspaceConfig.getProjectDependencies());
        assertStateValid(PROJECT_ID, workspaceId, null);

        Revision configUpdateRevision = ProjectStructure.newUpdateBuilder(this.fileAccessProvider, PROJECT_ID)
                .withProjectConfigurationUpdater(ProjectConfigurationUpdater.newUpdater().withGroupId(GROUP_ID_2).withArtifactId(ARTIFACT_ID_2))
                .withWorkspace(SourceSpecification.newSourceSpecification(workspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE))
                .withRevisionId(entitiesRevision.getId())
                .withMessage("Update group and artifact ids")
                .withProjectStructureExtensionProvider(this.projectStructureExtensionProvider)
                .withProjectStructurePlatformExtensions(this.projectStructurePlatformExtensions)
                .update();
        ProjectConfiguration afterWorkspaceConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(workspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);
        Assert.assertEquals(PROJECT_ID, afterWorkspaceConfig.getProjectId());
        Assert.assertEquals(GROUP_ID_2, afterWorkspaceConfig.getGroupId());
        Assert.assertEquals(ARTIFACT_ID_2, afterWorkspaceConfig.getArtifactId());
        Assert.assertEquals(this.projectStructureVersion, afterWorkspaceConfig.getProjectStructureVersion().getVersion());
        Assert.assertEquals(this.projectStructureExtensionVersion, afterWorkspaceConfig.getProjectStructureVersion().getExtensionVersion());
        Assert.assertEquals(Collections.emptyList(), afterWorkspaceConfig.getMetamodelDependencies());
        Assert.assertEquals(Collections.emptyList(), afterWorkspaceConfig.getProjectDependencies());
        assertStateValid(PROJECT_ID, workspaceId, null);

        this.fileAccessProvider.commitWorkspace(PROJECT_ID, workspaceId);

        ProjectConfiguration afterProjectConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);
        Assert.assertEquals(PROJECT_ID, afterProjectConfig.getProjectId());
        Assert.assertEquals(GROUP_ID_2, afterProjectConfig.getGroupId());
        Assert.assertEquals(ARTIFACT_ID_2, afterProjectConfig.getArtifactId());
        Assert.assertEquals(this.projectStructureVersion, afterProjectConfig.getProjectStructureVersion().getVersion());
        Assert.assertEquals(this.projectStructureExtensionVersion, afterProjectConfig.getProjectStructureVersion().getExtensionVersion());
        Assert.assertEquals(Collections.emptyList(), afterProjectConfig.getMetamodelDependencies());
        Assert.assertEquals(Collections.emptyList(), afterProjectConfig.getProjectDependencies());
        assertStateValid(PROJECT_ID, null, null);


        ProjectConfiguration projectEntitiesRevisionConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), entitiesRevision.getId(), this.fileAccessProvider);
        Assert.assertEquals(PROJECT_ID, projectEntitiesRevisionConfig.getProjectId());
        Assert.assertEquals(GROUP_ID, projectEntitiesRevisionConfig.getGroupId());
        Assert.assertEquals(ARTIFACT_ID, projectEntitiesRevisionConfig.getArtifactId());
        Assert.assertEquals(this.projectStructureVersion, projectEntitiesRevisionConfig.getProjectStructureVersion().getVersion());
        Assert.assertEquals(this.projectStructureExtensionVersion, projectEntitiesRevisionConfig.getProjectStructureVersion().getExtensionVersion());
        Assert.assertEquals(Collections.emptyList(), projectEntitiesRevisionConfig.getMetamodelDependencies());
        Assert.assertEquals(Collections.emptyList(), projectEntitiesRevisionConfig.getProjectDependencies());

        ProjectConfiguration projectConfigUpdateRevisionConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), configUpdateRevision.getId(), this.fileAccessProvider);
        Assert.assertEquals(PROJECT_ID, projectConfigUpdateRevisionConfig.getProjectId());
        Assert.assertEquals(GROUP_ID_2, projectConfigUpdateRevisionConfig.getGroupId());
        Assert.assertEquals(ARTIFACT_ID_2, projectConfigUpdateRevisionConfig.getArtifactId());
        Assert.assertEquals(this.projectStructureVersion, projectConfigUpdateRevisionConfig.getProjectStructureVersion().getVersion());
        Assert.assertEquals(this.projectStructureExtensionVersion, projectConfigUpdateRevisionConfig.getProjectStructureVersion().getExtensionVersion());
        Assert.assertEquals(Collections.emptyList(), projectConfigUpdateRevisionConfig.getMetamodelDependencies());
        Assert.assertEquals(Collections.emptyList(), projectConfigUpdateRevisionConfig.getProjectDependencies());
        assertEntitiesEquivalent(testEntities, getActualEntities(PROJECT_ID));

        Map<String, String> actualFiles = this.fileAccessProvider.getFileAccessContext(PROJECT_ID, null, null, null, configUpdateRevision.getId()).getFiles().collect(Collectors.toMap(ProjectFileAccessProvider.ProjectFile::getPath, ProjectFileAccessProvider.ProjectFile::getContentAsString));

        List<String> unExpectedFiles = actualFiles.keySet().stream().filter(filePath -> !filePath.equals("/pom.xml") && filePath.endsWith("/pom.xml") && !filePath.startsWith("/" + ARTIFACT_ID_2)).collect(Collectors.toList());
        Assert.assertTrue("non expected files " + Arrays.toString(unExpectedFiles.toArray()), unExpectedFiles.isEmpty());

    }

    @Test
    public void testUpdateProjectDependencies()
    {
        List<ProjectDependency> projectDependencies = Arrays.asList(
                ProjectDependency.parseProjectDependency(GROUP_ID + ":testproject0:0.0.1"),
                ProjectDependency.parseProjectDependency(GROUP_ID + ":testproject1:1.0.0"),
                ProjectDependency.parseProjectDependency(GROUP_ID + ":testproject3:2.0.1"));
        projectDependencies.sort(ProjectStructure.getProjectDependencyComparator());
        for (ProjectDependency projectDependency : projectDependencies)
        {
            String artifactId = projectDependency.getProjectId().substring(GROUP_ID.length() + 1);
            createProjectWithVersions(artifactId, GROUP_ID, artifactId, projectDependency.getVersionId());
        }

        ProjectStructure projectStructure = buildProjectStructure();
        List<Entity> testEntities = getTestEntities();

        List<ProjectFileOperation> addEntityOperations = ListIterate.collectWith(testEntities, this::generateAddOperationForEntity, projectStructure);
        this.fileAccessProvider.getProjectFileModificationContext(PROJECT_ID).submit("Add entities", addEntityOperations);

        ProjectConfiguration beforeProjectConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);
        Assert.assertEquals(PROJECT_ID, beforeProjectConfig.getProjectId());
        Assert.assertEquals(GROUP_ID, beforeProjectConfig.getGroupId());
        Assert.assertEquals(ARTIFACT_ID, beforeProjectConfig.getArtifactId());
        Assert.assertEquals(this.projectStructureVersion, beforeProjectConfig.getProjectStructureVersion().getVersion());
        Assert.assertEquals(this.projectStructureExtensionVersion, beforeProjectConfig.getProjectStructureVersion().getExtensionVersion());
        Assert.assertEquals(Collections.emptyList(), beforeProjectConfig.getMetamodelDependencies());
        Assert.assertEquals(Collections.emptyList(), beforeProjectConfig.getProjectDependencies());
        assertStateValid(PROJECT_ID, null, null);

        String addDependenciesWorkspaceId = "AddDependencies";
        this.fileAccessProvider.createWorkspace(PROJECT_ID, addDependenciesWorkspaceId);
        ProjectConfiguration beforeWorkspaceConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);
        Assert.assertEquals(PROJECT_ID, beforeWorkspaceConfig.getProjectId());
        Assert.assertEquals(GROUP_ID, beforeWorkspaceConfig.getGroupId());
        Assert.assertEquals(ARTIFACT_ID, beforeWorkspaceConfig.getArtifactId());
        Assert.assertEquals(this.projectStructureVersion, beforeWorkspaceConfig.getProjectStructureVersion().getVersion());
        Assert.assertEquals(this.projectStructureExtensionVersion, beforeWorkspaceConfig.getProjectStructureVersion().getExtensionVersion());
        Assert.assertEquals(Collections.emptyList(), beforeWorkspaceConfig.getMetamodelDependencies());
        Assert.assertEquals(Collections.emptyList(), beforeWorkspaceConfig.getProjectDependencies());
        assertStateValid(PROJECT_ID, addDependenciesWorkspaceId, null);

        ProjectStructure.newUpdateBuilder(this.fileAccessProvider, PROJECT_ID)
                .withProjectConfigurationUpdater(ProjectConfigurationUpdater.newUpdater().withProjectDependenciesToAdd(projectDependencies))
                .withWorkspace(SourceSpecification.newSourceSpecification(addDependenciesWorkspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE))
                .withMessage("Add dependencies")
                .withProjectStructureExtensionProvider(this.projectStructureExtensionProvider)
                .withProjectStructurePlatformExtensions(this.projectStructurePlatformExtensions)
                .update();
        ProjectConfiguration afterWorkspaceConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(addDependenciesWorkspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE),null, this.fileAccessProvider);
        Assert.assertEquals(PROJECT_ID, afterWorkspaceConfig.getProjectId());
        Assert.assertEquals(GROUP_ID, afterWorkspaceConfig.getGroupId());
        Assert.assertEquals(ARTIFACT_ID, afterWorkspaceConfig.getArtifactId());
        Assert.assertEquals(this.projectStructureVersion, afterWorkspaceConfig.getProjectStructureVersion().getVersion());
        Assert.assertEquals(this.projectStructureExtensionVersion, afterWorkspaceConfig.getProjectStructureVersion().getExtensionVersion());
        Assert.assertEquals(Collections.emptyList(), afterWorkspaceConfig.getMetamodelDependencies());
        Assert.assertEquals(projectDependencies, afterWorkspaceConfig.getProjectDependencies());
        assertStateValid(PROJECT_ID, addDependenciesWorkspaceId, null);

        this.fileAccessProvider.commitWorkspace(PROJECT_ID, addDependenciesWorkspaceId);

        ProjectConfiguration afterProjectConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);
        Assert.assertEquals(PROJECT_ID, afterProjectConfig.getProjectId());
        Assert.assertEquals(GROUP_ID, afterProjectConfig.getGroupId());
        Assert.assertEquals(ARTIFACT_ID, afterProjectConfig.getArtifactId());
        Assert.assertEquals(this.projectStructureVersion, afterProjectConfig.getProjectStructureVersion().getVersion());
        Assert.assertEquals(this.projectStructureExtensionVersion, afterProjectConfig.getProjectStructureVersion().getExtensionVersion());
        Assert.assertEquals(Collections.emptyList(), afterProjectConfig.getMetamodelDependencies());
        Assert.assertEquals(projectDependencies, afterProjectConfig.getProjectDependencies());
        assertStateValid(PROJECT_ID, null, null);

        String noChangeWorkspaceId = "NoChangeToDependencies";
        this.fileAccessProvider.createWorkspace(PROJECT_ID, noChangeWorkspaceId);
        Revision newRevision = ProjectStructure.newUpdateBuilder(this.fileAccessProvider, PROJECT_ID)
                .withProjectConfigurationUpdater(ProjectConfigurationUpdater.newUpdater().withGroupId("temp.group.id"))
                .withWorkspace(SourceSpecification.newSourceSpecification(noChangeWorkspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE, null))
                .withMessage("No change to dependencies")
                .withProjectStructureExtensionProvider(this.projectStructureExtensionProvider)
                .withProjectStructurePlatformExtensions(this.projectStructurePlatformExtensions)
                .update();
        Assert.assertNotNull(newRevision);
        ProjectConfiguration noChangeWorkspaceConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(noChangeWorkspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);
        Assert.assertEquals(PROJECT_ID, noChangeWorkspaceConfig.getProjectId());
        Assert.assertEquals("temp.group.id", noChangeWorkspaceConfig.getGroupId());
        Assert.assertEquals(ARTIFACT_ID, noChangeWorkspaceConfig.getArtifactId());
        Assert.assertEquals(this.projectStructureVersion, noChangeWorkspaceConfig.getProjectStructureVersion().getVersion());
        Assert.assertEquals(this.projectStructureExtensionVersion, noChangeWorkspaceConfig.getProjectStructureVersion().getExtensionVersion());
        Assert.assertEquals(Collections.emptyList(), noChangeWorkspaceConfig.getMetamodelDependencies());
        Assert.assertEquals(projectDependencies, noChangeWorkspaceConfig.getProjectDependencies());
        assertStateValid(PROJECT_ID, noChangeWorkspaceId, null);
        this.fileAccessProvider.deleteWorkspace(PROJECT_ID, noChangeWorkspaceId);

        for (int i = 0; i < projectDependencies.size(); i++)
        {
            String removeDependencyWorkspaceId = "RemoveDependency" + 0;
            this.fileAccessProvider.createWorkspace(PROJECT_ID, removeDependencyWorkspaceId);
            ProjectConfiguration beforeRemovalConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);
            Assert.assertEquals(PROJECT_ID, beforeRemovalConfig.getProjectId());
            Assert.assertEquals(GROUP_ID, beforeRemovalConfig.getGroupId());
            Assert.assertEquals(ARTIFACT_ID, beforeRemovalConfig.getArtifactId());
            Assert.assertEquals(this.projectStructureVersion, beforeRemovalConfig.getProjectStructureVersion().getVersion());
            Assert.assertEquals(this.projectStructureExtensionVersion, beforeRemovalConfig.getProjectStructureVersion().getExtensionVersion());
            Assert.assertEquals(Collections.emptyList(), beforeRemovalConfig.getMetamodelDependencies());
            Assert.assertEquals(projectDependencies.subList(i, projectDependencies.size()), beforeRemovalConfig.getProjectDependencies());
            assertStateValid(PROJECT_ID, removeDependencyWorkspaceId, null);

            ProjectStructure.newUpdateBuilder(this.fileAccessProvider, PROJECT_ID)
                    .withProjectConfigurationUpdater(ProjectConfigurationUpdater.newUpdater().withProjectDependencyToRemove(projectDependencies.get(i)))
                    .withWorkspace(SourceSpecification.newSourceSpecification(removeDependencyWorkspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE, null))
                    .withMessage("Remove dependencies")
                    .withProjectStructureExtensionProvider(this.projectStructureExtensionProvider)
                    .withProjectStructurePlatformExtensions(this.projectStructurePlatformExtensions)
                    .update();
            ProjectConfiguration afterRemovalWorkspaceConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(removeDependencyWorkspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);
            Assert.assertEquals(PROJECT_ID, afterRemovalWorkspaceConfig.getProjectId());
            Assert.assertEquals(GROUP_ID, afterRemovalWorkspaceConfig.getGroupId());
            Assert.assertEquals(ARTIFACT_ID, afterRemovalWorkspaceConfig.getArtifactId());
            Assert.assertEquals(this.projectStructureVersion, afterRemovalWorkspaceConfig.getProjectStructureVersion().getVersion());
            Assert.assertEquals(this.projectStructureExtensionVersion, afterRemovalWorkspaceConfig.getProjectStructureVersion().getExtensionVersion());
            Assert.assertEquals(Collections.emptyList(), afterRemovalWorkspaceConfig.getMetamodelDependencies());
            Assert.assertEquals(projectDependencies.subList(i + 1, projectDependencies.size()), afterRemovalWorkspaceConfig.getProjectDependencies());
            assertStateValid(PROJECT_ID, removeDependencyWorkspaceId, null);

            this.fileAccessProvider.commitWorkspace(PROJECT_ID, removeDependencyWorkspaceId);

            ProjectConfiguration afterRemovalProjectConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);
            Assert.assertEquals(PROJECT_ID, afterRemovalProjectConfig.getProjectId());
            Assert.assertEquals(GROUP_ID, afterRemovalProjectConfig.getGroupId());
            Assert.assertEquals(ARTIFACT_ID, afterRemovalProjectConfig.getArtifactId());
            Assert.assertEquals(this.projectStructureVersion, afterRemovalProjectConfig.getProjectStructureVersion().getVersion());
            Assert.assertEquals(this.projectStructureExtensionVersion, afterRemovalProjectConfig.getProjectStructureVersion().getExtensionVersion());
            Assert.assertEquals(Collections.emptyList(), afterRemovalProjectConfig.getMetamodelDependencies());
            Assert.assertEquals(projectDependencies.subList(i + 1, projectDependencies.size()), afterRemovalProjectConfig.getProjectDependencies());
            assertStateValid(PROJECT_ID, null, null);
        }

        ProjectConfiguration projectConfigUpdateRevisionConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);
        Assert.assertEquals(PROJECT_ID, projectConfigUpdateRevisionConfig.getProjectId());
        Assert.assertEquals(GROUP_ID, projectConfigUpdateRevisionConfig.getGroupId());
        Assert.assertEquals(ARTIFACT_ID, projectConfigUpdateRevisionConfig.getArtifactId());
        Assert.assertEquals(this.projectStructureVersion, projectConfigUpdateRevisionConfig.getProjectStructureVersion().getVersion());
        Assert.assertEquals(this.projectStructureExtensionVersion, projectConfigUpdateRevisionConfig.getProjectStructureVersion().getExtensionVersion());
        Assert.assertEquals(Collections.emptyList(), projectConfigUpdateRevisionConfig.getMetamodelDependencies());
        Assert.assertEquals(Collections.emptyList(), projectConfigUpdateRevisionConfig.getProjectDependencies());

        assertEntitiesEquivalent(testEntities, getActualEntities(PROJECT_ID));
    }

    @Test
    public void testUpdateOldProjectDependencies()
    {
        ProjectDependency oldProjectDependency = ProjectDependency.parseProjectDependency("TestProject3:2.0.1");
        ProjectDependency oldProjectDependencyToRemove = ProjectDependency.parseProjectDependency("TestProject0:0.0.1");
        List<ProjectDependency> oldProjectDependencyList = Arrays.asList(oldProjectDependency, oldProjectDependencyToRemove);
        ProjectDependency updatedProjectDependency = ProjectDependency.parseProjectDependency(GROUP_ID + ":testproject3:2.0.1");

        ProjectStructure projectStructure = buildProjectStructure();
        createProjectWithVersions(oldProjectDependency.getProjectId(), GROUP_ID, "testproject3", oldProjectDependency.getVersionId());

        ProjectConfiguration beforeProjectConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);
        Assert.assertEquals(PROJECT_ID, beforeProjectConfig.getProjectId());
        Assert.assertEquals(GROUP_ID, beforeProjectConfig.getGroupId());
        Assert.assertEquals(ARTIFACT_ID, beforeProjectConfig.getArtifactId());
        Assert.assertEquals(this.projectStructureVersion, beforeProjectConfig.getProjectStructureVersion().getVersion());
        Assert.assertEquals(this.projectStructureExtensionVersion, beforeProjectConfig.getProjectStructureVersion().getExtensionVersion());
        Assert.assertEquals(Collections.emptyList(), beforeProjectConfig.getMetamodelDependencies());
        Assert.assertEquals(Collections.emptyList(), beforeProjectConfig.getProjectDependencies());
        assertStateValid(PROJECT_ID, null, null);

        SimpleProjectConfiguration newConfig = new SimpleProjectConfiguration(beforeProjectConfig);
        newConfig.setProjectDependencies(oldProjectDependencyList);
        String serializedConfig = serializeConfig(newConfig);
        List<ProjectFileOperation> operations = Lists.mutable.empty();
        operations.add(ProjectFileOperation.modifyFile("/project.json", serializedConfig));
        this.fileAccessProvider.getProjectFileModificationContext(PROJECT_ID).submit("set dependencies", operations);

        ProjectConfiguration afterUpdateProjectConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);;
        Assert.assertEquals(PROJECT_ID, afterUpdateProjectConfig.getProjectId());
        Assert.assertEquals(GROUP_ID, afterUpdateProjectConfig.getGroupId());
        Assert.assertEquals(ARTIFACT_ID, afterUpdateProjectConfig.getArtifactId());
        Assert.assertEquals(this.projectStructureVersion, afterUpdateProjectConfig.getProjectStructureVersion().getVersion());
        Assert.assertEquals(this.projectStructureExtensionVersion, afterUpdateProjectConfig.getProjectStructureVersion().getExtensionVersion());
        Assert.assertEquals(Collections.emptyList(), afterUpdateProjectConfig.getMetamodelDependencies());
        Assert.assertEquals(oldProjectDependencyList, afterUpdateProjectConfig.getProjectDependencies());

        String updateOldDependenciesId = "UpdateOldDependencies";
        this.fileAccessProvider.createWorkspace(PROJECT_ID, updateOldDependenciesId);
        ProjectStructure.newUpdateBuilder(this.fileAccessProvider, PROJECT_ID)
                .withProjectConfigurationUpdater(ProjectConfigurationUpdater.newUpdater().withProjectDependencyToRemove(oldProjectDependencyToRemove))
                .withWorkspace(SourceSpecification.newSourceSpecification(updateOldDependenciesId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE))
                .withMessage("Update Old Dependencies")
                .withProjectStructureExtensionProvider(this.projectStructureExtensionProvider)
                .withProjectStructurePlatformExtensions(this.projectStructurePlatformExtensions)
                .update();
        ProjectConfiguration afterUpdateWorkspaceConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(updateOldDependenciesId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);
        Assert.assertEquals(PROJECT_ID, afterUpdateWorkspaceConfig.getProjectId());
        Assert.assertEquals(GROUP_ID, afterUpdateWorkspaceConfig.getGroupId());
        Assert.assertEquals(ARTIFACT_ID, afterUpdateWorkspaceConfig.getArtifactId());
        Assert.assertEquals(this.projectStructureVersion, afterUpdateWorkspaceConfig.getProjectStructureVersion().getVersion());
        Assert.assertEquals(this.projectStructureExtensionVersion, afterUpdateWorkspaceConfig.getProjectStructureVersion().getExtensionVersion());
        Assert.assertEquals(Collections.emptyList(), afterUpdateWorkspaceConfig.getMetamodelDependencies());

        //asserting after deleting one of the old form dependency and updating one of the dependency
        Assert.assertEquals(Collections.singletonList(updatedProjectDependency), afterUpdateWorkspaceConfig.getProjectDependencies());
        assertStateValid(PROJECT_ID, updateOldDependenciesId, null);
    }

    private static String serializeConfig(SimpleProjectConfiguration newConfig)
    {
        try
        {
            return JsonMapper.builder()
                    .enable(SerializationFeature.INDENT_OUTPUT)
                    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                    .disable(StreamWriteFeature.AUTO_CLOSE_TARGET)
                    .disable(StreamReadFeature.AUTO_CLOSE_SOURCE)
                    .serializationInclusion(JsonInclude.Include.NON_NULL)
                    .build().writeValueAsString(newConfig);
        }
        catch (Exception e)
        {
            StringBuilder message = new StringBuilder("Error creating project configuration file");
            String errorMessage = e.getMessage();
            if (errorMessage != null)
            {
                message.append(": ").append(errorMessage);
            }
            throw new RuntimeException(message.toString(), e);
        }
    }

    @Test
    public void testUpdateMetamodelDependencies()
    {
        List<MetamodelDependency> metamodelDependencies = Arrays.asList(
                MetamodelDependency.parseMetamodelDependency("pure:1"),
                MetamodelDependency.parseMetamodelDependency("tds:1"),
                MetamodelDependency.parseMetamodelDependency("service:1"));
        metamodelDependencies.sort(ProjectStructure.getMetamodelDependencyComparator());

        ProjectStructure projectStructure = buildProjectStructure();
        List<Entity> testEntities = getTestEntities();

        List<ProjectFileOperation> addEntityOperations = ListIterate.collectWith(testEntities, this::generateAddOperationForEntity, projectStructure);
        this.fileAccessProvider.getProjectFileModificationContext(PROJECT_ID).submit("Add entities", addEntityOperations);

        ProjectConfiguration beforeProjectConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);;
        Assert.assertEquals(PROJECT_ID, beforeProjectConfig.getProjectId());
        Assert.assertEquals(GROUP_ID, beforeProjectConfig.getGroupId());
        Assert.assertEquals(ARTIFACT_ID, beforeProjectConfig.getArtifactId());
        Assert.assertEquals(this.projectStructureVersion, beforeProjectConfig.getProjectStructureVersion().getVersion());
        Assert.assertEquals(this.projectStructureExtensionVersion, beforeProjectConfig.getProjectStructureVersion().getExtensionVersion());
        Assert.assertEquals(Collections.emptyList(), beforeProjectConfig.getMetamodelDependencies());
        assertStateValid(PROJECT_ID, null, null);

        String addDependenciesWorkspaceId = "AddDependencies";
        this.fileAccessProvider.createWorkspace(PROJECT_ID, addDependenciesWorkspaceId);
        ProjectConfiguration beforeWorkspaceConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);;
        Assert.assertEquals(PROJECT_ID, beforeWorkspaceConfig.getProjectId());
        Assert.assertEquals(GROUP_ID, beforeWorkspaceConfig.getGroupId());
        Assert.assertEquals(ARTIFACT_ID, beforeWorkspaceConfig.getArtifactId());
        Assert.assertEquals(this.projectStructureVersion, beforeWorkspaceConfig.getProjectStructureVersion().getVersion());
        Assert.assertEquals(this.projectStructureExtensionVersion, beforeWorkspaceConfig.getProjectStructureVersion().getExtensionVersion());
        Assert.assertEquals(Collections.emptyList(), beforeWorkspaceConfig.getMetamodelDependencies());
        Assert.assertEquals(Collections.emptyList(), beforeProjectConfig.getProjectDependencies());
        assertStateValid(PROJECT_ID, addDependenciesWorkspaceId, null);

        ProjectStructure.newUpdateBuilder(this.fileAccessProvider, PROJECT_ID)
                .withProjectConfigurationUpdater(ProjectConfigurationUpdater.newUpdater().withMetamodelDependenciesToAdd(metamodelDependencies))
                .withWorkspace(SourceSpecification.newSourceSpecification(addDependenciesWorkspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE, null))
                .withMessage("Add dependencies")
                .withProjectStructureExtensionProvider(this.projectStructureExtensionProvider)
                .withProjectStructurePlatformExtensions(this.projectStructurePlatformExtensions)
                .update();
        ProjectConfiguration afterWorkspaceConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(addDependenciesWorkspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);
        Assert.assertEquals(PROJECT_ID, afterWorkspaceConfig.getProjectId());
        Assert.assertEquals(GROUP_ID, afterWorkspaceConfig.getGroupId());
        Assert.assertEquals(ARTIFACT_ID, afterWorkspaceConfig.getArtifactId());
        Assert.assertEquals(this.projectStructureVersion, afterWorkspaceConfig.getProjectStructureVersion().getVersion());
        Assert.assertEquals(this.projectStructureExtensionVersion, afterWorkspaceConfig.getProjectStructureVersion().getExtensionVersion());
        Assert.assertEquals(metamodelDependencies, afterWorkspaceConfig.getMetamodelDependencies());
        Assert.assertEquals(Collections.emptyList(), afterWorkspaceConfig.getProjectDependencies());
        assertStateValid(PROJECT_ID, addDependenciesWorkspaceId, null);

        this.fileAccessProvider.commitWorkspace(PROJECT_ID, addDependenciesWorkspaceId);

        ProjectConfiguration afterProjectConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);;
        Assert.assertEquals(PROJECT_ID, afterProjectConfig.getProjectId());
        Assert.assertEquals(GROUP_ID, afterProjectConfig.getGroupId());
        Assert.assertEquals(ARTIFACT_ID, afterProjectConfig.getArtifactId());
        Assert.assertEquals(this.projectStructureVersion, afterProjectConfig.getProjectStructureVersion().getVersion());
        Assert.assertEquals(this.projectStructureExtensionVersion, afterProjectConfig.getProjectStructureVersion().getExtensionVersion());
        Assert.assertEquals(metamodelDependencies, afterProjectConfig.getMetamodelDependencies());
        Assert.assertEquals(Collections.emptyList(), afterProjectConfig.getProjectDependencies());
        assertStateValid(PROJECT_ID, null, null);

        String noChangeWorkspaceId = "NoChangeToDependencies";
        this.fileAccessProvider.createWorkspace(PROJECT_ID, noChangeWorkspaceId);
        Revision newRevision = ProjectStructure.newUpdateBuilder(this.fileAccessProvider, PROJECT_ID)
                .withProjectConfigurationUpdater(ProjectConfigurationUpdater.newUpdater().withGroupId("temp.group.id"))
                .withWorkspace(SourceSpecification.newSourceSpecification(noChangeWorkspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE))
                .withMessage("No change to dependencies")
                .withProjectStructureExtensionProvider(this.projectStructureExtensionProvider)
                .withProjectStructurePlatformExtensions(this.projectStructurePlatformExtensions)
                .update();
        Assert.assertNotNull(newRevision);
        ProjectConfiguration noChangeWorkspaceConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(noChangeWorkspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);
        Assert.assertEquals(PROJECT_ID, noChangeWorkspaceConfig.getProjectId());
        Assert.assertEquals("temp.group.id", noChangeWorkspaceConfig.getGroupId());
        Assert.assertEquals(ARTIFACT_ID, noChangeWorkspaceConfig.getArtifactId());
        Assert.assertEquals(this.projectStructureVersion, noChangeWorkspaceConfig.getProjectStructureVersion().getVersion());
        Assert.assertEquals(this.projectStructureExtensionVersion, noChangeWorkspaceConfig.getProjectStructureVersion().getExtensionVersion());
        Assert.assertEquals(metamodelDependencies, noChangeWorkspaceConfig.getMetamodelDependencies());
        Assert.assertEquals(Collections.emptyList(), noChangeWorkspaceConfig.getProjectDependencies());
        assertStateValid(PROJECT_ID, noChangeWorkspaceId, null);
        this.fileAccessProvider.deleteWorkspace(PROJECT_ID, noChangeWorkspaceId);

        for (int i = 0; i < metamodelDependencies.size(); i++)
        {
            String removeDependencyWorkspaceId = "RemoveDependency" + 0;
            this.fileAccessProvider.createWorkspace(PROJECT_ID, removeDependencyWorkspaceId);
            ProjectConfiguration beforeRemovalConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);;
            Assert.assertEquals(PROJECT_ID, beforeRemovalConfig.getProjectId());
            Assert.assertEquals(GROUP_ID, beforeRemovalConfig.getGroupId());
            Assert.assertEquals(ARTIFACT_ID, beforeRemovalConfig.getArtifactId());
            Assert.assertEquals(this.projectStructureVersion, beforeRemovalConfig.getProjectStructureVersion().getVersion());
            Assert.assertEquals(this.projectStructureExtensionVersion, beforeRemovalConfig.getProjectStructureVersion().getExtensionVersion());
            Assert.assertEquals(metamodelDependencies.subList(i, metamodelDependencies.size()), beforeRemovalConfig.getMetamodelDependencies());
            Assert.assertEquals(Collections.emptyList(), beforeRemovalConfig.getProjectDependencies());
            assertStateValid(PROJECT_ID, removeDependencyWorkspaceId, null);

            ProjectStructure.newUpdateBuilder(this.fileAccessProvider, PROJECT_ID)
                    .withProjectConfigurationUpdater(ProjectConfigurationUpdater.newUpdater().withMetamodelDependencyToRemove(metamodelDependencies.get(i)))
                    .withWorkspace(SourceSpecification.newSourceSpecification(removeDependencyWorkspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE))
                    .withMessage("Remove dependencies")
                    .withProjectStructureExtensionProvider(this.projectStructureExtensionProvider)
                    .withProjectStructurePlatformExtensions(this.projectStructurePlatformExtensions)
                    .update();
            ProjectConfiguration afterRemovalWorkspaceConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(removeDependencyWorkspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);
            Assert.assertEquals(PROJECT_ID, afterRemovalWorkspaceConfig.getProjectId());
            Assert.assertEquals(GROUP_ID, afterRemovalWorkspaceConfig.getGroupId());
            Assert.assertEquals(ARTIFACT_ID, afterRemovalWorkspaceConfig.getArtifactId());
            Assert.assertEquals(this.projectStructureVersion, afterRemovalWorkspaceConfig.getProjectStructureVersion().getVersion());
            Assert.assertEquals(this.projectStructureExtensionVersion, afterRemovalWorkspaceConfig.getProjectStructureVersion().getExtensionVersion());
            Assert.assertEquals(metamodelDependencies.subList(i + 1, metamodelDependencies.size()), afterRemovalWorkspaceConfig.getMetamodelDependencies());
            Assert.assertEquals(Collections.emptyList(), afterRemovalWorkspaceConfig.getProjectDependencies());
            assertStateValid(PROJECT_ID, removeDependencyWorkspaceId, null);

            this.fileAccessProvider.commitWorkspace(PROJECT_ID, removeDependencyWorkspaceId);

            ProjectConfiguration afterRemovalProjectConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);;
            Assert.assertEquals(PROJECT_ID, afterRemovalProjectConfig.getProjectId());
            Assert.assertEquals(GROUP_ID, afterRemovalProjectConfig.getGroupId());
            Assert.assertEquals(ARTIFACT_ID, afterRemovalProjectConfig.getArtifactId());
            Assert.assertEquals(this.projectStructureVersion, afterRemovalProjectConfig.getProjectStructureVersion().getVersion());
            Assert.assertEquals(this.projectStructureExtensionVersion, afterRemovalProjectConfig.getProjectStructureVersion().getExtensionVersion());
            Assert.assertEquals(metamodelDependencies.subList(i + 1, metamodelDependencies.size()), afterRemovalProjectConfig.getMetamodelDependencies());
            Assert.assertEquals(Collections.emptyList(), afterRemovalProjectConfig.getProjectDependencies());
            assertStateValid(PROJECT_ID, null, null);
        }

        ProjectConfiguration projectConfigUpdateRevisionConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);;
        Assert.assertEquals(PROJECT_ID, projectConfigUpdateRevisionConfig.getProjectId());
        Assert.assertEquals(GROUP_ID, projectConfigUpdateRevisionConfig.getGroupId());
        Assert.assertEquals(ARTIFACT_ID, projectConfigUpdateRevisionConfig.getArtifactId());
        Assert.assertEquals(this.projectStructureVersion, projectConfigUpdateRevisionConfig.getProjectStructureVersion().getVersion());
        Assert.assertEquals(this.projectStructureExtensionVersion, projectConfigUpdateRevisionConfig.getProjectStructureVersion().getExtensionVersion());
        Assert.assertEquals(Collections.emptyList(), projectConfigUpdateRevisionConfig.getMetamodelDependencies());
        Assert.assertEquals(Collections.emptyList(), projectConfigUpdateRevisionConfig.getProjectDependencies());

        assertEntitiesEquivalent(testEntities, getActualEntities(PROJECT_ID));
    }

    @Test
    public void testUpgradeProjectStructureVersion()
    {
        List<Entity> testEntities = getTestEntities();
        for (int i = 0; i < this.projectStructureVersion; i++)
        {
            if (this.unpublishedVersion.contains(i))
            {
                continue;
            }

            if (i > 0)
            {
                this.fileAccessProvider = newProjectFileAccessProvider();
            }

            ProjectStructure otherProjectStructure = buildProjectStructure(i, null, null, null);

            List<ProjectFileOperation> addEntityOperations = ListIterate.collectWith(testEntities, this::generateAddOperationForEntity, otherProjectStructure);
            Revision revision = this.fileAccessProvider.getProjectFileModificationContext(PROJECT_ID).submit("Add entities", addEntityOperations);

            ProjectConfiguration beforeConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);;
            Assert.assertEquals(PROJECT_ID, beforeConfig.getProjectId());
            Assert.assertEquals(GROUP_ID, beforeConfig.getGroupId());
            Assert.assertEquals(ARTIFACT_ID, beforeConfig.getArtifactId());
            Assert.assertEquals(i, beforeConfig.getProjectStructureVersion().getVersion());
            Assert.assertEquals(Collections.emptyList(), beforeConfig.getMetamodelDependencies());
            Assert.assertEquals(Collections.emptyList(), beforeConfig.getProjectDependencies());

            String workspaceId = "ConvertProjectToVersion" + i;
            this.fileAccessProvider.createWorkspace(PROJECT_ID, workspaceId);
            ProjectStructure.newUpdateBuilder(this.fileAccessProvider, PROJECT_ID)
                    .withProjectConfigurationUpdater(ProjectConfigurationUpdater.newUpdater().withProjectStructureVersion(this.projectStructureVersion).withProjectStructureExtensionVersion(this.projectStructureExtensionVersion))
                    .withWorkspace(SourceSpecification.newSourceSpecification(workspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE))
                    .withRevisionId(revision.getId())
                    .withMessage("Update project structure version")
                    .withProjectStructureExtensionProvider(this.projectStructureExtensionProvider)
                    .withProjectStructurePlatformExtensions(this.projectStructurePlatformExtensions)
                    .update();
            assertStateValid(PROJECT_ID, workspaceId, null);
            this.fileAccessProvider.commitWorkspace(PROJECT_ID, workspaceId);
            assertStateValid(PROJECT_ID, null, null);

            ProjectConfiguration afterConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);;
            Assert.assertEquals(PROJECT_ID, afterConfig.getProjectId());
            Assert.assertEquals(GROUP_ID, afterConfig.getGroupId());
            Assert.assertEquals(ARTIFACT_ID, afterConfig.getArtifactId());
            Assert.assertEquals(this.projectStructureVersion, afterConfig.getProjectStructureVersion().getVersion());
            Assert.assertEquals(this.projectStructureExtensionVersion, afterConfig.getProjectStructureVersion().getExtensionVersion());
            Assert.assertEquals(Collections.emptyList(), afterConfig.getMetamodelDependencies());
            Assert.assertEquals(Collections.emptyList(), afterConfig.getProjectDependencies());

            assertEntitiesEquivalent("convert version " + i + " to " + this.projectStructureVersion, testEntities, getActualEntities(PROJECT_ID));
        }
    }

    @Test
    public void testConvertManagedToEmbeddedAndBack()
    {
        this.projectStructureExtensionVersion = 1;
        this.projectStructureExtensionProvider = DefaultProjectStructureExtensionProvider.fromExtensions(DefaultProjectStructureExtension.newProjectStructureExtension(this.projectStructureVersion, this.projectStructureExtensionVersion, Collections.singletonMap("/PANGRAM.TXT", "THE QUICK BROWN FOX JUMPED OVER THE LAZY DOG")));
        List<Entity> testEntities = getTestEntities();
        ProjectStructure otherProjectStructure = buildProjectStructure(this.projectStructureVersion, this.projectStructureExtensionVersion, null, null);
        List<ProjectFileOperation> addEntityOperations = ListIterate.collectWith(testEntities, this::generateAddOperationForEntity, otherProjectStructure);
        Revision revision = this.fileAccessProvider.getProjectFileModificationContext(PROJECT_ID).submit("Add entities", addEntityOperations);
        ProjectConfiguration initialConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);
        Assert.assertEquals(PROJECT_ID, initialConfig.getProjectId());
        Assert.assertEquals(GROUP_ID, initialConfig.getGroupId());
        Assert.assertEquals(ARTIFACT_ID, initialConfig.getArtifactId());
        Assert.assertEquals(this.projectStructureVersion, initialConfig.getProjectStructureVersion().getVersion());
        Assert.assertEquals(this.projectStructureExtensionVersion, initialConfig.getProjectStructureVersion().getExtensionVersion());
        Assert.assertEquals(ProjectType.MANAGED, initialConfig.getProjectType());
        Assert.assertEquals(Collections.emptyList(), initialConfig.getMetamodelDependencies());
        Assert.assertEquals(Collections.emptyList(), initialConfig.getProjectDependencies());
        String workspaceId = "ConvertManagedToEmbedded";
        this.fileAccessProvider.createWorkspace(PROJECT_ID, workspaceId);
        Revision nextRevision = ProjectStructure.newUpdateBuilder(this.fileAccessProvider, PROJECT_ID)
                .withProjectConfigurationUpdater(ProjectConfigurationUpdater.newUpdater().withProjectType(ProjectType.EMBEDDED))
                .withWorkspace(SourceSpecification.newSourceSpecification(workspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE))
                .withRevisionId(revision.getId())
                .withMessage("Update project structure version")
                .withProjectStructureExtensionProvider(this.projectStructureExtensionProvider)
                .withProjectStructurePlatformExtensions(this.projectStructurePlatformExtensions)
                .update();
        assertStateValid(PROJECT_ID, workspaceId, null);
        this.fileAccessProvider.commitWorkspace(PROJECT_ID, workspaceId);
        assertStateValid(PROJECT_ID, null, null);
        ProjectConfiguration embeddedConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);
        Assert.assertEquals(PROJECT_ID, embeddedConfig.getProjectId());
        Assert.assertEquals(GROUP_ID, embeddedConfig.getGroupId());
        Assert.assertEquals(ARTIFACT_ID, embeddedConfig.getArtifactId());
        Assert.assertEquals(this.projectStructureVersion, embeddedConfig.getProjectStructureVersion().getVersion());
        Assert.assertEquals(null, embeddedConfig.getProjectStructureVersion().getExtensionVersion());
        Assert.assertEquals(ProjectType.EMBEDDED, embeddedConfig.getProjectType());
        Assert.assertEquals(Collections.emptyList(), embeddedConfig.getMetamodelDependencies());
        Assert.assertEquals(Collections.emptyList(), embeddedConfig.getProjectDependencies());
        assertEntitiesEquivalent("convert managed to embedded " + this.projectStructureVersion, testEntities, getActualEntities(PROJECT_ID));
        this.fileAccessProvider.createWorkspace(PROJECT_ID, workspaceId);
        ProjectStructure.newUpdateBuilder(this.fileAccessProvider, PROJECT_ID)
                .withProjectConfigurationUpdater(ProjectConfigurationUpdater.newUpdater().withProjectType(ProjectType.MANAGED))
                .withWorkspace(SourceSpecification.newSourceSpecification(workspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE))
                .withRevisionId(nextRevision.getId())
                .withMessage("Update project structure version")
                .withProjectStructureExtensionProvider(this.projectStructureExtensionProvider)
                .withProjectStructurePlatformExtensions(this.projectStructurePlatformExtensions)
                .update();
        assertStateValid(PROJECT_ID, workspaceId, null);
        this.fileAccessProvider.commitWorkspace(PROJECT_ID, workspaceId);
        assertStateValid(PROJECT_ID, null, null);
        ProjectConfiguration managedConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);
        Assert.assertEquals(PROJECT_ID, managedConfig.getProjectId());
        Assert.assertEquals(GROUP_ID, managedConfig.getGroupId());
        Assert.assertEquals(ARTIFACT_ID, managedConfig.getArtifactId());
        Assert.assertEquals(this.projectStructureVersion, managedConfig.getProjectStructureVersion().getVersion());
        Assert.assertEquals(this.projectStructureExtensionVersion, managedConfig.getProjectStructureVersion().getExtensionVersion());
        Assert.assertEquals(ProjectType.MANAGED, managedConfig.getProjectType());
        Assert.assertEquals(Collections.emptyList(), managedConfig.getMetamodelDependencies());
        Assert.assertEquals(Collections.emptyList(), managedConfig.getProjectDependencies());
        assertEntitiesEquivalent("convert embedded to managed " + this.projectStructureVersion, testEntities, getActualEntities(PROJECT_ID));
    }

    @Test
    public void testUpdateFullProjectConfig()
    {
        // TODO add some dependencies
        List<Entity> testEntities = getTestEntities();
        for (int i = 0; i < this.projectStructureVersion; i++)
        {
            if (unpublishedVersion.contains(i))
            {
                continue;
            }

            if (i > 0)
            {
                this.fileAccessProvider = newProjectFileAccessProvider();
            }

            ProjectStructure otherProjectStructure = buildProjectStructure(i, null, null, null);

            List<ProjectFileOperation> addEntityOperations = ListIterate.collectWith(testEntities, this::generateAddOperationForEntity, otherProjectStructure);
            Revision revision = this.fileAccessProvider.getProjectFileModificationContext(PROJECT_ID).submit("Add entities", addEntityOperations);

            ProjectConfiguration beforeConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);;
            Assert.assertEquals(PROJECT_ID, beforeConfig.getProjectId());
            Assert.assertEquals(GROUP_ID, beforeConfig.getGroupId());
            Assert.assertEquals(ARTIFACT_ID, beforeConfig.getArtifactId());
            Assert.assertEquals(i, beforeConfig.getProjectStructureVersion().getVersion());

            String workspaceId = "ConvertProjectToVersion" + i;
            this.fileAccessProvider.createWorkspace(PROJECT_ID, workspaceId);
            ProjectStructure.newUpdateBuilder(this.fileAccessProvider, PROJECT_ID)
                    .withProjectConfigurationUpdater(ProjectConfigurationUpdater.newUpdater()
                            .withProjectStructureVersion(this.projectStructureVersion)
                            .withProjectStructureExtensionVersion(this.projectStructureExtensionVersion)
                            .withGroupId(GROUP_ID_2)
                            .withArtifactId(ARTIFACT_ID_2))
                    .withWorkspace(SourceSpecification.newSourceSpecification(workspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE))
                    .withRevisionId(revision.getId())
                    .withMessage("Update project configuration")
                    .withProjectStructureExtensionProvider(this.projectStructureExtensionProvider)
                    .withProjectStructurePlatformExtensions(this.projectStructurePlatformExtensions)
                    .update();
            assertStateValid(PROJECT_ID, workspaceId, null);
            this.fileAccessProvider.commitWorkspace(PROJECT_ID, workspaceId);
            assertStateValid(PROJECT_ID, null, null);

            ProjectConfiguration afterConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);;
            Assert.assertEquals(PROJECT_ID, afterConfig.getProjectId());
            Assert.assertEquals(GROUP_ID_2, afterConfig.getGroupId());
            Assert.assertEquals(ARTIFACT_ID_2, afterConfig.getArtifactId());
            Assert.assertEquals(this.projectStructureVersion, afterConfig.getProjectStructureVersion().getVersion());
            Assert.assertEquals(this.projectStructureExtensionVersion, afterConfig.getProjectStructureVersion().getExtensionVersion());

            assertEntitiesEquivalent("convert version " + i + " to " + this.projectStructureVersion, testEntities, getActualEntities(PROJECT_ID));
        }
    }

    @Test
    public void testVacuousProjectConfigUpdate()
    {
        buildProjectStructure();
        Revision revision = this.fileAccessProvider.getProjectRevisionAccessContext(PROJECT_ID).getCurrentRevision();

        String workspaceId = "UpdateWorkspace";
        this.fileAccessProvider.createWorkspace(PROJECT_ID, workspaceId);
        Assert.assertEquals(revision, this.fileAccessProvider.getWorkspaceRevisionAccessContext(PROJECT_ID, workspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE).getCurrentRevision());
        assertStateValid(PROJECT_ID, workspaceId, null);

        Revision revision2 = ProjectStructure.newUpdateBuilder(this.fileAccessProvider, PROJECT_ID)
                .withWorkspace(SourceSpecification.newSourceSpecification(workspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE))
                .withMessage("Vacuous update")
                .withProjectStructureExtensionProvider(this.projectStructureExtensionProvider)
                .withProjectStructurePlatformExtensions(this.projectStructurePlatformExtensions)
                .update();
        Assert.assertNull(revision2);
        Assert.assertEquals(revision, this.fileAccessProvider.getWorkspaceRevisionAccessContext(PROJECT_ID, workspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE).getCurrentRevision());
        assertStateValid(PROJECT_ID, workspaceId, null);

        Revision revision3 = ProjectStructure.newUpdateBuilder(this.fileAccessProvider, PROJECT_ID)
                .withProjectConfigurationUpdater(ProjectConfigurationUpdater.newUpdater()
                        .withProjectStructureVersion(this.projectStructureVersion)
                        .withProjectStructureExtensionVersion(this.projectStructureExtensionVersion)
                        .withGroupId(GROUP_ID)
                        .withArtifactId(ARTIFACT_ID))
                .withWorkspace(SourceSpecification.newSourceSpecification(workspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE))
                .withMessage("Vacuous update")
                .withProjectStructureExtensionProvider(this.projectStructureExtensionProvider)
                .withProjectStructurePlatformExtensions(this.projectStructurePlatformExtensions)
                .update();
        Assert.assertNull(revision3);
        Assert.assertEquals(revision, this.fileAccessProvider.getWorkspaceRevisionAccessContext(PROJECT_ID, workspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE).getCurrentRevision());
        assertStateValid(PROJECT_ID, workspaceId, null);
    }

    @Test
    public void testEntitiesDirectory()
    {
        ProjectStructure structure = buildProjectStructure();
        Assert.assertEquals(Collections.emptyList(), ListIterate.reject(structure.getEntitySourceDirectories(), sd -> sd.getDirectory().matches("(/[-\\w]++)++")));

//        Assert.assertTrue(structure.startsWithEntitiesDirectory(entitiesDirectory));
//        Assert.assertTrue(structure.startsWithEntitiesDirectory(entitiesDirectory + "/someFile.json"));
//        Assert.assertFalse(structure.startsWithEntitiesDirectory("/not/an/entities/directory.json"));
//        Assert.assertFalse(structure.startsWithEntitiesDirectory("/pom.xml"));
    }

    @Test
    public void testArtifactTypes()
    {
        T structure = buildProjectStructure();
        Assert.assertTrue(structure.isSupportedArtifactType(ArtifactType.entities));
        Assert.assertEquals(Arrays.stream(ArtifactType.values()).filter(structure::isSupportedArtifactType).collect(Collectors.toSet()), structure.getSupportedArtifactTypes());
        Map<ArtifactType, List<String>> expectedArtifactIdsByType = getExpectedArtifactIdsByType(structure);
        expectedArtifactIdsByType.forEach((type, expectedArtifactIds) ->
        {
            Assert.assertTrue("must contain " + type, structure.isSupportedArtifactType(type));
            Assert.assertTrue("must contain " + type, structure.getSupportedArtifactTypes().contains(type));
            Assert.assertEquals(Sets.mutable.withAll(expectedArtifactIds), structure.getArtifactIdsForType(type).collect(Collectors.toSet()));
        });
    }

    protected abstract Map<ArtifactType, List<String>> getExpectedArtifactIdsByType(T projectStructure);

    protected final void assertStateValid(String projectId, String workspaceId, String revisionId)
    {
        ProjectConfiguration configuration = ProjectStructure.getProjectConfiguration(projectId, SourceSpecification.newSourceSpecification(workspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), revisionId, this.fileAccessProvider);
        Assert.assertNotNull(configuration);
        ProjectStructure projectStructure = ProjectStructure.getProjectStructure(configuration);
        Assert.assertEquals(this.projectStructureVersion, projectStructure.getVersion());
        Class<T> structureClass = getProjectStructureClass();
        Assert.assertSame(structureClass, projectStructure.getClass());
        assertStateValid(structureClass.cast(projectStructure), projectId, workspaceId, revisionId);
    }

    protected void assertStateValid(T projectStructure, String projectId, String workspaceId, String revisionId)
    {
        Map<String, String> expectedFiles = Maps.mutable.empty();
        Set<String> unexpectedFiles = Sets.mutable.empty();
        collectExpectedFiles(projectStructure, expectedFiles::put, unexpectedFiles::add);

        if (!expectedFiles.isEmpty() || !unexpectedFiles.isEmpty())
        {
            Map<String, String> actualFiles = this.fileAccessProvider.getFileAccessContext(projectId, workspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE, revisionId).getFiles().collect(Collectors.toMap(ProjectFileAccessProvider.ProjectFile::getPath, ProjectFileAccessProvider.ProjectFile::getContentAsString));
            expectedFiles.forEach((path, expectedContent) ->
            {
                String actualContent = actualFiles.get(path);
                if (actualContent == null)
                {
                    Assert.fail("Missing file: " + path);
                }
                else
                {
                    Assert.assertEquals(path, expectedContent, actualContent);
                }
            });
            List<String> unexpectedButPresent = unexpectedFiles.stream().filter(actualFiles::containsKey).collect(Collectors.toList());
            if (!unexpectedButPresent.isEmpty())
            {
                StringJoiner joiner = new StringJoiner(", ", "Unexpected files were present: ", "");
                unexpectedButPresent.sort(Comparator.naturalOrder());
                unexpectedButPresent.forEach(joiner::add);
                Assert.fail(joiner.toString());
            }
        }
    }

    protected void collectExpectedFiles(T projectStructure, BiConsumer<String, String> expectedFilePathAndContentConsumer, Consumer<String> unexpectedFilePathConsumer)
    {
    }

    private List<Entity> getTestEntities()
    {
        return Arrays.asList(
                TestTools.newClassEntity("EmptyClass", "model::domain::test::empty"),
                TestTools.newClassEntity("EmptyClass2", "model::domain::test::empty"),
                TestTools.newClassEntity("ClassWith1Property", "model::domain::test::notEmpty", TestTools.newProperty("prop1", "String", 0, 1)),
                TestTools.newClassEntity("ClassWith2Properties", "model::domain::test::notEmpty", Arrays.asList(TestTools.newProperty("prop2", "Integer", 1, 1), TestTools.newProperty("prop3", "Date", 0, 1))),
                TestTools.newEnumerationEntity("MusicGenre", "model::domain::test::enums", "JAZZ", "BLUES", "BAROQUE", "SOUL", "FUNK", "SEA_CHANTEY"),
                TestTools.newEnumerationEntity("ArtMovements", "model::domain::test::enums", "CUBISM", "DADA", "POP_ART", "DE_STIJL", "SURREALISM")
        );
    }

    private ProjectFileOperation generateAddOperationForEntity(Entity entity, ProjectStructure projectStructure)
    {
        ProjectStructure.EntitySourceDirectory sourceDirectory = projectStructure.findSourceDirectoryForEntity(entity);
        if (sourceDirectory == null)
        {
            throw new RuntimeException("Cannot find source directory for entity \"" + entity.getPath() + "\" with classifier \"" + entity.getClassifierPath() + "\"");
        }
        return ProjectFileOperation.addFile(sourceDirectory.entityPathToFilePath(entity.getPath()), sourceDirectory.serializeToBytes(entity));
    }

    private List<Entity> getActualEntities(String projectId)
    {
        return getActualEntities(projectId, null, null);
    }

    private List<Entity> getActualEntities(String projectId, String workspaceId, String revisionId)
    {
        return getActualEntities(this.fileAccessProvider.getFileAccessContext(projectId, workspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE, revisionId));
    }

    private List<Entity> getActualEntities(ProjectFileAccessProvider.FileAccessContext fileAccessContext)
    {
        ProjectStructure projectStructure = ProjectStructure.getProjectStructure(fileAccessContext);
        return fileAccessContext.getFiles()
                .map(f -> Optional.ofNullable(projectStructure.findSourceDirectoryForEntityFilePath(f.getPath())).map(sd -> sd.deserialize(f)).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    protected abstract int getProjectStructureVersion();

    protected abstract Class<T> getProjectStructureClass();

    protected Integer getProjectStructureExtensionVersion()
    {
        return null;
    }

    protected ProjectStructureExtensionProvider getProjectStructureExtensionProvider()
    {
        return null;
    }

    protected InMemoryProjectFileAccessProvider newProjectFileAccessProvider()
    {
        return new InMemoryProjectFileAccessProvider(AUTHOR, COMMITTER);
    }

    protected T buildProjectStructure()
    {
        return buildProjectStructure(null);
    }

    protected T buildProjectStructure(String message)
    {
        return (T) buildProjectStructure(this.projectStructureVersion, this.projectStructureExtensionVersion, null, message);
    }

    protected ProjectStructure buildProjectStructure(Integer projectStructureVersion, Integer projectStructureExtensionVersion, String workspaceId, String message)
    {
        return buildProjectStructure(null, projectStructureVersion, projectStructureExtensionVersion, workspaceId, message);
    }

    protected ProjectStructure buildProjectStructure(String projectId, Integer projectStructureVersion, Integer projectStructureExtensionVersion, String workspaceId, String message)
    {
        return buildProjectStructure(projectId, projectStructureVersion, projectStructureExtensionVersion, null, null, workspaceId, message);
    }

    protected ProjectStructure buildProjectStructure(String projectId, Integer projectStructureVersion, Integer projectStructureExtensionVersion, String groupId, String artifactId, String workspaceId, String message)
    {
        if (projectId == null)
        {
            projectId = PROJECT_ID;
        }
        if (groupId == null)
        {
            groupId = GROUP_ID;
        }
        if (artifactId == null)
        {
            artifactId = ARTIFACT_ID;
        }
        if (workspaceId == null)
        {
            workspaceId = BUILD_WORKSPACE_ID;
        }
        if (message == null)
        {
            StringBuilder builder = new StringBuilder("Build project structure version ").append(projectStructureVersion);
            if (projectStructureExtensionVersion != null)
            {
                builder.append(" (extension version ").append(projectStructureExtensionVersion).append(")");
            }
            message = builder.toString();
        }

        this.fileAccessProvider.createWorkspace(projectId, workspaceId);
        ProjectStructure.newUpdateBuilder(this.fileAccessProvider, projectId)
                .withProjectConfigurationUpdater(ProjectConfigurationUpdater.newUpdater()
                        .withProjectStructureVersion(projectStructureVersion)
                        .withProjectStructureExtensionVersion(projectStructureExtensionVersion)
                        .withGroupId(groupId)
                        .withArtifactId(artifactId))
                .withWorkspace(SourceSpecification.newSourceSpecification(workspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE))
                .withMessage(message)
                .withProjectStructureExtensionProvider(this.projectStructureExtensionProvider)
                .withProjectStructurePlatformExtensions(this.projectStructurePlatformExtensions)
                .build();
        this.fileAccessProvider.commitWorkspace(projectId, workspaceId);
        return ProjectStructure.getProjectStructure(projectId, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);
    }

    private void createProjectWithVersions(String projectId, String groupId, String artifactId, String... versionIds)
    {
        ProjectStructure projectStructure = buildProjectStructure(projectId, this.projectStructureVersion, this.projectStructureExtensionVersion, groupId, artifactId, null, null);

        for (String versionId : versionIds)
        {
            String workspaceId = "WS" + versionId;
            String modelPackage = "model::" + projectId.toLowerCase().replaceAll("[^a-z0-9_]+", "_") + "::domain::v" + versionId.replace(".", "_");
            String entityName = "TestClass_" + versionId.replace(".", "_");
            this.fileAccessProvider.createWorkspace(projectId, workspaceId);
            Entity newClass = TestTools.newClassEntity(entityName, modelPackage, TestTools.newProperty("prop1", "String", 0, 1));
            ProjectFileOperation addEntityOperation = generateAddOperationForEntity(newClass, projectStructure);
            this.fileAccessProvider.getWorkspaceFileModificationContext(projectId, workspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE).submit("Add " + modelPackage + EntityPaths.PACKAGE_SEPARATOR + entityName, Collections.singletonList(addEntityOperation));
            this.fileAccessProvider.commitWorkspace(projectId, workspaceId);
            this.fileAccessProvider.createVersion(projectId, VersionId.parseVersionId(versionId));
        }
    }

    protected List<ArtifactGeneration> getArtifactGenerationConfiguration()
    {
        return Collections.emptyList();
    }

    @Test
    public void testUpdateProjectArtifactGeneration()
    {
        List<ArtifactGeneration> generations = Arrays.asList(
                new SimpleArtifactGeneration().withType(ArtifactType.java).withName(JAVA_TEST_MODULE),
                new SimpleArtifactGeneration().withType(ArtifactType.avro).withName(AVRO_TEST_MODULE));

        List<ArtifactGeneration> expectedGenerations = Lists.mutable.withAll(generations);
        expectedGenerations.addAll(getArtifactGenerationConfiguration());

        ProjectStructure projectStructure = buildProjectStructure();
        if (!generations.stream().map(ArtifactGeneration::getType).allMatch(projectStructure::isSupportedArtifactType))
        {
            return;
        }

        List<Entity> testEntities = getTestEntities();

        List<ProjectFileOperation> addEntityOperations = ListIterate.collectWith(testEntities, this::generateAddOperationForEntity, projectStructure);
        this.fileAccessProvider.getProjectFileModificationContext(PROJECT_ID).submit("Add entities", addEntityOperations);

        ProjectConfiguration beforeProjectConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);;
        Assert.assertEquals(PROJECT_ID, beforeProjectConfig.getProjectId());
        Assert.assertEquals(GROUP_ID, beforeProjectConfig.getGroupId());
        Assert.assertEquals(ARTIFACT_ID, beforeProjectConfig.getArtifactId());
        Assert.assertEquals(this.projectStructureVersion, beforeProjectConfig.getProjectStructureVersion().getVersion());
        Assert.assertEquals(this.projectStructureExtensionVersion, beforeProjectConfig.getProjectStructureVersion().getExtensionVersion());
        Assert.assertTrue(sameArtifactGenerations(getArtifactGenerationConfiguration(), beforeProjectConfig.getArtifactGenerations()));
        assertStateValid(PROJECT_ID, null, null);

        String addGenerationsWorkspaceId = "AddGeneration";
        this.fileAccessProvider.createWorkspace(PROJECT_ID, addGenerationsWorkspaceId);
        ProjectConfiguration beforeWorkspaceConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);;
        Assert.assertEquals(PROJECT_ID, beforeWorkspaceConfig.getProjectId());
        Assert.assertEquals(GROUP_ID, beforeWorkspaceConfig.getGroupId());
        Assert.assertEquals(ARTIFACT_ID, beforeWorkspaceConfig.getArtifactId());
        Assert.assertEquals(this.projectStructureVersion, beforeWorkspaceConfig.getProjectStructureVersion().getVersion());
        Assert.assertEquals(this.projectStructureExtensionVersion, beforeWorkspaceConfig.getProjectStructureVersion().getExtensionVersion());
        Assert.assertTrue(beforeWorkspaceConfig.getMetamodelDependencies().isEmpty());
        Assert.assertTrue(beforeWorkspaceConfig.getProjectDependencies().isEmpty());
        Assert.assertTrue(sameArtifactGenerations(getArtifactGenerationConfiguration(), beforeWorkspaceConfig.getArtifactGenerations()));
        assertStateValid(PROJECT_ID, addGenerationsWorkspaceId, null);

        ProjectStructure.newUpdateBuilder(this.fileAccessProvider, PROJECT_ID)
                .withProjectConfigurationUpdater(ProjectConfigurationUpdater.newUpdater().withArtifactGenerationsToAdd(generations))
                .withWorkspace(SourceSpecification.newSourceSpecification(addGenerationsWorkspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE))
                .withMessage("Add generation")
                .withProjectStructureExtensionProvider(this.projectStructureExtensionProvider)
                .withProjectStructurePlatformExtensions(this.projectStructurePlatformExtensions)
                .update();

        ProjectConfiguration afterWorkspaceConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(addGenerationsWorkspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);
        Assert.assertEquals(PROJECT_ID, afterWorkspaceConfig.getProjectId());
        Assert.assertEquals(GROUP_ID, afterWorkspaceConfig.getGroupId());
        Assert.assertEquals(ARTIFACT_ID, afterWorkspaceConfig.getArtifactId());
        Assert.assertEquals(this.projectStructureVersion, afterWorkspaceConfig.getProjectStructureVersion().getVersion());
        Assert.assertEquals(this.projectStructureExtensionVersion, afterWorkspaceConfig.getProjectStructureVersion().getExtensionVersion());
        Assert.assertTrue(sameArtifactGenerations(expectedGenerations, afterWorkspaceConfig.getArtifactGenerations()));

        assertStateValid(PROJECT_ID, addGenerationsWorkspaceId, null);
        this.fileAccessProvider.commitWorkspace(PROJECT_ID, addGenerationsWorkspaceId);


        ProjectConfiguration afterProjectConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);;
        Assert.assertEquals(PROJECT_ID, afterProjectConfig.getProjectId());
        Assert.assertEquals(GROUP_ID, afterProjectConfig.getGroupId());
        Assert.assertEquals(ARTIFACT_ID, afterProjectConfig.getArtifactId());
        Assert.assertEquals(this.projectStructureVersion, afterProjectConfig.getProjectStructureVersion().getVersion());
        Assert.assertEquals(this.projectStructureExtensionVersion, afterProjectConfig.getProjectStructureVersion().getExtensionVersion());
        Assert.assertTrue(sameArtifactGenerations(expectedGenerations, afterProjectConfig.getArtifactGenerations()));
        assertStateValid(PROJECT_ID, null, null);
        //
        String noChangeWorkspaceId = "NoChangeToGeneration";
        this.fileAccessProvider.createWorkspace(PROJECT_ID, noChangeWorkspaceId);
        Revision newRevision = ProjectStructure.newUpdateBuilder(this.fileAccessProvider, PROJECT_ID)
                .withProjectConfigurationUpdater(ProjectConfigurationUpdater.newUpdater().withGroupId("temp.group.id"))
                .withWorkspace(SourceSpecification.newSourceSpecification(noChangeWorkspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE, null))
                .withMessage("No change to generation")
                .withProjectStructureExtensionProvider(this.projectStructureExtensionProvider)
                .withProjectStructurePlatformExtensions(this.projectStructurePlatformExtensions)
                .update();
        Assert.assertNotNull(newRevision);
        ProjectConfiguration noChangeWorkspaceConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(noChangeWorkspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);
        Assert.assertEquals(PROJECT_ID, noChangeWorkspaceConfig.getProjectId());
        Assert.assertEquals("temp.group.id", noChangeWorkspaceConfig.getGroupId());
        Assert.assertEquals(ARTIFACT_ID, noChangeWorkspaceConfig.getArtifactId());
        Assert.assertEquals(this.projectStructureVersion, noChangeWorkspaceConfig.getProjectStructureVersion().getVersion());
        Assert.assertEquals(this.projectStructureExtensionVersion, noChangeWorkspaceConfig.getProjectStructureVersion().getExtensionVersion());
        Assert.assertTrue(sameArtifactGenerations(expectedGenerations, noChangeWorkspaceConfig.getArtifactGenerations()));
        assertStateValid(PROJECT_ID, noChangeWorkspaceId, null);
        this.fileAccessProvider.deleteWorkspace(PROJECT_ID, noChangeWorkspaceId);

        for (int i = 0; i < generations.size(); i++)
        {
            String removeDependencyWorkspaceId = "RemoveGeneration" + 0;
            this.fileAccessProvider.createWorkspace(PROJECT_ID, removeDependencyWorkspaceId);
            ProjectConfiguration beforeRemovalConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);;
            Assert.assertEquals(PROJECT_ID, beforeRemovalConfig.getProjectId());
            Assert.assertEquals(GROUP_ID, beforeRemovalConfig.getGroupId());
            Assert.assertEquals(ARTIFACT_ID, beforeRemovalConfig.getArtifactId());
            Assert.assertEquals(this.projectStructureVersion, beforeRemovalConfig.getProjectStructureVersion().getVersion());
            Assert.assertEquals(this.projectStructureExtensionVersion, beforeRemovalConfig.getProjectStructureVersion().getExtensionVersion());
            Assert.assertEquals(Collections.emptyList(), beforeRemovalConfig.getMetamodelDependencies());
            Assert.assertTrue(sameArtifactGenerations(expectedGenerations.subList(i, expectedGenerations.size()), beforeRemovalConfig.getArtifactGenerations()));
            assertStateValid(PROJECT_ID, removeDependencyWorkspaceId, null);


            ProjectStructure.newUpdateBuilder(this.fileAccessProvider, PROJECT_ID)
                    .withProjectConfigurationUpdater(ProjectConfigurationUpdater.newUpdater().withArtifactGenerationsToRemove(Collections.singletonList(generations.get(i).getName())))
                    .withWorkspace(SourceSpecification.newSourceSpecification(removeDependencyWorkspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE, null))
                    .withMessage("Remove generations")
                    .withProjectStructureExtensionProvider(this.projectStructureExtensionProvider)
                    .withProjectStructurePlatformExtensions(this.projectStructurePlatformExtensions)
                    .update();
            ProjectConfiguration afterRemovalWorkspaceConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(removeDependencyWorkspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);
            Assert.assertEquals(PROJECT_ID, afterRemovalWorkspaceConfig.getProjectId());
            Assert.assertEquals(GROUP_ID, afterRemovalWorkspaceConfig.getGroupId());
            Assert.assertEquals(ARTIFACT_ID, afterRemovalWorkspaceConfig.getArtifactId());
            Assert.assertEquals(this.projectStructureVersion, afterRemovalWorkspaceConfig.getProjectStructureVersion().getVersion());
            Assert.assertEquals(this.projectStructureExtensionVersion, afterRemovalWorkspaceConfig.getProjectStructureVersion().getExtensionVersion());
            Assert.assertEquals(Collections.emptyList(), afterRemovalWorkspaceConfig.getMetamodelDependencies());
            Assert.assertTrue(sameArtifactGenerations(expectedGenerations.subList(i + 1, expectedGenerations.size()), afterRemovalWorkspaceConfig.getArtifactGenerations()));
            assertStateValid(PROJECT_ID, removeDependencyWorkspaceId, null);

            this.fileAccessProvider.commitWorkspace(PROJECT_ID, removeDependencyWorkspaceId);

            ProjectConfiguration afterRemovalProjectConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);;
            Assert.assertEquals(PROJECT_ID, afterRemovalProjectConfig.getProjectId());
            Assert.assertEquals(GROUP_ID, afterRemovalProjectConfig.getGroupId());
            Assert.assertEquals(ARTIFACT_ID, afterRemovalProjectConfig.getArtifactId());
            Assert.assertEquals(this.projectStructureVersion, afterRemovalProjectConfig.getProjectStructureVersion().getVersion());
            Assert.assertEquals(this.projectStructureExtensionVersion, afterRemovalProjectConfig.getProjectStructureVersion().getExtensionVersion());
            Assert.assertEquals(Collections.emptyList(), afterRemovalProjectConfig.getMetamodelDependencies());
            Assert.assertTrue(sameArtifactGenerations(expectedGenerations.subList(i + 1, expectedGenerations.size()), afterRemovalProjectConfig.getArtifactGenerations()));
            assertStateValid(PROJECT_ID, null, null);
        }

        ProjectConfiguration projectConfigUpdateRevisionConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);;
        Assert.assertEquals(PROJECT_ID, projectConfigUpdateRevisionConfig.getProjectId());
        Assert.assertEquals(GROUP_ID, projectConfigUpdateRevisionConfig.getGroupId());
        Assert.assertEquals(ARTIFACT_ID, projectConfigUpdateRevisionConfig.getArtifactId());
        Assert.assertEquals(this.projectStructureVersion, projectConfigUpdateRevisionConfig.getProjectStructureVersion().getVersion());
        Assert.assertEquals(this.projectStructureExtensionVersion, projectConfigUpdateRevisionConfig.getProjectStructureVersion().getExtensionVersion());
        Assert.assertEquals(Collections.emptyList(), projectConfigUpdateRevisionConfig.getMetamodelDependencies());
        Assert.assertEquals(Collections.emptyList(), projectConfigUpdateRevisionConfig.getProjectDependencies());

        assertEntitiesEquivalent(testEntities, getActualEntities(PROJECT_ID));
        //todo assert non expected files
    }

    private boolean sameArtifactGenerations(List<ArtifactGeneration> expectedGenerations, List<ArtifactGeneration> artifactGeneration)
    {
        boolean areSameSize = expectedGenerations.size() == artifactGeneration.size();

        Map<String, ArtifactGeneration> generationsByName = expectedGenerations.stream().collect(Collectors.toMap(ArtifactGeneration::getName, Function.identity()));
        Map<String, ArtifactGeneration> actualGenerationsByName = expectedGenerations.stream().collect(Collectors.toMap(ArtifactGeneration::getName, Function.identity()));

        boolean sameContents = generationsByName.keySet().stream().allMatch(key -> generationsByName.get(key).getType().equals(actualGenerationsByName.get(key).getType()));

        return areSameSize && sameContents;
    }

    @Test
    public void testAddSameTypeProjectArtifactGenerations()
    {
        List<ArtifactGeneration> generations = Arrays.asList(
                new SimpleArtifactGeneration().withType(ArtifactType.java).withName(JAVA_TEST_MODULE),
                new SimpleArtifactGeneration().withType(ArtifactType.java).withName(JAVA_TEST_MODULE + "newone"),
                new SimpleArtifactGeneration().withType(ArtifactType.avro).withName("avro1").withParameters(Collections.singletonMap("one", "one")),
                new SimpleArtifactGeneration().withType(ArtifactType.avro).withName("avro2").withParameters(Collections.singletonMap("two", 2)));

        ProjectStructure projectStructure = buildProjectStructure();
        if (!generations.stream().map(ArtifactGeneration::getType).allMatch(projectStructure::isSupportedArtifactType))
        {
            return;
        }

        List<Entity> testEntities = getTestEntities();

        List<ProjectFileOperation> addEntityOperations = ListIterate.collectWith(testEntities, this::generateAddOperationForEntity, projectStructure);
        this.fileAccessProvider.getProjectFileModificationContext(PROJECT_ID).submit("Add entities", addEntityOperations);

        ProjectConfiguration beforeProjectConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);;
        Assert.assertEquals(PROJECT_ID, beforeProjectConfig.getProjectId());
        Assert.assertEquals(GROUP_ID, beforeProjectConfig.getGroupId());
        Assert.assertEquals(ARTIFACT_ID, beforeProjectConfig.getArtifactId());
        Assert.assertEquals(this.projectStructureVersion, beforeProjectConfig.getProjectStructureVersion().getVersion());
        Assert.assertEquals(this.projectStructureExtensionVersion, beforeProjectConfig.getProjectStructureVersion().getExtensionVersion());
        Assert.assertTrue(sameArtifactGenerations(getArtifactGenerationConfiguration(), beforeProjectConfig.getArtifactGenerations()));
        assertStateValid(PROJECT_ID, null, null);

        String addGenerationsWorkspaceId = "AddGeneration";
        this.fileAccessProvider.createWorkspace(PROJECT_ID, addGenerationsWorkspaceId);
        ProjectConfiguration beforeWorkspaceConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);;
        Assert.assertEquals(PROJECT_ID, beforeWorkspaceConfig.getProjectId());
        Assert.assertEquals(GROUP_ID, beforeWorkspaceConfig.getGroupId());
        Assert.assertEquals(ARTIFACT_ID, beforeWorkspaceConfig.getArtifactId());
        Assert.assertEquals(this.projectStructureVersion, beforeWorkspaceConfig.getProjectStructureVersion().getVersion());
        Assert.assertEquals(this.projectStructureExtensionVersion, beforeWorkspaceConfig.getProjectStructureVersion().getExtensionVersion());
        Assert.assertTrue(beforeWorkspaceConfig.getMetamodelDependencies().isEmpty());
        Assert.assertTrue(beforeWorkspaceConfig.getProjectDependencies().isEmpty());
        Assert.assertTrue(sameArtifactGenerations(getArtifactGenerationConfiguration(), beforeWorkspaceConfig.getArtifactGenerations()));
        assertStateValid(PROJECT_ID, addGenerationsWorkspaceId, null);

        ProjectStructure.newUpdateBuilder(this.fileAccessProvider, PROJECT_ID)
                .withProjectConfigurationUpdater(ProjectConfigurationUpdater.newUpdater().withArtifactGenerationsToAdd(generations))
                .withWorkspace(SourceSpecification.newSourceSpecification(addGenerationsWorkspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE))
                .withMessage("Add generation multiple same types")
                .withProjectStructureExtensionProvider(this.projectStructureExtensionProvider)
                .withProjectStructurePlatformExtensions(this.projectStructurePlatformExtensions)
                .update();

        ProjectConfiguration afterWorkspaceConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(addGenerationsWorkspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);
        Assert.assertEquals(PROJECT_ID, afterWorkspaceConfig.getProjectId());
        Assert.assertEquals(GROUP_ID, afterWorkspaceConfig.getGroupId());
        Assert.assertEquals(ARTIFACT_ID, afterWorkspaceConfig.getArtifactId());
        Assert.assertEquals(this.projectStructureVersion, afterWorkspaceConfig.getProjectStructureVersion().getVersion());
        Assert.assertEquals(this.projectStructureExtensionVersion, afterWorkspaceConfig.getProjectStructureVersion().getExtensionVersion());
        Assert.assertTrue(sameArtifactGenerations(generations, afterWorkspaceConfig.getArtifactGenerations()));

        assertStateValid(PROJECT_ID, addGenerationsWorkspaceId, null);
        this.fileAccessProvider.commitWorkspace(PROJECT_ID, addGenerationsWorkspaceId);


        ProjectConfiguration afterProjectConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);;
        Assert.assertEquals(PROJECT_ID, afterProjectConfig.getProjectId());
        Assert.assertEquals(GROUP_ID, afterProjectConfig.getGroupId());
        Assert.assertEquals(ARTIFACT_ID, afterProjectConfig.getArtifactId());
        Assert.assertEquals(this.projectStructureVersion, afterProjectConfig.getProjectStructureVersion().getVersion());
        Assert.assertEquals(this.projectStructureExtensionVersion, afterProjectConfig.getProjectStructureVersion().getExtensionVersion());
        Assert.assertTrue(sameArtifactGenerations(generations, afterProjectConfig.getArtifactGenerations()));
        assertStateValid(PROJECT_ID, null, null);
    }

    @Test
    public void testCantAddInvalidProjectArtifactGenerations()
    {
        ProjectStructure projectStructure = buildProjectStructure();
        List<Entity> testEntities = getTestEntities();

        List<ProjectFileOperation> addEntityOperations = ListIterate.collectWith(testEntities, this::generateAddOperationForEntity, projectStructure);
        this.fileAccessProvider.getProjectFileModificationContext(PROJECT_ID).submit("Add entities", addEntityOperations);

        ProjectConfiguration beforeProjectConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);;
        Assert.assertEquals(PROJECT_ID, beforeProjectConfig.getProjectId());
        Assert.assertEquals(GROUP_ID, beforeProjectConfig.getGroupId());
        Assert.assertEquals(ARTIFACT_ID, beforeProjectConfig.getArtifactId());
        Assert.assertEquals(this.projectStructureVersion, beforeProjectConfig.getProjectStructureVersion().getVersion());
        Assert.assertEquals(this.projectStructureExtensionVersion, beforeProjectConfig.getProjectStructureVersion().getExtensionVersion());
        Assert.assertTrue(beforeProjectConfig.getArtifactGenerations().isEmpty());
        assertStateValid(PROJECT_ID, null, null);

        String addGenerationsWorkspaceId = "AddInvalidGeneration";
        this.fileAccessProvider.createWorkspace(PROJECT_ID, addGenerationsWorkspaceId);
        ProjectConfiguration beforeWorkspaceConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);;
        Assert.assertEquals(PROJECT_ID, beforeWorkspaceConfig.getProjectId());
        Assert.assertEquals(GROUP_ID, beforeWorkspaceConfig.getGroupId());
        Assert.assertEquals(ARTIFACT_ID, beforeWorkspaceConfig.getArtifactId());
        Assert.assertEquals(this.projectStructureVersion, beforeWorkspaceConfig.getProjectStructureVersion().getVersion());
        Assert.assertEquals(this.projectStructureExtensionVersion, beforeWorkspaceConfig.getProjectStructureVersion().getExtensionVersion());
        Assert.assertTrue(beforeWorkspaceConfig.getMetamodelDependencies().isEmpty());
        Assert.assertTrue(beforeWorkspaceConfig.getProjectDependencies().isEmpty());
        Assert.assertTrue(beforeWorkspaceConfig.getArtifactGenerations().isEmpty());
        assertStateValid(PROJECT_ID, addGenerationsWorkspaceId, null);

        LegendSDLCServerException e1 = Assert.assertThrows(
                LegendSDLCServerException.class,
                () -> ProjectStructure.newUpdateBuilder(this.fileAccessProvider, PROJECT_ID)
                        .withProjectConfigurationUpdater(ProjectConfigurationUpdater.newUpdater()
                                .withArtifactGenerationsToAdd(Lists.fixedSize.with(new SimpleArtifactGeneration().withType(ArtifactType.entities).withName("invalid"), new SimpleArtifactGeneration().withType(ArtifactType.versioned_entities).withName("invalid2"))))
                        .withWorkspace(SourceSpecification.newSourceSpecification(addGenerationsWorkspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE))
                        .withMessage("Add invalid type generations")
                        .withProjectStructureExtensionProvider(this.projectStructureExtensionProvider)
                        .withProjectStructurePlatformExtensions(this.projectStructurePlatformExtensions)
                        .update());
        Assert.assertEquals("There were issues with one or more added artifact generations: generation types Entity, Service Execution, Versioned Entity are not allowed", e1.getMessage());

        if (!projectStructure.isSupportedArtifactType(ArtifactType.avro))
        {
            return;
        }

        Map<String, Object> params = Collections.singletonMap("one", "one");
        LegendSDLCServerException e2 = Assert.assertThrows(
                LegendSDLCServerException.class,
                () -> ProjectStructure.newUpdateBuilder(this.fileAccessProvider, PROJECT_ID)
                        .withProjectConfigurationUpdater(ProjectConfigurationUpdater.newUpdater()
                                .withArtifactGenerationsToAdd(Lists.fixedSize.with(new SimpleArtifactGeneration().withType(ArtifactType.avro).withName("dup"), new SimpleArtifactGeneration().withType(ArtifactType.avro).withName("dup").withParameters(params))))
                        .withWorkspace(SourceSpecification.newSourceSpecification(addGenerationsWorkspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE))
                        .withMessage("Add duplicate generations")
                        .withProjectStructureExtensionProvider(this.projectStructureExtensionProvider)
                        .withProjectStructurePlatformExtensions(this.projectStructurePlatformExtensions)
                        .update());
        Assert.assertEquals("There were issues with one or more added artifact generations: duplicate generations: \"dup\"", e2.getMessage());

        if (!projectStructure.isSupportedArtifactType(ArtifactType.java))
        {
            return;
        }
        LegendSDLCServerException e3 = Assert.assertThrows(
                LegendSDLCServerException.class,
                () -> ProjectStructure.newUpdateBuilder(this.fileAccessProvider, PROJECT_ID)
                        .withProjectConfigurationUpdater(ProjectConfigurationUpdater.newUpdater()
                                .withArtifactGenerationsToAdd(Lists.fixedSize.with(new SimpleArtifactGeneration().withType(ArtifactType.avro).withName("same"), new SimpleArtifactGeneration().withType(ArtifactType.java).withName("same"))))
                        .withWorkspace(SourceSpecification.newSourceSpecification(addGenerationsWorkspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE))
                        .withMessage("Add duplicate names generations")
                        .withProjectStructureExtensionProvider(this.projectStructureExtensionProvider)
                        .withProjectStructurePlatformExtensions(this.projectStructurePlatformExtensions)
                        .update());
        Assert.assertEquals("There were issues with one or more added artifact generations: duplicate generations: \"same\"", e3.getMessage());

        LegendSDLCServerException e4 = Assert.assertThrows(
                LegendSDLCServerException.class,
                () -> ProjectStructure.newUpdateBuilder(this.fileAccessProvider, PROJECT_ID)
                        .withProjectConfigurationUpdater(ProjectConfigurationUpdater.newUpdater()
                                .withArtifactGenerationsToAdd(Lists.fixedSize.with(new SimpleArtifactGeneration().withType(ArtifactType.avro).withName("dup"), new SimpleArtifactGeneration().withType(ArtifactType.avro).withName("dup"))))
                        .withWorkspace(SourceSpecification.newSourceSpecification(addGenerationsWorkspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE))
                        .withMessage("Add a names generations")
                        .withProjectStructureExtensionProvider(this.projectStructureExtensionProvider)
                        .withProjectStructurePlatformExtensions(this.projectStructurePlatformExtensions)
                        .update());
        Assert.assertEquals("There were issues with one or more added artifact generations: duplicate generations: \"dup\"", e4.getMessage());

        ProjectStructure.newUpdateBuilder(this.fileAccessProvider, PROJECT_ID)
                .withProjectConfigurationUpdater(ProjectConfigurationUpdater.newUpdater()
                        .withArtifactGenerationsToAdd(Lists.fixedSize.with(new SimpleArtifactGeneration().withType(ArtifactType.avro).withName("dup"))))
                .withWorkspace(SourceSpecification.newSourceSpecification(addGenerationsWorkspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE))
                .withMessage("Add a names generations")
                .withProjectStructureExtensionProvider(this.projectStructureExtensionProvider)
                .withProjectStructurePlatformExtensions(this.projectStructurePlatformExtensions)
                .update();
        LegendSDLCServerException e5 = Assert.assertThrows(
                LegendSDLCServerException.class,
                () -> ProjectStructure.newUpdateBuilder(this.fileAccessProvider, PROJECT_ID)
                        .withProjectConfigurationUpdater(ProjectConfigurationUpdater.newUpdater()
                                .withArtifactGenerationsToAdd(Lists.fixedSize.with(new SimpleArtifactGeneration().withType(ArtifactType.avro).withName("dup").withParameters(params))))
                        .withWorkspace(SourceSpecification.newSourceSpecification(addGenerationsWorkspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE))
                        .withMessage("Add a duplicate name")

                        .withProjectStructureExtensionProvider(this.projectStructureExtensionProvider)
                        .withProjectStructurePlatformExtensions(this.projectStructurePlatformExtensions)
                        .update());
        Assert.assertEquals("There were issues with one or more added artifact generations: duplicate generations: \"dup\"", e5.getMessage());
    }


    protected abstract void assertMultiFormatGenerationStateValid(String projectId, String workspaceId, String revisionId, ArtifactType generationType);

    @Test
    public void testMultiFormatArtifactGeneration()
    {
        ProjectStructure projectStructure = buildProjectStructure();
        Set<ArtifactType> configs = projectStructure.getAvailableGenerationConfigurations().stream().map(ArtifactTypeGenerationConfiguration::getType).collect(Collectors.toSet());
        Assert.assertEquals(getExpectedSupportedArtifactConfigurationTypes(), configs);
        Assert.assertEquals(Collections.emptySet(), configs.stream().filter(t -> !projectStructure.isSupportedArtifactType(t)).collect(Collectors.toSet()));
        if (!configs.isEmpty())
        {
            List<Entity> testEntities = getTestEntities();
            List<ProjectFileOperation> addEntityOperations = ListIterate.collectWith(testEntities, this::generateAddOperationForEntity, projectStructure);
            this.fileAccessProvider.getProjectFileModificationContext(PROJECT_ID).submit("Add entities", addEntityOperations);

            ProjectConfiguration beforeProjectConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);;
            Assert.assertEquals(PROJECT_ID, beforeProjectConfig.getProjectId());
            Assert.assertEquals(GROUP_ID, beforeProjectConfig.getGroupId());
            Assert.assertEquals(ARTIFACT_ID, beforeProjectConfig.getArtifactId());
            Assert.assertEquals(this.projectStructureVersion, beforeProjectConfig.getProjectStructureVersion().getVersion());
            Assert.assertEquals(this.projectStructureExtensionVersion, beforeProjectConfig.getProjectStructureVersion().getExtensionVersion());
            Assert.assertTrue(beforeProjectConfig.getArtifactGenerations().isEmpty());
            assertStateValid(PROJECT_ID, null, null);

            String addGenerationsWorkspaceId = "AddGeneration";
            this.fileAccessProvider.createWorkspace(PROJECT_ID, addGenerationsWorkspaceId);
            ProjectConfiguration beforeWorkspaceConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);;
            Assert.assertEquals(PROJECT_ID, beforeWorkspaceConfig.getProjectId());
            Assert.assertEquals(GROUP_ID, beforeWorkspaceConfig.getGroupId());
            Assert.assertEquals(ARTIFACT_ID, beforeWorkspaceConfig.getArtifactId());
            Assert.assertEquals(this.projectStructureVersion, beforeWorkspaceConfig.getProjectStructureVersion().getVersion());
            Assert.assertEquals(this.projectStructureExtensionVersion, beforeWorkspaceConfig.getProjectStructureVersion().getExtensionVersion());
            Assert.assertTrue(beforeWorkspaceConfig.getMetamodelDependencies().isEmpty());
            Assert.assertTrue(beforeWorkspaceConfig.getProjectDependencies().isEmpty());
            Assert.assertTrue(beforeWorkspaceConfig.getArtifactGenerations().isEmpty());
            assertStateValid(PROJECT_ID, addGenerationsWorkspaceId, null);

            List<ProjectDependency> projectDependencies = Arrays.asList(
                    ProjectDependency.parseProjectDependency(GROUP_ID + ":testproject0:0.0.1"),
                    ProjectDependency.parseProjectDependency(GROUP_ID + ":testproject1:1.0.0"),
                    ProjectDependency.parseProjectDependency(GROUP_ID + ":testproject3:2.0.1"));
            projectDependencies.sort(ProjectStructure.getProjectDependencyComparator());
            for (ProjectDependency projectDependency : projectDependencies)
            {
                String artifactId = projectDependency.getProjectId().substring(GROUP_ID.length() + 1);
                createProjectWithVersions(artifactId, GROUP_ID, artifactId, projectDependency.getVersionId());
            }

            ProjectStructure.newUpdateBuilder(this.fileAccessProvider, PROJECT_ID)
                    .withProjectConfigurationUpdater(ProjectConfigurationUpdater.newUpdater().withProjectDependenciesToAdd(projectDependencies))
                    .withWorkspace(SourceSpecification.newSourceSpecification(addGenerationsWorkspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE))
                    .withMessage("Add multi generation")
                    .withProjectStructureExtensionProvider(this.projectStructureExtensionProvider)
                    .withProjectStructurePlatformExtensions(this.projectStructurePlatformExtensions)
                    .update();

            ProjectConfiguration afterWorkspaceConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(addGenerationsWorkspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);
            Assert.assertEquals(PROJECT_ID, afterWorkspaceConfig.getProjectId());
            Assert.assertEquals(GROUP_ID, afterWorkspaceConfig.getGroupId());
            Assert.assertEquals(ARTIFACT_ID, afterWorkspaceConfig.getArtifactId());
            Assert.assertEquals(this.projectStructureVersion, afterWorkspaceConfig.getProjectStructureVersion().getVersion());
            Assert.assertEquals(this.projectStructureExtensionVersion, afterWorkspaceConfig.getProjectStructureVersion().getExtensionVersion());
            Assert.assertEquals(projectDependencies, afterWorkspaceConfig.getProjectDependencies());

            assertStateValid(PROJECT_ID, addGenerationsWorkspaceId, null);
            this.fileAccessProvider.commitWorkspace(PROJECT_ID, addGenerationsWorkspaceId);

            for (ArtifactType type : configs)
            {
                testMultiFormatFormatGeneration(type, Collections.singletonList(new SimpleArtifactGeneration().withType(type).withName("test-" + type.name()).withParameters(Collections.emptyMap())));
            }
        }
    }

    protected abstract Set<ArtifactType> getExpectedSupportedArtifactConfigurationTypes();

    private void testMultiFormatFormatGeneration(ArtifactType type, List<ArtifactGeneration> generations)
    {
        String addGenerationsWorkspaceId = "AddGeneration" + type.name();
        this.fileAccessProvider.createWorkspace(PROJECT_ID, addGenerationsWorkspaceId);
        ProjectConfiguration beforeWorkspaceConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(null, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);;
        Assert.assertEquals(PROJECT_ID, beforeWorkspaceConfig.getProjectId());
        Assert.assertEquals(GROUP_ID, beforeWorkspaceConfig.getGroupId());
        Assert.assertEquals(ARTIFACT_ID, beforeWorkspaceConfig.getArtifactId());
        Assert.assertEquals(this.projectStructureVersion, beforeWorkspaceConfig.getProjectStructureVersion().getVersion());
        Assert.assertEquals(this.projectStructureExtensionVersion, beforeWorkspaceConfig.getProjectStructureVersion().getExtensionVersion());
        assertStateValid(PROJECT_ID, addGenerationsWorkspaceId, null);

        ProjectStructure.newUpdateBuilder(this.fileAccessProvider, PROJECT_ID)
                .withProjectConfigurationUpdater(ProjectConfigurationUpdater.newUpdater().withArtifactGenerationsToAdd(generations))
                .withWorkspace(SourceSpecification.newSourceSpecification(addGenerationsWorkspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE))
                .withProjectStructureExtensionProvider(this.projectStructureExtensionProvider)
                .withProjectStructurePlatformExtensions(this.projectStructurePlatformExtensions)
                .update();

        ProjectConfiguration afterWorkspaceConfig = ProjectStructure.getProjectConfiguration(PROJECT_ID, SourceSpecification.newSourceSpecification(addGenerationsWorkspaceId, WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), null, this.fileAccessProvider);
        Assert.assertEquals(PROJECT_ID, afterWorkspaceConfig.getProjectId());
        Assert.assertEquals(GROUP_ID, afterWorkspaceConfig.getGroupId());
        Assert.assertEquals(ARTIFACT_ID, afterWorkspaceConfig.getArtifactId());
        Assert.assertEquals(this.projectStructureVersion, afterWorkspaceConfig.getProjectStructureVersion().getVersion());
        Assert.assertEquals(this.projectStructureExtensionVersion, afterWorkspaceConfig.getProjectStructureVersion().getExtensionVersion());
        Assert.assertTrue(sameArtifactGenerations(generations, afterWorkspaceConfig.getArtifactGenerations()));

        assertStateValid(PROJECT_ID, addGenerationsWorkspaceId, null);
        assertMultiFormatGenerationStateValid(PROJECT_ID, addGenerationsWorkspaceId, null, type);
    }

    private void assertEntitiesEquivalent(Iterable<? extends Entity> expectedEntities, Iterable<? extends Entity> actualEntities)
    {
        assertEntitiesEquivalent(null, expectedEntities, actualEntities);
    }

    private void assertEntitiesEquivalent(String message, Iterable<? extends Entity> expectedEntities, Iterable<? extends Entity> actualEntities)
    {
        List<Entity> expectedEntitiesList = normalizeEntitiesForEquivalence(expectedEntities);
        List<Entity> actualEntitiesList = normalizeEntitiesForEquivalence(actualEntities);

        JsonMapper jsonMapper = JsonMapper.builder()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .build();
        String expectedJson;
        String actualJson;
        try
        {
            expectedJson = jsonMapper.writeValueAsString(expectedEntitiesList);
            actualJson = jsonMapper.writeValueAsString(actualEntitiesList);
        }
        catch (JsonProcessingException e)
        {
            throw new RuntimeException(e);
        }
        Assert.assertEquals(message, expectedJson, actualJson);
    }

    private List<Entity> normalizeEntitiesForEquivalence(Iterable<? extends Entity> entities)
    {
        return Iterate.collect(entities, this::normalizeEntityForEquivalence, Lists.mutable.empty()).sortThisBy(Entity::getPath);
    }

    private Entity normalizeEntityForEquivalence(Entity entity)
    {
        Map<String, ?> newContent = normalizeEntityContent(entity.getContent());
        return (newContent == null) ? entity : Entity.newEntity(entity.getPath(), entity.getClassifierPath(), newContent);
    }

    private <K> Map<K, ?> normalizeEntityContent(Map<K, ?> map)
    {
        Map<K, Object> newMap = Maps.mutable.ofMap(map);
        boolean changed = false;
        for (Map.Entry<K, ?> entry : map.entrySet())
        {
            K key = entry.getKey();
            Object value = entry.getValue();
            if ((value == null) || "sourceInformation".equals(key) || "propertyTypeSourceInformation".equals(key))
            {
                newMap.remove(key);
                changed = true;
            }
            else if (value instanceof Map)
            {
                Map<?, ?> replacement = normalizeEntityContent((Map<?, ?>) value);
                if (replacement != null)
                {
                    newMap.put(key, replacement);
                    changed = true;
                }
            }
            else if (value instanceof List)
            {
                List<?> list = (List<?>) value;
                if (list.isEmpty())
                {
                    newMap.remove(key);
                    changed = true;
                }
                else
                {
                    List<Object> replacement = normalizeEntityContent(list);
                    if (replacement != null)
                    {
                        newMap.put(key, replacement);
                        changed = true;
                    }
                }
            }
        }
        return changed ? newMap : null;
    }

    private List<Object> normalizeEntityContent(List<?> list)
    {
        List<Object> newList = Lists.mutable.ofInitialCapacity(list.size());
        boolean changed = false;
        for (Object value : list)
        {
            if (value instanceof List)
            {
                List<?> newValue = normalizeEntityContent((List<?>) value);
                changed |= (newValue != null);
                newList.add((newValue == null) ? value : newValue);
            }
            else if (value instanceof Map)
            {
                Map<?, ?> newValue = normalizeEntityContent((Map<?, ?>) value);
                changed |= (newValue != null);
                newList.add((newValue == null) ? value : newValue);
            }
            else
            {
                newList.add(value);
            }
        }
        return changed ? newList : null;
    }
}
