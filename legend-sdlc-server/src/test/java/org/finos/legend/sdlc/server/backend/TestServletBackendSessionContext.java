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

import org.finos.legend.sdlc.backend.api.spi.BackendSessionStateStore;
import org.finos.legend.sdlc.server.auth.LegendSDLCWebFilter;
import org.finos.legend.sdlc.server.auth.StateSession;
import org.finos.legend.sdlc.server.auth.StateSessionBuilder;
import org.finos.legend.sdlc.server.guice.UserContext;
import org.junit.Before;
import org.junit.Test;
import org.pac4j.core.profile.CommonProfile;

import java.util.Collections;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestServletBackendSessionContext
{
    private HttpServletRequest httpRequest;
    private HttpServletResponse httpResponse;
    private ServletContext servletContext;

    @Before
    public void setUp()
    {
        CommonProfile profile = new CommonProfile();
        profile.setId("user1");
        StateSession stateSession = StateSessionBuilder.newBuilder().withProfile(profile).build();

        this.servletContext = mock(ServletContext.class);

        this.httpRequest = mock(HttpServletRequest.class);
        when(this.httpRequest.getServletContext()).thenReturn(this.servletContext);
        when(this.httpRequest.getAttribute(LegendSDLCWebFilter.SESSION_ATTRIBUTE)).thenReturn(stateSession);
        when(this.httpRequest.getCookies()).thenReturn(null);

        this.httpResponse = mock(HttpServletResponse.class);
        when(this.httpResponse.getHeaders("Set-Cookie")).thenReturn(Collections.emptyList());
    }

    @Test
    public void testPut_resolvesSessionStoreOnlyOnceAcrossMultiplePutCallsOnTheSameStateStore()
    {
        UserContext userContext = new UserContext(this.httpRequest, this.httpResponse);
        ServletBackendSessionContext context = new ServletBackendSessionContext(userContext);

        BackendSessionStateStore store = context.getStateStore();
        store.put("APP_ID", "app1");
        store.put("TOKEN_TYPE", "PRIVATE");
        store.put("TOKEN", "tok123");

        verify(this.servletContext, times(1)).getAttribute(LegendSDLCWebFilter.SESSION_STORE);
    }
}
