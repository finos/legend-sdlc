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

import io.dropwizard.setup.Environment;
import org.eclipse.collections.api.factory.Maps;
import org.finos.legend.sdlc.server.config.LegendSDLCServerConfiguration;
import org.glassfish.jersey.CommonProperties;

public class LegendSDLCServer extends BaseLegendSDLCServer<LegendSDLCServerConfiguration>
{
    public LegendSDLCServer(String mode)
    {
        super(mode);
    }

    @Override
    public void run(LegendSDLCServerConfiguration configuration, Environment environment)
    {
        super.run(configuration, environment);
        // Ensure JacksonJaxbJsonProvider coming from org.glassfish.jersey.media:jersey-media-json-jackson
        // is not automatically picked up by the Jersey server via AutoDiscoverable mechanism
        // See https://eclipse-ee4j.github.io/jersey.github.io/documentation/latest/deployment.html#deployment.autodiscoverable
        environment.jersey().getResourceConfig().setProperties(Maps.mutable.of(CommonProperties.FEATURE_AUTO_DISCOVERY_DISABLE, true));
    }

    @Override
    protected ServerPlatformInfo newServerPlatformInfo()
    {
        return new ServerPlatformInfo(null, null, null);
    }

    public static void main(String... args) throws Exception
    {
        new LegendSDLCServer(GITLAB_MODE).run(args);
    }
}
