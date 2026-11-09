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

import org.finos.legend.sdlc.backend.api.spi.Backend;
import org.finos.legend.sdlc.backend.api.spi.BackendSession;
import org.finos.legend.sdlc.server.auth.CommonProfileSession;
import org.finos.legend.sdlc.server.auth.Session;
import org.finos.legend.server.pac4j.LegendPac4jConfiguration;
import org.junit.Assert;
import org.junit.Test;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.util.Pac4jConstants;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static org.finos.legend.sdlc.server.auth.LegendSDLCWebFilter.SESSION_ATTRIBUTE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestAuthCheckResource
{
    @Test
    public void testKerberosSessionIsNotAuthorizedForGitlabClient()
    {
        // Studio asks with client_name=gitlab; pac4j refuses that client on a Kerberos login, so true would strand it
        Assert.assertFalse(isAuthorized(sessionFromClient("KerberosClient"), "gitlab"));
    }

    @Test
    public void testSessionIsAuthorizedForItsOwnClient()
    {
        Assert.assertTrue(isAuthorized(sessionFromClient("gitlab"), "gitlab"));
        Assert.assertTrue(isAuthorized(sessionFromClient("gitlab"), "GITLAB"));
        Assert.assertTrue(isAuthorized(sessionFromClient("KerberosClient"), "KerberosClient"));
    }

    @Test
    public void testNoClientNamedKeepsTheBackendAnswer()
    {
        Assert.assertTrue(isAuthorized(sessionFromClient("KerberosClient"), null));
        Assert.assertTrue(isAuthorized(sessionFromClient("KerberosClient"), ""));
    }

    @Test
    public void testSessionWithoutClientNameKeepsTheBackendAnswer()
    {
        // e.g. a personal access token session built outside pac4j, whose profile has no client name
        Assert.assertTrue(isAuthorized(sessionFromClient(null), "gitlabPAToken"));
        Assert.assertTrue(AuthCheckResource.isFromClient(mock(Session.class), "gitlab"));
    }

    private static boolean isAuthorized(Session session, String clientName)
    {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(SESSION_ATTRIBUTE)).thenReturn(session);
        when(request.getParameter(Pac4jConstants.DEFAULT_CLIENT_NAME_PARAMETER)).thenReturn(clientName);

        BackendSession backendSession = mock(BackendSession.class);
        when(backendSession.isAuthorized()).thenReturn(true);
        Backend backend = mock(Backend.class);
        when(backend.newSession(any())).thenReturn(backendSession);

        AuthCheckResource resource = new AuthCheckResource(request, mock(HttpServletResponse.class), () -> backend, new LegendPac4jConfiguration(), null);
        return resource.isAuthorized();
    }

    @SuppressWarnings("unchecked")
    private static Session sessionFromClient(String clientName)
    {
        CommonProfile profile = new CommonProfile();
        profile.setId("user");
        profile.setClientName(clientName);
        CommonProfileSession<CommonProfile> session = mock(CommonProfileSession.class);
        when(session.getProfile()).thenReturn(profile);
        return session;
    }
}
