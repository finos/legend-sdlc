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

package org.finos.legend.sdlc.server.gitlab.resources;

import org.finos.legend.sdlc.server.auth.Session;
import org.finos.legend.sdlc.server.gitlab.GitLabAppInfo;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabAuthAccessException;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabAuthorizerManager;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.resources.BaseResource;
import org.finos.legend.sdlc.server.tools.SessionUtil;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/auth")
public class GitLabAuthCheckResource extends BaseResource
{

    private final HttpServletRequest httpRequest;
    private final HttpServletResponse httpResponse;
    private final GitLabAuthorizerManager authorizerManager;
    private final GitLabAppInfo appInfo;

    @Inject
    public GitLabAuthCheckResource(HttpServletRequest httpRequest, HttpServletResponse httpResponse, GitLabAuthorizerManager authorizerManager, GitLabAppInfo appInfo)
    {
        super();
        this.httpRequest = httpRequest;
        this.httpResponse = httpResponse;
        this.authorizerManager = authorizerManager;
        this.appInfo = appInfo;
    }

    @GET
    @Path("authorized")
    @Produces(MediaType.APPLICATION_JSON)
    public boolean isAuthorized()
    {
        return executeWithLogging("checking authorization", () ->
        {
            Session session = SessionUtil.findSession(httpRequest);

            if (session != null)
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
