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

import com.hubspot.dropwizard.guicier.GuiceBundle;
import org.finos.legend.sdlc.server.config.LegendSDLCServerConfiguration;
import org.finos.legend.sdlc.server.guice.AbstractBaseModule;
import org.finos.legend.sdlc.server.guice.FileSystemModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public  class LegendSDLCServerFS extends BaseLegendSDLCServer<LegendSDLCServerConfiguration>
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LegendSDLCServerFS.class);

    private GuiceBundle<LegendSDLCServerConfiguration> guiceBundle;

    public LegendSDLCServerFS()
    {
        super(null);
    }

//    @Override
//    protected void configureApis(Bootstrap<LegendSDLCServerConfiguration> bootstrap)
//    {
//        super.configureApis(bootstrap);
//    }

    @Override
    protected GuiceBundle<LegendSDLCServerConfiguration> buildGuiceBundle()
    {
        this.guiceBundle = super.buildGuiceBundle();
        return this.guiceBundle;
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

    @Override
    protected AbstractBaseModule buildBaseModule()
    {
        return new FileSystemModule(this);
    }

    public static void main(String... args) throws Exception
    {
        LOGGER.info("Starting demo SDLC server");
        new LegendSDLCServerFS().run(args);
    }
}
