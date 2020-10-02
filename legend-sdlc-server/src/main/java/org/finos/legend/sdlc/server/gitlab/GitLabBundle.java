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

package org.finos.legend.sdlc.server.gitlab;

import io.dropwizard.Configuration;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabWebFilter;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import java.util.EnumSet;
import java.util.Objects;
import java.util.function.Function;

public class GitLabBundle<C extends Configuration> implements ConfiguredBundle<C>
{
    private final Function<? super C, ? extends GitLabConfiguration> configSupplier;

    public GitLabBundle(Function<? super C, ? extends GitLabConfiguration> configSupplier)
    {
        this.configSupplier = Objects.requireNonNull(configSupplier);
    }

    @Override
    public void initialize(Bootstrap<?> bootstrap)
    {
    }

    @Override
    public void run(C configuration, Environment environment)
    {
        GitLabConfiguration gitLabConfig = this.configSupplier.apply(configuration);
        if (gitLabConfig == null)
        {
            throw new RuntimeException("Could not find GitLabConfiguration");
        }

        Filter filter = GitLabWebFilter.fromConfig(gitLabConfig);
        GitLabServerHealthCheck healthCheck = GitLabServerHealthCheck.fromConfig(gitLabConfig);
        FilterRegistration.Dynamic registration = environment.servlets().addFilter("GitLab", filter);
        environment.healthChecks().register("gitLabServer", healthCheck);
        registration.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), false, "*");
    }
}
