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

import org.finos.legend.server.pac4j.hazelcaststore.HazelcastSessionStore;
import org.finos.legend.server.pac4j.mongostore.MongoDbSessionStore;
import org.finos.legend.server.pac4j.sessionutil.SessionToken;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.WebContextHelper;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.jee.context.JEEContext;
import org.pac4j.jee.context.session.JEESessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

public abstract class LegendSDLCWebFilter<P extends CommonProfile> implements Filter
{
    public static final String SESSION_TOKEN_COOKIE_NAME = "LegendSDLCSession";
    public static final String SESSION_ATTRIBUTE = "org.finos.legend.sdlc.server.Session";
    public static final String SESSION_STORE = "PAC4J_SESSION_STORE";

    protected static final Logger LOGGER = LoggerFactory.getLogger(LegendSDLCWebFilter.class);

    private final Class<P> profileClass;

    /**
     * {@code profileClass} is needed because erasure hides {@code P}: an {@code instanceof CommonProfile}
     * check would let {@link #narrowProfiles} accept profiles of any {@code CommonProfile} subtype.
     */
    protected LegendSDLCWebFilter(Class<P> profileClass)
    {
        this.profileClass = profileClass;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException
    {
        HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
        HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
        SessionStore sessionStore = getSessionStore(httpRequest);
        WebContext webContext = new JEEContext(httpRequest, httpResponse);
        ProfileManager manager = new ProfileManager(webContext, sessionStore);
        List<UserProfile> rawProfiles;
        try
        {
            rawProfiles = manager.getProfiles();
        }
        catch (RuntimeException e)
        {
            // The session stores rethrow transient failures (Mongo or Hazelcast unreachable) rather
            // than report no session. Passing through would serve an authenticated user as
            // anonymous; 503 tells the client to retry.
            LOGGER.error("Error reading authentication profiles from the session store", e);
            httpResponse.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }
        if (rawProfiles.isEmpty())
        {
            LOGGER.debug("No authentication profiles found. Passing through.");
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        List<P> profiles = narrowProfiles(rawProfiles);
        if (profiles.isEmpty())
        {
            // Fail closed: passing through would downgrade an authenticated request to anonymous.
            LOGGER.warn("All {} authentication profile(s) discarded by narrowing; rejecting", rawProfiles.size());
            httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED);
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

        cacheSession(session, httpRequest, (sessionCookie == null) ? httpResponse : null, httpSession, sessionStore);
        try (SessionContext sessionContext = new SessionContext(session))
        {
            filterChain.doFilter(httpRequest, httpResponse);
        }
    }

    List<P> narrowProfiles(List<UserProfile> rawProfiles)
    {
        List<P> narrowed = new ArrayList<>(rawProfiles.size());
        for (UserProfile profile : rawProfiles)
        {
            if (this.profileClass.isInstance(profile))
            {
                narrowed.add(this.profileClass.cast(profile));
            }
        }
        return narrowed;
    }

    public static SessionStore getSessionStore(HttpServletRequest httpRequest)
    {
        Object sessionStore = httpRequest.getServletContext().getAttribute(SESSION_STORE);
        if (Objects.nonNull(sessionStore) && sessionStore instanceof SessionStore)
        {
            return (SessionStore) sessionStore;
        }
        return new JEESessionStore();
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

    private void cacheSession(Session session, HttpServletRequest httpRequest, HttpServletResponse httpResponse, HttpSession httpSession, SessionStore sessionStore)
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
            setSessionCookie(httpResponse, session, sessionStore);
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

    /**
     * Uses the session store's SameSite policy so this cookie is delivered like LegendSSO. The header
     * is built by pac4j's {@code WebContextHelper}, which adds {@code Secure} when the policy is
     * {@code None}; browsers reject {@code SameSite=None} without it.
     */
    public static void setSessionCookie(HttpServletRequest httpRequest, HttpServletResponse httpResponse, Session session)
    {
        setSessionCookie(httpResponse, session, getSessionStore(httpRequest));
    }

    public static void setSessionCookie(HttpServletResponse httpResponse, Session session, SessionStore sessionStore)
    {
        String sameSite = sameSitePolicyFor(sessionStore);
        org.pac4j.core.context.Cookie cookie = new org.pac4j.core.context.Cookie(SESSION_TOKEN_COOKIE_NAME, session.encode());
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSameSitePolicy(sameSite);
        String newCookieHeader = WebContextHelper.createCookieHeader(cookie);
        replaceSessionCookieHeader(httpResponse, newCookieHeader);
    }

    private static String sameSitePolicyFor(SessionStore sessionStore)
    {
        if (sessionStore instanceof HazelcastSessionStore)
        {
            return ((HazelcastSessionStore) sessionStore).getSameSitePolicy();
        }
        if (sessionStore instanceof MongoDbSessionStore)
        {
            return ((MongoDbSessionStore) sessionStore).getSameSitePolicy();
        }
        return SessionToken.DEFAULT_SAME_SITE_POLICY;
    }

    private static void replaceSessionCookieHeader(HttpServletResponse httpResponse, String newCookieHeader)
    {
        List<String> otherCookies = httpResponse.getHeaders("Set-Cookie").stream()
                .filter(header -> !header.startsWith(SESSION_TOKEN_COOKIE_NAME + "="))
                .collect(Collectors.toList());
        httpResponse.setHeader("Set-Cookie", newCookieHeader);
        otherCookies.forEach(header -> httpResponse.addHeader("Set-Cookie", header));
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
