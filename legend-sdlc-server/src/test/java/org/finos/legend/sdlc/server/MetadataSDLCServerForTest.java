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

import com.hubspot.dropwizard.guicier.GuiceBundle;
import io.dropwizard.setup.Bootstrap;
import org.finos.legend.sdlc.domain.model.project.Project;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.config.MetadataSDLCServerConfiguration;
import org.finos.legend.sdlc.server.guice.AbstractBaseModule;
import org.finos.legend.sdlc.server.guice.InMemoryModule;
import org.finos.legend.sdlc.server.inmemory.backend.InMemoryMixins;
import org.finos.legend.sdlc.server.jackson.ProjectDependencyMixin;
import org.finos.legend.sdlc.server.jackson.VersionIdMixin;

public class MetadataSDLCServerForTest extends MetadataSDLCServer
{
    private GuiceBundle<MetadataSDLCServerConfiguration> guiceBundle;

    public static void main(String... args) throws Exception
    {
        new MetadataSDLCServerForTest().run(args);
    }

    public MetadataSDLCServerForTest()
    {
        super(null);
    }

    @Override
    protected void configureApis(Bootstrap<MetadataSDLCServerConfiguration> bootstrap)
    {
        super.configureApis(bootstrap);

        bootstrap.getObjectMapper().addMixIn(Project.class, InMemoryMixins.Project.class);
        bootstrap.getObjectMapper().addMixIn(ProjectDependency.class, ProjectDependencyMixin.class);
        bootstrap.getObjectMapper().addMixIn(VersionId.class, VersionIdMixin.class);
    }

    @Override
    protected GuiceBundle<MetadataSDLCServerConfiguration> buildGuiceBundle()
    {
        this.guiceBundle = super.buildGuiceBundle();
        return this.guiceBundle;
    }

    @Override
    protected AbstractBaseModule buildBaseModule()
    {
        return new InMemoryModule(this);
    }

    public GuiceBundle<MetadataSDLCServerConfiguration> getGuiceBundle()
    {
        return this.guiceBundle;
    }
}
