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

package org.finos.legend.sdlc.core.tck;

import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;
import org.finos.legend.sdlc.core.entity.EntityModificationOperations;
import org.finos.legend.sdlc.core.project.ProjectConfigurationUpdater;
import org.finos.legend.sdlc.core.project.ProjectStructureUpdater;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.entity.change.EntityChange;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider;
import org.finos.legend.sdlc.project.structure.ProjectStructure;
import org.finos.legend.sdlc.project.source.SourceSpecification;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

/**
 * The seed of the backend test suite (TCK) promised by re-architecture Phase 3 (seam R2): executable layout
 * invariants of the project-structure write-side, parameterized over a {@link ProjectFileAccessProvider}. The
 * invariants are the contract the layout-reconciliation companion plan builds on:
 *
 * <ul>
 * <li><b>update &#8801; create</b>: updating a project to a configuration yields the same file layout as creating a
 * project at that configuration from scratch;</li>
 * <li><b>reconciling an already-correct project is a no-op</b>: re-applying a project's current configuration
 * produces no revision and changes no files.</li>
 * </ul>
 *
 * A backend (or other storage provider) runs the suite by subclassing and supplying its provider. The suite grows
 * into the full backend TCK (`legend-sdlc-backend-test-suite`) in Phase 4; reconciling structure versions must be
 * certified against these same invariants.
 */
public abstract class LayoutInvariantsTestSuite
{
    protected static final String GROUP_ID = "org.finos.legend.sdlc.test";
    protected static final String ARTIFACT_ID = "layout-invariants";

    /**
     * Create a provider holding an existing, empty project with the given id (whatever "an existing project" means
     * for the backend: an initialized repository, an empty in-memory VCS, ...), such that the project-structure
     * updater can build and update the project's layout through it.
     */
    protected abstract ProjectFileAccessProvider newProviderWithProject(String projectId);

    @Test
    public void testCreateIsDeterministic()
    {
        int latestVersion = ProjectStructure.getLatestProjectStructureVersion();
        Map<String, String> first = buildAndGetFiles("ProjectA", latestVersion);
        Map<String, String> second = buildAndGetFiles("ProjectB", latestVersion);
        Assert.assertEquals(first, relabel(second, "ProjectB", "ProjectA"));
    }

    @Test
    public void testUpdateIsEquivalentToCreate()
    {
        int latestVersion = ProjectStructure.getLatestProjectStructureVersion();
        for (int fromVersion : getUpdateSourceVersions())
        {
            // create a project directly at the latest version
            String createdProjectId = "CreatedProject";
            ProjectFileAccessProvider createdProvider = newProviderWithProject(createdProjectId);
            buildProject(createdProvider, createdProjectId, latestVersion);
            addTestEntity(createdProvider, createdProjectId);
            Map<String, String> created = getFiles(createdProvider, createdProjectId);

            // create a project at an older version, then update it to the latest
            String updatedProjectId = "UpdatedProject";
            ProjectFileAccessProvider updatedProvider = newProviderWithProject(updatedProjectId);
            buildProject(updatedProvider, updatedProjectId, fromVersion);
            addTestEntity(updatedProvider, updatedProjectId);
            ProjectStructureUpdater.newUpdateBuilder(updatedProvider, updatedProjectId,
                            ProjectConfigurationUpdater.newUpdater().withProjectStructureVersion(latestVersion))
                    .withMessage("Update project structure to version " + latestVersion)
                    .update();
            Map<String, String> updated = getFiles(updatedProvider, updatedProjectId);

            Assert.assertEquals("update from version " + fromVersion + " to " + latestVersion + " should produce the layout a fresh version " + latestVersion + " project has",
                    created, relabel(updated, updatedProjectId, createdProjectId));
        }
    }

