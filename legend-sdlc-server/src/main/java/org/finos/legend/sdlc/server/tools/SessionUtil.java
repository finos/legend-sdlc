package org.finos.legend.sdlc.server.tools;

import org.finos.legend.sdlc.server.auth.LegendSDLCWebFilter;
import org.finos.legend.sdlc.server.auth.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestWrapper;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

public class SessionUtil
{

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionUtil.class);

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
}
