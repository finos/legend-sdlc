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
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.finos.legend.sdlc.server.config.MetadataSDLCServerConfiguration;
import org.finos.legend.sdlc.server.gitlab.GitLabBundle;
import org.finos.legend.sdlc.server.guice.AbstractBaseModule;
import org.finos.legend.sdlc.server.guice.BaseModule;
import org.finos.legend.sdlc.server.project.config.ProjectStructureConfiguration;
import org.finos.legend.sdlc.server.tools.BackgroundTaskProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class MetadataSDLCServer extends BaseServer<MetadataSDLCServerConfiguration>
{
    public static final String GITLAB_MODE = "gitlab";

    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataSDLCServer.class);

    private final String mode;
    private BackgroundTaskProcessor backgroundTaskProcessor;

    public MetadataSDLCServer(String mode)
    {
        this.mode = mode;
    }

    public static void main(String... args) throws Exception
    {
        new MetadataSDLCServer(GITLAB_MODE).run(args);
    }

    @Override
    public String getName()
    {
        return "Metadata SDLC";
    }

    @Override
    public void initialize(Bootstrap<MetadataSDLCServerConfiguration> bootstrap)
    {
        super.initialize(bootstrap);

        configureApis(bootstrap);

        // SDLC specific initialization
        ProjectStructureConfiguration.configureObjectMapper(bootstrap.getObjectMapper());
    }

    protected void configureApis(Bootstrap<MetadataSDLCServerConfiguration> bootstrap)
    {
        if (GITLAB_MODE.equals(this.mode))
        {
            // Add GitLab bundle
            bootstrap.addBundle(new GitLabBundle<>(MetadataSDLCServerConfiguration::getGitLabConfiguration));
        }

        // Guice bootstrapping..
        bootstrap.addBundle(buildGuiceBundle());
    }

    protected GuiceBundle<MetadataSDLCServerConfiguration> buildGuiceBundle()
    {
        return GuiceBundle.defaultBuilder(MetadataSDLCServerConfiguration.class)
                .modules(buildBaseModule())
                .build();
    }

    protected AbstractBaseModule buildBaseModule()
    {
        return new BaseModule(this);
    }

    @Override
    public void run(MetadataSDLCServerConfiguration configuration, Environment environment)
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

    public String getMode()
    {
        return this.mode;
    }

    public BackgroundTaskProcessor getBackgroundTaskProcessor()
    {
        return this.backgroundTaskProcessor;
    }

    @Override
    protected ServerPlatformInfo newServerPlatformInfo() throws Exception
    {
        return new ServerPlatformInfo(null, null, null);
    }
}
