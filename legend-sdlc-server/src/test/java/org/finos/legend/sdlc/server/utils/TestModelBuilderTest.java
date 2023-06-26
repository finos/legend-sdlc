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

package org.finos.legend.sdlc.server.utils;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.sdlc.domain.model.TestTools;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.depot.model.DepotProjectId;
import org.finos.legend.sdlc.server.depot.model.DepotProjectVersion;
import org.finos.legend.sdlc.server.domain.api.dependency.DependenciesApi;
import org.finos.legend.sdlc.server.domain.api.dependency.DependenciesApiImpl;
import org.finos.legend.sdlc.server.domain.api.test.TestModelBuilder;
import org.finos.legend.sdlc.server.domain.api.project.SourceSpecification;
import org.finos.legend.sdlc.server.inmemory.backend.InMemoryBackend;
import org.finos.legend.sdlc.server.inmemory.backend.metadata.InMemoryMetadataBackend;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class TestModelBuilderTest
{
    private TestModelBuilder testModelBuilder;
    private InMemoryBackend backend;
    private InMemoryMetadataBackend metadata;
    private DependenciesApi dependenciesApi;

    @Before
    public void setUp()
    {
        this.metadata = new InMemoryMetadataBackend();
        this.backend = new InMemoryBackend(this.metadata);
        this.dependenciesApi = new DependenciesApiImpl(this.backend.getProjectApi(), this.backend.getProjectConfigurationApi(), this.backend.getRevisionApi());
        this.testModelBuilder = new TestModelBuilder(this.dependenciesApi, this.backend.getEntityApi(), this.backend.getProjectConfigurationApi(), this.metadata.getMetadataApi());
    }

    @Test
    public void addEntitiesToUpstreamProject()
    {
        /*
            Path from x to y indicates that project x depends on project y
            x(~) indicates that project x has been modified

            org.test:A
             +-- org.test:B(~)
             +-- org.test:C

             Add new entities to project org.test:B
         */
        this.backend.project("PROD-1").addVersionedClasses("1.0.0", "b1", "b2");
        this.backend.project("PROD-1").setMavenCoordinates("org.test", "B");

        this.metadata.project("org.test:A").addVersionedClasses("1.0.0", "a1", "a2");
        this.metadata.project("org.test:B").addVersionedClasses("1.0.0", "b1", "b2");
        this.metadata.project("org.test:C").addVersionedClasses("1.0.0", "c1", "c2");
        this.metadata.project("org.test:A").addDependency("1.0.0", "org.test:B:1.0.0", "org.test:C:1.0.0");

        List<Entity> entitiesBefore = findEntitiesInMetadata("org.test:A", "1.0.0");
        Assert.assertEquals(
                Sets.mutable.with("org.test:A::a1", "org.test:A::a2", "org.test:B::b1", "org.test:B::b2", "org.test:C::c1", "org.test:C::c2"),
                toEntityPathSet(entitiesBefore));

        this.backend.project("PROD-1").addClasses("w1", "b3");

        List<Entity> entitiesAfter = this.testModelBuilder.buildEntitiesForTest(
                "PROD-1",
                "w1",
                revisionId("PROD-1", "w1"),
                "org.test:A",
                "1.0.0"
        );

        Assert.assertEquals(
                Sets.mutable.with("org.test:A::a1", "org.test:A::a2", "PROD-1::b1", "PROD-1::b2", "PROD-1::b3", "org.test:C::c1", "org.test:C::c2"),
                toEntityPathSet(entitiesAfter));

        List<Entity> entitiesAfterForVersion = this.testModelBuilder.buildEntitiesForTest(
                "org.test:B",
                VersionId.parseVersionId("1.0.0"),
                "org.test:A",
                "1.0.0"
        );

        Assert.assertEquals(
                Sets.mutable.with("org.test:A::a1", "org.test:A::a2", "org.test:B::b1", "org.test:B::b2", "org.test:C::c1", "org.test:C::c2"),
                toEntityPathSet(entitiesAfterForVersion));

        List<Entity> entitiesAfterForRevision = this.testModelBuilder.buildEntitiesForTest(
                "PROD-1",
                revisionId("PROD-1"),
                "org.test:A",
                "1.0.0"
        );

        Assert.assertEquals(
                Sets.mutable.with("org.test:A::a1", "org.test:A::a2", "PROD-1::b1", "PROD-1::b2", "org.test:C::c1", "org.test:C::c2"),
                toEntityPathSet(entitiesAfterForRevision));
    }

    @Test
    public void deleteEntitiesFromUpstreamProject()
    {
        /*
            Path from x to y indicates that project x depends on project y
            x(~) indicates that project x has been modified

            org.test:A
             +-- org.test:B(~)
             +-- org.test:C

            Entity b1 is deleted in project org.test:B.
         */

        this.backend.project("PROD-1").addVersionedClasses("1.0.0", "b1", "b2");
        this.backend.project("PROD-1").setMavenCoordinates("org.test", "B");

        this.metadata.project("org.test:A").addVersionedClasses("1.0.0", "a1", "a2");
        this.metadata.project("org.test:B").addVersionedClasses("1.0.0", "b1", "b2");
        this.metadata.project("org.test:C").addVersionedClasses("1.0.0", "c1", "c2");
        this.metadata.project("org.test:A").addDependency("1.0.0","org.test:B:1.0.0", "org.test:C:1.0.0");

        Entity entityB1InVersion1 = this.backend.getEntityApi().getVersionEntityAccessContext("PROD-1", "1.0.0").getEntity("PROD-1::b1");

        List<Entity> entitiesBefore = findEntitiesInMetadata("org.test:A", "1.0.0");
        Assert.assertEquals(
                Sets.mutable.with("org.test:A::a1", "org.test:A::a2", "org.test:B::b1", "org.test:B::b2", "org.test:C::c1", "org.test:C::c2"),
                toEntityPathSet(entitiesBefore));

        this.backend.project("PROD-1")
                .removeEntities("w1", entityB1InVersion1);

        // entity b1 not found in ws1
        try
        {
            this.backend.getEntityApi().getUserWorkspaceEntityAccessContext("PROD-1", "w1").getEntity("PROD-1::b1");
            Assert.fail("Failed to get entity not found exception");
        }
        catch (IllegalStateException e)
        {
            Assert.assertEquals("Entity with path PROD-1::b1 not found", e.getMessage());
        }

        List<Entity> entitiesAfter = this.testModelBuilder.buildEntitiesForTest(
                "PROD-1",
                "w1",
                revisionId("PROD-1", "w1"),
                "org.test:A",
                "1.0.0"
        );

        Assert.assertEquals(
                Sets.mutable.with("org.test:A::a1", "org.test:A::a2", "PROD-1::b2", "org.test:C::c1", "org.test:C::c2"),
                toEntityPathSet(entitiesAfter));

        List<Entity> entitiesAfterForVersion = this.testModelBuilder.buildEntitiesForTest(
                "org.test:B",
                VersionId.parseVersionId("1.0.0"),
                "org.test:A",
                "1.0.0"
        );

        Assert.assertEquals(
                Sets.mutable.with("org.test:A::a1", "org.test:A::a2", "org.test:B::b1", "org.test:B::b2", "org.test:C::c1", "org.test:C::c2"),
                toEntityPathSet(entitiesAfterForVersion));

        List<Entity> entitiesAfterForRevision = this.testModelBuilder.buildEntitiesForTest(
                "PROD-1",
                revisionId("PROD-1"),
                "org.test:A",
                "1.0.0"
        );

        Assert.assertEquals(
                Sets.mutable.with("org.test:A::a1", "org.test:A::a2", "PROD-1::b1", "PROD-1::b2", "org.test:C::c1", "org.test:C::c2"),
                toEntityPathSet(entitiesAfterForRevision));
    }

    @Test
    public void releaseNewVersionOfUpstreamProject()
    {
        /*
            Path from x to y indicates that project x depends on project y
            x(~) indicates that project x has been modified

            org.test:A
             +-- org.test:B(~)
             +-- org.test:C

             Add new entities to project org.test:B and release new version
         */
        this.backend.project("PROD-1").addVersionedClasses("1.0.0", "b1", "b2");
        this.backend.project("PROD-1").setMavenCoordinates("org.test", "B");

        this.metadata.project("org.test:A").addVersionedClasses("1.0.0", "a1", "a2");
        this.metadata.project("org.test:B").addVersionedClasses("1.0.0", "b1", "b2");
        this.metadata.project("org.test:C").addVersionedClasses("1.0.0", "c1", "c2");
        this.metadata.project("org.test:A").addDependency("1.0.0", "org.test:B:1.0.0", "org.test:C:1.0.0");

        List<Entity> entitiesBefore = findEntitiesInMetadata("org.test:A", "1.0.0");
        Assert.assertEquals(
                Sets.mutable.with("org.test:A::a1", "org.test:A::a2", "org.test:B::b1", "org.test:B::b2", "org.test:C::c1", "org.test:C::c2"),
                toEntityPathSet(entitiesBefore));

        this.backend.project("PROD-1").addVersionedClasses("2.0.0", "b3");
        this.metadata.project("org.test:B").addVersionedClasses("2.0.0", "b3");

        List<Entity> entitiesAfterForNewVersion = this.testModelBuilder.buildEntitiesForTest(
                "org.test:B",
                VersionId.parseVersionId("2.0.0"),
                "org.test:A",
                "1.0.0"
        );

        Assert.assertEquals(
                Sets.mutable.with("org.test:A::a1", "org.test:A::a2", "org.test:B::b1", "org.test:B::b2", "org.test:B::b3", "org.test:C::c1", "org.test:C::c2"),
                toEntityPathSet(entitiesAfterForNewVersion));

        List<Entity> entitiesAfterForRevision = this.testModelBuilder.buildEntitiesForTest(
                "PROD-1",
                revisionId("PROD-1"),
                "org.test:A",
                "1.0.0"
        );

        Assert.assertEquals(
                Sets.mutable.with("org.test:A::a1", "org.test:A::a2", "PROD-1::b1", "PROD-1::b2", "PROD-1::b3", "org.test:C::c1", "org.test:C::c2"),
                toEntityPathSet(entitiesAfterForRevision));
    }

    @Test
    public void modifyEntitiesInUpstreamProject()
    {
        /*
            Path from x to y indicates that project x depends on project y
            x(~) indicates that project x has been modified

            org.test:A
             +-- org.test:B(~)
             +-- org.test:C

            Entity b1 is modified in project org.test:B.
            Result should include the latest version of b1.
         */
        this.backend.project("PROD-1").addVersionedEntities("1.0.0", TestTools.newClassEntity("b1", "PROD-1", Collections.singletonList(TestTools.newProperty("prop1", "Integer", 1, 1))));
        this.backend.project("PROD-1").setMavenCoordinates("org.test", "B");


        this.metadata.project("org.test:A").addVersionedClasses("1.0.0", "a1", "a2");
        this.metadata.project("org.test:B")
                .addVersionedEntities("1.0.0", TestTools.newClassEntity("b1", "org.test:B", Collections.singletonList(TestTools.newProperty("prop1", "Integer", 1, 1))));
        this.metadata.project("org.test:C").addVersionedClasses("1.0.0", "c1", "c2");
        this.metadata.project("org.test:A").addDependency("1.0.0", "org.test:B:1.0.0", "org.test:C:1.0.0");

        Entity entityB1InVersion1 = this.backend.getEntityApi().getVersionEntityAccessContext("PROD-1", "1.0.0").getEntity("PROD-1::b1");

        List<Entity> entitiesBefore = findEntitiesInMetadata("org.test:A", "1.0.0");
        Assert.assertEquals(
                Sets.mutable.with("org.test:A::a1", "org.test:A::a2", "org.test:B::b1", "org.test:C::c1", "org.test:C::c2"),
                toEntityPathSet(entitiesBefore));

        this.backend.project("PROD-1")
                .addEntities("w1", WorkspaceType.GROUP, TestTools.newClassEntity("b1", "PROD-1", Collections.singletonList(TestTools.newProperty("prop1", "Integer", 1, 100))));

        Entity entityB1InWorkspace = this.backend.getEntityApi().getGroupWorkspaceEntityAccessContext("PROD-1", "w1").getEntity("PROD-1::b1");

        Assert.assertNotEquals("Version of the entity PROD-1::b1 in 1.0.0 is the same as in workspace ws1", entityB1InVersion1.getContent(), entityB1InWorkspace.getContent());

        MutableSet<String> expected = Sets.mutable.with("org.test:A::a1", "org.test:A::a2", "PROD-1::b1", "org.test:C::c1", "org.test:C::c2");

        List<Entity> entitiesAfter = this.testModelBuilder.buildEntitiesForTest(
                "PROD-1",
                "w1",
                WorkspaceType.GROUP,
                revisionId("PROD-1", "w1", WorkspaceType.GROUP),
                "org.test:A",
                "1.0.0"
        );

        Assert.assertEquals(expected, toEntityPathSet(entitiesAfter));

        List<Entity> entitiesAfterForVersion = this.testModelBuilder.buildEntitiesForTest(
                "org.test:B",
                VersionId.parseVersionId("1.0.0"),
                "org.test:A",
                "1.0.0"
        );

        Assert.assertEquals(Sets.mutable.with("org.test:A::a1", "org.test:A::a2", "org.test:B::b1", "org.test:C::c1", "org.test:C::c2"),
                toEntityPathSet(entitiesAfterForVersion));

        Entity entityB1InResult = Iterate.detect(entitiesAfter, e -> "PROD-1::b1".equals(e.getPath()));

        Assert.assertEquals("Transitive closure did not return the latest version of PROD-1::b1 from workspace w1", entityB1InWorkspace.getContent(), entityB1InResult.getContent());

        List<Entity> entitiesAfterForRevision = this.testModelBuilder.buildEntitiesForTest(
                "PROD-1",
                revisionId("PROD-1"),
                "org.test:A",
                "1.0.0"
        );

        Assert.assertEquals(
                Sets.mutable.with("org.test:A::a1", "org.test:A::a2", "PROD-1::b1", "org.test:C::c1", "org.test:C::c2"),
                toEntityPathSet(entitiesAfterForRevision));
    }


    @Test
    public void diamondDependencyTree()
    {
        /*
            Path from x to y indicates that project x depends on project y
            x(~) indicates that project x has been modified
            x(*) refers to project x that has been defined elsewhere in the tree

            org.test:A
             +-- org.test:B(~)
             |   +-- org.test:D
             |       +-- org.test:E
             +-- org.test:C
                 +-- org.test:D*
        */
        this.backend.project("PROD-1").addVersionedClasses("1.0.0", "b1", "b2");
        this.backend.project("PROD-1").setMavenCoordinates("org.test", "B");

        this.metadata.project("org.test:A").addVersionedClasses("1.0.0", "a1", "a2");
        this.metadata.project("org.test:B").addVersionedClasses("1.0.0", "b1", "b2");
        this.metadata.project("org.test:C").addVersionedClasses("1.0.0", "c1", "c2");
        this.metadata.project("org.test:D").addVersionedClasses("1.0.0", "d1", "d2");
        this.metadata.project("org.test:E").addVersionedClasses("1.0.0", "e1", "e2");
        this.metadata.project("org.test:A").addDependency("1.0.0", "org.test:B:1.0.0");
        this.metadata.project("org.test:A").addDependency("1.0.0", "org.test:C:1.0.0");
        this.metadata.project("org.test:B").addDependency("1.0.0", "org.test:D:1.0.0");
        this.metadata.project("org.test:C").addDependency("1.0.0", "org.test:D:1.0.0");
        this.metadata.project("org.test:D").addDependency("1.0.0", "org.test:E:1.0.0");

        List<Entity> entitiesBefore = findEntitiesInMetadata("org.test:A", "1.0.0");
        Assert.assertEquals(
                Sets.mutable.with("org.test:A::a1", "org.test:A::a2", "org.test:B::b1", "org.test:B::b2", "org.test:C::c1", "org.test:C::c2", "org.test:D::d1", "org.test:D::d2", "org.test:E::e1", "org.test:E::e2"),
                toEntityPathSet(entitiesBefore));

        this.backend.project("PROD-1").addClasses("w1", "b3");

        List<Entity> entitiesAfter = this.testModelBuilder.buildEntitiesForTest(
                "PROD-1",
                "w1",
                revisionId("PROD-1", "w1"),
                "org.test:A",
                "1.0.0"
        );

        Assert.assertEquals(
                Sets.mutable.with("org.test:A::a1", "org.test:A::a2", "PROD-1::b1", "PROD-1::b2", "PROD-1::b3", "org.test:C::c1", "org.test:C::c2", "org.test:D::d1", "org.test:D::d2", "org.test:E::e1", "org.test:E::e2"),
                toEntityPathSet(entitiesAfter));

        List<Entity> entitiesAfterForVersion = this.testModelBuilder.buildEntitiesForTest(
                "org.test:B",
                VersionId.parseVersionId("1.0.0"),
                "org.test:A",
                "1.0.0"
        );

        Assert.assertEquals(
                Sets.mutable.with("org.test:A::a1", "org.test:A::a2", "org.test:B::b1", "org.test:B::b2", "org.test:C::c1", "org.test:C::c2", "org.test:D::d1", "org.test:D::d2", "org.test:E::e1", "org.test:E::e2"),
                toEntityPathSet(entitiesAfterForVersion));

        List<Entity> entitiesAfterForRevision = this.testModelBuilder.buildEntitiesForTest(
                "PROD-1",
                revisionId("PROD-1"),
                "org.test:A",
                "1.0.0"
        );

        Assert.assertEquals(
                Sets.mutable.with("org.test:A::a1", "org.test:A::a2", "PROD-1::b1", "PROD-1::b2", "org.test:C::c1", "org.test:C::c2", "org.test:D::d1", "org.test:D::d2", "org.test:E::e1", "org.test:E::e2"),
                toEntityPathSet(entitiesAfterForRevision));
    }

    @Test
    public void dependencyAddedToUpstreamProject()
    {
        /*
            Path from x to y indicates that project x depends on project y
            x(~) indicates that project x has been modified
            x(*) refers to project x that has been defined elsewhere in the tree

            org.test:A
             +-- org.test:B(~)
                 +-- org.test:C
            org.test:D

            Dependency org.test:D is added to project org.test:B
         */
        this.backend.project("PROD-1").addVersionedClasses("1.0.0", "b1", "b2");
        this.backend.project("PROD-1").setMavenCoordinates("org.test", "B");

        this.metadata.project("org.test:A").addVersionedClasses("1.0.0", "a1", "a2");
        this.metadata.project("org.test:B").addVersionedClasses("1.0.0", "b1", "b2");
        this.metadata.project("org.test:C").addVersionedClasses("1.0.0", "c1", "c2");
        this.metadata.project("org.test:D").addVersionedClasses("1.0.0", "d1", "d2");
        this.metadata.project("org.test:A").addDependency("1.0.0", "org.test:B:1.0.0");
        this.metadata.project("org.test:B").addDependency("1.0.0", "org.test:C:1.0.0");

        this.backend.project("PROD-1").addDependency("org.test:C:1.0.0");

        List<Entity> entitiesBefore = findEntitiesInMetadata("org.test:A", "1.0.0");
        Assert.assertEquals(
                Sets.mutable.with("org.test:A::a1", "org.test:A::a2", "org.test:B::b1", "org.test:B::b2", "org.test:C::c1", "org.test:C::c2"),
                toEntityPathSet(entitiesBefore));

        this.backend.project("PROD-1").addProjectDependency("w1", "org.test:D:1.0.0");

        List<Entity> entitiesAfter = this.testModelBuilder.buildEntitiesForTest(
                "PROD-1",
                "w1",
                revisionId("PROD-1", "w1"),
                "org.test:A",
                "1.0.0"
        );

        Assert.assertEquals(Sets.mutable.with("org.test:A::a1", "org.test:A::a2", "PROD-1::b1", "PROD-1::b2", "org.test:C::c1", "org.test:C::c2", "org.test:D::d1", "org.test:D::d2"),
                toEntityPathSet(entitiesAfter));

        List<Entity> entitiesAfterForVersion = this.testModelBuilder.buildEntitiesForTest(
                "org.test:B",
                VersionId.parseVersionId("1.0.0"),
                "org.test:A",
                "1.0.0"
        );

        Assert.assertEquals(Sets.mutable.with("org.test:A::a1", "org.test:A::a2", "org.test:B::b1", "org.test:B::b2", "org.test:C::c1", "org.test:C::c2"),
                toEntityPathSet(entitiesAfterForVersion));

        List<Entity> entitiesAfterForRevision = this.testModelBuilder.buildEntitiesForTest(
                "PROD-1",
                revisionId("PROD-1"),
                "org.test:A",
                "1.0.0"
        );

        Assert.assertEquals(
                Sets.mutable.with("org.test:A::a1", "org.test:A::a2", "PROD-1::b1", "PROD-1::b2", "org.test:C::c1", "org.test:C::c2", "org.test:D::d1", "org.test:D::d2"),
                toEntityPathSet(entitiesAfterForRevision));
    }

    @Test
    public void dependencyRemovedFromUpstreamProject()
    {
        /*
            Path from x to y indicates that project x depends on project y
            x(~) indicates that project x has been modified
            x(*) refers to project x that has been defined elsewhere in the tree

            org.test:A
             +-- org.test:B(~)
             |   +-- org.test:C
             +-- org.test:D

            Dependency org.test:C is removed from project org.test:B
         */
        this.backend.project("PROD-1").addVersionedClasses("1.0.0", "b1", "b2");
        this.backend.project("PROD-1").setMavenCoordinates("org.test", "B");

        this.metadata.project("org.test:A").addVersionedClasses("1.0.0", "a1", "a2");
        this.metadata.project("org.test:B").addVersionedClasses("1.0.0", "b1", "b2");
        this.metadata.project("org.test:C").addVersionedClasses("1.0.0", "c1", "c2");
        this.metadata.project("org.test:D").addVersionedClasses("1.0.0", "d1", "d2");
        this.metadata.project("org.test:A").addDependency("1.0.0", "org.test:B:1.0.0");
        this.metadata.project("org.test:A").addDependency("1.0.0", "org.test:D:1.0.0");
        this.metadata.project("org.test:B").addDependency("1.0.0", "org.test:C:1.0.0");

        this.backend.project("PROD-1").addDependency("org.test:C:1.0.0");

        List<Entity> entitiesBefore = findEntitiesInMetadata("org.test:A", "1.0.0");
        Assert.assertEquals(
                Sets.mutable.with("org.test:A::a1", "org.test:A::a2", "org.test:B::b1", "org.test:B::b2", "org.test:C::c1", "org.test:C::c2", "org.test:D::d1", "org.test:D::d2"),
                toEntityPathSet(entitiesBefore));

        this.backend.project("PROD-1").removeProjectDependency("w1", "org.test:C:1.0.0");

        List<Entity> entitiesAfter = this.testModelBuilder.buildEntitiesForTest(
                "PROD-1",
                "w1",
                revisionId("PROD-1", "w1"),
                "org.test:A",
                "1.0.0"
        );

        Assert.assertEquals(Sets.mutable.with("org.test:A::a1", "org.test:A::a2", "PROD-1::b1", "PROD-1::b2", "org.test:D::d1", "org.test:D::d2"),
                toEntityPathSet(entitiesAfter));

        List<Entity> entitiesAfterForVersion = this.testModelBuilder.buildEntitiesForTest(
                "org.test:B",
                VersionId.parseVersionId("1.0.0"),
                "org.test:A",
                "1.0.0"
        );

        Assert.assertEquals(Sets.mutable.with("org.test:A::a1", "org.test:A::a2", "org.test:B::b1", "org.test:B::b2", "org.test:C::c1", "org.test:C::c2", "org.test:D::d1", "org.test:D::d2"),
                toEntityPathSet(entitiesAfterForVersion));

        List<Entity> entitiesAfterForRevision = this.testModelBuilder.buildEntitiesForTest(
                "PROD-1",
                revisionId("PROD-1"),
                "org.test:A",
                "1.0.0"
        );

        Assert.assertEquals(
                Sets.mutable.with("org.test:A::a1", "org.test:A::a2", "PROD-1::b1", "PROD-1::b2", "org.test:D::d1", "org.test:D::d2"),
                toEntityPathSet(entitiesAfterForRevision));
    }

    @Test
    public void sharedDependencyRemoved()
    {
        /*
            Path from x to y indicates that project x depends on project y
            x(~) indicates that project x has been modified
            x(*) refers to project x that has been defined elsewhere in the tree

            org.test:A
             +-- org.test:B(~)
             |   +-- org.test:C
             |       +-- org.test:D
             +-- org.test:C(*)
             +-- org.test:E

             Project org.test:C is common dependency of both org.test:A and org.test:B
             Project org.test:C is removed as a dependency of project org.test:B
             We still see org.test:C in the result via the direct org.test:A to org.test:C dependency

         */
        this.backend.project("PROD-1").addVersionedClasses("1.0.0", "b1", "b2");
        this.backend.project("PROD-1").setMavenCoordinates("org.test", "B");

        this.metadata.project("org.test:A").addVersionedClasses("1.0.0", "a1", "a2");
        this.metadata.project("org.test:B").addVersionedClasses("1.0.0", "b1", "b2");
        this.metadata.project("org.test:C").addVersionedClasses("1.0.0", "c1", "c2");
        this.metadata.project("org.test:D").addVersionedClasses("1.0.0", "d1", "d2");
        this.metadata.project("org.test:E").addVersionedClasses("1.0.0", "e1", "e2");
        this.metadata.project("org.test:A").addDependency("1.0.0", "org.test:B:1.0.0");
        this.metadata.project("org.test:A").addDependency("1.0.0", "org.test:C:1.0.0");
        this.metadata.project("org.test:A").addDependency("1.0.0", "org.test:E:1.0.0");
        this.metadata.project("org.test:B").addDependency("1.0.0", "org.test:C:1.0.0");
        this.metadata.project("org.test:C").addDependency("1.0.0", "org.test:D:1.0.0");

        this.backend.project("PROD-1").addDependency("org.test:C:1.0.0");

        List<Entity> entitiesBefore = findEntitiesInMetadata("org.test:A", "1.0.0");
        Assert.assertEquals(
                Sets.mutable.with("org.test:A::a1", "org.test:A::a2", "org.test:B::b1", "org.test:B::b2", "org.test:C::c1", "org.test:C::c2", "org.test:D::d1", "org.test:D::d2", "org.test:E::e1", "org.test:E::e2"),
                toEntityPathSet(entitiesBefore));

        this.backend.project("PROD-1").removeProjectDependency("w1", "org.test:C:1.0.0");

        List<Entity> entitiesAfter = this.testModelBuilder.buildEntitiesForTest(
                "PROD-1",
                "w1",
                revisionId("PROD-1", "w1"),
                "org.test:A",
                "1.0.0"
        );

        Assert.assertEquals(Sets.mutable.with("org.test:A::a1", "org.test:A::a2", "PROD-1::b1", "PROD-1::b2", "org.test:C::c1", "org.test:C::c2", "org.test:D::d1", "org.test:D::d2", "org.test:E::e1", "org.test:E::e2"),
                toEntityPathSet(entitiesAfter));

        List<Entity> entitiesAfterForVersion = this.testModelBuilder.buildEntitiesForTest(
                "org.test:B",
                VersionId.parseVersionId("1.0.0"),
                "org.test:A",
                "1.0.0"
        );

        Assert.assertEquals(Sets.mutable.with("org.test:A::a1", "org.test:A::a2", "org.test:B::b1", "org.test:B::b2", "org.test:C::c1", "org.test:C::c2", "org.test:D::d1", "org.test:D::d2", "org.test:E::e1", "org.test:E::e2"),
                toEntityPathSet(entitiesAfterForVersion));

        List<Entity> entitiesAfterForRevision = this.testModelBuilder.buildEntitiesForTest(
                "PROD-1",
                revisionId("PROD-1"),
                "org.test:A",
                "1.0.0"
        );

        Assert.assertEquals(
                Sets.mutable.with("org.test:A::a1", "org.test:A::a2", "PROD-1::b1", "PROD-1::b2", "org.test:C::c1", "org.test:C::c2", "org.test:D::d1", "org.test:D::d2", "org.test:E::e1", "org.test:E::e2"),
                toEntityPathSet(entitiesAfterForRevision));
    }

    @Test
    public void sharedDependencyRemovedSecondScenario()
    {
        /*
            Path from x to y indicates that project x depends on project y
            x(~) indicates that project x has been modified
            x(*) refers to project x that has been defined elsewhere in the tree

            org.test:A
             +-- org.test:B
             |   +-- org.test:C
             +-- org.test:C(~)

             Project org.test:C is common dependency of both org.test:A and org.test:B
             Project org.test:C is removed as a dependency of project org.test:B
         */
        this.backend.project("PROD-1").addVersionedClasses("1.0.0", "c1", "c2");
        this.backend.project("PROD-1").setMavenCoordinates("org.test", "C");

        this.metadata.project("org.test:A").addVersionedClasses("1.0.0", "a1", "a2");
        this.metadata.project("org.test:B").addVersionedClasses("1.0.0", "b1", "b2");
        this.metadata.project("org.test:C").addVersionedClasses("1.0.0", "c1", "c2");
        this.metadata.project("org.test:A").addDependency("1.0.0", "org.test:B:1.0.0");
        this.metadata.project("org.test:A").addDependency("1.0.0", "org.test:C:1.0.0");
        this.metadata.project("org.test:B").addDependency("1.0.0", "org.test:C:1.0.0");

        List<Entity> entitiesBefore = findEntitiesInMetadata("org.test:C", "1.0.0");
        Assert.assertEquals(Sets.mutable.with("org.test:C::c1", "org.test:C::c2"), toEntityPathSet(entitiesBefore));

        this.backend.project("PROD-1").addClasses("w1", "c3");

        List<Entity> entitiesAfter = this.testModelBuilder.buildEntitiesForTest(
                "PROD-1",
                "w1",
                revisionId("PROD-1", "w1"),
                "org.test:A",
                "1.0.0"
        );

        Assert.assertEquals(Sets.mutable.with("org.test:A::a1", "org.test:A::a2", "PROD-1::c1", "PROD-1::c2", "PROD-1::c3", "org.test:B::b1", "org.test:B::b2"),
                toEntityPathSet(entitiesAfter));
    }

    @Test
    public void sharedDependencyWithDifferingVersions()
    {
        /*
            Path from x to y indicates that project x depends on project y
            x(~) indicates that project x has been modified
            x(*) refers to project x that has been defined elsewhere in the tree

            org.test:A
             +-- org.test:B(~)
             |   +-- org.test:C
             +-- org.test:C(*)
             +-- org.test:D

             Projects org.test:A and org.test:B depend on Project org.test:C version 1.0.0
             Project org.test:B is modified to depend on Project org.test:C version 2.0.0
             We should see Project org.test:C version 2.0.0 in the result (i.e the version of org.test:C used by the upstream project overrides the version used by the downstream project)
         */
        this.backend.project("PROD-1").addVersionedClasses("1.0.0", "b1", "b2");
        this.backend.project("PROD-1").setMavenCoordinates("org.test", "B");

        this.metadata.project("org.test:A").addVersionedClasses("1.0.0", "a1", "a2");
        this.metadata.project("org.test:B").addVersionedClasses("1.0.0", "b1", "b2");
        this.metadata.project("org.test:C").addVersionedClasses("1.0.0", "c1", "c2");
        // Note :  Version org.test:C:2.0.0 builds on top of Version org.test:C:1.0.0 by adding 2 new entities
        this.metadata.project("org.test:C").addVersionedClasses("2.0.0", "c3", "c4");
        this.metadata.project("org.test:D").addVersionedClasses("1.0.0", "d1", "d2");
        this.metadata.project("org.test:A").addDependency("1.0.0", "org.test:B:1.0.0");
        this.metadata.project("org.test:A").addDependency("1.0.0", "org.test:C:1.0.0");
        this.metadata.project("org.test:A").addDependency("1.0.0", "org.test:D:1.0.0");
        this.metadata.project("org.test:B").addDependency("1.0.0", "org.test:C:1.0.0");

        this.backend.project("PROD-1").addDependency("org.test:C:1.0.0");

        // before the change, org.test:A depends on version 1.0.0 of org.test:C i.e it has entities c1 and c2
        List<Entity> entitiesBefore = findEntitiesInMetadata("org.test:A", "1.0.0");
        Assert.assertEquals(
                Sets.mutable.with("org.test:A::a1", "org.test:A::a2", "org.test:B::b1", "org.test:B::b2", "org.test:C::c1", "org.test:C::c2", "org.test:D::d1", "org.test:D::d2"),
                toEntityPathSet(entitiesBefore));

        this.backend.project("PROD-1").updateProjectDependency("w1", "org.test:C:1.0.0", "org.test:C:2.0.0");

        List<Entity> entitiesAfter = this.testModelBuilder.buildEntitiesForTest(
                "PROD-1",
                "w1",
                revisionId("PROD-1", "w1"),
                "org.test:A",
                "1.0.0"
        );

        // after the change, org.test:A depends on version 2.0.0 of org.test:C i.e it has entities c1, c2, c3, c4
        Assert.assertEquals(Sets.mutable.with("org.test:A::a1", "org.test:A::a2", "PROD-1::b1", "PROD-1::b2", "org.test:C::c1", "org.test:C::c2", "org.test:C::c3", "org.test:C::c4", "org.test:D::d1", "org.test:D::d2"),
                toEntityPathSet(entitiesAfter));

        List<Entity> entitiesAfterForVersion = this.testModelBuilder.buildEntitiesForTest(
                "org.test:B",
                VersionId.parseVersionId("1.0.0"),
                "org.test:A",
                "1.0.0"
        );

        Assert.assertEquals(Sets.mutable.with("org.test:A::a1", "org.test:A::a2", "org.test:B::b1", "org.test:B::b2", "org.test:C::c1", "org.test:C::c2", "org.test:D::d1", "org.test:D::d2"),
                toEntityPathSet(entitiesAfterForVersion));

        List<Entity> entitiesAfterForRevision = this.testModelBuilder.buildEntitiesForTest(
                "PROD-1",
                revisionId("PROD-1"),
                "org.test:A",
                "1.0.0"
        );

        Assert.assertEquals(
                Sets.mutable.with("org.test:A::a1", "org.test:A::a2", "PROD-1::b1", "PROD-1::b2", "org.test:C::c1", "org.test:C::c2", "org.test:C::c3", "org.test:C::c4", "org.test:D::d1", "org.test:D::d2"),
                toEntityPathSet(entitiesAfterForRevision));
    }

    @Test
    public void testNotDirectDependency()
    {
        /*
            Path from x to y indicates that project x depends on project y
            x(~) indicates that project x has been modified

            org.test:A
             +-- org.test:B
                 +-- org.test:C
                    +-- org.test:D(~)
         */
        this.backend.project("PROD-1").addVersionedClasses("1.0.0", "d1", "d2");
        this.backend.project("PROD-1").setMavenCoordinates("org.test", "D");

        this.metadata.project("org.test:A").addVersionedClasses("1.0.0", "a1", "a2");
        this.metadata.project("org.test:B").addVersionedClasses("1.0.0", "b1", "b2");
        this.metadata.project("org.test:C").addVersionedClasses("1.0.0", "c1", "c2");
        this.metadata.project("org.test:D").addVersionedClasses("1.0.0", "d1", "d2");
        this.metadata.project("org.test:A").addDependency("1.0.0", "org.test:B:1.0.0");
        this.metadata.project("org.test:B").addDependency("1.0.0", "org.test:C:1.0.0");
        this.metadata.project("org.test:C").addDependency("1.0.0", "org.test:D:1.0.0");

        List<Entity> entitiesBefore = findEntitiesInMetadata("org.test:D", "1.0.0");
        Assert.assertEquals(Sets.mutable.with("org.test:D::d1", "org.test:D::d2"), toEntityPathSet(entitiesBefore));

        this.backend.project("PROD-1").addClasses("w1", "d3");

        List<Entity> entitiesAfter = this.testModelBuilder.buildEntitiesForTest(
                "PROD-1",
                "w1",
                revisionId("PROD-1", "w1"),
                "org.test:A",
                "1.0.0"
        );

        Assert.assertEquals(Sets.mutable.with("org.test:A::a1", "org.test:A::a2", "PROD-1::d1", "PROD-1::d2", "PROD-1::d3", "org.test:B::b1", "org.test:B::b2", "org.test:C::c1", "org.test:C::c2"),
                toEntityPathSet(entitiesAfter));
    }

    @Test
    public void testTestModelBuilderWithWrongInput()
    {
        /*
            Path from x to y indicates that project x depends on project y

            org.test:A
             +-- org.test:B
                 +-- org.test:C
                     +-- org.test:D
         */
        this.metadata.project("org.test:A").addVersionedClasses("1.0.0", "a1", "a2");
        this.metadata.project("org.test:B").addVersionedClasses("1.0.0", "b1", "b2");
        this.metadata.project("org.test:C").addVersionedClasses("1.0.0", "c1", "c2");
        this.metadata.project("org.test:D").addVersionedClasses("1.0.0", "d1", "d2");

        this.metadata.project("org.test:A").addDependency("1.0.0", "org.test:B:1.0.0");
        this.metadata.project("org.test:B").addDependency("1.0.0", "org.test:C:1.0.0");
        this.metadata.project("org.test:C").addDependency("1.0.0", "org.test:D:1.0.0");

        this.testModelBuilder.buildEntitiesForTest(
                "org.test:B",
                VersionId.parseVersionId("1.0.0"),
                "org.test:A",
                "1.0.0"
        );

        try
        {
            this.testModelBuilder.buildEntitiesForTest(
                    "org.test:A",
                    VersionId.parseVersionId("1.0.0"),
                    "org.test:B",
                    "1.0.0"
            );
        }
        catch (Exception ex)
        {
            Assert.assertEquals("Error processing dependencies: Project org.test:B was specified as downstream but in fact it is a direct dependency for upstream project org.test:A", ex.getMessage());
        }

        try
        {
            this.testModelBuilder.buildEntitiesForTest(
                    "org.test:A",
                    VersionId.parseVersionId("1.0.0"),
                    "org.test:C",
                    "1.0.0"
            );
        }
        catch (Exception ex)
        {
            Assert.assertEquals("Error processing dependencies: Project org.test:C was specified as downstream but in fact it is an indirect dependency for upstream project org.test:A", ex.getMessage());
        }
    }

    private String revisionId(String projectId, String workspaceId)
    {
        return this.backend.getRevisionApi().getUserWorkspaceRevisionContext(projectId, workspaceId).getCurrentRevision().getId();
    }

    private String revisionId(String projectId, String workspaceId, WorkspaceType type)
    {
        return this.backend.getRevisionApi().getWorkspaceRevisionContext(projectId, SourceSpecification.newSourceSpecification(workspaceId, type)).getCurrentRevision().getId();
    }

    private String revisionId(String projectId)
    {
        return this.backend.getRevisionApi().getProjectRevisionContext(projectId).getCurrentRevision().getId();
    }

    private List<Entity> findEntitiesInMetadata(String projectId, String versionId)
    {
        DepotProjectId depotProjectId = DepotProjectId.parseProjectId(projectId);
        Set<DepotProjectVersion> dependencies = this.metadata.getMetadataApi().getProjectDependencies(depotProjectId, versionId, true);
        List<Entity> entities = Lists.mutable.withAll(this.metadata.getMetadataApi().getEntities(depotProjectId, versionId));
        dependencies.stream().map(d -> this.metadata.getMetadataApi().getEntities(d.getDepotProjectId(), d.getVersionId())).forEach(entities::addAll);
        return entities;
    }

    private MutableSet<String> toEntityPathSet(Iterable<? extends Entity> entities)
    {
        return Iterate.collect(entities, Entity::getPath, Sets.mutable.empty());
    }
}
