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
import org.finos.legend.sdlc.server.auth.Session;
import org.finos.legend.sdlc.server.auth.StateSession;
import org.finos.legend.sdlc.server.auth.StateSessionBuilder;
import org.finos.legend.server.pac4j.gitlab.GitlabPersonalAccessTokenProfile;
import org.finos.legend.server.pac4j.kerberos.KerberosProfile;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.oidc.profile.OidcProfile;

import java.util.List;
import javax.servlet.FilterConfig;
import javax.servlet.http.Cookie;

/**
 * The server's session filter, backend-independent: builds a {@link StateSession} from the pac4j profiles and
 * the session cookie (which carries the state bag backends persist through their session state store). Replaces
 * the former GitLab-specific web filter; the supported profile types are unchanged from it.
 */
public class StateSessionWebFilter extends LegendSDLCWebFilter<CommonProfile>
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
        StateSession session = null;
        if (sessionCookie != null)
        {
            session = newSessionFromProfilesAndToken(profiles, sessionCookie.getValue());
        }
        if (session == null)
        {
            session = newSessionFromProfiles(profiles);
        }
        return session;
    }

    private StateSession newSessionFromProfilesAndToken(List<CommonProfile> profiles, String sessionToken)
    {
        StateSessionBuilder builder;
        try
        {
            builder = StateSessionBuilder.newBuilder().fromToken(sessionToken);
        }
        catch (Exception e)
        {
            LOGGER.debug("invalid session cookie: {}", sessionToken);
            return null;
        }

        String userId = builder.getUserId();
        if (userId == null)
        {
            return null;
        }
        for (CommonProfile profile : profiles)
        {
            if (userId.equals(profile.getId()) && isSupportedProfile(profile))
            {
                try
                {
                    StateSession session = builder.withProfile(profile).build();
                    LOGGER.debug("session created from cookie and profile: {} / {}", sessionToken, profile);
                    return session;
                }
                catch (Exception e)
                {
                    LOGGER.error("error creating session from cookie and profile: {} / {}", sessionToken, profile);
                    builder.reset().fromToken(sessionToken);
                }
            }
        }
        LOGGER.debug("no suitable profile for cookie: {}", sessionToken);
        return null;
    }

    private StateSession newSessionFromProfiles(List<CommonProfile> profiles)
    {
        for (CommonProfile profile : profiles)
        {
            if (isSupportedProfile(profile))
            {
                try
                {
                    StateSession session = StateSessionBuilder.newBuilder().withProfile(profile).build();
                    LOGGER.debug("session created from profile: {}", profile);
                    return session;
                }
                catch (Exception e)
                {
                    LOGGER.error("error creating session from profile: {}", profile);
                }
            }
        }
        return null;
    }

    static boolean isSupportedProfile(CommonProfile profile)
    {
        return (profile instanceof KerberosProfile) || (profile instanceof OidcProfile) || (profile instanceof GitlabPersonalAccessTokenProfile);
    }
}
