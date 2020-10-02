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

package org.finos.legend.sdlc.server.inmemory.backend;

import org.finos.legend.sdlc.domain.model.TestTools;

import java.util.Collections;

public class InMemoryBackendExample
{
    private void example()
    {
        /*
            proj2 -----> proj1

            proj3 is added as a new dependency in workspace

            proj2 -----> proj1
                                -----> proj3

            Transitive closure should include proj3
         */

        InMemoryBackend backend = new InMemoryBackend();
        backend.project("project1").addVersionedClasses("1.0.0", "A", "B");
        backend.project("project2").addVersionedClasses("1.0.0", "C", "D");
        backend.project("project3").addVersionedClasses("1.0.0", "E", "F");
        backend.project("project2").addDependency("project1:1.0.0");

        backend.project("project1")
                .addProjectDependency("w1", "project3:1.0.0");


        backend.project("project1")
                .addEntities("w1", TestTools.newClassEntity("A", "project1", Collections.singletonList(TestTools.newProperty("prop1", "Integer", 1, 100))));
    }
}
