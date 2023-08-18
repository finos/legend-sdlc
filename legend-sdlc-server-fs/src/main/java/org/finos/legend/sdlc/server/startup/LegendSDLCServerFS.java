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

package org.finos.legend.sdlc.server.startup;

import com.hubspot.dropwizard.guicier.GuiceBundle;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.finos.legend.engine.protocol.pure.v1.PureProtocolObjectMapperFactory;
import org.finos.legend.sdlc.server.BaseServer;
import org.finos.legend.sdlc.server.depot.DepotConfiguration;
import org.finos.legend.sdlc.server.project.config.ProjectStructureConfiguration;
import org.finos.legend.sdlc.server.tools.BackgroundTaskProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class LegendSDLCServerFS<T extends LegendSDLCServerFSConfiguration> extends BaseServer<T>
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LegendSDLCServerFS.class);

    private BackgroundTaskProcessor backgroundTaskProcessor;

    public LegendSDLCServerFS()
    {
    }

    @Override
    public String getName()
    {
        return "Metadata SDLC";
    }

    @Override
    public void initialize(Bootstrap<T> bootstrap)
    {
        super.initialize(bootstrap);

        configureApis(bootstrap);

        // SDLC specific initialization
        ProjectStructureConfiguration.configureObjectMapper(bootstrap.getObjectMapper());
        DepotConfiguration.configureObjectMapper(bootstrap.getObjectMapper());
        PureProtocolObjectMapperFactory.withPureProtocolExtensions(bootstrap.getObjectMapper());
    }

    protected void configureApis(Bootstrap<T> bootstrap)
    {
        // Guice bootstrapping..
        bootstrap.addBundle(buildGuiceBundle());
    }

    protected GuiceBundle<LegendSDLCServerFSConfiguration> buildGuiceBundle()
    {
        return GuiceBundle.defaultBuilder(LegendSDLCServerFSConfiguration.class)
                .modules(buildBaseModule())
                .build();
    }

    protected FSModule buildBaseModule()
    {
        return new FSModule(this);
    }

    @Override
    public void run(T configuration, Environment environment)
    {
        super.run(configuration, environment);
        LifecycleEnvironment lifecycleEnvironment = environment.lifecycle();
        LOGGER.debug("Creating background task processor");
        BackgroundTaskProcessor taskProcessor = new BackgroundTaskProcessor(1);
        lifecycleEnvironment.manage(new Managed()
        {
            @Override
            public void start()
            {
                // nothing to do
            }

            @Override
            public void stop() throws Exception
            {
                LOGGER.debug("Shutting down background task processor");
                taskProcessor.shutdown();
                if (taskProcessor.awaitTermination(30, TimeUnit.SECONDS))
                {
                    LOGGER.debug("Done shutting down background task processor");
                }
                else
                {
                    LOGGER.debug("Background task processor did not terminate within the timeout");
                }
            }
        });
        this.backgroundTaskProcessor = taskProcessor;
    }

    public BackgroundTaskProcessor getBackgroundTaskProcessor()
    {
        return this.backgroundTaskProcessor;
    }

    @Override
    protected ServerPlatformInfo newServerPlatformInfo()
    {
        return new ServerPlatformInfo(null, null, null);
    }

    public static void main(String... args) throws Exception
    {
        LOGGER.info("Starting SDLC server with File System as backend");
        new LegendSDLCServerFS().run(args);
    }
}
