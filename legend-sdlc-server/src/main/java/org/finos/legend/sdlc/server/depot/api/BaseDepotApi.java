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

package org.finos.legend.sdlc.server.depot.api;

import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.finos.legend.sdlc.server.depot.DepotServerInfo;
import org.finos.legend.sdlc.server.depot.auth.AuthClientInjector;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.tools.StringTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

abstract class BaseDepotApi
{
    private static final Logger LOGGER = LoggerFactory.getLogger(BaseDepotApi.class);

    private final DepotServerInfo serverInfo;
    private final AuthClientInjector authClientInjector;

    protected BaseDepotApi(DepotServerInfo serverInfo, AuthClientInjector authClientInjector)
    {
        LegendSDLCServerException.validateNonNull(authClientInjector, "Auth client injector may be null");

        this.serverInfo = serverInfo;
        this.authClientInjector = authClientInjector;
    }

    public DepotServerInfo getServerInfo()
    {
        return this.serverInfo;
    }

    protected URI buildURI(String path, List<NameValuePair> parameters)
    {
        URIBuilder builder = this.serverInfo.newURIBuilder().setPath(path);

        if (parameters != null)
        {
            builder.addParameters(parameters);
        }

        try
        {
            return builder.build();
        }
        catch (URISyntaxException ex)
        {
            throw new RuntimeException("Error building depot URI: " + builder.toString(), ex);
        }
    }

    protected String execute(HttpUriRequest request)
    {
        try (CloseableHttpClient client = this.authClientInjector.inject(HttpClientBuilder.create()).build();
              CloseableHttpResponse response = client.execute(request))
        {
            int statusCode = response.getStatusLine().getStatusCode();

            switch (statusCode)
            {
                case HttpStatus.SC_OK:
                case HttpStatus.SC_NO_CONTENT:
                {
                    return response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity(), "UTF-8");
                }
                case HttpStatus.SC_UNAUTHORIZED:
                case HttpStatus.SC_FORBIDDEN:
                {
                    throw new DepotServerException("Authentication failed. Server responded with code " + statusCode);
                }
                default:
                {
                    throw new DepotServerException("Server responded with code " + statusCode + ". Response received: " + response.getEntity());
                }
            }
        }
        catch (Exception ex)
        {
            LOGGER.error("Request {} {} failed.", request.getMethod(), request.getURI(), ex);
            throw new DepotServerException(this.serverInfo.getDepotURLString(), StringTools.appendThrowableMessageIfPresent("Error getting data from Depot", ex), ex);
        }
    }
}
