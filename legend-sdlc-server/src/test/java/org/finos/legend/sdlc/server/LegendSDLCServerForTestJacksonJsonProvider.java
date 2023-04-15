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

package org.finos.legend.sdlc.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.project.Project;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.domain.model.review.Review;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.inmemory.backend.InMemoryMixins;
import org.finos.legend.sdlc.server.jackson.ProjectDependencyMixin;
import org.finos.legend.sdlc.server.jackson.VersionIdMixin;

import javax.ws.rs.ext.ContextResolver;

public class LegendSDLCServerForTestJacksonJsonProvider extends JacksonJsonProvider implements ContextResolver<ObjectMapper>
{
    private final ObjectMapper objectMapper;

    public LegendSDLCServerForTestJacksonJsonProvider()
    {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.addMixIn(Project.class, InMemoryMixins.Project.class);
        objectMapper.addMixIn(Workspace.class, InMemoryMixins.Workspace.class);
        objectMapper.addMixIn(Entity.class, InMemoryMixins.Entity.class);
        objectMapper.addMixIn(Revision.class, InMemoryMixins.Revision.class);
        objectMapper.addMixIn(ProjectDependency.class, ProjectDependencyMixin.class);
        objectMapper.addMixIn(VersionId.class, VersionIdMixin.class);
        objectMapper.addMixIn(Review.class, InMemoryMixins.Review.class);
    }

    @Override
    public ObjectMapper getContext(Class<?> type)
    {
        return this.objectMapper;
    }
}
