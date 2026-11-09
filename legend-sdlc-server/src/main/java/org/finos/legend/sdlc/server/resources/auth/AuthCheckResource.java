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
import org.finos.legend.sdlc.server.auth.CommonProfileSession;
import org.finos.legend.sdlc.server.auth.Session;
import org.finos.legend.sdlc.server.backend.ServletBackendSessionContext;
import org.finos.legend.sdlc.server.guice.UserContext;
import org.finos.legend.sdlc.server.resources.BaseResource;
import org.finos.legend.sdlc.server.tools.SessionProvider;
import org.finos.legend.server.pac4j.LegendPac4jConfiguration;
import org.finos.legend.server.pac4j.gitlab.GitlabPersonalAccessTokenClient;
import org.pac4j.core.client.Client;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.util.CommonHelper;
import org.pac4j.core.util.Pac4jConstants;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

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
            String clientName = this.httpRequest.getParameter(Pac4jConstants.DEFAULT_CLIENT_NAME_PARAMETER);

            if (session == null)
            {
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
            if (!isFromClient(session, clientName))
            {
                // This path bypasses pac4j, but a request naming another client goes through it, and pac4j refuses that
                // client on a session it didn't log in: saying true here leaves the caller (e.g. Studio) stuck on 401s.
                getLogger().debug("Session was not logged in by the requested client {}: not authorized", clientName);
                return false;
            }
            return this.backendProvider.get().newSession(new ServletBackendSessionContext(new UserContext(this.httpRequest, this.httpResponse))).isAuthorized();
        });
    }

    /**
     * Whether the session was logged in by the client the request names (pac4j matches client names ignoring case).
     * No client named, or a session whose client isn't known, keeps the previous answer.
     */
    static boolean isFromClient(Session session, String requestedClientName)
    {
        if (CommonHelper.isBlank(requestedClientName) || !(session instanceof CommonProfileSession))
        {
            return true;
        }
        CommonProfile profile = ((CommonProfileSession<?>) session).getProfile();
        String sessionClientName = (profile == null) ? null : profile.getClientName();
        return CommonHelper.isBlank(sessionClientName) || CommonHelper.areEqualsIgnoreCaseAndTrim(sessionClientName, requestedClientName);
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
