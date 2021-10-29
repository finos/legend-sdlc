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

import org.apache.http.client.utils.URIBuilder;

import java.io.Serializable;
import java.util.Objects;

public class DepotServerInfo implements Serializable
{
    private static final long serialVersionUID = -3058713373597247765L;
    private static final String DEFAULT_SCHEME = "https";

    private final String scheme;
    private final String host;
    private final Integer port;

    private DepotServerInfo(String scheme, String host, Integer port)
    {
        this.scheme = (scheme == null) ? DEFAULT_SCHEME : scheme.toLowerCase();
        this.host = Objects.requireNonNull(host, "host may not be null");
        this.port = port;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }

        if (!(other instanceof DepotServerInfo))
        {
            return false;
        }

        DepotServerInfo that = (DepotServerInfo) other;
        return this.scheme.equals(that.scheme) && this.host.equals(that.host) && Objects.equals(this.port, that.port);
    }

    @Override
    public int hashCode()
    {
        return this.scheme.hashCode() + 127 * (this.host.hashCode() + 127 * Objects.hashCode(this.port));
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

    public String getDepotURLString()
    {
        return this.scheme + "://" + this.host + ((this.port == null) ? "" : (":" + this.port));
    }

    public URIBuilder newURIBuilder()
    {
        URIBuilder builder = new URIBuilder().setScheme(this.scheme).setHost(this.host);
        if (this.port != null)
        {
            builder.setPort(this.port);
        }
        return builder;
    }

    public static DepotServerInfo newServerInfo(DepotConfiguration.ServerConfiguration serverConfig)
    {
        return newServerInfo(serverConfig.getScheme(), serverConfig.getHost(), serverConfig.getPort());
    }

    public static DepotServerInfo newServerInfo(String scheme, String host, Integer port)
    {
        return new DepotServerInfo(scheme, host, port);
    }

}