    @Test
    public void testReconcilingAlreadyCorrectProjectIsNoOp()
    {
        int latestVersion = ProjectStructure.getLatestProjectStructureVersion();
        String projectId = "NoOpProject";
        ProjectFileAccessProvider provider = newProviderWithProject(projectId);
        buildProject(provider, projectId, latestVersion);
        addTestEntity(provider, projectId);
        Map<String, String> before = getFiles(provider, projectId);

        // re-applying the current configuration produces no revision and changes nothing
        Assert.assertNull(ProjectStructureUpdater.newUpdateBuilder(provider, projectId,
                        ProjectConfigurationUpdater.newUpdater().withProjectStructureVersion(latestVersion).withGroupId(GROUP_ID).withArtifactId(ARTIFACT_ID))
                .withMessage("Reconcile already-correct project")
                .update());
        Assert.assertEquals(before, getFiles(provider, projectId));

        // ... and so does an update that specifies nothing at all
        Assert.assertNull(ProjectStructureUpdater.newUpdateBuilder(provider, projectId, ProjectConfigurationUpdater.newUpdater())
                .withMessage("Reconcile already-correct project (empty update)")
                .update());
        Assert.assertEquals(before, getFiles(provider, projectId));
    }

    /**
     * The structure versions used as update starting points in {@link #testUpdateIsEquivalentToCreate}. By default,
     * the two most recent versions before the latest that are implemented in this repository.
     */
    protected int[] getUpdateSourceVersions()
    {
        return new int[] {11, 12};
    }

    protected void buildProject(ProjectFileAccessProvider provider, String projectId, int projectStructureVersion)
    {
        ProjectStructureUpdater.newUpdateBuilder(provider, projectId,
                        ProjectConfigurationUpdater.newUpdater()
                                .withProjectId(projectId)
                                .withProjectType(ProjectType.MANAGED)
                                .withGroupId(GROUP_ID)
                                .withArtifactId(ARTIFACT_ID)
                                .withProjectStructureVersion(projectStructureVersion))
                .withMessage("Build project structure version " + projectStructureVersion)
                .build();
    }

    protected void addTestEntity(ProjectFileAccessProvider provider, String projectId)
    {
        Entity entity = Entity.newEntity("model::domain::TestClass", "meta::pure::metamodel::type::Class",
                Maps.mutable.<String, Object>empty()
                        .withKeyValue("_type", "class")
                        .withKeyValue("name", "TestClass")
                        .withKeyValue("package", "model::domain")
                        .withKeyValue("properties", Collections.emptyList()));
        EntityModificationOperations.performChanges(provider, projectId, SourceSpecification.projectSourceSpecification(), null, "Add test entity",
                Collections.singletonList(EntityChange.newCreateEntity(entity.getPath(), entity.getClassifierPath(), entity.getContent())));
    }

    protected Map<String, String> buildAndGetFiles(String projectId, int projectStructureVersion)
    {
        ProjectFileAccessProvider provider = newProviderWithProject(projectId);
        buildProject(provider, projectId, projectStructureVersion);
        addTestEntity(provider, projectId);
        return getFiles(provider, projectId);
    }

    protected Map<String, String> getFiles(ProjectFileAccessProvider provider, String projectId)
    {
        MutableMap<String, String> files = Maps.mutable.empty();
        provider.getFileAccessContext(projectId, SourceSpecification.projectSourceSpecification(), null)
                .getFiles()
                .forEach(f -> files.put(f.getPath(), new String(f.getContentAsBytes(), StandardCharsets.UTF_8)));
        return files;
    }

    /**
     * Layouts embed the project id (project.json); to compare layouts of two differently-named projects, rewrite
     * occurrences of one project id to the other.
     */
    protected Map<String, String> relabel(Map<String, String> files, String fromProjectId, String toProjectId)
    {
        MutableMap<String, String> result = Maps.mutable.empty();
        files.forEach((path, content) -> result.put(path.replace(fromProjectId, toProjectId), content.replace(fromProjectId, toProjectId)));
        return result;
    }
}
