// Copyright 2025 Goldman Sachs
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
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.core.util.Pac4jConstants;
import org.pac4j.jee.context.session.JEESessionStore;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LegendSDLCWebFilterTest
{

    @Mock
    private HttpServletRequest request;

    @Mock
    private ServletContext servletContext;

    @Mock
    private HttpServletResponse response;

    @Mock
    private Session session;

    @Before
    public void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(request.getServletContext()).thenReturn(servletContext);
        when(session.encode()).thenReturn("tok123");
    }

    private String capturedSetCookieHeader()
    {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(response).setHeader(eq("Set-Cookie"), captor.capture());
        return captor.getValue();
    }

    @Test
    public void testSetSessionCookie_addsSecureWhenSameSitePolicyIsNone()
    {
        HazelcastSessionStore sessionStore = mock(HazelcastSessionStore.class);
        when(sessionStore.getSameSitePolicy()).thenReturn("None");
        when(servletContext.getAttribute(LegendSDLCWebFilter.SESSION_STORE)).thenReturn(sessionStore);
        when(response.getHeaders("Set-Cookie")).thenReturn(Collections.emptyList());

        LegendSDLCWebFilter.setSessionCookie(request, response, session);

        String header = capturedSetCookieHeader();
        assertTrue("SameSite=None cookies must be marked Secure or browsers reject them outright: " + header,
                header.toLowerCase().contains("secure"));
        assertTrue(header.contains("HttpOnly"));
        assertTrue(header.toLowerCase().contains("samesite=none"));
    }

    @Test
    public void testSetSessionCookie_defaultSameSiteStillSetsHttpOnly()
    {
        when(servletContext.getAttribute(LegendSDLCWebFilter.SESSION_STORE)).thenReturn(null);
        when(response.getHeaders("Set-Cookie")).thenReturn(Collections.emptyList());

        LegendSDLCWebFilter.setSessionCookie(request, response, session);

        String header = capturedSetCookieHeader();
        assertTrue(header.contains("HttpOnly"));
        assertTrue(header.toLowerCase().contains("samesite=lax"));
    }

    @Test
    public void testSetSessionCookie_preservesOtherExistingCookies()
    {
        when(servletContext.getAttribute(LegendSDLCWebFilter.SESSION_STORE)).thenReturn(null);
        when(response.getHeaders("Set-Cookie")).thenReturn(
                Collections.singletonList("LegendSSO=abc; Path=/; HttpOnly"));

        LegendSDLCWebFilter.setSessionCookie(request, response, session);

        capturedSetCookieHeader();
        verify(response).addHeader("Set-Cookie", "LegendSSO=abc; Path=/; HttpOnly");
    }

    @Test
    public void testSetSessionCookie_doesNotDuplicateItsOwnPriorHeader()
    {
        when(servletContext.getAttribute(LegendSDLCWebFilter.SESSION_STORE)).thenReturn(null);
        when(response.getHeaders("Set-Cookie")).thenReturn(Arrays.asList(
                LegendSDLCWebFilter.SESSION_TOKEN_COOKIE_NAME + "=stale; Path=/; HttpOnly",
                "LegendSSO=abc; Path=/; HttpOnly"));

        LegendSDLCWebFilter.setSessionCookie(request, response, session);

        capturedSetCookieHeader();
        verify(response).addHeader("Set-Cookie", "LegendSSO=abc; Path=/; HttpOnly");
        verify(response, never()).addHeader("Set-Cookie",
                LegendSDLCWebFilter.SESSION_TOKEN_COOKIE_NAME + "=stale; Path=/; HttpOnly");
    }

    @Test
    public void testGetSessionStore_returnsCustomSessionStoreWhenAvailable()
    {
        // Setup
        HazelcastSessionStore expectedSessionStore = mock(HazelcastSessionStore.class);
        when(request.getServletContext()).thenReturn(servletContext);
        when(servletContext.getAttribute(LegendSDLCWebFilter.SESSION_STORE)).thenReturn(expectedSessionStore);

        // Execute
        SessionStore actualSessionStore = LegendSDLCWebFilter.getSessionStore(request);

        // Verify
        assertTrue(actualSessionStore instanceof HazelcastSessionStore);
        verify(servletContext).getAttribute(LegendSDLCWebFilter.SESSION_STORE);

    }

    @Test
    public void testGetSessionStore_returnsDefaultSessionStoreWhenNotAvailable()
    {
        // Setup
        when(request.getServletContext()).thenReturn(servletContext);
        when(servletContext.getAttribute(LegendSDLCWebFilter.SESSION_STORE)).thenReturn(null);

        // Execute
        SessionStore actualSessionStore = LegendSDLCWebFilter.getSessionStore(request);

        // Verify
        assertTrue(actualSessionStore instanceof JEESessionStore);
        verify(servletContext).getAttribute(LegendSDLCWebFilter.SESSION_STORE);
    }

    private static final class NarrowerProfile extends CommonProfile
    {
    }

    private static final class TestNarrowingFilter extends LegendSDLCWebFilter<NarrowerProfile>
    {
        TestNarrowingFilter()
        {
            super(NarrowerProfile.class);
        }

        @Override
        public void init(FilterConfig filterConfig)
        {
        }

        @Override
        public void destroy()
        {
        }

        @Override
        protected Session newSession(List<NarrowerProfile> profiles, Cookie sessionCookie)
        {
            return null;
        }
    }

    @Test
    public void testNarrowProfiles_rejectsProfileOfWrongConcreteType()
    {
        TestNarrowingFilter filter = new TestNarrowingFilter();
        UserProfile wrongTypeProfile = new CommonProfile();

        List<NarrowerProfile> narrowed = filter.narrowProfiles(Collections.singletonList(wrongTypeProfile));

        assertTrue(
                "a CommonProfile that is not this filter's declared P (NarrowerProfile) must be "
                        + "rejected by narrowing, not silently cast through via the generic upper bound",
                narrowed.isEmpty());
    }

    @Test
    public void testNarrowProfiles_acceptsProfileOfDeclaredType()
    {
        TestNarrowingFilter filter = new TestNarrowingFilter();
        NarrowerProfile rightTypeProfile = new NarrowerProfile();

        List<NarrowerProfile> narrowed = filter.narrowProfiles(Collections.singletonList(rightTypeProfile));

        assertEquals(1, narrowed.size());
        assertTrue(narrowed.get(0) == rightTypeProfile);
    }

    @Test
    public void testDoFilter_answers503WhenSessionStoreIsUnreachable() throws Exception
    {
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);
        SessionStore sessionStore = mock(SessionStore.class);
        when(sessionStore.get(any(), any())).thenThrow(new RuntimeException("session store unreachable"));
        when(this.request.getServletContext()).thenReturn(this.servletContext);
        when(this.servletContext.getAttribute(LegendSDLCWebFilter.SESSION_STORE)).thenReturn(sessionStore);

        newUnreachableStoreFilter().doFilter(this.request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        verify(filterChain, never()).doFilter(any(), any());
    }

    private static Filter newUnreachableStoreFilter()
    {
        return new LegendSDLCWebFilter<CommonProfile>(CommonProfile.class)
        {
            @Override
            public void init(FilterConfig filterConfig)
            {
            }

            @Override
            public void destroy()
            {
            }

            @Override
            protected Session newSession(List<CommonProfile> profiles, Cookie sessionCookie)
            {
                throw new AssertionError("newSession must not be reached when the session store is unreachable");
            }
        };
    }

    @Test
    public void testDoFilter_rejectsRequestWhenEveryProfileIsDiscardedByNarrowing() throws Exception
    {
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);
        givenProfiles(mock(UserProfile.class));

        newRejectingFilter().doFilter(this.request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    public void testDoFilter_passesThroughWhenNoProfilesAreFoundAtAll() throws Exception
    {
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);
        givenProfiles();

        newRejectingFilter().doFilter(this.request, response, filterChain);

        verify(filterChain).doFilter(any(), any());
        verify(response, never()).sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }

    private void givenProfiles(UserProfile... profiles)
    {
        Map<String, UserProfile> profileMap = new LinkedHashMap<>();
        for (int i = 0; i < profiles.length; i++)
        {
            profileMap.put("client" + i, profiles[i]);
        }
        SessionStore sessionStore = mock(SessionStore.class);
        when(sessionStore.get(any(), any())).thenReturn(Optional.empty());
        when(sessionStore.get(any(), eq(Pac4jConstants.USER_PROFILES))).thenReturn(Optional.of(profileMap));
        when(this.request.getServletContext()).thenReturn(this.servletContext);
        when(this.servletContext.getAttribute(LegendSDLCWebFilter.SESSION_STORE)).thenReturn(sessionStore);
    }

    private static Filter newRejectingFilter()
    {
        return new LegendSDLCWebFilter<CommonProfile>(CommonProfile.class)
        {
            @Override
            public void init(FilterConfig filterConfig)
            {
            }

            @Override
            public void destroy()
            {
            }

            @Override
            protected Session newSession(List<CommonProfile> profiles, Cookie sessionCookie)
            {
                throw new AssertionError("newSession must not be reached when every profile was discarded");
            }
        };
    }

}
