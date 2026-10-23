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
import org.finos.legend.sdlc.server.auth.StateSessionBuilder;
import org.finos.legend.server.pac4j.gitlab.GitlabClient;
import org.finos.legend.server.pac4j.gitlab.GitlabPersonalAccessTokenClient;
import org.finos.legend.server.pac4j.gitlab.GitlabPersonalAccessTokenProfile;
import org.pac4j.core.context.CallContext;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.credentials.Credentials;
import org.pac4j.core.credentials.authenticator.Authenticator;
import org.pac4j.core.credentials.extractor.CredentialsExtractor;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.core.profile.creator.ProfileCreator;
import org.pac4j.core.util.Pac4jConstants;
import org.pac4j.jee.context.JEEContext;
import org.pac4j.jee.context.session.JEESessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import java.util.Optional;

public class SessionProvider
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionProvider.class);

    private final SessionStore sessionStore;

    public SessionProvider(SessionStore sessionStore)
    {
        this.sessionStore = sessionStore;
    }

    public Session getSessionFromSessionStore(HttpServletRequest httpRequest, HttpServletResponse httpResponse)
    {
        if (sessionStore != null)
        {
            WebContext context = new JEEContext(httpRequest, httpResponse);
            Map<String, CommonProfile> profileMap =
                    (Map<String, CommonProfile>) sessionStore.get(context, Pac4jConstants.USER_PROFILES).orElse(null);

            if (profileMap != null)
            {
                CommonProfile profile = profileMap.get(GitlabClient.GITLAB_CLIENT_NAME);
                if (profile != null)
                {
                    return StateSessionBuilder.newBuilder().withProfile(profile).build();
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
                                                                   GitlabPersonalAccessTokenClient client)
    {
        WebContext webContext = new JEEContext(httpRequest, httpResponse);
        CallContext callContext = new CallContext(webContext, new JEESessionStore());

        client.init();
        return getSessionUsingGitlabPersonalAccessToken(callContext, client.getCredentialsExtractor(), client.getAuthenticator(), client.getProfileCreator());
    }

    static Session getSessionUsingGitlabPersonalAccessToken(CallContext callContext,
                                                             CredentialsExtractor extractor,
                                                             Authenticator authenticator,
                                                             ProfileCreator creator)
    {
        Optional<Credentials> extracted;
        try
        {
            extracted = extractor.extract(callContext);
        }
        catch (Exception e)
        {
            LOGGER.error("Could not extract PA token credentials", e);
            return null;
        }
        if (!extracted.isPresent())
        {
            LOGGER.error("No PA token credentials found on the request");
            return null;
        }

        // pac4j 6 may reject a token by returning an empty Optional instead of throwing
        Optional<Credentials> validated;
        try
        {
            validated = authenticator.validate(callContext, extracted.get());
        }
        catch (Exception e)
        {
            LOGGER.error("Validation failed for PA token", e);
            return null;
        }
        if (!validated.isPresent())
        {
            LOGGER.error("Validation failed for PA token: authenticator returned no credentials");
            return null;
        }

        // validate() fills in the GitLab user id and name, so the profile is built from its result, not the extracted credentials
        Optional<UserProfile> created;
        try
        {
            created = creator.create(callContext, validated.get());
        }
        catch (Exception e)
        {
            LOGGER.error("Profile creation failed for PA token", e);
            return null;
        }
        if (!created.isPresent())
        {
            LOGGER.error("Profile creation declined for PA token");
            return null;
        }

        GitlabPersonalAccessTokenProfile profile = (GitlabPersonalAccessTokenProfile) created.get();
        return StateSessionBuilder.newBuilder().withProfile(profile).build();
    }
}
