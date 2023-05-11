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

package org.finos.legend.sdlc.server.resources;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.patch.Patch;
import org.finos.legend.sdlc.domain.model.project.Project;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.domain.api.dependency.ProjectRevision;
import org.finos.legend.sdlc.domain.model.review.Review;
import org.finos.legend.sdlc.server.inmemory.backend.InMemoryMixins;
import org.finos.legend.sdlc.server.jackson.ProjectDependencyMixin;
import org.finos.legend.sdlc.server.jackson.ProjectRevisionMixin;
import org.finos.legend.sdlc.server.jackson.VersionIdMixin;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

public class SDLCServerClientRule implements TestRule
{
    private ObjectMapper objectMapper;
    private Client client;

    @Override
    public Statement apply(Statement base, Description description)
    {
        return new Statement()
        {
            @Override
            public void evaluate() throws Throwable
            {
                SDLCServerClientRule.this.before();
                base.evaluate();
            }
        };
    }

    private void before()
    {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        this.objectMapper.addMixIn(Project.class, InMemoryMixins.Project.class);
        this.objectMapper.addMixIn(Workspace.class, InMemoryMixins.Workspace.class);
        this.objectMapper.addMixIn(Entity.class, InMemoryMixins.Entity.class);
        this.objectMapper.addMixIn(Revision.class, InMemoryMixins.Revision.class);
        this.objectMapper.addMixIn(Review.class, InMemoryMixins.Review.class);
        this.objectMapper.addMixIn(ProjectRevision.class, ProjectRevisionMixin.class);
        this.objectMapper.addMixIn(ProjectDependency.class, ProjectDependencyMixin.class);
        this.objectMapper.addMixIn(Patch.class, InMemoryMixins.Patch.class);
        this.objectMapper.addMixIn(VersionId.class, VersionIdMixin.class);
        this.objectMapper.findAndRegisterModules();
        this.client = this.createClient();
    }

    public Client getClient()
    {
        return this.client;
    }

    private Client createClient()
    {
        JacksonJsonProvider jacksonJsonProvider = new JacksonJsonProvider();
        jacksonJsonProvider.setMapper(this.objectMapper);
        return ClientBuilder.newClient().register(jacksonJsonProvider);
    }
}

