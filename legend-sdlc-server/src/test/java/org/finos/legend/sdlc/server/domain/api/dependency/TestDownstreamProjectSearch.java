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

package org.finos.legend.sdlc.server.domain.api.dependency;

import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.sdlc.server.inmemory.backend.InMemoryBackend;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.Set;

public class TestDownstreamProjectSearch
{
    private DependenciesApiImpl dependenciesApi;
    private InMemoryBackend backend;

    @Before
    public void setup()
    {
        this.backend = new InMemoryBackend();
        this.dependenciesApi = new DependenciesApiImpl(this.backend.getProjectApi(), this.backend.getProjectConfigurationApi(), this.backend.getRevisionApi());
    }

    /*
        A
     */

    @Test
    public void case1()
    {
        this.backend.project("A").addVersionedClasses("1.0.0", "a1");

        Set<ProjectRevision> downstreamProjects = this.dependenciesApi.getDownstreamProjects("A");
        Assert.assertEquals(Collections.emptySet(), downstreamProjects);
    }

    /*
        Path from x to y indicates that project y depends on project x
        x(*) refers to project x that has been defined elsewhere in the tree

        A
         +-- B
         |   +-- D
         |   +-- E
         |       +-- F
         |       +-- G
         +-- C
     */
    @Test
    public void case2()
    {
        this.backend.project("A").addVersionedClasses("1.0.0", "a1");
        this.backend.project("B").addVersionedClasses("1.0.0", "b1");
        this.backend.project("C").addVersionedClasses("1.0.0", "c1");
        this.backend.project("D").addVersionedClasses("1.0.0", "d1");
        this.backend.project("E").addVersionedClasses("1.0.0", "e1");
        this.backend.project("F").addVersionedClasses("1.0.0", "f1");
        this.backend.project("G").addVersionedClasses("1.0.0", "g1");

        this.backend.project("B").addDependency("A:1.0.0");
        this.backend.project("D").addDependency("B:1.0.0");
        this.backend.project("E").addDependency("B:1.0.0");
        this.backend.project("F").addDependency("E:1.0.0");
        this.backend.project("G").addDependency("E:1.0.0");
        this.backend.project("C").addDependency("A:1.0.0");


        Set<ProjectRevision> downstreamProjects = this.dependenciesApi.getDownstreamProjects("A");
        Assert.assertEquals(Sets.mutable.with("B:rev~2", "C:rev~2"), Iterate.collect(downstreamProjects, ProjectRevision::toProjectRevisionString, Sets.mutable.empty()));
    }

    /*
        Path from x to y indicates that project y depends on project x
        x(*) refers to project x that has been defined elsewhere in the tree

        A
         +-- B
         |   +-- D
         +-- C
             +-- D*
     */

    @Test
    public void case3()
    {
        this.backend.project("A").addVersionedClasses("1.0.0", "a1");
        this.backend.project("B").addVersionedClasses("1.0.0", "b1");
        this.backend.project("C").addVersionedClasses("1.0.0", "c1");
        this.backend.project("D").addVersionedClasses("1.0.0", "d1");
        this.backend.project("E").addVersionedClasses("1.0.0", "e1");
        this.backend.project("F").addVersionedClasses("1.0.0", "f1");
        this.backend.project("G").addVersionedClasses("1.0.0", "g1");

        this.backend.project("B").addDependency("A:1.0.0");
        this.backend.project("D").addDependency("B:1.0.0");
        this.backend.project("C").addDependency("A:1.0.0");
        this.backend.project("D").addDependency("C:1.0.0");

        Set<ProjectRevision> downstreamProjects = this.dependenciesApi.getDownstreamProjects("A");
        Assert.assertEquals(Sets.mutable.with("B:rev~2", "C:rev~2"), Iterate.collect(downstreamProjects, ProjectRevision::toProjectRevisionString, Sets.mutable.empty()));
    }
}