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

package org.finos.legend.sdlc.server.resources.auth;

import org.finos.legend.sdlc.backend.api.spi.Backend;
import org.finos.legend.sdlc.server.auth.Session;
import org.finos.legend.sdlc.server.backend.ServletBackendSessionContext;
import org.finos.legend.sdlc.server.guice.UserContext;
import org.finos.legend.sdlc.server.resources.BaseResource;
import org.finos.legend.sdlc.server.tools.SessionProvider;
import org.finos.legend.server.pac4j.LegendPac4jConfiguration;
import org.finos.legend.server.pac4j.gitlab.GitlabPersonalAccessTokenClient;
import org.pac4j.core.client.Client;
import org.pac4j.core.util.Pac4jConstants;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import static org.finos.legend.sdlc.server.auth.LegendSDLCWebFilter.SESSION_ATTRIBUTE;

/**
 * The generic authorization check ({@code GET /auth/authorized}), replacing the per-backend check resources:
 * finds (or bootstraps) the server session — including the personal-access-token header and session-store paths
 * the web filter cannot serve — and asks the backend session whether the user is authorized. No session means
 * not authorized, never an error.
 */
@Path("/auth")
public class AuthCheckResource extends BaseResource
{
    private final HttpServletRequest httpRequest;
    private final HttpServletResponse httpResponse;
    private final Provider<Backend> backendProvider;
    private final LegendPac4jConfiguration legendPac4jConfiguration;
    private final SessionProvider sessionProvider;

    @Inject
    public AuthCheckResource(HttpServletRequest httpRequest,
                             HttpServletResponse httpResponse,
                             Provider<Backend> backendProvider,
                             LegendPac4jConfiguration legendPac4jConfiguration,
                             SessionProvider sessionProvider)
    {
        super();
        this.httpRequest = httpRequest;
        this.httpResponse = httpResponse;
        this.backendProvider = backendProvider;
        this.legendPac4jConfiguration = legendPac4jConfiguration;
        this.sessionProvider = sessionProvider;
    }

    @GET
    @Path("authorized")
    @Produces(MediaType.APPLICATION_JSON)
    public boolean isAuthorized()
    {
        return executeWithLogging("checking authorization", () ->
        {
            Session session = SessionProvider.findSession(this.httpRequest);

            if (session == null)
            {
                String clientName = this.httpRequest.getParameter(Pac4jConstants.DEFAULT_CLIENT_NAME_PARAMETER);
                GitlabPersonalAccessTokenClient client = getGitlabPersonalAccessTokenClient(this.legendPac4jConfiguration, clientName);
                if ((client != null) && (this.httpRequest.getHeader(client.headerTokenName) != null))
                {
                    session = SessionProvider.getSessionUsingGitlabPersonalAccessToken(this.httpRequest, this.httpResponse, client);
                }
                else
                {
                    session = this.sessionProvider.getSessionFromSessionStore(this.httpRequest, this.httpResponse);
                }
                this.httpRequest.setAttribute(SESSION_ATTRIBUTE, session);
            }

            if (session == null)
            {
                return false;
            }
            return this.backendProvider.get().newSession(new ServletBackendSessionContext(new UserContext(this.httpRequest, this.httpResponse))).isAuthorized();
        });
    }

    private GitlabPersonalAccessTokenClient getGitlabPersonalAccessTokenClient(LegendPac4jConfiguration configuration, String requestClientName)
    {
        for (Client client : configuration.getClients())
        {
            if (client.getName().equals(requestClientName) && (client instanceof GitlabPersonalAccessTokenClient))
            {
                return (GitlabPersonalAccessTokenClient) client;
            }
        }
        return null;
    }
}
