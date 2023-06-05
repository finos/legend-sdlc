// Copyright 2023 Goldman Sachs
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

package org.finos.legend.sdlc.server.gitlab.resources;

import org.finos.legend.sdlc.server.auth.Session;
import org.finos.legend.sdlc.server.gitlab.GitLabAppInfo;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabAuthAccessException;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabAuthorizerManager;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabSession;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.resources.BaseResource;
import org.finos.legend.sdlc.server.tools.SessionProvider;
import org.pac4j.core.context.Pac4jConstants;

import javax.inject.Inject;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Arrays;
import java.util.Optional;

import static org.finos.legend.sdlc.server.auth.LegendSDLCWebFilter.SESSION_ATTRIBUTE;

@Path("/auth")
public class GitLabAuthCheckResource extends BaseResource
{

    private static final String SESSION_COOKIE_NAME = "LegendSSO";

    private final HttpServletRequest httpRequest;
    private final HttpServletResponse httpResponse;
    private final GitLabAuthorizerManager authorizerManager;
    private final GitLabAppInfo appInfo;
    private final SessionProvider sessionProvider;

    @Inject
    public GitLabAuthCheckResource(HttpServletRequest httpRequest,
                                   HttpServletResponse httpResponse,
                                   GitLabAuthorizerManager authorizerManager,
                                   GitLabAppInfo appInfo,
                                   SessionProvider sessionProvider)
    {
        super();
        this.httpRequest = httpRequest;
        this.httpResponse = httpResponse;
        this.authorizerManager = authorizerManager;
        this.appInfo = appInfo;
        this.sessionProvider = sessionProvider;
    }

    @GET
    @Path("authorized")
    @Produces(MediaType.APPLICATION_JSON)
    public boolean isAuthorized()
    {
        return executeWithLogging("checking authorization", () ->
        {
            Session session = SessionProvider.findSession(httpRequest);


            if (session == null && httpRequest.getCookies() != null)
            {
                Optional<Cookie> optional =
                        Arrays.stream(httpRequest.getCookies())
                        .filter(c -> c.getName().equals(SESSION_COOKIE_NAME))
                        .findFirst();

                if (optional.isPresent())
                {
                    session = sessionProvider.getSession(httpRequest, httpResponse, appInfo, Pac4jConstants.USER_PROFILES);
                    httpRequest.setAttribute(SESSION_ATTRIBUTE, session);
                }
            }


            if (session instanceof GitLabSession)
            {
                GitLabUserContext gitLabUserContext = new GitLabUserContext(httpRequest, httpResponse, authorizerManager, appInfo);
                try
                {
                    return gitLabUserContext.isUserAuthorized();
                }
                catch (GitLabAuthAccessException e)
                {
                    getLogger().error("Access exception occurred while checking authorization", e);
                }
            }
            return false;
        });
    }
}
