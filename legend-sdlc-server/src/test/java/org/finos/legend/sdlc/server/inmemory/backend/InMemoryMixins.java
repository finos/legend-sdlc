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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryEntity;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryPatch;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryProject;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryRevision;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryWorkspace;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryReview;

public class InMemoryMixins
{
    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.PROPERTY,
            property = "jackson-type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = InMemoryProject.class, name = "InMemoryProject")})
    public abstract class Project
    {

    }

    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.PROPERTY,
            property = "jackson-type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = InMemoryWorkspace.class, name = "InMemoryWorkspace")})
    public abstract class Workspace
    {

    }

    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.PROPERTY,
            property = "jackson-type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = InMemoryEntity.class, name = "InMemoryEntity")})
    public abstract class Entity
    {

    }

    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.PROPERTY,
            property = "jackson-type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = InMemoryRevision.class, name = "InMemoryRevision")})
    public abstract class Revision
    {

    }

    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "jackson-type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = InMemoryReview.class, name = "InMemoryReview")})
    public abstract class Review
    {

    }

    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.PROPERTY,
            property = "jackson-type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = InMemoryPatch.class, name = "InMemoryPatch")})
    public abstract class Patch
    {

    }
}
