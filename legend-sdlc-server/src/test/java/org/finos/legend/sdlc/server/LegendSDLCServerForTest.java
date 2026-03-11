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
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.patch.Patch;
import org.finos.legend.sdlc.domain.model.project.Project;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.config.LegendSDLCServerConfiguration;
import org.finos.legend.sdlc.server.guice.AbstractBaseModule;
import org.finos.legend.sdlc.server.guice.InMemoryModule;
import org.finos.legend.sdlc.server.inmemory.backend.InMemoryMixins;
import org.finos.legend.sdlc.server.jackson.ProjectDependencyMixin;
import org.finos.legend.sdlc.server.jackson.VersionIdMixin;
import org.finos.legend.sdlc.domain.model.review.Review;

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

        bootstrap.getObjectMapper().addMixIn(Project.class, InMemoryMixins.Project.class);
        bootstrap.getObjectMapper().addMixIn(Workspace.class, InMemoryMixins.Workspace.class);
        bootstrap.getObjectMapper().addMixIn(Entity.class, InMemoryMixins.Entity.class);
        bootstrap.getObjectMapper().addMixIn(Revision.class, InMemoryMixins.Revision.class);
        bootstrap.getObjectMapper().addMixIn(ProjectDependency.class, ProjectDependencyMixin.class);
        bootstrap.getObjectMapper().addMixIn(VersionId.class, VersionIdMixin.class);
        bootstrap.getObjectMapper().addMixIn(Review.class, InMemoryMixins.Review.class);
        bootstrap.getObjectMapper().addMixIn(Patch.class, InMemoryMixins.Patch.class);
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
}
