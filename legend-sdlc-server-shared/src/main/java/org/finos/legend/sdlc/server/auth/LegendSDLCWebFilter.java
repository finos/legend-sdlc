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

package org.finos.legend.sdlc.server.auth;

import org.pac4j.core.context.J2EContext;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.profile.ProfileManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public abstract class LegendSDLCWebFilter<P extends CommonProfile> implements Filter
{
    public static final String SESSION_TOKEN_COOKIE_NAME = "LegendSDLCSession";
    public static final String SESSION_ATTRIBUTE = "org.finos.legend.sdlc.server.Session";

    protected static final Logger LOGGER = LoggerFactory.getLogger(LegendSDLCWebFilter.class);

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException
    {
        HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
        HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
        WebContext webContext = new J2EContext(httpRequest, httpResponse);

        ProfileManager<P> manager = new ProfileManager<>(webContext);
        List<P> profiles = manager.getAll(true);
        if (profiles.isEmpty())
        {
            LOGGER.debug("No authentication profiles found. Passing through.");
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }
        LOGGER.debug("Found {} profiles: {}", profiles.size(), profiles);

        // First check if we have one in a request attribute
        Session sessionFromServletRequestAttribute = getSessionFromServletRequestAttribute(servletRequest);
        if (sessionFromServletRequestAttribute != null)
        {
            if (sessionFromServletRequestAttribute.isValid())
            {
                LOGGER.debug("metadata session found in request attribute: {}", sessionFromServletRequestAttribute);
                // TODO should we cache this in a cookie?
                try (SessionContext sessionContext = new SessionContext(sessionFromServletRequestAttribute))
                {
                    filterChain.doFilter(httpRequest, servletResponse);
                }
                return;
            }
            else
            {
                LOGGER.debug("Invalid metadata session removed from request: {}", sessionFromServletRequestAttribute);
                removeSessionAttributeFromServletRequest(servletRequest);
            }
        }

        HttpSession httpSession = getHttpSession(httpRequest);

        // Next check if we have one cached in the HTTP session
        if (httpSession != null)
        {
            Session sessionFromHttpSession = getSessionFromHttpSession(httpSession);
            if (sessionFromHttpSession != null)
            {
                if (sessionFromHttpSession.isValid())
                {
                    LOGGER.debug("session found in HTTP session: {}", sessionFromHttpSession);
                    setSessionAttributeOnServletRequest(servletRequest, sessionFromHttpSession);
                    // TODO should we cache this in a cookie?
                    try (SessionContext sessionContext = new SessionContext(sessionFromHttpSession))
                    {
                        filterChain.doFilter(httpRequest, httpResponse);
                    }
                    return;
                }
                else
                {
                    LOGGER.debug("Invalid session removed from HTTP session: {}", sessionFromHttpSession);
                    removeSessionFromHttpSession(httpSession);
                }
            }
        }

        // Get session cookie, if present
        Cookie sessionCookie = getSessionCookie(httpRequest);
        if (sessionCookie != null)
        {
            LOGGER.debug("Session cookie found: {}", sessionCookie.getValue());
        }

        Session session;
        try
        {
            session = newSession(profiles, sessionCookie);
        }
        catch (RuntimeException | IOException | ServletException e)
        {
            LOGGER.error("Error creating session", e);
            throw e;
        }

        if (session == null)
        {
            LOGGER.warn("No session created");
            return;
        }

        cacheSession(session, httpRequest, (sessionCookie == null) ? httpResponse : null, httpSession);
        try (SessionContext sessionContext = new SessionContext(session))
        {
            filterChain.doFilter(httpRequest, httpResponse);
        }
    }

    protected abstract Session newSession(List<P> profiles, Cookie sessionCookie) throws IOException, ServletException;

    private HttpSession getHttpSession(HttpServletRequest httpRequest)
    {
        try
        {
            return httpRequest.getSession();
        }
        catch (Exception e)
        {
            LOGGER.warn("error getting session from HttpServletRequest", e);
            return null;
        }
    }

    private void cacheSession(Session session, HttpServletRequest httpRequest, HttpServletResponse httpResponse, HttpSession httpSession)
    {
        if (httpRequest != null)
        {
            setSessionAttributeOnServletRequest(httpRequest, session);
        }
        if (httpSession != null)
        {
            setSessionOnHttpSession(httpSession, session);
        }
        if (httpResponse != null)
        {
            setSessionCookie(httpResponse, session);
        }
    }

    public static Session getSessionFromServletRequestAttribute(ServletRequest request)
    {
        Object value = request.getAttribute(SESSION_ATTRIBUTE);
        return (value instanceof Session) ? (Session) value : null;
    }

    public static void setSessionAttributeOnServletRequest(ServletRequest request, Session session)
    {
        request.setAttribute(SESSION_ATTRIBUTE, session);
    }

    public static void removeSessionAttributeFromServletRequest(ServletRequest request)
    {
        request.removeAttribute(SESSION_ATTRIBUTE);
    }

    public static Session getSessionFromHttpSession(HttpSession httpSession)
    {
        Object value = httpSession.getAttribute(SESSION_ATTRIBUTE);
        return (value instanceof Session) ? (Session) value : null;
    }

    public static void setSessionOnHttpSession(HttpSession httpSession, Session session)
    {
        httpSession.setAttribute(SESSION_ATTRIBUTE, session);
    }

    public static void removeSessionFromHttpSession(HttpSession httpSession)
    {
        httpSession.removeAttribute(SESSION_ATTRIBUTE);
    }

    public static Cookie getSessionCookie(HttpServletRequest httpRequest)
    {
        Cookie[] cookies = httpRequest.getCookies();
        if (cookies != null)
        {
            for (int i = 0; i < cookies.length; i++)
            {
                Cookie cookie = cookies[i];
                if (SESSION_TOKEN_COOKIE_NAME.equalsIgnoreCase(cookie.getName()))
                {
                    return cookie;
                }
            }
        }
        return null;
    }

    public static void setSessionCookie(HttpServletResponse httpResponse, Session session)
    {
        // TODO should we make this Secure and HttpOnly?
        String value = SESSION_TOKEN_COOKIE_NAME + "=" + session.encode() + "; path=/";
        httpResponse.setHeader("Set-Cookie", value);
    }

    private static class SessionContext implements AutoCloseable
    {
        private final Thread currentThread;
        private final String oldThreadName;

        private SessionContext(Session session)
        {
            String userId = session.getUserId();
            if (userId == null)
            {
                this.currentThread = null;
                this.oldThreadName = null;
            }
            else
            {
                Thread thread = Thread.currentThread();
                String name = thread.getName();
                if (name == null)
                {
                    this.currentThread = null;
                    this.oldThreadName = null;
                }
                else
                {
                    this.currentThread = thread;
                    this.oldThreadName = name;
                    thread.setName(name + " - " + userId);
                }
            }
        }

        @Override
        public void close()
        {
            if (this.currentThread != null)
            {
                this.currentThread.setName(this.oldThreadName);
            }
        }
    }
}
