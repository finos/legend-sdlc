// Copyright 2021 Goldman Sachs
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

import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.hubspot.dropwizard.guicier.GuiceBundle;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.finos.legend.engine.protocol.pure.v1.PureProtocolObjectMapperFactory;
import org.finos.legend.sdlc.backend.api.spi.BackendFactory;
import org.finos.legend.sdlc.server.backend.UnsupportedCapabilityExceptionMapper;
import org.finos.legend.sdlc.server.config.LegendSDLCServerConfiguration;
import org.finos.legend.sdlc.server.depot.DepotConfiguration;
import org.finos.legend.sdlc.server.backend.AuthorizationRequiredExceptionMapper;
import org.finos.legend.sdlc.server.backend.StateSessionWebFilter;
import org.finos.legend.sdlc.server.guice.AbstractBaseModule;
import org.finos.legend.sdlc.server.guice.BaseModule;
import org.finos.legend.sdlc.server.project.config.ProjectStructureConfiguration;
import org.finos.legend.sdlc.backend.api.tools.BackgroundTaskProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;

public abstract class BaseLegendSDLCServer<T extends LegendSDLCServerConfiguration> extends BaseServer<T>
{
    /**
     * @deprecated Backend selection is by the polymorphic {@code backend:} configuration section (with a legacy
     * adapter for the top-level {@code gitLab:} section); the mode string no longer decides anything.
     */
    @Deprecated
    public static final String GITLAB_MODE = "gitlab";

    /**
     * The registered name of the session filter. Historically "GitLab" (the filter was GitLab-specific);
     * deployments reference it in {@code filterPriorities}, so the configuration aliases the old name to this
     * one (see {@code LegendSDLCServerConfiguration#getFilterPriorities()}).
     */
    public static final String SESSION_FILTER_NAME = "LegendSDLCSession";

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseLegendSDLCServer.class);

    private final String mode;
    private BackgroundTaskProcessor backgroundTaskProcessor;

    public BaseLegendSDLCServer(String mode)
    {
        this.mode = mode;
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
        // Register each discovered backend factory's configuration class as a subtype of the polymorphic
        // "backend" configuration section, keyed by the factory's type, and let the factory configure the
        // configuration mapper (e.g. mix-ins its configuration class needs)
        ServiceLoader.load(BackendFactory.class).forEach(factory ->
        {
            bootstrap.getObjectMapper().registerSubtypes(new NamedType(factory.getConfigurationClass(), factory.getType()));
            factory.configureObjectMapper(bootstrap.getObjectMapper());
        });

        // Guice bootstrapping..
        bootstrap.addBundle(buildGuiceBundle());
    }

    protected GuiceBundle<LegendSDLCServerConfiguration> buildGuiceBundle()
    {
        return GuiceBundle.defaultBuilder(LegendSDLCServerConfiguration.class)
                .modules(buildBaseModule())
                .build();
    }

    protected AbstractBaseModule buildBaseModule()
    {
        return new BaseModule(this);
    }

    @Override
    public void run(T configuration, Environment environment)
    {
        super.run(configuration, environment);
        environment.jersey().register(new UnsupportedCapabilityExceptionMapper());
        environment.jersey().register(new AuthorizationRequiredExceptionMapper());
        // StaleAuthorizationExceptionMapper is Guice-bound (it needs the request through a lazy provider) and
        // registered with Jersey by the Guice bundle's binding scan

        // The session filter: builds the backend-independent state session from the pac4j profiles and the
        // session cookie. Registered for every deployment; with no authentication profiles it passes through.
        FilterRegistration.Dynamic sessionFilter = environment.servlets().addFilter(SESSION_FILTER_NAME, new StateSessionWebFilter());
        sessionFilter.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), false, "*");
        LifecycleEnvironment lifecycleEnvironment = environment.lifecycle();
        BackgroundTaskProcessor taskProcessor = getBackgroundTaskProcessor();
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
    }

    public String getMode()
    {
        return this.mode;
    }

    /**
     * The server's background task processor, created on first use. Guice bindings may pull this during injector
     * creation, which happens in the bundle run phase, before {@link #run}; the processor must therefore not
     * depend on {@link #run} having executed ({@link #run} registers its lifecycle shutdown). The underlying
     * executor starts no threads until a task is submitted, and idle threads time out, so early creation is
     * harmless for commands that never use it.
     *
     * @return background task processor
     */
    public synchronized BackgroundTaskProcessor getBackgroundTaskProcessor()
    {
        if (this.backgroundTaskProcessor == null)
        {
            LOGGER.debug("Creating background task processor");
            this.backgroundTaskProcessor = new BackgroundTaskProcessor(1);
        }
        return this.backgroundTaskProcessor;
    }
}
