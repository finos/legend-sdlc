// Copyright 2023 Goldman Sachs
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

package org.finos.legend.sdlc.server.backend.simple.guice;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.finos.legend.sdlc.server.backend.simple.domain.model.project.SimpleBackendProject;
import org.finos.legend.sdlc.server.backend.simple.domain.model.project.workspace.SimpleBackendProjectWorkspace;
import org.finos.legend.sdlc.server.backend.simple.domain.model.revision.SimpleBackendRevision;

public class SimpleBackendMixins
{
    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.PROPERTY,
            property = "jackson-type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = SimpleBackendProject.class, name = "SimpleBackendProject")})
    public abstract class Project
    {

    }

    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.PROPERTY,
            property = "jackson-type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = SimpleBackendProjectWorkspace.class, name = "SimpleBackendProjectWorkspace")})
    public abstract class Workspace
    {

    }

    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.PROPERTY,
            property = "jackson-type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = SimpleBackendRevision.class, name = "SimpleBackendRevision")})
    public abstract class Revision
    {

    }
}
