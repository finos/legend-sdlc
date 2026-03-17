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

package org.finos.legend.sdlc.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.hubspot.dropwizard.guicier.GuiceBundle;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.patch.Patch;
import org.finos.legend.sdlc.domain.model.project.Project;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.config.LegendSDLCServerConfiguration;
import org.finos.legend.sdlc.server.domain.api.dependency.ProjectRevision;
import org.finos.legend.sdlc.server.guice.AbstractBaseModule;
import org.finos.legend.sdlc.server.guice.InMemoryModule;
import org.finos.legend.sdlc.server.inmemory.backend.InMemoryMixins;
import org.finos.legend.sdlc.server.jackson.ProjectDependencyMixin;
import org.finos.legend.sdlc.server.jackson.ProjectRevisionMixin;
import org.finos.legend.sdlc.server.jackson.VersionIdMixin;
import org.finos.legend.sdlc.domain.model.review.Review;

import javax.ws.rs.ext.ContextResolver;


public class LegendSDLCServerForTest extends BaseLegendSDLCServer<LegendSDLCServerConfiguration>
{
    private GuiceBundle<LegendSDLCServerConfiguration> guiceBundle;

    public LegendSDLCServerForTest()
    {
        super(null);
    }

    @Override
    protected void configureApis(Bootstrap<LegendSDLCServerConfiguration> bootstrap)
    {
        super.configureApis(bootstrap);
    }

    @Override
    public void run(LegendSDLCServerConfiguration configuration, Environment environment)
    {
        super.run(configuration, environment);
        environment.jersey().register(LegendSDLCServerForTestJacksonJsonProvider.class);
    }

    @Override
    protected GuiceBundle<LegendSDLCServerConfiguration> buildGuiceBundle()
    {
        this.guiceBundle = super.buildGuiceBundle();
        return this.guiceBundle;
    }

    @Override
    protected AbstractBaseModule buildBaseModule()
    {
        return new InMemoryModule(this);
    }

    public GuiceBundle<LegendSDLCServerConfiguration> getGuiceBundle()
    {
        return this.guiceBundle;
    }

    @Override
    protected ServerPlatformInfo newServerPlatformInfo()
    {
        return new ServerPlatformInfo(null, null, null);
    }

    public static void main(String... args) throws Exception
    {
        new LegendSDLCServerForTest().run(args);
    }

    private static class LegendSDLCServerForTestJacksonJsonProvider extends JacksonJsonProvider implements ContextResolver<ObjectMapper>
    {
        private final ObjectMapper objectMapper;

        public LegendSDLCServerForTestJacksonJsonProvider()
        {
            this.objectMapper = Jackson.newObjectMapper();
            this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .addMixIn(Project.class, InMemoryMixins.Project.class)
                    .addMixIn(Workspace.class, InMemoryMixins.Workspace.class)
                    .addMixIn(Entity.class, InMemoryMixins.Entity.class)
                    .addMixIn(Revision.class, InMemoryMixins.Revision.class)
                    .addMixIn(Review.class, InMemoryMixins.Review.class)
                    .addMixIn(ProjectRevision.class, ProjectRevisionMixin.class)
                    .addMixIn(ProjectDependency.class, ProjectDependencyMixin.class)
                    .addMixIn(VersionId.class, VersionIdMixin.class)
                    .addMixIn(Patch.class, InMemoryMixins.Patch.class);
        }

        @Override
        public ObjectMapper getContext(Class<?> type)
        {
            return this.objectMapper;
        }
    }
}
