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

package org.finos.legend.sdlc.server.depot;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.finos.legend.sdlc.server.depot.auth.AuthClientInjector;

public class DepotConfiguration
{
    private final ServerConfiguration serverConfiguration;
    private final AuthClientInjector authClientInjector;

    private DepotConfiguration(ServerConfiguration serverConfiguration, AuthClientInjector authClientInjector)
    {
        this.serverConfiguration = serverConfiguration;
        this.authClientInjector = authClientInjector;
    }

    public ServerConfiguration getServerConfiguration()
    {
        return this.serverConfiguration;
    }

    public AuthClientInjector getAuthClientInjector()
    {
        return this.authClientInjector;
    }

    public static DepotConfiguration emptyConfiguration()
    {
        return new DepotConfiguration(ServerConfiguration.emptyConfiguration(), null);
    }

    @JsonCreator
    public static DepotConfiguration newConfiguration(@JsonProperty("server") ServerConfiguration serverConfiguration, @JsonProperty("auth") AuthClientInjector authClientInjector)
    {
        return new DepotConfiguration(serverConfiguration, authClientInjector);
    }

    public static ObjectMapper configureObjectMapper(ObjectMapper objectMapper)
    {
        @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.WRAPPER_OBJECT)
        abstract class WrapperMixin
        {
        }

        return objectMapper.addMixIn(AuthClientInjector.class, WrapperMixin.class);
    }

    public static class ServerConfiguration
    {
        private final String scheme;
        private final String host;
        private final Integer port;

        private ServerConfiguration(String scheme, String host, Integer port)
        {
            this.scheme = scheme;
            this.host = host;
            this.port = port;
        }

        public String getScheme()
        {
            return this.scheme;
        }

        public String getHost()
        {
            return this.host;
        }

        public Integer getPort()
        {
            return this.port;
        }

        public static ServerConfiguration emptyConfiguration()
        {
            return new ServerConfiguration(null, null, null);
        }

        @JsonCreator
        public static ServerConfiguration newServerConfiguration(@JsonProperty("scheme") String scheme, @JsonProperty("host") String host, @JsonProperty("port") Integer port)
        {
            return new ServerConfiguration(scheme, host, port);
        }
    }
}
