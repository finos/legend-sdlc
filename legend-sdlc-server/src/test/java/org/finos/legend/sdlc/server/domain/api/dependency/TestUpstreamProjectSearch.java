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
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.inmemory.backend.InMemoryBackend;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

public class TestUpstreamProjectSearch
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
        Assert.assertEquals(Collections.emptySet(), this.dependenciesApi.getProjectRevisionUpstreamProjects("A", revisionId("A"), false));
        Assert.assertEquals(Collections.emptySet(), this.dependenciesApi.getProjectRevisionUpstreamProjects("A", revisionId("A"), true));
    }

    @Test
    public void case2()
    {
        VersionId patchReleaseVersionId = VersionId.parseVersionId("1.0.1");
        this.backend.project("A").addVersionedClasses("1.0.0", "a1");
        this.backend.project("A").addPatch(patchReleaseVersionId);
        Assert.assertEquals(Collections.emptySet(), this.dependenciesApi.getProjectRevisionUpstreamProjects("A", patchReleaseVersionId, revisionId("A", patchReleaseVersionId), false));
        Assert.assertEquals(Collections.emptySet(), this.dependenciesApi.getProjectRevisionUpstreamProjects("A", patchReleaseVersionId, revisionId("A", patchReleaseVersionId), true));
    }

    /*
        Path from x to y indicates that project x depends on project y
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
    public void case3()
    {
        this.backend.project("A").addVersionedClasses("1.0.0", "a1");
        this.backend.project("B").addVersionedClasses("1.0.0", "b1");
        this.backend.project("C").addVersionedClasses("1.0.0", "c1");
        this.backend.project("D").addVersionedClasses("1.0.0", "d1");
        this.backend.project("E").addVersionedClasses("1.0.0", "e1");
        this.backend.project("F").addVersionedClasses("1.0.0", "f1");
        this.backend.project("G").addVersionedClasses("1.0.0", "g1");

        this.backend.project("A").addDependency("B:1.0.0");
        this.backend.project("A").addDependency("C:1.0.0");
        this.backend.project("B").addDependency("D:1.0.0");
        this.backend.project("B").addDependency("E:1.0.0");
        this.backend.project("E").addDependency("F:1.0.0");
        this.backend.project("E").addDependency("G:1.0.0");

        Assert.assertEquals(Sets.mutable.with("B:1.0.0", "C:1.0.0"), toProjectRevisionStringSet(this.dependenciesApi.getProjectRevisionUpstreamProjects("A", revisionId("A"), false)));
        Assert.assertEquals(Sets.mutable.with("B:1.0.0", "C:1.0.0", "D:1.0.0", "E:1.0.0", "F:1.0.0", "G:1.0.0"), toProjectRevisionStringSet(this.dependenciesApi.getProjectRevisionUpstreamProjects("A", revisionId("A"), true)));
    }

    @Test
    public void case4()
    {
        VersionId patchReleaseVersionId = VersionId.parseVersionId("1.0.1");
        this.backend.project("A").addVersionedClasses("1.0.0", "a1");
        this.backend.project("B").addVersionedClasses("1.0.0", "b1");
        this.backend.project("C").addVersionedClasses("1.0.0", "c1");
        this.backend.project("D").addVersionedClasses("1.0.0", "d1");
        this.backend.project("E").addVersionedClasses("1.0.0", "e1");
        this.backend.project("F").addVersionedClasses("1.0.0", "f1");
        this.backend.project("G").addVersionedClasses("1.0.0", "g1");

        this.backend.project("A").addDependency("B:1.0.0");
        this.backend.project("A").addDependency("C:1.0.0");
        this.backend.project("B").addDependency("D:1.0.0");
        this.backend.project("B").addDependency("E:1.0.0");
        this.backend.project("E").addDependency("F:1.0.0");
        this.backend.project("E").addDependency("G:1.0.0");
        this.backend.project("A").addPatch(patchReleaseVersionId);

        Assert.assertEquals(Sets.mutable.with("B:1.0.0", "C:1.0.0"), toProjectRevisionStringSet(this.dependenciesApi.getProjectRevisionUpstreamProjects("A", patchReleaseVersionId, revisionId("A", patchReleaseVersionId), false)));
        Assert.assertEquals(Sets.mutable.with("B:1.0.0", "C:1.0.0", "D:1.0.0", "E:1.0.0", "F:1.0.0", "G:1.0.0"), toProjectRevisionStringSet(this.dependenciesApi.getProjectRevisionUpstreamProjects("A", patchReleaseVersionId, revisionId("A", patchReleaseVersionId), true)));
    }

    /*
        Path from x to y indicates that project x depends on project y
        x(*) refers to project x that has been defined elsewhere in the tree

        A
         +-- B
         |   +-- D
         |       +-- E
         |       +-- F
         +-- C
             +-- D*
     */

    @Test
    public void case5()
    {
        this.backend.project("A").addVersionedClasses("1.0.0", "a1");
        this.backend.project("B").addVersionedClasses("1.0.0", "b1");
        this.backend.project("C").addVersionedClasses("1.0.0", "c1");
        this.backend.project("D").addVersionedClasses("1.0.0", "d1");
        this.backend.project("E").addVersionedClasses("1.0.0", "e1");
        this.backend.project("F").addVersionedClasses("1.0.0", "f1");
        this.backend.project("G").addVersionedClasses("1.0.0", "g1");

        this.backend.project("A").addDependency("B:1.0.0");
        this.backend.project("A").addDependency("C:1.0.0");
        this.backend.project("B").addDependency("D:1.0.0");
        this.backend.project("D").addDependency("E:1.0.0");
        this.backend.project("D").addDependency("F:1.0.0");
        this.backend.project("C").addDependency("D:1.0.0");

        Assert.assertEquals(Sets.mutable.with("B:1.0.0", "C:1.0.0"), toProjectRevisionStringSet(this.dependenciesApi.getProjectRevisionUpstreamProjects("A", revisionId("A"), false)));
        Assert.assertEquals(Sets.mutable.with("B:1.0.0", "C:1.0.0", "D:1.0.0", "E:1.0.0", "F:1.0.0"), toProjectRevisionStringSet(this.dependenciesApi.getProjectRevisionUpstreamProjects("A", revisionId("A"), true)));
    }

    @Test
    public void case6()
    {
        VersionId patchReleaseVersionId = VersionId.parseVersionId("1.0.1");
        this.backend.project("A").addVersionedClasses("1.0.0", "a1");
        this.backend.project("B").addVersionedClasses("1.0.0", "b1");
        this.backend.project("C").addVersionedClasses("1.0.0", "c1");
        this.backend.project("D").addVersionedClasses("1.0.0", "d1");
        this.backend.project("E").addVersionedClasses("1.0.0", "e1");
        this.backend.project("F").addVersionedClasses("1.0.0", "f1");
        this.backend.project("G").addVersionedClasses("1.0.0", "g1");

        this.backend.project("A").addDependency("B:1.0.0");
        this.backend.project("A").addDependency("C:1.0.0");
        this.backend.project("B").addDependency("D:1.0.0");
        this.backend.project("D").addDependency("E:1.0.0");
        this.backend.project("D").addDependency("F:1.0.0");
        this.backend.project("C").addDependency("D:1.0.0");
        this.backend.project("A").addPatch(patchReleaseVersionId);

        Assert.assertEquals(Sets.mutable.with("B:1.0.0", "C:1.0.0"), toProjectRevisionStringSet(this.dependenciesApi.getProjectRevisionUpstreamProjects("A", patchReleaseVersionId, revisionId("A", patchReleaseVersionId), false)));
        Assert.assertEquals(Sets.mutable.with("B:1.0.0", "C:1.0.0", "D:1.0.0", "E:1.0.0", "F:1.0.0"), toProjectRevisionStringSet(this.dependenciesApi.getProjectRevisionUpstreamProjects("A", patchReleaseVersionId, revisionId("A", patchReleaseVersionId), true)));
    }

    private String revisionId(String projectId)
    {
        return this.revisionId(projectId, null);
    }

    private String revisionId(String projectId, VersionId patchReleaseVersionId)
    {
        return this.backend.getRevisionApi().getProjectRevisionContext(projectId, patchReleaseVersionId).getCurrentRevision().getId();
    }

    private MutableSet<String> toProjectRevisionStringSet(Iterable<? extends ProjectDependency> projectDependencies)
    {
        return Iterate.collect(projectDependencies, ProjectDependency::toDependencyString, Sets.mutable.empty());
    }
}