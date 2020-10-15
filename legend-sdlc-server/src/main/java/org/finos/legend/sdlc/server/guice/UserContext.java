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

package org.finos.legend.sdlc.server.guice;

import com.google.inject.servlet.RequestScoped;
import org.finos.legend.sdlc.server.auth.MetadataWebFilter;
import org.finos.legend.sdlc.server.auth.Session;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestWrapper;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@RequestScoped
public class UserContext
{
    private static final Logger LOGGER = LoggerFactory.getLogger(UserContext.class);

    protected final HttpServletRequest httpRequest;
    protected final HttpServletResponse httpResponse;
    protected final Session session;

    @Inject
    public UserContext(HttpServletRequest httpRequest, HttpServletResponse httpResponse)
    {
        this.httpRequest = httpRequest;
        this.httpResponse = httpResponse;
        this.session = LegendSDLCServerException.validateNonNull(findSession(httpRequest), "Invalid request");
    }

    public String getCurrentUser()
    {
        return this.session.getUserId();
    }

    public HttpServletRequest getHttpRequest()
    {
        return this.httpRequest;
    }

    private static Session findSession(ServletRequest request)
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
        Session sdlcSession = MetadataWebFilter.getSessionFromServletRequestAttribute(request);
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
                sdlcSession = MetadataWebFilter.getSessionFromHttpSession(httpSession);
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
