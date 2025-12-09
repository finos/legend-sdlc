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
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.pac4j.core.context.JEEContext;
import org.pac4j.core.context.session.JEESessionStore;
import org.pac4j.core.context.session.SessionStore;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LegendSDLCWebFilterTest
{

    @Mock
    private HttpServletRequest request;

    @Mock
    private ServletContext servletContext;

    @Before
    public void setUp()
    {
        MockitoAnnotations.openMocks(this);
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
        SessionStore<JEEContext> actualSessionStore = LegendSDLCWebFilter.getSessionStore(request);

        // Verify
        assertEquals(JEESessionStore.INSTANCE, actualSessionStore);
        verify(servletContext).getAttribute(LegendSDLCWebFilter.SESSION_STORE);
    }

}
