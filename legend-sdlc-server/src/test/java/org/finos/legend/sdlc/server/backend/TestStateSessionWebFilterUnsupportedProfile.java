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

package org.finos.legend.sdlc.server.backend;

import org.finos.legend.sdlc.server.auth.LegendSDLCWebFilter;
import org.junit.Test;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.profile.AnonymousProfile;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.core.util.Pac4jConstants;
import org.pac4j.jee.context.session.JEESessionStore;

import java.util.LinkedHashMap;
import java.util.Optional;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class TestStateSessionWebFilterUnsupportedProfile
{
    @Test
    public void unsupportedCommonProfileIsRejected() throws Exception
    {
        CommonProfile profile = new CommonProfile();
        profile.setId("alice");
        assertRejected(profile);
    }

    @Test
    public void persistedAnonymousProfileIsServedUnauthenticated() throws Exception
    {
        HttpServletRequest request = requestWithProfile(new AnonymousProfile());
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        new StateSessionWebFilter().doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(response);
    }

    private static void assertRejected(UserProfile profile) throws Exception
    {
        HttpServletRequest request = requestWithProfile(profile);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        new StateSessionWebFilter().doFilter(request, response, chain);

        verifyNoInteractions(chain);
        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response, never()).setStatus(anyInt());
        verify(response, never()).sendError(anyInt(), anyString());
    }

    private static HttpServletRequest requestWithProfile(UserProfile profile)
    {
        LinkedHashMap<String, UserProfile> profiles = new LinkedHashMap<>();
        profiles.put(profile.getClientName() == null ? "client" : profile.getClientName(), profile);
        JEESessionStore store = new JEESessionStore()
        {
            @Override
            public Optional<Object> get(WebContext context, String key)
            {
                return Pac4jConstants.USER_PROFILES.equals(key) ? Optional.of(profiles) : Optional.empty();
            }
        };
        ServletContext servletContext = mock(ServletContext.class);
        when(servletContext.getAttribute(LegendSDLCWebFilter.SESSION_STORE)).thenReturn(store);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getServletContext()).thenReturn(servletContext);
        return request;
    }
}
