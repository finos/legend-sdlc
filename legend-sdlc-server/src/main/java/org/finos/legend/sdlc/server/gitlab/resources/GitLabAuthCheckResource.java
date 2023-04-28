package org.finos.legend.sdlc.server.gitlab.resources;

import org.finos.legend.sdlc.server.auth.Session;
import org.finos.legend.sdlc.server.gitlab.GitLabAppInfo;
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

            if (session == null)
            {
                return  false;
            } else
            {
                GitLabUserContext gitLabUserContext = new GitLabUserContext(httpRequest, httpResponse, authorizerManager, appInfo);
                return gitLabUserContext.isUserAuthorized();
            }
        });

    }

}
