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

package org.finos.legend.sdlc.server.tools;

import org.finos.legend.sdlc.server.auth.LegendSDLCWebFilter;
import org.finos.legend.sdlc.server.auth.Session;
import org.finos.legend.sdlc.server.gitlab.GitLabAppInfo;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabSessionBuilder;
import org.finos.legend.server.pac4j.gitlab.GitlabClient;
import org.finos.legend.server.pac4j.gitlab.GitlabPersonalAccessTokenAuthenticator;
import org.finos.legend.server.pac4j.gitlab.GitlabPersonalAccessTokenClient;
import org.finos.legend.server.pac4j.gitlab.GitlabPersonalAccessTokenCredentials;
import org.finos.legend.server.pac4j.gitlab.GitlabPersonalAccessTokenExtractor;
import org.finos.legend.server.pac4j.gitlab.GitlabPersonalAccessTokenProfile;
import org.finos.legend.server.pac4j.gitlab.GitlabPersonalAccessTokenProfileCreator;
import org.pac4j.core.context.J2EContext;
import org.pac4j.core.context.Pac4jConstants;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.profile.CommonProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestWrapper;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;

public class SessionProvider
{

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionProvider.class);

    private final SessionStore sessionStore;

    public SessionProvider(SessionStore sessionStore)
    {
        this.sessionStore = sessionStore;
    }

    public Session getSessionFromSessionStore(HttpServletRequest httpRequest, HttpServletResponse httpResponse, GitLabAppInfo appInfo)
    {
        if (sessionStore != null)
        {
            WebContext context = new J2EContext(httpRequest, httpResponse);
            Map<String, CommonProfile> profileMap =
                    (Map<String, CommonProfile>) sessionStore.get(context, Pac4jConstants.USER_PROFILES);

            if (profileMap != null)
            {
                CommonProfile profile = profileMap.get(GitlabClient.GITLAB_CLIENT_NAME);
                if (profile != null)
                {
                    return GitLabSessionBuilder.newBuilder(appInfo).withProfile(profile).build();
                }
            }
        }

        return null;
    }

    public static Session findSession(ServletRequest request)
    {
        Session session = findSession(request, 0);
        if (session == null)
        {
            LOGGER.warn("Could not find SDLC session from request: {} (class: {})", request, request.getClass());
        }
        return session;
    }

    private static Session findSession(ServletRequest request, int depth)
    {
        Session sdlcSession = LegendSDLCWebFilter.getSessionFromServletRequestAttribute(request);
        if (sdlcSession != null)
        {
            LOGGER.debug("got SDLC session from request attribute (depth {})", depth);
            return sdlcSession;
        }

        if (request instanceof HttpServletRequest)
        {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpSession httpSession;
            try
            {
                httpSession = httpRequest.getSession(false);
            }
            catch (Exception e)
            {
                httpSession = null;
            }
            if (httpSession != null)
            {
                sdlcSession = LegendSDLCWebFilter.getSessionFromHttpSession(httpSession);
                if (sdlcSession != null)
                {
                    LOGGER.debug("got SDLC session from HTTP session (depth {})", depth);
                    return sdlcSession;
                }
            }
        }

        if (request instanceof ServletRequestWrapper)
        {
            return findSession(((ServletRequestWrapper) request).getRequest(), depth + 1);
        }

        LOGGER.debug("Did not find session at depth {}; no more nested requests to check; request: {}; request class: {}", depth, request, request.getClass());
        return null;
    }

    public static Session getSessionUsingGitlabPersonalAccessToken(HttpServletRequest httpRequest,
                                                                   HttpServletResponse httpResponse,
                                                                   GitLabAppInfo appInfo,
                                                                   GitlabPersonalAccessTokenClient client)
    {
        WebContext context = new J2EContext(httpRequest, httpResponse);
        GitlabPersonalAccessTokenExtractor extractor = new GitlabPersonalAccessTokenExtractor(client.headerTokenName);
        GitlabPersonalAccessTokenProfileCreator creator = new GitlabPersonalAccessTokenProfileCreator(client.host);
        GitlabPersonalAccessTokenAuthenticator authenticator = new GitlabPersonalAccessTokenAuthenticator(client.scheme, client.host, client.apiVersion);
        GitlabPersonalAccessTokenCredentials credentials = extractor.extract(context);

        try
        {
            authenticator.validate(credentials, context);
        }
        catch (Exception e)
        {
            LOGGER.error("Validation failed for PA token: {}", e.getMessage());
            return null;
        }

        GitlabPersonalAccessTokenProfile profile = creator.create(credentials, context);
        return GitLabSessionBuilder.newBuilder(appInfo).withProfile(profile).build();
    }
}
