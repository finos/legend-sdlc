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

package org.finos.legend.sdlc.server.demo;

import com.hubspot.dropwizard.guicier.GuiceBundle;
import io.dropwizard.setup.Bootstrap;
import org.finos.legend.sdlc.domain.model.project.Project;
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.server.BaseLegendSDLCServer;
import org.finos.legend.sdlc.server.backend.simple.guice.SimpleBackendMixins;
import org.finos.legend.sdlc.server.backend.simple.guice.SimpleBackendModule;
import org.finos.legend.sdlc.server.config.LegendSDLCServerConfiguration;
import org.finos.legend.sdlc.server.guice.AbstractBaseModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LegendSDLCServerForDemo extends BaseLegendSDLCServer<LegendSDLCServerConfiguration>
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LegendSDLCServerForDemo.class);

    private GuiceBundle<LegendSDLCServerConfiguration> guiceBundle;

    public LegendSDLCServerForDemo()
    {
        super(null);
    }

    @Override
    protected void configureApis(Bootstrap<LegendSDLCServerConfiguration> bootstrap)
    {
        super.configureApis(bootstrap);

        bootstrap.getObjectMapper().addMixIn(Project.class, SimpleBackendMixins.Project.class);
        bootstrap.getObjectMapper().addMixIn(Workspace.class, SimpleBackendMixins.Workspace.class);
        bootstrap.getObjectMapper().addMixIn(Revision.class, SimpleBackendMixins.Revision.class);
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
        return new SimpleBackendModule(this);
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
        LOGGER.info("Starting demo SDLC server");
        new LegendSDLCServerForDemo().run(args);
    }
}
