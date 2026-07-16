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

import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.set.MutableSet;
import org.finos.legend.sdlc.backend.api.spi.BackendSessionContext;
import org.finos.legend.sdlc.backend.api.spi.BackendSessionStateStore;
import org.finos.legend.sdlc.backend.api.spi.OidcAuthMaterial;
import org.finos.legend.sdlc.backend.api.spi.PersonalAccessTokenAuthMaterial;
import org.finos.legend.sdlc.server.auth.CommonProfileSession;
import org.finos.legend.sdlc.server.auth.KerberosSession;
import org.finos.legend.sdlc.server.auth.LegendSDLCWebFilter;
import org.finos.legend.sdlc.server.auth.Session;
import org.finos.legend.sdlc.server.auth.StateSession;
import org.finos.legend.sdlc.server.guice.UserContext;
import org.finos.legend.server.pac4j.gitlab.GitlabPersonalAccessTokenProfile;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.oidc.profile.OidcProfile;

import javax.security.auth.Subject;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;

/**
 * The server's {@link BackendSessionContext}: identity from the authenticated {@link UserContext}; per-user
 * state persisted on the {@link StateSession} and written back to the session cookie on every change; auth
 * material harvested from the pac4j profile and published as data through {@link #getService} — the L6 adapter
 * of the session contract, and the only place pac4j types appear on the backend session path.
 */
public class ServletBackendSessionContext implements BackendSessionContext
{
    private final UserContext userContext;
    private final MutableMap<String, String> transientState = Maps.mutable.empty();

    public ServletBackendSessionContext(UserContext userContext)
    {
        this.userContext = Objects.requireNonNull(userContext, "userContext may not be null");
    }

    @Override
    public String getUserId()
    {
        return this.userContext.getCurrentUser();
    }

    @Override
    public BackendSessionStateStore getStateStore()
    {
        Session session = this.userContext.getSession();
        if (session instanceof StateSession)
        {
            StateSession stateSession = (StateSession) session;
            return new BackendSessionStateStore()
            {
                @Override
                public String get(String key)
                {
                    return stateSession.getState().get(key);
                }

                @Override
                public void put(String key, String value)
                {
                    stateSession.putState(key, value);
                    LegendSDLCWebFilter.setSessionCookie(ServletBackendSessionContext.this.userContext.getHttpResponse(), stateSession);
                }
            };
        }

        // no state-carrying session (e.g. a test session): fall back to a request-transient store
        return new BackendSessionStateStore()
        {
            @Override
            public String get(String key)
            {
                return ServletBackendSessionContext.this.transientState.get(key);
            }

            @Override
            public void put(String key, String value)
            {
                if (value == null)
                {
                    ServletBackendSessionContext.this.transientState.remove(key);
                }
                else
                {
                    ServletBackendSessionContext.this.transientState.put(key, value);
                }
            }
        };
    }

    @Override
    public <T> T getService(Class<T> serviceType)
    {
        Session session = this.userContext.getSession();
        if (serviceType == Subject.class)
        {
            return (session instanceof KerberosSession) ? serviceType.cast(((KerberosSession) session).getSubject()) : null;
        }
        if (serviceType == OidcAuthMaterial.class)
        {
            CommonProfile profile = getProfile(session);
            return (profile instanceof OidcProfile) ? serviceType.cast(buildOidcAuthMaterial((OidcProfile) profile)) : null;
        }
        if (serviceType == PersonalAccessTokenAuthMaterial.class)
        {
            CommonProfile profile = getProfile(session);
            return (profile instanceof GitlabPersonalAccessTokenProfile) ? serviceType.cast(buildPersonalAccessTokenAuthMaterial((GitlabPersonalAccessTokenProfile) profile)) : null;
        }
        return null;
    }

    public UserContext getServerUserContext()
    {
        return this.userContext;
    }

    private static CommonProfile getProfile(Session session)
    {
        return (session instanceof CommonProfileSession) ? ((CommonProfileSession<?>) session).getProfile() : null;
    }

    private static OidcAuthMaterial buildOidcAuthMaterial(OidcProfile profile)
    {
        com.nimbusds.oauth2.sdk.token.AccessToken accessToken = profile.getAccessToken();
        if ((accessToken == null) || (accessToken.getValue() == null))
        {
            return null;
        }
        MutableSet<String> scopes = (accessToken.getScope() == null) ? null : Sets.mutable.withAll(accessToken.getScope().toStringList());
        String refreshToken = (profile.getRefreshToken() == null) ? null : profile.getRefreshToken().getValue();
        Date expiration = profile.getExpiration();
        return OidcAuthMaterial.newOidcAuthMaterial(profile.getIssuer(), accessToken.getValue(), scopes, refreshToken, (expiration == null) ? null : Instant.ofEpochMilli(expiration.getTime()));
    }

    private static PersonalAccessTokenAuthMaterial buildPersonalAccessTokenAuthMaterial(GitlabPersonalAccessTokenProfile profile)
    {
        String token = profile.getPersonalAccessToken();
        return (token == null) ? null : PersonalAccessTokenAuthMaterial.newPersonalAccessTokenAuthMaterial(profile.getGitlabHost(), token);
    }
}
