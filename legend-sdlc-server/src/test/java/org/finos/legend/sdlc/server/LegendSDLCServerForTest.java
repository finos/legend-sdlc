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
import io.dropwizard.setup.Environment;
import org.finos.legend.sdlc.server.config.LegendSDLCServerConfiguration;
import org.finos.legend.sdlc.server.guice.AbstractBaseModule;
import org.finos.legend.sdlc.server.guice.InMemoryModule;

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
    protected GuiceBundle<LegendSDLCServerConfiguration> buildGuiceBundle()
    {
        this.guiceBundle = super.buildGuiceBundle();
        return this.guiceBundle;
    }

    @Override
    public void run(LegendSDLCServerConfiguration configuration, Environment environment)
    {
        super.run(configuration, environment);
        environment.jersey().register(LegendSDLCServerForTestJacksonJsonProvider.class);
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
