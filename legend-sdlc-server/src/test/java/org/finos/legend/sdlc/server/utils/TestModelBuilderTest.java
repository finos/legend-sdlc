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
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.domain.api.dependency.DependenciesApi;
import org.finos.legend.sdlc.server.domain.api.dependency.DependenciesApiImpl;
import org.finos.legend.sdlc.server.domain.api.test.TestModelBuilder;
import org.finos.legend.sdlc.server.inmemory.backend.InMemoryBackend;
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
    private DependenciesApi dependenciesApi;

    @Before
    public void setUp()
    {
        this.backend = new InMemoryBackend();
        this.dependenciesApi = new DependenciesApiImpl(this.backend.getProjectApi(), this.backend.getProjectConfigurationApi(), this.backend.getRevisionApi());
        this.testModelBuilder = new TestModelBuilder(this.dependenciesApi, this.backend.getEntityApi());
    }

    @Test
    public void addEntitiesToUpstreamProject()
    {
        /*
            Path from x to y indicates that project x depends on project y
            x(~) indicates that project x has been modified

            A
             +-- B(~)
             +-- C

             Add new entities to project B
         */

        this.backend.project("A").addVersionedClasses("1.0.0", "a1", "a2");
        this.backend.project("B").addVersionedClasses("1.0.0", "b1", "b2");
        this.backend.project("C").addVersionedClasses("1.0.0", "c1", "c2");
        this.backend.project("A").addDependency("B:1.0.0", "C:1.0.0");

        List<Entity> entitiesBefore = findVersionEntities("A", "1.0.0");
        Assert.assertEquals(
                Sets.mutable.with("A::a1", "A::a2", "B::b1", "B::b2", "C::c1", "C::c2"),
                toEntityPathSet(entitiesBefore));

        this.backend.project("B").addClasses("w1", "b3");

        List<Entity> entitiesAfter = this.testModelBuilder.buildEntitiesForTest(
                "B",
                "w1",
                revisionId("B", "w1"),
                "A",
                revisionId("A")
        );

        Assert.assertEquals(
                Sets.mutable.with("A::a1", "A::a2", "B::b1", "B::b2", "B::b3", "C::c1", "C::c2"),
                toEntityPathSet(entitiesAfter));

        List<Entity> entitiesAfterForUserWorkspace = this.testModelBuilder.buildEntitiesForTest(
                "B",
                "w1",
                WorkspaceType.USER,
                revisionId("B", "w1"),
                "A",
                revisionId("A")
        );

        Assert.assertEquals(
                Sets.mutable.with("A::a1", "A::a2", "B::b1", "B::b2", "B::b3", "C::c1", "C::c2"),
                toEntityPathSet(entitiesAfterForUserWorkspace));

        List<Entity> entitiesAfterForVersion = this.testModelBuilder.buildEntitiesForTest(
                "B",
                VersionId.parseVersionId("1.0.0"),
                "A",
                revisionId("A")
        );

        Assert.assertEquals(
                Sets.mutable.with("A::a1", "A::a2", "B::b1", "B::b2", "C::c1", "C::c2"),
                toEntityPathSet(entitiesAfterForVersion));

        List<Entity> entitiesAfterForRevision = this.testModelBuilder.buildEntitiesForTest(
                "B",
                revisionId("B"),
                "A",
                revisionId("A")
        );

        Assert.assertEquals(
                Sets.mutable.with("A::a1", "A::a2", "B::b1", "B::b2", "C::c1", "C::c2"),
                toEntityPathSet(entitiesAfterForRevision));
    }

    @Test
    public void deleteEntitiesFromUpstreamProject()
    {
        /*
            Path from x to y indicates that project x depends on project y
            x(~) indicates that project x has been modified

            A
             +-- B(~)
             +-- C

            Entity b1 is deleted in project B.
         */

        this.backend.project("A").addVersionedClasses("1.0.0", "a1", "a2");
        this.backend.project("B").addVersionedClasses("1.0.0", "b1", "b2");
        this.backend.project("C").addVersionedClasses("1.0.0", "c1", "c2");
        this.backend.project("A").addDependency("B:1.0.0", "C:1.0.0");

        Entity entityB1InVersion1 = this.backend.getEntityApi().getVersionEntityAccessContext("B", "1.0.0").getEntity("B::b1");

        List<Entity> entitiesBefore = findVersionEntities("A", "1.0.0");
        Assert.assertEquals(
                Sets.mutable.with("A::a1", "A::a2", "B::b1", "B::b2", "C::c1", "C::c2"),
                toEntityPathSet(entitiesBefore));

        this.backend.project("B")
                .removeEntities("w1", entityB1InVersion1);

        // entity b1 not found in ws1
        try
        {
            this.backend.getEntityApi().getUserWorkspaceEntityAccessContext("B", "w1").getEntity("B::b1");
            Assert.fail("Failed to get entity not found exception");
        }
        catch (IllegalStateException e)
        {
            Assert.assertEquals("Entity with path B::b1 not found", e.getMessage());
        }

        List<Entity> entitiesAfter = this.testModelBuilder.buildEntitiesForTest(
                "B",
                "w1",
                revisionId("B", "w1"),
                "A",
                revisionId("A")
        );

        Assert.assertEquals(
                Sets.mutable.with("A::a1", "A::a2", "B::b2", "C::c1", "C::c2"),
                toEntityPathSet(entitiesAfter));

        List<Entity> entitiesAfterForVersion = this.testModelBuilder.buildEntitiesForTest(
                "B",
                VersionId.parseVersionId("1.0.0"),
                "A",
                revisionId("A")
        );

        Assert.assertEquals(
                Sets.mutable.with("A::a1", "A::a2", "B::b1", "B::b2", "C::c1", "C::c2"),
                toEntityPathSet(entitiesAfterForVersion));

        List<Entity> entitiesAfterForRevision = this.testModelBuilder.buildEntitiesForTest(
                "B",
                revisionId("B"),
                "A",
                revisionId("A")
        );

        Assert.assertEquals(
                Sets.mutable.with("A::a1", "A::a2", "B::b1", "B::b2", "C::c1", "C::c2"),
                toEntityPathSet(entitiesAfterForRevision));
    }

    @Test
    public void releaseNewVersionOfUpstreamProject()
    {
        /*
            Path from x to y indicates that project x depends on project y
            x(~) indicates that project x has been modified

            A
             +-- B(~)
             +-- C

             Add new entities to project B and release new version
         */

        this.backend.project("A").addVersionedClasses("1.0.0", "a1", "a2");
        this.backend.project("B").addVersionedClasses("1.0.0", "b1", "b2");
        this.backend.project("C").addVersionedClasses("1.0.0", "c1", "c2");
        this.backend.project("A").addDependency("B:1.0.0", "C:1.0.0");

        List<Entity> entitiesBefore = findVersionEntities("A", "1.0.0");
        Assert.assertEquals(
                Sets.mutable.with("A::a1", "A::a2", "B::b1", "B::b2", "C::c1", "C::c2"),
                toEntityPathSet(entitiesBefore));

        this.backend.project("B").addVersionedClasses("2.0.0", "b3");

        List<Entity> entitiesAfterForNewVersion = this.testModelBuilder.buildEntitiesForTest(
                "B",
                VersionId.parseVersionId("2.0.0"),
                "A",
                revisionId("A")
        );

        Assert.assertEquals(
                Sets.mutable.with("A::a1", "A::a2", "B::b1", "B::b2", "B::b3", "C::c1", "C::c2"),
                toEntityPathSet(entitiesAfterForNewVersion));

        List<Entity> entitiesAfterForRevision = this.testModelBuilder.buildEntitiesForTest(
                "B",
                revisionId("B"),
                "A",
                revisionId("A")
        );

        Assert.assertEquals(
                Sets.mutable.with("A::a1", "A::a2", "B::b1", "B::b2", "B::b3", "C::c1", "C::c2"),
                toEntityPathSet(entitiesAfterForRevision));
    }

    @Test
    public void modifyEntitiesInUpstreamProject()
    {
        /*
            Path from x to y indicates that project x depends on project y
            x(~) indicates that project x has been modified

            A
             +-- B(~)
             +-- C

            Entity b1 is modified in project B.
            Result should include the latest version of b1.
         */

        this.backend.project("A").addVersionedClasses("1.0.0", "a1", "a2");
        this.backend.project("B")
                .addVersionedEntities("1.0.0", TestTools.newClassEntity("b1", "B", Collections.singletonList(TestTools.newProperty("prop1", "Integer", 1, 1))));
        this.backend.project("C").addVersionedClasses("1.0.0", "c1", "c2");
        this.backend.project("A").addDependency("B:1.0.0", "C:1.0.0");

        Entity entityB1InVersion1 = this.backend.getEntityApi().getVersionEntityAccessContext("B", "1.0.0").getEntity("B::b1");

        List<Entity> entitiesBefore = findVersionEntities("A", "1.0.0");
        Assert.assertEquals(
                Sets.mutable.with("A::a1", "A::a2", "B::b1", "C::c1", "C::c2"),
                toEntityPathSet(entitiesBefore));

        this.backend.project("B")
                .addEntities("w1", WorkspaceType.GROUP, TestTools.newClassEntity("b1", "B", Collections.singletonList(TestTools.newProperty("prop1", "Integer", 1, 100))));

        Entity entityB1InWorkspace = this.backend.getEntityApi().getGroupWorkspaceEntityAccessContext("B", "w1").getEntity("B::b1");

        Assert.assertNotEquals("Version of the entity B::b1 in 1.0.0 is the same as in workspace ws1", entityB1InVersion1.getContent(), entityB1InWorkspace.getContent());

        MutableSet<String> expected = Sets.mutable.with("A::a1", "A::a2", "B::b1", "C::c1", "C::c2");

        List<Entity> entitiesAfter = this.testModelBuilder.buildEntitiesForTest(
                "B",
                "w1",
                WorkspaceType.GROUP,
                revisionId("B", "w1", WorkspaceType.GROUP),
                "A",
                revisionId("A")
        );

        Assert.assertEquals(expected, toEntityPathSet(entitiesAfter));

        List<Entity> entitiesAfterForVersion = this.testModelBuilder.buildEntitiesForTest(
                "B",
                VersionId.parseVersionId("1.0.0"),
                "A",
                revisionId("A")
        );

        Assert.assertEquals(expected, toEntityPathSet(entitiesAfterForVersion));

        Entity entityB1InResult = Iterate.detect(entitiesAfter, e -> "B::b1".equals(e.getPath()));

        Assert.assertEquals("Transitive closure did not return the latest version of B::b1 from workspace w1", entityB1InWorkspace.getContent(), entityB1InResult.getContent());

        List<Entity> entitiesAfterForRevision = this.testModelBuilder.buildEntitiesForTest(
                "B",
                revisionId("B"),
                "A",
                revisionId("A")
        );

        Assert.assertEquals(
                Sets.mutable.with("A::a1", "A::a2", "B::b1", "C::c1", "C::c2"),
                toEntityPathSet(entitiesAfterForRevision));
    }


    @Test
    public void diamondDependencyTree()
    {
        /*
            Path from x to y indicates that project x depends on project y
            x(~) indicates that project x has been modified
            x(*) refers to project x that has been defined elsewhere in the tree

            A
             +-- B(~)
             |   +-- D
             |       +-- E
             +-- C
                 +-- D*
        */

        this.backend.project("A").addVersionedClasses("1.0.0", "a1", "a2");
        this.backend.project("B").addVersionedClasses("1.0.0", "b1", "b2");
        this.backend.project("C").addVersionedClasses("1.0.0", "c1", "c2");
        this.backend.project("D").addVersionedClasses("1.0.0", "d1", "d2");
        this.backend.project("E").addVersionedClasses("1.0.0", "e1", "e2");
        this.backend.project("A").addDependency("B:1.0.0");
        this.backend.project("A").addDependency("C:1.0.0");
        this.backend.project("B").addDependency("D:1.0.0");
        this.backend.project("C").addDependency("D:1.0.0");
        this.backend.project("D").addDependency("E:1.0.0");

        List<Entity> entitiesBefore = findVersionEntities("A", "1.0.0");
        Assert.assertEquals(
                Sets.mutable.with("A::a1", "A::a2", "B::b1", "B::b2", "C::c1", "C::c2", "D::d1", "D::d2", "E::e1", "E::e2"),
                toEntityPathSet(entitiesBefore));

        this.backend.project("B").addClasses("w1", "b3");

        List<Entity> entitiesAfter = this.testModelBuilder.buildEntitiesForTest(
                "B",
                "w1",
                revisionId("B", "w1"),
                "A",
                revisionId("A")
        );

        Assert.assertEquals(
                Sets.mutable.with("A::a1", "A::a2", "B::b1", "B::b2", "B::b3", "C::c1", "C::c2", "D::d1", "D::d2", "E::e1", "E::e2"),
                toEntityPathSet(entitiesAfter));

        List<Entity> entitiesAfterForVersion = this.testModelBuilder.buildEntitiesForTest(
                "B",
                VersionId.parseVersionId("1.0.0"),
                "A",
                revisionId("A")
        );

        Assert.assertEquals(
                Sets.mutable.with("A::a1", "A::a2", "B::b1", "B::b2", "C::c1", "C::c2", "D::d1", "D::d2", "E::e1", "E::e2"),
                toEntityPathSet(entitiesAfterForVersion));

        List<Entity> entitiesAfterForRevision = this.testModelBuilder.buildEntitiesForTest(
                "B",
                revisionId("B"),
                "A",
                revisionId("A")
        );

        Assert.assertEquals(
                Sets.mutable.with("A::a1", "A::a2", "B::b1", "B::b2", "C::c1", "C::c2", "D::d1", "D::d2", "E::e1", "E::e2"),
                toEntityPathSet(entitiesAfterForRevision));
    }

    @Test
    public void dependencyAddedToUpstreamProject()
    {
        /*
            Path from x to y indicates that project x depends on project y
            x(~) indicates that project x has been modified
            x(*) refers to project x that has been defined elsewhere in the tree

            A
             +-- B(~)
                 +-- C
            D

            Dependency D is added to project B
         */

        this.backend.project("A").addVersionedClasses("1.0.0", "a1", "a2");
        this.backend.project("B").addVersionedClasses("1.0.0", "b1", "b2");
        this.backend.project("C").addVersionedClasses("1.0.0", "c1", "c2");
        this.backend.project("D").addVersionedClasses("1.0.0", "d1", "d2");
        this.backend.project("A").addDependency("B:1.0.0");
        this.backend.project("B").addDependency("C:1.0.0");

        List<Entity> entitiesBefore = findVersionEntities("A", "1.0.0");
        Assert.assertEquals(
                Sets.mutable.with("A::a1", "A::a2", "B::b1", "B::b2", "C::c1", "C::c2"),
                toEntityPathSet(entitiesBefore));

        this.backend.project("B").addProjectDependency("w1", "D:1.0.0");

        MutableSet<String> expected =  Sets.mutable.with("A::a1", "A::a2", "B::b1", "B::b2", "C::c1", "C::c2", "D::d1", "D::d2");

        List<Entity> entitiesAfter = this.testModelBuilder.buildEntitiesForTest(
                "B",
                "w1",
                revisionId("B", "w1"),
                "A",
                revisionId("A")
        );

        Assert.assertEquals(expected, toEntityPathSet(entitiesAfter));

        List<Entity> entitiesAfterForVersion = this.testModelBuilder.buildEntitiesForTest(
                "B",
                VersionId.parseVersionId("1.0.0"),
                "A",
                revisionId("A")
        );

        Assert.assertEquals(expected, toEntityPathSet(entitiesAfterForVersion));

        List<Entity> entitiesAfterForRevision = this.testModelBuilder.buildEntitiesForTest(
                "B",
                revisionId("B"),
                "A",
                revisionId("A")
        );

        Assert.assertEquals(
                Sets.mutable.with("A::a1", "A::a2", "B::b1", "B::b2", "C::c1", "C::c2", "D::d1", "D::d2"),
                toEntityPathSet(entitiesAfterForRevision));
    }

    @Test
    public void dependencyRemovedFromUpstreamProject()
    {
        /*
            Path from x to y indicates that project x depends on project y
            x(~) indicates that project x has been modified
            x(*) refers to project x that has been defined elsewhere in the tree

            A
             +-- B(~)
             |   +-- C
             +-- D

            Dependency C is removed from project B
         */

        this.backend.project("A").addVersionedClasses("1.0.0", "a1", "a2");
        this.backend.project("B").addVersionedClasses("1.0.0", "b1", "b2");
        this.backend.project("C").addVersionedClasses("1.0.0", "c1", "c2");
        this.backend.project("D").addVersionedClasses("1.0.0", "d1", "d2");
        this.backend.project("A").addDependency("B:1.0.0");
        this.backend.project("A").addDependency("D:1.0.0");
        this.backend.project("B").addDependency("C:1.0.0");

        List<Entity> entitiesBefore = findVersionEntities("A", "1.0.0");
        Assert.assertEquals(
                Sets.mutable.with("A::a1", "A::a2", "B::b1", "B::b2", "C::c1", "C::c2", "D::d1", "D::d2"),
                toEntityPathSet(entitiesBefore));

        this.backend.project("B").removeProjectDependency("w1", "C:1.0.0");

        MutableSet<String> expected =  Sets.mutable.with("A::a1", "A::a2", "B::b1", "B::b2", "D::d1", "D::d2");

        List<Entity> entitiesAfter = this.testModelBuilder.buildEntitiesForTest(
                "B",
                "w1",
                revisionId("B", "w1"),
                "A",
                revisionId("A")
        );

        Assert.assertEquals(expected, toEntityPathSet(entitiesAfter));

        List<Entity> entitiesAfterForVersion = this.testModelBuilder.buildEntitiesForTest(
                "B",
                VersionId.parseVersionId("1.0.0"),
                "A",
                revisionId("A")
        );

        Assert.assertEquals(expected, toEntityPathSet(entitiesAfterForVersion));

        List<Entity> entitiesAfterForRevision = this.testModelBuilder.buildEntitiesForTest(
                "B",
                revisionId("B"),
                "A",
                revisionId("A")
        );

        Assert.assertEquals(
                Sets.mutable.with("A::a1", "A::a2", "B::b1", "B::b2", "D::d1", "D::d2"),
                toEntityPathSet(entitiesAfterForRevision));
    }

    @Test
    public void sharedDependencyRemoved()
    {
        /*
            Path from x to y indicates that project x depends on project y
            x(~) indicates that project x has been modified
            x(*) refers to project x that has been defined elsewhere in the tree

            A
             +-- B(~)
             |   +-- C
             |       +-- D
             +-- C(*)
             +-- E

             Project C is common dependency of both A and B
             Project C is removed as a dependency of project B
             We still see C in the result via the direct A to C dependency

         */

        this.backend.project("A").addVersionedClasses("1.0.0", "a1", "a2");
        this.backend.project("B").addVersionedClasses("1.0.0", "b1", "b2");
        this.backend.project("C").addVersionedClasses("1.0.0", "c1", "c2");
        this.backend.project("D").addVersionedClasses("1.0.0", "d1", "d2");
        this.backend.project("E").addVersionedClasses("1.0.0", "e1", "e2");
        this.backend.project("A").addDependency("B:1.0.0");
        this.backend.project("A").addDependency("C:1.0.0");
        this.backend.project("A").addDependency("E:1.0.0");
        this.backend.project("B").addDependency("C:1.0.0");
        this.backend.project("C").addDependency("D:1.0.0");

        List<Entity> entitiesBefore = findVersionEntities("A", "1.0.0");
        Assert.assertEquals(
                Sets.mutable.with("A::a1", "A::a2", "B::b1", "B::b2", "C::c1", "C::c2", "D::d1", "D::d2", "E::e1", "E::e2"),
                toEntityPathSet(entitiesBefore));

        this.backend.project("B").removeProjectDependency("w1", "C:1.0.0");

        MutableSet<String> expected =  Sets.mutable.with("A::a1", "A::a2", "B::b1", "B::b2", "C::c1", "C::c2", "D::d1", "D::d2", "E::e1", "E::e2");

        List<Entity> entitiesAfter = this.testModelBuilder.buildEntitiesForTest(
                "B",
                "w1",
                revisionId("B", "w1"),
                "A",
                revisionId("A")
        );

        Assert.assertEquals(expected, toEntityPathSet(entitiesAfter));

        List<Entity> entitiesAfterForVersion = this.testModelBuilder.buildEntitiesForTest(
                "B",
                VersionId.parseVersionId("1.0.0"),
                "A",
                revisionId("A")
        );

        Assert.assertEquals(expected, toEntityPathSet(entitiesAfterForVersion));

        List<Entity> entitiesAfterForRevision = this.testModelBuilder.buildEntitiesForTest(
                "B",
                revisionId("B"),
                "A",
                revisionId("A")
        );

        Assert.assertEquals(
                Sets.mutable.with("A::a1", "A::a2", "B::b1", "B::b2", "C::c1", "C::c2", "D::d1", "D::d2", "E::e1", "E::e2"),
                toEntityPathSet(entitiesAfterForRevision));
    }

    @Test
    public void sharedDependencyWithDifferingVersions()
    {
        /*
            Path from x to y indicates that project x depends on project y
            x(~) indicates that project x has been modified
            x(*) refers to project x that has been defined elsewhere in the tree

            A
             +-- B(~)
             |   +-- C
             +-- C(*)
             +-- D

             Projects A and B depend on Project C version 1.0.0
             Project B is modified to depend on Project C version 2.0.0
             We should see Project C version 2.0.0 in the result (i.e the version of C used by the upstream project overrides the version used by the downstream project)
         */

        this.backend.project("A").addVersionedClasses("1.0.0", "a1", "a2");
        this.backend.project("B").addVersionedClasses("1.0.0", "b1", "b2");
        this.backend.project("C").addVersionedClasses("1.0.0", "c1", "c2");
        // Note :  Version C:2.0.0 builds on top of Version C:1.0.0 by adding 2 new entities
        this.backend.project("C").addVersionedClasses("2.0.0", "c3", "c4");
        this.backend.project("D").addVersionedClasses("1.0.0", "d1", "d2");
        this.backend.project("A").addDependency("B:1.0.0");
        this.backend.project("A").addDependency("C:1.0.0");
        this.backend.project("A").addDependency("D:1.0.0");
        this.backend.project("B").addDependency("C:1.0.0");

        // before the change, A depends on version 1.0.0 of C i.e it has entities c1 and c2
        List<Entity> entitiesBefore = findVersionEntities("A", "1.0.0");
        Assert.assertEquals(
                Sets.mutable.with("A::a1", "A::a2", "B::b1", "B::b2", "C::c1", "C::c2", "D::d1", "D::d2"),
                toEntityPathSet(entitiesBefore));

        this.backend.project("B").updateProjectDependency("w1", "C:1.0.0", "C:2.0.0");

        MutableSet<String> expected =  Sets.mutable.with("A::a1", "A::a2", "B::b1", "B::b2", "C::c1", "C::c2", "C::c3", "C::c4", "D::d1", "D::d2");

        List<Entity> entitiesAfter = this.testModelBuilder.buildEntitiesForTest(
                "B",
                "w1",
                revisionId("B", "w1"),
                "A",
                revisionId("A")
        );

        // after the change, A depends on version 2.0.0 of C i.e it has entities c1, c2, c3, c4
        Assert.assertEquals(expected, toEntityPathSet(entitiesAfter));

        List<Entity> entitiesAfterForVersion = this.testModelBuilder.buildEntitiesForTest(
                "B",
                VersionId.parseVersionId("1.0.0"),
                "A",
                revisionId("A")
        );

        Assert.assertEquals(expected, toEntityPathSet(entitiesAfterForVersion));

        List<Entity> entitiesAfterForRevision = this.testModelBuilder.buildEntitiesForTest(
                "B",
                revisionId("B"),
                "A",
                revisionId("A")
        );

        Assert.assertEquals(
                Sets.mutable.with("A::a1", "A::a2", "B::b1", "B::b2", "C::c1", "C::c2", "C::c3", "C::c4", "D::d1", "D::d2"),
                toEntityPathSet(entitiesAfterForRevision));
    }

    private String revisionId(String projectId, String workspaceId)
    {
        return this.backend.getRevisionApi().getUserWorkspaceRevisionContext(projectId, workspaceId).getCurrentRevision().getId();
    }

    private String revisionId(String projectId, String workspaceId, WorkspaceType type)
    {
        return this.backend.getRevisionApi().getWorkspaceRevisionContext(projectId, workspaceId, type).getCurrentRevision().getId();
    }

    private String revisionId(String projectId)
    {
        return this.backend.getRevisionApi().getProjectRevisionContext(projectId).getCurrentRevision().getId();
    }

    private List<Entity> findVersionEntities(String projectId, String versionId)
    {
        Set<ProjectDependency> dependencies = this.dependenciesApi.getProjectVersionUpstreamProjects(projectId, versionId, true);
        List<Entity> entities = Lists.mutable.withAll(this.backend.getEntityApi().getVersionEntityAccessContext(projectId, versionId).getEntities(null, null, null));
        dependencies.stream().map(d -> this.backend.getEntityApi().getVersionEntityAccessContext(d.getProjectId(), d.getVersionId()).getEntities(null, null, null)).forEach(entities::addAll);
        return entities;
    }

    private MutableSet<String> toEntityPathSet(Iterable<? extends Entity> entities)
    {
        return Iterate.collect(entities, Entity::getPath, Sets.mutable.empty());
    }
}
