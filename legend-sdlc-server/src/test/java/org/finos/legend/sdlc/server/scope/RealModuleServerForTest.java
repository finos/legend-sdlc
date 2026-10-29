// Copyright 2026 Goldman Sachs
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

package org.finos.legend.sdlc.server.scope;

import io.dropwizard.core.setup.Environment;
import org.finos.legend.sdlc.server.BaseLegendSDLCServer;
import org.finos.legend.sdlc.server.auth.LegendSDLCWebFilter;
import org.finos.legend.sdlc.server.auth.StateSessionBuilder;
import org.finos.legend.sdlc.server.config.LegendSDLCServerConfiguration;
import org.pac4j.core.profile.CommonProfile;
import ru.vyarus.dropwizard.guice.GuiceBundle;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.EnumSet;

public class RealModuleServerForTest extends BaseLegendSDLCServer<LegendSDLCServerConfiguration>
{
    public static final String USER_HEADER = "X-Test-User";

    private GuiceBundle guiceBundle;

    public RealModuleServerForTest()
    {
        super(null);
    }

    @Override
    protected GuiceBundle buildGuiceBundle()
    {
        this.guiceBundle = super.buildGuiceBundle();
        return this.guiceBundle;
    }

    public GuiceBundle getGuiceBundle()
    {
        return this.guiceBundle;
    }

    @Override
    public void run(LegendSDLCServerConfiguration configuration, Environment environment)
    {
        super.run(configuration, environment);
        environment.servlets().addFilter("TestUser", new TestUserFilter())
                .addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
    }

    @Override
    protected ServerPlatformInfo newServerPlatformInfo()
    {
        return new ServerPlatformInfo(null, null, null);
    }

    private static class TestUserFilter implements Filter
    {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException
        {
            String user = ((HttpServletRequest) request).getHeader(USER_HEADER);
            if (user != null)
            {
                CommonProfile profile = new CommonProfile();
                profile.setId(user);
                request.setAttribute(LegendSDLCWebFilter.SESSION_ATTRIBUTE, StateSessionBuilder.newBuilder().withProfile(profile).build());
            }
            chain.doFilter(request, response);
        }
    }
}
