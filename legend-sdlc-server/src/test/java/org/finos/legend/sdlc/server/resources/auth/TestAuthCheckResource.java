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

package org.finos.legend.sdlc.server.resources.auth;

import org.finos.legend.sdlc.error.LegendSDLCException;
import org.finos.legend.sdlc.server.tools.SessionProvider;
import org.finos.legend.server.pac4j.LegendPac4jConfiguration;
import org.finos.legend.server.pac4j.gitlab.GitlabPersonalAccessTokenClient;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.pac4j.core.client.Client;
import org.pac4j.core.util.Pac4jConstants;

import java.util.Arrays;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Must choose the same client as legend-shared's {@code LegendClientFinder}, which reads only
 * {@code client_name} and deliberately ignores pac4j's {@code force_client}. No token header is
 * ever present, so the chosen client shows only in which header is looked up.
 */
public class TestAuthCheckResource
{
    private static final String CLIENT_A = "patClientA";
    private static final String HEADER_A = "X-Token-A";
    private static final String CLIENT_B = "patClientB";
    private static final String HEADER_B = "X-Token-B";

    private HttpServletRequest request;
    private HttpServletResponse response;
    private SessionProvider sessionProvider;
    private AuthCheckResource resource;

    @Before
    public void setUp()
    {
        this.request = mock(HttpServletRequest.class);
        this.response = mock(HttpServletResponse.class);
        this.sessionProvider = mock(SessionProvider.class);
        LegendPac4jConfiguration pac4jConfiguration = new LegendPac4jConfiguration();
        pac4jConfiguration.setClients(Arrays.<Client>asList(newClient(CLIENT_A, HEADER_A), newClient(CLIENT_B, HEADER_B)));
        this.resource = new AuthCheckResource(this.request, this.response, null, pac4jConfiguration, this.sessionProvider);
    }

    @Test
    public void clientNameAloneSelectsThatClient()
    {
        givenParameters(null, CLIENT_A);

        Assert.assertFalse(this.resource.isAuthorized());

        verify(this.request).getHeader(HEADER_A);
        verify(this.request, never()).getHeader(HEADER_B);
    }

    @Test
    public void forceClientAloneIsIgnored()
    {
        givenParameters(CLIENT_B, null);

        Assert.assertFalse(this.resource.isAuthorized());

        verify(this.request, never()).getHeader(anyString());
        verify(this.sessionProvider).getSessionFromSessionStore(this.request, this.response);
    }

    @Test
    public void clientNameDecidesWhenForceClientIsAlsoPresent()
    {
        givenParameters(CLIENT_B, CLIENT_A);

        Assert.assertFalse(this.resource.isAuthorized());

        verify(this.request).getHeader(HEADER_A);
        verify(this.request, never()).getHeader(HEADER_B);
    }

    @Test
    public void anUnknownForceClientIsIgnored()
    {
        givenParameters("noSuchClient", CLIENT_A);

        Assert.assertFalse(this.resource.isAuthorized());

        verify(this.request).getHeader(HEADER_A);
        verify(this.request, never()).getHeader(HEADER_B);
    }

    @Test
    public void noClientNameFallsBackToTheSessionStore()
    {
        givenParameters(null, null);

        Assert.assertFalse(this.resource.isAuthorized());

        verify(this.request, never()).getHeader(anyString());
        verify(this.sessionProvider).getSessionFromSessionStore(this.request, this.response);
    }

    @Test
    public void anUnknownClientNameFallsBackToTheSessionStore()
    {
        givenParameters(null, "noSuchClient");

        Assert.assertFalse(this.resource.isAuthorized());

        verify(this.request, never()).getHeader(anyString());
        verify(this.sessionProvider).getSessionFromSessionStore(this.request, this.response);
    }

    @Test
    public void aSelectedClientWithoutItsHeaderFallsBackToTheSessionStore()
    {
        givenParameters(null, CLIENT_A);
        // HEADER_A is never stubbed, so request.getHeader(HEADER_A) returns null.

        Assert.assertFalse(this.resource.isAuthorized());

        verify(this.sessionProvider).getSessionFromSessionStore(this.request, this.response);
    }

    @Test
    public void aSessionStoreFailureIsA503RatherThanFalse()
    {
        givenParameters(null, null);
        when(this.sessionProvider.getSessionFromSessionStore(any(), any())).thenThrow(new IllegalStateException("store unreachable"));

        LegendSDLCException e = Assert.assertThrows(LegendSDLCException.class, () -> this.resource.isAuthorized());

        Assert.assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, e.getStatusCode());
        Assert.assertTrue(e.getCause() instanceof IllegalStateException);
    }

    @Test
    public void forceClientIsNeverRead()
    {
        givenParameters(CLIENT_B, CLIENT_A);

        this.resource.isAuthorized();

        verify(this.request).getParameter(Pac4jConstants.DEFAULT_CLIENT_NAME_PARAMETER);
        verify(this.request, never()).getParameter(Pac4jConstants.DEFAULT_FORCE_CLIENT_PARAMETER);
    }

    private void givenParameters(String forceClient, String clientName)
    {
        when(this.request.getParameter(Pac4jConstants.DEFAULT_FORCE_CLIENT_PARAMETER)).thenReturn(forceClient);
        when(this.request.getParameter(Pac4jConstants.DEFAULT_CLIENT_NAME_PARAMETER)).thenReturn(clientName);
    }

    private static GitlabPersonalAccessTokenClient newClient(String name, String header)
    {
        GitlabPersonalAccessTokenClient client = new GitlabPersonalAccessTokenClient();
        client.setName(name);
        client.headerTokenName = header;
        return client;
    }
}
